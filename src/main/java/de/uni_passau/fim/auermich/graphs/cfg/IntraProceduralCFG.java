package de.uni_passau.fim.auermich.graphs.cfg;

import de.uni_passau.fim.auermich.graphs.GraphType;

public class IntraProceduralCFG extends BaseCFG {

    private static final GraphType GRAPH_TYPE = GraphType.INTRACFG;

    /*
    * Contains the full-qualified name of the method,
    * e.g. className->methodName(p0...pN)ReturnType.
     */
    private final String methodName;

    public IntraProceduralCFG(String methodName) {
        super();
        this.methodName = methodName;
    }

}
