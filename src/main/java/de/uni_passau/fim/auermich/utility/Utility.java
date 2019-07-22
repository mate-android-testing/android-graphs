package de.uni_passau.fim.auermich.utility;

import com.google.common.collect.Lists;
import de.uni_passau.fim.auermich.Main;
import de.uni_passau.fim.auermich.graphs.Vertex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.LoggerRegistry;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.DexClassProvider;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;


public final class Utility {

    public static final String EXCLUSION_PATTERN_FILE = "exclude.txt";
    private static final Logger LOGGER = LogManager.getLogger(Utility.class);

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

    public static MethodAnalyzer getAnalyzer(DexFile dexFile, Method targetMethod) {

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), targetMethod,
                null, false);
        return analyzer;
    }

    /**
     * Generates patterns of classes which should be excluded from the instrumentation.
     *
     * @return The pattern representing classes that should not be instrumented.
     * @throws IOException        If the file containing excluded classes is not available.
     * @throws URISyntaxException If the file is not present.
     */
    public static Pattern readExcludePatterns() throws IOException {

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(EXCLUSION_PATTERN_FILE);

        if (inputStream == null) {
            LOGGER.warn("Couldn't find exlcusion file!");
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (first)
                first = false;
            else
                builder.append("|");
            builder.append(line);
        }
        reader.close();
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
        className = className.substring(className.indexOf('L') + 1, className.indexOf(';'));
        className = className.replace('/', '.');
        return className;
    }

}
