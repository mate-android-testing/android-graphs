package de.uni_passau.fim.auermich.core.graphs;

import de.uni_passau.fim.auermich.core.app.APK;
import de.uni_passau.fim.auermich.core.graphs.cfg.InterCFG;
import de.uni_passau.fim.auermich.core.graphs.cfg.IntraCFG;
import de.uni_passau.fim.auermich.core.utility.Utility;
import org.jf.dexlib2.iface.DexFile;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BaseGraphBuilder {

    /* REQUIRED FIELDS */

    private GraphType type;

    // may use some abstract 'source' type here
    private List<DexFile> dexFiles;

    /* END REQUIRED FIELDS */

    /* OPTIONAL FIELDS */

    // the CFG (method) name
    private String name;

    private boolean useBasicBlocks = false;

    private File apkFile;

    private boolean excludeARTClasses = false;

    /* END OPTIONAL FIELDS */

    public BaseGraphBuilder(GraphType type, List<DexFile> dexFiles) {
        this.type = type;
        this.dexFiles = dexFiles;
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

    public BaseGraph build() {
        switch (type) {
            case INTRACFG:

                // constraints:
                Objects.requireNonNull(name, "CFG method name is mandatory!");
                Optional<DexFile> dexFile = Utility.containsTargetMethod(dexFiles, name);
                if (!dexFile.isPresent()) {
                    throw new IllegalArgumentException("Method not present in dex files!");
                }

                return new IntraCFG(name, dexFile.get(), useBasicBlocks);
            case INTERCFG:
                Objects.requireNonNull(name, "CFG name is mandatory!");
                Objects.requireNonNull(apkFile, "The path to the APK file is mandatory!");
                APK apk = new APK(apkFile, dexFiles);
                return new InterCFG(name, apk, useBasicBlocks, excludeARTClasses);
            default:
                throw new UnsupportedOperationException("Graph type not yet supported!");
        }
    }
}
