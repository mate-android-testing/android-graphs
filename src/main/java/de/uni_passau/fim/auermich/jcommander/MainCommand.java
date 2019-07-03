package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.beust.jcommander.converters.PathConverter;
import de.uni_passau.fim.auermich.graphs.GraphType;

import java.io.File;
import java.nio.file.Path;

public class MainCommand {

    /*
     * TODO: Handle multi-dex files
     * The current implementation can only handle a single dex file, although APKs may consist of multiple dex files.
     * Thus we should use in some point in time a path instead of a file, which refers to the parent directory of the classes.dex file.
     * We can make use of the built-in PathConverter.class to check if the input represents a path.
     *
     * THERE SHOULD BE ALREADY SOME FUNCTIONALITY INTEGRATED INTO DEXLIB2 TO PROCESS DIRECTLY APK FILES. (ASK FELIX)
     */

    @Parameter(names = { "-f", "-file"}, description = "File path to the classes.dex file we want to analyze.",
            required = true, converter = FileConverter.class)
    private File dexFile;

    @Parameter(names = { "-e", "-exceptional" }, description = "Whether the graph should contain edges from try-catch blocks.")
    private boolean exceptionalFlow = false;

    @Parameter(names = { "-d", "-debug" }, description = "Debug mode.")
    private boolean debug = false;

    @Parameter(names = { "-h", "--help" }, help = true)
    private boolean help;

    public File getDexFile() {
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
