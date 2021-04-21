package de.uni_passau.fim.auermich.android_graphs.core.graphs;

import org.jgrapht.graph.DefaultEdge;

import java.util.Objects;

public class Edge extends DefaultEdge implements Cloneable {

    public Edge() {
        super();
    }

    @Override
    public Vertex getSource() {
        return (Vertex) super.getSource();
    }

    @Override
    public Vertex getTarget() {
        return (Vertex) super.getTarget();
    }

    @Override
    public String toString() {
        return "(" + this.getSource() + "->" + this.getTarget() + ")";
    }

    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof Edge)) {
            return false;
        }

        Edge other = (Edge) o;

        // compare edge based on source and target vertex of edge
        return this.getSource().equals(other.getSource())
                && this.getTarget().equals(other.getTarget());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSource(), getTarget());
    }

    public Edge clone() {
        Edge clone = (Edge) super.clone();
        return clone;
    }
}
