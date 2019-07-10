package de.uni_passau.fim.auermich.graphs;

import org.jgrapht.graph.DefaultEdge;

public class Edge extends DefaultEdge {

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
}
