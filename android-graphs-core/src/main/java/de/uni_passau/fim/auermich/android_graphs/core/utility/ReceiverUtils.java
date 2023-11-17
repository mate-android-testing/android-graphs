package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.BroadcastReceiver;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        // TODO: There exists a sendBroadcastSync() method in the LocalBroadcastManager class.
        return method.equals("sendBroadcast(Landroid/content/Intent;)V")
                || method.equals("sendBroadcast(Landroid/content/Intent;Ljava/lang/String;)V")
                // https://developer.android.com/reference/android/support/v4/content/LocalBroadcastManager.html#summary
                || method.equals("sendBroadcast(Landroid/content/Intent;)Z");
    }

    /**
     * Tries to resolve a sendBroadcast() invocation back to the concrete receiver(s).
     *
     * @param components The set of components.
     * @param analyzedInstruction The sendBroadcast() instruction.
     * @return Returns the resolved broadcast receiver(s) or {@code null} otherwise.
     */
    public static List<BroadcastReceiver> isReceiverInvocation(Set<Component> components, AnalyzedInstruction analyzedInstruction) {

        if (analyzedInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack receiver invocation to receiver
            return null;
        }

        /*
         * A broadcast receiver invocation looks as follows:
         *
         * (1) invoke-virtual {v1, v0}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V
         * (2) invoke-virtual {v4, v2}, Landroid/support/v4/content/LocalBroadcastManager;->sendBroadcast(Landroid/content/Intent;)Z
         *
         * (1) We need to backtrack the intent (register v0) to the location where it was created or setComponentName()
         * was called. Then, we should be able to retrieve the name of the receiver. Right now we assume that the
         * last created string constant refers to the receiver name!
         *
         * (2) We need to backtrack the intent (register v2) to the location where the action was passed over since
         * the name of the broadcast receiver is likely not mentioned (dynamic receiver).
         */
        final Instruction35c invokeInstruction = (Instruction35c) analyzedInstruction.getInstruction();
        final String sendBroadcast = invokeInstruction.getReference().toString();
        final int intentRegister = invokeInstruction.getRegisterD();
        final boolean isLocalBroadcast = sendBroadcast.startsWith("Landroid/support/v4/content/LocalBroadcastManager;->");
        Integer actionRegister = null;
        final Set<BroadcastReceiver> broadcastReceivers = components.stream()
                .filter(component -> component instanceof BroadcastReceiver)
                .map(component -> (BroadcastReceiver) component)
                .collect(Collectors.toSet());

        AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

        while (pred.getInstructionIndex() != -1) {

            final Instruction predecessor = pred.getInstruction();

            if (isLocalBroadcast) {

                if (InstructionUtils.isInvokeInstruction(pred) && predecessor instanceof Instruction35c) {
                    // NOTE: setsRegister() only works on the arguments (v4) and not on the calling class argument (v2).
                    if (((Instruction35c) predecessor).getRegisterC() == intentRegister) {
                        Instruction35c invocation = (Instruction35c) predecessor;
                        if (invocation.getReference().toString()
                                .equals("Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;")) {
                            // invoke-virtual {v2, v4}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;
                            if (actionRegister == null) {
                                actionRegister = invocation.getRegisterD();
                            }
                        }
                    }
                } else if (actionRegister != null && pred.setsRegister(actionRegister)) {
                    if (predecessor.getOpcode() == Opcode.CONST_STRING || predecessor.getOpcode() == Opcode.CONST_STRING_JUMBO) {
                        final String action = ((ReferenceInstruction) predecessor).getReference().toString();
                        final List<BroadcastReceiver> receivers = broadcastReceivers.stream()
                                .filter(BroadcastReceiver::hasAction)
                                .filter(broadcastReceiver -> broadcastReceiver.getAction().equals(action))
                                .collect(Collectors.toList());
                        if (!receivers.isEmpty()) {
                            return receivers;
                        }
                    }
                }

            } else if (predecessor.getOpcode() == Opcode.CONST_STRING || predecessor.getOpcode() == Opcode.CONST_CLASS) {
                // TODO: Improve backtracking!

                String receiverName = ((Instruction21c) predecessor).getReference().toString();

                /*
                * It can happen that the string constant represents a dotted class name, e.g. when the target component
                * of the intent was set via setComponent(). In this case, we need to convert to a dalvik conform class name.
                 */
                receiverName = ClassUtils.convertDottedClassName(receiverName);

                Optional<BroadcastReceiver> component = ComponentUtils.getBroadcastReceiverByName(components, receiverName);
                if (component.isPresent()) {
                    return Collections.singletonList(component.get());
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

            if (instruction instanceof Instruction35c && InstructionUtils.isInvokeInstruction(instruction)) {
                registerD = ((Instruction35c) instruction).getRegisterD();
            }

            AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

            while (pred.getInstructionIndex() != -1) {

                Instruction predecessor = pred.getInstruction();

                if (predecessor.getOpcode() == Opcode.NEW_INSTANCE) {

                    Instruction21c newInstance = (Instruction21c) predecessor;

                    if (newInstance.getRegisterA() == registerD) {

                        String receiverName = newInstance.getReference().toString();
                        Optional<BroadcastReceiver> component = ComponentUtils.getBroadcastReceiverByName(components, receiverName);

                        if (component.isPresent()) {
                            BroadcastReceiver receiver = component.get();
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
            // registration via LocalBroadcastManager
        } else if (targetMethod.endsWith("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)V")) {

            // invoke-virtual {v8, v3, v2}, Landroid/support/v4/content/LocalBroadcastManager;->
            // registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)V

            if (analyzedInstruction.getPredecessors().isEmpty()) {
                // broadcast receiver name is defined outside of method context
                return;
            }

            if (instruction instanceof Instruction35c && InstructionUtils.isInvokeInstruction(instruction)) {

                // the register in which the broadcast receiver is stored
                int receiverRegister = ((Instruction35c) instruction).getRegisterD();

                // the register in which the intent filter is stored
                int intentFilterRegister = ((Instruction35c) instruction).getRegisterE();
                boolean foundIntentFilterConstructor = false;

                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                // TODO: There might be theoretically multiple actions per intent filter.
                // the action described by the intent filter
                String action = null;

                // We need to backtrack both the receiver and the intent filter (action tag)
                while (pred.getInstructionIndex() != -1) {

                    final Instruction predecessor = pred.getInstruction();

                    if (pred.setsRegister(intentFilterRegister)) { // backtracking intent filter
                        if (predecessor instanceof Instruction35c && InstructionUtils.isInvokeInstruction(predecessor)) {
                            final String methodReference = ((ReferenceInstruction) predecessor).getReference().toString();
                            if (methodReference.equals("Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V")) {
                                // The action is passed to the constructor of the intent filter (v8)
                                // invoke-direct {v2, v8}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V
                                intentFilterRegister = ((Instruction35c) predecessor).getRegisterD();
                                foundIntentFilterConstructor = true;
                            }
                            // TODO: The action might be passed via addAction() to the intent filter.
                        } else if (foundIntentFilterConstructor
                                && (predecessor.getOpcode() == Opcode.CONST_STRING
                                || predecessor.getOpcode() == Opcode.CONST_STRING_JUMBO)) {
                            if (action == null) {
                                action = ((ReferenceInstruction) predecessor).getReference().toString();
                            }
                        }
                    } else if (pred.setsRegister(receiverRegister)) { // backtracking receiver
                        if (predecessor.getOpcode() == Opcode.NEW_INSTANCE) {
                            final Instruction21c newInstance = (Instruction21c) predecessor;
                            final String receiverName = newInstance.getReference().toString();
                            final Optional<BroadcastReceiver> component
                                    = ComponentUtils.getBroadcastReceiverByName(components, receiverName);

                            if (component.isPresent()) {
                                BroadcastReceiver receiver = component.get();
                                LOGGER.debug("Found dynamic receiver: " + receiver);
                                receiver.setDynamicReceiver(true);
                                if (action != null) {
                                    receiver.setAction(action);
                                }
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
}
