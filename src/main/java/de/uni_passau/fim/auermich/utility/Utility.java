package de.uni_passau.fim.auermich.utility;

import brut.androlib.ApkDecoder;
import brut.common.BrutException;
import com.google.common.collect.Lists;
import de.uni_passau.fim.auermich.graphs.BaseGraph;
import de.uni_passau.fim.auermich.graphs.BaseGraphBuilder;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.statement.BasicStatement;
import de.uni_passau.fim.auermich.statement.BlockStatement;
import de.uni_passau.fim.auermich.statement.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Format;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.DexClassProvider;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.value.DexBackedTypeEncodedValue;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import static de.uni_passau.fim.auermich.Cli.API_OPCODE;


public final class Utility {

    public static final String EXCLUSION_PATTERN_FILE = "exclude.txt";
    private static final Logger LOGGER = LogManager.getLogger(Utility.class);

    /**
     * It seems that certain resource classes are API dependent, e.g.
     * "R$interpolator" is only available in API 21.
     */
    private static final Set<String> resourceClasses = new HashSet<String>() {{
        add("R$anim");
        add("R$attr");
        add("R$bool");
        add("R$color");
        add("R$dimen");
        add("R$drawable");
        add("R$id");
        add("R$integer");
        add("R$layout");
        add("R$mipmap");
        add("R$string");
        add("R$style");
        add("R$styleable");
        add("R$interpolator");
    }};

