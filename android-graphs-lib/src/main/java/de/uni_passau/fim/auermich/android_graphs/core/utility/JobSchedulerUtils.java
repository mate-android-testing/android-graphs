package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class JobSchedulerUtils {

    private static final Logger LOGGER = LogManager.getLogger(JobSchedulerUtils.class);

    private JobSchedulerUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    // TODO: Add further JobService callbacks.

    /**
     * Retrieves the FQN of the onStartJob() method for the given job service.
     *
     * @param jobServiceClassName The name of the JobService class.
     * @return Returns the FQN of the onStartJob() method.
     */
    public static String getOnJobStartMethod(final String jobServiceClassName) {
        // https://developer.android.com/reference/android/app/job/JobService#onStartJob(android.app.job.JobParameters)
        return jobServiceClassName + "->onStartJob(Landroid/app/job/JobParameters;)Z";
    }

    /**
     * Retrieves the FQN of the onStopJob() method for the given job service.
     *
     * @param jobServiceClassName The name of the JobService class.
     * @return Returns the FQN of the onStopJob() method.
     */
    public static String getOnJobStopMethod(final String jobServiceClassName) {
        // https://developer.android.com/reference/android/app/job/JobService#onStopJob(android.app.job.JobParameters)
        return jobServiceClassName + "->onStopJob(Landroid/app/job/JobParameters;)Z";
    }

    /**
     * Checks whether the given method refers to a schedule() invocation of a JobScheduler.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if method is referring to a schedule() invocation, otherwise {@code false}.
     */
    public static boolean isScheduleMethod(final String methodSignature) {
        return methodSignature.equals("Landroid/app/job/JobScheduler;->schedule(Landroid/app/job/JobInfo;)I");
    }

    /**
     * Retrieves the JobService class name for the given schedule() invocation if possible.
     *
     * @param invokeInstruction The invoke instruction referring to a schedule() invocation.
     * @param classHierarchy A mapping from class name to its class.
     * @return Returns the JobService class name for the given schedule() invocation if possible, otherwise {@code null}.
     */
    public static String getJobServiceClassName(final AnalyzedInstruction invokeInstruction, final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:
        //    new-instance v3, Landroid/app/job/JobInfo$Builder;
        //    new-instance v4, Landroid/content/ComponentName;
        //    const-class v5, Lnet/exclaimindustries/geohashdroid/services/AlarmService$AlarmServiceJobService;
        //    invoke-direct {v4, p0, v5}, Landroid/content/ComponentName;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
        //    invoke-direct {v3, v2, v4}, Landroid/app/job/JobInfo$Builder;-><init>(ILandroid/content/ComponentName;)V
        //    const/16 v4, 0x10c
        //    aput-boolean v2, v0, v4
        //    invoke-virtual {v3, v2}, Landroid/app/job/JobInfo$Builder;->setRequiredNetworkType(I)Landroid/app/job/JobInfo$Builder;
        //    move-result-object v3
        //    const/16 v4, 0x10d
        //    aput-boolean v2, v0, v4
        //    invoke-virtual {v3}, Landroid/app/job/JobInfo$Builder;->build()Landroid/app/job/JobInfo;
        //    move-result-object v3
        //    const/16 v4, 0x10e
        //    aput-boolean v2, v0, v4
        //    invoke-virtual {v1, v3}, Landroid/app/job/JobScheduler;->schedule(Landroid/app/job/JobInfo;)I
        //
        // We need to track the JobInfo object (v3) back to the JobInfo$Builder object where the raw component name
        // of the JobService is set.

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The JobInfo class is stored in the second register (v3 above) passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            int targetRegister = invoke.getRegisterD();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();
            boolean foundMoveResult = false;

            // backtrack until we discover last write to register D (v3 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (foundMoveResult) {
                    // NOTE: We assume that this is always an invocation on a JobInfo$Builder object.
                    // predecessor of move-result-object v3 must be an invoke instruction:
                    // invoke-virtual {v5, v2}, Landroid/app/job/JobInfo$Builder;->setRequiredNetworkType(I)Landroid/app/job/JobInfo$Builder;
                    targetRegister = ((Instruction35c) predecessor).getRegisterC();
                    foundMoveResult = false;
                } else if (predecessor.getOpcode() == Opcode.INVOKE_DIRECT
                        && ((Instruction35c) predecessor).getRegisterC() == targetRegister) {
                    final String invocation = ((ReferenceInstruction) predecessor).getReference().toString();
                    if (invocation.equals("Landroid/app/job/JobInfo$Builder;-><init>(ILandroid/content/ComponentName;)V")) {
                        // backtrack the component name passed to the constructor
                        // invoke-direct {v3, v2, v4}, Landroid/app/job/JobInfo$Builder;-><init>(ILandroid/content/ComponentName;)V
                        targetRegister = ((Instruction35c) predecessor).getRegisterE();
                    } else if (invocation.equals("Landroid/content/ComponentName;-><init>(Landroid/content/Context;Ljava/lang/Class;)V")) {
                        // backtrack the raw component name
                        // invoke-direct {v4, p0, v5}, Landroid/content/ComponentName;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
                        targetRegister = ((Instruction35c) predecessor).getRegisterE();
                    }
                } else if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                    // inspect the previous instruction to derive the new target register (the JobInfo$Builder object)
                    foundMoveResult = true;
                } else if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.CONST_CLASS) {
                    // const-class v5, Lnet/exclaimindustries/geohashdroid/services/AlarmService$AlarmServiceJobService;
                    final String className = ((ReferenceInstruction) predecessor).getReference().toString();
                    if (classHierarchy.getSuperClasses(className).contains("Landroid/app/job/JobService;")) {
                        LOGGER.debug("Found Job class: " + className);
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

        return null; // couldn't resolve invocation
    }
}
