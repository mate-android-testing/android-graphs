package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.beust.jcommander.converters.PathConverter;
import de.uni_passau.fim.auermich.graphs.GraphType;

import java.io.File;
import java.nio.file.Path;

@Parameters(commandDescription = "Produces an inter-procedural CFG.")
public class InterCFGCommand {

    /*
    * TODO: use a custom data type for metrics (APPROACH_LEVEL,BRANCH_DISTANCE) + enforce field (required)
    *
     */

    @Parameter(names = { "-m", "-metric" }, description = "Metric.")
    private String metric;

    private GraphType graphType = GraphType.INTERCFG;

    public String getMetric() {
        return metric;
    }

    public GraphType getGraphType() {
        return graphType;
    }
}
