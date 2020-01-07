package de.uni_passau.fim.auermich.graphs;

import de.uni_passau.fim.auermich.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.graphs.cfg.InterProceduralCFG;
import de.uni_passau.fim.auermich.statement.BasicStatement;
import de.uni_passau.fim.auermich.statement.BlockStatement;
import de.uni_passau.fim.auermich.statement.ExitStatement;
import de.uni_passau.fim.auermich.statement.Statement;
import de.uni_passau.fim.auermich.utility.Utility;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Format;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.uni_passau.fim.auermich.Main.API_OPCODE;

public class BaseGraphBuilderTest {

    @Test
    public void checkReachAbility() throws IOException {

        // File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk");
        File apkFile = new File("C:\\Users\\Michael\\git\\mate-commander\\ws.xsoh.etar_17.apk");
        // File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\BMI-debug.apk");

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
                .withExcludeARTClasses()
                .withAPKFile(apkFile)
                .build();

        BaseCFG interCFG = (BaseCFG) baseGraph;

        System.out.println("Total number of Branches: " + interCFG.getBranches().size());

        /*
        Vertex targetVertex = interCFG.getVertices().stream().filter(v -> v.isEntryVertex()
                && v.getMethod().equals("Landroid/support/v7/widget/ToolbarWidgetWrapper" +
                ";->setMenu(Landroid/view/Menu;Landroid/support/v7/view/menu/MenuPresenter$Callback;)V"))
                .findFirst().get();
        */

        Vertex targetVertex = interCFG.getVertices().stream().filter(v -> v.isEntryVertex()
            && v.getMethod().equals("Lcom/android/calendar/DayFragment;->onAttach(Landroid/content/Context;)V")).findFirst().get();

        interCFG.getIncomingEdges(targetVertex).forEach(edge -> System.out.println("Predecessor: " + edge.getSource()));

        int distance = interCFG.getShortestDistance(interCFG.getEntry(), targetVertex);
        boolean reachable = distance != -1;
        System.out.println("Target Vertex reachable " + reachable);
        System.out.println("Distance: " + distance);
    }

