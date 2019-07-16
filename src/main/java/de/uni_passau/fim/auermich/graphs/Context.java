package de.uni_passau.fim.auermich.graphs;

import org.jf.dexlib2.analysis.AnalyzedInstruction;

public class Context {

    private final int id;
    private final AnalyzedInstruction instruction;
    private final String method;

    public Context(int id, AnalyzedInstruction instruction, String method) {
        this.id = id;
        this.instruction = instruction;
        this.method = method;
    }

    public int getId() {
        return id;
    }

    public AnalyzedInstruction getInstruction() {
        return instruction;
    }

    public String getMethod() {
        return method;
    }
}
