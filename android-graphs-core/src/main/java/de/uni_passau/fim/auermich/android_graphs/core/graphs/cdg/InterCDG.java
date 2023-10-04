package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;

public class InterCDG extends CDG {

    public InterCDG(BaseCFG interCFG) {
        super(interCFG);
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.INTERCDG;
    }
}
