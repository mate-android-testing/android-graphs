package de.uni_passau.fim.auermich.android_graphs.cli.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;

@Parameters(commandDescription = "Produces an inter-procedural CFG.")
public class InterCFGCommand {

    private GraphType graphType = GraphType.INTERCFG;

    @Parameter(names = {"-art"}, description = "Whether ART classes should be resolved.")
    private boolean art = false;

    @Parameter(names = {"-oaut", "-only-aut"}, description = "Whether only AUT classes should be resolved.")
    private boolean resolveOnlyAUTClasses = false;

    @Parameter(names = { "-b", "-basic-blocks" }, description = "Whether to use basic blocks or not.")
    private boolean useBasicBlocks = false;

    public boolean resolveOnlyAUTClasses() {
        return resolveOnlyAUTClasses;
    }

    public boolean resolveARTClasses() {
        return art;
    }

    public boolean isUseBasicBlocks() {
        return useBasicBlocks;
    }

    public GraphType getGraphType() {
        return graphType;
    }
}
