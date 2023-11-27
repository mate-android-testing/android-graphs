package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enables to search for usages of a class or method, respectively.
 */
public final class UsageSearch {

    private static final Logger LOGGER = LogManager.getLogger(UsageSearch.class);

    // caches requested class usages
    private static final Map<String, Set<Usage>> CACHE = new HashMap<>();

    private UsageSearch() {
        throw new UnsupportedOperationException("utility class!");
    }

    /**
     * The internal representation of a usage.
     */
    public static class Usage {

        /**
         * The class that makes use of the looked up class/method.
         */
        private final ClassDef clazz;

        /**
         * The method that makes use of the looked up class/method.
         */
        private final Method method;

        /**
         * The instruction that makes use of the looked up class/method.
         */
        private final AnalyzedInstruction instruction;

        public Usage(ClassDef clazz) {
            this(clazz, null, null);
        }

        public Usage(ClassDef clazz, Method method) {
            this(clazz, method, null);
        }

        public Usage(ClassDef clazz, Method method, AnalyzedInstruction instruction) {
            this.clazz = clazz;
            this.method = method;
            this.instruction = instruction;
        }

        public ClassDef getClazz() {
            if (clazz == null) {
                throw new IllegalStateException("Usage doesn't define any class attribute!");
            }
            return clazz;
        }

        public Method getMethod() {
            if (method == null) {
                throw new IllegalStateException("Usage doesn't define any method attribute!");
            }
            return method;
        }

        public AnalyzedInstruction getInstruction() {
            if (instruction == null) {
                throw new IllegalStateException("Usage doesn't define any instruction attribute!");
            }
            return instruction;
        }

        private String formatInstruction(AnalyzedInstruction instruction) {
            if (instruction == null) {
                return null;
            } else {
                return "(" + instruction.getOriginalInstruction().getOpcode() + ","
                        + instruction.getInstructionIndex() + ")";
            }
        }

        private String formatMethod(Method method) {
            if (method == null) {
                return null;
            } else {
                return MethodUtils.getMethodName(method.toString());
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Usage usage = (Usage) o;
            return Objects.equals(clazz, usage.clazz)
                    && Objects.equals(method, usage.method)
                    && Objects.equals(instruction, usage.instruction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, method, instruction);
        }

        @Override
        public String toString() {
            return "Usage{class=" + clazz + ", method=" + formatMethod(method)
                    + ", instruction=" + formatInstruction(instruction) + "}";
        }
    }