    @Test
    public void computeBranchDistanceLinux() throws IOException {

        // File apkFile = new File("/home/auermich/smali/com.zola.bmi_400.apk");
        File apkFile = new File("/home/auermich/smali/ws.xsoh.etar_17.apk");
        // File apkFile = new File("/home/auermich/tools/mate-commander/BMI-debug.apk");
        // File apkFile = new File("C:\\Users\\Michael\\git\\mate-commander\\ws.xsoh.etar_17.apk");

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
                .withExcludeARTClasses()
                .build();

        BaseCFG interCFG = (BaseCFG) baseGraph;
        // interCFG.drawGraph();

        System.out.println("Total number of Branches: " + interCFG.getBranches().size());

        Vertex targetVertex = interCFG.getVertices().stream().filter(v ->
                v.containsInstruction("Lcom/android/calendar/DayView$TodayAnimatorListener;" +
                        "->onAnimationEnd(Landroid/animation/Animator;)V", 20))
                .findFirst().get();

        System.out.println("Selected Target Vertex: " + targetVertex);

        String tracesDir = "/home/auermich/tools/mate-commander/";
        // String tracesDir = "C:\\Users\\Michael\\git\\mate-commander\\";
        File traces = new File(tracesDir, "traces.txt");

        List<String> executionPath = new ArrayList<>();

        System.out.println("Reading traces from file ...");

        try (Stream<String> stream = Files.lines(traces.toPath(), StandardCharsets.UTF_8)) {
            executionPath = stream.collect(Collectors.toList());
        }
        catch (IOException e) {
            System.out.println("Reading traces.txt failed!");
            e.printStackTrace();
            return;
        }

        System.out.println("Number of visited vertices: " + executionPath.size());

        // we need to mark vertices we visit
        Set<Vertex> visitedVertices = Collections.newSetFromMap(new ConcurrentHashMap<Vertex, Boolean>());

        Map<String, Vertex> vertexMap = new ConcurrentHashMap<>();

        System.out.println("Constructing vertexMap ...");

        interCFG.getVertices().parallelStream().forEach(vertex -> {
            if (vertex.isEntryVertex()) {
                vertexMap.put(vertex.getMethod() + "->entry", vertex);
            } else if (vertex.isExitVertex()) {
                vertexMap.put(vertex.getMethod() + "->exit", vertex);
            } else if (vertex.isBranchVertex()) {
                // get instruction id of first stmt
                Statement statement = vertex.getStatement();

                if (statement.getType() == Statement.StatementType.BASIC_STATEMENT) {
                    BasicStatement basicStmt = (BasicStatement) statement;
                    vertexMap.put(vertex.getMethod() + "->" + basicStmt.getInstructionIndex(), vertex);
                } else {
                    // should be a block stmt, other stmt types shouldn't be branch targets
                    BlockStatement blockStmt = (BlockStatement) statement;
                    // a branch target can only be the first instruction in a basic block since it has to be a leader
                    BasicStatement basicStmt = (BasicStatement) blockStmt.getFirstStatement();
                    // identify a basic block by its first instruction (the branch target)
                    vertexMap.put(vertex.getMethod() + "->" + basicStmt.getInstructionIndex(), vertex);
                }
            }
        });

        Set<Vertex> entryVertices = Collections.newSetFromMap(new ConcurrentHashMap<Vertex, Boolean>());

        System.out.println("Mapping traces to vertices...");

        // map trace to vertex
        executionPath.parallelStream().forEach(pathNode -> {

            int index = pathNode.lastIndexOf("->");
            String type = pathNode.substring(index+2);

            Vertex visitedVertex = vertexMap.get(pathNode);

            if (visitedVertex == null) {
                System.out.println("Couldn't derive vertex for trace entry: " + pathNode);
            }  else {

                visitedVertices.add(visitedVertex);

                if (visitedVertex.isEntryVertex()) {
                    entryVertices.add(visitedVertex);
                }

            }
        });

        System.out.println("Marking intermediate path nodes now...");

        // mark the intermediate path nodes that are between branches we visited
        entryVertices.forEach(entry -> {
            Vertex exit = new Vertex(new ExitStatement(entry.getMethod()));
            if (visitedVertices.contains(entry) && visitedVertices.contains(exit)) {
                markIntermediatePathVertices(entry, exit, visitedVertices, interCFG);
            }
        });

        // the minimal distance between a execution path and a chosen target vertex
        AtomicInteger min = new AtomicInteger(Integer.MAX_VALUE);

        // cache already computed branch distances
        Map<Vertex, Double> branchDistances = new ConcurrentHashMap<>();

        // use bidirectional dijkstra
        ShortestPathAlgorithm<Vertex, Edge> bfs = interCFG.initBidirectionalDijkstraAlgorithm();

        visitedVertices.parallelStream().forEach(visitedVertex -> {

            System.out.println("Visited Vertex: " + visitedVertex + " " + visitedVertex.getMethod());

            int distance = -1;

            if (branchDistances.containsKey(visitedVertex)) {
                distance = branchDistances.get(visitedVertex).intValue();
            } else {
                GraphPath<Vertex, Edge> path = bfs.getPath(visitedVertex, targetVertex);
                if (path != null) {
                    distance = path.getLength();
                    // update branch distance map
                    branchDistances.put(visitedVertex, Double.valueOf(distance));
                } else {
                    // update branch distance map
                    branchDistances.put(visitedVertex, Double.valueOf(-1));
                }
            }

            // int distance = branchDistances.get(visitedVertex).intValue();
            if (distance < min.get() && distance != -1) {
                // found shorter path
                min.set(distance);
                System.out.println("Current min distance: " + distance);
            }
        });


        // we maximise branch distance in contradiction to its meaning, that means a branch distance of 1 is the best
        String branchDistance = null;

        // we need to normalise approach level / branch distance to the range [0,1] where 1 is best
        if (min.get() == Integer.MAX_VALUE) {
            // branch not reachable by execution path
            branchDistance = String.valueOf(0);
        } else {
            branchDistance = String.valueOf(1 - ((double) min.get() / (min.get() + 1)));
        }

        System.out.println("Branch Distance: " + branchDistance);
    }

