package de.uni_passau.fim.auermich.android_graphs.core.graphs;

import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.IntraCFG;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class BaseGraphBuilder {

    /* REQUIRED FIELDS */

    private GraphType type;

    // may use some abstract 'source' type here
    private List<DexFile> dexFiles;

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

    // used for InterCFG
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
            case INTRACFG:
                Objects.requireNonNull(method, "Method is mandatory!");
                return new IntraCFG(method, dexFiles.get(0), useBasicBlocks);
            case INTERCFG:
                Objects.requireNonNull(name, "CFG name is mandatory!");
                Objects.requireNonNull(apkFile, "The path to the APK file is mandatory!");
                APK apk = new APK(apkFile, dexFiles);
                return new InterCFG(name, apk, useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
            default:
                throw new UnsupportedOperationException("Graph type not yet supported!");
        }
    }
}