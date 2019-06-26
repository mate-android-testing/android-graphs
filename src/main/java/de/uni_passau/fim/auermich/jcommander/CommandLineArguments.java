package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import de.uni_passau.fim.auermich.graphs.GraphType;

import java.io.File;

/**
 * Describes the parameter the main proceedur
 */
public class CommandLineArguments {

    // TODO: can't handle multi-dex files right now, may use path to (de-compressed) apk file and search for classes.dex files

    @Parameter(description = "Path to the classes.dex file we want to analyze.", required = true, converter = FileConverter.class)
    private File dexFile;

    @Parameter(names = { "-g", "-graph" }, description = "The graph we want to generate.", required = true, converter = GraphTypeConverter.class)
    private GraphType graph;

    @Parameter(names = { "-e", "-exceptional" }, description = "Whether the graph should contain edges from try-catch blocks.")
    private boolean exceptionalFlow = false;

    @Parameter(names = { "-d", "-debug" }, description = "Debug mode.")
    private boolean debug = false;

    @Parameter(names = { "-h", "--help" }, help = true)
    private boolean help;

    public File getDexFile() {
        return dexFile;
    }

    public GraphType getGraph() {
        return graph;
    }

    public boolean isExceptionalFlow() {
        return exceptionalFlow;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isHelp() {
        return help;
    }
}
