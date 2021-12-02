package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.app.components.BroadcastReceiver;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Component;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.ComponentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;

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

    public static boolean isReceiverInvocation(String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        return method.equals("sendBroadcast(Landroid/content/Intent;)V");
    }

    /**
     *
     * @param components
     * @param analyzedInstruction
     * @return
     */
    public static BroadcastReceiver isReceiverInvocation(Set<Component> components, AnalyzedInstruction analyzedInstruction) {

        if (analyzedInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack receiver invocation to receiver
            return null;
        }

        AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

        while (pred.getInstructionIndex() != -1) {

            LOGGER.debug("Instruction: " + pred.getInstructionIndex() + ", " + pred.getInstruction().getOpcode());

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
                LOGGER.debug("Receiver name: " + receiverName);
                Optional<Component> component = ComponentUtils.getComponentByName(components, receiverName);
                if (component.isPresent()) {
                    LOGGER.debug("Receiver!");
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
     * Checks whether the given instruction refers to a receiver invocation. If this is the case, the invocation
     * is backtracked to the respective service component if possible.
     *
     * @param components          The set of discovered components.
     * @param method              The method in which the given instruction is located.
     * @param analyzedInstruction The instruction to be checked.
     */
    public static void checkForReceiverInvocation(final Set<Component> components, String method,
                                                 AnalyzedInstruction analyzedInstruction) {

        // TODO: resolve whether receiver is dynamic or not -> registerReceiver

    }
}
