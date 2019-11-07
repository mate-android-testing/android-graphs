package de.uni_passau.fim.auermich;

import brut.androlib.Androlib;
import brut.androlib.AndrolibException;
import brut.androlib.ApkDecoder;
import brut.androlib.ApkOptions;
import brut.common.BrutException;
import brut.directory.ExtFile;
import com.beust.jcommander.JCommander;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.graphs.*;
import de.uni_passau.fim.auermich.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.graphs.cfg.InterProceduralCFG;
import de.uni_passau.fim.auermich.graphs.cfg.IntraProceduralCFG;
import de.uni_passau.fim.auermich.jcommander.InterCFGCommand;
import de.uni_passau.fim.auermich.jcommander.IntraCFGCommand;
import de.uni_passau.fim.auermich.jcommander.MainCommand;
import de.uni_passau.fim.auermich.statement.BasicStatement;
import de.uni_passau.fim.auermich.statement.BlockStatement;
import de.uni_passau.fim.auermich.statement.ReturnStatement;
import de.uni_passau.fim.auermich.statement.Statement;
import de.uni_passau.fim.auermich.utility.Utility;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.DexClassProvider;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.builder.BuilderOffsetInstruction;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.BuilderInstruction21t;
import org.jf.dexlib2.builder.instruction.BuilderInstruction22t;
import org.jf.dexlib2.builder.instruction.BuilderInstruction35c;
import org.jf.dexlib2.builder.instruction.BuilderInstruction3rc;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.instruction.formats.*;
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths;
import org.jgrapht.graph.DefaultEdge;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;

