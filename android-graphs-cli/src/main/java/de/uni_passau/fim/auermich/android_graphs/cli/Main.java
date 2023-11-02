package de.uni_passau.fim.auermich.android_graphs.cli;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.beust.jcommander.JCommander;
import de.uni_passau.fim.auermich.android_graphs.cli.jcommander.*;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraphBuilder;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG;
import de.uni_passau.fim.auermich.android_graphs.core.utility.MethodUtils;
import de.uni_passau.fim.auermich.android_graphs.core.utility.Tuple;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

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
    private static final CallTreeCommand callTreeCmd = new CallTreeCommand();
    private static final InterCDGCommand interCDGCmd = new InterCDGCommand();
    private static final IntraCDGCommand intraCDGCmd = new IntraCDGCommand();
    private static final ModularCDGCommand modularCDGCmd = new ModularCDGCommand();

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
     *             The 'intercdg' sub commando can handle the following arguments:
     *             The switch -b specifies whether basic blocks should be used. (optional)
     *             The switch -art specifies whether ART classes should be resolved. (optional)
     *             The switch -oaut specifies whether only AUT classes should be resolved. (optional)
     *             The switch -pim specifies whether isolated methods should be printed. (optional)
     *
     *             The 'intracdg' sub commando can handle the following arguments:
     *             The switch -t specifies the FQN of the method for which the intraCDG should be constructed.
     *             The switch -b specifies whether basic blocks should be used. (optional)
     *
     *             The 'modularcdg' sub commando can handle the following arguments:
     *             The switch -b specifies whether basic blocks should be used. (optional)
     *             The switch -art specifies whether ART classes should be resolved. (optional)
     *             The switch -oaut specifies whether only AUT classes should be resolved. (optional)
     *             The switch -pim specifies whether isolated methods should be printed. (optional)
     *
     *             The 'calltree' sub commando can handle the following arguments:
     *             The switch -art specifies whether ART classes should be resolved. (optional)
     *             The switch -oaut specifies whether only AUT classes should be resolved. (optional)
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
                .addCommand("calltree", callTreeCmd)
                .addCommand("intercdg", interCDGCmd)
                .addCommand("intracdg", intraCDGCmd)
                .addCommand("modularcdg", modularCDGCmd)
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
    private static boolean checkArguments(ModularCDGCommand cmd) {
        return cmd.getGraphType() == GraphType.MODULARCDG;
    }

    /**
     * Verifies that the given arguments are valid.
     *
     * @param cmd The command line arguments.
     */
    private static boolean checkArguments(IntraCFGCommand cmd) {
        return cmd.getGraphType() == GraphType.INTRACFG;
    }

    /**
     * Verifies that the given arguments are valid.
     *
     * @param cmd The command line arguments.
     */
    private static boolean checkArguments(InterCFGCommand cmd) {
        return cmd.getGraphType() == GraphType.INTERCFG;
    }

    /**
     * Verifies that the given arguments are valid.
     *
     * @param cmd The command line arguments.
     */
    private static boolean checkArguments(IntraCDGCommand cmd) {
        return cmd.getGraphType() == GraphType.INTRACDG;
    }

    /**
     * Verifies that the given arguments are valid.
     *
     * @param cmd The command line arguments.
     */
    private static boolean checkArguments(InterCDGCommand cmd) {
        return cmd.getGraphType() == GraphType.INTERCDG;
    }

    /**
     * Verifies that the given arguments are valid.
     *
     * @param cmd The command line arguments.
     */
    private static boolean checkArguments(CallTreeCommand cmd) {
        return cmd.getGraphType() == GraphType.CALLTREE;
    }

    private static void run(JCommander commander) throws IOException {

        LOGGER.info("APK: " + mainCmd.getAPKFile().getAbsolutePath());

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
                case INTRACFG: {
                    // check that specified target method is part of some class
                    final Optional<Tuple<DexFile, Method>> optionalTuple
                            = MethodUtils.containsTargetMethod(dexFiles, intraCFGCmd.getTarget());

                    if (checkArguments(intraCFGCmd) && optionalTuple.isPresent()) {

                        final Tuple<DexFile, Method> tuple = optionalTuple.get();

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTRACFG, tuple.getX(), tuple.getY());

                        if (intraCFGCmd.isUseBasicBlocks()) {
                            builder = builder.withBasicBlocks();
                        }

                        BaseGraph baseGraph = builder.build();

                        if (mainCmd.isDraw()) {
                            LOGGER.info("Drawing graph!");
                            baseGraph.drawGraph();
                        }

                        if (mainCmd.lookup()) {
                            LOGGER.info("Lookup vertex: " + baseGraph.lookUpVertex(mainCmd.getTrace()));
                        }

                    } else {
                        LOGGER.error("Target method not contained in dex file!");
                    }
                    break;
                }
                case INTERCFG: {
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

                        LOGGER.info("Size of graph: " + baseGraph.size());

                        if (mainCmd.isDraw()) {
                            LOGGER.info("Drawing graph!");
                            baseGraph.drawGraph();
                        }

                        if (mainCmd.lookup()) {
                            LOGGER.info("Lookup vertex: " + baseGraph.lookUpVertex(mainCmd.getTrace()));
                        }
                    }
                    break;
                }
                case INTRACDG: {
                    // check that specified target method is part of some class
                    final Optional<Tuple<DexFile, Method>> optionalTuple
                            = MethodUtils.containsTargetMethod(dexFiles, intraCDGCmd.getTarget());

                    if (checkArguments(intraCDGCmd) && optionalTuple.isPresent()) {

                        final Tuple<DexFile, Method> tuple = optionalTuple.get();

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTRACDG, tuple.getX(), tuple.getY());

                        if (intraCDGCmd.isUseBasicBlocks()) {
                            builder = builder.withBasicBlocks();
                        }

                        BaseGraph baseGraph = builder.build();

                        if (mainCmd.isDraw()) {
                            LOGGER.info("Drawing graph!");
                            baseGraph.drawGraph();
                        }

                        if (mainCmd.lookup()) {
                            LOGGER.info("Lookup vertex: " + baseGraph.lookUpVertex(mainCmd.getTrace()));
                        }

                    } else {
                        LOGGER.error("Target method not contained in dex file!");
                    }
                    break;
                }
                case INTERCDG: {
                    if (checkArguments(interCDGCmd)) {

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTERCDG, dexFiles)
                                .withName("global")
                                .withAPKFile(mainCmd.getAPKFile());

                        if (interCDGCmd.isUseBasicBlocks()) {
                            builder = builder.withBasicBlocks();
                        }

                        if (!interCDGCmd.resolveARTClasses()) {
                            builder = builder.withExcludeARTClasses();
                        }

                        if (interCDGCmd.resolveOnlyAUTClasses()) {
                            builder = builder.withResolveOnlyAUTClasses();
                        }

                        BaseGraph baseGraph = builder.build();

                        if (interCDGCmd.printIsolatedMethods()) {
                            ((InterCFG) baseGraph).printIsolatedSubGraphs();
                        }

                        LOGGER.info("Size of graph: " + baseGraph.size());

                        if (mainCmd.isDraw()) {
                            LOGGER.info("Drawing graph!");
                            baseGraph.drawGraph();
                        }

                        if (mainCmd.lookup()) {
                            LOGGER.info("Lookup vertex: " + baseGraph.lookUpVertex(mainCmd.getTrace()));
                        }
                    }
                    break;
                }
                case MODULARCDG: {
                    if (checkArguments(modularCDGCmd)) {

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.MODULARCDG, dexFiles)
                                .withName("global")
                                .withAPKFile(mainCmd.getAPKFile());

                        if (modularCDGCmd.isUseBasicBlocks()) {
                            builder = builder.withBasicBlocks();
                        }

                        if (!modularCDGCmd.resolveARTClasses()) {
                            builder = builder.withExcludeARTClasses();
                        }

                        if (modularCDGCmd.resolveOnlyAUTClasses()) {
                            builder = builder.withResolveOnlyAUTClasses();
                        }

                        BaseGraph baseGraph = builder.build();

                        if (modularCDGCmd.printIsolatedMethods()) {
                            // TODO: Implement.
                        }

                        LOGGER.info("Size of graph: " + baseGraph.size());

                        if (mainCmd.isDraw()) {
                            LOGGER.info("Drawing graph!");
                            baseGraph.drawGraph();
                        }

                        if (mainCmd.lookup()) {
                            LOGGER.info("Lookup vertex: " + baseGraph.lookUpVertex(mainCmd.getTrace()));
                        }
                    }
                    break;
                }
                case CALLTREE: {

                    if (checkArguments(callTreeCmd)) {

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.CALLTREE, dexFiles)
                                .withName("global")
                                .withBasicBlocks()
                                .withAPKFile(mainCmd.getAPKFile());

                        if (!callTreeCmd.resolveARTClasses()) {
                            builder = builder.withExcludeARTClasses();
                        }

                        if (callTreeCmd.resolveOnlyAUTClasses()) {
                            builder = builder.withResolveOnlyAUTClasses();
                        }

                        BaseGraph baseGraph = builder.build();

                        LOGGER.info("Size of graph: " + baseGraph.size());

                        if (mainCmd.isDraw()) {
                            LOGGER.info("Drawing graph!");
                            baseGraph.drawGraph();
                        }

                        if (mainCmd.lookup()) {
                            LOGGER.info("Lookup vertex: " + baseGraph.lookUpVertex(mainCmd.getTrace()));
                        }
                    }
                    break;
                }
            }
        }
    }
}
