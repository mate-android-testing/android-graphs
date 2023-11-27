package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11x;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.google.common.collect.Multimap;
import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Activity;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Component;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.ComponentType;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Fragment;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.LayoutFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
                "replace(ILandroid/app/Fragment;Ljava/lang/String;)Landroid/app/FragmentTransaction;");
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
     *         otherwise {@code false} is returned.
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
     *         couldn't be derived.
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
     * @param predecessor The current predecessor instruction.
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
        } else if (predecessor.getInstruction().getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
            /*
             * In addition to above possibilities, a fragment can be also created through a
             * regular method. For instance, consider the following example:
             *
             *   invoke-static {p1}, Lde/retujo/bierverkostung/beer/SelectBeerFragment;
             *       ->newInstance(Landroid/view/View$OnClickListener;)Lde/retujo/bierverkostung/beer/SelectBeerFragment;
             *   move-result-object p1
             *
             * We first check whether the result has been saved in the relevant register. Then we
             * look at the direct predecessor and check the return type of the invocation.
             */
            Instruction11x moveResultObject = (Instruction11x) predecessor.getInstruction();
            if (moveResultObject.getRegisterA() == fragmentRegisterID) {
                Instruction35c invokeInstruction = (Instruction35c) predecessor.getPredecessors().first().getInstruction();
                String fragment = MethodUtils.getReturnType(invokeInstruction.getReference().toString());
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
     * @param apk The APK file.
     * @param components The set of components.
     * @param classDef The class in which the invocation happens.
     * @param method The method defining the invocation.
     * @param analyzedInstruction The fragment invocation instruction.
     */
    public static void checkForFragmentInvocation(final APK apk, final Set<Component> components,
                                                  final ClassDef classDef, final String method,
                                                  final AnalyzedInstruction analyzedInstruction) {

        Instruction instruction = analyzedInstruction.getInstruction();
        String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

        // track which fragments are hosted by which activity
        if (isFragmentInvocation(targetMethod)) {

            // check whether fragment is defined within given method, i.e. a local call to the fragment constructor
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
            // fragments can also be defined via setContentView()
        } else if (targetMethod.endsWith(";->setContentView(I)V")) {

            final String activityName = MethodUtils.getClassName(method);
            final Optional<Activity> activityComponent = ComponentUtils.getActivityByName(components, activityName);

            if (activityComponent.isPresent()) { // we assume only activities can declare fragments

                final String resourceID = Utility.getLayoutResourceID(classDef, analyzedInstruction);
                if (resourceID != null) {
                    // Map the resource id to a layout file if possible.
                    final LayoutFile layoutFile = LayoutFile.findLayoutFile(apk.getDecodingOutputPath(), resourceID);
                    if (layoutFile != null) {
                        final Set<String> fragments = layoutFile.parseFragments().stream()
                                .map(ClassUtils::convertDottedClassName)
                                .collect(Collectors.toSet());
                        for (String fragmentName : fragments) {
                            final Optional<Fragment> fragmentComponent
                                    = ComponentUtils.getFragmentByName(components, fragmentName);
                            if (fragmentComponent.isPresent()
                                    && fragmentComponent.get().getComponentType() == ComponentType.FRAGMENT) {
                                final Activity activity = activityComponent.get();
                                final Fragment fragment = fragmentComponent.get();
                                activity.addHostingFragment(fragment);
                            }
                        }
                    }
                }
            }
            // convenience method to add an fragment
        } else if (targetMethod.endsWith("->show(Landroid/support/v4/app/FragmentManager;Ljava/lang/String;)V")) {

            // NOTE: Retrieving the activity to which the fragment belongs is a mere heuristic. Ideally we should try
            // to backtrack the invocation instead of relying upon (direct and indirect) usages.

            // fragment name is encoded in the invocation call
            final String fragmentName = MethodUtils.getClassName(targetMethod);
            final Optional<Fragment> fragmentComponent
                    = ComponentUtils.getFragmentByName(components, fragmentName);

            if (ClassUtils.isInnerClass(classDef.toString())) {

                final String outerClassName = ClassUtils.getOuterClass(classDef.toString());

                // TODO: The fragment invocation might be arbitrarily nested, thus it remains unclear which lookup level
                //  should be chosen for the (indirect) usages.

                // TODO: A plain usage does not guarantee that an activity is really making use of the fragment.

                // check which application classes make direct or indirect use of the given class
                final Set<UsageSearch.Usage> usages = UsageSearch.findClassUsages(apk, outerClassName, 2);

                // check whether any found class represents an activity
                for (UsageSearch.Usage usage : usages) {

                    final String clazzName = usage.getClazz().toString();
                    final Optional<Activity> activityComponent = ComponentUtils.getActivityByName(components, clazzName);

                    // TODO: There are potentially several activities that make use of the fragment but the call to
                    //  show() actually refers only to a single activity.
                    if (activityComponent.isPresent() && fragmentComponent.isPresent()) {
                        final Activity activity = activityComponent.get();
                        final Fragment fragment = fragmentComponent.get();
                        activity.addHostingFragment(fragment);
                    }
                }
            }
        }
    }

    /**
     * Retrieves fragment view pager usages for activities by inspecting the given instruction. Adds to each activity
     * the hosted fragments (described by the fragment view pager usage).
     *
     * @param components The set of components.
     * @param method The given method in which the instruction is defined.
     * @param analyzedInstruction The given instruction possible describing a view pager usage.
     * @param classHierarchy The discovered class hierarchy.
     * @return Returns a consumer expecting the resolved class usages.
     */
    public static Consumer<Multimap<String, String>> checkForFragmentViewPager(final Set<Component> components,
                                                                               String method,
                                                                               AnalyzedInstruction analyzedInstruction,
                                                                               ClassHierarchy classHierarchy) {

        return usages -> { // the resolved class usages are required as input
            Instruction instruction = analyzedInstruction.getInstruction();
            String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

            final Set<String> pageAdapterClasses = Set.of(
                    "Landroid/support/v4/view/PagerAdapter;",
                    "Landroid/support/v4/app/FragmentStatePagerAdapter;"
            );

            final Set<String> pageAdapterMethods = Set.of(
                    "Landroid/support/v4/view/ViewPager;->setAdapter(Landroid/support/v4/view/PagerAdapter;)V",
                    "Landroidx/viewpager/widget/ViewPager;->setAdapter(Landroidx/viewpager/widget/PagerAdapter;)V"
            );

            if (pageAdapterMethods.contains(targetMethod)) {

                String callingClass = MethodUtils.getClassName(method);

                // only resolve usage if surrounding class refers to an activity
                ComponentUtils.getActivityByName(components, callingClass)
                        .ifPresent(activity -> usages.get(callingClass).stream()
                                // only consider page adapters used on the given activity
                                .filter(clazz -> classHierarchy.getSuperClasses(clazz).stream()
                                        .anyMatch(pageAdapterClasses::contains))
                                // get usages of page adapters
                                .map(usages::get)
                                .flatMap(Collection::stream)
                                // map page adapter usages to fragments if possible
                                .map(pageAdapterUsage -> ComponentUtils.getFragmentByName(components, pageAdapterUsage))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                // add all page adapter fragments to the given activity
                                .forEach(activity::addHostingFragment)
                        );
            }
        };
    }
}
