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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A set of utility functions to detect and resolve broadcast receiver invocations.
 */
public class ReceiverUtils {

    private static final Logger LOGGER = LogManager.getLogger(ReceiverUtils.class);

    /**
     * The file containing the system events to which system event receivers can react.
     */
    private static final String SYSTEM_EVENTS_FILE = "broadcast_actions.txt";

    /**
     * The set of system events to which system event receivers react.
     */
    private static final Set<String> SYSTEM_EVENTS = loadSystemEvents();

    private ReceiverUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Loads the system events from the given file.
     *
     * @return Returns the set of system events.
     */
    private static Set<String> loadSystemEvents() {

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(SYSTEM_EVENTS_FILE);

        if (inputStream == null) {
            LOGGER.warn("Couldn't find system events file!");
            return Collections.emptySet();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String systemEvent;
        final Set<String> systemEvents = new HashSet<>();

        try {
            while ((systemEvent = reader.readLine()) != null) {
                systemEvents.add(systemEvent);
            }
            reader.close();
        } catch (IOException e) {
            LOGGER.error("Couldn't read from system events file!");
            e.printStackTrace();
            return Collections.emptySet();
        }
        return systemEvents;
    }

    /**
     * Checks whether the given broadcast receiver reacts to system events.
     *
     * @param receiver The receiver to be checked.
     * @return Returns {@code true} if the receiver reacts to system events, otherwise {@code false}.
     */
    public static boolean isSystemEventReceiver(final BroadcastReceiver receiver) {
        return receiver.hasAction() && SYSTEM_EVENTS.contains(receiver.getAction());
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
        final boolean isLocalBroadcast
                = sendBroadcast.startsWith("Landroid/support/v4/content/LocalBroadcastManager;->")
                || sendBroadcast.startsWith("Landroidx/localbroadcastmanager/content/LocalBroadcastManager;->");
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
                        final String reference = invocation.getReference().toString();
                        if (reference.equals("Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;")
                            || reference.equals("Landroid/content/Intent;-><init>(Ljava/lang/String;)V")) {
                            // invoke-virtual {v2, v4}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;
                            // or
                            // invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
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

            if (instruction instanceof Instruction35c && InstructionUtils.isInvokeInstruction(instruction)) {

                int receiverRegister = ((Instruction35c) instruction).getRegisterD();
                int intentFilterRegister = ((Instruction35c) instruction).getRegisterE();
                boolean foundIntentFilter = false;

                // TODO: There might be theoretically multiple actions per intent filter.
                // the action described by the intent filter
                String action = null;
                BroadcastReceiver receiver = null;

                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                while (pred.getInstructionIndex() != -1) {

                    Instruction predecessor = pred.getInstruction();

                    if (pred.setsRegister(intentFilterRegister)) { // backtracking intent filter
                        if (predecessor instanceof Instruction35c && InstructionUtils.isInvokeInstruction(predecessor)) {
                            final String methodReference = ((ReferenceInstruction) predecessor).getReference().toString();
                            if (methodReference.equals("Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V")) {
                                // The action is passed to the constructor of the intent filter (v8)
                                // invoke-direct {v2, v8}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V
                                intentFilterRegister = ((Instruction35c) predecessor).getRegisterD();
                                foundIntentFilter = true;
                            } else if (methodReference.equals("Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V")) {
                                // The action is passed via the addAction() method to the intent filter (v8)
                                // invoke-virtual {v0, v8}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V
                                foundIntentFilter = true;
                            }
                        } else if (foundIntentFilter
                                && (predecessor.getOpcode() == Opcode.CONST_STRING
                                || predecessor.getOpcode() == Opcode.CONST_STRING_JUMBO)) {
                            if (action == null) {
                                action = ((ReferenceInstruction) predecessor).getReference().toString();
                                if (receiver != null) {
                                    receiver.setAction(action);
                                    break;
                                }
                            }
                        }
                    } else if (pred.setsRegister(receiverRegister) && receiver == null) { // backtrack receiver
                        if (predecessor.getOpcode() == Opcode.NEW_INSTANCE) {

                            final Instruction21c newInstance = (Instruction21c) predecessor;

                            if (newInstance.getRegisterA() == receiverRegister) {

                                final String receiverName = newInstance.getReference().toString();
                                final Optional<BroadcastReceiver> component
                                        = ComponentUtils.getBroadcastReceiverByName(components, receiverName);

                                if (component.isPresent()) {
                                    receiver = component.get();
                                    LOGGER.debug("Found dynamic receiver: " + receiver);
                                    receiver.setDynamicReceiver(true);
                                    if (action != null) {
                                        receiver.setAction(action);
                                        break;
                                    }
                                }
                            }
                        } else if (predecessor.getOpcode() == Opcode.IGET_OBJECT) {
                            // The broadcast receiver might be retrieved from an instance variable:
                            // iget-object v1, p0, Lcom/ichi2/anki/DeckOptions;->mUnmountReceiver:Landroid/content/BroadcastReceiver;
                            final String reference = ((ReferenceInstruction) predecessor).getReference().toString();
                            final String className = reference.split("->")[0];
                            final String variableName = reference.split("->")[1].split(":")[0];
                            final String returnType = reference.split(":")[1];

                            // TODO: Check if the return type can be any subclass of 'BroadcastReceiver'.
                            if (returnType.equals("Landroid/content/BroadcastReceiver;")) {
                                // TODO: Locate where the instance variable has been written. In most cases this is the
                                //  constructor but in fact it could be any method (hopefully inside of the given class).
                                LOGGER.warn("Backtracking instance variable of broadcast receiver is not yet supported!");
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
            // TODO: Merge with above implementation!
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
                boolean foundIntentFilter = false;

                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                // TODO: There might be theoretically multiple actions per intent filter.
                // the action described by the intent filter
                String action = null;

                // TODO: It is not guaranteed that the intent filter (action) is declared after the broadcast receiver!
                //  Consider the approach implemented above.

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
                                foundIntentFilter = true;
                            } else if (methodReference.equals("Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V")) {
                                // The action is passed via the addAction() method to the intent filter (v8)
                                // invoke-virtual {v0, v8}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V
                                foundIntentFilter = true;
                            }
                        } else if (foundIntentFilter
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
                        } else if (predecessor.getOpcode() == Opcode.IGET_OBJECT) {
                            // The broadcast receiver might be retrieved from an instance variable:
                            // iget-object v1, p0, Lcom/ichi2/anki/DeckOptions;->mUnmountReceiver:Landroid/content/BroadcastReceiver;
                            final String reference = ((ReferenceInstruction) predecessor).getReference().toString();
                            final String className = reference.split("->")[0];
                            final String variableName = reference.split("->")[1].split(":")[0];
                            final String returnType = reference.split(":")[1];

                            // TODO: Check if the return type can be any subclass of 'BroadcastReceiver'.
                            if (returnType.equals("Landroid/content/BroadcastReceiver;")) {
                                // TODO: Locate where the instance variable has been written. In most cases this is the
                                //  constructor but in fact it could be any method (hopefully inside of the given class).
                                LOGGER.warn("Backtracking instance variable of broadcast receiver is not yet supported!");
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
