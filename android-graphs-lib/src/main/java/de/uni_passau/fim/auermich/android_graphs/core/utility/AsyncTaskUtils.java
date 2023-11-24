package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Constitutes an AsyncTask, see https://developer.android.com/reference/android/os/AsyncTask.
 */
public class AsyncTaskUtils {

    private static final Logger LOGGER = LogManager.getLogger(AsyncTaskUtils.class);

    /**
     * The AsyncTask class name.
     */
    private static final String ASYNC_TASK_CLASS = "Landroid/os/AsyncTask;";

    private AsyncTaskUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Checks whether the given method represents an AsyncTask invocation, i.e. a call to execute().
     *
     * @param methodSignature The method to be checked for a method invocation.
     * @return Returns {@code true} if the method refers to a AsyncTask invocation,
     *          otherwise {@code false} is returned.
     */
    public static boolean isAsyncTaskInvocation(final String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        return method.equals("execute([Ljava/lang/Object;)Landroid/os/AsyncTask;");
    }

    /**
     * Retrieves the class name of the AsyncTask class to which the execute() method belongs if possible.
     *
     * @param callingClass The class in which the execute() method is invoked.
     * @param invokeInstruction The invoke instruction referring to the execute() method.
     * @param classHierarchy A mapping from class name to its class.
     * @return Returns the AsyncTask class name to which the execute() method belongs if possible, otherwise {@code null}.
     */
    public static String getAsyncTaskClass(final String callingClass, final AnalyzedInstruction invokeInstruction,
                                           final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:
        // invoke-virtual {v0, v1}, Landroid/os/AsyncTask;->execute([Ljava/lang/Object;)Landroid/os/AsyncTask;

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The AsyncTask class is stored in the first register passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterC();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (v3 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.NEW_INSTANCE) {
                    // new-instance v0, Lcom/ichi2/anki/stats/AnkiStatsTaskHandler$DeckPreviewStatistics;
                    final String className = ((ReferenceInstruction) predecessor).getReference().toString();
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classHierarchy.getSuperClasses(classDef).contains(ASYNC_TASK_CLASS)) {
                        LOGGER.debug("Found AsyncTask class: " + className);
                        return className;
                    }
                }

                // consider next predecessor if available
                if (!pred.getPredecessors().isEmpty()) {
                    pred = pred.getPredecessors().first();
                } else {
                    break;
                }
            }
        }

        // NOTE: The following is just a mere heuristic, e.g., multiple inner classes could represent AsyncTasks!
        // If we couldn't backtrack the execute() method to its AsyncTask class, our last hope is that the class or any
        // inner class invoking the execute() method is an AsyncTask itself.
        final ClassDef clazz = classHierarchy.getClass(callingClass);
        if (clazz != null) {
            // check calling class
            if (classHierarchy.getSuperClasses(clazz).contains(ASYNC_TASK_CLASS)) {
                LOGGER.debug("Found AsyncTask class: " + callingClass);
                return callingClass;
            } else {
                // check inner classes
                for (final ClassDef innerClass : classHierarchy.getInnerClasses(clazz)) {
                    if (classHierarchy.getSuperClasses(innerClass).contains(ASYNC_TASK_CLASS)) {
                        LOGGER.debug("Found AsyncTask class: " + innerClass);
                        return innerClass.toString();
                    }
                }
            }
        }

        return null; // couldn't resolve invocation
    }

    /**
     * Returns the onPreExecute() method of an AsyncTask.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the onPreExecute() method.
     */
    public static String getOnPreExecuteMethod(String className) {
        return className + "->onPreExecute()V";
    }

    /**
     * Returns the doInBackground() method of an AsyncTask.
     * NOTE: At the bytecode level, this method is a bridge method with fixed parameters,
     * which in turn calls the original doInBackground() method.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the doInBackground() method.
     */
    public static String getDoInBackgroundMethod(String className) {
        return className + "->doInBackground([Ljava/lang/Object;)Ljava/lang/Object;";
    }

    /**
     * Returns the onProgressUpdate() method of an AsyncTask.
     * NOTE: At the bytecode level, this method is a bridge method with fixed parameters,
     * which in turn calls the original onProgressUpdate() method.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the onProgressUpdate() method.
     */
    public static String getOnProgressUpdateMethod(String className) {
        return className + "->onProgressUpdate([Ljava/lang/Object;)V";
    }

    /**
     * Returns the onPostExecute() method of an AsyncTask.
     * NOTE: At the bytecode level, this method is a bridge method with fixed parameters,
     * which in turn calls the original onPostExecute() method.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the onPostExecute() method.
     */
    public static String getOnPostExecuteMethod(String className) {
        return className + "->onPostExecute(Ljava/lang/Object;)V";
    }

    /**
     * Returns the onCancelled() method of an AsyncTask.
     * NOTE: There is no bridge method in the bytecode.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the onCancelled() method.
     */
    public static String getOnCancelledMethod(String className) {
        return className + "->onCancelled()V";
    }
}
