package de.uni_passau.fim.auermich;

import com.beust.jcommander.JCommander;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.jcommander.CommandLineArguments;
import de.uni_passau.fim.auermich.jcommander.InterCFGCommand;
import de.uni_passau.fim.auermich.jcommander.IntraCFGCommand;
import de.uni_passau.fim.auermich.jcommander.MainCommand;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.DexFile;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class Main {

    private Main() {
        throw new UnsupportedOperationException("Utility class!");
    }

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static final Opcodes API_OPCODE = Opcodes.forApi(28);

    public static void main(String[] args) throws IOException {

        // the set of possible commnads
        MainCommand mainCmd = new MainCommand();
        InterCFGCommand interCFGCmd = new InterCFGCommand();
        IntraCFGCommand intraCFGCmd = new IntraCFGCommand();

        /*
        * TODO: allow custom order of arguments
        * The current implementation only allows to specify
        * the cmd-line args in a pre-defined order, i.e.
        * first comes the option of the main command
        * and afterwards the remaining arguments of
        * sub-commands like 'intra'. However, we would
        * like to specify the main arguments in any order
        * without having to define any prefix.
         */

        JCommander commander = JCommander.newBuilder()
                .addObject(mainCmd)
                .addCommand("intra", intraCFGCmd)
                .addCommand("inter", interCFGCmd)
                .build();

        // the program name displayed in the help/usage cmd.
        commander.setProgramName("Android-Graphs");

        // parse command line arguments
        commander.parse(args);

        LOGGER.debug("Command input: " + commander.getParsedCommand());

        // determine which logging level should be used
        if(mainCmd.isDebug()) {
            LOGGER.debug("Debug mode is enabled!");
            Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.DEBUG);
        } else {
            Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.INFO);
        }

        // check whether help command is executed
        if (mainCmd.isHelp()) {
            commander.usage();
        } else {

            boolean exceptionalFlow = false;

            // check whether we want to model edges from try-catch blocks
            if (mainCmd.isExceptionalFlow()) {
                exceptionalFlow = true;
            }

            // process apk and construct desired graph
            run(commander, exceptionalFlow);
        }
    }

    /**
     * Checks whether the given {@param args} are valid. That means they represent
     * some valid data and the combination among them is allowed.
     *
     * @param args The command line arguments.
     */
    private static void checkArguments(CommandLineArguments args) {
        // Objects.requireNonNull(args.getDexFile(), "Path to dex file is missing!");
        // Objects.requireNonNull(args.getGraph(), "Graph type is missing!");
    }


    private static void run(JCommander commander, boolean exceptionalFlow) throws IOException {

        LOGGER.debug("Determining which action to take dependent on given command");

        System.out.println(commander.getParameters());

    }

}
