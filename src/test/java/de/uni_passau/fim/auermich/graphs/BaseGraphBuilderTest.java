package de.uni_passau.fim.auermich.graphs;

import de.uni_passau.fim.auermich.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.statement.BasicStatement;
import de.uni_passau.fim.auermich.statement.BlockStatement;
import de.uni_passau.fim.auermich.statement.Statement;
import de.uni_passau.fim.auermich.utility.Utility;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
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
                .build();

        BaseCFG interCFG = (BaseCFG) baseGraph;

        System.out.println("Total number of Branches: " + interCFG.getBranches().size());

        Vertex targetVertex = interCFG.getVertices().stream().filter(v -> v.isEntryVertex()
                && v.getMethod().equals("Landroid/support/v7/widget/ToolbarWidgetWrapper" +
                ";->setMenu(Landroid/view/Menu;Landroid/support/v7/view/menu/MenuPresenter$Callback;)V"))
                .findFirst().get();

        interCFG.getIncomingEdges(targetVertex).forEach(edge -> System.out.println("Predecessor: " + edge.getSource()));

        int distance = interCFG.getShortestDistance(interCFG.getEntry(), targetVertex);
        boolean reachable = distance != -1;
        System.out.println("Target Vertex reachable " + reachable);
    }

    @Test
    public void computeBranchDistanceLinux() throws IOException {

        // File apkFile = new File("/home/auermich/smali/com.zola.bmi_400.apk");
        // File apkFile = new File("/home/auermich/smali/ws.xsoh.etar_17.apk");
        File apkFile = new File("/home/auermich/tools/mate-commander/BMI-debug.apk");

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
        interCFG.drawGraph();

        System.out.println("Total number of Branches: " + interCFG.getBranches().size());


        Vertex targetVertex = interCFG.getVertices().stream().filter(v ->
                v.isEntryVertex() &&
                        v.getMethod().equals("Lcom/zola/bmi/BMIMain$PlaceholderFragment;->onActivityCreated(Landroid/os/Bundle;)V")).findFirst().get();


        String tracesDir = "/home/auermich/tools/mate-commander/";
        File traces = new File(tracesDir, "traces1.txt");

        List<String> executionPath = new ArrayList<>();

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

        // map trace to vertex
        executionPath.parallelStream().forEach(pathNode -> {

            int index = pathNode.lastIndexOf("->");
            String type = pathNode.substring(index+2);

            Vertex visitedVertex = vertexMap.get(pathNode);

            if (visitedVertex == null) {
                System.out.println("Couldn't derive vertex for trace entry: " + pathNode);
            }

            visitedVertices.add(visitedVertex);
        });

        // the minimal distance between a execution path and a chosen target vertex
        AtomicInteger min = new AtomicInteger(Integer.MAX_VALUE);

        // cache already computed branch distances
        Map<Vertex, Double> branchDistances = new ConcurrentHashMap<>();

        // use bidirectional dijkstra
        ShortestPathAlgorithm<Vertex, Edge> bfs = interCFG.initBFSAlgorithm();

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

        BaseGraph baseGraph = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                .withName("Landroid/support/v7/widget/ToolbarWidgetWrapper;->setMenu(Landroid/view/Menu;Landroid/support/v7/view/menu/MenuPresenter$Callback;)V")
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
        // File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\BMI-debug.apk");
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
