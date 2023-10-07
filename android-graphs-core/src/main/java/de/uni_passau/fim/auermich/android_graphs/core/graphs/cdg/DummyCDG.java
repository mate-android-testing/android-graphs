package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGVertex;

public class DummyCDG extends BaseCFG {

    public DummyCDG(String methodName) {
        super(methodName);
    }

    public DummyCDG(BaseCFG baseCFG) {
        super(baseCFG.getMethodName());
    }

    @Override
    public CFGVertex lookUpVertex(String trace) {
        throw new UnsupportedOperationException("Look up of vertex in dummy CDG not yet supported!");
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.INTRACDG;
    }

    @Override
    public BaseCFG copy() {
        throw new UnsupportedOperationException("Copy of dummy CDG not yet supported!");
    }
}
