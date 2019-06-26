package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import de.uni_passau.fim.auermich.graphs.GraphType;

import java.io.File;

public class HelpCommand {

    @Parameter(names = { "-h", "--help" }, help = true)
    private boolean help;
}
