package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Provides utility functions to check whether a given method represents the start() method of a Thread class
 * or the run() method of a Thread or Runnable class.
 */
public final class ThreadUtils {

    private static final Logger LOGGER = LogManager.getLogger(ThreadUtils.class);

    private ThreadUtils() {
        throw new UnsupportedOperationException("utility class");
    }


    /**
     * Checks whether the given method refers to a postDelayed() invocation.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if method is referring to a postDelayed() invocation, otherwise {@code false}.
     */
    public static boolean isPostDelayMethod(final String methodSignature) {
        return methodSignature.equals("Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z");
    }

    /**
     * Retrieves the callback function, i.e. a run() method, for the postDelayed() invocation if possible.
     *
     * @param invokeInstruction The invoke instruction referring to the postDelayed() call.
     * @param classHierarchy A mapping from class name to its class.
     * @return Returns the callback method belonging to the postDelayed() invocation if possible, otherwise {@code null}.
     */
    public static String getPostDelayCallback(final AnalyzedInstruction invokeInstruction, final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:
        // iget-object v1, p0, Lorg/dmfs/tasks/QuickAddDialogFragment;->mDismiss:Ljava/lang/Runnable;
        // const-wide/16 v2, 0x3e8
        // invoke-virtual {v0, v1, v2, v3}, Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z
        //
        // constructor (write on same instance variable):
        // new-instance v0, Lorg/dmfs/tasks/QuickAddDialogFragment$1;
        // invoke-direct {v0, p0}, Lorg/dmfs/tasks/QuickAddDialogFragment$1;-><init>(Lorg/dmfs/tasks/QuickAddDialogFragment;)V
        // iput-object v0, p0, Lorg/dmfs/tasks/QuickAddDialogFragment;->mDismiss:Ljava/lang/Runnable;

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The runnable class is stored in the second register (v1 above) passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterD();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (v1 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.IGET_OBJECT) {

                    // iget-object v1, p0, Lorg/dmfs/tasks/QuickAddDialogFragment;->mDismiss:Ljava/lang/Runnable;
                    final String reference = ((ReferenceInstruction) predecessor).getReference().toString();
                    final String className = reference.split("->")[0];
                    final String variableName = reference.split("->")[1].split(":")[0];

                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null) {

                        // We assume that the instance variable is preferably written in one of the constructors
                        for (Method constructor : classDef.getDirectMethods()) {
                            final MethodImplementation implementation = constructor.getImplementation();
                            if (implementation != null) {
                                final List<Instruction> instructions = StreamSupport
                                        .stream(implementation.getInstructions().spliterator(), false)
                                        .collect(Collectors.toList());
                                int instructionIndex = 0;
                                for (Instruction instruction : implementation.getInstructions()) {
                                    // // check whether the instruction accesses (writes) the variable
                                    if (instruction.getOpcode() == Opcode.IPUT_OBJECT) {
                                        final String ref = ((ReferenceInstruction) instruction).getReference().toString();
                                        final String var = ref.split("->")[1].split(":")[0];
                                        if (variableName.equals(var)) {
                                            // We need to start backtracking from here.
                                            final Instruction22c iputObject = (Instruction22c) instruction;
                                            final int variableRegister = iputObject.getRegisterA(); // holds the variable
                                            for (int i = instructionIndex; i > 0; i--) {
                                                final Instruction precedingInstruction = instructions.get(i);
                                                if (precedingInstruction.getOpcode() == Opcode.NEW_INSTANCE
                                                        && ((Instruction21c) precedingInstruction).getRegisterA() == variableRegister) {
                                                    final String runnableClassName
                                                            = ((ReferenceInstruction) precedingInstruction).getReference().toString();
                                                    LOGGER.debug("Found callback: " + runnableClassName + "->run()V");
                                                    return runnableClassName + "->run()V";
                                                }
                                            }
                                        }
                                    }
                                    instructionIndex++;
                                }
                            }
                        }
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
        return null; // couldn't resolve invocation
    }

    /**
     * Checks whether the given method represents the start() or run() method of the Thread/Runnable class.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if the method refers to the run method,
     * otherwise {@code false} is returned.
     */
    private static boolean isStartOrRunMethod(String methodSignature) {
        return methodSignature.equals("Ljava/lang/Runnable;->run()V")
                || methodSignature.equals("Ljava/lang/Thread;->start()V");
    }

    /**
     * Checks whether the given method represents either the start() method of a Thread class or the
     * run() method of either a Thread or Runnable class.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if the method is either the start() or run() method of a Thread
     *          or Runnable class, otherwise {@code false} is returned.
     */
    public static boolean isThreadMethod(final ClassHierarchy classHierarchy, final String methodSignature) {

        String methodName = MethodUtils.getMethodName(methodSignature);

        if (!methodName.equals("run()V") && !methodName.equals("start()V")) {
            // not even matching the method name
            return false;
        }

        if (isStartOrRunMethod(methodSignature)) {
            return true;
        } else {
            // We need to check that the start() or run() method belongs actually to a Thread/Runnable.
            final String className = MethodUtils.getClassName(methodSignature);
            List<String> superClasses = classHierarchy.getSuperClasses(className);

            if (methodName.endsWith("start()V")) {
                // the start() method is only contained in a subclass of a Thread
                return superClasses.contains("Ljava/lang/Thread;");
            } else {
                // the run() can both belong to a Thread or Runnable
                return superClasses.contains("Ljava/lang/Thread;") || superClasses.contains("Ljava/lang/Runnable;");
            }
        }
    }
}
