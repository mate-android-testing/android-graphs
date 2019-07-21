package de.uni_passau.fim.auermich;

import com.beust.jcommander.JCommander;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
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
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OffsetInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21t;
import org.jf.dexlib2.iface.instruction.formats.Instruction22t;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;
import org.jf.dexlib2.iface.instruction.formats.Instruction3rc;
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

    private static BaseCFG computeInterCFGWithBasicBlocks(DexFile dexFile) throws IOException {
        return new InterProceduralCFG("dummy");
    }


    private static BaseCFG computeInterProceduralCFG(DexFile dexFile, boolean useBasicBlocks) throws IOException {

        if (useBasicBlocks) {
            return computeInterCFGWithBasicBlocks(dexFile);
        } else {
            return computeInterCFG(dexFile);
        }
    }

    /**
     * Computes the intra-procedural CFG for a given method. Uses basic blocks to reduce
     * the number of vertices. The underlying algorithm computes first the leader statements
     * and then groups statements between two leaders within a basic block.
     *
     * @param dexFile The classes.dex containing all classes and its methods.
     * @param targetMethod The method for which we want to generate the CFG.
     * @return Returns the intra-procedural CFG using basic blocks for the given method.
     */
    private static BaseCFG computeIntraCFGWithBasicBlocks(DexFile dexFile, Method targetMethod) {

        String methodName = Utility.deriveMethodSignature(targetMethod);
        LOGGER.info("Method Signature: " + methodName);
        BaseCFG cfg = new IntraProceduralCFG(methodName);

        MethodImplementation methodImplementation = targetMethod.getImplementation();
        MutableMethodImplementation mutableMethodImplementation = new MutableMethodImplementation(methodImplementation);
        List<BuilderInstruction> instructions = mutableMethodImplementation.getInstructions();

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), targetMethod,
                null, false);

        List<AnalyzedInstruction> analyzedInstructions = analyzer.getAnalyzedInstructions();

        // stores the edge mapping between basic blocks based on the instruction id
        Multimap<Integer, Integer> basicBlockEdges = TreeMultimap.create();

        Set<AnalyzedInstruction> leaderInstructions = new HashSet<>();

        List<Integer> returnStmtIndices = new ArrayList<>();

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
                    basicBlockEdges.put(predecessor.getInstructionIndex(), analyzedInstruction.getInstructionIndex());
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
                    basicBlockEdges.put(predecessor.getInstructionIndex(), analyzedInstruction.getInstructionIndex());
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
                .sorted((i1,i2) -> Integer.compare(i1.getInstructionIndex(),i2.getInstructionIndex())).collect(Collectors.toList());
        leaders.forEach(instruction ->LOGGER.debug(instruction.getInstructionIndex()));
        LOGGER.debug(basicBlockEdges);

        // stores all the basic blocks
        Set<List<AnalyzedInstruction>> basicBlocks = new HashSet<>();

        // the next leader index, not the leader at the first instruction!
        int nextLeaderIndex = 1;

        // the next leader
        AnalyzedInstruction nextLeader = leaders.get(nextLeaderIndex);

        // a single basic block
        List<AnalyzedInstruction> basicBlock = new ArrayList<>();

        // maps to each vertex the instruction id of the last statement
        Map<Integer, Vertex> vertexMap = new HashMap<>();

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
                List<Statement> stmts = instructionsOfBasicBlock.stream().map(i -> new BasicStatement(methodName,i)).collect(Collectors.toList());

                // construct the block statement
                Statement blockStmt = new BlockStatement(methodName, stmts);

                // each basic block is represented by a vertex
                Vertex vertex = new Vertex(blockStmt);

                int firstStmtIndex = basicBlock.get(0).getInstructionIndex();
                int lastStmtIndex = basicBlock.get(basicBlock.size()-1).getInstructionIndex();
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
                    stmts = basicBlock.stream().map(i -> new BasicStatement(methodName,i)).collect(Collectors.toList());

                    // construct the block statement
                    blockStmt = new BlockStatement(methodName, stmts);

                    // each basic block is represented by a vertex
                    vertex = new Vertex(blockStmt);

                    // identify each basic block by its first and last statement
                    firstStmtIndex = basicBlock.get(0).getInstructionIndex();
                    lastStmtIndex = basicBlock.get(basicBlock.size()-1).getInstructionIndex();
                    vertexMap.put(firstStmtIndex, vertex);
                    vertexMap.put(lastStmtIndex, vertex);

                    cfg.addVertex(vertex);

                    break;
                } else {
                    nextLeader = leaders.get(nextLeaderIndex);
                }
            }
        }

        LOGGER.debug("Number of BasicBlocks: " + basicBlocks.size());
        basicBlocks.forEach(bb -> {
            bb.forEach(i -> {
                // LOGGER.debug(i);
                LOGGER.debug(i.getInstructionIndex());
            }); LOGGER.debug(System.lineSeparator());});

        LOGGER.debug("Size of vertexMap: "+ vertexMap.size());

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

        cfg.drawGraph();
        return cfg;
    }

    /**
     * Computes the intra-procedural CFG for a given method. Doesn't use basic blocks.
     *
     * @param dexFile The classes.dex containing all classes and its methods.
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
        cfg.drawGraph();
        return cfg;
    }

    /**
     * Computes the intra-procedural CFG for a given method.
     *
     * @param dexFile The classes.dex file containing all classes and its methods.
     * @param targetMethod The method for which we want to generate the CFG.
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
