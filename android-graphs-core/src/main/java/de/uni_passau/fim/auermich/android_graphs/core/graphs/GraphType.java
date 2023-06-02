package de.uni_passau.fim.auermich.android_graphs.core.graphs;

import java.util.Optional;

/**
 * Describes the various graph types the program
 * can generate.
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

    POST_DOMINATOR_TREE {

        @Override
        public String toString() {
            return "post_dominator_tree";
        }
    },

    INTER_CDG {

        @Override
        public String toString() {
            return "inter_cdg";
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
