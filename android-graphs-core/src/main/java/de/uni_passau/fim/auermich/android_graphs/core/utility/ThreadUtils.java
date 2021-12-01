package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Provides utility functions to check whether a given method represents the start() method of a Thread class
 * or the run() method of a Thread or Runnable class.
 */
public final class ThreadUtils {

    private static final Logger LOGGER = LogManager.getLogger(ThreadUtils.class);

    private ThreadUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Checks whether the given method represents the start() or run() method of the Thread/Runnable class.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if the method refers to the run method,
     * otherwise {@code false} is returned.
     */
    private static boolean isStartOrRunMethod(String methodSignature) {
        return methodSignature.equals("Ljava/lang/Runnable;->run()V")
                || methodSignature.equals("Ljava/lang/Thread;->start()V");
    }

    /**
     * Checks whether the given method represents either the start() method of a Thread class or the
     * run() method of either a Thread or Runnable class.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if the method is either the start() or run() method of a Thread
     *          or Runnable class, otherwise {@code false} is returned.
     */
    public static boolean isThreadMethod(final ClassHierarchy classHierarchy, final String methodSignature) {

        String methodName = MethodUtils.getMethodName(methodSignature);

        if (!methodName.equals("run()V") && !methodName.equals("start()V")) {
            // not even matching the method name
            return false;
        }

        if (isStartOrRunMethod(methodSignature)) {
            return true;
        } else {
            // We need to check that the start() or run() method belongs actually to a Thread/Runnable.
            final String className = MethodUtils.getClassName(methodSignature);
            List<String> superClasses = classHierarchy.getSuperClasses(className);

            if (methodName.endsWith("start()V")) {
                // the start() method is only contained in a subclass of a Thread
                return superClasses.contains("Ljava/lang/Thread;");
            } else {
                // the run() can both belong to a Thread or Runnable
                return superClasses.contains("Ljava/lang/Thread;") || superClasses.contains("Ljava/lang/Runnable;");
            }
        }
    }
}
