package de.uni_passau.fim.auermich.utility;

import de.uni_passau.fim.auermich.graphs.Vertex;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Iterator;
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
     * Searches for a target method in the given {@code dexFile}.
     * @param dexFile The dexFile to search in.
     * @param methodName The name of the target method.
     * @return Returns an optional containing either the target method or not.
     */
    public static Optional<Method> searchForTargetMethod(DexFile dexFile, String methodName) {

        Set<? extends ClassDef> classes = dexFile.getClasses();

        // search for target method
        for (ClassDef classDef : classes) {
            for (Method method : classDef.getMethods()) {
                if (method.getName().equals(methodName)) {
                    return Optional.of(method);
                }
            }
        }
        return Optional.empty();
    }

}
