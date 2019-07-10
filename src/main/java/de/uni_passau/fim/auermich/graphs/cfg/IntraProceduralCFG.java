package de.uni_passau.fim.auermich.graphs.cfg;

import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;

public class IntraProceduralCFG extends BaseCFG {

    private static final GraphType GRAPH_TYPE = GraphType.INTRACFG;

    public IntraProceduralCFG(String methodName) {
        super(methodName);
    }
}
