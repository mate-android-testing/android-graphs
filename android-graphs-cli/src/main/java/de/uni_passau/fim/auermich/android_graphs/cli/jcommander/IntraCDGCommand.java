package de.uni_passau.fim.auermich.android_graphs.cli.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;

@Parameters(commandDescription = "Produces an intra-procedural CDG for a given method.")
public class IntraCDGCommand {

    private GraphType graphType = GraphType.INTRACDG;

    @Parameter(names = { "-t", "-target" }, description = "The full-qualified name of the target method.", required = true)
    private String target;

    @Parameter(names = { "-b", "-basic-blocks" }, description = "Whether to use basic blocks or not.")
    private boolean useBasicBlocks = false;

    public boolean isUseBasicBlocks() {
        return useBasicBlocks;
    }

    public String getTarget() {
        return target;
    }

    public GraphType getGraphType() {
        return graphType;
    }
}
