package de.uni_passau.fim.auermich.graphs;


import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.util.Objects;

public class Vertex {

    // TODO: may use basic blocks (list of ids,instructions,...)

    // the instruction ID within the defined method
    private final int id;

    // the corresponding instruction
    private final AnalyzedInstruction instruction;

    // uniquely identifies each vertex by its method signature
    private final String method;

    private final VERTEX_TYPE type;

    public Vertex(int id, AnalyzedInstruction instruction, String method) {
        this.id = id;
        this.instruction = instruction;
        this.method = method;
        type = VERTEX_TYPE.mapType(id);
    }

    public String toString() {

        if (isEntryVertex() || isExitVertex() || isReturnVertex()) {
            return type + " " + method;
        } else {
            return String.valueOf(id) + ": " + instruction.getInstruction().getOpcode().name;
        }
    }

    public boolean isEntryVertex() {
        return type == VERTEX_TYPE.ENTRY_VERTEX;
    }

    public boolean isExitVertex() { return type == VERTEX_TYPE.EXIT_VERTEX; }

    public boolean isReturnVertex() { return type == VERTEX_TYPE.RETURN_VERTEX; }

    public AnalyzedInstruction getInstruction() {
        return instruction;
    }


    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof Vertex)) {
            return false;
        }

        Vertex other = (Vertex) o;

        // unique method signature + instruction id
        return this.method.equals(other.method) && this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, id);
    }


    /**
     * Represents the type of vertex. This can be either
     * an entry vertex, an exit vertex, a return vertex or
     * simply a normal vertex.
     */
    private enum VERTEX_TYPE {

        // TODO: may add INVOKE_VERTEX

        ENTRY_VERTEX,
        EXIT_VERTEX,
        RETURN_VERTEX,
        NORMAL_VERTEX;

        /**
         * Maps a given number to its vertex type.
         * @param num The number representing a vertex type.
         * @return Returns the vertex type corresponding to the given number.
         */
        private static VERTEX_TYPE mapType(int num) {
            if (num == -1 ) {
                return ENTRY_VERTEX;
            } else if (num == -2 ) {
                return EXIT_VERTEX;
            } else if (num == -3 ) {
                return RETURN_VERTEX;
            } else {
                return NORMAL_VERTEX;
            }
        }

        @Override
        public String toString() {
            switch (this) {
                case EXIT_VERTEX: return "exit";
                case ENTRY_VERTEX: return "entry";
                case RETURN_VERTEX: return "return";
                case NORMAL_VERTEX: return "";
            }
            throw new UnsupportedOperationException();
        }
    }
}
