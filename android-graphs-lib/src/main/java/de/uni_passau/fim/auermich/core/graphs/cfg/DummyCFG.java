package de.uni_passau.fim.auermich.core.graphs.cfg;

import de.uni_passau.fim.auermich.core.graphs.GraphType;
import de.uni_passau.fim.auermich.core.graphs.Vertex;

/**
 * A skeleton of a CFG that only wraps the entry and exit vertex.
 * This class is used to reduce the memory consumption.
 */
public class DummyCFG extends BaseCFG {


    public DummyCFG(String methodName) {
        super(methodName);
    }

    public DummyCFG(BaseCFG baseCFG) {
        super(baseCFG.getMethodName());
    }

    @Override
    public Vertex lookUpVertex(String trace) {
        throw new UnsupportedOperationException("Look up of vertex in dummy CFG not yet supported!");
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.INTRACFG;
    }

    @Override
    public BaseCFG copy() {
        throw new UnsupportedOperationException("Copy of dummy CFG not yet supported!");
    }
}
