package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;

public class IntraCDG extends CDG {

    public IntraCDG(BaseCFG intraCFG) {
        super(intraCFG);
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.INTRACDG;
    }
}