    private boolean isInvokeVertex(Vertex vertex) {

        if (vertex.isEntryVertex() || vertex.isExitVertex()
                || vertex.isReturnVertex()) {
            return false;
        }

        Statement statement = vertex.getStatement();

        if (statement.getType() == Statement.StatementType.BASIC_STATEMENT) {
            System.out.println("Basic statement not yet supported!");
            // TODO: check if is ART method invocation
            return false;
        } else if (statement.getType() == Statement.StatementType.BLOCK_STATEMENT) {

            BlockStatement blockStatement = (BlockStatement) statement;

            // only the last statement is of interest
            Statement stmt = blockStatement.getLastStatement();

            // there could be potentially isolated return vertices
            if (stmt.getType() == Statement.StatementType.BASIC_STATEMENT) {

                BasicStatement basicStatement = (BasicStatement) stmt;
                Instruction instruction = basicStatement.getInstruction().getInstruction();

                // check if we have an invoke stmt
                if (instruction.getOpcode().format == Format.Format35c
                    || instruction.getOpcode().format == Format.Format3rc
                        && instruction.getOpcode() != Opcode.FILLED_NEW_ARRAY_RANGE
                        && instruction.getOpcode() != Opcode.FILLED_NEW_ARRAY) {
                    System.out.println("Instruction: " + basicStatement.getInstructionIndex() + ":" + instruction.getOpcode());
                    return true;
                }
            }
        }
        return false;
    }

    private void markIntermediatePathVertices(Vertex entry, Vertex exit, Set<Vertex> visitedVertices,
                                              BaseCFG interCFG) {

        Queue<Vertex> queue = new LinkedList<>();
        queue.offer(entry);
        String method = entry.getMethod();

        while (!queue.isEmpty()) {

            Vertex currentVertex = queue.poll();
            visitedVertices.add(currentVertex);

            // check if we gonna leave method, then enqueue return vertex
            if (isInvokeVertex(currentVertex)) {

                System.out.println("Invoke Vertex: " + currentVertex);

                Vertex successor = interCFG.getOutgoingEdges(currentVertex)
                        .stream()
                        .map(Edge::getTarget).findFirst().get();

                System.out.println("Size of successors: " + interCFG.getOutgoingEdges(currentVertex).size());

                // should be the entry vertex of invoked method
                if (successor.isEntryVertex()) {
                    Vertex exitVertex = new Vertex(new ExitStatement(successor.getMethod()));
                    queue.offer(getReturnVertex(currentVertex, exitVertex, visitedVertices, interCFG));
                } else {
                    System.out.println("Might be an invoke stmt that calls an ART method");
                }
            }

            List<Vertex> successors = interCFG.getOutgoingEdges(currentVertex)
                    .stream()
                    .map(Edge::getTarget)
                    .collect(Collectors.toList());

            if (successors.size() == 1) {
                // single path
                Vertex successor = successors.get(0);
                if (method.equals(successor.getMethod())) {
                    // ensure that we stay within the same method
                    queue.offer(successor);
                }
            } else {
                // there are multiple successors, check which path(s) we visited
                for (Vertex successor : successors) {
                    if (method.equals(successor.getMethod())
                            && visitedVertices.contains(successor)) {
                        queue.offer(successor);
                    }
                }
            }
        }
    }

    private void markIntermediatePathVertices2(Vertex entry, Vertex exit, Set<Vertex> visitedVertices,
                                              BaseCFG interCFG) {

        System.out.println("Entry vertex: " + entry);

        Vertex currentVertex = entry;
        Vertex previous = currentVertex;

        while (!currentVertex.equals(exit)) {

            // System.out.println("Distance to exit: " + interCFG.getShortestDistance(currentVertex, exit));

            Set<Edge> outgoingEdges = interCFG.getOutgoingEdges(currentVertex);

            if (outgoingEdges.size() == 1) {
                // single successors -> follow the path
                previous = currentVertex;
                currentVertex = outgoingEdges.stream().findFirst().get().getTarget();
                System.out.println("Updated Vertex: " + currentVertex);
            } else {


                outgoingEdges.parallelStream().forEach(e -> {
                    Vertex targetVertex = e.getTarget();
                    if (visitedVertices.contains(targetVertex)) {
                        markIntermediatePathVertices(targetVertex, exit, visitedVertices, interCFG);
                    }
                });

                break;
            }

            if (!entry.getMethod().equals(currentVertex.getMethod())) {
                // we entered a new method

                System.out.println("Entered method: " + currentVertex.getMethod());

                // search for return vertex, which must be a successor of the method's exit vertex
                Vertex exitVertex = new Vertex(new ExitStatement(currentVertex.getMethod()));
                currentVertex = getReturnVertex(previous, exitVertex, visitedVertices, interCFG);
                System.out.println("Updated Vertex: " + currentVertex);
            }

            visitedVertices.add(currentVertex);
        }
    }

