package de.uni_passau.fim.auermich.android_graphs.core.statements;

import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.rits.cloning.Cloner;

import java.util.Objects;

public class BasicStatement extends Statement implements Cloneable {

    private AnalyzedInstruction instruction;

    public BasicStatement(String method, AnalyzedInstruction instruction) {
        super(method);
        this.instruction = instruction;
        type = StatementType.BASIC_STATEMENT;
    }

    public int getInstructionIndex() { return instruction.getInstructionIndex(); }

    public AnalyzedInstruction getInstruction() {
        return instruction;
    }

    @Override
    public String toString() {
        return instruction.getInstructionIndex() + ": " + instruction.getInstruction().getOpcode().name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof BasicStatement)) {
            return false;
        }

        BasicStatement other = (BasicStatement) o;

        // unique method signature + instruction id
        return this.method.equals(other.method)
                && this.instruction.getInstruction().getOpcode().equals(other.instruction.getInstruction().getOpcode())
                && this.instruction.getInstructionIndex() == other.instruction.getInstructionIndex();
                /*
                && this.instruction.getInstruction().getCodeUnits() == other.instruction.getInstruction().getCodeUnits()
                && this.instruction.getInstruction().getOpcode().format == other.instruction.getInstruction().getOpcode().format
                && this.instruction.getPredecessors().equals(other.instruction.getPredecessors())
                && this.instruction.getSuccessors().equals(other.instruction.getSuccessors());
                */
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, instruction.getInstructionIndex());
    }

    @Override
    public BasicStatement clone() {
        BasicStatement clone = (BasicStatement) super.clone();
        Cloner cloner = new Cloner();
        clone.instruction = cloner.deepClone(this.instruction);
        // clone.instruction = this.instruction;
        return clone;
    }

}
