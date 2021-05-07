package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.google.common.collect.Lists;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Activity;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Component;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Service;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraphBuilder;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Vertex;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.ReturnStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Format;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.DexClassProvider;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;
import org.jf.dexlib2.iface.instruction.formats.Instruction22c;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public final class Utility {

    // the dalvik bytecode level (Android API version)
    public static final Opcodes API_OPCODE = Opcodes.forApi(28);

    public static final String EXCLUSION_PATTERN_FILE = "exclude.txt";
    private static final Logger LOGGER = LogManager.getLogger(Utility.class);

    /**
     * It seems that certain resource classes are API dependent, e.g.
     * "R$interpolator" is only available in API 21.
     */
    private static final Set<String> RESOURCE_CLASSES = new HashSet<>() {{
        add("R$anim");
        add("R$attr");
        add("R$bool");
        add("R$color");
        add("R$dimen");
        add("R$drawable");
        add("R$id");
        add("R$integer");
        add("R$layout");
        add("R$mipmap");
        add("R$string");
        add("R$style");
        add("R$styleable");
        add("R$interpolator");
        add("R$menu");
        add("R$array");
        add("R$xml");
    }};

    /**
     * The recognized activity classes.
     */
    private static final Set<String> ACTIVITY_CLASSES = new HashSet<>() {{
        add("Landroid/app/Activity;");
        add("Landroidx/appcompat/app/AppCompatActivity;");
        add("Landroid/support/v7/app/AppCompatActivity;");
        add("Landroid/support/v7/app/ActionBarActivity;");
        add("Landroid/support/v4/app/FragmentActivity;");
    }};

    /**
     * The recognized fragment classes, see https://developer.android.com/reference/android/app/Fragment.
     */
    private static final Set<String> FRAGMENT_CLASSES = new HashSet<>() {{
        add("Landroid/app/Fragment;");
        add("Landroidx/fragment/app/Fragment;");
        add("Landroid/support/v4/app/Fragment;");
        add("Landroid/app/DialogFragment;");
        add("Landroid/app/ListFragment;");
        add("Landroid/preference/PreferenceFragment;");
        add("Landroid/webkit/WebViewFragment;");
    }};

    /**
     * The recognized service classes, see https://developer.android.com/reference/android/app/Service.
     */
    private static final Set<String> SERVICE_CLASSES = new HashSet<>() {{
        add("Landroid/app/Service;");
    }};

    /**
     * The recognized ART methods.
     */
    private static final Set<String> ART_METHODS = new HashSet<>() {{
        add("startActivity(Landroid/content/Intent;)V");
        add("startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V");
        add("findViewById(I)Landroid/view/View;");
        add("setContentView(I)V");
        add("setContentView(Landroid/view/View;)V");
        add("setContentView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V");
        add("getSupportFragmentManager()Landroid/support/v4/app/FragmentManager;");
    }};

    private static final Set<Opcode> INVOKE_OPCODES = new HashSet<>() {{
        add(Opcode.INVOKE_CUSTOM_RANGE);
        add(Opcode.INVOKE_CUSTOM);
        add(Opcode.INVOKE_DIRECT_RANGE);
        add(Opcode.INVOKE_DIRECT);
        add(Opcode.INVOKE_DIRECT_EMPTY);
        add(Opcode.INVOKE_INTERFACE_RANGE);
        add(Opcode.INVOKE_INTERFACE);
        add(Opcode.INVOKE_OBJECT_INIT_RANGE);
        add(Opcode.INVOKE_POLYMORPHIC_RANGE);
        add(Opcode.INVOKE_POLYMORPHIC);
        add(Opcode.INVOKE_STATIC_RANGE);
        add(Opcode.INVOKE_STATIC);
        add(Opcode.INVOKE_SUPER_RANGE);
        add(Opcode.INVOKE_SUPER);
        add(Opcode.INVOKE_SUPER_QUICK_RANGE);
        add(Opcode.INVOKE_SUPER_QUICK);
        add(Opcode.INVOKE_VIRTUAL_RANGE);
        add(Opcode.INVOKE_VIRTUAL);
        add(Opcode.INVOKE_VIRTUAL_QUICK_RANGE);
        add(Opcode.INVOKE_VIRTUAL_QUICK);
    }};

    /**
     * The various API methods to invoke a component, e.g. an activity.
     */
    private static final Set<String> COMPONENT_INVOCATIONS = new HashSet<>() {{
        add("startActivity(Landroid/content/Intent;)V");
        add("startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V");
        add("startService(Landroid/content/Intent;)Landroid/content/ComponentName;");
        add("bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z");
    }};

    /**
     * The various API methods to add or replace a fragment.
     */
    private static final Set<String> FRAGMENT_INVOCATIONS = new HashSet<>() {{
        add("Landroid/support/v4/app/FragmentTransaction;->" +
                "add(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;");
        add("Landroid/app/FragmentTransaction;->" +
                "replace(ILandroid/app/Fragment;)Landroid/app/FragmentTransaction;");
        add("Landroidx/fragment/app/FragmentTransaction;->" +
                "add(ILandroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;");
    }};

    private Utility() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Removes the given file, can be either a directory a simple file.
     *
     * @param toBeRemoved The file to be removed.
     * @return Returns {@code true} if removing file succeeded, otherwise {@code false} is returned.
     */
    public static boolean removeFile(final File toBeRemoved) {
        try {
            // FIXME: Certain files can't be properly deleted. This seems to be caused by some interplay between
            //  the decoding of the APK and the removal afterwards. Those files seem to be locked temporarily.
            FileUtils.forceDelete(toBeRemoved);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Couldn't remove file: " + toBeRemoved);
            LOGGER.warn(e.getMessage());
            return false;
        }
    }

    /**
     * Returns the dex files in the given {@param directory}.
     *
     * @param directory The directory to search for the dex files.
     * @return Returns a list of dex files found in the given directory.
     */
    @SuppressWarnings("unused")
    public static File[] getDexFiles(final File directory) {

        File[] matches = directory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("classes") && name.endsWith(".dex");
            }
        });
        return matches;
    }

    /**
     * Checks for a call to setContentView() or inflate() respectively and retrieves the layout resource id
     * associated with the layout file.
     *
     * @param classDef            The class defining the invocation.
     * @param analyzedInstruction The instruction referring to an invocation of setContentView() or inflate().
     * @return Returns the layout resource for the given class (if any).
     */
    public static String getLayoutResourceID(final ClassDef classDef, final AnalyzedInstruction analyzedInstruction) {

        Instruction35c invokeVirtual = (Instruction35c) analyzedInstruction.getInstruction();
        String methodReference = invokeVirtual.getReference().toString();

        if (methodReference.endsWith("setContentView(I)V")
                // ensure that setContentView() refers to the given class
                && classDef.toString().equals(Utility.getClassName(methodReference))) {
            // TODO: there are multiple overloaded setContentView() implementations
            // we assume here only setContentView(int layoutResID)
            // link: https://developer.android.com/reference/android/app/Activity.html#setContentView(int)

            /*
             * We need to find the resource id located in one of the registers. A typical call to
             * setContentView(int layoutResID) looks as follows:
             *     invoke-virtual {p0, v0}, Lcom/zola/bmi/BMIMain;->setContentView(I)V
             * Here, v0 contains the resource id, thus we need to search backwards for the last
             * change of v0. This is typically the previous instruction and is of type 'const'.
             */

            LOGGER.debug("ClassName: " + classDef);
            LOGGER.debug("Method Reference: " + methodReference);
            LOGGER.debug("LayoutResID Register: " + invokeVirtual.getRegisterD());

            // the id of the register, which contains the layoutResID
            int layoutResIDRegister = invokeVirtual.getRegisterD();

            boolean foundLayoutResID = false;
            assert !analyzedInstruction.getPredecessors().isEmpty();
            AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

            while (!foundLayoutResID) {

                LOGGER.debug("Predecessor: " + predecessor.getInstruction().getOpcode());
                Instruction pred = predecessor.getInstruction();

                // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                if (pred instanceof NarrowLiteralInstruction
                        && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                        || pred.getOpcode() == Opcode.CONST_16 || pred.getOpcode() == Opcode.CONST_HIGH16)
                        && predecessor.setsRegister(layoutResIDRegister)) {
                    foundLayoutResID = true;
                    LOGGER.debug("XML ID: " + (((NarrowLiteralInstruction) pred).getNarrowLiteral()));
                    int resourceID = ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                    return "0x" + Integer.toHexString(resourceID);
                }

                if (predecessor.getPredecessors().isEmpty()) {
                    // couldn't find layout resource id
                    return null;
                } else {
                    predecessor = predecessor.getPredecessors().first();
                }
            }
        } else if (methodReference.endsWith("setContentView(Landroid/view/View;)V")
                // ensure that setContentView() refers to the given class
                && classDef.toString().equals(Utility.getClassName(methodReference))) {

            /*
             * A typical example of this call looks as follows:
             * invoke-virtual {v2, v3}, Landroid/widget/PopupWindow;->setContentView(Landroid/view/View;)V
             *
             * Here, register v2 is the PopupWindow instance while v3 refers to the View object param.
             * Thus, we need to search for the call of setContentView/inflate() on the View object
             * in order to retrieve its layout resource ID.
             */

            LOGGER.debug("Class " + Utility.getClassName(methodReference) + " makes use of setContentView(View v)!");

            /*
             * TODO: are we interested in calls to setContentView(..) that don't refer to the this object?
             * The primary goal is to derive the layout ID of a given component (class). However, it seems
             * like classes (components) can define the layout of other (sub) components. Are we interested
             * in getting the layout ID of those (sub) components?
             */

            // we need to resolve the layout ID of the given View object parameter


        } else if (methodReference.contains("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z")) {
            // TODO: there are multiple overloaded inflate() implementations
            // see: https://developer.android.com/reference/android/view/LayoutInflater.html#inflate(org.xmlpull.v1.XmlPullParser,%20android.view.ViewGroup,%20boolean)
            // we assume here inflate(int resource,ViewGroup root, boolean attachToRoot)

            /*
             * A typical call of inflate(int resource,ViewGroup root, boolean attachToRoot) looks as follows:
             *   invoke-virtual {p1, v0, p2, v1}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;
             * Here, v0 contains the resource id, thus we need to search backwards for the last change of v0.
             * This is typically the previous instruction and is of type 'const'.
             */

            LOGGER.debug("ClassName: " + classDef);
            LOGGER.debug("Method Reference: " + methodReference);
            LOGGER.debug("LayoutResID Register: " + invokeVirtual.getRegisterD());

            // the id of the register, which contains the layoutResID
            int layoutResIDRegister = invokeVirtual.getRegisterD();

            boolean foundLayoutResID = false;
            assert !analyzedInstruction.getPredecessors().isEmpty();
            AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

            while (!foundLayoutResID) {

                LOGGER.debug("Predecessor: " + predecessor.getInstruction().getOpcode());
                Instruction pred = predecessor.getInstruction();

                // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                if (pred instanceof NarrowLiteralInstruction
                        && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                        || pred.getOpcode() == Opcode.CONST_16 || pred.getOpcode() == Opcode.CONST_HIGH16)
                        && predecessor.setsRegister(layoutResIDRegister)) {
                    foundLayoutResID = true;
                    LOGGER.debug("XML ID: " + (((NarrowLiteralInstruction) pred).getNarrowLiteral()));
                    int resourceID = ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                    return "0x" + Integer.toHexString(resourceID);
                }

                if (predecessor.getPredecessors().isEmpty()) {
                    // couldn't find layout resource id
                    return null;
                } else {
                    predecessor = predecessor.getPredecessors().first();
                }
            }
        } else if (methodReference.contains("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;")) {

        } else if (methodReference.contains("Landroid/view/LayoutInflater;->" +
                "inflate(Lorg/xmlpull/v1/XmlPullParser;Landroid/view/ViewGroup;")) {

        } else if (methodReference.contains("Landroid/view/LayoutInflater;->" +
                "inflate(Lorg/xmlpull/v1/XmlPullParser;Landroid/view/ViewGroup;Z")) {

        }
        return null;
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

        LOGGER.debug("Try deriving name of fragment...");

        // TODO: get rid of list and directly return the string or null
        List<String> fragments = new ArrayList<>();

        Instruction instruction = analyzedInstruction.getInstruction();
        String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

        // TODO: verify that only 'Instruction35c' is valid for fragment invocations
        if (instruction instanceof Instruction35c && instruction.getOpcode() == Opcode.INVOKE_VIRTUAL) {

            Instruction35c invokeVirtual = (Instruction35c) instruction;

            if (targetMethod.contains("Landroid/support/v4/app/FragmentTransaction;->" +
                    "add(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;")) {
                // a fragment is added to the current component (class)

                // typical call: v0 (Reg C), v1 (Reg D), v2 (Reg E)
                //     invoke-virtual {v0, v1, v2}, Landroid/support/v4/app/FragmentTransaction;->
                // add(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;

                // we are interested in register E (refers to the fragment)
                int fragmentRegisterID = invokeVirtual.getRegisterE();

                // TODO: avoid 'redundant' recursion steps
                // go over all predecessors
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
                predecessors.forEach(pred -> fragments.addAll(isFragmentAddInvocationRecursive(pred, fragmentRegisterID)));

            } else if (targetMethod.contains("Landroid/app/FragmentTransaction;->" +
                    "replace(ILandroid/app/Fragment;)Landroid/app/FragmentTransaction;")) {
                // a fragment is replaced with another one

                // Register C: v8, Register D: p0, Register E: p4
                // invoke-virtual {v8, p0, p4}, Landroid/app/FragmentTransaction;
                //              ->replace(ILandroid/app/Fragment;)Landroid/app/FragmentTransaction;

                // we are interested in register E (refers to the fragment)
                int fragmentRegisterID = invokeVirtual.getRegisterE();

                // TODO: avoid 'redundant' recursion steps
                // go over all predecessors
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
                predecessors.forEach(pred -> fragments.addAll(isFragmentReplaceInvocationRecursive(pred, fragmentRegisterID)));
            } else if (targetMethod.contains("Landroidx/fragment/app/FragmentTransaction;->" +
                    "add(ILandroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;")) {
                // a fragment is added (API 28)

                // invoke-virtual {v1, v3, v2, v4}, Landroidx/fragment/app/FragmentTransaction;->
                //      add(ILandroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;

                // we are interested in register E (refers to the fragment)
                int fragmentRegisterID = invokeVirtual.getRegisterE();

                // TODO: avoid 'redundant' recursion steps
                // go over all predecessors
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
                predecessors.forEach(pred -> fragments.addAll(isFragmentAddInvocationRecursive(pred, fragmentRegisterID)));
            }
        }

        if (!fragments.isEmpty()) {
            return fragments.get(0);
        } else {
            return null;
        }
    }

    /**
     * Recursively looks up every predecessor for holding a reference to a fragment.
     *
     * @param pred               The current predecessor instruction.
     * @param fragmentRegisterID The register potentially holding a fragment.
     * @return Returns a list of fragments or an empty list if no fragment was found.
     */
    private static List<String> isFragmentReplaceInvocationRecursive(final AnalyzedInstruction pred,
                                                                     final int fragmentRegisterID) {

        List<String> fragments = new ArrayList<>();

        // basic case
        if (pred.getInstructionIndex() == -1) {
            return fragments;
        }

        // check current instruction
        if (pred.getInstruction().getOpcode() == Opcode.NEW_INSTANCE) {
            // new-instance p4, Lcom/android/calendar/DayFragment;
            Instruction21c newInstance = (Instruction21c) pred.getInstruction();
            if (newInstance.getRegisterA() == fragmentRegisterID) {
                String fragment = newInstance.getReference().toString();
                LOGGER.debug("Fragment: " + fragment);
                // save for each activity the name of the fragment it hosts
                fragments.add(fragment);
            }
        }

        // check all predecessors
        Set<AnalyzedInstruction> predecessors = pred.getPredecessors();
        predecessors.forEach(p -> fragments.addAll(isFragmentReplaceInvocationRecursive(p, fragmentRegisterID)));
        return fragments;
    }

    /**
     * Recursively looks up every predecessor for holding a reference to a fragment.
     *
     * @param pred               The current predecessor instruction.
     * @param fragmentRegisterID The register potentially holding a fragment.
     * @return Returns a list of fragments or an empty list if no fragment was found.
     */
    private static List<String> isFragmentAddInvocationRecursive(final AnalyzedInstruction pred,
                                                                 final int fragmentRegisterID) {

        List<String> fragments = new ArrayList<>();

        // base case
        if (pred.getInstructionIndex() == -1) {
            return fragments;
        }

        // invoke direct refers to constructor calls
        if (pred.getInstruction().getOpcode() == Opcode.INVOKE_DIRECT) {
            // invoke-direct {v2}, Lcom/zola/bmi/BMIMain$PlaceholderFragment;-><init>()V
            Instruction35c constructor = (Instruction35c) pred.getInstruction();
            if (constructor.getRegisterC() == fragmentRegisterID) {
                String constructorInvocation = constructor.getReference().toString();
                LOGGER.debug("Fragment: " + constructorInvocation);
                // save for each activity the name of the fragment it hosts
                String fragment = Utility.getClassName(constructorInvocation);
                fragments.add(fragment);
            }
        }

        // check all predecessors
        Set<AnalyzedInstruction> predecessors = pred.getPredecessors();
        predecessors.forEach(p -> fragments.addAll(isFragmentAddInvocationRecursive(p, fragmentRegisterID)));
        return fragments;
    }

    /**
     * Returns solely the method name from a fully qualified method name.
     *
     * @param fullyQualifiedMethodName The fully qualified method name.
     * @return Returns the method name from the fully qualified method name.
     */
    public static String getMethodName(final String fullyQualifiedMethodName) {
        return fullyQualifiedMethodName.split(";->")[1];
    }

    /**
     * Checks whether the given method refers to the invocation of a component, e.g. an activity.
     *
     * @param components               The set of recognized components.
     * @param fullyQualifiedMethodName The method to be checked against.
     * @return Returns {@code true} if method refers to the invocation of a component,
     * otherwise {@code false} is returned.
     */
    public static boolean isComponentInvocation(final Set<Component> components, final String fullyQualifiedMethodName) {

        String clazz = Utility.getClassName(fullyQualifiedMethodName);
        String method = Utility.getMethodName(fullyQualifiedMethodName);

        // component invocations require a context object, this can be the application context or a component
        return COMPONENT_INVOCATIONS.contains(method)
                && (clazz.equals("Landroid/content/Context;")
                || components.stream().map(Component::getName).anyMatch(name -> name.equals(clazz)));
    }

    /**
     * Checks whether the given instruction refers to the invocation of a component.
     * A component is an activity or service for instance.
     * Only call this method when isComponentInvocation() returns {@code true}.
     *
     * @param components          The set of recognized components.
     * @param analyzedInstruction The given instruction.
     * @return Returns the constructor name of the target component if the instruction
     * refers to a component invocation, otherwise {@code null}.
     */
    public static String isComponentInvocation(final Set<Component> components,
                                               final AnalyzedInstruction analyzedInstruction) {

        // check for invoke/invoke-range instruction
        if (Utility.isInvokeInstruction(analyzedInstruction)) {

            Instruction instruction = analyzedInstruction.getInstruction();
            String invokeTarget = ((ReferenceInstruction) instruction).getReference().toString();
            String method = Utility.getMethodName(invokeTarget);

            if (method.equals("startActivity(Landroid/content/Intent;)V")
                    || method.equals("startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V")) {

                LOGGER.debug("Backtracking startActivity() invocation!");

                if (analyzedInstruction.getPredecessors().isEmpty()) {
                    // there is no predecessor -> target activity name might be defined somewhere else or external
                    return null;
                }

                // go back until we find const-class instruction which holds the activity name
                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                // TODO: check that we don't miss activities, go back recursively if there are several predecessors
                // upper bound to avoid resolving external activities or activities defined in a different method

                while (pred.getInstructionIndex() != -1) {
                    Instruction predecessor = pred.getInstruction();
                    if (predecessor.getOpcode() == Opcode.CONST_CLASS) {

                        String activityName = ((Instruction21c) predecessor).getReference().toString();
                        Optional<Component> activity = getComponentByName(components, activityName);

                        if (activity.isPresent()) {
                            // return the full-qualified name of the constructor
                            return activity.get().getName() + "-><init>()V";
                        }
                    } else {
                        if (analyzedInstruction.getPredecessors().isEmpty()) {
                            // there is no predecessor -> target activity name might be defined somewhere else or external
                            return null;
                        } else {
                            // TODO: may use recursive search over all predecessors
                            pred = pred.getPredecessors().first();
                        }
                    }
                }
            } else if (method.equals("startService(Landroid/content/Intent;)Landroid/content/ComponentName;")) {

                LOGGER.debug("Backtracking startService() invocation!");

                // invoke-virtual {p0, p1}, Landroid/content/Context;->startService(Landroid/content/Intent;)Landroid/content/ComponentName;

                if (analyzedInstruction.getPredecessors().isEmpty()) {
                    // there is no predecessor -> target activity name might be defined somewhere else or external
                    return null;
                }

                // go back until we find const-class instruction which holds the service name
                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                while (pred.getInstructionIndex() != -1) {
                    Instruction predecessor = pred.getInstruction();
                    if (predecessor.getOpcode() == Opcode.CONST_CLASS) {

                        String serviceName = ((Instruction21c) predecessor).getReference().toString();

                        // track as a side effect that the service was invoked through startService()
                        Optional<Component> component = getComponentByName(components, serviceName);

                        if (component.isPresent()) {
                            Service service = (Service) component.get();
                            service.setStarted(true);

                            // return the full-qualified name of the constructor
                            return service.getName() + "-><init>()V";
                        }
                    } else {
                        if (analyzedInstruction.getPredecessors().isEmpty()) {
                            // there is no predecessor -> target activity name might be defined somewhere else or external
                            return null;
                        } else {
                            // TODO: may use recursive search over all predecessors
                            pred = pred.getPredecessors().first();
                        }
                    }
                }
            } else if (method.equals("bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z")) {

                LOGGER.debug("Backtracking bindService() invocation!");

                /*
                 * We need to perform backtracking and extract the service name from the intent. A typical call
                 * looks as follows:
                 *
                 * invoke-virtual {p0, v0, v1, v2}, Lcom/base/myapplication/MainActivity;
                 *                       ->bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z
                 *
                 * The intent object is the first (explicit) parameter and refers to v0 in above case. Typically,
                 * the intent is generated locally and we are able to extract the service name by looking for the
                 * last const-class instruction, which is handed over to the intent constructor as parameter.
                 * In addition, we also look for the service connection object that is used. This refers to v1
                 * in the above example. Typically, v1 is set as follows:
                 *
                 * iget-object v1, p0, Lcom/base/myapplication/MainActivity;
                 *                       ->serviceConnection:Lcom/base/myapplication/MainActivity$MyServiceConnection;
                 *
                 * NOTE: In a typical scenario, we first encounter the iget-object instruction in order to derive the
                 * service connection object, and afterwards the service object itself.
                 */

                if (analyzedInstruction.getPredecessors().isEmpty()) {
                    // there is no predecessor -> all arguments of the invoke call are method parameters
                    return null;
                }

                String serviceConnection = null;

                // go back until we find const-class instruction which holds the service name
                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                while (pred.getInstructionIndex() != -1) {
                    Instruction predecessor = pred.getInstruction();

                    if (predecessor.getOpcode() == Opcode.CONST_CLASS) {

                        String serviceName = ((Instruction21c) predecessor).getReference().toString();
                        Optional<Component> component = getComponentByName(components, serviceName);

                        if (component.isPresent()) {
                            Service service = (Service) component.get();
                            service.setBound(true);

                            if (serviceConnection != null) {
                                service.setServiceConnection(serviceConnection);
                            }
                            return service.getDefaultConstructor();
                        }
                    } else if (predecessor.getOpcode() == Opcode.IGET_OBJECT) {
                        // TODO: check that instruction sets the register declared in the invoke instruction
                        Instruction22c serviceConnectionObject = ((Instruction22c) predecessor);
                        serviceConnection = Utility.getObjectType(serviceConnectionObject.getReference().toString());
                    }

                    // consider next predecessor if available
                    if (analyzedInstruction.getPredecessors().isEmpty()) {
                        return null;
                    } else {
                        pred = pred.getPredecessors().first();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the object type of a given reference. A typical reference looks as follows:
     * <p>
     * Lcom/base/application/MainActivity;->serviceConnection:Lcom/base/application/MainActivity$MyServiceConnection;
     * <p>
     * The above example represents a reference to the instance variable 'serviceConnection' of the 'MainActivity'
     * class, where the object type (class) is 'MyServiceConnection'.
     *
     * @param reference The reference.
     * @return Returns the object type (class) of the reference.
     */
    public static String getObjectType(String reference) {
        return reference.split(":")[1];
    }

    /**
     * Returns the method signature of the default constructor for a given class.
     *
     * @param clazz The name of the class.
     * @return Returns the default constructor signature.
     */
    public static String getDefaultConstructor(String clazz) {
        if (Utility.isInnerClass(clazz)) {
            String outerClass = Utility.getOuterClass(clazz);
            return clazz + "-><init>(" + outerClass + ")V";
        } else {
            return clazz + "-><init>()V";
        }
    }

    /**
     * Returns the component that has the given name.
     *
     * @param components    The set of components.
     * @param componentName The name of the component we search for.
     * @return Returns the component matching the given name.
     */
    public static Optional<Component> getComponentByName(final Set<Component> components, String componentName) {
        return components.stream().filter(c -> c.getName().equals(componentName)).findFirst();
    }

    /**
     * Retrieves the class name of the method's defining class.
     *
     * @param methodSignature The given method signature.
     * @return Returns the class name.
     */
    public static String getClassName(final String methodSignature) {
        return methodSignature.split("->")[0];
    }

    // TODO: Check whether there is any difference to method.toString()!

    /**
     * Derives a unique method signature in order to avoid
     * name clashes originating from overloaded/inherited methods
     * or methods in different classes.
     *
     * @param method The method to derive its method signature.
     * @return Returns the method signature of the given {@param method}.
     */
    public static String deriveMethodSignature(final Method method) {

        String className = method.getDefiningClass();
        String methodName = method.getName();
        List<? extends MethodParameter> parameters = method.getParameters();
        String returnType = method.getReturnType();

        StringBuilder builder = new StringBuilder();
        builder.append(className);
        builder.append("->");
        builder.append(methodName);
        builder.append("(");

        for (MethodParameter param : parameters) {
            builder.append(param.getType());
        }

        builder.append(")");
        builder.append(returnType);
        return builder.toString();
    }

    public static Optional<DexFile> containsTargetMethod(final List<DexFile> dexFiles, final String methodSignature) {

        String className = methodSignature.split("->")[0];

        for (DexFile dexFile : dexFiles) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (classDef.toString().equals(className)) {
                    for (Method method : classDef.getMethods()) {
                        if (Utility.deriveMethodSignature(method).equals(methodSignature)) {
                            return Optional.of(dexFile);
                        }
                    }
                    // speed up
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether the given instruction refers to an if or goto instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a branch or goto instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isJumpInstruction(final AnalyzedInstruction analyzedInstruction) {
        return isBranchingInstruction(analyzedInstruction) || isGotoInstruction(analyzedInstruction);
    }

    /**
     * Checks whether the given instruction refers to an parse-switch or packed-switch payload instruction.
     * These instructions are typically located at the end of a method after the return statement, thus
     * being isolated.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a parse-switch or packed-switch instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isSwitchPayloadInstruction(final AnalyzedInstruction analyzedInstruction) {
        // TODO: may handle the actual parse-switch and packed-switch instructions (not the payload instructions)
        // https://stackoverflow.com/questions/19855800/difference-between-packed-switch-and-sparse-switch-dalvik-opcode
        EnumSet<Opcode> opcodes = EnumSet.of(Opcode.PACKED_SWITCH_PAYLOAD, Opcode.SPARSE_SWITCH_PAYLOAD);
        if (opcodes.contains(analyzedInstruction.getInstruction().getOpcode())) {
            LOGGER.debug("Sparse/Packed-switch payload instruction at index: " + analyzedInstruction.getInstructionIndex());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether the given instruction refers to an parse-switch or packed-switch instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a parse-switch or packed-switch instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isSwitchInstruction(final AnalyzedInstruction analyzedInstruction) {
        // https://stackoverflow.com/questions/19855800/difference-between-packed-switch-and-sparse-switch-dalvik-opcode
        EnumSet<Opcode> opcodes = EnumSet.of(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH);
        if (opcodes.contains(analyzedInstruction.getInstruction().getOpcode())) {
            LOGGER.debug("Sparse/Packed-switch instruction at index: " + analyzedInstruction.getInstructionIndex());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether the given instruction refers to a goto instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a goto instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isGotoInstruction(final AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        EnumSet<Format> gotoInstructions = EnumSet.of(Format.Format10t, Format.Format20t, Format.Format30t);
        return gotoInstructions.contains(instruction.getOpcode().format);
    }

    /**
     * Checks whether the given instruction refers to an if instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a branching instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isBranchingInstruction(final AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        EnumSet<Format> branchingInstructions = EnumSet.of(Format.Format21t, Format.Format22t);
        return branchingInstructions.contains(instruction.getOpcode().format);
    }

    /**
     * Checks whether the given instruction refers to a return or throw statement.
     *
     * @param instruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is a return or throw statement,
     * otherwise {@code false} is returned.
     */
    public static boolean isTerminationStatement(final AnalyzedInstruction instruction) {
        // TODO: should we handle the throw-verification-error instruction?
        return isReturnStatement(instruction) || instruction.getInstruction().getOpcode() == Opcode.THROW;
    }

    /**
     * Checks whether the given instruction refers to a return statement.
     *
     * @param analyzedInstruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is a return statement, otherwise
     * {@code false} is returned.
     */
    public static boolean isReturnStatement(final AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        EnumSet<Opcode> returnStmts = EnumSet.of(Opcode.RETURN, Opcode.RETURN_WIDE, Opcode.RETURN_OBJECT,
                Opcode.RETURN_VOID, Opcode.RETURN_VOID_BARRIER, Opcode.RETURN_VOID_NO_BARRIER);
        return returnStmts.contains(instruction.getOpcode());
    }

    /**
     * Convenient function to get the list of {@code AnalyzedInstruction} of a certain target method.
     *
     * @param dexFile The dex file containing the target method.
     * @param method  The target method.
     * @return Returns a list of {@code AnalyzedInstruction} included in the target method.
     */
    public static List<AnalyzedInstruction> getAnalyzedInstructions(final DexFile dexFile, final Method method) {

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), method,
                null, false);

        return analyzer.getAnalyzedInstructions();
    }

    /**
     * Searches for a target method in the given {@code dexFile}.
     *
     * @param dexFile         The dexFile to search in.
     * @param methodSignature The signature of the target method.
     * @return Returns an optional containing either the target method or not.
     */
    public static Optional<Method> searchForTargetMethod(final DexFile dexFile, final String methodSignature) {

        // TODO: search for target method based on className + method signature
        String className = methodSignature.split("->")[0];

        Set<? extends ClassDef> classes = dexFile.getClasses();

        // search for target method
        for (ClassDef classDef : classes) {
            if (classDef.toString().equals(className)) {
                for (Method method : classDef.getMethods()) {
                    if (Utility.deriveMethodSignature(method).equals(methodSignature)) {
                        return Optional.of(method);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether the given method signature represents an inner class invocation.
     *
     * @param methodSignature The method signature to be checked against.
     * @return Returns {@code true} if the method signature refers to a inner class invocation,
     * otherwise {@code false} is returned.
     */
    public static boolean isInnerClass(final String methodSignature) {
        return methodSignature.contains("$");
    }

    /**
     * Returns the outer class name from the given class.
     * Should be only called in case {@link #isInnerClass(String)} returns {@code true}.
     *
     * @param className The class name to be checked.
     * @return Returns the outer class name.
     */
    public static String getOuterClass(final String className) {
        assert isInnerClass(className);
        return className.split("\\$")[0] + ";";
    }

    @SuppressWarnings("unused")
    public static MethodAnalyzer getAnalyzer(final DexFile dexFile, final Method targetMethod) {

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), targetMethod,
                null, false);
        return analyzer;
    }

    /**
     * Generates patterns of classes which should be excluded from the instrumentation.
     *
     * @return The pattern representing classes that should not be instrumented.
     */
    public static Pattern readExcludePatterns() {

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(EXCLUSION_PATTERN_FILE);

        if (inputStream == null) {
            LOGGER.warn("Couldn't find exclusion file!");
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        StringBuilder builder = new StringBuilder();

        try {
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first)
                    first = false;
                else
                    builder.append("|");
                builder.append(line);
            }
            reader.close();
        } catch (IOException e) {
            LOGGER.error("Couldn't read from exclusion file!");
            e.printStackTrace();
            return null;
        }
        return Pattern.compile(builder.toString());
    }

    /**
     * Transforms a class name containing '/' into a class name with '.'
     * instead, and removes the leading 'L' as well as the ';' at the end.
     *
     * @param className The class name which should be transformed.
     * @return The transformed class name.
     */
    public static String dottedClassName(String className) {

        if (className.startsWith("[")) {
            // array type
            int index = className.indexOf('L');
            if (index == -1) {
                // primitive array type, e.g [I or [[I
                return className;
            } else {
                // complex array type, e.g. [Ljava/lang/Integer;
                int beginClassName = className.indexOf('L');
                className = className.substring(0, beginClassName)
                        + className.substring(beginClassName + 1, className.indexOf(';'));
                className = className.replace('/', '.');
                return className;
            }
        } else {
            className = className.substring(className.indexOf('L') + 1, className.indexOf(';'));
            className = className.replace('/', '.');
            return className;
        }
    }

    /**
     * Checks whether the given class name represents an array type, e.g. [Ljava/lang/Integer;.
     * Handles both dalvik and java based class names.
     *
     * @param className The class name to be checked.
     * @return Returns {@code true} if the class name refers to an array type,
     * otherwise {@code false} is returned.
     */
    public static boolean isArrayType(final String className) {
        return className.startsWith("[");
    }

    /**
     * Converts a vertex into a DOT node matching the dot language specification.
     *
     * @param vertex The vertex to be converted.
     * @return Returns a DOT node representing the given vertex.
     */
    public static String convertVertexToDOTNode(final Vertex vertex) {

        /*
         * TODO: Simplify the entire conversion process. We need to comply to the following specification:
         *  https://graphviz.org/doc/info/lang.html. In particular, this holds true for the node identifiers,
         *  while the label can be assigned any name.
         */

        String methodSignature = vertex.getMethod();

        if (methodSignature.equals("global")) {
            if (vertex.isEntryVertex()) {
                return "entry_" + methodSignature;
            } else {
                return "exit_" + methodSignature;
            }
        }

        if (methodSignature.startsWith("callbacks")) {
            String className = methodSignature.split("callbacks")[1].trim();
            if (vertex.isEntryVertex()) {
                return "<entry_callbacks_" + className + ">";
            } else {
                return "<exit_callbacks_" + className + ">";
            }
        }

        String className = Utility.dottedClassName(Utility.getClassName(methodSignature))
                .replace("$", "_").replace(".", "_");
        String method = Utility.getMethodName(methodSignature).split("\\(")[0];
        method = method.replace("<init>", "ctr");

        String label = "";
        String signature = className + "_" + method;

        if (vertex.isEntryVertex()) {
            label = "entry_" + signature;
        } else if (vertex.isExitVertex()) {
            label = "exit_" + signature;
        } else if (vertex.isReturnVertex()) {
            label = "return_" + signature;
        } else {
            BlockStatement blockStatement = (BlockStatement) vertex.getStatement();
            Statement firstStmt = blockStatement.getFirstStatement();
            Statement lastStmt = blockStatement.getLastStatement();
            Integer begin = null;
            Integer end = null;

            if (firstStmt instanceof ReturnStatement) {

                if (blockStatement.getStatements().size() == 1) {
                    // isolated return vertex, no begin and end index
                    return "<return_" + signature + ">";
                }

                BasicStatement second = (BasicStatement) blockStatement.getStatements().get(1);
                begin = second.getInstructionIndex();

            } else {
                // must be a basic block statement
                begin = ((BasicStatement) firstStmt).getInstructionIndex();
            }

            end = ((BasicStatement) lastStmt).getInstructionIndex();
            label = signature + "_" + begin + "_" + end;
        }

        // DOT supports html tags and those seem to require little to no escaping inside
        return "<" + label + ">";
    }

    /**
     * Converts a vertex into a DOT label. This label is what we see when
     * we render the graph.
     *
     * @param vertex The vertex to be converted into a DOT label.
     * @return Returns the DOT label representing the given vertex.
     */
    public static String convertVertexToDOTLabel(final Vertex vertex) {

        // TODO: Display the instructions actually instead of solely the instruction indices.
        String label = "";
        String methodSignature = vertex.getMethod();

        if (methodSignature.equals("global")) {
            if (vertex.isEntryVertex()) {
                label = "entry_global";
            } else {
                label = "exit_global";
            }
        } else if (methodSignature.startsWith("callbacks")) {
            if (vertex.isEntryVertex()) {
                label = "entry_callbacks";
            } else {
                label = "exit_callbacks";
            }
        } else {

            String className = Utility.dottedClassName(Utility.getClassName(methodSignature));
            String method = Utility.getMethodName(methodSignature).split("\\(")[0];
            String signature = "<" + className + "_" + method + ">";

            if (vertex.isEntryVertex()) {
                label = "entry_" + signature;
            } else if (vertex.isExitVertex()) {
                label = "exit_" + signature;
            } else if (vertex.isReturnVertex()) {
                label = "return_" + signature;
            } else {
                BlockStatement blockStatement = (BlockStatement) vertex.getStatement();
                Statement firstStmt = blockStatement.getFirstStatement();
                Statement lastStmt = blockStatement.getLastStatement();
                Integer begin = null;
                Integer end = null;

                if (firstStmt instanceof ReturnStatement) {

                    if (blockStatement.getStatements().size() == 1) {
                        // isolated return statement
                        label = "return_" + signature;
                    } else {
                        BasicStatement second = (BasicStatement) blockStatement.getStatements().get(1);
                        begin = second.getInstructionIndex();
                    }
                } else {
                    // must be a basic block statement
                    begin = ((BasicStatement) firstStmt).getInstructionIndex();
                }

                if (blockStatement.getStatements().size() > 1) {
                    // only check for last index if vertex is not an isolated return vertex
                    end = ((BasicStatement) lastStmt).getInstructionIndex();
                    label = signature + "_" + begin + "_" + end;
                }
            }
        }
        return label;
    }

    /**
     * Checks whether a block statement contains an invoke instruction.
     *
     * @param blockStatement The block statement to be checked.
     * @return Returns {@code true} if the block statement contains an
     * invoke instruction, otherwise {@code false} is returned.
     */
    public static boolean containsInvoke(final BlockStatement blockStatement) {

        for (Statement statement : blockStatement.getStatements()) {
            if (statement instanceof BasicStatement) {
                if (Utility.isInvokeInstruction(((BasicStatement) statement).getInstruction())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks whether the given instruction is any sort of invoke statement.
     *
     * @param analyzedInstruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is an invoke statement,
     * otherwise {@code false} is returned.
     */
    public static boolean isInvokeInstruction(final AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        return INVOKE_OPCODES.contains(instruction.getOpcode());
    }

    /**
     * Checks whether the given class represents the dynamically generated BuildConfig class.
     *
     * @param classDef The class to be checked.
     * @return Returns {@code true} if the given class represents the dynamically generated
     * BuildConfig class, otherwise {@code false} is returned.
     */
    public static boolean isBuildConfigClass(final ClassDef classDef) {
        String className = Utility.dottedClassName(classDef.toString());
        return className.endsWith("BuildConfig");
    }

    /**
     * Checks whether the given class represents the dynamically generated R class or any
     * inner class of it.
     *
     * @param classDef The class to be checked.
     * @return Returns {@code true} if the given class represents the R class or any
     * inner class of it, otherwise {@code false} is returned.
     */
    public static boolean isResourceClass(final ClassDef classDef) {

        String className = Utility.dottedClassName(classDef.toString());
        String[] tokens = className.split("\\.");

        // check whether it is the R class itself
        if (tokens[tokens.length - 1].equals("R")) {
            return true;
        }

        // check for inner R classes
        for (String resourceClass : RESOURCE_CLASSES) {
            if (className.contains(resourceClass)) {
                return true;
            }
        }

        // TODO: can be removed, just for illustration how to process annotations
        /*
        Set<? extends Annotation> annotations = classDef.getAnnotations();

        for (Annotation annotation : annotations) {

            // check if the enclosing class is the R class
            if (annotation.getType().equals("Ldalvik/annotation/EnclosingClass;")) {
                for (AnnotationElement annotationElement : annotation.getElements()) {
                    if (annotationElement.getValue() instanceof DexBackedTypeEncodedValue) {
                        DexBackedTypeEncodedValue value = (DexBackedTypeEncodedValue) annotationElement.getValue();
                        if (value.getValue().equals("Landroidx/appcompat/R;")) {
                            return true;
                        }
                    }
                }
            }
        }
         */
        return false;
    }

    /**
     * Checks whether the given method is an (inherited) ART method, e.g. startActivity().
     *
     * @param fullyQualifiedMethodName The method signature.
     * @return Returns {@code true} if the given method is an ART method,
     * otherwise {@code false}.
     */
    public static boolean isARTMethod(final String fullyQualifiedMethodName) {
        // TODO: add further patterns, e.g.:
        // getSupportFragmentManager()
        // setContentView()
        // startService()
        String method = getMethodName(fullyQualifiedMethodName);
        return ART_METHODS.contains(method);
    }

    /**
     * Checks whether the given class represents an activity by checking against the super class.
     *
     * @param classes      The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is an activity,
     * otherwise {@code false}.
     */
    public static boolean isActivity(final List<ClassDef> classes, final ClassDef currentClass) {

        // TODO: this approach might be quite time-consuming, may find a better solution

        String superClass = currentClass.getSuperclass();
        boolean abort = false;

        while (!abort && superClass != null && !superClass.equals("Ljava/lang/Object;")) {

            abort = true;

            if (ACTIVITY_CLASSES.contains(superClass)) {
                return true;
            } else {
                // step up in the class hierarchy
                for (ClassDef classDef : classes) {
                    if (classDef.toString().equals(superClass)) {
                        superClass = classDef.getSuperclass();
                        abort = false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given class represents a fragment by checking against the super class.
     *
     * @param classes      The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is a fragment,
     * otherwise {@code false}.
     */
    public static boolean isFragment(final List<ClassDef> classes, final ClassDef currentClass) {

        // TODO: this approach might be quite time-consuming, may find a better solution

        String superClass = currentClass.getSuperclass();
        boolean abort = false;

        while (!abort && superClass != null && !superClass.equals("Ljava/lang/Object;")) {

            abort = true;

            if (FRAGMENT_CLASSES.contains(superClass)) {
                return true;
            } else {
                // step up in the class hierarchy
                for (ClassDef classDef : classes) {
                    if (classDef.toString().equals(superClass)) {
                        superClass = classDef.getSuperclass();
                        abort = false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Convenient function to construct an intraCFG. Should be used
     * for the construction requested by mate server.
     *
     * @param apkPath        The path to the APK file.
     * @param method         The FQN name of the method.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @return Returns an intraCFG for the specified method.
     */
    public static BaseCFG constructIntraCFG(final File apkPath, String method, final boolean useBasicBlocks) {

        MultiDexContainer<? extends DexBackedDexFile> apk = null;

        try {
            apk = DexFileFactory.loadDexContainer(apkPath, API_OPCODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DexFile> dexFiles = new ArrayList<>();
        List<String> dexEntries = new ArrayList<>();

        try {
            dexEntries = apk.getDexEntryNames();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String dexEntry : dexEntries) {
            try {
                dexFiles.add(apk.getEntry(dexEntry).getDexFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                .withName(method);

        if (useBasicBlocks) {
            builder = builder.withBasicBlocks();
        }

        BaseGraph baseGraph = builder.build();
        return (BaseCFG) baseGraph;
    }

    /**
     * Convenient function to construct an interCFG. Should be used
     * for the construction requested by mate server.
     *
     * @param apkPath           The path to the APK file.
     * @param useBasicBlocks    Whether to use basic blocks or not.
     * @param excludeARTClasses Whether to exclude ART classes or not.
     * @return Returns an interCFG.
     */
    public static BaseCFG constructInterCFG(final File apkPath, final boolean useBasicBlocks,
                                            final boolean excludeARTClasses) {

        MultiDexContainer<? extends DexBackedDexFile> apk = null;

        try {
            apk = DexFileFactory.loadDexContainer(apkPath, API_OPCODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DexFile> dexFiles = new ArrayList<>();
        List<String> dexEntries = new ArrayList<>();

        try {
            dexEntries = apk.getDexEntryNames();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String dexEntry : dexEntries) {
            try {
                dexFiles.add(apk.getEntry(dexEntry).getDexFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTERCFG, dexFiles)
                .withName("global")
                .withAPKFile(apkPath);

        if (useBasicBlocks) {
            builder = builder.withBasicBlocks();
        }

        if (excludeARTClasses) {
            builder = builder.withExcludeARTClasses();
        }

        BaseGraph baseGraph = builder.build();
        return (BaseCFG) baseGraph;
    }

    /**
     * Checks whether the given class represents a service by checking against the super class.
     *
     * @param classes      The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is a service,
     * otherwise {@code false} is returned.
     */
    public static boolean isService(final List<ClassDef> classes, final ClassDef currentClass) {

        // TODO: this approach might be quite time-consuming, may find a better solution

        String superClass = currentClass.getSuperclass();
        boolean abort = false;

        while (!abort && superClass != null && !superClass.equals("Ljava/lang/Object;")) {

            abort = true;

            if (SERVICE_CLASSES.contains(superClass)) {
                return true;
            } else {
                // step up in the class hierarchy
                for (ClassDef classDef : classes) {
                    if (classDef.toString().equals(superClass)) {
                        superClass = classDef.getSuperclass();
                        abort = false;
                    }
                }
            }
        }
        return false;
    }
}