    /**
     * Searches for the return vertex belonging to the invocation stmt included in the
     * given vertex.
     *
     * @param vertex The vertex including the invocation stmt.
     * @param exit The exit vertex of the invoked method.
     * @param visitedVertices The set of currently visited vertices. Needs to be updated
     *                        as a side effect in case no basic blocks are used.
     * @return Returns the corresponding return vertex. In case no basic blocks are used,
     *          the appropriate successor vertex is returned.
     * @throws IllegalStateException If the return vertex couldn't be found.
     */
    private Vertex getReturnVertex(Vertex vertex, Vertex exit, Set<Vertex> visitedVertices,
                                   BaseCFG interCFG) {

        System.out.println("Get return vertex for: " + vertex + ":" + vertex.getMethod());

        // the vertex contains an invoke instruction as last statement
        Statement stmt = vertex.getStatement();

        // we need to know instruction index of the invoke stmt
        int index = -1;

        if (stmt.getType() == Statement.StatementType.BASIC_STATEMENT) {
            BasicStatement basicStatement = (BasicStatement) stmt;
            index = basicStatement.getInstructionIndex();
        } else if (stmt.getType() == Statement.StatementType.BLOCK_STATEMENT) {
            BlockStatement blockStatement = (BlockStatement) stmt;
            BasicStatement basicStatement = (BasicStatement) blockStatement.getLastStatement();
            index = basicStatement.getInstructionIndex();
        }

        System.out.println("Found Index: " + index);

        System.out.println("Outgoing Edge Targets: "
                + interCFG.getOutgoingEdges(exit).stream().map(Edge::getTarget).collect(Collectors.toList()));

        // find the return vertices that return to the original method
        List<Vertex> returnVertices = interCFG.getOutgoingEdges(exit).stream().filter(edge ->
                edge.getTarget().getMethod().equals(vertex.getMethod())).map(Edge::getTarget)
                .collect(Collectors.toList());

        System.out.println("Number of return vertices: " + returnVertices.size());

        // the return vertex that has index + 1 as next instruction index is the right one
        for (Vertex returnVertex : returnVertices) {

            System.out.println("Return Vertex: " + returnVertex);

            Statement returnStmt = returnVertex.getStatement();

            // we need to know instruction index of the invoke stmt
            int nextIndex = -1;

            if (returnStmt.getType() == Statement.StatementType.BASIC_STATEMENT) {

                // we need to inspect the successor vertices since the return stmt is shared
                List<Vertex> successors = interCFG.getOutgoingEdges(returnVertex).stream().
                        map(Edge::getTarget).collect(Collectors.toList());

                for (Vertex successor : successors) {
                    // each successor is a basic stmt
                    BasicStatement basicStatement = (BasicStatement) successor.getStatement();
                    nextIndex = basicStatement.getInstructionIndex();

                    if (nextIndex == index + 1) {

                        // we can't return the return stmt, but we need to mark it as visited as a side effect
                        visitedVertices.add(returnVertex);

                        // here we return the successor of the return vertex directly
                        // otherwise we don't know again which is the actual successor
                        return successor;
                    }
                }
            } else if (returnStmt.getType() == Statement.StatementType.BLOCK_STATEMENT) {

                BlockStatement blockStatement = (BlockStatement) returnStmt;

                System.out.println("Is ReturnStmt: " + blockStatement.getFirstStatement().getType());
                System.out.println("Block Stmt: " + blockStatement);

                if (blockStatement.getStatements().size() == 1) {
                    // there can be isolated virtual return stmts
                    // (in case the invoke is a branch target and a predecessor of another leader instruction)

                    System.out.println("Outgoing Edges: " + interCFG.getOutgoingEdges(returnVertex));

                    // we need to inspect the successors in this case
                    List<Vertex> successors = interCFG.getOutgoingEdges(returnVertex).stream().
                            map(Edge::getTarget).collect(Collectors.toList());

                    System.out.println("Successors: " + successors);

                    for (Vertex successor : successors) {

                        BlockStatement blockStmt = (BlockStatement) successor.getStatement();

                        // the stmt after the return stmt is the next actual instruction stmt
                        BasicStatement basicStatement = (BasicStatement) blockStmt.getFirstStatement();
                        nextIndex = basicStatement.getInstructionIndex();

                        if (nextIndex == index + 1) {

                            // we can't return the return stmt, but we need to mark it as visited as a side effect
                            visitedVertices.add(returnVertex);

                            // here we return the successor of the return vertex directly
                            // otherwise we don't know again which is the actual successor
                            return successor;
                        }
                    }
                } else {
                    // the stmt after the return stmt is the next actual instruction stmt
                    BasicStatement basicStatement = (BasicStatement) blockStatement.getStatements().get(1);
                    nextIndex = basicStatement.getInstructionIndex();
                }

                if (nextIndex == index + 1) {
                    return returnVertex;
                }
            }
        }
        throw new IllegalStateException("Couldn't find corresponding return vertex for " + vertex);
    }



