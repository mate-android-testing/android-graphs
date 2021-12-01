package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Constitutes an AsyncTask, see https://developer.android.com/reference/android/os/AsyncTask.
 */
public class AsyncTaskUtils {

    private static final Logger LOGGER = LogManager.getLogger(AsyncTaskUtils.class);

    private AsyncTaskUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Checks whether the given method represents an AsyncTask invocation, i.e. a call to execute().
     *
     * @param methodSignature The method to be checked for a method invocation.
     * @return Returns {@code true} if the method refers to a AsyncTask invocation,
     *          otherwise {@code false} is returned.
     */
    public static boolean isAsyncTaskInvocation(String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        return method.equals("execute([Ljava/lang/Object;)Landroid/os/AsyncTask;");
    }

    /**
     * Returns the onPreExecute() method of an AsyncTask.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the onPreExecute() method.
     */
    public static String getOnPreExecuteMethod(String className) {
        return className + "->onPreExecute()V";
    }

    /**
     * Returns the doInBackground() method of an AsyncTask.
     * NOTE: At the bytecode level, this method is a bridge method with fixed parameters,
     * which in turn calls the original doInBackground() method.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the doInBackground() method.
     */
    public static String getDoInBackgroundMethod(String className) {
        return className + "->doInBackground([Ljava/lang/Object;)Ljava/lang/Object;";
    }

    /**
     * Returns the onProgressUpdate() method of an AsyncTask.
     * NOTE: At the bytecode level, this method is a bridge method with fixed parameters,
     * which in turn calls the original onProgressUpdate() method.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the onProgressUpdate() method.
     */
    public static String getOnProgressUpdateMethod(String className) {
        return className + "->onProgressUpdate([Ljava/lang/Object;)V";
    }

    /**
     * Returns the onPostExecute() method of an AsyncTask.
     * NOTE: At the bytecode level, this method is a bridge method with fixed parameters,
     * which in turn calls the original onPostExecute() method.
     *
     * @param className The class in which the method is defined.
     * @return Returns the signature of the onPostExecute() method.
     */
    public static String getOnPostExecuteMethod(String className) {
        return className + "->onPostExecute(Ljava/lang/Object;)V";
    }
}
