package de.uni_passau.fim.auermich.android_graphs.core.graphs;

import java.util.Optional;

/**
 * Describes the various graph types the program can generate.
 */
public enum GraphType {

    /**
     * An intra-procedural CFG.
     */
    INTRACFG  {

        @Override
        public String toString() {
            return "intra";
        }
    },

    /**
     * An inter-procedural CFG.
     */
    INTERCFG {

        @Override
        public String toString() {
            return "inter";
        }
    },

    /**
     * A post dominator tree.
     */
    PDT {

        @Override
        public String toString() {
            return "pdt";
        }
    },

    /**
     * An inter-procedural control dependence graph.
     */
    INTERCDG {

        @Override
        public String toString() {
            return "intercdg";
        }
    },

    /**
     * An intra-procedural control dependence graph.
     */
    INTRACDG {

        @Override
        public String toString() {
            return "intracdg";
        }
    },

    /**
     * A system dependence graph.
     */
    SGD {

        @Override
        public String toString() {
            return "sgd";
        }
    },

    /**
     * A call tree.
     */
    CALLTREE {
        @Override
        public String toString() {
            return "calltree";
        }
    };

    /**
     * Checks whether the given {@param input} is a valid enum.
     *
     * @param input The possible enum given as string.
     * @return Returns the given enum if present.
     */
    public static Optional<GraphType> fromString(String input) {

        for(GraphType type : GraphType.values()) {
            if(type.toString().equalsIgnoreCase(input)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
