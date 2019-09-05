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
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
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
import org.jgrapht.graph.DefaultEdge;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingInt;

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
                    dexFiles.add(apk.getEntry(dexFile));
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
                    }
                    break;
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

        // avoid concurrent modification exception
        Map<String, BaseCFG> intraCFGsCopy = new HashMap<>(intraCFGs);

        // store graphs already inserted into inter-procedural CFG
        Set<BaseCFG> coveredGraphs = new HashSet<>();

        // compute inter-procedural CFG by connecting intra CFGs
        for (Map.Entry<String, BaseCFG> entry : intraCFGsCopy.entrySet()) {

            BaseCFG cfg = entry.getValue();
            IntraProceduralCFG intraCFG = (IntraProceduralCFG) cfg;
            LOGGER.debug(intraCFG.getMethodName());

            if (intraCFG.getMethodName().startsWith("Lcom/zola/bmi/BMIMain;")) {
                if (!coveredGraphs.contains(cfg)) {
                    // add first source graph
                    interCFG.addSubGraph(intraCFG);
                    coveredGraphs.add(cfg);
                }
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

                    if (intraCFG.getMethodName().startsWith("Lcom/zola/bmi/BMIMain;")) {

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

        if (DEBUG_MODE) {
            // intraCFGs.get("Lcom/android/calendar/AllInOneActivity;->checkAppPermissions()V").drawGraph();
            LOGGER.debug(interCFG);
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
     * Looks within the CFG representing the onCreate method for the component's (activity)
     * XML ID. This is done by searching for the setContentView() invocation, and retrieving
     * the XML ID from the predecessor instruction, typically  a 'const' instruction, holding
     * the ID.
     *
     * @param onCreateCFG The CFG of the onCreate() method.
     * @return Returns the XML ID of the component (activity) as integer, or -1 if the XML
     * ID couldn't be found.
     */
    private static int getComponentXMLID(BaseCFG onCreateCFG) {

        LOGGER.debug("Searching for " + onCreateCFG.getMethodName() + " XML ID!");

        // TODO: we assume that setContentView is called within onCreate!
        // search for setContentView invoke-virtual instruction
        for (Vertex vertex : onCreateCFG.getVertices()) {

            if (vertex.isEntryVertex() || vertex.isExitVertex()) {
                // no instruction attached, skip
                continue;
            }

            Statement stmt = vertex.getStatement();
            BasicStatement basicStatement = null;

            if (stmt.getType() == Statement.StatementType.BASIC_STATEMENT) {
                basicStatement = (BasicStatement) stmt;

            } else if (stmt.getType() == Statement.StatementType.BLOCK_STATEMENT) {
                BlockStatement blockStatement = (BlockStatement) stmt;
                // only the first instruction of a basic block can be an invoke instruction
                Statement statement = blockStatement.getFirstStatement();
                if (statement.getType() != Statement.StatementType.BASIC_STATEMENT) {
                    continue;
                }
                basicStatement = (BasicStatement) blockStatement.getFirstStatement();
            } else {
                continue;
            }

            AnalyzedInstruction analyzedInstruction = basicStatement.getInstruction();
            Instruction instruction = analyzedInstruction.getInstruction();

            // check for invoke virtual /invoke virtual range instruction
            if (instruction instanceof ReferenceInstruction
                    && (instruction.getOpcode() == Opcode.INVOKE_VIRTUAL
                    || instruction.getOpcode() == Opcode.INVOKE_VIRTUAL_RANGE
                    || instruction.getOpcode() == Opcode.INVOKE_VIRTUAL_QUICK
                    || instruction.getOpcode() == Opcode.INVOKE_VIRTUAL_QUICK_RANGE)) {

                String methodReference = ((ReferenceInstruction) instruction).getReference().toString();

                // TODO: there a three different kind of setContentView methods
                // https://developer.android.com/reference/android/app/Activity.html#setContentView(int)
                if (methodReference.endsWith("setContentView(I)V")) {
                    LOGGER.debug("Located setContentView() invocation!");

                    /*
                     * Typically the XML ID is loaded as a constant using one of the possible
                     * 'const' instructions. Directly afterwards, the XML ID is used within
                     * the respective invoke virtual instruction 'setContentView(...)V'. In terms
                     * of smali code, this looks as follows:
                     *
                     *     const v0, 0x7f0a001c
                     *     invoke-virtual {p0, v0}, Lcom/zola/bmi/BMIMain;->setContentView(I)V
                     *
                     * Thus, once we found the setContentView() invocation, we look at its predecessor
                     * instruction and extract the XML ID stored within its register. Note that we need
                     * to convert the obtained XML ID into its hexadecimal representation for further
                     * processing.
                     */

                    AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();
                    Instruction pred = predecessor.getInstruction();

                    // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                    if (pred instanceof NarrowLiteralInstruction
                            && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                            || pred.getOpcode() == Opcode.CONST_16)) {
                        LOGGER.debug("XML ID: " + (((NarrowLiteralInstruction) pred).getNarrowLiteral()));
                        return ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                    }
                }
            }
        }
        // we couldn't find the XML ID
        return -1;
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

        // add the callbacks specified either through XML or directly in code
        addCallbacks(interCFG, intraCFGs, callbackEntryPoints, dexFile);

        if (DEBUG_MODE) {
            // LOGGER.debug(interCFG);
            // intraCFGsClone.get("Lcom/zola/bmi/BMIMain;->onCreate(Landroid/os/Bundle;)V").drawGraph();
            interCFG.drawGraph();
        }

        return interCFG;
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
        // decodeAPK();

        // stores the relation between outer and inner classes
        Multimap<String, String> classRelations = TreeMultimap.create();

        lookUpCallbacksXML(dexFile, classRelations);
        // int xmlID = getComponentXMLID(onCreateMethod);

    }

    private static Multimap<String, BaseCFG> lookUpCallbacksXML(DexFile dexFile, Multimap<String, String> classRelations)
            throws IOException {

        // return value, key: name of component
        Multimap<String, BaseCFG> callbacks = TreeMultimap.create();

        Pattern exclusionPattern = Utility.readExcludePatterns();

        for (ClassDef classDef : dexFile.getClasses()) {

            String className = Utility.dottedClassName(Utility.getClassName(classDef.toString()));

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

                                if (methodReference.contains("setContentView")) {
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
                                        }

                                        predecessor = predecessor.getPredecessors().first();
                                    }
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

        // TODO: search in public.xml (res/values/) for name of layout files per component
        // we have a linkage between component and layout file, e.g. BMIMainActivity -> activity_bmimain

        // TODO: go to layout file and search for specific tags, e.g. android:onClick
        // we have a linkage which component defines a callback, e.g. fragment_bmimain -> fragmentOnClick()

        // TODO: search for callback method in associated component class file (first in inner class, then outer class)
        //  we now finally know which component reacts to which callback

        // TODO: add to callbacks subgraph (should probably outside this method)
        return callbacks;
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
                    && methodName.endsWith("onClick(Landroid/view/View;)V")) {
                // TODO: is it always an inner class???
                if (Utility.isInnerClass(methodName)) {
                    String outerClass = Utility.getOuterClass(className);
                    callbacks.put(outerClass, intraCFG.getValue());
                }
            }
        }
        return callbacks;
    }

    // inefficient, can be removed in the future
    private static void lookUpCallbacks(DexFile dexFile) throws IOException {

        /*
         * Since each callback/listener is represented as a (sequence of) method(s), e.g. onClick(),
         * we only need to find the FQN and retrieve the corresponding intra CFG, only the entry
         * point actually, and connect it to the callbacks sub-graph. We could use some brute force
         * approach by checking whether a given class contains a specific listener method. This can
         * be done by checking the set of intra CFGs for a specific method signature.
         *
         * TODO: return type -> multi-map of <component,callback(FQN)>
         *       get all callbacks, a class may define several of them
         */

        Pattern exclusionPattern = Utility.readExcludePatterns();

        // inner classes typically define listeners
        List<ClassDef> innerClasses = new ArrayList<>();
        String innerClass = null;

        for (ClassDef classDef : dexFile.getClasses()) {

            // we need to save the inner classes as they typically represent listeners
            if (Utility.isInnerClass(classDef.toString())) {
                innerClasses.add(classDef);
            }

            for (Method method : classDef.getMethods()) {

                MethodImplementation methodImplementation = method.getImplementation();
                String className = Utility.dottedClassName(Utility.getClassName(classDef.toString()));

                if (methodImplementation != null && !exclusionPattern.matcher(className).matches()) {

                    MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                            true, ClassPath.NOT_ART), method,
                            null, false);

                    for (AnalyzedInstruction analyzedInstruction : analyzer.getAnalyzedInstructions()) {

                        Instruction instruction = analyzedInstruction.getInstruction();

                        // check for invoke-virtual instruction
                        if (instruction.getOpcode() == Opcode.INVOKE_VIRTUAL) {

                            Instruction35c invokeVirtual = (Instruction35c) instruction;

                            // the target method
                            String methodReference = invokeVirtual.getReference().toString();

                            if (methodReference.contains("setOnClickListener")) {

                                LOGGER.debug("ClassName: " + classDef);
                                LOGGER.debug("Method Reference: " + methodReference);
                                LOGGER.debug("Callback-Instance Register: " + invokeVirtual.getRegisterD());

                                // the register id containing the onClickListener instance
                                int callbackReg = invokeVirtual.getRegisterD();

                                boolean foundListenerClass = false;
                                AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

                                /*
                                 * We need to search backwards for the inner class that defines the onClick method.
                                 * We do this by checking for a new-instance instruction, where the register id matches
                                 * the register id used within the setOnClickListener(..) instruction.
                                 */
                                while (!foundListenerClass) {

                                    LOGGER.debug("Predecessor: " + predecessor.getInstruction().getOpcode());
                                    Instruction pred = predecessor.getInstruction();

                                    if (pred.getOpcode() == Opcode.NEW_INSTANCE) {
                                        LOGGER.debug("found new instance instruction!");
                                        Instruction21c newInstance = (Instruction21c) pred;
                                        // we need to check that newInstance is containing the callback
                                        if (newInstance.getRegisterA() == callbackReg) {
                                            foundListenerClass = true;
                                            innerClass = newInstance.getReference().toString();
                                            LOGGER.debug("Callback Class: " + innerClass);
                                            LOGGER.debug("Is Inner Class: " + Utility.isInnerClass(innerClass));
                                        }
                                    }
                                    predecessor = predecessor.getPredecessors().first();
                                }
                            }
                        }
                    }
                }
            }
        }

        ClassDef targetClass = null;

        // search for inner class defining listener callback action
        for (ClassDef innerClassDef : innerClasses) {
            if (innerClassDef.toString().equals(innerClass)) {
                targetClass = innerClassDef;
                break;
            }
        }

        // search for the onClick method (virtual method)
        for (Method method : targetClass.getVirtualMethods()) {
            LOGGER.debug(method.toString());
            if (method.toString().endsWith("onClick(Landroid/view/View;)V")) {

            }
        }

        // save some mapping, e.g. (class/component -> btn,callback)
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