    @Test
    public void checkReachAbilityLinux() throws IOException {

        // File apkFile = new File("/home/auermich/smali/com.zola.bmi_400.apk");
        File apkFile = new File("/home/auermich/smali/ws.xsoh.etar_17.apk");
        // File apkFile = new File("/home/auermich/smali/BMI-debug.apk");

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
                .withExcludeARTClasses()
                .build();

        BaseCFG interCFG = (BaseCFG) baseGraph;

        System.out.println("Total number of Branches: " + interCFG.getBranches().size());

        /*
        Vertex targetVertex = interCFG.getVertices().stream().filter(v -> v.isEntryVertex()
                && v.getMethod().equals("Landroid/support/v7/widget/ToolbarWidgetWrapper" +
                ";->setMenu(Landroid/view/Menu;Landroid/support/v7/view/menu/MenuPresenter$Callback;)V"))
                .findFirst().get();
        */

        /*
        Vertex targetVertex = interCFG.getVertices().stream().filter(v ->
                !v.isEntryVertex() &&
                !v.isExitVertex() &&
                v.getMethod().equals("Lcom/android/calendar/CalendarEventModel;->equals(Ljava/lang/Object;)Z") &&
                v.containsInstruction("Lcom/android/calendar/CalendarEventModel;->equals(Ljava/lang/Object;)Z", 76))
                .findFirst().get();
        */

        Vertex targetVertex = interCFG.getVertices().stream().filter(v ->
                v.isExitVertex() &&
                v.getMethod().equals("Landroid/app/TimePickerDialog;->" +
                        "<init>(Landroid/content/Context;Landroid/app/TimePickerDialog$OnTimeSetListener;IIZ)V")).findFirst().get();

        interCFG.getIncomingEdges(targetVertex).forEach(edge -> System.out.println("Predecessor: " + edge.getSource()));

        int distance = interCFG.getShortestDistance(interCFG.getEntry(), targetVertex);
        boolean reachable = distance != -1;
        System.out.println("Target Vertex reachable " + reachable);
        System.out.println("Distance: " + distance);
    }

    @Test
    public void countUnreachableVertices() throws IOException {

        File apkFile = new File("/home/auermich/smali/com.zola.bmi_400.apk");
        // File apkFile = new File("/home/auermich/smali/ws.xsoh.etar_17.apk");

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
                // .withExcludeARTClasses()
                .build();

        // baseGraph.drawGraph();

        BaseCFG interCFG = (BaseCFG) baseGraph;

        System.out.println("Total number of Vertices: " + interCFG.getVertices().size());
        System.out.println("Total number of Branches: " + interCFG.getBranches().size());

        int unreachableVertices = 0;
        int unreachableARTVertices = 0;
        Pattern exclusionPattern = Utility.readExcludePatterns();

        for (Vertex vertex : interCFG.getVertices()) {

            if (vertex.equals(interCFG.getEntry()) || vertex.equals(interCFG.getExit())) {
                continue;
            }

            if (interCFG.getShortestDistance(interCFG.getEntry(), vertex) == -1) {
                unreachableVertices++;

                String className = Utility.dottedClassName(Utility.getClassName(vertex.getMethod()));
                if (exclusionPattern != null && exclusionPattern.matcher(className).matches()) {
                    unreachableARTVertices++;
                } else {
                    System.out.println("Unreachable Vertex: " + vertex.getMethod() + " -> " + vertex);
                }
            }
        }

        System.out.println("Total Number of unreachable Vertices: " + unreachableVertices);
        System.out.println("Total Number of unreachable ART Vertices: " + unreachableARTVertices);
    }

