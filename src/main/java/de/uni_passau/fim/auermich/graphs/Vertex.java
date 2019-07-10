package de.uni_passau.fim.auermich.graphs;


import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.util.Objects;

public class Vertex {

    // TODO: may use inheritance for virtual vertices (entry,exit)

    // the instruction ID within the defined method
    private final int id;

    // the corresponding instruction
    private final AnalyzedInstruction instruction;

    // uniquely identifies each vertex by its method signature
    private final String method;

    public Vertex(int id, AnalyzedInstruction instruction, String method) {
        this.id = id;
        this.instruction = instruction;
        this.method = method;
    }

    public String toString() {

        if (isEntryVertex()) {
            return "entry " + " (" + method + ")";
        } else if (isExitVertex()) {
            return "exit " + " (" + method + ")";
        } else {
            return id + ": " + instruction.getInstruction().getOpcode().name + " (" + method + ")";
        }
    }

    public boolean isEntryVertex() {
        return id == -1;
    }

    public boolean isExitVertex() { return id == -2; }

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

}
