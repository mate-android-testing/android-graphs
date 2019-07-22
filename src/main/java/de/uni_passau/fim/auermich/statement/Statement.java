package de.uni_passau.fim.auermich.statement;

import de.uni_passau.fim.auermich.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Statement implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(Statement.class);

    // the method the statement is belonging to
    protected String method;

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

    public Statement clone() {
        try {
            Statement cloneStmt = (Statement) super.clone();
            // added two
            cloneStmt.method = this.getMethod();
            cloneStmt.type = this.getType();
            return cloneStmt;
        } catch (CloneNotSupportedException e) {
            LOGGER.warn("Cloning of Statement failed" + e.getMessage());
            return null;
        }
    }

}
