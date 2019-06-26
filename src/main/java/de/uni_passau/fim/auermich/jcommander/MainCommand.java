package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.beust.jcommander.converters.PathConverter;
import de.uni_passau.fim.auermich.graphs.GraphType;

import java.io.File;
import java.nio.file.Path;

@Parameters(commandDescription = "Add file contents to the index")
public class MainCommand {

    @Parameter(names = {"-f", "-file"}, description = "Path to the classes.dex file we want to analyze.", required = true, converter = PathConverter.class)
    private Path dexFile;

    @Parameter(names = { "-e", "-exceptional" }, description = "Whether the graph should contain edges from try-catch blocks.")
    private boolean exceptionalFlow = false;

    @Parameter(names = { "-d", "-debug" }, description = "Debug mode.")
    private boolean debug = false;

    @Parameter(names = { "-h", "--help" }, help = true)
    private boolean help;

    public Path getDexFile() {
        return dexFile;
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
