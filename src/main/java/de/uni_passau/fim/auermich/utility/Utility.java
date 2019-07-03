package de.uni_passau.fim.auermich.utility;

import de.uni_passau.fim.auermich.Main;
import de.uni_passau.fim.auermich.graphs.Vertex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public final class Utility {

    private Utility() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Returns the dex files in the given {@param directory}.
     *
     * @param directory The directory to search for the dex files.
     * @return Returns a list of dex files found in the given directory.
     */
    public static File[] getDexFiles(File directory) {

        File[] matches = directory.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.startsWith("classes") && name.endsWith(".dex");
            }
        });
        return matches;
    }

    /**
     * Derives a unique method signature in order to avoid
     * name clashes originating from overloaded/inherited methods
     * or methods in different classes.
     *
     * @param method The method to derive its method signature.
     * @return Returns the method signature of the given {@param method}.
     */
    public static String deriveMethodSignature(Method method) {

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

    /**
     * Searches for a target method in the given {@code dexFile}.
     *
     * @param dexFile The dexFile to search in.
     * @param methodSignature The signature of the target method.
     * @return Returns an optional containing either the target method or not.
     */
    public static Optional<Method> searchForTargetMethod(DexFile dexFile, String methodSignature) {

        // TODO: search for target method based on className + method signature
        String className = methodSignature.split("->")[0];
        // String methodName = methodSignature.split("->")[1].split("\\(")[0];

        Set<? extends ClassDef> classes = dexFile.getClasses();

        // search for target method
        for (ClassDef classDef : classes) {
            System.out.println(classDef.toString());
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

}
