package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ComponentUtils {

    private static final Logger LOGGER = LogManager.getLogger(ComponentUtils.class);

    /**
     * The recognized activity classes.
     */
    private static final Set<String> ACTIVITY_CLASSES = new HashSet<>() {{
        add("Landroid/app/Activity;");
        add("Landroidx/appcompat/app/AppCompatActivity;");
        add("Landroid/support/v7/app/AppCompatActivity;");
        add("Landroid/support/v7/app/ActionBarActivity;");
        add("Landroid/support/v4/app/FragmentActivity;");
        add("Landroid/preference/PreferenceActivity;");
        add("Landroid/app/ListActivity;");
        add("Landroid/app/TabActivity;");
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
        add("Landroidx/preference/PreferenceFragment;");
        add("Landroid/webkit/WebViewFragment;");
        add("Landroidx/fragment/app/DialogFragment;");
        add("Landroidx/fragment/app/ListFragment;");
        add("Landroidx/preference/PreferenceFragmentCompat;");
        add("Landroidx/appcompat/app/AppCompatDialogFragment;");
        add("Landroidx/preference/PreferenceDialogFragmentCompat;");
        add("Landroidx/mediarouter/app/MediaRouteChooserDialogFragment;");
        add("Landroidx/mediarouter/app/MediaRouteControllerDialogFragment;");
    }};

    /**
     * The recognized service classes, see https://developer.android.com/reference/android/app/Service.
     */
    private static final Set<String> SERVICE_CLASSES = new HashSet<>() {{
        add("Landroid/app/Service;");
        add("Landroid/app/IntentService;");
        add("Landroid/widget/RemoteViewsService;");
        add("Landroid/app/job/JobService;");
    }};

    /**
     * The recognized binder classes, see https://developer.android.com/reference/android/os/Binder.
     */
    private static final Set<String> BINDER_CLASSES = new HashSet<>() {{
        add("Landroid/os/Binder;");
    }};

    /**
     * The recognized receiver classes, see https://developer.android.com/reference/android/content/BroadcastReceiver.
     */
    private static final Set<String> BROADCAST_RECEIVER_CLASSES = new HashSet<>() {{
        // TODO: add further directly known sub classes
        // https://developer.android.com/reference/android/content/BroadcastReceiver
        add("Landroid/content/BroadcastReceiver;");
        // https://developer.android.com/reference/android/appwidget/AppWidgetProvider
        add("Landroid/appwidget/AppWidgetProvider;");
    }};

    /**
     * The various API methods to invoke a component, e.g. an activity.
     */
    private static final Set<String> COMPONENT_INVOCATIONS = new HashSet<>() {{
        add("startActivity(Landroid/content/Intent;)V");
        add("startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V");
        add("startActivityForResult(Landroid/content/Intent;I)V");
        add("startActivityForResult(Landroid/content/Intent;ILandroid/os/Bundle;)V");
        add("startService(Landroid/content/Intent;)Landroid/content/ComponentName;");
        add("bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z");
    }};

    private ComponentUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Checks whether the given method refers to the invocation of a component, e.g. an activity.
     *
     * @param components The set of recognized components.
     * @param fullyQualifiedMethodName The method to be checked against.
     * @return Returns {@code true} if method refers to the invocation of a component,
     *         otherwise {@code false} is returned.
     */
    public static boolean isComponentInvocation(final Set<Component> components, final String fullyQualifiedMethodName) {

        String clazz = MethodUtils.getClassName(fullyQualifiedMethodName);
        String method = MethodUtils.getMethodName(fullyQualifiedMethodName);

        // component invocations require a context object, this can be the application context or a component
        return (COMPONENT_INVOCATIONS.contains(method)
                && (clazz.equals("Landroid/content/Context;")
                || components.stream().map(Component::getName).anyMatch(name -> name.equals(clazz))))
                || method.startsWith("startActivityForResultWithAnimation") // TODO temporary fix for com.ichi2.anki
                || fullyQualifiedMethodName.equals("Landroid/widget/TabHost$TabSpec;->" +
                "setContent(Landroid/content/Intent;)Landroid/widget/TabHost$TabSpec;");
    }

    /**
     * Checks whether the given instruction refers to the invocation of a component.
     * A component is an activity or service for instance.
     * Only call this method when isComponentInvocation() returns {@code true}.
     *
     * @param components The set of recognized components.
     * @param analyzedInstruction The given instruction.
     * @return Returns the constructor name of the target component if the instruction
     *         refers to a component invocation, otherwise {@code null}.
     */
    public static String isComponentInvocation(final Set<Component> components,
                                               final AnalyzedInstruction analyzedInstruction) {

        // check for invoke/invoke-range instruction
        if (InstructionUtils.isInvokeInstruction(analyzedInstruction)) {

            Instruction instruction = analyzedInstruction.getInstruction();
            String invokeTarget = ((ReferenceInstruction) instruction).getReference().toString();
            String method = MethodUtils.getMethodName(invokeTarget);

            if (method.equals("startActivity(Landroid/content/Intent;)V")
                    || method.equals("startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V")
                    || method.equals("startActivityForResult(Landroid/content/Intent;I)V")
                    || method.equals("startActivityForResult(Landroid/content/Intent;ILandroid/os/Bundle;)V")
                    || method.startsWith("startActivityForResultWithAnimation") // TODO temporary fix for com.ichi2.anki
                    || invokeTarget.equals("Landroid/widget/TabHost$TabSpec;->setContent(Landroid/content/Intent;)Landroid/widget/TabHost$TabSpec;")) {

                LOGGER.debug("Backtracking startActivity()/startActivityForResult() invocation!");

                if (analyzedInstruction.getPredecessors().isEmpty()) {
                    // there is no predecessor -> target activity name might be defined somewhere else or external
                    return null;
                }

                // go back until we find const-class / static invocation instruction which holds the activity name
                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                // TODO: Consider the used register when backtracking instructions!

                // TODO: check that we don't miss activities, go back recursively if there are several predecessors
                // upper bound to avoid resolving external activities or activities defined in a different method
                while (pred.getInstructionIndex() != -1) {
                    final Instruction predecessor = pred.getInstruction();
                    if (predecessor.getOpcode() == Opcode.CONST_CLASS) {
                        // This is the most common pattern - the activity name is encoded in the const-class instruction.

                        String activityName = ((Instruction21c) predecessor).getReference().toString();
                        Optional<Component> activity = getComponentByName(components, activityName);

                        if (activity.isPresent()) {
                            // return the full-qualified name of the constructor
                            return activity.get().getDefaultConstructor();
                        }
                    } else if (predecessor.getOpcode() == Opcode.INVOKE_STATIC) {
                        /*
                        * In some cases the intent for startActivity() is supplied by the target activity through a
                        * static method. We can retrieve then the activity name from the static invocation, e.g.,
                        * consider the following example:
                        *
                        * Intent intent = TargetActivity.newIntent(CurrentActivity.this);
                        * startActivity(intent);
                         */
                        String reference = ((ReferenceInstruction) pred.getInstruction()).getReference().toString();
                        String activityName = reference.split("->")[0];
                        Optional<Component> activity = getComponentByName(components, activityName);
                        boolean returnTypeIsIntent = reference.endsWith("Landroid/content/Intent;");
                        if (activity.isPresent() && returnTypeIsIntent) {
                            return activity.get().getDefaultConstructor();
                        }
                    }

                    if (pred.getPredecessors().isEmpty()) {
                        // there is no predecessor -> target activity name might be defined somewhere else or external
                        return null;
                    } else {
                        // TODO: may use recursive search over all predecessors
                        pred = pred.getPredecessors().first();
                    }
                }

            } else if (method.equals("bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z")
                    || method.equals("startService(Landroid/content/Intent;)Landroid/content/ComponentName;")) {

                LOGGER.debug("Backtracking startService()/bindService() invocation!");

                /*
                 * We need to perform backtracking and extract the service name from the intent. A typical call
                 * looks as follows:
                 *
                 * invoke-virtual {p0, v0, v1, v2}, Lcom/base/myapplication/MainActivity;
                 *                       ->bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z
                 *
                 * The intent object is hold in the register v0 for the above example. Typically, the intent is
                 * generated locally and the service name can be extracted from the 'last' const-class instruction.
                 * The same procedure is also applicable for startService() invocations.
                 */

                if (analyzedInstruction.getPredecessors().isEmpty()) {
                    // service name might be hold in some parameter register
                    return null;
                }

                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                while (pred.getInstructionIndex() != -1) {
                    Instruction predecessor = pred.getInstruction();

                    // the const-class instruction is typically holding the service name
                    if (predecessor.getOpcode() == Opcode.CONST_CLASS) {

                        String serviceName = ((Instruction21c) predecessor).getReference().toString();
                        Optional<Component> component = getComponentByName(components, serviceName);

                        if (component.isPresent() && component.get() instanceof Service) {
                            Service service = (Service) component.get();
                            return service.getDefaultConstructor();
                        }
                    }

                    // consider next predecessor if available
                    if (pred.getPredecessors().isEmpty()) {
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
     * Returns the component that has the given name.
     *
     * @param components The set of components.
     * @param componentName The name of the component we search for.
     * @return Returns the component matching the given name.
     */
    public static Optional<Component> getComponentByName(final Set<Component> components, String componentName) {
        return components.stream().filter(c -> c.getName().equals(componentName)).findFirst();
    }

    /**
     * Returns the activity that has the given name.
     *
     * @param components The set of components.
     * @param componentName The name of the activity we search for.
     * @return Returns the activity matching the given name if present.
     */
    public static Optional<Activity> getActivityByName(final Set<Component> components, String componentName) {
        return getComponentByName(components, componentName)
                .flatMap(c -> c instanceof Activity ? Optional.of((Activity) c) : Optional.empty());
    }

    /**
     * Returns the fragment that has the given name.
     *
     * @param components The set of components.
     * @param componentName The name of the fragment we search for.
     * @return Returns the fragment matching the given name if present.
     */
    public static Optional<Fragment> getFragmentByName(final Set<Component> components, String componentName) {
        return getComponentByName(components, componentName)
                .flatMap(c -> c instanceof Fragment ? Optional.of((Fragment) c) : Optional.empty());
    }

    /**
     * Checks whether the given class represents an activity by checking against the super class.
     *
     * @param classes The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is an activity, otherwise {@code false} is returned.
     */
    public static boolean isActivity(final List<ClassDef> classes, final ClassDef currentClass) {

        if (Arrays.stream(AccessFlags.getAccessFlagsForClass(currentClass.getAccessFlags()))
                .anyMatch(flag -> flag == AccessFlags.ABSTRACT)) {
            /*
             * We can ignore abstract activity classes, it doesn't make sense to resolve them.
             */
            return false;
        }

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
     * @param classes The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is a fragment, otherwise {@code false}.
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
     * Checks whether the given class represents a service by checking against the super class.
     *
     * @param classes The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is a service, otherwise {@code false} is returned.
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

    /**
     * Checks whether the given class represents a binder class by checking against the super class.
     *
     * @param classes The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is a binder class, otherwise {@code false} is returned.
     */
    public static boolean isBinder(final List<ClassDef> classes, final ClassDef currentClass) {

        String superClass = currentClass.getSuperclass();
        boolean abort = false;

        while (!abort && superClass != null && !superClass.equals("Ljava/lang/Object;")) {

            abort = true;

            if (BINDER_CLASSES.contains(superClass)) {
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
     * Check for relations between the given components. This is a mere heuristic.
     * In particular, we define a relation between two components in following cases:
     * <p>
     * (1) There is a field reference to another application class.
     * (2) There is a method parameter reference to another application class.
     * (3) If the method return value represents a reference to another application class.
     * (4) If an instruction, e.g. invoke, references another application class.
     * (5) If there is an outer to inner class relation.
     * <p>
     * NOTE: Right now only direct relations between activities and fragments are saved.
     *
     * @param apk The APK file.
     * @param components The set of discovered components.
     * @param classHierarchy The derived class hierarchy.
     */
    public static void checkComponentRelations(APK apk, final Set<Component> components,
                                               final ClassHierarchy classHierarchy) {

        LOGGER.debug("Checking component relations...");

        // provides a mapping which class makes use of which other class
        final Multimap<String, String> classUsages = ArrayListMultimap.create();

        final String applicationPackage = apk.getManifest().getPackageName();

        // consumes the class usages and checks for view pager fragment usages of activities
        final List<Consumer<Multimap<String, String>>> viewPagerFragmentUsages = new LinkedList<>();

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                // TODO: track usages defined by interfaces

                final String className = classDef.toString();

                if (!ClassUtils.dottedClassName(className).startsWith(applicationPackage)) {
                    // TODO: Find here a better heuristic!
                    // don't look at 3rd party classes
                    continue;
                }

                // an outer class has a direct relation to its inner class, e.g. ActivityA$FragmentA
                if (ClassUtils.isInnerClass(className)) {
                    final String outerClass = ClassUtils.getOuterClass(className);
                    // we detect such relation when we traverse the inner class (className)
                    classUsages.put(outerClass, className);
                }

                // check the references to other application classes
                for (Field field : classDef.getFields()) {
                    if (ClassUtils.dottedClassName(field.getType()).startsWith(applicationPackage)) {
                        if (!className.equals(field.getType())) {
                            classUsages.put(className, field.getType());
                        }
                    }
                }

                for (Method method : classDef.getMethods()) {

                    final String fullyQualifiedMethodName = MethodUtils.deriveMethodSignature(method);

                    // check the method parameters for references
                    for (MethodParameter parameter : method.getParameters()) {
                        if (ClassUtils.dottedClassName(parameter.getType()).startsWith(applicationPackage)) {
                            if (!className.equals(parameter.getType())) {
                                classUsages.put(className, parameter.getType());
                            }
                        }
                    }

                    // check method return value
                    if (ClassUtils.dottedClassName(method.getReturnType()).startsWith(applicationPackage)) {
                        if (!className.equals(method.getReturnType())) {
                            classUsages.put(className, method.getReturnType());
                        }
                    }

                    // check instructions for references
                    if (method.getImplementation() != null) {

                        List<AnalyzedInstruction> analyzedInstructions
                                = MethodUtils.getAnalyzedInstructions(dexFile, method);

                        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {

                            Instruction instruction = analyzedInstruction.getInstruction();

                            if (instruction.getOpcode() == Opcode.NEW_INSTANCE) {
                                // check constructor call
                                final Instruction21c newInstance = (Instruction21c) instruction;
                                final String targetClass = newInstance.getReference().toString();
                                if (ClassUtils.dottedClassName(targetClass).startsWith(applicationPackage)) {
                                    if (!className.equals(targetClass)) {
                                        // don't track self references
                                        classUsages.put(className, targetClass);
                                    }
                                }
                            } else if (InstructionUtils.isInvokeInstruction(instruction)) {

                                // check for fragment invocation
                                FragmentUtils.checkForFragmentInvocation(components, fullyQualifiedMethodName,
                                        analyzedInstruction);

                                // check for fragment view pager usages
                                viewPagerFragmentUsages.add(FragmentUtils.checkForFragmentViewPager(components,
                                        fullyQualifiedMethodName, analyzedInstruction, classHierarchy));

                                // check for service invocation
                                ServiceUtils.checkForServiceInvocation(components, fullyQualifiedMethodName,
                                        analyzedInstruction);

                                // check for dynamic broadcast receiver registration
                                ReceiverUtils.checkForDynamicReceiverRegistration(components, analyzedInstruction);

                                final String invokeCall = ((ReferenceInstruction) instruction).getReference().toString();

                                // check defining class (called class)
                                final String definingClass = MethodUtils.getClassName(invokeCall);
                                if (ClassUtils.dottedClassName(definingClass).startsWith(applicationPackage)) {
                                    if (!className.equals(definingClass)) {
                                        // don't track self references
                                        classUsages.put(className, definingClass);
                                    }
                                }

                                if (ClassUtils.isInnerClass(definingClass)) {
                                    // every inner class has per default a relation to its outer class
                                    final String outerClass = ClassUtils.getOuterClass(definingClass);
                                    if (ClassUtils.dottedClassName(outerClass).startsWith(applicationPackage)) {
                                        if (!className.equals(outerClass)) {
                                            // don't track self references
                                            classUsages.put(className, outerClass);
                                        }
                                    }
                                }

                                // check return type
                                final String returnType = MethodUtils.getReturnType(invokeCall);
                                if (ClassUtils.dottedClassName(returnType).startsWith(applicationPackage)) {
                                    if (!className.equals(returnType)) {
                                        // don't track self references
                                        classUsages.put(className, returnType);
                                    }
                                }
                            } else if (instruction.getOpcode() == Opcode.CONST_CLASS) {
                                final Instruction21c constClass = (Instruction21c) instruction;
                                final String targetClass = constClass.getReference().toString();
                                if (ClassUtils.dottedClassName(targetClass).startsWith(applicationPackage)) {
                                    if (!className.equals(targetClass)) {
                                        // don't track self references
                                        classUsages.put(className, targetClass);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // derive the view pager fragment usages by consuming the class usages
        viewPagerFragmentUsages.forEach(viewPagerFragmentUsage -> viewPagerFragmentUsage.accept(classUsages));

        // derive relations between components based on the found class usages
        for (Component component : components) {

            Set<String> componentUsages = new HashSet<>(classUsages.get(component.getName()));

            // add transitive inner class usages (only direct usages of inner classes)
            componentUsages.addAll(componentUsages.stream()
                    .filter(ClassUtils::isInnerClass)
                    .filter(usage -> ClassUtils.getOuterClass(usage).equals(component.getName()))
                    .map(classUsages::get)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet()));

            LOGGER.debug("Component " + component + "has the following usages: " + componentUsages);

            for (String usage : componentUsages) {
                // TODO: handle transitive usages
                Optional<Component> optionalComponent = getComponentByName(components, usage);

                optionalComponent.ifPresent(c -> {
                    LOGGER.debug("Component " + component + " makes use of component: " + c);

                    if (component.getComponentType() == ComponentType.ACTIVITY
                            && c.getComponentType() == ComponentType.FRAGMENT) {
                        ((Activity) component).addHostingFragment((Fragment) c);
                    } else if (component.getComponentType() == ComponentType.FRAGMENT
                            && c.getComponentType() == ComponentType.ACTIVITY) {
                        /*
                         * TODO: Avoid defining the relation in the wrong direction.
                         * This can be either a fragment invoking an activity, in which case we shouldn't declare
                         * any relation, or a usage defined through an inner to outer class call, in which case
                         * the relation should be reversed.
                         */
                        LOGGER.debug("Fragment to activity relation: " + component + " -> " + c);
                    } else if (component.getComponentType() == ComponentType.FRAGMENT
                            && c.getComponentType() == ComponentType.FRAGMENT) {
                        /*
                         * TODO: Handle relation between fragments.
                         * This can be either a nested fragment or a usage caused through class inheritance.
                         */
                        LOGGER.debug("Fragment relation: " + component + " -> " + c);
                    }
                });
            }
        }
    }

    /**
     * Checks whether the given class represents an application class by checking against the super class.
     *
     * @param classes The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is an application class, otherwise {@code false}.
     */
    public static boolean isApplication(final List<ClassDef> classes, final ClassDef currentClass) {

        String superClass = currentClass.getSuperclass();
        boolean abort = false;

        while (!abort && superClass != null && !superClass.equals("Ljava/lang/Object;")) {

            abort = true;

            if (superClass.equals("Landroid/app/Application;")) {
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
     * Checks whether the given class represents a broadcast receiver by checking against the super class.
     *
     * @param classes The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is a broadcast receiver, otherwise {@code false}.
     */
    public static boolean isBroadcastReceiver(final List<ClassDef> classes, final ClassDef currentClass) {

        String superClass = currentClass.getSuperclass();
        boolean abort = false;

        while (!abort && superClass != null && !superClass.equals("Ljava/lang/Object;")) {

            abort = true;

            if (BROADCAST_RECEIVER_CLASSES.contains(superClass)) {
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
     * Retrieves the fragments that are not hosted by any activity.
     *
     * @param components The set of components.
     * @return Returns the set of fragments that are not hosted by any activity.
     */
    public static Set<Fragment> getFragmentsWithoutHost(Collection<Component> components) {
        return components.stream()
                .filter(component -> component instanceof Fragment)
                .map(c -> (Fragment) c)
                .filter(fragment -> components.stream()
                        .noneMatch(component -> component instanceof Activity && ((Activity) component)
                                .getHostingFragments().contains(fragment)))
                .collect(Collectors.toSet());
    }
}