    private static final Set<Opcode> invokeOpcodes = new HashSet<Opcode>() {{
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

    private Utility() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Returns the dex files in the given {@param directory}.
     *
     * @param directory The directory to search for the dex files.
     * @return Returns a list of dex files found in the given directory.
     */
    public static File[] getDexFiles(File directory) {

        File[] matches = directory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("classes") && name.endsWith(".dex");
            }
        });
        return matches;
    }

    /**
     * Checks whether the given method refers to the invocation of a fragment. That
     * is either a call to add() or replace(). We do not ensure that commit() is
     * called afterwards.
     *
     * @param method The method to be checked against.
     * @return Returns {@code true} if method refers to the invocation of a component,
     * otherwise {@code false} is returned.
     */
    public static boolean isFragmentInvocation(String method) {
        return method.contains("Landroid/support/v4/app/FragmentTransaction;->" +
                "add(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;")
                || method.contains("Landroid/app/FragmentTransaction;->" +
                "replace(ILandroid/app/Fragment;)Landroid/app/FragmentTransaction;")
                || method.contains("Landroidx/fragment/app/FragmentTransaction;->" +
                "add(ILandroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;");
    }

    /**
     * Checks whether the instruction refers to the invocation of a fragment.
     * Only call this method when isFragmentInvocation() returns {@code true}.
     *
     * @param analyzedInstruction The given instruction.
     * @return Returns the name of the fragment or {@code null} if the fragment name
     *      couldn't be derived.
     */
    public static String isFragmentInvocation(AnalyzedInstruction analyzedInstruction) {

        LOGGER.debug("Try deriving name of fragment...");

        // TODO: get rid of list and directly return the string or null
        List<String> fragments = new ArrayList<>();

        Instruction instruction = analyzedInstruction.getInstruction();
        String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

        // TODO: verify that only 'Instruction35c' is valid for fragment invocations
        if (instruction instanceof Instruction35c && instruction.getOpcode() == Opcode.INVOKE_VIRTUAL) {

            Instruction35c invokeVirtual = (Instruction35c) instruction;

            if (targetMethod.contains("Landroid/support/v4/app/FragmentTransaction;->" +
                    "add(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;")) {
                // a fragment is added to the current component (class)

                // typical call: v0 (Reg C), v1 (Reg D), v2 (Reg E)
                //     invoke-virtual {v0, v1, v2}, Landroid/support/v4/app/FragmentTransaction;->
                // add(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;

                // we are interested in register E (refers to the fragment)
                int fragmentRegisterID = invokeVirtual.getRegisterE();

                // go over all predecessors
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
                predecessors.forEach(pred -> fragments.addAll(isFragmentAddInvocationRecursive(pred, fragmentRegisterID)));

            } else if (targetMethod.contains("Landroid/app/FragmentTransaction;->" +
                    "replace(ILandroid/app/Fragment;)Landroid/app/FragmentTransaction;")) {
                // a fragment is replaced with another one

                // Register C: v8, Register D: p0, Register E: p4
                // invoke-virtual {v8, p0, p4}, Landroid/app/FragmentTransaction;
                //              ->replace(ILandroid/app/Fragment;)Landroid/app/FragmentTransaction;

                // we are interested in register E (refers to the fragment)
                int fragmentRegisterID = invokeVirtual.getRegisterE();

                // go over all predecessors
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
                predecessors.forEach(pred -> fragments.addAll(isFragmentReplaceInvocationRecursive(pred, fragmentRegisterID)));
            } else if (targetMethod.contains("Landroidx/fragment/app/FragmentTransaction;->" +
                    "add(ILandroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;")) {
                // a fragment is added (API 28)

                // invoke-virtual {v1, v3, v2, v4}, Landroidx/fragment/app/FragmentTransaction;->
                //      add(ILandroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;

                // we are interested in register E (refers to the fragment)
                int fragmentRegisterID = invokeVirtual.getRegisterE();

                // go over all predecessors
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
                predecessors.forEach(pred -> fragments.addAll(isFragmentAddInvocationRecursive(pred, fragmentRegisterID)));
            }
        }

        if (!fragments.isEmpty()) {
            return fragments.get(0);
        } else {
            return null;
        }
    }

    /**
     * Recursively looks up every predecessor for holding a reference to a fragment.
     *
     * @param pred               The current predecessor instruction.
     * @param fragmentRegisterID The register potentially holding a fragment.
     * @return Returns a list of fragments or an empty list if no fragment was found.
     */
    private static List<String> isFragmentReplaceInvocationRecursive(AnalyzedInstruction pred, int fragmentRegisterID) {

        List<String> fragments = new ArrayList<>();

        // basic case
        if (pred.getInstructionIndex() == -1) {
            return fragments;
        }

        // check current instruction
        if (pred.getInstruction().getOpcode() == Opcode.NEW_INSTANCE) {
            // new-instance p4, Lcom/android/calendar/DayFragment;
            Instruction21c newInstance = (Instruction21c) pred.getInstruction();
            if (newInstance.getRegisterA() == fragmentRegisterID) {
                String fragment = newInstance.getReference().toString();
                LOGGER.debug("Fragment: " + fragment);
                // save for each activity the name of the fragment it hosts
                fragments.add(fragment);
            }
        }

        // check all predecessors
        Set<AnalyzedInstruction> predecessors = pred.getPredecessors();
        predecessors.forEach(p -> fragments.addAll(isFragmentReplaceInvocationRecursive(p, fragmentRegisterID)));
        return fragments;
    }

    /**
     * Recursively looks up every predecessor for holding a reference to a fragment.
     *
     * @param pred               The current predecessor instruction.
     * @param fragmentRegisterID The register potentially holding a fragment.
     * @return Returns a list of fragments or an empty list if no fragment was found.
     */
    private static List<String> isFragmentAddInvocationRecursive(AnalyzedInstruction pred, int fragmentRegisterID) {

        List<String> fragments = new ArrayList<>();

        // basic case
        if (pred.getInstructionIndex() == -1) {
            return fragments;
        }

        // invoke direct refers to constructor calls
        if (pred.getInstruction().getOpcode() == Opcode.INVOKE_DIRECT) {
            // invoke-direct {v2}, Lcom/zola/bmi/BMIMain$PlaceholderFragment;-><init>()V
            Instruction35c constructor = (Instruction35c) pred.getInstruction();
            if (constructor.getRegisterC() == fragmentRegisterID) {
                String constructorInvocation = constructor.getReference().toString();
                LOGGER.debug("Fragment: " + constructorInvocation);
                // save for each activity the name of the fragment it hosts
                String fragment = Utility.getClassName(constructorInvocation);
                fragments.add(fragment);
            }
        }

        // check all predecessors
        Set<AnalyzedInstruction> predecessors = pred.getPredecessors();
        predecessors.forEach(p -> fragments.addAll(isFragmentAddInvocationRecursive(p, fragmentRegisterID)));
        return fragments;
    }

    /**
     * Checks whether the given method refers to the invocation of a component, e.g. an activity.
     *
     * @param method The method to be checked against.
     * @return Returns {@code true} if method refers to the invocation of a component,
     * otherwise {@code false} is returned.
     */
    public static boolean isComponentInvocation(String method) {
        // TODO: add missing invocations, e.g. startService() + use static class variable (set)
        return method.endsWith("startActivity(Landroid/content/Intent;)V")
                || method.endsWith("startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V");
    }

    /**
     * Checks whether the given instruction refers to the invocation of a component.
     * Only call this method when isComponentInvocation() returns {@code true}.
     *
     * @param analyzedInstruction The given instruction.
     * @return Returns the constructor name of the target component if the instruction
     * refers to a component invocation, otherwise {@code null}.
     */
    public static String isComponentInvocation(AnalyzedInstruction analyzedInstruction) {

        Instruction instruction = analyzedInstruction.getInstruction();

        // check for invoke/invoke-range instruction
        if (Utility.isInvokeInstruction(analyzedInstruction)) {

            String methodSignature = ((ReferenceInstruction) instruction).getReference().toString();

            if (methodSignature.endsWith("startActivity(Landroid/content/Intent;)V")
                    || methodSignature.endsWith("startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V")) {

                if (analyzedInstruction.getPredecessors().isEmpty()) {
                    // there is no predecessor -> target activity name might be defined somewhere else or external
                    return null;
                }

                // go back until we find const-class instruction which holds the activity name
                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                // TODO: check that we don't miss activities, go back recursively if there are several predecessors
                // upper bound to avoid resolving external activities or activities defined in a different method

                while (pred.getInstructionIndex() != -1) {
                    Instruction predecessor = pred.getInstruction();
                    if (predecessor.getOpcode() == Opcode.CONST_CLASS) {
                        String targetActivity = ((Instruction21c) predecessor).getReference().toString();
                        // return the full-qualified name of the constructor
                        return targetActivity + "-><init>()V";
                    } else {
                        if (analyzedInstruction.getPredecessors().isEmpty()) {
                            // there is no predecessor -> target activity name might be defined somewhere else or external
                            return null;
                        } else {
                            // TODO: may use recursive search over all predecessors
                            pred = pred.getPredecessors().first();
                        }
                    }
                }
            } else if (methodSignature.equals("Landroid/content/Context;->startService(Landroid/content/Intent;)Landroid/content/ComponentName;")) {

                // invoke-virtual {p0, p1}, Landroid/content/Context;->startService(Landroid/content/Intent;)Landroid/content/ComponentName;

                if (analyzedInstruction.getPredecessors().isEmpty()) {
                    // there is no predecessor -> target activity name might be defined somewhere else or external
                    return null;
                }

                // go back until we find const-class instruction which holds the service name
                AnalyzedInstruction pred = analyzedInstruction.getPredecessors().first();

                while (pred.getInstructionIndex() != -1) {
                    Instruction predecessor = pred.getInstruction();
                    if (predecessor.getOpcode() == Opcode.CONST_CLASS) {
                        String service = ((Instruction21c) predecessor).getReference().toString();
                        // return the full-qualified name of the constructor
                        return service + "-><init>()V";
                    } else {
                        if (analyzedInstruction.getPredecessors().isEmpty()) {
                            // there is no predecessor -> target activity name might be defined somewhere else or external
                            return null;
                        } else {
                            // TODO: may use recursive search over all predecessors
                            pred = pred.getPredecessors().first();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Retrieves the class name of the method's defining class.
     *
     * @param methodSignature The given method signature.
     * @return Returns the class name.
     */
    public static String getClassName(String methodSignature) {
        return methodSignature.split("->")[0];
    }

    /**
     * Derives a unique method signature in order to avoid
     * name clashes originating from overloaded/inherited methods
     * or methods in different classes.
     *
     * @param method The method to derive its method signature.
     * @return Returns the method signature of the given {@param method}.
     */
    public static String deriveMethodSignature(Method method) {

        String className = method.getDefiningClass();
        String methodName = method.getName();
        List<? extends MethodParameter> parameters = method.getParameters();
        String returnType = method.getReturnType();

        StringBuilder builder = new StringBuilder();
        builder.append(className);
        builder.append("->");
        builder.append(methodName);
        builder.append("(");

        for (MethodParameter param : parameters) {
            builder.append(param.getType());
        }

        builder.append(")");
        builder.append(returnType);
        return builder.toString();
    }

    public static Optional<DexFile> containsTargetMethod(List<DexFile> dexFiles, String methodSignature) {

        String className = methodSignature.split("->")[0];

        for (DexFile dexFile : dexFiles) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (classDef.toString().equals(className)) {
                    for (Method method : classDef.getMethods()) {
                        if (Utility.deriveMethodSignature(method).equals(methodSignature)) {
                            return Optional.of(dexFile);
                        }
                    }
                    // speed up
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<Method> searchForTargetMethod(List<DexFile> dexFile, String methodSignature) {
        return null;
    }

    /**
     * Checks whether the given instruction refers to an if or goto instruction.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a branch or goto instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isJumpInstruction(AnalyzedInstruction analyzedInstruction) {
        return isBranchingInstruction(analyzedInstruction) || isGotoInstruction(analyzedInstruction);
    }

    /**
     * Checks whether the given instruction refers to an parse-switch or packed-switch payload instruction.
     * These instructions are typically located at the end of a method after the return statement, thus
     * being isolated.
     *
     * @param analyzedInstruction The instruction to be analyzed.
     * @return Returns {@code true} if the instruction is a parse-switch or packed-switch instruction,
     * otherwise {@code false} is returned.
     */
    public static boolean isSwitchPayloadInstruction(AnalyzedInstruction analyzedInstruction) {
        // TODO: may handle the actual parse-switch and packed-switch instructions (not the payload instructions)
        // https://stackoverflow.com/questions/19855800/difference-between-packed-switch-and-sparse-switch-dalvik-opcode
        EnumSet<Opcode> opcodes = EnumSet.of(Opcode.PACKED_SWITCH_PAYLOAD, Opcode.SPARSE_SWITCH_PAYLOAD);
        if (opcodes.contains(analyzedInstruction.getInstruction().getOpcode())) {
            LOGGER.debug("Sparse/Packed-switch payload instruction at index: " + analyzedInstruction.getInstructionIndex());
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
    public static boolean isSwitchInstruction(AnalyzedInstruction analyzedInstruction) {
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
    public static boolean isGotoInstruction(AnalyzedInstruction analyzedInstruction) {
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
    public static boolean isBranchingInstruction(AnalyzedInstruction analyzedInstruction) {
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
    public static boolean isTerminationStatement(AnalyzedInstruction instruction) {
        // TODO: should we handle the throw-verification-error instruction?
        return isReturnStatement(instruction) || instruction.getInstruction().getOpcode() == Opcode.THROW;
    }

    /**
     * Checks whether the given instruction refers to a return statement.
     *
     * @param analyzedInstruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is a return statement, otherwise
     * {@code false} is returned.
     */
    public static boolean isReturnStatement(AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        EnumSet<Opcode> returnStmts = EnumSet.of(Opcode.RETURN, Opcode.RETURN_WIDE, Opcode.RETURN_OBJECT,
                Opcode.RETURN_VOID, Opcode.RETURN_VOID_BARRIER, Opcode.RETURN_VOID_NO_BARRIER);
        return returnStmts.contains(instruction.getOpcode());
    }

    /**
     * Convenient function to get the list of {@code AnalyzedInstruction} of a certain target method.
     *
     * @param dexFile The dex file containing the target method.
     * @param method  The target method.
     * @return Returns a list of {@code AnalyzedInstruction} included in the target method.
     */
    public static List<AnalyzedInstruction> getAnalyzedInstructions(DexFile dexFile, Method method) {

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), method,
                null, false);

        return analyzer.getAnalyzedInstructions();
    }

    /**
     * Searches for a target method in the given {@code dexFile}.
     *
     * @param dexFile         The dexFile to search in.
     * @param methodSignature The signature of the target method.
     * @return Returns an optional containing either the target method or not.
     */
    public static Optional<Method> searchForTargetMethod(DexFile dexFile, String methodSignature) {

        // TODO: search for target method based on className + method signature
        String className = methodSignature.split("->")[0];

        Set<? extends ClassDef> classes = dexFile.getClasses();

        // search for target method
        for (ClassDef classDef : classes) {
            if (classDef.toString().equals(className)) {
                for (Method method : classDef.getMethods()) {
                    if (Utility.deriveMethodSignature(method).equals(methodSignature)) {
                        return Optional.of(method);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Decodes a given APK using apktool.
     */
    public static String decodeAPK(File apkFile) {

        String decodingOutputPath = null;

        try {
            // ApkDecoder decoder = new ApkDecoder(new Androlib());
            ApkDecoder decoder = new ApkDecoder(apkFile);

            // path where we want to decode the APK
            String parentDir = apkFile.getParent();
            String outputDir = parentDir + File.separator + "out";

            LOGGER.debug("Decoding Output Dir: " + outputDir);
            decoder.setOutDir(new File(outputDir));
            decodingOutputPath = outputDir;

            // whether to decode classes.dex into smali files: -s
            decoder.setDecodeSources(ApkDecoder.DECODE_SOURCES_NONE);

            // overwrites existing dir: -f
            decoder.setForceDelete(true);

            decoder.decode();
        } catch (BrutException | IOException e) {
            LOGGER.warn("Failed to decode APK file!");
            LOGGER.warn(e.getMessage());
        }
        return decodingOutputPath;
    }

    public static boolean isInnerClass(String methodSignature) {
        return methodSignature.contains("$");
    }

    public static String getOuterClass(String className) {
        return className.split("\\$")[0] + ";";
    }

    public static MethodAnalyzer getAnalyzer(DexFile dexFile, Method targetMethod) {

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), targetMethod,
                null, false);
        return analyzer;
    }

    /**
     * Generates patterns of classes which should be excluded from the instrumentation.
     *
     * @return The pattern representing classes that should not be instrumented.
     */
    public static Pattern readExcludePatterns() {

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(EXCLUSION_PATTERN_FILE);

        if (inputStream == null) {
            LOGGER.warn("Couldn't find exlcusion file!");
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        StringBuilder builder = new StringBuilder();

        try {
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first)
                    first = false;
                else
                    builder.append("|");
                builder.append(line);
            }
            reader.close();
        } catch (IOException e) {
            LOGGER.error("Couldn't read from exclusion file!");
            e.printStackTrace();
            return null;
        }
        return Pattern.compile(builder.toString());
    }

    /**
     * Transforms a class name containing '/' into a class name with '.'
     * instead, and removes the leading 'L' as well as the ';' at the end.
     *
     * @param className The class name which should be transformed.
     * @return The transformed class name.
     */
    public static String dottedClassName(String className) {
        className = className.substring(className.indexOf('L') + 1, className.indexOf(';'));
        className = className.replace('/', '.');
        return className;
    }

    /**
     * Checks whether a block statement contains an invoke instruction.
     *
     * @param blockStatement The block statement to be checked.
     * @return Returns {@code true} if the block statement contains an
     * invoke instruction, otherwise {@code false} is returned.
     */
    public static boolean containsInvoke(BlockStatement blockStatement) {

        for (Statement statement : blockStatement.getStatements()) {
            if (statement instanceof BasicStatement) {
                if (Utility.isInvokeInstruction(((BasicStatement) statement).getInstruction())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks whether the given instruction is any sort of invoke statement.
     *
     * @param analyzedInstruction The instruction to be inspected.
     * @return Returns {@code true} if the given instruction is an invoke statement,
     * otherwise {@code false} is returned.
     */
    public static boolean isInvokeInstruction(AnalyzedInstruction analyzedInstruction) {
        Instruction instruction = analyzedInstruction.getInstruction();
        return invokeOpcodes.contains(instruction.getOpcode());
    }

    /**
     * Checks whether the given class represents the dynamically generated BuildConfig class.
     *
     * @param classDef The class to be checked.
     * @return Returns {@code true} if the given class represents the dynamically generated
     * BuildConfig class, otherwise {@code false} is returned.
     */
    public static boolean isBuildConfigClass(ClassDef classDef) {
        String className = Utility.dottedClassName(classDef.toString());
        // TODO: check solely the last token (the actual class name)
        return className.endsWith("BuildConfig");
    }

    /**
     * Checks whether the given class represents the dynamically generated R class or any
     * inner class of it.
     *
     * @param classDef The class to be checked.
     * @return Returns {@code true} if the given class represents the R class or any
     * inner class of it, otherwise {@code false} is returned.
     */
    public static boolean isResourceClass(ClassDef classDef) {

        String className = Utility.dottedClassName(classDef.toString());

        String[] tokens = className.split("\\.");

        // check whether it is the R class itself
        if (tokens[tokens.length - 1].equals("R")) {
            return true;
        }

        // check for inner R classes
        for (String resourceClass : resourceClasses) {
            if (className.contains(resourceClass)) {
                return true;
            }
        }

        // TODO: can be removed, just for illustration how to process annotations
        Set<? extends Annotation> annotations = classDef.getAnnotations();

        for (Annotation annotation : annotations) {

            // check if the enclosing class is the R class
            if (annotation.getType().equals("Ldalvik/annotation/EnclosingClass;")) {
                for (AnnotationElement annotationElement : annotation.getElements()) {
                    if (annotationElement.getValue() instanceof DexBackedTypeEncodedValue) {
                        DexBackedTypeEncodedValue value = (DexBackedTypeEncodedValue) annotationElement.getValue();
                        if (value.getValue().equals("Landroidx/appcompat/R;")) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks whether the given method is an (inherited) ART method, e.g. startActivity().
     *
     * @param method The method signature.
     * @return Returns {@code true} if the given method is an ART method,
     * otherwise {@code false}.
     */
    public static boolean isARTMethod(String method) {

        // TODO: add further patterns, e.g.:
        // getSupportFragmentManager()
        // setContentView()
        // startService()

        if (method.endsWith("startActivity(Landroid/content/Intent;)V")
                || method.endsWith("startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V")
                || method.endsWith("findViewById(I)Landroid/view/View;")
                || method.endsWith("setContentView(I)V")
                || method.endsWith("setContentView(Landroid/view/View;)V")
                || method.endsWith("setContentView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V")
                || method.endsWith("getSupportFragmentManager()Landroid/support/v4/app/FragmentManager;")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks whether the given class represents an activity by checking against the super class.
     *
     * @param classes      The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is an activity,
     * otherwise {@code false}.
     */
    public static boolean isActivity(List<ClassDef> classes, ClassDef currentClass) {

        // TODO: this approach might be quite time-consuming, may find a better solution

        String superClass = currentClass.getSuperclass();
        boolean abort = false;

        while (!abort && superClass != null && !superClass.equals("Ljava/lang/Object;")) {

            abort = true;

            if (superClass.equals("Landroid/app/Activity;")
                    || superClass.equals("Landroidx/appcompat/app/AppCompatActivity;")
                    || superClass.equals("Landroid/support/v7/app/AppCompatActivity;")
                    || superClass.equals("Landroid/support/v7/app/ActionBarActivity;")
                    || superClass.equals("Landroid/support/v4/app/FragmentActivity;")) {
                return true;
            } else {
                // step up in the class hierarchy
                for (ClassDef classDef : classes) {
                    if (classDef.toString().equals(superClass)) {
                        superClass = classDef.getSuperclass();
                        abort = false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given class represents a fragment by checking against the super class.
     *
     * @param classes      The set of classes.
     * @param currentClass The class to be inspected.
     * @return Returns {@code true} if the current class is a fragment,
     * otherwise {@code false}.
     */
    public static boolean isFragment(List<ClassDef> classes, ClassDef currentClass) {

        // TODO: this approach might be quite time-consuming, may find a better solution

        String superClass = currentClass.getSuperclass();
        boolean abort = false;

        while (!abort && superClass != null && !superClass.equals("Ljava/lang/Object;")) {

            abort = true;

            // https://developer.android.com/reference/android/app/Fragment
            if (superClass.equals("Landroid/app/Fragment;")
                    || superClass.equals("Landroidx/fragment/app/Fragment;")
                    || superClass.equals("Landroid/support/v4/app/Fragment;")
                    || superClass.equals("Landroid/app/DialogFragment;")
                    || superClass.equals("Landroid/app/ListFragment;")
                    || superClass.equals("Landroid/preference/PreferenceFragment;")
                    || superClass.equals("Landroid/webkit/WebViewFragment;")) {
                return true;
            } else {
                // step up in the class hierarchy
                for (ClassDef classDef : classes) {
                    if (classDef.toString().equals(superClass)) {
                        superClass = classDef.getSuperclass();
                        abort = false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Convenient function to construct an intraCFG. Should be used
     * for the construction requested by mate server.
     *
     * @param apkPath The path to the APK file.
     * @param method The FQN name of the method.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @return Returns an intraCFG for the specified method.
     */
    public static BaseCFG constructIntraCFG(File apkPath, String method, boolean useBasicBlocks) {

        MultiDexContainer<? extends DexBackedDexFile> apk = null;

        try {
            apk = DexFileFactory.loadDexContainer(apkPath, API_OPCODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DexFile> dexFiles = new ArrayList<>();
        List<String> dexEntries = new ArrayList<>();

        try {
            dexEntries = apk.getDexEntryNames();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String dexEntry : dexEntries) {
            try {
                dexFiles.add(apk.getEntry(dexEntry).getDexFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTRACFG, dexFiles)
                .withName(method);

        if (useBasicBlocks) {
            builder = builder.withBasicBlocks();
        }

        BaseGraph baseGraph = builder.build();
        return (BaseCFG) baseGraph;
    }

    /**
     * Convenient function to construct an interCFG. Should be used
     * for the construction requested by mate server.
     *
     * @param apkPath The path to the APK file.
     * @param useBasicBlocks Whether to use basic blocks or not.
     * @param excludeARTClasses Whether to exclude ART classes or not.
     * @return Returns an interCFG.
     */
    public static BaseCFG constructInterCFG(File apkPath, boolean useBasicBlocks, boolean excludeARTClasses) throws IOException {

        MultiDexContainer<? extends DexBackedDexFile> apk = null;

        try {
            apk = DexFileFactory.loadDexContainer(apkPath, API_OPCODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<DexFile> dexFiles = new ArrayList<>();
        List<String> dexEntries = new ArrayList<>();

        try {
            dexEntries = apk.getDexEntryNames();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (String dexEntry : dexEntries) {
            try {
                dexFiles.add(apk.getEntry(dexEntry).getDexFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        BaseGraphBuilder builder = new BaseGraphBuilder(GraphType.INTERCFG, dexFiles)
                .withName("global")
                .withAPKFile(apkPath);

        if (useBasicBlocks) {
            builder = builder.withBasicBlocks();
        }

        if (excludeARTClasses) {
            builder = builder.withExcludeARTClasses();
        }

        BaseGraph baseGraph = builder.build();
        return (BaseCFG) baseGraph;
    }

}
