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

    public static BaseCFG computerInterCFGWithBB(String apkPath) throws IOException {

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

        BaseGraph baseGraph = new BaseGraphBuilder(GraphType.INTERCFG, dexFiles)
                .withName("global")
                .withBasicBlocks()
                .withAPKFile(apkFile)
                .build();

        return (BaseCFG) baseGraph;
    }

    public static BaseCFG computeInterCFGWithBasicBlocks(String apkfile) throws IOException {

        // process directly apk file (support for multi-dex)
        MultiDexContainer<? extends DexBackedDexFile> apk
                = DexFileFactory.loadDexContainer(new File(apkfile), API_OPCODE);

        // used inside decodeAPK()
        mainCmd.setApkFile(apkfile);
        mainCmd.setDebug(true);

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

        // TODO: change methods to support a list of dexFiles, now just pick first one
        DexFile dexFile = dexFiles.get(0);

        // TODO: remove decoded APK folder (see BranchDistance implementation)

        return computeInterProceduralCFG(dexFile, true);
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

            // TODO: change methods to support a list of dexFiles, now just pick first one
            DexFile dexFile = dexFiles.get(0);

            // determine which sub-commando was executed
            switch (graphType.get()) {
                case INTRACFG:
                    // check that specified target method is part of some class
                    Optional<Method> targetMethod = Utility.searchForTargetMethod(dexFile, intraCFGCmd.getTarget());

                    if (checkArguments(intraCFGCmd) && targetMethod.isPresent()) {
                        // BaseCFG cfg = computeIntraCFGWithBasicBlocks(dexFile, targetMethod.get());
                        BaseCFG intraCFG = computeIntraProceduralCFG(dexFile, targetMethod.get(),
                                intraCFGCmd.isUseBasicBlocks());
                        // TODO: perform some computation on the graph (check for != null)
                    }
                    break;
                case INTERCFG:
                    if (checkArguments(interCFGCmd)) {
                        BaseCFG interCFG = computeInterProceduralCFG(dexFile, interCFGCmd.isUseBasicBlocks());
                        // TODO: perform some computation on the graph
                        computeApproachLevel(interCFG);
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


    /**
     * Decodes a given APK using apktool.
     */
    private static void decodeAPK() {

        try {
            // ApkDecoder decoder = new ApkDecoder(new Androlib());
            ApkDecoder decoder = new ApkDecoder((mainCmd.getAPKFile()));

            // path where we want to decode the APK
            String parentDir = mainCmd.getAPKFile().getParent();
            String outputDir = parentDir + File.separator + "out";

            LOGGER.debug("Decoding Output Dir: " + outputDir);
            decoder.setOutDir(new File(outputDir));
            decodingOutputPath = outputDir;

            // whether to decode classes.dex into smali files: -s
            decoder.setDecodeSources(ApkDecoder.DECODE_SOURCES_NONE);

            // overwrites existing dir: -f
            decoder.setForceDelete(true);

            decoder.decode();
        } catch (BrutException | IOException e) {
            LOGGER.warn("Failed to decode APK file!");
            LOGGER.warn(e.getMessage());
        }

    }

    /**
     * Builds a given APK using apktool.
     */
    private static void buildAPK() {

        ApkOptions apkOptions = new ApkOptions();
        // apkOptions.useAapt2 = true;
        apkOptions.verbose = true;

        try {
            // when building the APK, it can be found typically in the 'dist' folder
            File apk = new File(decodingOutputPath + File.separator + "dist", "final.apk");
            // outFile specifies the path and name of the resulting APK, if null -> default location (dist dir) is used
            new Androlib(apkOptions).build(new ExtFile(new File(decodingOutputPath)), null);
        } catch (BrutException e) {
            LOGGER.warn("Failed to build APK file!");
            LOGGER.warn(e.getMessage());
        }
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private static BaseCFG dummyIntraProceduralCFG(Method targetMethod) {

        LOGGER.info("Method Signature: " + Utility.deriveMethodSignature(targetMethod));

        BaseCFG cfg = new IntraProceduralCFG(Utility.deriveMethodSignature(targetMethod));
        cfg.addEdge(cfg.getEntry(), cfg.getExit());
        return cfg;
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private static BaseCFG dummyIntraProceduralCFG(String targetMethod) {

        BaseCFG cfg = new IntraProceduralCFG(targetMethod);
        cfg.addEdge(cfg.getEntry(), cfg.getExit());
        return cfg;
    }

    /**
     * Constructs the intra procedural CFG for every method inlcuded in the {@param dexFile}. Since
     * a regular {@param dexFile} includes > 30 000 methods, the graph would explode. To reduce the
     * size of the graph, we only model for all the ART classes, more precisely for all classes matched
     * by a certain pattern, a simplistic CFG consisting only of the virtual start and end vertex plus
     * an edge in between those vertices.
     *
     * @param dexFile   The classes.dex file containing all the classes.
     * @param intraCFGs A map containing for each method (key: method signature) its CFG.
     * @throws IOException Should never happen.
     */
    private static void constructIntraCFGs(DexFile dexFile, Map<String, BaseCFG> intraCFGs,
                                           boolean useBasicBlocks) throws IOException {

        // only use dummy CFGs for ART classes
        Pattern exclusionPattern = Utility.readExcludePatterns();

        // track for how many methods we computed the complete CFG (no dummy CFGs)
        AtomicInteger realMethods = new AtomicInteger(0);

        // construct for ART classes only a dummy CFG consisting of virtual start and end vertex
        dexFile.getClasses().forEach(classDef ->
                classDef.getMethods().forEach(method -> {

                    String methodSignature = Utility.deriveMethodSignature(method);
                    String className = Utility.dottedClassName(classDef.toString());

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()) {
                        // dummy CFG consisting only of entry, exit vertex and edge between
                        intraCFGs.put(methodSignature, dummyIntraProceduralCFG(method));
                    } else {
                        intraCFGs.put(methodSignature, computeIntraProceduralCFG(dexFile, method, useBasicBlocks));
                        realMethods.incrementAndGet();
                    }
                }));

        LOGGER.debug("Number of completely constructed CFGs: " + realMethods.get());
    }

    /**
     * Computes the inter-procedural CFG without using basic blocks.
     *
     * @param dexFile The classes.dex file ocntaining all the classes and its methods.
     * @return Returns the inter-procedural CFG.
     * @throws IOException Should never happen.
     */
    private static BaseCFG computeInterCFG(DexFile dexFile) throws IOException {

        // the final inter-procedural CFG
        BaseCFG interCFG = new InterProceduralCFG("globalEntryPoint");

        // construct for each method firs the intra-procedural CFG (key: method signature)
        Map<String, BaseCFG> intraCFGs = new HashMap<>();
        constructIntraCFGs(dexFile, intraCFGs, false);

        // stores the relevant onCreate methods
        List<BaseCFG> onCreateMethods = new ArrayList<>();

        // avoid concurrent modification exception
        Map<String, BaseCFG> intraCFGsCopy = new HashMap<>(intraCFGs);

        // store graphs already inserted into inter-procedural CFG
        Set<BaseCFG> coveredGraphs = new HashSet<>();

        // exclude certain methods/classes
        Pattern exclusionPattern = Utility.readExcludePatterns();

        // compute inter-procedural CFG by connecting intra CFGs
        for (Map.Entry<String, BaseCFG> entry : intraCFGsCopy.entrySet()) {

            BaseCFG cfg = entry.getValue();
            IntraProceduralCFG intraCFG = (IntraProceduralCFG) cfg;
            LOGGER.debug(intraCFG.getMethodName());

            if (intraCFG.getMethodName().startsWith("Lcom/zola/bmi/BMIMain")) {
                if (!coveredGraphs.contains(cfg)) {
                    // add first source graph
                    interCFG.addSubGraph(intraCFG);
                    coveredGraphs.add(cfg);
                }
            }

            // the method signature (className->methodName->params->returnType)
            String method = intraCFG.getMethodName();
            String className = Utility.dottedClassName(Utility.getClassName(method));
            LOGGER.debug("ClassName: " + className);

            // we need to model the android lifecycle as well -> collect onCreate methods
            if (method.contains("onCreate(Landroid/os/Bundle;)V") && !exclusionPattern.matcher(className).matches()) {
                // copy necessary, otherwise the sub graph misses some vertices
                onCreateMethods.add(intraCFG.copy());
            }

            LOGGER.debug("Searching for invoke instructions!");

            // TODO: may track previously all call instructions when computing intraCFG
            for (Vertex vertex : cfg.getVertices()) {

                if (vertex.isEntryVertex() || vertex.isExitVertex()) {
                    // entry and exit vertices do not have instructions attached, skip
                    continue;
                }

                // all vertices apart entry, exit contain basic statements
                BasicStatement statement = (BasicStatement) vertex.getStatement();
                Instruction instruction = statement.getInstruction().getInstruction();

                // check for invoke/invoke-range instruction
                if (instruction instanceof ReferenceInstruction
                        && (instruction instanceof Instruction3rc
                        || instruction instanceof Instruction35c)) {

                    // search for target CFG by reference of invoke instruction (target method)
                    String methodSignature = ((ReferenceInstruction) instruction).getReference().toString();

                    LOGGER.debug("Invoke: " + methodSignature);

                    BaseCFG targetCFG;

                    if (intraCFGs.containsKey(methodSignature)) {
                        targetCFG = intraCFGs.get(methodSignature);
                        LOGGER.debug("Target CFG: " + ((IntraProceduralCFG) targetCFG).getMethodName());
                    } else {

                        /*
                         * There are some Android specific classes, e.g. android/view/View, which are
                         * not included in the classes.dex file for yet unknown reasons. Basically,
                         * these classes should be just treated like other classes from the ART.
                         */
                        LOGGER.warn("Target CFG for method: " + methodSignature + " not found!");
                        targetCFG = dummyIntraProceduralCFG(methodSignature);
                        intraCFGs.put(methodSignature, targetCFG);
                    }

                    if (intraCFG.getMethodName().startsWith("Lcom/zola/bmi/BMIMain")) {

                        /*
                         * Store the original outgoing edges first, since we add further
                         * edges later.
                         *
                         */
                        Set<Edge> outgoingEdges = intraCFG.getOutgoingEdges(vertex);
                        LOGGER.debug("Outgoing edges of vertex " + vertex + ": " + outgoingEdges);

                        if (!coveredGraphs.contains(targetCFG)) {
                            // add target graph to inter CFG
                            interCFG.addSubGraph(targetCFG);
                            coveredGraphs.add(targetCFG);
                        }

                        if (interCFG.containsVertex(vertex) && interCFG.containsVertex(targetCFG.getEntry())) {
                            // add edge from invoke instruction to entry vertex of target CFG
                            LOGGER.debug("Source: " + vertex);
                            LOGGER.debug("Target: " + targetCFG.getEntry());
                            interCFG.addEdge(vertex, targetCFG.getEntry());
                        }

                        // TODO: need unique return vertices (multiple call within method to same target method)
                        // insert dummy return vertex
                        Statement returnStmt = new ReturnStatement(vertex.getMethod(), targetCFG.getMethodName());
                        Vertex returnVertex = new Vertex(returnStmt);
                        interCFG.addVertex(returnVertex);

                        // remove edge from invoke to its successor instruction(s)
                        interCFG.removeEdges(outgoingEdges);

                        // add edge from exit of target CFG to dummy return vertex
                        interCFG.addEdge(targetCFG.getExit(), returnVertex);

                        // add edge from dummy return vertex to the original successor(s) of the invoke instruction
                        for (Edge edge : outgoingEdges) {
                            interCFG.addEdge(returnVertex, edge.getTarget());
                        }
                    }
                }
            }
        }

        // add the android lifecycle methods
        Map<String, BaseCFG> callbackEntryPoints = addAndroidLifecycleMethods(interCFG, intraCFGs, onCreateMethods);

        // add global entry point to each constructor and link constructor to onCreate method
        addGlobalEntryPoints(interCFG, intraCFGs, onCreateMethods);

        // add the callbacks specified either through XML or directly in code
        addCallbacks(interCFG, intraCFGs, callbackEntryPoints, dexFile);

        if (DEBUG_MODE) {
            // intraCFGs.get("Lcom/android/calendar/AllInOneActivity;->checkAppPermissions()V").drawGraph();
            // LOGGER.debug(interCFG);
            interCFG.drawGraph();
        }

        return interCFG;
    }

    private static Map<String, BaseCFG> addAndroidLifecycleMethods(BaseCFG interCFG, Map<String, BaseCFG> intraCFGs,
                                                                   List<BaseCFG> onCreateMethods) {

        // stores the entry points of the callbacks, which can happen between onResume and onPause
        Map<String, BaseCFG> callbacksCFGs = new HashMap<>();

        for (BaseCFG onCreateMethod : onCreateMethods) {

            String methodName = onCreateMethod.getMethodName();
            String className = Utility.getClassName(methodName);

            // onCreate directly invokes onStart()
            String onStart = className + "->onStart()V";
            BaseCFG onStartCFG = addLifeCycle(onStart, intraCFGs, interCFG, onCreateMethod);

            // onStart directly invokes onResume()
            String onResume = className + "->onResume()V";
            BaseCFG onResumeCFG = addLifeCycle(onResume, intraCFGs, interCFG, onStartCFG);

            /*
             * Each component may define several listeners for certain events, e.g. a button click,
             * which causes the invocation of a callback function. Those callbacks are active as
             * long as the corresponding component (activity) is in the onResume state. Thus, in our
             * graph we have an additional sub-graph 'callbacks' that is directly linked to the end
             * of 'onResume()' and can either call one of the specified listeners or directly invoke
             * the onPause() method (indirectly through the entry-exit edge). Each listener function
             * points back to the 'callbacks' entry node.
             */

            // add callbacks sub graph
            BaseCFG callbacksCFG = dummyIntraProceduralCFG("callbacks");
            interCFG.addSubGraph(callbacksCFG);
            callbacksCFGs.put(className, callbacksCFG);

            // callbacks can be invoked after onResume() has finished
            interCFG.addEdge(onResumeCFG.getExit(), callbacksCFG.getEntry());

            // there can be a sequence of callbacks (loop)
            interCFG.addEdge(callbacksCFG.getExit(), callbacksCFG.getEntry());

            // onPause() can be invoked after some callback
            String onPause = className + "->onPause()V";
            BaseCFG onPauseCFG = addLifeCycle(onPause, intraCFGs, interCFG, callbacksCFG);

            // onPause can either invoke onStop() or onResume()
            interCFG.addEdge(onPauseCFG.getExit(), onResumeCFG.getEntry());
            String onStop = className + "->onStop()V";
            BaseCFG onStopCFG = addLifeCycle(onStop, intraCFGs, interCFG, onPauseCFG);

            // onStop can either invoke onRestart() or onDestroy()
            String onRestart = className + "->onRestart()V";
            String onDestroy = className + "->onDestroy()V";
            BaseCFG onRestartCFG = addLifeCycle(onRestart, intraCFGs, interCFG, onStopCFG);
            BaseCFG onDestroyCFG = addLifeCycle(onDestroy, intraCFGs, interCFG, onStopCFG);

            // onRestart invokes onStart()
            interCFG.addEdge(onRestartCFG.getExit(), onStartCFG.getEntry());
        }
        return callbacksCFGs;
    }

    /**
     * Adds a new lifecycle CFG to the existing graph and connects it to the lifecycle's predecessor.
     * Uses the custom lifecycle CFG if available, otherwise creates a dummy lifecycle CFG.
     *
     * @param method      The FQN of the lifecycle, e.g. MAIN_ACTIVTY_FQN->onStop()V.
     * @param intraCFGs   The set of derived intra CFGs.
     * @param interCFG    The resulting graph.
     * @param predecessor The lifecycle's predecessor.
     * @return Returns the new lifecycle CFG.
     */
    private static BaseCFG addLifeCycle(String method, Map<String, BaseCFG> intraCFGs, BaseCFG interCFG, BaseCFG predecessor) {

        BaseCFG lifeCyle = null;

        if (intraCFGs.containsKey(method)) {
            lifeCyle = intraCFGs.get(method);
        } else {
            // use custom lifecycle CFG
            lifeCyle = dummyIntraProceduralCFG(method);
            interCFG.addSubGraph(lifeCyle);
        }

        interCFG.addEdge(predecessor.getExit(), lifeCyle.getEntry());
        return lifeCyle;
    }

    /**
     * Computes the inter procedural CFG using basic blocks. It links together the individual intra CFGs
     * by introducing edges from invoke calls to the entry point of an intra CFG. This causes that a basic
     * block is split into two parts, where one basic block solely consists of the invoke instruction, while
     * the second basic block contains a virtual return statement and all instructions up to the next leader
     * instruction.
     *
     * @param dexFile The dex file containing all classes and its methods.
     * @return Returns the inter procedural CFG containing basic blocks.
     * @throws IOException Should never happen.
     */
    private static BaseCFG computeInterCFGWithBasicBlocks(DexFile dexFile) throws IOException {

        // the final inter-procedural CFG
        BaseCFG interCFG = new InterProceduralCFG("globalEntryPoint");

        // construct for each method first the intra-procedural CFG (key: method signature)
        Map<String, BaseCFG> intraCFGs = new HashMap<>();
        constructIntraCFGs(dexFile, intraCFGs, true);

        // avoid concurrent modification exception
        Map<String, BaseCFG> intraCFGsCopy = new HashMap<>(intraCFGs);

        // store graphs already inserted into inter-procedural CFG
        Set<BaseCFG> coveredGraphs = new HashSet<>();

        // store the cloned intra CFGs
        Map<String, BaseCFG> intraCFGsClone = new HashMap<>();

        // stores the relevant onCreate methods
        List<BaseCFG> onCreateMethods = new ArrayList<>();

        // exclude certain methods/classes
        Pattern exclusionPattern = Utility.readExcludePatterns();

        // compute inter-procedural CFG by connecting intra CFGs
        for (Map.Entry<String, BaseCFG> entry : intraCFGsCopy.entrySet()) {

            // performs a deep copy of the intra CFG for possible later usage
            intraCFGsClone.put(entry.getKey(), entry.getValue().copy());

            BaseCFG cfg = entry.getValue();
            IntraProceduralCFG intraCFG = (IntraProceduralCFG) cfg;
            LOGGER.debug("Integrating CFG: " + intraCFG.getMethodName());

            if (intraCFG.getMethodName().startsWith("Lcom/zola/bmi/BMIMain")) {
                if (!coveredGraphs.contains(intraCFG)) {
                    // add first source graph
                    interCFG.addSubGraph(intraCFG);
                    coveredGraphs.add(intraCFG);
                }
            }

            // the method signature (className->methodName->params->returnType)
            String method = intraCFG.getMethodName();
            String className = Utility.dottedClassName(Utility.getClassName(method));
            LOGGER.debug("ClassName: " + className);

            // we need to model the android lifecycle as well -> collect onCreate methods
            if (method.contains("onCreate(Landroid/os/Bundle;)V") && !exclusionPattern.matcher(className).matches()) {
                // copy necessary, otherwise the sub graph misses some vertices
                onCreateMethods.add(intraCFG.copy());
            }

            LOGGER.debug("Searching for invoke instructions!");

            for (Vertex vertex : intraCFG.getVertices()) {

                if (vertex.isEntryVertex() || vertex.isExitVertex()) {
                    // entry and exit vertices do not have instructions attached, skip
                    continue;
                }

                // operate on cloned vertex
                // Vertex cloneVertex = vertex.clone();

                // all vertices are represented by block statements
                BlockStatement blockStatement = (BlockStatement) vertex.getStatement();

                // invoke instructions can only be the first instruction of a block statement
                BasicStatement statement = ((BasicStatement) blockStatement.getFirstStatement());
                Instruction instruction = statement.getInstruction().getInstruction();

                // check for invoke/invoke-range instruction
                if (instruction instanceof ReferenceInstruction
                        && (instruction instanceof Instruction3rc
                        || instruction instanceof Instruction35c)) {

                    Set<Edge> incomingEdges = intraCFG.getIncomingEdges(vertex);
                    Set<Edge> outgoingEdges = intraCFG.getOutgoingEdges(vertex);
                    LOGGER.debug("Incoming edges: " + incomingEdges.stream().map(e -> e.getSource().toString()).collect(Collectors.joining(",")));
                    LOGGER.debug("Outgoing edges: " + outgoingEdges.stream().map(e -> e.getTarget().toString()).collect(Collectors.joining(",")));

                    // search for target CFG by reference of invoke instruction (target method)
                    String methodSignature = ((ReferenceInstruction) instruction).getReference().toString();

                    // the CFG that corresponds to the invoke call
                    BaseCFG targetCFG;

                    if (intraCFGs.containsKey(methodSignature)) {
                        targetCFG = intraCFGs.get(methodSignature);
                        LOGGER.debug("Target CFG: " + ((IntraProceduralCFG) targetCFG).getMethodName());
                    } else {

                        /*
                         * There are some Android specific classes, e.g. android/view/View, which are
                         * not included in the classes.dex file for yet unknown reasons. Basically,
                         * these classes should be just treated like other classes from the ART.
                         */
                        LOGGER.warn("Target CFG for method: " + methodSignature + " not found!");
                        targetCFG = dummyIntraProceduralCFG(methodSignature);
                        intraCFGs.put(methodSignature, targetCFG);
                    }

                    if (intraCFG.getMethodName().startsWith("Lcom/zola/bmi/BMIMain")) {

                        if (!coveredGraphs.contains(targetCFG)) {
                            // add target graph to inter CFG
                            interCFG.addSubGraph(targetCFG);
                            coveredGraphs.add(targetCFG);
                        }

                        /*
                         * We need to remove the vertex, modify it offline and re-insert it.
                         * Directly modifying the vertex without removing/adding doesn't
                         * work, since the graph doesn't recognize anymore the vertex in
                         * the graph due to yet unknown reasons, probably equals() fails.
                         * Or stated differently, the vertex reference is no longer valid.
                         */

                        // remove the vertex from the graph -> removes edges as well inherently
                        interCFG.removeVertex(vertex);

                        // remove invoke from basic block
                        blockStatement.removeStatement(statement);

                        // add virtual return at front of basic block
                        Statement returnStmt = new ReturnStatement(vertex.getMethod(), targetCFG.getMethodName());
                        blockStatement.addStatement(0, returnStmt);

                        // add modified vertex to graph
                        interCFG.addVertex(vertex);

                        // add edge from exit to virtual return vertex
                        interCFG.addEdge(targetCFG.getExit(), vertex);

                        // the invoke statement is split off the basic block and represented as an own vertex
                        List<Statement> invokeStmt = new ArrayList<>();
                        invokeStmt.add(statement);
                        Vertex invokeVertex = new Vertex(new BlockStatement(intraCFG.getMethodName(), invokeStmt));
                        interCFG.addVertex(invokeVertex);

                        LOGGER.debug("Incoming edges of vertex " + vertex + ": " + incomingEdges);
                        LOGGER.debug("Outgoing edges of vertex " + vertex + ":" + outgoingEdges);

                        // add from each predecessor an edge to the invoke statement
                        for (Edge edge : incomingEdges) {
                            interCFG.addEdge(edge.getSource(), invokeVertex);
                        }

                        // add again each successor now to the return vertex
                        for (Edge edge : outgoingEdges) {
                            interCFG.addEdge(vertex, edge.getTarget());
                        }

                        // add edge from invoke instruction to entry vertex of target CFG
                        interCFG.addEdge(invokeVertex, targetCFG.getEntry());
                    }
                }
            }
            LOGGER.debug(System.lineSeparator());
        }

        // add the android lifecycle methods
        Map<String, BaseCFG> callbackEntryPoints = addAndroidLifecycleMethods(interCFG, intraCFGs, onCreateMethods);

        // add global entry point to each constructor and link constructor to onCreate method
        addGlobalEntryPoints(interCFG, intraCFGs, onCreateMethods);

        // add the callbacks specified either through XML or directly in code
        addCallbacks(interCFG, intraCFGs, callbackEntryPoints, dexFile);

        if (DEBUG_MODE) {
            // LOGGER.debug(interCFG);
            // intraCFGsClone.get("Lcom/zola/bmi/BMIMain;->onCreate(Landroid/os/Bundle;)V").drawGraph();
            interCFG.drawGraph();
        }

        return interCFG;
    }

    /**
     * Adds for each component (activity) a global entry point to the respective constructor. Additionally, an edge
     * is created between constructor CFG and the onCreate CFG since the constructor is called prior to onCreate().
     *
     * @param interCFG The inter-procedural CFG.
     * @param intraCFGs The set of intra-procedural CFGs.
     * @param onCreateMethods The set of onCreate methods (the respective CFGs).
     */
    private static void addGlobalEntryPoints(BaseCFG interCFG, Map<String, BaseCFG> intraCFGs, List<BaseCFG> onCreateMethods) {

        for (BaseCFG onCreate : onCreateMethods) {

            // each component defines a default constructor, which is called prior to onCreate()
            String className = Utility.getClassName(onCreate.getMethodName());
            String constructorName = className + "-><init>()V";

            if (intraCFGs.containsKey(constructorName)) {
                BaseCFG constructor = intraCFGs.get(constructorName);
                interCFG.addEdge(constructor.getExit(), onCreate.getEntry());

                // add global entry point to constructor
                interCFG.addEdge(interCFG.getEntry(), constructor.getEntry());
            }
        }
    }

    private static void addCallbacks(BaseCFG interCFG, Map<String, BaseCFG> intraCFGs,
                                     Map<String, BaseCFG> callbackEntryPoints, DexFile dexFile) throws IOException {

        // get callbacks directly declared in code
        Multimap<String, BaseCFG> callbacks = lookUpCallbacks(intraCFGs);

        // add for each android component, e.g. activity, its callbacks/listeners to its callbacks subgraph (the callback entry point)
        for (Map.Entry<String, BaseCFG> callbackEntryPoint : callbackEntryPoints.entrySet()) {
            callbacks.get(callbackEntryPoint.getKey()).forEach(cfg -> {
                interCFG.addEdge(callbackEntryPoint.getValue().getEntry(), cfg.getEntry());
                interCFG.addEdge(cfg.getExit(), callbackEntryPoint.getValue().getExit());
            });
        }

        // get callbacks declared in XML files
        Multimap<String, BaseCFG> callbacksXML = lookUpCallbacksXML(dexFile, intraCFGs);

        // add for each android component callbacks declared in XML to its callbacks subgraph (the callback entry point)
        for (Map.Entry<String, BaseCFG> callbackEntryPoint : callbackEntryPoints.entrySet()) {
            callbacksXML.get(callbackEntryPoint.getKey()).forEach(cfg -> {
                interCFG.addEdge(callbackEntryPoint.getValue().getEntry(), cfg.getEntry());
                interCFG.addEdge(cfg.getExit(), callbackEntryPoint.getValue().getExit());
            });
        }
    }

    private static void recursiveLayoutIDLookup() {

    }

    /**
     * Looks up callbacks declared in XML layout files and associates them to its defining component.
     *
     * @param dexFile The classes.dex file containing all classes and its methods.
     * @param intraCFGs The set of intra CFGs.
     * @return Returns a mapping between a component (its class name) and its callbacks (actually the
     *          corresponding intra CFGs). Each component may define multiple callbacks.
     * @throws IOException Should never happen.
     */
    private static Multimap<String, BaseCFG> lookUpCallbacksXML(DexFile dexFile, Map<String, BaseCFG> intraCFGs)
            throws IOException {

        // return value, key: name of component
        Multimap<String, BaseCFG> callbacks = TreeMultimap.create();

        // stores the relation between outer and inner classes
        Multimap<String, String> classRelations = TreeMultimap.create();

        // stores for each component its resource id in hexadecimal representation
        Map<String, String> componentResourceID = new HashMap<>();

        Pattern exclusionPattern = Utility.readExcludePatterns();

        for (ClassDef classDef : dexFile.getClasses()) {

            String className = Utility.dottedClassName(classDef.toString());

            if (!exclusionPattern.matcher(className).matches()) {

                // track outer/inner class relations
                if (Utility.isInnerClass(classDef.toString())) {
                    classRelations.put(Utility.getOuterClass(classDef.toString()), classDef.toString());
                }

                for (Method method : classDef.getMethods()) {

                    MethodImplementation methodImplementation = method.getImplementation();

                    if (methodImplementation != null
                            // we can speed up search for looking only for onCreate(..) and onCreateView(..)
                            // this assumes that only these two methods declare the layout via setContentView()/inflate()!
                            && method.getName().contains("onCreate")) {

                        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                                true, ClassPath.NOT_ART), method,
                                null, false);

                        for (AnalyzedInstruction analyzedInstruction : analyzer.getAnalyzedInstructions()) {

                            Instruction instruction = analyzedInstruction.getInstruction();

                            /*
                             * We need to search for calls to setContentView(..) and inflate(..).
                             * Both of them are of type invoke-virtual.
                             * TODO: check if there are cases where invoke-virtual/range is used
                             */
                            if (instruction.getOpcode() == Opcode.INVOKE_VIRTUAL) {

                                Instruction35c invokeVirtual = (Instruction35c) instruction;

                                // the method that is invoked by this instruction
                                String methodReference = invokeVirtual.getReference().toString();

                                if (methodReference.endsWith("setContentView(I)V")
                                        // ensure that setContentView() refers to the given class
                                        && classDef.toString().equals(Utility.getClassName(methodReference))) {
                                    // TODO: there are multiple overloaded setContentView() implementations
                                    // we assume here only setContentView(int layoutResID)
                                    // link: https://developer.android.com/reference/android/app/Activity.html#setContentView(int)

                                    /*
                                     * We need to find the resource id located in one of the registers. A typicall call to
                                     * setContentView(int layoutResID) looks as follows:
                                     *     invoke-virtual {p0, v0}, Lcom/zola/bmi/BMIMain;->setContentView(I)V
                                     * Here, v0 contains the resource id, thus we need to search backwards for the last
                                     * change of v0. This is typically the previous instruction and is of type 'const'.
                                     */

                                    LOGGER.debug("ClassName: " + classDef);
                                    LOGGER.debug("Method Reference: " + methodReference);
                                    LOGGER.debug("LayoutResID Register: " + invokeVirtual.getRegisterD());

                                    // the id of the register, which contains the layoutResID
                                    int layoutResIDRegister = invokeVirtual.getRegisterD();

                                    boolean foundLayoutResID = false;
                                    AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

                                    while (!foundLayoutResID) {

                                        LOGGER.debug("Predecessor: " + predecessor.getInstruction().getOpcode());
                                        Instruction pred = predecessor.getInstruction();

                                        // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                                        if (pred instanceof NarrowLiteralInstruction
                                                && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                                                || pred.getOpcode() == Opcode.CONST_16) && predecessor.setsRegister(layoutResIDRegister)) {
                                            foundLayoutResID = true;
                                            LOGGER.debug("XML ID: " + (((NarrowLiteralInstruction) pred).getNarrowLiteral()));
                                            int resourceID = ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                                            componentResourceID.put(classDef.toString(), "0x" + Integer.toHexString(resourceID));
                                        }

                                        predecessor = predecessor.getPredecessors().first();
                                    }
                                } else if (methodReference.endsWith("setContentView(Landroid/view/View;)V")
                                        // ensure that setContentView() refers to the given class
                                        && classDef.toString().equals(Utility.getClassName(methodReference))) {

                                    /*
                                    * A typical example of this call looks as follows:
                                    * invoke-virtual {v2, v3}, Landroid/widget/PopupWindow;->setContentView(Landroid/view/View;)V
                                    *
                                    * Here, register v2 is the PopupWindow instance while v3 refers to the View object param.
                                    * Thus, we need to search for the call of setContentView/inflate() on the View object
                                    * in order to retrieve its layout resource ID.
                                     */

                                    LOGGER.debug("Class " + className + " makes use of setContentView(View v)!");

                                    /*
                                    * TODO: are we interested in calls to setContentView(..) that don't refer to the this object?
                                    * The primary goal is to derive the layout ID of a given component (class). However, it seems
                                    * like classes (components) can define the layout of other (sub) components. Are we interested
                                    * in getting the layout ID of those (sub) components?
                                     */

                                    // we need to resolve the layout ID of the given View object parameter


                                } else if (methodReference.contains("Landroid/view/LayoutInflater;->inflate(")) {
                                    // TODO: there are multiple overloaded inflate() implementations
                                    // see: https://developer.android.com/reference/android/view/LayoutInflater.html#inflate(org.xmlpull.v1.XmlPullParser,%20android.view.ViewGroup,%20boolean)
                                    // we assume here inflate(int resource,ViewGroup root, boolean attachToRoot)

                                    /*
                                     * A typical call of inflate(int resource,ViewGroup root, boolean attachToRoot) looks as follows:
                                     *   invoke-virtual {p1, v0, p2, v1}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;
                                     * Here, v0 contains the resource id, thus we need to search backwards for the last change of v0.
                                     * This is typically the previous instruction and is of type 'const'.
                                     */

                                    LOGGER.debug("ClassName: " + classDef);
                                    LOGGER.debug("Method Reference: " + methodReference);
                                    LOGGER.debug("LayoutResID Register: " + invokeVirtual.getRegisterD());

                                    // the id of the register, which contains the layoutResID
                                    int layoutResIDRegister = invokeVirtual.getRegisterD();

                                    boolean foundLayoutResID = false;
                                    AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

                                    while (!foundLayoutResID) {

                                        LOGGER.debug("Predecessor: " + predecessor.getInstruction().getOpcode());
                                        Instruction pred = predecessor.getInstruction();

                                        // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                                        if (pred instanceof NarrowLiteralInstruction
                                                && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                                                || pred.getOpcode() == Opcode.CONST_16) && predecessor.setsRegister(layoutResIDRegister)) {
                                            foundLayoutResID = true;
                                            LOGGER.debug("XML ID: " + (((NarrowLiteralInstruction) pred).getNarrowLiteral()));
                                            int resourceID = ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                                            componentResourceID.put(classDef.toString(), "0x" + Integer.toHexString(resourceID));
                                        }

                                        predecessor = predecessor.getPredecessors().first();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LOGGER.debug(classRelations);
        LOGGER.debug(componentResourceID);

        /*
        * We now need to find the layout file for a given component. Then, we need to
        * parse it in order to get possible callbacks. Finally, we need to add these callbacks
        * to the 'callbacks' sub graph of the respecitve component.
         */

        // we need to first decode the APK to access its resource files
        decodeAPK();

        // we want to link a component to its associated layout file -> (parsing the public.xml file)
        // a typical entry has the following form: <className,layoutFileName>, e.g. <L../BMIMain;,activity_bmimain>
        Map<String, String> componentLayoutFile = retrieveComponentLayoutFile(componentResourceID);

        // we search in each component's layout file for specific tags describing callbacks
        // a typical entry has the following form: <className,callbackName>
        Multimap<String, String> componentCallbacks = findCallbacksXML(componentLayoutFile);
        LOGGER.debug(componentCallbacks);

        // associate each component with its intraCFGs representing callbacks
        for (String component : componentCallbacks.keySet()) {
            for (String callbackName : componentCallbacks.get(component)) {
                // TODO: may need to distinguish between different callbacks, e.g. onClick, onLongClick, ...
                // callbacks can have a custom method name but the rest of the method signature is fixed
                String callback = component + "->" + callbackName + "(Landroid/view/View;)V";

                // first check whether the callback is declared directly in its defining component
                if (intraCFGs.containsKey(callback)) {
                    callbacks.put(component, intraCFGs.get(callback));
                } else {
                    // check for outer class defining the callback in its code base
                    if (Utility.isInnerClass(component)) {
                        String outerClassName = Utility.getOuterClass(component);
                        callback = callback.replace(component, outerClassName);
                        if (intraCFGs.containsKey(callback)) {
                            callbacks.put(outerClassName, intraCFGs.get(callback));
                        }
                    }
                }
            }
        }
        return callbacks;
    }

    /**
     * Returns a mapping between a component (its class name) and associated callbacks declared in the component's
     * layout file. Each component can define multiple callbacks.
     *
     * @param componentLayoutFile A mapping between a component (its class name) and its associated layout file.
     * @return Returns a mapping between a component and its associated callbacks declared in its layout file.
     */
    private static Multimap<String, String> findCallbacksXML(Map<String, String> componentLayoutFile) {

        Multimap<String, String> componentCallbacks = TreeMultimap.create();

        for (Map.Entry<String, String> component : componentLayoutFile.entrySet()) {

            LOGGER.debug("Parsing layout file of component: " + component.getKey());

            String layoutFileName = component.getValue();

            final String layoutFilePath = decodingOutputPath + File.separator + "res" + File.separator
                    + "layout" + File.separator + layoutFileName + ".xml";

            SAXReader reader = new SAXReader();
            Document document = null;

            try {

                document = reader.read(new File(layoutFilePath));
                Element rootElement = document.getRootElement();

                Iterator itr = rootElement.elementIterator();
                while (itr.hasNext()) {

                    // each node is a widget, e.g. a button, textView, ...
                    // TODO: we may can exclude some sort of widgets
                    Node node = (Node) itr.next();
                    Element element = (Element) node;
                    // LOGGER.debug(element.getName());

                    // NOTE: we need to access the attribute WITHOUT its namespace -> can't use android:onClick!
                    String onClickCallback = element.attributeValue("onClick");
                    if (onClickCallback != null) {
                        LOGGER.debug(onClickCallback);
                        componentCallbacks.put(component.getKey(), onClickCallback);
                    }
                }
            } catch (DocumentException e) {
                LOGGER.error("Reading layout file " + layoutFileName + " failed");
                LOGGER.error(e.getMessage());
            }
        }
        return componentCallbacks;
    }

    /**
     * Parses the public.xml file to retrieve the layout file names associated with certain layout resource ids.
     * Returns a mapping between a component (its class name) and the associated layout file name.
     *
     * @param componentResourceID A map associating a component (its class name) with its resource id.
     * @return Returns a mapping between a component (its class name) and the associated layout file name.
     */
    private static Map<String, String> retrieveComponentLayoutFile(Map<String, String> componentResourceID) {

        Map<String, String> componentLayoutFile = new HashMap<>();

        final String publicXMLPath = decodingOutputPath + File.separator + "res" + File.separator
                + "values" + File.separator + "public.xml";

        LOGGER.debug("Path to public.xml file: " + publicXMLPath);

        SAXReader reader = new SAXReader();
        Document document = null;

        try {
            document = reader.read(new File(publicXMLPath));
            Element rootElement = document.getRootElement();
            // LOGGER.debug(rootElement.getName());

            Iterator itr = rootElement.elementIterator();
            while (itr.hasNext()) {

                // each node is a <public ... /> xml tag
                Node node = (Node) itr.next();
                Element element = (Element) node;
                // LOGGER.debug("ElementName: " + node.getName());

                // each <public /> tag contains the attributes type,name,id
                String layoutFile = element.attributeValue("name");
                String layoutResourceID = element.attributeValue("id");

                /*
                LOGGER.debug("Type: " + element.attributeValue("type"));
                LOGGER.debug("Name: " + element.attributeValue("name"));
                LOGGER.debug("ID: " + element.attributeValue("id"));
                */

                // check whether componentResourceID contains a mapping for the given resource ID
                List<String> componentName = componentResourceID
                        .entrySet()
                        .stream()
                        .filter(entry -> layoutResourceID.equals(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                if (!componentName.isEmpty()) {
                    // each component can have only one associated layout file/id
                    String className = componentName.get(0);
                    LOGGER.debug("Class: " + className + " has associated the following layout file: " + layoutFile);
                    componentLayoutFile.put(className, layoutFile);
                }
            }

        } catch (DocumentException e) {
            LOGGER.error("Reading public.xml failed");
            LOGGER.error(e.getMessage());
        }

        return componentLayoutFile;
    }

    /**
     * Returns for each component, e.g. an activity, its associated callbacks. It goes through all
     * intra CFGs looking for a specific callback by its full-qualified name. If there is a match,
     * we extract the defining component, which is typically the outer class, and the CFG representing
     * the callback.
     *
     * @param intraCFGs The set of all generated intra CFGs.
     * @return Returns a mapping between a component and its associated callbacks (can be multiple per instance).
     * @throws IOException Should never happen.
     */
    private static Multimap<String, BaseCFG> lookUpCallbacks(Map<String, BaseCFG> intraCFGs) throws IOException {

        /*
         * Rather than searching for the call of e.g. setOnClickListener() and following
         * the invocation to the corresponding onClick() method defined by some inner class,
         * we can directly search for the onClick() method and query the outer class (the component
         * defining the callback). We don't even need to go through the code, we can actually
         * look up in the set of intra CFGs for a specific listener through its FQN. To get
         * the outer class, we need to inspect the FQN of the inner class, which is of the following form:
         *       Lmy/package/OuterClassName$InnerClassName;
         * This means, we need to split the FQN at the '$' symbol to retrieve the name of the outer class.
         */

        // key: FQN of component defining a callback (may define several ones)
        Multimap<String, BaseCFG> callbacks = TreeMultimap.create();

        Pattern exclusionPattern = Utility.readExcludePatterns();

        for (Map.Entry<String, BaseCFG> intraCFG : intraCFGs.entrySet()) {
            String methodName = intraCFG.getKey();
            String className = Utility.getClassName(methodName);

            if (!exclusionPattern.matcher(Utility.dottedClassName(className)).matches()
                    // TODO: add missing callbacks for each event listener
                    // see: https://developer.android.com/guide/topics/ui/ui-events
                    // TODO: check whether there can be other custom event listeners
                    && (methodName.endsWith("onClick(Landroid/view/View;)V")
                        || methodName.endsWith("onLongClick(Landroid/view/View;)Z")
                        || methodName.endsWith("onFocusChange(Landroid/view/View;Z)V")
                        || methodName.endsWith("onKey(Landroid/view/View;ILandroid/view/KeyEvent;)Z")
                        || methodName.endsWith("onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z")
                        || methodName.endsWith("onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V"))) {
                // TODO: is it always an inner class???
                if (Utility.isInnerClass(methodName)) {
                    String outerClass = Utility.getOuterClass(className);
                    callbacks.put(outerClass, intraCFG.getValue());
                }
            }
        }
        return callbacks;
    }

    /**
     * Computes the inter procedural CFG with basic blocks or without depending on the given flag {@param useBasicBlocks}.
     *
     * @param dexFile        The dex file containing the classes and its methods.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @return Returns the inter procedural CFG.
     * @throws IOException Should never happen.
     */
    private static BaseCFG computeInterProceduralCFG(DexFile dexFile, boolean useBasicBlocks) throws IOException {

        if (useBasicBlocks) {
            return computeInterCFGWithBasicBlocks(dexFile);
        } else {
            return computeInterCFG(dexFile);
        }
    }

    /**
     * Computes the leader instructions. We consider branch targets, return statements, jump targets and invoke
     * instructions as leaders. As a side product, the indices of return instructions are tracked as well as
     * the edges between basic blocks are computed.
     *
     * @param analyzedInstructions The set of instructions for a given method.
     * @param basicBlockEdges      Contains the edges between basic blocks. Initially empty.
     * @param returnStmtIndices    Contains the instruction indices of return statements. Initially empty.
     * @return Returns a sorted list of leader instructions.
     */
    private static List<AnalyzedInstruction> computeLeaders(List<AnalyzedInstruction> analyzedInstructions,
                                                            Multimap<Integer, Integer> basicBlockEdges,
                                                            List<Integer> returnStmtIndices) {

        Set<AnalyzedInstruction> leaderInstructions = new HashSet<>();

        // the first instruction is also a leader
        leaderInstructions.add(analyzedInstructions.get(0));

        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {

            Instruction instruction = analyzedInstruction.getInstruction();

            if (instruction instanceof Instruction35c
                    || instruction instanceof Instruction3rc) {
                // invoke instruction
                leaderInstructions.add(analyzedInstruction);

                // each predecessor should be the last statement of a basic block
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();

                for (AnalyzedInstruction predecessor : predecessors) {
                    if (predecessor.getInstructionIndex() != -1) {
                        // there is a dummy instruction located at pos -1 which is the predecessor of the first instruction
                        basicBlockEdges.put(predecessor.getInstructionIndex(), analyzedInstruction.getInstructionIndex());
                    }
                }
            } else if (instruction.getOpcode() == Opcode.RETURN
                    || instruction.getOpcode() == Opcode.RETURN_OBJECT
                    || instruction.getOpcode() == Opcode.RETURN_VOID
                    || instruction.getOpcode() == Opcode.RETURN_VOID_BARRIER
                    || instruction.getOpcode() == Opcode.RETURN_VOID_NO_BARRIER
                    || instruction.getOpcode() == Opcode.RETURN_WIDE) {
                // return instruction
                leaderInstructions.add(analyzedInstruction);

                // save instruction index
                returnStmtIndices.add(analyzedInstruction.getInstructionIndex());

                // each predecessor should be the last statement of a basic block
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();

                for (AnalyzedInstruction predecessor : predecessors) {
                    if (predecessor.getInstructionIndex() != -1) {
                        // there is a dummy instruction located at pos -1 which is the predecessor of the first instruction
                        basicBlockEdges.put(predecessor.getInstructionIndex(), analyzedInstruction.getInstructionIndex());
                    }
                }
            } else if (instruction instanceof OffsetInstruction) {

                if (instruction instanceof Instruction21t
                        || instruction instanceof Instruction22t) {
                    // if instruction
                    List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();
                    leaderInstructions.addAll(successors);

                    // there is an edge from the if instruction to each successor
                    for (AnalyzedInstruction successor : successors) {
                        basicBlockEdges.put(analyzedInstruction.getInstructionIndex(), successor.getInstructionIndex());
                    }
                } else {
                    // some jump instruction, goto packed-switch, sparse-switch
                    List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();
                    leaderInstructions.addAll(successors);

                    // there is an edge from the jump instruction to each successor (there should be only one)
                    for (AnalyzedInstruction successor : successors) {
                        basicBlockEdges.put(analyzedInstruction.getInstructionIndex(), successor.getInstructionIndex());
                    }
                }
            }
        }

        List<AnalyzedInstruction> leaders = leaderInstructions.stream()
                .sorted((i1, i2) -> Integer.compare(i1.getInstructionIndex(), i2.getInstructionIndex())).collect(Collectors.toList());

        return leaders;
    }

    /**
     * Constructs the basic blocks for a given method and adds them to the given CFG.
     *
     * @param cfg                  The CFG that should contain the basic blocks.
     * @param analyzedInstructions The set of instructions of a given method.
     * @param leaders              The set of leader instructions previously identified.
     * @param vertexMap            A map that stores for each basic block (a vertex) the instruction id of
     *                             the first and last statement. So we have two entries per vertex.
     * @param methodName           The name of the method for which we want to construct the basic blocks.
     * @return Returns the basic blocks each as a list of instructions.
     */
    private static Set<List<AnalyzedInstruction>> constructBasicBlocks(BaseCFG cfg, List<AnalyzedInstruction> analyzedInstructions,
                                                                       List<AnalyzedInstruction> leaders, Map<Integer, Vertex> vertexMap,
                                                                       String methodName) {
        // stores all the basic blocks
        Set<List<AnalyzedInstruction>> basicBlocks = new HashSet<>();

        // the next leader index, not the leader at the first instruction!
        int nextLeaderIndex = 1;

        // the next leader
        AnalyzedInstruction nextLeader = leaders.get(nextLeaderIndex);

        // a single basic block
        List<AnalyzedInstruction> basicBlock = new ArrayList<>();

        // construct basic blocks and build graph
        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {

            // while we haven't found the next leader
            if (analyzedInstruction.getInstructionIndex() != nextLeader.getInstructionIndex()) {
                basicBlock.add(analyzedInstruction);
            } else {
                // we reached the next leader

                // update basic blocks
                List<AnalyzedInstruction> instructionsOfBasicBlock = new ArrayList<>(basicBlock);
                basicBlocks.add(instructionsOfBasicBlock);

                // construct a basic statement for each instruction
                List<Statement> stmts = instructionsOfBasicBlock.stream().map(i -> new BasicStatement(methodName, i)).collect(Collectors.toList());

                // construct the block statement
                Statement blockStmt = new BlockStatement(methodName, stmts);

                // each basic block is represented by a vertex
                Vertex vertex = new Vertex(blockStmt);

                int firstStmtIndex = basicBlock.get(0).getInstructionIndex();
                int lastStmtIndex = basicBlock.get(basicBlock.size() - 1).getInstructionIndex();
                vertexMap.put(firstStmtIndex, vertex);
                vertexMap.put(lastStmtIndex, vertex);

                cfg.addVertex(vertex);

                // reset basic block
                basicBlock.clear();

                // the leader we reached belongs to the next basic block
                basicBlock.add(nextLeader);

                // update the next leader
                nextLeaderIndex++;
                if (nextLeaderIndex >= leaders.size()) {
                    // add the last leader as basic block
                    basicBlocks.add(basicBlock);

                    // construct a basic statement for each instruction
                    stmts = basicBlock.stream().map(i -> new BasicStatement(methodName, i)).collect(Collectors.toList());

                    // construct the block statement
                    blockStmt = new BlockStatement(methodName, stmts);

                    // each basic block is represented by a vertex
                    vertex = new Vertex(blockStmt);

                    // identify each basic block by its first and last statement
                    firstStmtIndex = basicBlock.get(0).getInstructionIndex();
                    lastStmtIndex = basicBlock.get(basicBlock.size() - 1).getInstructionIndex();
                    vertexMap.put(firstStmtIndex, vertex);
                    vertexMap.put(lastStmtIndex, vertex);

                    cfg.addVertex(vertex);

                    break;
                } else {
                    nextLeader = leaders.get(nextLeaderIndex);
                }
            }
        }
        return basicBlocks;
    }

    /**
     * Computes the intra-procedural CFG for a given method. Uses basic blocks to reduce
     * the number of vertices. The underlying algorithm computes first the leader statements
     * and then groups statements between two leaders within a basic block.
     *
     * @param dexFile      The classes.dex containing all classes and its methods.
     * @param targetMethod The method for which we want to generate the CFG.
     * @return Returns the intra-procedural CFG using basic blocks for the given method.
     */
    private static BaseCFG computeIntraCFGWithBasicBlocks(DexFile dexFile, Method targetMethod) {

        String methodName = Utility.deriveMethodSignature(targetMethod);
        LOGGER.info("Method Signature: " + methodName);
        BaseCFG cfg = new IntraProceduralCFG(methodName);

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), targetMethod,
                null, false);
        List<AnalyzedInstruction> analyzedInstructions = analyzer.getAnalyzedInstructions();

        // stores the edge mapping between basic blocks based on the instruction id
        Multimap<Integer, Integer> basicBlockEdges = TreeMultimap.create();

        // keeps track of the instruction indices of return statements
        List<Integer> returnStmtIndices = new ArrayList<>();

        // compute the leader instructions, as a byproduct also compute the edges between the basic blocks + return indices
        List<AnalyzedInstruction> leaders = computeLeaders(analyzedInstructions, basicBlockEdges, returnStmtIndices);

        LOGGER.debug("Leaders: " + leaders.stream().map(instruction -> instruction.getInstructionIndex()).collect(Collectors.toList()));
        LOGGER.debug("Basic Block Edges: " + basicBlockEdges);

        // maps to each vertex the instruction id of the first and last statement
        Map<Integer, Vertex> vertexMap = new HashMap<>();

        // construct the basic blocks
        Set<List<AnalyzedInstruction>> basicBlocks = constructBasicBlocks(cfg, analyzedInstructions,
                leaders, vertexMap, methodName);

        LOGGER.debug("Number of BasicBlocks: " + basicBlocks.size());
        LOGGER.debug("Basic Blocks: " + basicBlocks.stream()
                .sorted((b1, b2) -> Integer.compare(b1.get(0).getInstructionIndex(), b2.get(0).getInstructionIndex()))
                .map(list -> list.stream()
                        .map(elem -> String.valueOf(elem.getInstructionIndex())).collect(Collectors.joining("-", "[", "]")))
                .collect(Collectors.joining(", ")));

        // connect the basic blocks
        for (Integer srcIndex : basicBlockEdges.keySet()) {

            LOGGER.debug("Source: " + srcIndex);
            Vertex src = vertexMap.get(srcIndex);

            Collection<Integer> targets = basicBlockEdges.get(srcIndex);

            for (Integer target : targets) {
                Vertex dest = vertexMap.get(target);
                LOGGER.debug("Target: " + target);
                cfg.addEdge(src, dest);
            }
            LOGGER.debug(System.lineSeparator());
        }

        // connect entry vertex with first basic block
        cfg.addEdge(cfg.getEntry(), vertexMap.get(0));

        // connect each return statement with exit vertex
        for (Integer returnIndex : returnStmtIndices) {
            cfg.addEdge(vertexMap.get(returnIndex), cfg.getExit());
        }

        // cfg.drawGraph();
        return cfg;
    }

    /**
     * Computes the intra-procedural CFG for a given method. Doesn't use basic blocks.
     *
     * @param dexFile      The classes.dex containing all classes and its methods.
     * @param targetMethod The method for which we want to generate the CFG.
     * @return Returns the intra-procedural CFG for the given method.
     */
    private static BaseCFG computeIntraCFG(DexFile dexFile, Method targetMethod) {

        String methodName = Utility.deriveMethodSignature(targetMethod);

        LOGGER.info("Method Signature: " + methodName);

        BaseCFG cfg = new IntraProceduralCFG(methodName);

        MethodImplementation methodImplementation = targetMethod.getImplementation();

        // FIXME: negate condition and return empty cfg in this, or sort them out already before
        if (methodImplementation == null) {
            LOGGER.info("No implementation found for: " + methodName);
            return dummyIntraProceduralCFG(targetMethod);
        }

        List<Instruction> instructions = Lists.newArrayList(methodImplementation.getInstructions());

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), targetMethod,
                null, false);

        List<AnalyzedInstruction> analyzedInstructions = analyzer.getAnalyzedInstructions();

        List<Vertex> vertices = new ArrayList<>();

        // pre-create vertices for each single instruction
        for (int index = 0; index < instructions.size(); index++) {
            Statement stmt = new BasicStatement(methodName, analyzedInstructions.get(index));
            Vertex vertex = new Vertex(stmt);
            cfg.addVertex(vertex);
            vertices.add(vertex);
        }

        LOGGER.debug("#Instructions: " + instructions.size());

        for (int index = 0; index < instructions.size(); index++) {

            LOGGER.debug("Instruction: " + vertices.get(index));

            AnalyzedInstruction analyzedInstruction = analyzedInstructions.get(index);

            // the current instruction represented as vertex
            Vertex vertex = vertices.get(index);

            // special treatment for first instruction (virtual entry node as predecessor)
            if (analyzedInstruction.isBeginningInstruction()) {
                cfg.addEdge(cfg.getEntry(), vertex);
            }

            Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
            Iterator<AnalyzedInstruction> iterator = predecessors.iterator();

            // add for each predecessor an incoming edge to the current vertex
            while (iterator.hasNext()) {
                AnalyzedInstruction predecessor = iterator.next();

                if (predecessor.getInstructionIndex() != -1) {
                    // not entry vertex
                    Vertex src = vertices.get(predecessor.getInstructionIndex());
                    // Vertex src = new Vertex(predecessor.getInstructionIndex(), predecessor.getInstruction());
                    LOGGER.debug("Predecessor: " + src);
                    cfg.addEdge(src, vertex);
                }
            }

            List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();

            if (successors.isEmpty()) {
                // must be a return statement, thus we need to insert an edge to the exit vertex
                cfg.addEdge(vertex, cfg.getExit());
            } else {
                // add for each successor an outgoing each from the current vertex
                for (AnalyzedInstruction successor : successors) {
                    Vertex dest = vertices.get(successor.getInstructionIndex());
                    // Vertex dest = new Vertex(successor.getInstructionIndex(), successor.getInstruction());
                    LOGGER.debug("Successor: " + dest);
                    cfg.addEdge(vertex, dest);
                }
            }
        }

        LOGGER.info(cfg.toString());
        // cfg.drawGraph();
        return cfg;
    }

    /**
     * Computes the intra-procedural CFG for a given method.
     *
     * @param dexFile        The classes.dex file containing all classes and its methods.
     * @param targetMethod   The method for which we want to generate the CFG.
     * @param useBasicBlocks Whether to use basic blocks in the construction of the CFG or not.
     * @return Returns the intra-procedural CFG for a given method.
     */
    private static BaseCFG computeIntraProceduralCFG(DexFile dexFile, Method targetMethod, boolean useBasicBlocks) {

        if (useBasicBlocks) {
            return computeIntraCFGWithBasicBlocks(dexFile, targetMethod);
        } else {
            return computeIntraCFG(dexFile, targetMethod);
        }
    }

}
