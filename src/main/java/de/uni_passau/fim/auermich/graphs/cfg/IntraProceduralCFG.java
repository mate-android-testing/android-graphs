package de.uni_passau.fim.auermich.graphs.cfg;

import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.Vertex;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jgrapht.graph.AbstractGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;

public class IntraProceduralCFG {

    private AbstractGraph graph = new DirectedMultigraph(DefaultEdge.class);
    private final String methodName;
    private Vertex entry = new Vertex(-1, null);
    private Vertex exit = new Vertex(-2, null);

    public IntraProceduralCFG(String methodName) {
        this.methodName = methodName;
        graph.addVertex(entry);
        graph.addVertex(exit);
    }

    public void addEdge(Vertex src, Vertex dest) {
        graph.addEdge(src, dest);
    }

    public void addVertex(Vertex vertex) {
        graph.addVertex(vertex);
    }

    public Vertex getEntry() {
        return entry;
    }

    public Vertex getExit() {
        return exit;
    }

    @Override
    public String toString() {
        return graph.toString();
    }

    public void processStmt(Instruction instruction) {

    }
}
