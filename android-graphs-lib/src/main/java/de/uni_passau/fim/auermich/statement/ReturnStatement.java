package de.uni_passau.fim.auermich.statement;

import java.util.Objects;

public class ReturnStatement extends Statement implements Cloneable {

    /**
     * Store the instruction index of the invoke statement in order
     * to make a return statement unique within a method. Based on the
     * id, we can assign which invoke statement is linked to which return
     * statement.
     */
    private final int id;

    // stores the method name from which control flow returned
    private String targetMethod;

    public ReturnStatement(String method, String targetMethod, int id) {
        super(method);
        this.targetMethod = targetMethod;
        type = StatementType.RETURN_STATEMENT;
        this.id = id;
    }

    @Override
    public String toString() {
        return "Return to: " + method + " (" + id + ")";
    }


    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof ReturnStatement)) {
            return false;
        }

        ReturnStatement other = (ReturnStatement) o;

        // unique method signature of both source and target
        return this.method.equals(other.method)
                && this.id == other.id
                && this.targetMethod.equals(other.targetMethod);

    }

    @Override
    public int hashCode() {
        return Objects.hash(method, targetMethod, id);
    }

    public String getTargetMethod() {
        return targetMethod;
    }

    public ReturnStatement clone() {
        ReturnStatement stmt = (ReturnStatement) super.clone();
        stmt.targetMethod = this.getTargetMethod();
        return stmt;
    }

}
