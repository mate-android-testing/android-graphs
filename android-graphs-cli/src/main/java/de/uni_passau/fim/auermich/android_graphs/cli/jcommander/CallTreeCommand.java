package de.uni_passau.fim.auermich.android_graphs.cli.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;

@Parameters(commandDescription = "Produces a call tree.")
public class CallTreeCommand {

    private GraphType graphType = GraphType.CALLTREE;

    @Parameter(names = {"-art"}, description = "Whether ART classes should be resolved.")
    private boolean art = false;

    @Parameter(names = {"-oaut", "-only-aut"}, description = "Whether only AUT classes should be resolved.")
    private boolean resolveOnlyAUTClasses = false;

    public boolean resolveOnlyAUTClasses() {
        return resolveOnlyAUTClasses;
    }

    public boolean resolveARTClasses() {
        return art;
    }

    public GraphType getGraphType() {
        return graphType;
    }
}
