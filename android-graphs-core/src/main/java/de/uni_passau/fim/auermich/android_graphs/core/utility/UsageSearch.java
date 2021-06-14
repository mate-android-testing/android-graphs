package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class UsageSearch {

    private static final Logger LOGGER = LogManager.getLogger(UsageSearch.class);

    private UsageSearch() {
        throw new UnsupportedOperationException("utility class!");
    }

    public static class Usage {

        private ClassDef clazz;
        private Method method;
        private AnalyzedInstruction instruction;

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
    public static Set<Usage> findMethodUsages(APK apk, Method targetMethod) {

        LOGGER.debug("Find usages of method: " + targetMethod);
        Set<Usage> usages = new HashSet<>();

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (ClassUtils.dottedClassName(classDef.toString()).startsWith(apk.getManifest().getPackageName())) {
                    // only inspect application classes
                    for (Method method : classDef.getMethods()) {
                        MethodImplementation implementation = method.getImplementation();
                        if (implementation != null) {
                            List<AnalyzedInstruction> instructions = MethodUtils.getAnalyzedInstructions(dexFile, method);
                            for (AnalyzedInstruction instruction : instructions) {
                                if (InstructionUtils.isInvokeInstruction(instruction)) {
                                    String invokeTarget = ((ReferenceInstruction) instruction.getInstruction())
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
     * Finds specific usages of a given class in the application package, where a usage is given when:
     *
     * (1) Another class holds an instance variable of the given class.
     * (2) A method of another class has a method parameter of the given class.
     * (3) A method of another class invokes a method of the given class.
     *
     * @param apk   The APK file containing the dex classes.
     * @param clazz The class for which we should find its usages.
     * @return Returns a set of classes that make use of the given class.
     */
    public static Set<Usage> findClassUsages(final APK apk, final String clazz) {

        LOGGER.debug("Find usages of class: " + clazz);
        Set<Usage> usages = new HashSet<>();
        String applicationPackage = apk.getManifest().getPackageName();

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                boolean foundUsage = false;
                String className = classDef.toString();

                if (!ClassUtils.dottedClassName(className).startsWith(applicationPackage)) {
                    // don't consider usages outside the application package
                    continue;
                }

                if (className.equals(clazz)) {
                    // the class itself is not relevant
                    continue;
                }

                if (ClassUtils.isInnerClass(className)) {
                    if (clazz.equals(ClassUtils.getOuterClass(className))) {
                        // any inner class of the given class is also not relevant
                        continue;
                    }
                }

                // first check whether the class is hold as an instance variable
                for (Field instanceField : classDef.getInstanceFields()) {
                    if (clazz.equals(instanceField.getType())) {
                        usages.add(new Usage(classDef));
                        foundUsage = true;
                        break;
                    }
                }

                if (foundUsage) {
                    break;
                }

                for (Method method : classDef.getMethods()) {

                    // second check whether method parameters refer to class
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

                    // third check whether any method of the class is invoked
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
        return usages;
    }
}
