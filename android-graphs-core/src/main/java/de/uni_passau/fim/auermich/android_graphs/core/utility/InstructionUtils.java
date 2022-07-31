package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.Format;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.BuilderInstruction31t;
import org.jf.dexlib2.builder.instruction.BuilderPackedSwitchPayload;
import org.jf.dexlib2.builder.instruction.BuilderSwitchElement;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class InstructionUtils {

    private static final Logger LOGGER = LogManager.getLogger(InstructionUtils.class);

    private InstructionUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    private static final Set<Opcode> INVOKE_OPCODES = new HashSet<>() {{
        add(Opcode.INVOKE_CUSTOM_RANGE);
        add(Opcode.INVOKE_CUSTOM);
        add(Opcode.INVOKE_DIRECT_RANGE);
        add(Opcode.INVOKE_DIRECT);
        add(Opcode.INVOKE_DIRECT_EMPTY);
        add(Opcode.INVOKE_INTERFACE_RANGE);
        add(Opcode.INVOKE_INTERFACE);
        add(Opcode.INVOKE_OBJECT_INIT_RANGE);
        add(Opcode.INVOKE_POLYMORPHIC_RANGE);
        add(Opcode.INVOKE_POLYMORPHIC);
        add(Opcode.INVOKE_STATIC_RANGE);
        add(Opcode.INVOKE_STATIC);
        add(Opcode.INVOKE_SUPER_RANGE);
        add(Opcode.INVOKE_SUPER);
        add(Opcode.INVOKE_SUPER_QUICK_RANGE);
        add(Opcode.INVOKE_SUPER_QUICK);
        add(Opcode.INVOKE_VIRTUAL_RANGE);
        add(Opcode.INVOKE_VIRTUAL);
        add(Opcode.INVOKE_VIRTUAL_QUICK_RANGE);
        add(Opcode.INVOKE_VIRTUAL_QUICK);
    }};

    /**
     * Checks whether the given instruction refers to an if or goto instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a branch or goto instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isJumpInstruction(final AnalyzedInstruction analyzedInstruction) {
        return isBranchingInstruction(analyzedInstruction) || isGotoInstruction(analyzedInstruction);
    }

    /**
     * Checks whether the given instruction refers to an parse-switch, a packed-switch or array payload instruction.
     * These instructions are typically located at the end of a method after the return statement, thus being isolated.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a parse-switch, packed-switch or array payload instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isPayloadInstruction(final AnalyzedInstruction analyzedInstruction) {
        // TODO: may handle the actual parse-switch and packed-switch instructions (not the payload instructions)
        // https://stackoverflow.com/questions/19855800/difference-between-packed-switch-and-sparse-switch-dalvik-opcode
        EnumSet<Opcode> opcodes = EnumSet.of(Opcode.PACKED_SWITCH_PAYLOAD, Opcode.SPARSE_SWITCH_PAYLOAD, Opcode.ARRAY_PAYLOAD);
        if (opcodes.contains(analyzedInstruction.getInstruction().getOpcode())) {
            LOGGER.debug("Sparse/Packed-switch/array payload instruction at index: " + analyzedInstruction.getInstructionIndex());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether the given instruction refers to an parse-switch or packed-switch instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a parse-switch or packed-switch instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isSwitchInstruction(final AnalyzedInstruction analyzedInstruction) {
        // https://stackoverflow.com/questions/19855800/difference-between-packed-switch-and-sparse-switch-dalvik-opcode
        EnumSet<Opcode> opcodes = EnumSet.of(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH);
        if (opcodes.contains(analyzedInstruction.getInstruction().getOpcode())) {
            LOGGER.debug("Sparse/Packed-switch instruction at index: " + analyzedInstruction.getInstructionIndex());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether the given instruction refers to a goto instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a goto instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isGotoInstruction(final AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        EnumSet<Format> gotoInstructions = EnumSet.of(Format.Format10t, Format.Format20t, Format.Format30t);
        return gotoInstructions.contains(instruction.getOpcode().format);
    }

    /**
     * Checks whether the given instruction refers to an if instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a branching instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isBranchingInstruction(final AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        EnumSet<Format> branchingInstructions = EnumSet.of(Format.Format21t, Format.Format22t);
        return branchingInstructions.contains(instruction.getOpcode().format);
    }

    /**
     * Checks whether the given instruction refers to a return or throw statement.
     *
     * @param instruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is a return or throw statement,
     * otherwise {@code false} is returned.
     */
    public static boolean isTerminationStatement(final AnalyzedInstruction instruction) {
        // TODO: should we handle the throw-verification-error instruction?
        return isReturnInstruction(instruction) || instruction.getInstruction().getOpcode() == Opcode.THROW;
    }

    /**
     * Checks whether the given instruction refers to a nop instruction.
     *
     * @param instruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is a nop instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isNOPInstruction(final AnalyzedInstruction instruction) {
        return instruction.getInstruction().getOpcode() == Opcode.NOP;
    }

    /**
     * Checks whether the given instruction refers to a return statement.
     *
     * @param analyzedInstruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is a return statement, otherwise
     * {@code false} is returned.
     */
    public static boolean isReturnInstruction(final AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        EnumSet<Opcode> returnStmts = EnumSet.of(Opcode.RETURN, Opcode.RETURN_WIDE, Opcode.RETURN_OBJECT,
                Opcode.RETURN_VOID, Opcode.RETURN_VOID_BARRIER, Opcode.RETURN_VOID_NO_BARRIER);
        return returnStmts.contains(instruction.getOpcode());
    }

    /**
     * Checks whether the given instruction is any sort of invoke statement.
     *
     * @param analyzedInstruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is an invoke statement,
     * otherwise {@code false} is returned.
     */
    public static boolean isInvokeInstruction(final AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        return INVOKE_OPCODES.contains(instruction.getOpcode());
    }

    /**
     * Checks whether the given instruction is any sort of invoke statement.
     *
     * @param instruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is an invoke statement,
     * otherwise {@code false} is returned.
     */
    public static boolean isInvokeInstruction(final Instruction instruction) {
        return INVOKE_OPCODES.contains(instruction.getOpcode());
    }

    /**
     * Gets the last long value that was written to the target register
     *
     * @param analyzedInstruction The instruction before which to search (excluding)
     * @param register The target register to look out for
     * @return The last long value written to the target register
     */
    public static Optional<Long> getLastWriteToRegister(AnalyzedInstruction analyzedInstruction, int register) {
        return Stream.iterate(analyzedInstruction, a -> a.getPredecessorCount() == 1, a -> a.getPredecessors().first())
                .skip(1) // Skip seed
                .map(a -> isWriteToRegister(a.getInstruction(), register))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private static Optional<Long> isWriteToRegister(Instruction instruction, int register) {
        if (instruction instanceof WideLiteralInstruction
                && instruction instanceof OneRegisterInstruction
                && ((OneRegisterInstruction) instruction).getRegisterA() == register) {
            return Optional.of(((WideLiteralInstruction) instruction).getWideLiteral());
        }

        return Optional.empty();
    }

    private static List<BuilderInstruction> runBackwards(BuilderInstruction builderInstruction, Method method, DexFile dexFile) {
        List<AnalyzedInstruction> analyzedInstructions = MethodUtils.getAnalyzedInstructions(dexFile, method);
        MutableMethodImplementation methodImplementation = new MutableMethodImplementation(method.getImplementation());

        AnalyzedInstruction target = null;
        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {
            if (analyzedInstruction.getInstructionIndex() == builderInstruction.getLocation().getIndex()) {
                target = analyzedInstruction;
            }
        }

        Objects.requireNonNull(target);

        List<AnalyzedInstruction> parents = new LinkedList<>();

        while (true) {
            parents.add(target);

            if (target.getPredecessorCount() == 0) {
                return parents.stream().map(a -> methodImplementation.getInstructions().stream().filter(i -> i.getLocation().getIndex() == a.getInstructionIndex()).findFirst())
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());
            }
            target = target.getPredecessors().first();
        }
    }

    /**
     * Returns the switch case key that the instruction belongs to
     *
     * @param builderInstruction The target instruction
     * @param method The method containing the target instruction
     * @param dexFile The dex file containing the method
     * @return The switch case key that the instruction belongs to
     */
    public static Optional<Integer> getSwitchCaseKey(BuilderInstruction builderInstruction, Method method, DexFile dexFile) {
        return getSwitchElementOfInstruction(builderInstruction, method, dexFile)
                .map(SwitchElement::getKey);
    }

    private static Optional<SwitchElement> getSwitchElementOfInstruction(BuilderInstruction instruction, Method method, DexFile dexFile) {
        List<BuilderInstruction> path = InstructionUtils.runBackwards(instruction, method, dexFile);

        BuilderInstruction prev = null;
        for (BuilderInstruction analyzedInstruction : path) {
            var switchElements = getSwitchElements(analyzedInstruction);

            if (switchElements.isPresent()) {
                for (BuilderSwitchElement switchElement : switchElements.get()) {
                    if (switchElement.getTarget().getLocation().getIndex() == prev.getLocation().getIndex()) {
                        return Optional.of(switchElement);
                    }
                }

                throw new IllegalStateException("Was not able to find switch target!");
            }

            prev = analyzedInstruction;
        }

        return Optional.empty();
    }

    private static Optional<List<? extends BuilderSwitchElement>> getSwitchElements(BuilderInstruction maybeSwitchInstruction) {
        if (maybeSwitchInstruction.getOpcode() == Opcode.PACKED_SWITCH && maybeSwitchInstruction instanceof BuilderInstruction31t) {
            Instruction switchPayload = ((BuilderInstruction31t) maybeSwitchInstruction).getTarget().getLocation().getInstruction();

            if (switchPayload instanceof SwitchPayload) {
                return Optional.of(((BuilderPackedSwitchPayload) switchPayload).getSwitchElements());
            }
        }

        return Optional.empty();
    }
}
