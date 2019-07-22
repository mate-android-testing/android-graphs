package de.uni_passau.fim.auermich.graphs;


import de.uni_passau.fim.auermich.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.statement.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.util.List;
import java.util.Objects;

public class Vertex implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(Vertex.class);

    // the type of vertex
    private final VertexType type;

    private Statement statement;

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
     * vertex statement.
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

    public boolean isExitVertex() {
        return type == VertexType.EXIT_VERTEX;
    }

    public boolean isReturnVertex() {
        return type == VertexType.RETURN_VERTEX;
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
        return this.statement.equals(other.statement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statement);
    }

    public Vertex copy() {

        Vertex clone = new Vertex(this.statement.clone());

        Statement stmt = null;

        switch (statement.getType()) {
            case BASIC_STATEMENT:
                BasicStatement basicStatement = (BasicStatement) this.statement;
                /*
                AnalyzedInstruction analyzedInstruction = basicStatement.getInstruction();
                Instruction instruction = basicStatement.getInstruction().getInstruction();
                stmt = new BasicStatement(basicStatement.getMethod(),
                        new AnalyzedInstruction(analyzer, instruction,
                                analyzedInstruction.getInstructionIndex(), analyzedInstruction.getRegisterCount()));
                                */
                stmt = new BasicStatement(basicStatement.getMethod(), basicStatement.getInstruction());
                break;
            case BLOCK_STATEMENT:
                BlockStatement blockStatement = (BlockStatement) this.statement;
                stmt = new BlockStatement(blockStatement.getMethod(), null);
                break;
            case EXIT_STATEMENT:
                stmt = new ExitStatement(this.getMethod());
                break;
            case ENTRY_STATEMENT:
                stmt = new EntryStatement(this.getMethod());
                break;
            case RETURN_STATEMENT:
                ReturnStatement returnStatement = (ReturnStatement) this.statement;
                stmt = new ReturnStatement(returnStatement.getMethod(), returnStatement.getTargetMethod());
                break;
            default:
                throw new UnsupportedOperationException("Statement type not yet supported!");
        }
        clone.statement = stmt;
        return clone;
    }

    public Vertex clone() {

        try {
            Vertex vertexClone = (Vertex) super.clone();
            vertexClone.statement = this.statement.clone();
            return vertexClone;
        } catch (CloneNotSupportedException e) {
            throw new Error("Cloning failed");
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
                case EXIT_VERTEX:
                    return "exit";
                case ENTRY_VERTEX:
                    return "entry";
                case RETURN_VERTEX:
                    return "return";
                case BASIC_VERTEX:
                    return "basic";
                case BLOCK_VERTEX:
                    return "block";
            }
            throw new UnsupportedOperationException("Vertex type not supported yet!");
        }
    }
}
