package de.uni_passau.fim.auermich.android_graphs.core.statements;

import java.util.Objects;

public class ExitStatement extends Statement implements Cloneable {

    public ExitStatement(String method) {
        super(method);
        type = StatementType.EXIT_STATEMENT;
    }

    @Override
    public String toString() {
        return "exit " + method;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof ExitStatement)) {
            return false;
        }

        ExitStatement other = (ExitStatement) o;

        // unique method signature + instruction id
        return this.method.equals(other.method)
                && this.type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(method);
    }

    public ExitStatement clone() {
        return (ExitStatement) super.clone();
    }

}
