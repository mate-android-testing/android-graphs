package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FileUtils {

    private static final Logger LOGGER = LogManager.getLogger(FileUtils.class);

    private FileUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    // https://docs.oracle.com/javase/8/docs/api/java/io/FileFilter.html
    // https://docs.oracle.com/javase/8/docs/api/java/io/File.html#listFiles-java.io.FileFilter-
    public static boolean isListFilesInvocation(String methodSignature) {
        return methodSignature.equals("Ljava/io/File;->listFiles(Ljava/io/FileFilter;)[Ljava/io/File;");
    }

    /**
     * Backtracks a listFiles() invocation to the corresponding accept() method of the FileFilter class.
     *
     * @param invokeInstruction The listFiles() invocation instruction.
     * @return Returns the FQN of the accept() method or {@code null} upon failure.
     */
    public static String isListFilesInvocation(final AnalyzedInstruction invokeInstruction) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:        C   D
        // invoke-direct {v3, v0}, Lbander/notepad/NoteImport$ImportTask$1;-><init>(Lbander/notepad/NoteImport$ImportTask;)V
        // invoke-virtual {v2, v3}, Ljava/io/File;->listFiles(Ljava/io/FileFilter;)[Ljava/io/File;

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The FileFilter class is stored register D (v3 above).
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterD();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (v3 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                // TODO: There are alternative options how the FileFilter could be passed to the listFiles() invocation.

                if (InstructionUtils.isInvokeInstruction(predecessor) && pred.setsRegister(targetRegister)) {
                    final String invocation = ((ReferenceInstruction) predecessor).getReference().toString();
                    final String className = MethodUtils.getClassName(invocation);
                    LOGGER.debug("FileFilter class: " + className);
                    return className + "->accept(Ljava/io/File;)Z"; // under the hood the accept() method is called
                }

                // consider next predecessor if available
                if (!pred.getPredecessors().isEmpty()) {
                    pred = pred.getPredecessors().first();
                } else {
                    break;
                }
            }
        }
        return null; // couldn't resolve invocation
    }
}
