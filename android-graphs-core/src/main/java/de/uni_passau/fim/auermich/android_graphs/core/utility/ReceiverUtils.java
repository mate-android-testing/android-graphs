package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.BroadcastReceiver;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Component;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.ComponentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Set;

/**
 * A set of utility functions to detect and resolve broadcast receiver invocations.
 */
public class ReceiverUtils {

    private static final Logger LOGGER = LogManager.getLogger(ReceiverUtils.class);

    private ReceiverUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Whether the given method signature refers to the invocation of broadcast receiver, i.e. calling one of the
     * overloaded sendBroadcast() methods.
     *
     * @param methodSignature The method signature to be checked.
     * @return Returns {@code true} if the invocation constitutes a sendBroadcast() call, otherwise {@code false}.
     */
    public static boolean isReceiverInvocation(String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        return method.equals("sendBroadcast(Landroid/content/Intent;)V")
                || method.equals("sendBroadcast(Landroid/content/Intent;Ljava/lang/String;)V");
    }

    /**
     * Tries to resolve a sendBroadcast() invocation back to the concrete receiver. This is only works if the receiver
     * was statically defined and the corresponding intent is explicit.
     *
     * @param components The set of components.
     * @param analyzedInstruction The sendBroadcast() instruction.
     * @return Returns the resolved broadcast receiver or {@code null} otherwise.
     */
    public static BroadcastReceiver isReceiverInvocation(Set<Component> components, AnalyzedInstruction analyzedInstruction) {

        if (analyzedInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack receiver invocation to receiver
            return null;
        }

        AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

        while (pred.getInstructionIndex() != -1) {

            Instruction predecessor = pred.getInstruction();

            /*
            * A broadcast receiver invocation looks as follows:
            *
            *      invoke-virtual {v1, v0}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V
            *
            * We need to backtrack the intent (register v0) to the location where it was created or setComponentName()
            * was called. Then, we should be able to retrieve the name of the receiver. Right now we assume that the
            * last created string constant refers to the receiver name!
             */
            // TODO: improve backtracking!
            if (predecessor.getOpcode() == Opcode.CONST_STRING || predecessor.getOpcode() == Opcode.CONST_CLASS) {

                String receiverName = ((Instruction21c) predecessor).getReference().toString();

                /*
                * It can happen that the string constant represents a dotted class name, e.g. when the target component
                * of the intent was set via setComponent(). In this case, we need to convert to a dalvik conform class name.
                 */
                receiverName = ClassUtils.convertDottedClassName(receiverName);

                Optional<Component> component = ComponentUtils.getComponentByName(components, receiverName);
                if (component.isPresent()) {
                    if (component.get().getComponentType() == ComponentType.BROADCAST_RECEIVER) {
                        return (BroadcastReceiver) component.get();
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

        // couldn't backtrack receiver invocation to receiver
        return null;
    }

    /**
     * Checks whether the given instruction refers to a registration of a dynamic receiver. If this is the case,
     * the invocation is backtracked to the respective receiver component if possible.
     *
     * @param components          The set of discovered components.
     * @param analyzedInstruction The instruction to be checked.
     */
    public static void checkForDynamicReceiverRegistration(final Set<Component> components, AnalyzedInstruction analyzedInstruction) {

        Instruction instruction = analyzedInstruction.getInstruction();
        String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

        if (targetMethod.endsWith("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)" +
                "Landroid/content/Intent;")
                || targetMethod.endsWith("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;I)" +
                "Landroid/content/Intent;")
                || targetMethod.endsWith("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;" +
                "Ljava/lang/String;Landroid/os/Handler;)Landroid/content/Intent;")
                || targetMethod.endsWith("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;" +
                "Ljava/lang/String;Landroid/os/Handler;I)Landroid/content/Intent;")) {

            if (analyzedInstruction.getPredecessors().isEmpty()) {
                // broadcast receiver name is defined outside of method context
                return;
            }

            /*
             * A typical registerReceiver() invocation looks as follows:
             *
             * Registers:       C   D   E
             * invoke-virtual {p0, v7, v8}, Lcom/base/myapplication/MainActivity;->
             * registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;
             *
             * We need to backtrack the first argument in order to resolve the receiver name. In above case this is the
             * register v7. Typically, the dynamic receiver is declared by a new-instance instruction.
             */
            int registerD = -1;

            if (instruction instanceof Instruction35c) {
                registerD = ((Instruction35c) instruction).getRegisterD();
            }

            AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

            while (pred.getInstructionIndex() != -1) {

                Instruction predecessor = pred.getInstruction();

                if (predecessor.getOpcode() == Opcode.NEW_INSTANCE) {

                    Instruction21c newInstance = (Instruction21c) predecessor;

                    if (newInstance.getRegisterA() == registerD) {

                        String receiverName = newInstance.getReference().toString();
                        Optional<Component> component = ComponentUtils.getComponentByName(components, receiverName);

                        if (component.isPresent()
                                && component.get().getComponentType() == ComponentType.BROADCAST_RECEIVER) {
                            BroadcastReceiver receiver = (BroadcastReceiver) component.get();
                            LOGGER.debug("Found dynamic receiver: " + receiver);
                            receiver.setDynamicReceiver(true);
                            break;
                        }
                    }
                }

                // consider next predecessor if available
                if (!analyzedInstruction.getPredecessors().isEmpty()) {
                    pred = pred.getPredecessors().first();
                } else {
                    break;
                }
            }
        }
    }
}