// TODO: rename to AndroidGraphs or whatever
public final class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    // the dalvik bytecode level (Android API version)
    public static final Opcodes API_OPCODE = Opcodes.forApi(28);

    // debug mode
    private static boolean DEBUG_MODE = false;

    // the set of possible commands
    private static final MainCommand mainCmd = new MainCommand();
    private static final InterCFGCommand interCFGCmd = new InterCFGCommand();
    private static final IntraCFGCommand intraCFGCmd = new IntraCFGCommand();

    // the path to the decoded APK
    private static String decodingOutputPath;

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
        if (mainCmd.isDebug()) {
            LOGGER.debug("Debug mode is enabled!");
            DEBUG_MODE = true;
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
     *
     * @param cmd
     */
    private static boolean checkArguments(IntraCFGCommand cmd) {
        assert cmd.getGraphType() == GraphType.INTRACFG;
        // Objects.requireNonNull(cmd.getMetric());
        // Objects.requireNonNull(cmd.getTarget());
        return true;
    }

    /**
     * @param cmd
     */
    private static boolean checkArguments(InterCFGCommand cmd) {
        assert cmd.getGraphType() == GraphType.INTERCFG;
        // Objects.requireNonNull(cmd.getMetric());
        return true;
    }

    public static BaseCFG computerInterCFGWithBB(String apkPath, boolean excludeARTClasses) throws IOException {

        File apkFile = new File(apkPath);

        MultiDexContainer<? extends DexBackedDexFile> apk
                = DexFileFactory.loadDexContainer(apkFile, API_OPCODE);

        List<DexFile> dexFiles = new ArrayList<>();

        apk.getDexEntryNames().forEach(dexFile -> {
            try {
                dexFiles.add(apk.getEntry(dexFile).getDexFile());
            } catch (IOException e) {
                e.printStackTrace();
                throw new IllegalStateException("Couldn't load dex file!");
            }
        });

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTERCFG, dexFiles)
                .withName("global")
                .withBasicBlocks()
                .withAPKFile(apkFile);

        if (excludeARTClasses) {
            builder = builder.withExcludeARTClasses();
        }

        BaseGraph baseGraph = builder.build();

        return (BaseCFG) baseGraph;
    }

    private static void run(JCommander commander, boolean exceptionalFlow) throws IOException {

        /*
         * TODO: define some result data type
         * We basically want to return something, e.g. a distance between two nodes. This
         * should be stored in some result data type. Since mandatory options are missing
         * potentially, we may want to return an empty result -> Optional.
         */

        LOGGER.debug("Determining which action to take dependent on given command");

        LOGGER.info(mainCmd.getAPKFile().getAbsolutePath());

        if (!mainCmd.getAPKFile().exists()) {
            LOGGER.warn("No valid APK path!");
            return;
        }

        // intra, inter, sgd coincides with defined Graph type enum
        String selectedCommand = commander.getParsedCommand();
        Optional<GraphType> graphType = GraphType.fromString(selectedCommand);

        if (!graphType.isPresent()) {
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
                    LOGGER.warn(e.getMessage());
                    return;
                }
            });

            // determine which sub-commando was executed
            switch (graphType.get()) {
                case INTRACFG:
                    // check that specified target method is part of some class
                    Optional<DexFile> dexFile = Utility.containsTargetMethod(dexFiles, intraCFGCmd.getTarget());

                    if (checkArguments(intraCFGCmd) && dexFile.isPresent()) {

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                                .withName(intraCFGCmd.getTarget());

                        if (intraCFGCmd.isUseBasicBlocks()) {
                            builder = builder.withBasicBlocks();
                        }

                        BaseGraph baseGraph = builder.build();
                        baseGraph.drawGraph();
                        // TODO: perform some computation on the graph (check for != null)
                    }
                    break;
                case INTERCFG:
                    if (checkArguments(interCFGCmd)) {

                        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTERCFG, dexFiles)
                                .withName("global")
                                .withAPKFile(mainCmd.getAPKFile());

                        if (intraCFGCmd.isUseBasicBlocks()) {
                            builder = builder.withBasicBlocks();
                        }

                        BaseGraph baseGraph = builder.build();
                        // computeApproachLevel(interCFG);
                    }
                    break;
            }
        }
    }

    private static void computeApproachLevel(BaseCFG interCFG) {

        Path resourceDirectory = Paths.get("src","test","resources");
        File traces = new File(resourceDirectory.toFile(), "traces.txt");
        List<String> executionPath = new ArrayList<>();

        try (Stream<String> stream = Files.lines(traces.toPath(), StandardCharsets.UTF_8)) {
            executionPath = stream.collect(Collectors.toList());
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        LOGGER.debug(executionPath);

        // we need to mark vertices we visit
        List<Vertex> visitedVertices = new ArrayList<>();

        // look up each pathNode (vertex) in the CFG
        for (String pathNode : executionPath) {
            LOGGER.debug("Searching for vertex: " + pathNode);

            // get full-qualified method name + type (entry,exit,instructionID)
            int index = pathNode.lastIndexOf("->");
            String method = pathNode.substring(0, index);
            String type = pathNode.substring(index+2);

            if (type.equals("entry")) {
                Vertex entry = interCFG.getVertices().stream().filter(v -> v.isEntryVertex() && v.getMethod().equals(method)).findFirst().get();
                LOGGER.debug("Entry Vertex: " + entry);
                visitedVertices.add(entry);
            } else if (type.equals("exit")) {
                Vertex exit = interCFG.getVertices().stream().filter(v -> v.isExitVertex() && v.getMethod().equals(method)).findFirst().get();
                LOGGER.debug("Exit Vertex: " + exit);
                visitedVertices.add(exit);
            } else {
                // must be the instruction id of a branch
                int id = Integer.parseInt(type);
                Vertex branch = interCFG.getVertices().stream().filter(v -> v.containsInstruction(method,id)).findFirst().get();
                LOGGER.debug("Branch Vertex: " + branch);
                visitedVertices.add(branch);
            }
        }

        // we need to select a target vertex
        // Vertex target = interCFG.getExit();
        Vertex target = interCFG.getVertices().stream().filter(v -> v.isEntryVertex()
                        && v.getMethod().equals("Lcom/zola/bmi/BMIMain;->onStart()V")).findFirst().get();
        LOGGER.debug("Target Vertex: " + target);

        for (Vertex source : visitedVertices) {
            LOGGER.debug("Shortest Distance from Vertex: " + source);
            LOGGER.debug(interCFG.getShortestDistance(source, target));
        }

        // get all branches
        List<Vertex> branches = interCFG.getVertices().stream()
                .filter(v -> v.isBranchVertex()).collect(Collectors.toList());

        for (Vertex branch : branches) {
            Integer branchID = null;
            if (branch.getStatement() instanceof BasicStatement) {
                branchID = ((BasicStatement) branch.getStatement()).getInstructionIndex();
            } else if (branch.getStatement() instanceof BlockStatement) {
                branchID = ((BasicStatement) ((BlockStatement) branch.getStatement()).getFirstStatement()).getInstructionIndex();
            }

            if (branchID != null) {
                LOGGER.debug(branch.getMethod() + "->" + branchID);
            }
        }

    }
}
