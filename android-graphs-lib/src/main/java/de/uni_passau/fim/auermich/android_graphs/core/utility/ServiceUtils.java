package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Component;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Set;

/**
 * A utility class to backtrack a call to startService() or bindService() to the respective {@link Service} component.
 */
public final class ServiceUtils {

    private static final Logger LOGGER = LogManager.getLogger(ServiceUtils.class);

    private ServiceUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Checks whether the given method refers to an enqueueWork() invocation of a JobIntentService.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if method is referring to an enqueueWork() invocation, otherwise {@code false}.
     */
    public static boolean isJobIntentServiceInvocation(final String methodSignature) {
        // https://developer.android.com/reference/androidx/core/app/JobIntentService#enqueueWork(android.content.Context,java.lang.Class%3C?%3E,int,android.content.Intent)
        // https://developer.android.com/reference/androidx/core/app/JobIntentService#enqueueWork(android.content.Context,android.content.ComponentName,int,android.content.Intent)
        return methodSignature.endsWith("->enqueueWork(Landroid/content/Context;Ljava/lang/Class;ILandroid/content/Intent;)V")
                || methodSignature.endsWith("->enqueueWork(Landroid/content/Context;Landroid/content/ComponentName;ILandroid/content/Intent;)V");
    }

    /**
     * Retrieves the onHandleWork() callback method for the given enqueueWork() invocation of a JobIntentService if possible.
     *
     * @param invokeInstruction The invoke instruction referring to the enqueueWork() invocation.
     * @return Returns the onHandleWork() callback method if possible, otherwise {@code null}.
     */
    public static String getJobIntentServiceCallback(final AnalyzedInstruction invokeInstruction) {

        // TODO: Check that the retrieved service name really represents a JobIntentService.
        // TODO: Perform backtracking if really necessary.

        // Example:
        //     const-class v1, Lnet/exclaimindustries/geohashdroid/services/StockService;
        //    const/16 v2, 0x3e9
        //    invoke-static {p0, v1, v2, p1}, Lnet/exclaimindustries/geohashdroid/services/StockService;
        //                           ->enqueueWork(Landroid/content/Context;Ljava/lang/Class;ILandroid/content/Intent;)V
        //
        // It looks like the respective service name is always encoded in the static enqueueWork() invocation, thus no
        // backtracking seems to be necessary.

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {
            final String invocation = ((ReferenceInstruction) invokeInstruction.getInstruction()).getReference().toString();
            final String serviceName = MethodUtils.getClassName(invocation);
            LOGGER.debug("Found callback: " + serviceName + "->onHandleWork(Landroid/content/Intent;)V");
            return serviceName + "->onHandleWork(Landroid/content/Intent;)V";
        }
        return null; // couldn't resolve invocation
    }

    /**
     * Checks whether the given instruction refers to a service invocation. If this is the case, the invocation
     * is backtracked to the respective service component if possible.
     *
     * @param components          The set of discovered components.
     * @param method              The method in which the given instruction is located.
     * @param analyzedInstruction The instruction to be checked.
     */
    public static void checkForServiceInvocation(final Set<Component> components, String method,
                                                 AnalyzedInstruction analyzedInstruction) {

        Instruction instruction = analyzedInstruction.getInstruction();
        String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

        if (targetMethod.endsWith("startService(Landroid/content/Intent;)Landroid/content/ComponentName;")) {

            if (analyzedInstruction.getPredecessors().isEmpty()) {
                // service name might be hold in some parameter register
                return;
            }

            // go back until we find const-class instruction which holds the service name
            AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

            while (pred.getInstructionIndex() != -1) {

                Instruction predecessor = pred.getInstruction();

                if (predecessor.getOpcode() == Opcode.CONST_CLASS) {

                    String serviceName = ((Instruction21c) predecessor).getReference().toString();
                    Optional<Component> component = ComponentUtils.getComponentByName(components, serviceName);

                    if (component.isPresent()) {
                        Service service = (Service) component.get();
                        service.setStarted(true);

                        // service object has been filled completely
                        break;
                    }

                }

                // consider next predecessor if available
                if (!analyzedInstruction.getPredecessors().isEmpty()) {
                    pred = pred.getPredecessors().first();
                } else {
                    break;
                }
            }

        } else if (targetMethod.endsWith("bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z")) {

            /*
             * A typical call of bindService() looks as follows:
             *
             * invoke-virtual {p0, v0, v1, v2}, Lcom/base/myapplication/MainActivity;
             *                       ->bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z
             *
             * The intent object is the first (explicit) parameter and refers to v0 in above case. Typically,
             * the intent is generated locally and we are able to extract the service name by looking for the
             * last const-class instruction, which is handed over to the intent constructor as parameter.
             * In addition, we also look for the service connection object that is used. This refers to v1
             * in the above example. Typically, v1 is set as follows:
             *
             * iget-object v1, p0, Lcom/base/myapplication/MainActivity;
             *                       ->serviceConnection:Lcom/base/myapplication/MainActivity$MyServiceConnection;
             *
             * NOTE: In a typical scenario, we first encounter the iget-object instruction in order to derive the
             * service connection object, and afterwards the service object itself.
             */

            if (analyzedInstruction.getPredecessors().isEmpty()) {
                // service name might be hold in some parameter register
                return;
            }

            String serviceConnection = null;
            int serviceConnectionRegister = ((Instruction35c) instruction).getRegisterE();

            AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

            while (pred.getInstructionIndex() != -1) {

                Instruction predecessor = pred.getInstruction();

                // the const-class instruction is likely holding the service name
                if (predecessor.getOpcode() == Opcode.CONST_CLASS) {

                    String serviceName = ((Instruction21c) predecessor).getReference().toString();
                    Optional<Component> component = ComponentUtils.getComponentByName(components, serviceName);

                    if (component.isPresent()) {
                        Service service = (Service) component.get();
                        service.setBound(true);

                        if (serviceConnection != null) {
                            service.setServiceConnection(serviceConnection);
                        }

                        // service object has been filled completely
                        break;
                    }

                } else if (predecessor.getOpcode() == Opcode.IGET_OBJECT
                        && pred.setsRegister(serviceConnectionRegister)) {
                    if (serviceConnection == null) {
                        Instruction22c serviceConnectionObject = ((Instruction22c) predecessor);
                        serviceConnection = Utility.getObjectType(serviceConnectionObject.getReference().toString());
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
    }
}
