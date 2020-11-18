package de.uni_passau.fim.auermich.graphs;

public interface BaseGraph {

    String toString();

    void drawGraph();

    // number of vertices
    int size();

    GraphType getGraphType();

    /**
     * Queries the vertex from the graph specified through the trace.
     * The trace obeys the following format:
     *      className->methodName->instructionIndex
     *
     * whereas the instruction index might be optional (query for virtual entry/exit vertex).
     *
     * @param trace Identifies the vertex in the graph.
     * @return Returns the vertex corresponding to the trace.
     */
    Vertex lookUpVertex(String trace);
}
