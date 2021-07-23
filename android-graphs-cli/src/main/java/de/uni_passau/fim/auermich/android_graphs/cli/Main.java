package de.uni_passau.fim.auermich.android_graphs.cli;

import com.beust.jcommander.JCommander;
import de.uni_passau.fim.auermich.android_graphs.cli.jcommander.InterCFGCommand;
import de.uni_passau.fim.auermich.android_graphs.cli.jcommander.IntraCFGCommand;
import de.uni_passau.fim.auermich.android_graphs.cli.jcommander.MainCommand;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraphBuilder;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG;
import de.uni_passau.fim.auermich.android_graphs.core.utility.MethodUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Defines the command line interface. A typical invocation could be:
 *      java -jar android-graphs-all.jar -f <path-to-apk>
 *              -d intra -b -t <FQN of method>
 * This would generate an intra CFG with basic blocks enabled for the
 * method specified by the -t switch.
 */
public final class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    // the dalvik bytecode level (Android API version)
    public static final Opcodes API_OPCODE = Opcodes.forApi(28);

    // the set of possible commands
    private static final MainCommand mainCmd = new MainCommand();
    private static final InterCFGCommand interCFGCmd = new InterCFGCommand();
    private static final IntraCFGCommand intraCFGCmd = new IntraCFGCommand();

    // utility class implies private constructor
    private Main() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Processes the command line arguments and constructs the specified graph.
     *
     * @param args The command line arguments specify which graph should be generated.
     *             The switch -f specifies the path to the APK file (must come first).
     *             The switch -d specifies whether debugging mode should be enabled (no argument required).
     *             The switch -draw specifies whether the graph should be drawn.
     *             The switch -l (-lookup) requests a vertex lookup corresponding to the given trace.
     *
     *             After those global options, a sub commando must follow. This can be either
     *             'intra' or 'inter', which specifies which graph should be constructed.
     *             Each sub commando expects further arguments.
     *
     *             The 'intra' sub commando can handle the following arguments:
     *             The switch -t specifies the FQN of the method for which the intraCFG should be constructed.
     *             The switch -b specifies whether basic blocks should be used. (optional)
     *
     *             The 'inter' sub commando can handle the following arguments:
     *             The switch -b specifies whether basic blocks should be used. (optional)
     *             The switch -art specifies whether ART classes should be resolved. (optional)
     *             The switch -oaut specifies whether only AUT classes should be resolved. (optional)
     *             The switch -pim specifies whether isolated methods should be printed. (optional)
     *
     * @throws IOException Should never happen.
     */
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

        // determine which logging level should be used
        if (mainCmd.isDebug()) {
            Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.DEBUG);
        } else {
            Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.INFO);
        }

        // check whether help command is executed
        if (mainCmd.isHelp()) {
            commander.usage();
        } else {
            // process apk and construct desired graph
            run(commander);
        }
    }

    /**
     * Verifies that the given arguments are valid.
     *
     * @param cmd The command line arguments.
     */
    private static boolean checkArguments(IntraCFGCommand cmd) {
        assert cmd.getGraphType() == GraphType.INTRACFG;
        return true;
    }

    /**
     * Verifies that the given arguments are valid.
     *
     * @param cmd The command line arguments.
     */
    private static boolean checkArguments(InterCFGCommand cmd) {
        assert cmd.getGraphType() == GraphType.INTERCFG;
        return true;
    }

    private static void run(JCommander commander) throws IOException {

        LOGGER.debug("APK: " + mainCmd.getAPKFile().getAbsolutePath());

        if (!mainCmd.getAPKFile().exists()) {
            LOGGER.warn("No valid APK path!");
            return;
        }

        // intra, inter, sgd coincides with defined Graph type enum
        String selectedCommand = commander.getParsedCommand();
        Optional<GraphType> graphType = GraphType.fromString(selectedCommand);

        if (graphType.isEmpty()) {
            LOGGER.warn("Enter a valid command please!");
            commander.usage();
        } else {

            // process directly apk file (support for multi-dex)
            MultiDexContainer<? extends DexBackedDexFile> apk
                    = DexFileFactory.loadDexContainer(mainCmd.getAPKFile(), API_OPCODE);

            List<DexFile> dexFiles = new ArrayList<>();

            apk.getDexEntryNames().forEach(dexFile -> {
                try {
                    dexFiles.add(apk.getEntry(dexFile).getDexFile());
                } catch (IOException e) {
                    LOGGER.warn("Failure loading dexFile");
                    throw new UncheckedIOException(e);
                }
            });

            // determine which sub-commando was executed
            switch (graphType.get()) {
                case INTRACFG:
                    // check that specified target method is part of some class
                    Optional<DexFile> dexFile = MethodUtils.containsTargetMethod(dexFiles, intraCFGCmd.getTarget());

                    if (checkArguments(intraCFGCmd) && dexFile.isPresent()) {

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                                .withName(intraCFGCmd.getTarget());

                        if (intraCFGCmd.isUseBasicBlocks()) {
                            builder = builder.withBasicBlocks();
                        }

                        BaseGraph baseGraph = builder.build();

                        if (mainCmd.isDraw()) {
                            LOGGER.debug("Drawing graph!");
                            baseGraph.drawGraph();
                        }

                        if (mainCmd.lookup()) {
                            LOGGER.debug("Lookup vertex: " + baseGraph.lookUpVertex(mainCmd.getTrace()));
                        }

                    } else {
                        LOGGER.error("Target method not contained in dex file!");
                    }
                    break;
                case INTERCFG:
                    if (checkArguments(interCFGCmd)) {

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTERCFG, dexFiles)
                                .withName("global")
                                .withAPKFile(mainCmd.getAPKFile());

                        if (interCFGCmd.isUseBasicBlocks()) {
                            builder = builder.withBasicBlocks();
                        }

                        if (!interCFGCmd.resolveARTClasses()) {
                            builder = builder.withExcludeARTClasses();
                        }

                        if (interCFGCmd.resolveOnlyAUTClasses()) {
                            builder = builder.withResolveOnlyAUTClasses();
                        }

                        BaseGraph baseGraph = builder.build();

                        if (interCFGCmd.printIsolatedMethods()) {
                            ((InterCFG) baseGraph).printIsolatedSubGraphs();
                        }

                        LOGGER.debug("Size of graph: " + baseGraph.size());

                        if (mainCmd.isDraw()) {
                            LOGGER.debug("Drawing graph!");
                            baseGraph.drawGraph();
                        }

                        if (mainCmd.lookup()) {
                            LOGGER.debug("Lookup vertex: " + baseGraph.lookUpVertex(mainCmd.getTrace()));
                        }
                    }
                    break;
            }
        }
    }
}
