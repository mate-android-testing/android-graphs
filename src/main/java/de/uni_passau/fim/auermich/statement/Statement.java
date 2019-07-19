package de.uni_passau.fim.auermich.statement;

public abstract class Statement {

    // the method the statement is belonging to
    protected final String method;

    // the statement type
    protected StatementType type;

    public Statement(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public StatementType getType() {
        return type;
    }

    public abstract String toString();

    public abstract boolean equals(Object o);

    public abstract int hashCode();

    public enum StatementType {
        ENTRY_STATEMENT,
        EXIT_STATEMENT,
        BLOCK_STATEMENT, // basic blocks may need to be splitted into two blocks
        RETURN_STATEMENT,
        // INVOKE_STATEMENT,
        // LEADER_STATEMENT, // entry, exit, return, (return_stmt), if, branch_target(s), invoke-stmts!!!
        BASIC_STATEMENT;
    }

}