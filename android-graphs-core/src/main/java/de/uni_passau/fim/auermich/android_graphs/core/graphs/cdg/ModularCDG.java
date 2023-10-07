package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.Manifest;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGEdge;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGVertex;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.IntraCFG;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;
import de.uni_passau.fim.auermich.android_graphs.core.utility.Properties;
import de.uni_passau.fim.auermich.android_graphs.core.utility.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class ModularCDG extends BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(ModularCDG.class);
    private static final GraphType GRAPH_TYPE = GraphType.MODULARCDG;

    /**
     * Properties relevant for the construction process, e.g. whether basic blocks should be used.
     */
    private Properties properties;

    /**
     * Maintains a reference to the individual intra CDGs.
     * NOTE: Only a reference to the entry and exit vertex is hold!
     */
    private final Map<String, BaseCFG> intraCDGs = new HashMap<>();

    /**
     * The APK file.
     */
    private APK apk = null;

    // necessary for the copy constructor
    public ModularCDG(String graphName) {
        super(graphName);
    }

    public ModularCDG(String graphName, APK apk, boolean useBasicBlocks,
                      boolean excludeARTClasses, boolean resolveOnlyAUTClasses) {
        super(graphName);
        this.properties = new Properties(useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
        this.apk = apk;
        addEdge(getEntry(), getExit()); // exit is always control-dependent on entry
        constructCDG(apk);
    }

    /**
     * Returns the APK.
     *
     * @return Returns the APK.
     */
    public APK getApk() {
        return apk;
    }

    /**
     * Retrieves the mapping to the intra CDGs.
     *
     * @return Returns a mapping to the entry and exit vertices of the individual intra CDGs.
     */
    public Map<String, BaseCFG> getIntraCDGs() {
        return Collections.unmodifiableMap(intraCDGs);
    }

    private void constructCDG(APK apk) {

        // decode APK to access manifest and other resource files
        apk.decodeAPK();

        // parse manifest
        apk.setManifest(Manifest.parse(new File(apk.getDecodingOutputPath(), "AndroidManifest.xml")));

        // parse the resource strings
        apk.setResourceStrings(ResourceUtils.parseStringsXMLFile(apk.getDecodingOutputPath()));

        // create the individual intraCDGs and add them as sub graphs
        constructIntraCDGs(apk, properties.useBasicBlocks);

        LOGGER.debug("Removing decoded APK files: " + Utility.removeFile(apk.getDecodingOutputPath()));

        if (properties.useBasicBlocks) {
            constructCDGWithBasicBlocks(apk);
        } else {
            constructCDGNoBasicBlocks(apk);
        }
    }

    private void constructIntraCDGs(APK apk, boolean useBasicBlocks) {

        LOGGER.debug("Constructing IntraCDGs!");
        final Pattern exclusionPattern = properties.exclusionPattern;

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                final String className = ClassUtils.dottedClassName(classDef.toString());

                if (properties.resolveOnlyAUTClasses && !className.startsWith(apk.getManifest().getPackageName())) {
                    // don't resolve classes not belonging to AUT
                    continue;
                }

                if (ClassUtils.isResourceClass(classDef) || ClassUtils.isBuildConfigClass(classDef)) {
                    LOGGER.debug("Skipping resource/build class: " + className);
                    // skip R + BuildConfig classes
                    continue;
                }

                for (Method method : classDef.getMethods()) {

                    final String methodSignature = MethodUtils.deriveMethodSignature(method);

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || MethodUtils.isARTMethod(methodSignature)) {
                        // only construct dummy CFG for non ART classes
                        if (!properties.excludeARTClasses) {
                            final BaseCFG intraCDG = dummyIntraCDG(method);
                            addSubGraph(intraCDG);
                            intraCDGs.put(methodSignature, intraCDG);
                        }
                    } else {
                        // exclude methods from java.lang.Object, e.g. notify()
                        if (!MethodUtils.isJavaObjectMethod(methodSignature)) {
                            LOGGER.debug("Method: " + methodSignature);

                            final BaseCFG intraCDG = new IntraCDG(new IntraCFG(method, dexFile, useBasicBlocks));
                            addSubGraph(intraCDG);
                            addInvokeVertices(intraCDG.getInvokeVertices());
                            // only hold a reference to the entry and exit vertex
                            intraCDGs.put(methodSignature, new DummyCDG(intraCDG));
                        }
                    }
                }
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
    private BaseCFG dummyIntraCDG(Method targetMethod) {
        final BaseCFG cdg = new DummyCDG(MethodUtils.deriveMethodSignature(targetMethod));
        cdg.addEdge(cdg.getEntry(), cdg.getExit());
        return cdg;
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraCDG(String targetMethod) {
        final BaseCFG cdg = new DummyCDG(targetMethod);
        cdg.addEdge(cdg.getEntry(), cdg.getExit());
        return cdg;
    }

    /**
     * Creates and adds a dummy CDG for the given method.
     *
     * @param targetMethod The method for which a dummy CFG should be generated.
     * @return Returns the constructed dummy CFG.
     */
    private BaseCFG dummyCDG(String targetMethod) {
        final BaseCFG targetCDG = dummyIntraCDG(targetMethod);
        intraCDGs.put(targetMethod, targetCDG);
        addSubGraph(targetCDG);
        return targetCDG;
    }

    private void constructCDGNoBasicBlocks(APK apk) {
        // TODO: Implement.
    }

    /**
     * Constructs the inter CFG using basic blocks for a given app.
     *
     * @param apk The APK file describing the app.
     */
    private void constructCDGWithBasicBlocks(APK apk) {

        LOGGER.debug("Constructing modular CDG with basic blocks!");

        final String packageName = apk.getManifest().getPackageName();

        // resolve the invoke vertices and connect the sub graphs with each other
        for (CFGVertex invokeVertex : getInvokeVertices()) {

            BlockStatement blockStatement = (BlockStatement) invokeVertex.getStatement();

            for (Statement statement : blockStatement.getStatements()) {

                if (statement instanceof BasicStatement
                        && InstructionUtils.isInvokeInstruction(((BasicStatement) statement).getInstruction())) {
                    final BasicStatement invokeStmt = (BasicStatement) statement;
                    final BaseCFG targetCDG = lookupTargetCDG(packageName, invokeStmt);
                    if (targetCDG != null) {
                        addEdge(invokeVertex, targetCDG.getEntry());
                    }
                }
            }
        }

        for (Map.Entry<String, BaseCFG> entry : intraCDGs.entrySet()) {
            if (getIncomingEdges(entry.getValue().getEntry()).size() == 0) {
                // connect all non-connected sub graphs with global entry
                addEdge(getEntry(), entry.getValue().getEntry());
            }
        }
    }

    private BaseCFG lookupTargetCDG(final String packageName, final BasicStatement invokeStmt) {

        final Instruction instruction = invokeStmt.getInstruction().getInstruction();
        final String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
        final String className = MethodUtils.getClassName(targetMethod);

        if (properties.resolveOnlyAUTClasses && !ClassUtils.dottedClassName(className).startsWith(packageName)) {
            // don't resolve invocation to non AUT classes
            return null;
        }

        if (intraCDGs.containsKey(targetMethod)) {
            return intraCDGs.get(targetMethod);
        } else {
            return dummyCDG(targetMethod);
        }
    }

    /**
     * Searches for the vertex described by the given trace in the graph.
     * <p>
     * Searching an entry/exit vertex can be satisfied in O(1).
     * When a search of an intermediate vertex is requested, all directed
     * paths from the subgraph are traversed in a parallel manner.
     *
     * @param trace The trace describing the vertex, i.e. className->methodName->(entry|exit|instructionIndex).
     * @return Returns the vertex corresponding to the given trace.
     */
    @Override
    public CFGVertex lookUpVertex(String trace) {

        /*
         * A trace has the following form:
         *   className -> methodName -> ([entry|exit|if|switch])? -> (index)?
         *
         * The first two components are always fixed, while the instruction type and the instruction index
         * are optional, but not both at the same time:
         *
         * Making the instruction type optional allows to search (by index) for a custom instruction, e.g. a branch.
         * Making the index optional allows to look up virtual entry and exit vertices as well as if and switch vertices.
         */
        String[] tokens = trace.split("->");

        // retrieve fully qualified method name (class name + method name)
        final String method = tokens[0] + "->" + tokens[1];

        // check whether method belongs to graph
        if (!intraCDGs.containsKey(method)) {
            throw new IllegalArgumentException("Given trace refers to a method not part of the graph: " + method);
        }

        if (tokens.length == 3) {

            if (tokens[2].equals("entry")) {
                return intraCDGs.get(method).getEntry();
            } else if (tokens[2].equals("exit")) {
                return intraCDGs.get(method).getExit();
            } else {
                // lookup of a branch
                int instructionIndex = Integer.parseInt(tokens[2]);
                CFGVertex entry = intraCDGs.get(method).getEntry();
                return lookUpVertex(method, instructionIndex, entry);
            }

        } else if (tokens.length == 4) {

            // String instructionType = tokens[2];
            int instructionIndex = Integer.parseInt(tokens[3]);
            CFGVertex entry = intraCDGs.get(method).getEntry();
            return lookUpVertex(method, instructionIndex, entry);

        } else if (tokens.length == 5) { // basic block coverage trace

            int instructionIndex = Integer.parseInt(tokens[2]);
            CFGVertex entry = intraCDGs.get(method).getEntry();
            return lookUpVertex(method, instructionIndex, entry);

        } else {
            throw new IllegalArgumentException("Unrecognized trace: " + trace);
        }
    }

    /**
     * Looks up a vertex in the graph.
     *
     * @param method The method the vertex belongs to.
     * @param instructionIndex The instruction index.
     * @param entry The entry vertex of the given method (bound for the look up).
     * @param exit The exit vertex of the given method (bound for the look up).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     *         a {@link IllegalArgumentException} is thrown.
     */
    @SuppressWarnings("unused")
    private CFGVertex lookUpVertex(String method, int instructionIndex, CFGVertex entry, CFGVertex exit) {

        /*
         * If the 'AllDirectedPaths' algorithm appears to be too slow, we could alternatively use
         * some traversal strategy supplied by JGraphT, see
         * https://jgrapht.org/javadoc-1.4.0/org/jgrapht/traverse/package-summary.html.
         * If this is still not good enough, we can roll out our own search algorithm. One could
         * perform a parallel forward/backward search starting from the entry and exit vertex, respectively.
         * If a forward/backward step falls out of the given method, i.e. a vertex of a different method is reached,
         * we can directly jump from the entry vertex to the virtual return vertex in case of a forward step,
         * otherwise (a backward step was performed) we can directly jump to the invoke vertex leading to
         * the entry of the different method.
         */

        AllDirectedPaths<CFGVertex, CFGEdge> allDirectedPaths = new AllDirectedPaths<>(graph);

        /*
         * In general this algorithm is really fast, however, when dealing with cycles in the graph, the algorithm
         * fails to find the desired vertex. If we adjust the 'simplePathsOnly' parameter to handle cycles the
         * algorithm can be really slow.
         */
        List<GraphPath<CFGVertex, CFGEdge>> paths = allDirectedPaths.getAllPaths(entry, exit, true, null);

        // https://stackoverflow.com/questions/64929090/nested-parallel-stream-execution-in-java-findany-randomly-fails
        return paths.parallelStream().flatMap(path -> path.getEdgeList().parallelStream()
                .map(edge -> {
                    CFGVertex source = edge.getSource();
                    CFGVertex target = edge.getTarget();

                    LOGGER.debug("Inspecting source vertex: " + source);
                    LOGGER.debug("Inspecting target vertex: " + target);


                    if (source.containsInstruction(method, instructionIndex)) {
                        return source;
                    } else if (target.containsInstruction(method, instructionIndex)) {
                        return target;
                    } else {
                        return null;
                    }
                }).filter(Objects::nonNull)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Given trace refers to no vertex in graph!"));
    }

    /**
     * Performs a depth first search for looking up the vertex.
     *
     * @param method The method describing the vertex.
     * @param instructionIndex The instruction index of the vertex (the wrapped instruction).
     * @param entry The entry vertex of the method (limits the search).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     *         a {@link IllegalArgumentException} is thrown.
     */
    @SuppressWarnings("unused")
    private CFGVertex lookUpVertexDFS(String method, int instructionIndex, CFGVertex entry) {

        DepthFirstIterator<CFGVertex, CFGEdge> dfs = new DepthFirstIterator<>(graph, entry);

        while (dfs.hasNext()) {
            CFGVertex vertex = dfs.next();
            if (vertex.containsInstruction(method, instructionIndex)) {
                return vertex;
            }
        }

        throw new IllegalArgumentException("Given trace refers to no vertex in graph!");
    }

    /**
     * Performs a breadth first search for looking up the vertex.
     *
     * @param method The method describing the vertex.
     * @param instructionIndex The instruction index of the vertex (the wrapped instruction).
     * @param entry The entry vertex of the method (limits the search).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     *         a {@link IllegalArgumentException} is thrown.
     */
    private CFGVertex lookUpVertex(String method, int instructionIndex, CFGVertex entry) {

        BreadthFirstIterator<CFGVertex, CFGEdge> bfs = new BreadthFirstIterator<>(graph, entry);

        while (bfs.hasNext()) {
            CFGVertex vertex = bfs.next();
            if (vertex.containsInstruction(method, instructionIndex)) {
                return vertex;
            }
        }

        throw new IllegalArgumentException("Given trace refers to no vertex in graph!");
    }

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    // TODO: check if deep copy of vertices and edges is necessary
    @Override
    @SuppressWarnings("unused")
    public ModularCDG copy() {
        ModularCDG clone = new ModularCDG(getMethodName());

        Graph<CFGVertex, CFGEdge> graphClone = GraphTypeBuilder
                .<CFGVertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                .edgeClass(CFGEdge.class).buildGraph();

        Set<CFGVertex> vertices = graph.vertexSet();
        Set<CFGEdge> edges = graph.edgeSet();

        Cloner cloner = new Cloner();

        for (CFGVertex vertex : vertices) {
            graphClone.addVertex(cloner.deepClone(vertex));
        }

        for (CFGEdge edge : edges) {
            CFGVertex src = cloner.deepClone(edge.getSource());
            CFGVertex dest = cloner.deepClone(edge.getTarget());
            graphClone.addEdge(src, dest);
        }

        clone.graph = graphClone;
        return clone;
    }
}