    @Test
    public void countUnreachableVerticesWindows() throws IOException {

        // File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk");
        File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\BMI-debug.apk");

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
                .withExcludeARTClasses()
                .build();

        baseGraph.drawGraph();

        BaseCFG interCFG = (BaseCFG) baseGraph;

        System.out.println("Total number of Vertices: " + interCFG.getVertices().size());
        System.out.println("Total number of Branches: " + interCFG.getBranches().size());

        int unreachableVertices = 0;
        int unreachableARTVertices = 0;
        Pattern exclusionPattern = Utility.readExcludePatterns();

        for (Vertex vertex : interCFG.getVertices()) {

            if (vertex.equals(interCFG.getEntry()) || vertex.equals(interCFG.getExit())) {
                continue;
            }

            if (interCFG.getShortestDistance(interCFG.getEntry(), vertex) == -1) {
                unreachableVertices++;

                String className = Utility.dottedClassName(Utility.getClassName(vertex.getMethod()));
                if (exclusionPattern != null && exclusionPattern.matcher(className).matches()) {
                    unreachableARTVertices++;
                } else {
                    System.out.println("Unreachable Vertex: " + vertex.getMethod() + " -> " + vertex);
                }
            }
        }

        System.out.println("Total Number of unreachable Vertices: " + unreachableVertices);
        System.out.println("Total Number of unreachable ART Vertices: " + unreachableARTVertices);
    }

    @Test
    public void constructIntraCFG() throws IOException {

        MultiDexContainer<? extends DexBackedDexFile> apk
                = DexFileFactory.loadDexContainer(
                        new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk"),
                API_OPCODE);

        List<DexFile> dexFiles = new ArrayList<>();

        apk.getDexEntryNames().forEach(dexFile -> {
            try {
                dexFiles.add(apk.getEntry(dexFile).getDexFile());
            } catch (IOException e) {
                e.printStackTrace();
                throw new IllegalStateException("Couldn't load dex file!");
            }
        });

        BaseGraph baseGraph = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                .withName("Lcom/android/calendar/DynamicTheme;->getSuffix(Ljava/lang/String;)Ljava/lang/String;")
                .build();

        baseGraph.drawGraph();
    }

    @Test
    public void constructIntraCFGLinux() throws IOException {

        MultiDexContainer<? extends DexBackedDexFile> apk
                = DexFileFactory.loadDexContainer(new File("/home/auermich/smali/ws.xsoh.etar_17.apk"), API_OPCODE);

        List<DexFile> dexFiles = new ArrayList<>();

        apk.getDexEntryNames().forEach(dexFile -> {
            try {
                dexFiles.add(apk.getEntry(dexFile).getDexFile());
            } catch (IOException e) {
                e.printStackTrace();
                throw new IllegalStateException("Couldn't load dex file!");
            }
        });

        BaseGraph baseGraph = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                .withName("Lcom/android/calendar/EventLoader$LoaderThread;->run()V")
                .build();

        baseGraph.drawGraph();
    }

    @Test
    public void constructIntraCFGWithBasicBlocks() throws IOException {

        MultiDexContainer<? extends DexBackedDexFile> apk
                = DexFileFactory.loadDexContainer(
                        new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk"),
                API_OPCODE);

        List<DexFile> dexFiles = new ArrayList<>();

        apk.getDexEntryNames().forEach(dexFile -> {
            try {
                dexFiles.add(apk.getEntry(dexFile).getDexFile());
            } catch (IOException e) {
                e.printStackTrace();
                throw new IllegalStateException("Couldn't load dex file!");
            }
        });

        BaseGraph baseGraph = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                .withName("Lcom/android/calendar/DynamicTheme;->getSuffix(Ljava/lang/String;)Ljava/lang/String;")
                .withBasicBlocks()
                .build();

        baseGraph.drawGraph();
    }

