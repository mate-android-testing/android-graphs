package de.uni_passau.fim.auermich.android_graphs.core.utility;

import java.util.regex.Pattern;

/**
 * Represents properties relevant for the construction of the
 * {@link de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG}.
 */
public class Properties {

    /**
     * Whether basic blocks should be used.
     */
    public final boolean useBasicBlocks;

    /**
     * Whether ART classes should be excluded/ignored when method invocations
     * are resolved.
     */
    public final boolean excludeARTClasses;

    /**
     * Whether only classes belonging to the AUT should be resolved.
     */
    public final boolean resolveOnlyAUTClasses;

    /**
     * A pattern of classes that should be excluded/ignored.
     */
    public final Pattern exclusionPattern = Utility.readExcludePatterns();

    public Properties(boolean useBasicBlocks, boolean excludeARTClasses, boolean resolveOnlyAUTClasses) {
        this.useBasicBlocks = useBasicBlocks;
        this.excludeARTClasses = excludeARTClasses;
        this.resolveOnlyAUTClasses = resolveOnlyAUTClasses;
    }
}
