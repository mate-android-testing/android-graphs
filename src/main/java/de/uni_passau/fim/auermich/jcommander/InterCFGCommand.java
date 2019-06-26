package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.beust.jcommander.converters.PathConverter;
import de.uni_passau.fim.auermich.graphs.GraphType;

import java.io.File;
import java.nio.file.Path;

@Parameters(commandDescription = "Add file contents to the index")
public class InterCFGCommand {

    // @Parameter(description = "Path to the classes.dex file we want to analyze.", required = true)
    private String dexFile;

    @Parameter(names = { "-m", "-metric" }, description = "Metric.")
    private String metric;

}
