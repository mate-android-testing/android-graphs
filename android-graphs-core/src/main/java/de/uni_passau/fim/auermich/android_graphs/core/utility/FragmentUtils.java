package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.app.components.Activity;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Component;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.ComponentType;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Fragment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class FragmentUtils {

    private static final Logger LOGGER = LogManager.getLogger(FragmentUtils.class);

    /**
     * The various API methods to add or replace a fragment.
     */
    private static final Set<String> FRAGMENT_INVOCATIONS = new HashSet<>() {{
        add("Landroid/support/v4/app/FragmentTransaction;->" +
                "add(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;");
        add("Landroid/app/FragmentTransaction;->" +
                "replace(ILandroid/app/Fragment;)Landroid/app/FragmentTransaction;");
        add("Landroid/app/FragmentTransaction;->" +
                "replace(ILandroid/app/Fragment;L/java/lang/String;)Landroid/app/FragmentTransaction;");
        add("Landroidx/fragment/app/FragmentTransaction;->" +
                "add(ILandroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;");
        add("Landroid/support/v4/app/FragmentTransaction;->" +
                "replace(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;");
        add("Landroid/support/v4/app/FragmentTransaction;->" +
                "replace(ILandroid/support/v4/app/Fragment;Ljava/lang/String;)Landroid/support/v4/app/FragmentTransaction;");
        add("Landroid/support/v4/app/FragmentTransaction;->" +
                "add(ILandroid/support/v4/app/Fragment;Ljava/lang/String;)Landroid/support/v4/app/FragmentTransaction;");
        add("Landroid/support/v4/app/FragmentTransaction;->" +
                "add(Landroid/support/v4/app/Fragment;Ljava/lang/String;)Landroid/support/v4/app/FragmentTransaction;");
    }};

    private FragmentUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Checks whether the given method refers to the invocation of a fragment. That
     * is either a call to add() or replace(). We do not ensure that commit() is
     * called afterwards.
     *
     * @param method The method to be checked against.
     * @return Returns {@code true} if method refers to the invocation of a component,
     * otherwise {@code false} is returned.
     */
    public static boolean isFragmentInvocation(final String method) {
        return FRAGMENT_INVOCATIONS.contains(method);
    }

    /**
     * Checks whether the instruction refers to the invocation of a fragment.
     * Only call this method when isFragmentInvocation() returns {@code true}.
     *
     * @param analyzedInstruction The given instruction.
     * @return Returns the name of the fragment or {@code null} if the fragment name
     * couldn't be derived.
     */
    public static String isFragmentInvocation(final AnalyzedInstruction analyzedInstruction) {

        Set<String> fragments = new HashSet<>();

        Instruction instruction = analyzedInstruction.getInstruction();
        String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

        if (instruction.getOpcode() == Opcode.INVOKE_VIRTUAL) {

            Instruction35c invokeVirtual = (Instruction35c) instruction;

            /*
             * All fragment invocations share almost the same order of arguments independent
             * whether it is a call to add() or replace(). A typical example is as follows:
             *
             * Registers: v8 (Reg C), p0 (Reg D), p4 (Reg E)
             * invoke-virtual {v8, p0, p4}, Landroid/app/FragmentTransaction;
             *             ->replace(ILandroid/app/Fragment;)Landroid/app/FragmentTransaction;
             *
             * In any of these calls, we are interested in the parameter/register referring to
             * the fragment. For the above example this is register p4. We start backtracking
             * for p4 and check whether a new fragment is constructed and hold by p4.
             */
            int fragmentRegisterID = invokeVirtual.getRegisterE();

            if (targetMethod.equals("Landroid/support/v4/app/FragmentTransaction;->" +
                    "add(Landroid/support/v4/app/Fragment;Ljava/lang/String;)Landroid/support/v4/app/FragmentTransaction;")) {
                // fragment parameter is now first argument (p0 in above example)
                fragmentRegisterID = invokeVirtual.getRegisterD();
            }

            // go over all predecessors
            Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
            int finalFragmentRegisterID = fragmentRegisterID;
            predecessors.forEach(predecessor ->
                    fragments.addAll(isFragmentInvocationRecursive(predecessor, finalFragmentRegisterID)));
        }

        if (!fragments.isEmpty()) {
            return fragments.stream().findFirst().get();
        } else {
            return null;
        }
    }

    /**
     * Recursively looks up every predecessor for holding a reference to a fragment.
     *
     * @param predecessor        The current predecessor instruction.
     * @param fragmentRegisterID The register potentially holding a fragment.
     * @return Returns a set of fragments or an empty set if no fragment was found.
     */
    private static Set<String> isFragmentInvocationRecursive(final AnalyzedInstruction predecessor,
                                                             final int fragmentRegisterID) {

        Set<String> fragments = new HashSet<>();

        // base case
        if (predecessor.getInstructionIndex() == -1) {
            return fragments;
        }

        if (predecessor.getInstruction().getOpcode() == Opcode.NEW_INSTANCE) {
            // check for new instance declaration of fragment class
            Instruction21c newInstance = (Instruction21c) predecessor.getInstruction();
            if (newInstance.getRegisterA() == fragmentRegisterID) {
                String fragment = newInstance.getReference().toString();
                fragments.add(fragment);
                return fragments;
            }
        } else if (predecessor.getInstruction().getOpcode() == Opcode.INVOKE_DIRECT) {
            // check for constructor invocation of fragment class
            Instruction35c constructor = (Instruction35c) predecessor.getInstruction();
            if (constructor.getRegisterC() == fragmentRegisterID) {
                String constructorInvocation = constructor.getReference().toString();
                String fragment = MethodUtils.getClassName(constructorInvocation);
                fragments.add(fragment);
                return fragments;
            }
        }

        // check all predecessors
        Set<AnalyzedInstruction> predecessors = predecessor.getPredecessors();
        predecessors.forEach(p -> fragments.addAll(isFragmentInvocationRecursive(p, fragmentRegisterID)));
        return fragments;
    }

    /**
     * Tracks whether the given instruction refers to a fragment invocation. If this is the case,
     * the activity hosting the fragment is updated with this information.
     * NOTE: We assume that those fragment invocations happen only within an activity, but a fragment
     * itself can have nested fragments as well, or another class that has a reference to FragmentManager or
     * FragmentTransaction could also invoke such fragment invocation.
     *
     * @param method              The method defining the invocation.
     * @param components          The set of components.
     * @param analyzedInstruction The fragment invocation instruction.
     */
    public static void checkForFragmentInvocation(final Set<Component> components, String method,
                                                  AnalyzedInstruction analyzedInstruction) {

        Instruction instruction = analyzedInstruction.getInstruction();
        String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

        // track which fragments are hosted by which activity
        if (isFragmentInvocation(targetMethod)) {

            // check whether fragment is defined within given method, i.e. a local call to the fragment ctr
            String fragmentName = isFragmentInvocation(analyzedInstruction);

            if (fragmentName != null) {
                String activityName = MethodUtils.getClassName(method);

                Optional<Component> activityComponent = ComponentUtils.getComponentByName(components, activityName);
                Optional<Component> fragmentComponent = ComponentUtils.getComponentByName(components, fragmentName);

                if (activityComponent.isPresent()
                        && activityComponent.get().getComponentType() == ComponentType.ACTIVITY
                        && fragmentComponent.isPresent()) {
                    Activity activity = (Activity) activityComponent.get();
                    Fragment fragment = (Fragment) fragmentComponent.get();
                    activity.addHostingFragment(fragment);
                } else {
                    // TODO: Handle nested fragments.
                    LOGGER.warn("Couldn't assign fragment " + fragmentName + " to activity: " + activityName);
                }
            }
        }
    }
}