    @Test
    public void constructIntraCFGWithBasicBlocksLinux() throws IOException {

        // File apkFile = new File("/home/auermich/smali/com.zola.bmi_400.apk");
        File apkFile = new File("/home/auermich/tools/mate-commander/ws.xsoh.etar_17.apk");

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

        BaseGraph baseGraph = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                .withName("Lcom/android/datetimepicker/date/DayPickerView;->goTo(Lcom/android/datetimepicker/date/MonthAdapter$CalendarDay;ZZZ)Z")
                 // .withName("Lcom/android/calendar/EventLoader$LoaderThread;->run()V")
                .withBasicBlocks()
                .build();

        baseGraph.drawGraph();
    }

    @Test
    public void constructInterCFGLinux() throws IOException {

        File apkFile = new File("/home/auermich/smali/com.zola.bmi_400.apk");

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
                .withAPKFile(apkFile)
                .withExcludeARTClasses()
                .build();

        baseGraph.drawGraph();
    }

    @Test
    public void constructInterCFG() throws IOException {

        // File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk");
        File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\BMI-debug.apk");


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
                .withAPKFile(apkFile)
                .build();

        BaseCFG baseCFG = (BaseCFG) baseGraph;

        System.out.println("Number of Vertices: " + baseCFG.getVertices().size());
        System.out.println("Number of Branches: " + baseCFG.getBranches().size());
        // baseGraph.drawGraph();
    }

    @Test
    public void constructInterCFGExcludeARTClasses() throws IOException {

        // File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk");
        File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\BMI-debug.apk");


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
                .withAPKFile(apkFile)
                .withExcludeARTClasses()
                .build();

        BaseCFG baseCFG = (BaseCFG) baseGraph;

        System.out.println("Number of Vertices: " + baseCFG.getVertices().size());
        System.out.println("Number of Branches: " + baseCFG.getBranches().size());
        // baseGraph.drawGraph();
    }

    @Test
    public void constructInterCFGWithBasicBlocks() throws IOException {

        File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk");
        // File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\BMI-debug.apk");


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

        BaseCFG baseCFG = (BaseCFG) baseGraph;

        System.out.println("Number of Vertices: " + baseCFG.getVertices().size());
        System.out.println("Number of Branches: " + baseCFG.getBranches().size());

        if (baseCFG.getVertices().size() < 500) {
            baseGraph.drawGraph();
        }
    }

    @Test
    public void constructInterCFGWithBasicBlocksAndExcludeARTClasses() throws IOException {

        File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk");
        // File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\com.zola.bmi_400.apk");
        // File apkFile = new File("C:\\Users\\Michael\\Downloads\\com.zola.bmi\\BMI\\build\\outputs\\apk\\debug\\BMI-debug.apk");

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
                .withExcludeARTClasses()
                .build();

        BaseCFG baseCFG = (BaseCFG) baseGraph;

        System.out.println("Number of Vertices: " + baseCFG.getVertices().size());
        System.out.println("Number of Branches: " + baseCFG.getBranches().size());

        if (baseCFG.getVertices().size() < 500) {
            baseGraph.drawGraph();
        }
    }

    @Test
    public void constructInterCFGWithBasicBlocksLinux() throws IOException {

        File apkFile = new File("/home/auermich/smali/ws.xsoh.etar_17.apk");

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

        BaseCFG baseCFG = (BaseCFG) baseGraph;

        System.out.println("Number of Vertices: " + baseCFG.getVertices().size());
        System.out.println("Number of Branches: " + baseCFG.getBranches().size());

        // baseGraph.drawGraph();
    }

    @Test
    public void constructInterCFGWithBasicBlocksAndExcludeARTClassesLinux() throws IOException {

        // File apkFile = new File("/home/auermich/smali/ws.xsoh.etar_17.apk");
        // File apkFile = new File("/home/auermich/smali/com.zola.bmi_400.apk");
        File apkFile = new File("/home/auermich/smali/BMI-debug.apk");

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
                .withExcludeARTClasses()
                .build();

        BaseCFG baseCFG = (BaseCFG) baseGraph;

        System.out.println("Number of Vertices: " + baseCFG.getVertices().size());
        System.out.println("Number of Branches: " + baseCFG.getBranches().size());

        if (baseCFG.getVertices().size() < 800) {
            baseGraph.drawGraph();
        }
    }
}
