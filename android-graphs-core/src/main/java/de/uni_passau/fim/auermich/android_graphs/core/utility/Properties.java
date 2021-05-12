package de.uni_passau.fim.auermich.android_graphs.core.utility;

import java.util.regex.Pattern;

public class Properties {

    public final boolean useBasicBlocks;
    public final boolean excludeARTClasses;
    public final boolean resolveOnlyAUTClasses;
    public final Pattern exclusionPattern = Utility.readExcludePatterns();

    public Properties(boolean useBasicBlocks, boolean excludeARTClasses, boolean resolveOnlyAUTClasses) {
        this.useBasicBlocks = useBasicBlocks;
        this.excludeARTClasses = excludeARTClasses;
        this.resolveOnlyAUTClasses = resolveOnlyAUTClasses;
    }
}
