package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class DialogUtils {

    private static final Logger LOGGER = LogManager.getLogger(DialogUtils.class);

    /**
     * The recognized dialog invocation methods. Actually, those methods have been deprecated
     * since Android API 15. See the following link for more information:
     * https://developer.android.com/reference/android/app/Activity#showDialog(int,%20android.os.Bundle)
     */
    private static final Set<String> DIALOG_INVOCATIONS = new HashSet<>() {{
        add("showDialog(I)V");
        add("showDialog(ILandroid/os/Bundle;)V");
        add("show(Landroid/support/v4/app/FragmentManager;Ljava/lang/String;)V");
        add("show(Landroidx/fragment/app/FragmentManager;Ljava/lang/String;)V");
        add("show(Landroidx/fragment/app/FragmentTransaction;Ljava/lang/String;)I");
        add("show(Landroid/support/v4/app/FragmentTransaction;Ljava/lang/String;)I");
    }};

    /**
     * Checks whether the given method represents a dialog invocation, i.e. a call to showDialog().
     *
     * @param methodSignature The method to be checked for a method invocation.
     * @return Returns {@code true} if the method refers to a dialog invocation,
     *          otherwise {@code false} is returned.
     */
    public static boolean isDialogInvocation(String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        return DIALOG_INVOCATIONS.contains(method);
    }

    /**
     * Tracks a dialog invocation back to its dialog.
     *
     * @param analyzedInstruction The invoke instruction referring to the show() dialog call.
     * @param classHierarchy A mapping from class name to its class.
     * @return Returns the class name of the dialog if possible otherwise {@code null}.
     */
    public static String isDialogInvocation(final AnalyzedInstruction analyzedInstruction,
                                            final ClassHierarchy classHierarchy) {

        // Example:
        // iget-object v0, p0, Lio/github/zwieback/familyfinance/business/chart/fragment/PieChartOfExpensesFragment;
        //                          ->display:Lio/github/zwieback/familyfinance/business/chart/display/ChartDisplay;
        // check-cast v0, Lio/github/zwieback/familyfinance/business/chart/display/PieChartDisplay;
        // const v1, 0x7f0f01b1
        // invoke-static {v0, v1}, Lio/github/zwieback/familyfinance/business/chart/dialog/PieChartDisplayDialog;
        //                     ->newInstance(Lio/github/zwieback/familyfinance/business/chart/display/PieChartDisplay;I)
        //                     Lio/github/zwieback/familyfinance/business/chart/dialog/PieChartDisplayDialog;
        // move-result-object v0
        // invoke-virtual {p0}, Lio/github/zwieback/familyfinance/business/chart/fragment/PieChartOfExpensesFragment;
        //                      ->getChildFragmentManager()Landroid/support/v4/app/FragmentManager;
        // move-result-object v1
        // const-string v2, "PieChartDisplayDialog"
        // invoke-virtual {v0, v1, v2}, Landroid/support/v4/app/DialogFragment;
        //                              ->show(Landroid/support/v4/app/FragmentManager;Ljava/lang/String;)V

        // TODO: Verify that the extracted class is really a dialog class.

        final Instruction instruction = analyzedInstruction.getInstruction();
        final String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
        final String abstractDialogClass = MethodUtils.getClassName(targetMethod);
        final int targetRegister = ((Instruction35c) instruction).getRegisterC();

        if (analyzedInstruction.getPredecessors().isEmpty()) {
            // couldn't resolve dialog class
            return null;
        }

        // go back until we find const-class instruction which holds the service name
        AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

        while (pred.getInstructionIndex() != -1) {

            Instruction predecessor = pred.getInstruction();

            if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {

                // The return typ of the preceding invoke instruction refers to the concrete dialog class.
                pred = pred.getPredecessors().first();
                final String dialogClass = MethodUtils.getReturnType(((ReferenceInstruction)
                        pred.getInstruction()).getReference().toString());

                // Verify that the extracted class is actually a sub class of the supplied abstract dialog class.
                if (classHierarchy.getSuperClasses(dialogClass).contains(abstractDialogClass)) {
                    return dialogClass;
                } else {
                    return null;
                }
            }

            // consider next predecessor if available
            if (!analyzedInstruction.getPredecessors().isEmpty()) {
                pred = pred.getPredecessors().first();
            } else {
                break;
            }
        }

        return null; // couldn't derive the dialog class
    }

    /**
     * Maps the showDialog() invocation to its corresponding onCreateDialog() method.
     * NOTE: This method should be only called when {@link #isDialogInvocation(String)} returns {@code true}.
     *
     * @param methodSignature The showDialog() method.
     * @return Returns the corresponding onCreateDialog() method.
     */
    public static String getOnCreateDialogMethod(String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        if (method.equals("showDialog(I)V")) { // seems deprecated
            return "onCreateDialog(I)Landroid/app/Dialog;";
        } else if (method.equals("showDialog(ILandroid/os/Bundle;)V")) { // seems deprecated
            return "onCreateDialog(ILandroid/os/Bundle;)Landroid/app/Dialog;";
        } else if (method.equals("show(Landroid/support/v4/app/FragmentManager;Ljava/lang/String;)V")
                || method.equals("show(Landroidx/fragment/app/FragmentManager;Ljava/lang/String;)V")
                || method.equals("show(Landroidx/fragment/app/FragmentTransaction;Ljava/lang/String;)I")
                || method.equals("show(Landroid/support/v4/app/FragmentTransaction;Ljava/lang/String;)I")) {
            return "onCreateDialog(Landroid/os/Bundle;)Landroid/app/Dialog;";
        } else {
            throw new IllegalArgumentException("Method " + methodSignature + " doesn't refer to a dialog invocation!");
        }
    }
}
