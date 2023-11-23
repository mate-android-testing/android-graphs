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

import java.util.HashMap;
import java.util.Map;

public final class MediaPlayerUtils {

    private static final Logger LOGGER = LogManager.getLogger(MediaPlayerUtils.class);

    private MediaPlayerUtils() {
        throw new UnsupportedOperationException("Utility class!");
    }

    // TODO: Add additional registration, listener and callback for MediaPlayer class.

    private static final Map<String, String> REGISTRATION_METHOD_TO_LISTENER_MAPPING = new HashMap<>(){{
       put("Landroid/media/MediaPlayer;->setOnBufferingUpdateListener(Landroid/media/MediaPlayer$OnBufferingUpdateListener;)V",
               "Landroid/media/MediaPlayer$OnBufferingUpdateListener;");
       put("Landroid/media/MediaPlayer;->setOnInfoListener(Landroid/media/MediaPlayer$OnInfoListener;)V",
               "Landroid/media/MediaPlayer$OnInfoListener;");
       put("Landroid/media/MediaPlayer;->setOnPreparedListener(Landroid/media/MediaPlayer$OnPreparedListener;)V",
               "Landroid/media/MediaPlayer$OnPreparedListener;");
       put("Landroid/media/MediaPlayer;->setOnErrorListener(Landroid/media/MediaPlayer$OnErrorListener;)V",
               "Landroid/media/MediaPlayer$OnErrorListener;");
    }};

    private static final Map<String, String> LISTENER_TO_CALLBACK_MAPPING = new HashMap<>(){{
        put("Landroid/media/MediaPlayer$OnBufferingUpdateListener;", "onBufferingUpdate(Landroid/media/MediaPlayer;I)V");
        put("Landroid/media/MediaPlayer$OnInfoListener;", "onInfo(Landroid/media/MediaPlayer;II)Z");
        put("Landroid/media/MediaPlayer$OnPreparedListener;", "onPrepared(Landroid/media/MediaPlayer;)V");
        put("Landroid/media/MediaPlayer$OnErrorListener;", "onError(Landroid/media/MediaPlayer;II)Z");
    }};

    // https://developer.android.com/reference/android/media/MediaPlayer#callbacks
    public static boolean isMediaPlayerListenerInvocation(String methodSignature) {
        return REGISTRATION_METHOD_TO_LISTENER_MAPPING.containsKey(methodSignature);
    }

    /**
     * Retrieves the callback method associated with the given registration method if possible.
     *
     * @param registrationMethod The registration method of the listener, e.g., setOnErrorListener().
     * @param invokeInstruction The invoke instruction referring to the listener registration.
     * @param classHierarchy A mapping from a class name to its class instance.
     * @return Returns the callback associated with the given registration method if possible, otherwise {@code null}.
     */
    public static String getListenerCallback(final String registrationMethod, final AnalyzedInstruction invokeInstruction,
                                             final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example (1):
        // iget-object v1, p0, Lorg/y20k/transistor/PlayerService;->mMediaPlayer:Landroid/media/MediaPlayer;
        // invoke-virtual {v1, p0}, Landroid/media/MediaPlayer;->setOnInfoListener(Landroid/media/MediaPlayer$OnInfoListener;)V

        // Example (2):
        // new-instance v2, Lorg/y20k/transistor/PlayerService$1;
        // invoke-direct {v2, p0}, Lorg/y20k/transistor/PlayerService$1;-><init>(Lorg/y20k/transistor/PlayerService;)V
        // invoke-virtual {v1, v2}, Landroid/media/MediaPlayer;->setOnBufferingUpdateListener(Landroid/media/MediaPlayer$OnBufferingUpdateListener;)V

        final String listener = REGISTRATION_METHOD_TO_LISTENER_MAPPING.get(registrationMethod);
        final String callback = LISTENER_TO_CALLBACK_MAPPING.get(listener);

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The listener class is stored in the second register passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterD();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (v3 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.NEW_INSTANCE) {
                    // new-instance v2, Lorg/y20k/transistor/PlayerService$1;
                    final String className = ((ReferenceInstruction) predecessor).getReference().toString();
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classDef.getInterfaces().contains(listener)) {
                        LOGGER.debug("Found callback: " + className + "->" + callback);
                        return className + "->" + callback;
                    }
                } else if (predecessor.getOpcode() == Opcode.IGET_OBJECT
                        && ((Instruction22c) predecessor).getRegisterB() == targetRegister) {
                    // iget-object v1, p0, Lorg/y20k/transistor/PlayerService;->mMediaPlayer:Landroid/media/MediaPlayer;
                    final String reference = ((ReferenceInstruction) predecessor).getReference().toString();
                    final String className = MethodUtils.getClassName(reference);
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classDef.getInterfaces().contains(listener)) {
                        LOGGER.debug("Found callback: " + className + "->" + callback);
                        return className + "->" + callback;
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
