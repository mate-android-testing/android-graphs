package de.uni_passau.fim.auermich;

import com.beust.jcommander.JCommander;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.jcommander.CommandLineArguments;
import de.uni_passau.fim.auermich.jcommander.InterCFGCommand;
import de.uni_passau.fim.auermich.jcommander.IntraCFGCommand;
import de.uni_passau.fim.auermich.jcommander.MainCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static boolean debug = false;

    public static final Opcodes API_OPCODE = Opcodes.forApi(28);

    public static void main(String[] args) throws IOException {

        // parse command line arguments
        CommandLineArguments cmdArgs = new CommandLineArguments();
        MainCommand mainCmd = new MainCommand();
        InterCFGCommand interCFGCmd = new InterCFGCommand();
        IntraCFGCommand intraCFGCmd = new IntraCFGCommand();
        JCommander commander = JCommander.newBuilder()
                .addObject(cmdArgs)
                //.addCommand("main", mainCmd)
                .addCommand("intra", intraCFGCmd)
                // .addCommand("inter", interCFGCmd)
                .build();
        commander.setProgramName("Android-Graphs");
        String[] argv = { "intra", "-m", "one", "C:\\Users\\Michael\\matecommander.py" };
        commander.parse(argv);

        System.out.println(commander.getParsedCommand());

        /*

        // check whether help command is executed
        if (cmdArgs.isHelp()) {
            commander.usage();
        } else {
            // check whether input args are valid
            checkArguments(cmdArgs);

            // check whether we run in debug mode
            if (cmdArgs.isDebug()) {
                debug = true;
            }

            boolean exceptionalFlow = false;

            // check whether we want to model edges from try-catch blocks
            if (cmdArgs.isExceptionalFlow()) {
                exceptionalFlow = true;
            }

            // process apk and construct desired graph
            run(cmdArgs.getDexFile(), cmdArgs.getGraph(), exceptionalFlow);
        }
        */
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


    private static void run(File dexFilePath, GraphType graphType, boolean exceptionalFlow) throws IOException {

        LOGGER.debug("Initial log");

        DexFile dexFile = DexFileFactory.loadDexFile(dexFilePath, API_OPCODE);

        switch (graphType) {
            case INTRACFG:
            case INTERCFG:
            case SGD:
            default:

        }

    }

}
