package de.uni_passau.fim.auermich.statement;

import java.util.Objects;

public class EntryStatement extends Statement {

    public EntryStatement(String method) {
        super(method);
        type = StatementType.ENTRY_STATEMENT;
    }

    @Override
    public String toString() {
        return "entry " + method;
    }

    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof EntryStatement)) {
            return false;
        }

        EntryStatement other = (EntryStatement) o;

        // unique method signature + instruction id
        return this.method.equals(other.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method);
    }
}