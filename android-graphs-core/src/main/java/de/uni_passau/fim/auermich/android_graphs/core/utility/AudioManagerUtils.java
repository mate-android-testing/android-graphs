package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AudioManagerUtils {

    private static final Logger LOGGER = LogManager.getLogger(AudioManagerUtils.class);

    /**
     * The listener that triggers the onAudioFocusChange() callback.
     */
    private static final String AUDIO_MANAGER_LISTENER = "Landroid/media/AudioManager$OnAudioFocusChangeListener;";

    /**
     * The callback triggered by the OnAudioFocusChangeListener listener.
     */
    private static final String AUDIO_MANAGER_CALLBACK = "onAudioFocusChange(I)V";

    private AudioManagerUtils() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Checks whether the given method represents an OnAudioFocusChangeListener registration.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if method represents an OnAudioFocusChangeListener registration, otherwise {@code null}.
     */
    public static boolean isAudioManagerInvocation(final String methodSignature) {
        return methodSignature.equals("Landroid/media/AudioManager;->" +
                "requestAudioFocus(Landroid/media/AudioManager$OnAudioFocusChangeListener;II)I");
    }

    /**
     * Retrieves the callback method associated with the given registration method if possible.
     *
     * @param invokeInstruction The invoke instruction referring to the listener registration.
     * @param classHierarchy A mapping from a class name to its class instance.
     * @return Returns the callback associated with the given registration method if possible, otherwise {@code null}.
     */
    public static String getAudioManagerCallback(final AnalyzedInstruction invokeInstruction,
                                                 final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:
        //    iget-object v2, p0, Lorg/y20k/transistor/PlayerService;->mAudioManager:Landroid/media/AudioManager;
        //    const/4 v3, 0x3
        //    invoke-virtual {v2, p0, v3, v1}, Landroid/media/AudioManager;->
        //    requestAudioFocus(Landroid/media/AudioManager$OnAudioFocusChangeListener;II)I

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The listener class is stored in the second register passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterD();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (v3 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (predecessor.getOpcode() == Opcode.IGET_OBJECT
                        && ((Instruction22c) predecessor).getRegisterB() == targetRegister) {
                    // iget-object v2, p0, Lorg/y20k/transistor/PlayerService;->mAudioManager:Landroid/media/AudioManager;
                    final String reference = ((ReferenceInstruction) predecessor).getReference().toString();
                    final String className = MethodUtils.getClassName(reference);
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classDef.getInterfaces().contains(AUDIO_MANAGER_LISTENER)) {
                        LOGGER.debug("Found callback: " + className + "->" + AUDIO_MANAGER_CALLBACK);
                        return className + "->" + AUDIO_MANAGER_CALLBACK;
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
