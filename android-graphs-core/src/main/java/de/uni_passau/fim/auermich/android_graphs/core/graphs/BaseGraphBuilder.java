package de.uni_passau.fim.auermich.android_graphs.core.graphs;

import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.calltree.CallTree;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.InterCDG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.IntraCDG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.ModularCDG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.IntraCFG;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class BaseGraphBuilder {

    /* REQUIRED FIELDS */

    private final GraphType type;

    // may use some abstract 'source' type here
    private final List<DexFile> dexFiles;

    /* END REQUIRED FIELDS */

    /* OPTIONAL FIELDS */

    // the CFG (method) name
    private String name;

    // the target method (IntraCFG)
    private Method method;

    private boolean useBasicBlocks = false;

    private File apkFile;

    private boolean excludeARTClasses = false;

    private boolean resolveOnlyAUTClasses = false;

    /* END OPTIONAL FIELDS */

    // used for InterCFG and call tree
    public BaseGraphBuilder(GraphType type, List<DexFile> dexFiles) {
        this.type = type;
        this.dexFiles = dexFiles;
    }

    // used for IntraCFG
    public BaseGraphBuilder(GraphType type, DexFile dexFile, Method method) {
        this.type = type;
        this.dexFiles = List.of(dexFile);
        this.method = method;
    }

    public BaseGraphBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public BaseGraphBuilder withBasicBlocks() {
        this.useBasicBlocks = true;
        return this;
    }

    public BaseGraphBuilder withAPKFile(File apkFile) {
        this.apkFile = apkFile;
        return this;
    }

    public BaseGraphBuilder withExcludeARTClasses() {
        this.excludeARTClasses = true;
        return this;
    }

    public BaseGraphBuilder withResolveOnlyAUTClasses() {
        this.resolveOnlyAUTClasses = true;
        return this;
    }

    public BaseGraph build() {
        switch (type) {
            case INTRACFG: {
                Objects.requireNonNull(method, "Method is mandatory!");
                return new IntraCFG(method, dexFiles.get(0), useBasicBlocks);
            }
            case INTERCFG: {
                Objects.requireNonNull(name, "CFG name is mandatory!");
                Objects.requireNonNull(apkFile, "The path to the APK file is mandatory!");
                APK apk = new APK(apkFile, dexFiles);
                return new InterCFG(name, apk, useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
            }
            case INTERCDG: {
                Objects.requireNonNull(name, "CFG name is mandatory!");
                Objects.requireNonNull(apkFile, "The path to the APK file is mandatory!");
                APK apk = new APK(apkFile, dexFiles);
                final InterCFG cfg = new InterCFG(name, apk, useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
                return new InterCDG(cfg);
            }
            case INTRACDG: {
                Objects.requireNonNull(method, "Method is mandatory!");
                final IntraCFG intraCFG = new IntraCFG(method, dexFiles.get(0), useBasicBlocks);
                return new IntraCDG(intraCFG);
            }
            case MODULARCDG: {
                Objects.requireNonNull(name, "CFG name is mandatory!");
                Objects.requireNonNull(apkFile, "The path to the APK file is mandatory!");
                APK apk = new APK(apkFile, dexFiles);
                return new ModularCDG(name, apk, useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
            }
            case CALLTREE: {
                Objects.requireNonNull(name, "Call tree name is mandatory!");
                Objects.requireNonNull(apkFile, "The path to the APK file is mandatory!");
                APK apk = new APK(apkFile, dexFiles);
                InterCFG interCFG = new InterCFG(name, apk, useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
                return new CallTree(interCFG);
            }
            default:
                throw new UnsupportedOperationException("Graph type not yet supported!");
        }
    }
}
