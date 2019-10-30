package de.uni_passau.fim.auermich.graphs;

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
    public void constructIntraCFG() throws IOException {

        MultiDexContainer<? extends DexBackedDexFile> apk
                = DexFileFactory.loadDexContainer(new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\ws.xsoh.etar_17.apk"), API_OPCODE);

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
                .withName("Lcom/android/calendar/AboutPreferences;->onCreate(Landroid/os/Bundle;)V")
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
                .withBasicBlocks()
                .build();

        baseGraph.drawGraph();
    }

    @Test
    public void constructInterCFG() throws IOException {

        File apkFile = new File("C:\\Users\\Michael\\Documents\\Work\\Android\\apks\\com.zola.bmi_400.apk");

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

        // baseGraph.drawGraph();
    }
}
