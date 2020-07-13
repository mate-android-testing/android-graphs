package de.uni_passau.fim.auermich.graphs.cfg;

import com.google.common.collect.Lists;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.app.APK;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import de.uni_passau.fim.auermich.statement.BasicStatement;
import de.uni_passau.fim.auermich.statement.BlockStatement;
import de.uni_passau.fim.auermich.statement.ReturnStatement;
import de.uni_passau.fim.auermich.statement.Statement;
import de.uni_passau.fim.auermich.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.A;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class InterCFG extends BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(InterCFG.class);
    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    // whether for ART classes solely a dummy intra CFG should be generated
    private boolean excludeARTClasses;

    // track the set of activities
    private Set<String> activities = new HashSet<>();

    // track the set of fragments
    private Set<String> fragments = new HashSet<>();

    // maintain the individual intra CFGs
    Map<String, BaseCFG> intraCFGs = new HashMap<>();

    // copy constructor
    public InterCFG(String graphName) {
        super(graphName);
    }

    public InterCFG(String graphName, APK apk, boolean useBasicBlocks, boolean excludeARTClasses) {
        super(graphName);
        this.excludeARTClasses = excludeARTClasses;
        constructCFG(apk, useBasicBlocks);
    }

    private void constructCFG(APK apk, boolean useBasicBlocks) {

        // create the individual intraCFGs and add them as sub graphs
        constructIntraCFGs(apk, useBasicBlocks);

        if (useBasicBlocks) {
            constructCFGWithBasicBlocks(apk);
        } else {
            constructCFG(apk);
        }
    }

    private void constructCFGWithBasicBlocks(APK apk) {

        LOGGER.debug("Constructing Inter CFG with basic blocks!");

        // exclude certain classes and methods from graph
        Pattern exclusionPattern = Utility.readExcludePatterns();
        
        // resolve the invoke vertices and connect the sub graphs with each other
        for (Vertex invokeVertex : getInvokeVertices()) {

            BlockStatement blockStatement = (BlockStatement) invokeVertex.getStatement();
            List<List<Statement>> blocks = splitBlockStatement(blockStatement, exclusionPattern);

            if (blocks.size() == 1) {
                // the vertex is not split, no need to delete and re-insert the vertex
                LOGGER.debug("Unchanged vertex: " + invokeVertex + " [" + invokeVertex.getMethod() + "]" );
                continue;
            }

            LOGGER.debug("Invoke Vertex: " + invokeVertex + " [" + invokeVertex.getMethod() + "]");

            // save the predecessors and successors as we remove the vertex
            Set<Vertex> predecessors = getIncomingEdges(invokeVertex).stream().map(Edge::getSource).collect(Collectors.toSet());
            Set<Vertex> successors = getOutgoingEdges(invokeVertex).stream().map(Edge::getTarget).collect(Collectors.toSet());

            // remove original vertex, inherently removes edges
            removeVertex(invokeVertex);

            List<Vertex> blockVertices = new ArrayList<>();
            List<Vertex> exitVertices = new ArrayList<>();

            for (int i = 0; i < blocks.size(); i++) {

                // create a new vertex for each block
                List<Statement> block = blocks.get(i);
                Statement blockStmt = new BlockStatement(invokeVertex.getMethod(), block);
                Vertex blockVertex = new Vertex(blockStmt);
                blockVertices.add(blockVertex);

                // add modified block vertex to graph
                addVertex(blockVertex);

                // first block, add original predecessors to first block
                if (i == 0) {
                    LOGGER.debug("Number of predecessors: " + predecessors.size());
                    for (Vertex predecessor : predecessors) {
                        addEdge(predecessor, blockVertex);
                    }
                }

                // last block, add original successors to the last block
                if (i == blocks.size() - 1) {
                    LOGGER.debug("Number of successors: " + successors.size());
                    for (Vertex successor : successors) {
                        addEdge(blockVertex, successor);
                    }
                    // the last block doesn't contain any invoke instruction -> no target CFG
                    break;
                }

                // get target method CFG
                BasicStatement invokeStmt = (BasicStatement) ((BlockStatement) blockStmt).getLastStatement();
                Instruction instruction = invokeStmt.getInstruction().getInstruction();
                String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

                // the CFG that corresponds to the invoke call
                BaseCFG targetCFG;

                if (intraCFGs.containsKey(targetMethod)) {
                    targetCFG = intraCFGs.get(targetMethod);
                } else {

                    /*
                     * There are some Android specific classes, e.g. android/view/View, which are
                     * not included in the classes.dex file for yet unknown reasons. Basically,
                     * these classes should be just treated like other classes from the ART.
                     */
                    LOGGER.debug("Target method " + targetMethod + " not contained in dex files!");
                    targetCFG = dummyIntraProceduralCFG(targetMethod);
                    intraCFGs.put(targetMethod, targetCFG);
                    addSubGraph(targetCFG);
                }

                // add edge to entry of target CFG
                addEdge(blockVertex, targetCFG.getEntry());

                // save exit vertex -> there is an edge to the return vertex
                exitVertices.add(targetCFG.getExit());
            }

            // add edge from each targetCFG's exit vertex to the return vertex (next block)
            for (int i = 0; i < exitVertices.size(); i++) {
                addEdge(exitVertices.get(i), blockVertices.get(i + 1));
            }
        }
    }

    private List<List<Statement>> splitBlockStatement(BlockStatement blockStatement, Pattern exclusionPattern) {

        List<List<Statement>> blocks = new ArrayList<>();

        List<Statement> block = new ArrayList<>();

        List<Statement> statements = blockStatement.getStatements();

        for (Statement statement : statements) {

            BasicStatement basicStatement = (BasicStatement) statement;
            AnalyzedInstruction analyzedInstruction = basicStatement.getInstruction();

            if (!Utility.isInvokeInstruction(analyzedInstruction)) {
                // statement belongs to current block
                block.add(statement);
            } else {

                // invoke instruction belongs to current block
                block.add(statement);

                // get the target method of the invocation
                Instruction instruction = analyzedInstruction.getInstruction();
                String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
                String className = Utility.dottedClassName(Utility.getClassName(targetMethod));

                // don't resolve certain classes/methods, e.g. ART methods
                if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || (Utility.isARTMethod(targetMethod) && excludeARTClasses)) {
                    continue;
                }

                // save block
                blocks.add(block);

                // reset block
                block = new ArrayList<>();

                // add return statement to next block
                block.add(new ReturnStatement(blockStatement.getMethod(), targetMethod,
                        analyzedInstruction.getInstructionIndex()));
            }
        }

        // add last block
        if (!block.isEmpty()) {
            blocks.add(block);
        }

        return blocks;
    }

    private void constructCFG(APK apk) {

        LOGGER.debug("Constructing Inter CFG!");
    }

    private void constructIntraCFGs(APK apk, boolean useBasicBlocks) {

        LOGGER.debug("Constructing IntraCFGs!");
        final Pattern exclusionPattern = Utility.readExcludePatterns();

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                String className = Utility.dottedClassName(classDef.toString());

                if (Utility.isResourceClass(classDef) || Utility.isBuildConfigClass(classDef)) {
                    LOGGER.debug("Skipping resource/build class: " + className);
                    // skip R + BuildConfig classes
                    continue;
                }

                // as a side effect track whether the given class represents an activity or fragment
                if (exclusionPattern != null && !exclusionPattern.matcher(className).matches()) {
                    if (Utility.isActivity(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                            activities.add(classDef.toString());
                    } else if (Utility.isFragment(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                            fragments.add(classDef.toString());
                    }
                }

                for (Method method : classDef.getMethods()) {

                    String methodSignature = Utility.deriveMethodSignature(method);

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || Utility.isARTMethod(methodSignature)) {
                        // only construct dummy CFG for non ART classes
                        if (!excludeARTClasses) {
                            // dummy CFG consisting only of entry, exit vertex and edge between
                            intraCFGs.put(methodSignature, dummyIntraProceduralCFG(method));
                        }
                    } else {
                        LOGGER.debug("Method: " + methodSignature);
                        intraCFGs.put(methodSignature, new IntraCFG(methodSignature, dexFile, useBasicBlocks));
                    }
                }
            }
        }

        LOGGER.debug("Generated CFGs: " + intraCFGs.size());
        LOGGER.debug("List of activities: " + activities);
        LOGGER.debug("List of fragments: " + fragments);

        // add intraCFGs as sub graphs + update collected invoke vertices
        intraCFGs.forEach((name,intraCFG) -> {
            addSubGraph(intraCFG);
            addInvokeVertices(intraCFG.getInvokeVertices());
        });

        LOGGER.debug("Invoke Vertices: " + getInvokeVertices().size());
    }

    private void constructIntraCFGsParallel(APK apk, boolean useBasicBlocks) {

        LOGGER.debug("Constructing IntraCFGs!");

        final Pattern exclusionPattern = Utility.readExcludePatterns();

        apk.getDexFiles().parallelStream().forEach(dexFile -> {

            dexFile.getClasses().parallelStream().forEach(classDef -> {

                String className = Utility.dottedClassName(classDef.toString());

                // as a side effect track whether the given class represents an activity or fragment
                if (exclusionPattern != null && !exclusionPattern.matcher(className).matches()) {
                    if (Utility.isActivity(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        synchronized (this) {
                            LOGGER.debug("Activity detected!");
                            activities.add(classDef.toString());
                        }
                    } else if (Utility.isFragment(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        synchronized (this) {
                            LOGGER.debug("Fragment detected!");
                            fragments.add(classDef.toString());
                        }
                    }
                }

                StreamSupport.stream(classDef.getMethods().spliterator(), true).forEach(method -> {

                    String methodSignature = Utility.deriveMethodSignature(method);
                    LOGGER.debug("Method: " + methodSignature);

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || Utility.isARTMethod(methodSignature)) {
                        if (!excludeARTClasses) {
                            // dummy CFG consisting only of entry, exit vertex and edge between
                            synchronized (this) {
                                BaseCFG intraCFG = dummyIntraProceduralCFG(method);
                                addSubGraph(intraCFG);
                                intraCFGs.put(methodSignature, intraCFG);
                            }
                        }
                    } else {
                        synchronized (this) {
                            BaseCFG intraCFG = new IntraCFG(methodSignature, dexFile, useBasicBlocks);
                            addSubGraph(intraCFG);
                            intraCFGs.put(methodSignature, intraCFG);
                        }
                    }
                });
            });
        });
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraProceduralCFG(Method targetMethod) {

        BaseCFG cfg = new IntraCFG(Utility.deriveMethodSignature(targetMethod));
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
    private BaseCFG dummyIntraProceduralCFG(String targetMethod) {

        BaseCFG cfg = new IntraProceduralCFG(targetMethod);
        cfg.addEdge(cfg.getEntry(), cfg.getExit());
        return cfg;
    }

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    @Override
    public BaseCFG copy() {
        BaseCFG clone = new InterCFG(getMethodName());

        Graph<Vertex, Edge> graphClone = GraphTypeBuilder
                .<Vertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                .edgeClass(Edge.class).buildGraph();

        Set<Vertex> vertices = graph.vertexSet();
        Set<Edge> edges = graph.edgeSet();

        Cloner cloner = new Cloner();

        for (Vertex vertex : vertices) {
            graphClone.addVertex(cloner.deepClone(vertex));
        }

        for (Edge edge : edges) {
            Vertex src = cloner.deepClone(edge.getSource());
            Vertex dest = cloner.deepClone(edge.getTarget());
            graphClone.addEdge(src, dest);
        }

        clone.graph = graphClone;
        return clone;
    }
}
