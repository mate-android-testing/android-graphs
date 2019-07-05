package de.uni_passau.fim.auermich.graphs;

import org.jgrapht.graph.DefaultEdge;

public class Edge extends DefaultEdge {

    public Edge() {
        super();
    }

    @Override
    public String toString() {
        return "(" + this.getSource() + "->" + this.getTarget() + ")";
    }
}
