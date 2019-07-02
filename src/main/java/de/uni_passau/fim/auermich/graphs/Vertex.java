package de.uni_passau.fim.auermich.graphs;


import org.jf.dexlib2.iface.instruction.Instruction;

import java.util.Objects;

public class Vertex {

    // TODO: may use inheritance for virtual vertex or some boolean flag

    private final int id;
    private final Instruction instruction;

    public Vertex(int id, Instruction instruction) {
        this.id = id;
        this.instruction = instruction;
    }

    public String toString() {

        if (isEntryVertex()) {
            return "entry";
        } else if (isExitVertex()) {
            return "exit";
        } else {
            return String.valueOf(id);
        }
    }

    public boolean isEntryVertex() {
        return id == -1;
    }

    public boolean isExitVertex() { return id == -2; }

    @Override
    public boolean equals(Object o) {

        if (o == this)
            return true;

        if (!(o instanceof Vertex)) {
            return false;
        }

        Vertex other = (Vertex) o;

        // Instruction data type doesn't define equals/hashcode
        // currently only guarantees equality within same method
        // may try out Objects.equals(this.instruction, other.instruction);
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
