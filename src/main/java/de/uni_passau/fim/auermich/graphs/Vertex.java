package de.uni_passau.fim.auermich.graphs;


import org.jf.dexlib2.iface.instruction.Instruction;

public class Vertex {

    // TODO: may use inheritance for virtual vertex or some boolean flag

    private final int id;
    private final Instruction instruction;

    public Vertex(int id, Instruction instruction) {
        this.id = id;
        this.instruction = instruction;
    }

    public String toString() {
        return String.valueOf(id);
    }

    public boolean isEntryVertex() {
        return id == -1;
    }

}
