package de.uni_passau.fim.auermich.graphs;


import de.uni_passau.fim.auermich.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.statement.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.util.List;
import java.util.Objects;

public class Vertex implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(Vertex.class);

    // the type of vertex
    private final VertexType type;

    private final Statement statement;

    public Vertex(Statement statement) {
        this.statement = statement;
        type = VertexType.mapType(statement);
    }

    public String toString() {

        if (isEntryVertex() || isExitVertex() || isReturnVertex()) {
            return type + " " + statement.getMethod();
        } else {
            return statement.toString();
        }
    }

    /**
     * Returns the method name of the vertex's statement.
     *
     * @return Returns the method name belonging to the
     *          vertex statement.
     */
    public String getMethod() {
        return statement.getMethod();
    }

    public Statement getStatement() {
        return statement;
    }

    public boolean isEntryVertex() {
        return type == VertexType.ENTRY_VERTEX;
    }

    public boolean isExitVertex() { return type == VertexType.EXIT_VERTEX; }

    public boolean isReturnVertex() { return type == VertexType.RETURN_VERTEX; }

    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof Vertex)) {
            return false;
        }

        Vertex other = (Vertex) o;

        // unique method signature + instruction id
        return this.statement.equals(other.statement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statement);
    }

    public Vertex clone() {

        try {
            Vertex vertexClone = (Vertex) super.clone();
            // vertexClone.statement = this.statement.clone();
            return vertexClone;
        } catch (CloneNotSupportedException e) {
            LOGGER.warn("Cloning of Vertex failed");
            return null;
        }
    }


    /**
     * Represents the type of vertex. This can be either
     * an entry vertex, an exit vertex, a return vertex or
     * simply a normal vertex.
     */
    private enum VertexType {

        // TODO: may add INVOKE_VERTEX

        ENTRY_VERTEX,
        EXIT_VERTEX,
        RETURN_VERTEX,
        BASIC_VERTEX,
        BLOCK_VERTEX;

        /**
         * Maps a given statement to its vertex type.
         *
         * @param statement The statement belonging to the vertex.
         * @return Returns the vertex type corresponding to the given number.
         */
        private static VertexType mapType(Statement statement) {
            switch (statement.getType()) {
                case ENTRY_STATEMENT:
                    return VertexType.ENTRY_VERTEX;
                case EXIT_STATEMENT:
                    return VertexType.EXIT_VERTEX;
                case RETURN_STATEMENT:
                    return VertexType.RETURN_VERTEX;
                case BASIC_STATEMENT:
                    return VertexType.BASIC_VERTEX;
                case BLOCK_STATEMENT:
                    return VertexType.BLOCK_VERTEX;
                    default:
                        throw new UnsupportedOperationException("Statement type not supported yet!");
            }
        }

        @Override
        public String toString() {
            switch (this) {
                case EXIT_VERTEX: return "exit";
                case ENTRY_VERTEX: return "entry";
                case RETURN_VERTEX: return "return";
                case BASIC_VERTEX: return "basic";
                case BLOCK_VERTEX: return "block";
            }
            throw new UnsupportedOperationException("Vertex type not supported yet!");
        }
    }
}
