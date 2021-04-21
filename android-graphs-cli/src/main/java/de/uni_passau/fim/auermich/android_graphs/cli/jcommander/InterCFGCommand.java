package de.uni_passau.fim.auermich.android_graphs.cli.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;

@Parameters(commandDescription = "Produces an inter-procedural CFG.")
public class InterCFGCommand {

    private GraphType graphType = GraphType.INTERCFG;

    /*
    * TODO: Provide a field that enables an approach level / branch distance request.
    *  This requires a target vertex or a list of target vertices plus a path to
    *  a traces or multiple traces files. I assume that we need for this an additional
    *  sub command that encompasses a metric (approach level / branch distance), a target
    *  and the path to the traces file(s).
     */

    @Parameter(names = {"-art"}, description = "Whether ART classes should be resolved.")
    private boolean art = false;

    @Parameter(names = { "-m", "-metric" }, description = "Metric.")
    private String metric;

    @Parameter(names = { "-b", "-basic-blocks" }, description = "Whether to use basic blocks or not.")
    private boolean useBasicBlocks = false;

    public boolean resolveARTClasses() {
        return art;
    }

    public boolean isUseBasicBlocks() {
        return useBasicBlocks;
    }

    public String getMetric() {
        return metric;
    }

    public GraphType getGraphType() {
        return graphType;
    }
}