    /**
     * Finds the usages of the given method in the application package.
     *
     * @param apk The APK file.
     * @param targetMethod The method whose usages should be found.
     * @return Returns a set of usages of the given method.
     */
    @SuppressWarnings("unused")
    public static Set<Usage> findMethodUsages(APK apk, Method targetMethod) {

        LOGGER.debug("Find usages of method: " + targetMethod);
        final Set<Usage> usages = new HashSet<>();

        final String applicationPackage = apk.getManifest().getPackageName();
        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {
                final String dottedClassName = ClassUtils.dottedClassName(classDef.toString());
                if (dottedClassName.startsWith(applicationPackage)
                        || (mainActivityPackage != null && dottedClassName.startsWith(mainActivityPackage))) {
                    // only inspect application classes
                    for (Method method : classDef.getMethods()) {
                        MethodImplementation implementation = method.getImplementation();
                        if (implementation != null) {
                            List<AnalyzedInstruction> instructions = MethodUtils.getAnalyzedInstructions(dexFile, method);
                            for (AnalyzedInstruction instruction : instructions) {
                                if (InstructionUtils.isInvokeInstruction(instruction)) {
                                    final String invokeTarget = ((ReferenceInstruction) instruction.getInstruction())
                                            .getReference().toString();
                                    if (invokeTarget.equals(MethodUtils.deriveMethodSignature(targetMethod))) {
                                        Usage usage = new Usage(classDef, method, instruction);
                                        usages.add(usage);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        usages.forEach(LOGGER::debug);
        return usages;
    }

    /**
     * Finds direct and indirect usages of a given class in the application package, where indirect or transitive
     * usages are discovered up to the specified maximal level.
     *
     * @param apk The APK file containing the dex classes.
     * @param clazz The class for which we should find its usages.
     * @param maxLevel The maximal level that controls the lookup of transitive usages.
     * @return Returns a set of classes that make use of the given class.
     */
    @SuppressWarnings("unused")
    public static Set<Usage> findClassUsages(final APK apk, final String clazz, int maxLevel) {

        LOGGER.debug("Find direct and indirect usages of class: " + clazz);

        if (CACHE.containsKey(clazz)) {
            return CACHE.get(clazz);
        }

        final Set<Usage> totalUsages = new LinkedHashSet<>(); // save them in order
        final Set<String> classes = new HashSet<>();
        classes.add(clazz);

        for (int level = 0; level < maxLevel; level++) {

            final Set<String> newClasses = new HashSet<>();

            for (String className : classes) {
                final Set<Usage> usages = findClassUsages(apk, className);
                totalUsages.addAll(usages);
                // search for transitive usages in subsequent iterations
                usages.forEach(usage -> newClasses.add(usage.clazz.toString()));
            }

            classes.clear();
            classes.addAll(newClasses);
        }

        totalUsages.forEach(LOGGER::debug);
        return totalUsages;
    }

    /**
     * Checks whether the class annotations describe a usage of the given class.
     *
     * @param annotations The class annotations.
     * @param clazz The usage class.
     * @return Returns {@code true} if the class annotations describe a usage of the given class,
     *          otherwise {@code false} is returned.
     */
    private static boolean checkAnnotationsForUsage(Set<? extends Annotation> annotations, final String clazz) {

        AtomicBoolean containsUsage = new AtomicBoolean(false);

        annotations.forEach(annotation -> {
            annotation.getElements().forEach(annotationElement -> {
                // TODO: check that the match refers to the generic declaration, i.e. is within '<>'
                if (annotationElement.getValue().toString().contains(clazz)) {
                    containsUsage.set(true);
                }
            });
        });

        return containsUsage.get();
    }

    /**
     * Finds direct usages of a given class in the application package, where a usage is given when:
     *
     * (1) Another class makes use of the given clause in the 'extends' or 'implements' clause.
     * (2) Another class holds an instance variable of the given class.
     * (3) A method of another class has a method parameter of the given class.
     * (4) A method of another class invokes a method of the given class.
     *
     * @param apk   The APK file containing the dex classes.
     * @param clazz The class for which we should find its usages.
     * @return Returns a set of classes that make use of the given class.
     */
    public static Set<Usage> findClassUsages(final APK apk, final String clazz) {

        LOGGER.debug("Find direct usages of class: " + clazz);

        if (CACHE.containsKey(clazz)) {
            return CACHE.get(clazz);
        }

        final Set<Usage> usages = new HashSet<>();
        final String applicationPackage = apk.getManifest().getPackageName();
        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                // TODO: Define a usage to any outer class and take in account the following case: 'outerClass$InnerClass1$InnerClass2'.

                boolean foundUsage = false;
                String className = classDef.toString();

                if (!ClassUtils.dottedClassName(className).startsWith(applicationPackage)
                        && (mainActivityPackage == null || !ClassUtils.dottedClassName(className).startsWith(mainActivityPackage))) {
                    // don't consider usages outside the application package
                    continue;
                }

                if (ClassUtils.isResourceClass(classDef) || ClassUtils.isBuildConfigClass(classDef)) {
                    // don't consider resource classes or the build config class
                    continue;
                }

                if (className.equals(clazz)) {
                    // the class itself is not relevant
                    continue;
                }

                // TODO: We should probably rethink this. Although it is common that the outer class is using the inner
                //  class, e.g. as part of a callback specification, there is also sometimes a back reference from the
                //  inner to the outer class, in particular when we think about the somewhat strange handling of lambda
                //  callbacks.
                if (ClassUtils.isInnerClass(className)) {
                    if (clazz.equals(ClassUtils.getOuterClass(className))) {
                        // Any inner class of the given class is also not relevant, i.e. the outer class is using the
                        // inner class but not vice versa!
                        continue;
                    }
                }

                /*
                * Checks whether a 'usage' is defined through the 'extends' clause, i.e. inspecting
                * the super class and its annotations, e.g. generics. Assume we check for the usages
                * of class B and we are currently inspecting class A. In the following cases we say that
                * A is a usage of B or equivalently that the usages of B contain A:
                *
                * (1) A extends B
                * (2) A extends C<B> (Here A is also a usage of C)
                *
                 */
                if ((classDef.getSuperclass() != null && classDef.getSuperclass().equals(clazz))
                        || checkAnnotationsForUsage(classDef.getAnnotations(), clazz)) {
                    LOGGER.debug("Found super class / annotation usage: " + classDef);
                    usages.add(new Usage(classDef));
                    foundUsage = true;
                }

                if (foundUsage) {
                    continue;
                }

                /*
                * Checks whether a 'usage' is defined through the 'implements' clause, i.e.
                * inspecting the interface classes. The same conditions holds as for the 'extends' clause.
                * NOTE: We only need to inspect the interface names, the annotations have been checked
                * previously.
                 */
                for (String interfaceClass : classDef.getInterfaces()) {
                    if (interfaceClass.equals(clazz)) {
                        LOGGER.debug("Found interface class usage: " + classDef);
                        usages.add(new Usage(classDef));
                        foundUsage = true;
                        break;
                    }
                }

                if (foundUsage) {
                    continue;
                }

                // second check whether the class is hold as an instance variable
                for (Field instanceField : classDef.getInstanceFields()) {
                    if (clazz.equals(instanceField.getType())) {
                        usages.add(new Usage(classDef));
                        foundUsage = true;
                        break;
                    }
                }

                if (foundUsage) {
                    continue;
                }

                for (Method method : classDef.getMethods()) {

                    // third check whether method parameters refer to class
                    for (MethodParameter parameter : method.getParameters()) {
                        if (clazz.equals(parameter.getType())) {
                            usages.add(new Usage(classDef, method));
                            foundUsage = true;
                            break;
                        }
                    }

                    if (foundUsage) {
                        break;
                    }

                    // fourth check whether any method of the class is invoked
                    MethodImplementation implementation = method.getImplementation();
                    if (implementation != null) {
                        List<AnalyzedInstruction> instructions = MethodUtils.getAnalyzedInstructions(dexFile, method);
                        for (AnalyzedInstruction instruction : instructions) {
                            if (InstructionUtils.isInvokeInstruction(instruction)) {
                                String invokeTarget = ((ReferenceInstruction) instruction.getInstruction()).getReference().toString();
                                if (clazz.equals(MethodUtils.getClassName(invokeTarget))) {
                                    usages.add(new Usage(classDef, method, instruction));
                                    foundUsage = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (foundUsage) {
                        break;
                    }
                }
            }
        }
        usages.forEach(LOGGER::debug);
        CACHE.put(clazz, usages);
        return usages;
    }
}
