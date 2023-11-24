package de.uni_passau.fim.auermich.android_graphs.core.utility;

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
     * Maps the showDialog() invocation to its corresponding onCreateDialog() method.
     * NOTE: This method should be only called when {@link #isDialogInvocation(String)} returns {@code true}.
     *
     * @param methodSignature The showDialog() method.
     * @return Returns the corresponding onCreateDialog() method.
     */
    public static String getOnCreateDialogMethod(String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        if (method.equals("showDialog(I)V")) {
            return "onCreateDialog(I)Landroid/app/Dialog;";
        } else if (method.equals("showDialog(ILandroid/os/Bundle;)V")) {
            return "onCreateDialog(ILandroid/os/Bundle;)Landroid/app/Dialog;";
        } else {
            throw new IllegalArgumentException("Method " + methodSignature + " doesn't refer to a dialog invocation!");
        }
    }
}
