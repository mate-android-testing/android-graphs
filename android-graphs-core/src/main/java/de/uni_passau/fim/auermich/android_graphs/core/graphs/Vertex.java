package de.uni_passau.fim.auermich.android_graphs.core.graphs;


import de.uni_passau.fim.auermich.android_graphs.core.statement.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statement.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statement.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.Format;
import org.jf.dexlib2.analysis.AnalyzedInstruction;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Vertex implements Cloneable, Comparable<Vertex> {

    private static final Logger LOGGER = LogManager.getLogger(Vertex.class);

    private final VertexType type;

    private Statement statement;

    public Vertex(Statement statement) {
        this.statement = statement;
        type = VertexType.mapType(statement);
    }

    @Override
    public String toString() {

        if (isEntryVertex() || isExitVertex()) {
            return type + " " + statement.getMethod();
        } else {
            return statement.toString();
        }
    }

    @Override
    public int compareTo(Vertex other) {
        // TODO: find better comparison, required by MultiMap implementation
        return Integer.compare(this.hashCode(), other.hashCode());
    }

    /**
     * Checks whether a vertex represents a certain instruction. If the
     * vertex represents a basic block, each statement inside the block
     * is inspected.
     *
     * @param method The full-qualified method name. (matching criteria)
     * @param instructionID The instruction id. (matching criteria)
     * @return Returns {@code true} if the vertex represents a certain
     *          instruction, otherwise {@code false}.
     */
    public boolean containsInstruction(String method, int instructionID) {

        switch (statement.getType()) {
            case ENTRY_STATEMENT:
            case RETURN_STATEMENT:
            case EXIT_STATEMENT:
                return false;
            case BASIC_STATEMENT:
                BasicStatement stmt = (BasicStatement) statement;
                return statement.getMethod().equals(method)
                        && stmt.getInstructionIndex() == instructionID;
            case BLOCK_STATEMENT:
                // inspect each single statement in the basic block
                BlockStatement block = (BlockStatement) statement;
                List<Statement> stmts = block.getStatements();

                boolean containsInstruction = false;

                for (Statement statement : stmts) {
                    if (statement.getType() == Statement.StatementType.BASIC_STATEMENT) {
                        BasicStatement basicStatement = (BasicStatement) statement;
                        if (basicStatement.getMethod().equals(method)
                                && basicStatement.getInstructionIndex() == instructionID) {
                            containsInstruction = true;
                            break;
                        }
                    }
                }
                return containsInstruction;
            default:
                throw new UnsupportedOperationException("Statement type not supported yet");
        }
    }

    /**
     * Checks whether a given vertex represents a branch target, i.e. a successor of an if-statement.
     * Essentially, every branch target must be a leader instruction.
     *
     * @return Returns whether the given vertex represents a branch target.
     */
    public boolean isBranchVertex() {

        switch (statement.getType()) {
            case ENTRY_STATEMENT:
            case RETURN_STATEMENT:
            case EXIT_STATEMENT:
                return false;
            case BASIC_STATEMENT:
                BasicStatement stmt = (BasicStatement) statement;
                Set<AnalyzedInstruction> predecessors = stmt.getInstruction().getPredecessors();
                // check if one of the predecessors is an if statement
                for (AnalyzedInstruction predecessor : predecessors) {
                    Format format = predecessor.getInstruction().getOpcode().format;
                    // 21t and 22t are if instructions
                    if (format == Format.Format21t
                            || format == Format.Format22t) {
                        return true;
                    }
                }
                return false;
            case BLOCK_STATEMENT:
                // TODO: it should be sufficient to look at the first instruction -> branch is a leader instruction!
                // inspect each single statement in the basic block
                BlockStatement block = (BlockStatement) statement;
                List<Statement> stmts = block.getStatements();

                for (Statement statement : stmts) {
                    if (statement.getType() == Statement.StatementType.BASIC_STATEMENT) {
                        BasicStatement basicStatement = (BasicStatement) statement;
                        Set<AnalyzedInstruction> preds = basicStatement.getInstruction().getPredecessors();
                        // check if one of the predecessors is an if statement
                        for (AnalyzedInstruction predecessor : preds) {
                            Format format = predecessor.getInstruction().getOpcode().format;
                            // 21t and 22t are if instructions
                            if (format == Format.Format21t
                                    || format == Format.Format22t) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            default:
                throw new UnsupportedOperationException("Statement type not supported yet");
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

        if (statement.getType() != other.statement.getType()
            || !this.getMethod().equals(other.getMethod())) {
            return false;
        }

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
