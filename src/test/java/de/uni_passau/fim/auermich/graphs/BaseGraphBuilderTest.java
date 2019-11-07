package de.uni_passau.fim.auermich.graphs;

import de.uni_passau.fim.auermich.graphs.cfg.BaseCFG;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
                .withName("Lcom/android/calendar/icalendar/IcalendarUtils;->" +
                        "getStringArrayFromFile(Landroid/content/Context;Landroid/net/Uri;)Ljava/util/ArrayList;")
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
                .withName("Lcom/android/calendar/icalendar/IcalendarUtils;->" +
                        "getStringArrayFromFile(Landroid/content/Context;Landroid/net/Uri;)Ljava/util/ArrayList;")
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
}
