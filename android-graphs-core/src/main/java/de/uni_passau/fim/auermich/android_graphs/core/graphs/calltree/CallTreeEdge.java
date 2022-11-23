package de.uni_passau.fim.auermich.android_graphs.core.graphs.calltree;

import org.jgrapht.graph.DefaultEdge;

import java.util.Objects;

public class CallTreeEdge extends DefaultEdge implements Cloneable {

    @Override
    protected CallTreeVertex getSource() {
        return (CallTreeVertex) super.getSource();
    }

    @Override
    protected CallTreeVertex getTarget() {
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

    public CallTreeEdge clone() {
        CallTreeEdge clone = (CallTreeEdge) super.clone();
        return clone;
    }
}
