package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AnimationUtils {

    private static final Logger LOGGER = LogManager.getLogger(AnimationUtils.class);

    private AnimationUtils() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Checks whether the given method refers to animation invocation.
     * NOTE: We check against the invocation of setAnimationListener() but ideally one should check for startAnimation().
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if the method refers to an animation invocation, otherwise {@code false}.
     */
    public static boolean isAnimationInvocation(final String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        return method.equals("setAnimationListener(Landroid/view/animation/Animation$AnimationListener;)V");
    }

    /**
     * Checks whether the given class represents an animation listener.
     *
     * @param classDef The class to be checked.
     * @return Returns {@code true} if the given class represents an animation listener.
     */
    public static boolean isAnimationListener(final ClassDef classDef) {
        return classDef.getInterfaces().contains("Landroid/view/animation/Animation$AnimationListener;");
    }

    // https://developer.android.com/reference/android/animation/Animator.AnimatorListener#summary

    public static String getOnAnimationEndMethod(final String className) {
        return className + "->" + "onAnimationEnd(Landroid/view/animation/Animation;)V";
    }

    public static String getOnAnimationStartMethod(final String className) {
        return className + "->" + "onAnimationStart(Landroid/view/animation/Animation;)V";
    }

    public static String getOnAnimationCancelMethod(final String className) {
        return className + "->" + "onAnimationCancel(Landroid/view/animation/Animation;)V";
    }

    public static String getOnAnimationRepeatMethod(final String className) {
        return className + "->" + "onAnimationRepeat(Landroid/view/animation/Animation;)V";
    }

    /**
     * Retrieves the animation listener class by backtracking the setAnimationListener() invocation.
     * NOTE: {@link #isAnimationInvocation(String)} needs to be called in advance.
     *
     * @param analyzedInstruction The invoke instruction referring to the setAnimationListener() call.
     * @return Returns the name of the animation listener class if retrievable.
     */
    public static String getAnimationListener(final AnalyzedInstruction analyzedInstruction) {

        // invoke-virtual {v0, v1}, Landroid/view/animation/Animation;->
        // setAnimationListener(Landroid/view/animation/Animation$AnimationListener;)V
        Instruction35c invokeVirtual = (Instruction35c) analyzedInstruction.getInstruction();

        // the id of the register which refers to the animation listener (v1 above)
        int animationListenerRegister = invokeVirtual.getRegisterD();

        assert !analyzedInstruction.getPredecessors().isEmpty();
        AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

        // search backwards for the animation listener class
        while (predecessor.getInstructionIndex() != -1) {

            final Instruction pred = predecessor.getInstruction();

            // new-instance v1, Lorg/y20k/transistor/PlayerActivityFragment$5;
            if (pred.getOpcode() == Opcode.NEW_INSTANCE && predecessor.setsRegister(animationListenerRegister)) {
                return ((Instruction21c) pred).getReference().toString();
            }

            if (predecessor.getPredecessors().isEmpty()) {
                // there is no predecessor -> target activity name might be defined somewhere else or external
                return null;
            } else {
                // TODO: may use recursive search over all predecessors
                predecessor = predecessor.getPredecessors().first();
            }
        }

        return null;
    }
}
