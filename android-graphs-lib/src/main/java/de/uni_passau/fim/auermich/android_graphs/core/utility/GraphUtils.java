package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraphBuilder;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.calltree.CallTree;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.CDG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.ModularCDG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Enables the construction of an intra or inter CFG. This class is nothing else
 * than a factory that should be used by MATE.
 */
public class GraphUtils {

    private static final Logger LOGGER = LogManager.getLogger(GraphUtils.class);

    private GraphUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Convenient function to construct an intraCFG. Should be used
     * for the construction requested by mate server.
     *
     * @param apkPath The path to the APK file.
     * @param method The FQN name of the method.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @return Returns an intraCFG for the specified method.
     */
    public static BaseCFG constructIntraCFG(final File apkPath, final String method, final boolean useBasicBlocks) {

        LOGGER.info("APK: " + apkPath);
        LOGGER.info("Constructing INTRA-CFG for method: " + method);

        MultiDexContainer<? extends DexBackedDexFile> apk = null;

        try {
            apk = DexFileFactory.loadDexContainer(apkPath, Utility.API_OPCODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DexFile> dexFiles = new ArrayList<>();
        List<String> dexEntries = new ArrayList<>();

        try {
            dexEntries = apk.getDexEntryNames();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String dexEntry : dexEntries) {
            try {
                dexFiles.add(apk.getEntry(dexEntry).getDexFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        // check that specified target method is part of some class
        Optional<Tuple<DexFile, Method>> targetDexFileAndMethodTuple
                = MethodUtils.containsTargetMethod(dexFiles, method);

        if (targetDexFileAndMethodTuple.isEmpty()) {
            throw new IllegalArgumentException("Target method " + method + " not found in dex files!");
        }

        final DexFile targetDexFile = targetDexFileAndMethodTuple.get().getX();
        final Method targetMethod = targetDexFileAndMethodTuple.get().getY();

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTRACFG, targetDexFile, targetMethod)
                .withName(method);

        if (useBasicBlocks) {
            builder = builder.withBasicBlocks();
        }

        BaseGraph baseGraph = builder.build();
        return (BaseCFG) baseGraph;
    }

    /**
     * Convenient function to construct an interCFG. Should be used for the construction requested by mate server.
     *
     * @param apkPath The path to the APK file.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @param excludeARTClasses Whether to exclude ART classes or not.
     * @param onlyResolveAUTClasses Whether only AUT classes should be resolved.
     * @return Returns an interCFG.
     */
    public static BaseCFG constructInterCFG(final File apkPath, final boolean useBasicBlocks,
                                            final boolean excludeARTClasses, final boolean onlyResolveAUTClasses) {

        LOGGER.info("Constructing INTER CFG for APK: " + apkPath);

        long start = System.currentTimeMillis();

        MultiDexContainer<? extends DexBackedDexFile> apk = null;

        try {
            apk = DexFileFactory.loadDexContainer(apkPath, Utility.API_OPCODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DexFile> dexFiles = new ArrayList<>();
        List<String> dexEntries = new ArrayList<>();

        try {
            dexEntries = apk.getDexEntryNames();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String dexEntry : dexEntries) {
            try {
                dexFiles.add(apk.getEntry(dexEntry).getDexFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTERCFG, dexFiles)
                .withName("global")
                .withAPKFile(apkPath);

        if (useBasicBlocks) {
            builder = builder.withBasicBlocks();
        }

        if (excludeARTClasses) {
            builder = builder.withExcludeARTClasses();
        }

        if (onlyResolveAUTClasses) {
            builder = builder.withResolveOnlyAUTClasses();
        }

        BaseGraph baseGraph = builder.build();

        long end = System.currentTimeMillis();
        LOGGER.info("Graph construction took: " + ((end - start) / 1000) + " seconds");

        return (BaseCFG) baseGraph;
    }

    /**
     * Convenient function to construct an interCDG. Should be used for the construction requested by mate server.
     *
     * @param apkPath The path to the APK file.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @param excludeARTClasses Whether to exclude ART classes or not.
     * @param onlyResolveAUTClasses Whether only AUT classes should be resolved.
     * @return Returns an interCDG.
     */
    public static CDG constructInterCDG(final File apkPath, final boolean useBasicBlocks,
                                        final boolean excludeARTClasses, final boolean onlyResolveAUTClasses) {

        LOGGER.info("Constructing INTER CDG for APK: " + apkPath);
        long start = System.currentTimeMillis();

        InterCFG interCFG = (InterCFG) constructInterCFG(apkPath, useBasicBlocks, excludeARTClasses, onlyResolveAUTClasses);
        final CDG interCDG = new CDG(interCFG);

        long end = System.currentTimeMillis();
        LOGGER.info("Graph construction took: " + ((end - start) / 1000) + " seconds");
        return interCDG;
    }

    /**
     * Convenient function to construct a modular interCDG. Should be used for the construction requested by mate server.
     *
     * @param apkPath The path to the APK file.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @param excludeARTClasses Whether to exclude ART classes or not.
     * @param onlyResolveAUTClasses Whether only AUT classes should be resolved.
     * @return Returns a modular interCDG.
     */
    public static ModularCDG constructModularCDG(final File apkPath, final boolean useBasicBlocks,
                                                 final boolean excludeARTClasses, final boolean onlyResolveAUTClasses) {

        LOGGER.info("Constructing Modular Inter CDG for APK: " + apkPath);

        long start = System.currentTimeMillis();

        MultiDexContainer<? extends DexBackedDexFile> apk = null;

        try {
            apk = DexFileFactory.loadDexContainer(apkPath, Utility.API_OPCODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DexFile> dexFiles = new ArrayList<>();
        List<String> dexEntries = new ArrayList<>();

        try {
            dexEntries = apk.getDexEntryNames();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String dexEntry : dexEntries) {
            try {
                dexFiles.add(apk.getEntry(dexEntry).getDexFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.MODULARCDG, dexFiles)
                .withName("global")
                .withAPKFile(apkPath);

        if (useBasicBlocks) {
            builder = builder.withBasicBlocks();
        }

        if (excludeARTClasses) {
            builder = builder.withExcludeARTClasses();
        }

        if (onlyResolveAUTClasses) {
            builder = builder.withResolveOnlyAUTClasses();
        }

        BaseGraph baseGraph = builder.build();

        long end = System.currentTimeMillis();
        LOGGER.info("Graph construction took: " + ((end - start) / 1000) + " seconds");

        return (ModularCDG) baseGraph;
    }


    /**
     * Convenient function to construct a call tree. Should be used for the construction requested by mate server.
     *
     * @param apkPath The path to the APK file.
     * @param excludeARTClasses Whether to exclude ART classes or not.
     * @param onlyResolveAUTClasses Whether only AUT classes should be resolved.
     * @return Returns a call tree.
     */
    public static CallTree constructCallTree(final File apkPath, final boolean excludeARTClasses,
                                             final boolean onlyResolveAUTClasses) {

        LOGGER.info("Constructing CALL TREE for APK: " + apkPath);

        long start = System.currentTimeMillis();

        MultiDexContainer<? extends DexBackedDexFile> apk = null;

        try {
            apk = DexFileFactory.loadDexContainer(apkPath, Utility.API_OPCODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DexFile> dexFiles = new ArrayList<>();
        List<String> dexEntries = new ArrayList<>();

        try {
            dexEntries = apk.getDexEntryNames();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String dexEntry : dexEntries) {
            try {
                dexFiles.add(apk.getEntry(dexEntry).getDexFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.CALLTREE, dexFiles)
                .withName("global")
                .withBasicBlocks()
                .withAPKFile(apkPath);

        if (excludeARTClasses) {
            builder = builder.withExcludeARTClasses();
        }

        if (onlyResolveAUTClasses) {
            builder = builder.withResolveOnlyAUTClasses();
        }

        BaseGraph baseGraph = builder.build();

        long end = System.currentTimeMillis();
        LOGGER.info("Graph construction took: " + ((end - start) / 1000) + " seconds");

        return (CallTree) baseGraph;
    }
}
