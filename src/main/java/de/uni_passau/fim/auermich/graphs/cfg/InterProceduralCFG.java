package de.uni_passau.fim.auermich.graphs.cfg;

import de.uni_passau.fim.auermich.graphs.GraphType;

public class InterProceduralCFG extends BaseCFG {

    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    public InterProceduralCFG(String methodName) {
        super(methodName);
    }

}
