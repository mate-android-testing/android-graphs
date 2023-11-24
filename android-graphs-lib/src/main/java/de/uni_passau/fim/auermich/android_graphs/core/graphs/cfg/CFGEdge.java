package de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.Edge;

import java.util.Objects;

/**
 * An edge in a CFG.
 */
public class CFGEdge extends Edge {

    @Override
    public CFGVertex getSource() {
        return (CFGVertex) super.getSource();
    }

    @Override
    public CFGVertex getTarget() {
        return (CFGVertex) super.getTarget();
    }

    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof CFGEdge)) {
            return false;
        }

        CFGEdge other = (CFGEdge) o;

        // compare edge based on source and target vertex of edge
        return this.getSource().equals(other.getSource())
                && this.getTarget().equals(other.getTarget());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSource(), getTarget());
    }

    @Override
    public CFGEdge clone() {
        return (CFGEdge) super.clone();
    }
}
