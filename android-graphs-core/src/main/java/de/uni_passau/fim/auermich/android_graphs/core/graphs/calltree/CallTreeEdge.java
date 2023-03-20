package de.uni_passau.fim.auermich.android_graphs.core.graphs.calltree;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.Edge;

import java.util.Objects;

/**
 * An edge in the {@link CallTree}.
 */
public class CallTreeEdge extends Edge {

    @Override
    public CallTreeVertex getSource() {
        return (CallTreeVertex) super.getSource();
    }

    @Override
    public CallTreeVertex getTarget() {
        return (CallTreeVertex) super.getTarget();
    }

    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof CallTreeEdge)) {
            return false;
        }

        CallTreeEdge other = (CallTreeEdge) o;

        // compare edge based on source and target vertex of edge
        return this.getSource().equals(other.getSource())
                && this.getTarget().equals(other.getTarget());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSource(), getTarget());
    }

    @Override
    public CallTreeEdge clone() {
        return (CallTreeEdge) super.clone();
    }
}
