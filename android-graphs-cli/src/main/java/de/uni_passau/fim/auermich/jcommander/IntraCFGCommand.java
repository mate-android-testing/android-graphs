package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import de.uni_passau.fim.auermich.graphs.GraphType;

@Parameters(commandDescription = "Produces an intra-procedural CFG for a given method.")
public class IntraCFGCommand {

    private GraphType graphType = GraphType.INTRACFG;

    @Parameter(names = { "-m", "-metric" }, description = "Metric.")
    private String metric;

    /**
     * TODO: provide the full-qualified name as input (className + method signature)
     * Currently, we simple check for the first match on the specified method name, which
     * is not unique within the defined class (overloading) nor unique among classes,
     * e.g. onCreate is defined in every class.
     */
    @Parameter(names = { "-t", "-target" }, description = "The full-qualified name of the target method.", required = true)
    private String target;

    @Parameter(names = { "-b", "-basic-blocks" }, description = "Whether to use basic blocks or not.")
    private boolean useBasicBlocks = false;

    public boolean isUseBasicBlocks() {
        return useBasicBlocks;
    }

    /**
     * TODO: use -d, -draw parameter in combination with path for output of cfg.drawGraph() (windows/linux issue)
     */

    public String getMetric() {
        return metric;
    }

    public String getTarget() {
        return target;
    }

    public GraphType getGraphType() {
        return graphType;
    }
}
