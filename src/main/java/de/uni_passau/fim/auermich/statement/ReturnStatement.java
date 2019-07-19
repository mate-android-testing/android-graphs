package de.uni_passau.fim.auermich.statement;

import java.util.Objects;

public class ReturnStatement extends Statement {

    // either tracks source and target name as String
    // or uses an additional int to make them unique

    // two distinct cases:
    // return statement should be unique among different intra CFGS (YES)
    // return statement should be unique among same intra CFG (multiple calls) (NO)

    // stores the method name from which control flow returned
    private final String targetMethod;

    // should be the target method name
    public ReturnStatement(String method, String targetMethod) {
        super(method);
        this.targetMethod = targetMethod;
        type = StatementType.RETURN_STATEMENT;
    }

    @Override
    public String toString() {
        return targetMethod;
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
                && this.targetMethod.equals(other.targetMethod);

    }

    @Override
    public int hashCode() {
        return Objects.hash(method, targetMethod);
    }

    public String getTargetMethod() {
        return targetMethod;
    }
}