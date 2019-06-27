package de.uni_passau.fim.auermich;

import com.beust.jcommander.JCommander;
import de.uni_passau.fim.auermich.graphs.GraphType;
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
import java.util.Optional;

public final class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    // the dalvik bytecode level (Android API version)
    public static final Opcodes API_OPCODE = Opcodes.forApi(28);

    // the set of possible commands
    private static final MainCommand mainCmd = new MainCommand();
    private static final InterCFGCommand interCFGCmd = new InterCFGCommand();
    private static final IntraCFGCommand intraCFGCmd = new IntraCFGCommand();

    private Main() {
        throw new UnsupportedOperationException("Utility class!");
    }

    public static void main(String[] args) throws IOException {

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
     * Verifies that the given arguments are valid.
     * @param cmd
     */
    private static boolean checkArguments(IntraCFGCommand cmd) {
        assert cmd.getGraphType() == GraphType.INTRACFG;
        Objects.requireNonNull(cmd.getMetric());
        Objects.requireNonNull(cmd.getTarget());
        return true;
    }

    /**
     *
     * @param cmd
     */
    private static boolean checkArguments(InterCFGCommand cmd) {
        assert cmd.getGraphType() == GraphType.INTERCFG;
        Objects.requireNonNull(cmd.getMetric());
        return true;
    }


    private static void run(JCommander commander, boolean exceptionalFlow) throws IOException {

        /*
        * TODO: define some result data type
        * We basically want to return something, e.g. a distance between two nodes. This
        * should be stored in some result data type. Since mandatory options are missing
        * potentially, we may want to return an empty result -> Optional.
         */

        LOGGER.debug("Determining which action to take dependent on given command");

        if (!mainCmd.getDexFile().exists()) {
            LOGGER.warn("Path to classes.dex file not valid!");
            return;
        }

        String selectedCommand = commander.getParsedCommand();
        Optional<GraphType> graphType = GraphType.fromString(selectedCommand);

        if (!graphType.isPresent()) {
            LOGGER.warn("Enter a valid command please!");
            commander.usage();
        } else {

            DexFile dexFile = DexFileFactory.loadDexFile(mainCmd.getDexFile(), API_OPCODE);

            // determine which sub-commando was executed
            switch (graphType.get()) {
                case INTRACFG:
                    if(checkArguments(intraCFGCmd)) {
                        computeIntraProceduralCFG(dexFile);
                    }
                    break;
                case INTERCFG:
                    if(checkArguments(interCFGCmd)) {
                        computeInterProceduralCFG(dexFile);
                    }
                    break;
            }
        }
    }

    /*
    * TODO: add params for metric and return some result datatype
    *
     */

    private static void computeInterProceduralCFG(DexFile dexFile) {

    }

    private static void computeIntraProceduralCFG(DexFile dexFile) {

    }
}
