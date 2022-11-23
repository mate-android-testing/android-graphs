package de.uni_passau.fim.auermich.android_graphs.core.graphs.calltree;

import de.uni_passau.fim.auermich.android_graphs.core.utility.MethodUtils;

import java.util.Objects;

/**
 * A vertex in the {@link CallTree}. Such a vertex represents a single method.
 */
public class CallTreeVertex implements Comparable<CallTreeVertex> {

    /**
     * The encapsulated method.
     */
    private final String method;

    public CallTreeVertex(String method) {
        this.method = method;
    }

    public String getClassName() {
        if (method.startsWith("callbacks ")) {
            return method.split("callbacks ")[1];
        } else {
            return MethodUtils.getClassName(method);
        }
    }

    public String getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallTreeVertex that = (CallTreeVertex) o;
        return Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method);
    }

    @Override
    public int compareTo(CallTreeVertex other) {
        return this.method.compareTo(other.method);
    }
}
