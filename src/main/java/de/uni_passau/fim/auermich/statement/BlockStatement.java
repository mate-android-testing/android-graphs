package de.uni_passau.fim.auermich.statement;

import java.util.List;
import java.util.Objects;

public class BlockStatement extends Statement {

    private final List<Statement> statements;

    public BlockStatement(String method, List<Statement> statements) {
        super(method);
        this.statements = statements;
        type = StatementType.BLOCK_STATEMENT;
    }

    public Statement getFirstStatement() {
        return statements.get(0);
    }

    public Statement getLastStatement() {
        return statements.get(statements.size()-1);
    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        for (Statement statement : statements) {
            builder.append(statement);
            builder.append(System.lineSeparator());
        }

        // remove last new line
        builder.setLength(builder.length() -1);
        // builder.deleteCharAt(builder.length() - 1);
        return builder.toString();

        // doesn't seem to work as expected unfortunately
        // return String.join(System.lineSeparator(), statements.toString());
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
}