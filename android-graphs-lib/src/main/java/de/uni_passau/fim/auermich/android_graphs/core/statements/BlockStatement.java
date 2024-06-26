package de.uni_passau.fim.auermich.android_graphs.core.statements;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BlockStatement extends Statement implements Cloneable {

    private List<Statement> statements;

    public BlockStatement(String method) {
        super(method);
        statements = new ArrayList<>();
        type = StatementType.BLOCK_STATEMENT;
    }

    public BlockStatement(String method, List<Statement> statements) {
        super(method);
        this.statements = statements;
        type = StatementType.BLOCK_STATEMENT;
    }

    public List<Statement> getStatements() { return statements; }

    public Statement getFirstStatement() {
        return statements.get(0);
    }

    public Statement getLastStatement() {
        return statements.get(statements.size()-1);
    }

    public void addStatement(Statement statement) {
        statements.add(statement);
    }

    public void removeStatement(Statement statement) {
        statements.remove(statement);
    }

    public void addStatement(int index, Statement statement) {
        statements.add(index, statement);
    }

    public void removeStatement(int index) {
        statements.remove(index);
    }

    @Override
    public String toString() {
        return Joiner.on(System.lineSeparator()).join(statements);
    }

    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof BlockStatement)) {
            return false;
        }

        BlockStatement other = (BlockStatement) o;

        // unique method signature + individual statements
        return this.method.equals(other.method) &&
                this.statements.equals(other.statements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, statements);
    }


    @Override
    public BlockStatement clone() {
        BlockStatement clone = (BlockStatement) super.clone();
        return clone;

        // FIXME: enabling clone() on each statements breaks the implementation
        /*
        List<Statement> stmts = new ArrayList<>();
        for (Statement stmt : statements) {
            stmts.add(stmt.clone());
        }
        clone.statements = stmts;
        return clone;
        */
    }

}
