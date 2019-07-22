package de.uni_passau.fim.auermich.graphs.cfg;

import de.uni_passau.fim.auermich.graphs.GraphType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InterProceduralCFG extends BaseCFG implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(InterProceduralCFG.class);

    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    public InterProceduralCFG(String methodName) {
        super(methodName);
    }

    public InterProceduralCFG clone() {

        InterProceduralCFG cloneCFG = (InterProceduralCFG) super.clone();
        return cloneCFG;
    }

}
