package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraphBuilder;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Enables the construction of an intra or inter CFG.
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
     * @param apkPath        The path to the APK file.
     * @param method         The FQN name of the method.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @return Returns an intraCFG for the specified method.
     */
    public static BaseCFG constructIntraCFG(final File apkPath, final String method, final boolean useBasicBlocks) {

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

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                .withName(method);

        if (useBasicBlocks) {
            builder = builder.withBasicBlocks();
        }

        BaseGraph baseGraph = builder.build();
        return (BaseCFG) baseGraph;
    }

    /**
     * Convenient function to construct an interCFG. Should be used
     * for the construction requested by mate server.
     *
     * @param apkPath           The path to the APK file.
     * @param useBasicBlocks    Whether to use basic blocks or not.
     * @param excludeARTClasses Whether to exclude ART classes or not.
     * @param onlyResolveAUTClasses Whether only AUT classes should be resolved.
     * @return Returns an interCFG.
     */
    public static BaseCFG constructInterCFG(final File apkPath, final boolean useBasicBlocks,
                                            final boolean excludeARTClasses, final boolean onlyResolveAUTClasses) {

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
        return (BaseCFG) baseGraph;
    }
}
