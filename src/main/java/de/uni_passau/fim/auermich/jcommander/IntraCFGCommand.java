package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.beust.jcommander.converters.PathConverter;
import de.uni_passau.fim.auermich.graphs.GraphType;

import java.io.File;
import java.nio.file.Path;

@Parameters(commandDescription = "Produces an intra-procedural CFG for a given method.")
public class IntraCFGCommand {

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

    private GraphType graphType = GraphType.INTRACFG;

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
