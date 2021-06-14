package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class Utility {

    // the dalvik bytecode level (Android API version)
    public static final Opcodes API_OPCODE = Opcodes.forApi(28);

    public static final String EXCLUSION_PATTERN_FILE = "exclude.txt";
    private static final Logger LOGGER = LogManager.getLogger(Utility.class);

    private Utility() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Checks whether the given method represents a reflection call.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if the method refers to a reflection call,
     * otherwise {@code false} is returned.
     */
    public static boolean isReflectionCall(String methodSignature) {
        return methodSignature.equals("Ljava/lang/Class;->newInstance()Ljava/lang/Object;");
    }

    /**
     * Removes the given file, can be either a directory a simple file.
     *
     * @param toBeRemoved The file to be removed.
     * @return Returns {@code true} if removing file succeeded, otherwise {@code false} is returned.
     */
    public static boolean removeFile(final File toBeRemoved) {
        try {
            // FIXME: Certain files can't be properly deleted. This seems to be caused by some interplay between
            //  the decoding of the APK and the removal afterwards. Those files seem to be locked temporarily.
            FileUtils.forceDelete(toBeRemoved);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Couldn't remove file: " + toBeRemoved);
            LOGGER.warn(e.getMessage());
            return false;
        }
    }

    /**
     * Returns the dex files in the given {@param directory}.
     *
     * @param directory The directory to search for the dex files.
     * @return Returns a list of dex files found in the given directory.
     */
    @SuppressWarnings("unused")
    public static File[] getDexFiles(final File directory) {

        File[] matches = directory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("classes") && name.endsWith(".dex");
            }
        });
        return matches;
    }

    /**
     * Checks for a call to setContentView() or inflate() respectively and retrieves the layout resource id
     * associated with the layout file.
     *
     * @param classDef            The class defining the invocation.
     * @param analyzedInstruction The instruction referring to an invocation of setContentView() or inflate().
     * @return Returns the layout resource for the given class (if any).
     */
    public static String getLayoutResourceID(final ClassDef classDef, final AnalyzedInstruction analyzedInstruction) {

        Instruction35c invokeVirtual = (Instruction35c) analyzedInstruction.getInstruction();
        String methodReference = invokeVirtual.getReference().toString();

        if (methodReference.endsWith("setContentView(I)V")
                // ensure that setContentView() refers to the given class
                && classDef.toString().equals(MethodUtils.getClassName(methodReference))) {
            // TODO: there are multiple overloaded setContentView() implementations
            // we assume here only setContentView(int layoutResID)
            // link: https://developer.android.com/reference/android/app/Activity.html#setContentView(int)

            /*
             * We need to find the resource id located in one of the registers. A typical call to
             * setContentView(int layoutResID) looks as follows:
             *     invoke-virtual {p0, v0}, Lcom/zola/bmi/BMIMain;->setContentView(I)V
             * Here, v0 contains the resource id, thus we need to search backwards for the last
             * change of v0. This is typically the previous instruction and is of type 'const'.
             */

            // the id of the register, which contains the layoutResID
            int layoutResIDRegister = invokeVirtual.getRegisterD();

            boolean foundLayoutResID = false;
            assert !analyzedInstruction.getPredecessors().isEmpty();
            AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

            while (!foundLayoutResID) {

                Instruction pred = predecessor.getInstruction();

                // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                if (pred instanceof NarrowLiteralInstruction
                        && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                        || pred.getOpcode() == Opcode.CONST_16 || pred.getOpcode() == Opcode.CONST_HIGH16)
                        && predecessor.setsRegister(layoutResIDRegister)) {
                    int resourceID = ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                    return "0x" + Integer.toHexString(resourceID);
                }

                if (predecessor.getPredecessors().isEmpty()) {
                    // couldn't find layout resource id
                    LOGGER.warn("Couldn't derive resource ID for class " + classDef);
                    return null;
                } else {
                    predecessor = predecessor.getPredecessors().first();
                }
            }
        } else if (methodReference.endsWith("setContentView(Landroid/view/View;)V")
                // ensure that setContentView() refers to the given class
                && classDef.toString().equals(MethodUtils.getClassName(methodReference))) {

            /*
             * A typical example of this call looks as follows:
             * invoke-virtual {v2, v3}, Landroid/widget/PopupWindow;->setContentView(Landroid/view/View;)V
             *
             * Here, register v2 is the PopupWindow instance while v3 refers to the View object param.
             * Thus, we need to search for the call of setContentView/inflate() on the View object
             * in order to retrieve its layout resource ID.
             */
            LOGGER.warn("Couldn't derive resource ID for class " + classDef);

            /*
             * TODO: are we interested in calls to setContentView(..) that don't refer to the this object?
             * The primary goal is to derive the layout ID of a given component (class). However, it seems
             * like classes (components) can define the layout of other (sub) components. Are we interested
             * in getting the layout ID of those (sub) components?
             */

            // we need to resolve the layout ID of the given View object parameter

        } else if (methodReference.contains("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z")) {
            // TODO: there are multiple overloaded inflate() implementations
            // see: https://developer.android.com/reference/android/view/LayoutInflater.html#inflate(org.xmlpull.v1.XmlPullParser,%20android.view.ViewGroup,%20boolean)
            // we assume here inflate(int resource,ViewGroup root, boolean attachToRoot)

            /*
             * A typical call of inflate(int resource,ViewGroup root, boolean attachToRoot) looks as follows:
             *   invoke-virtual {p1, v0, p2, v1}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;
             * Here, v0 contains the resource id, thus we need to search backwards for the last change of v0.
             * This is typically the previous instruction and is of type 'const'.
             */

            // the id of the register, which contains the layoutResID
            int layoutResIDRegister = invokeVirtual.getRegisterD();

            boolean foundLayoutResID = false;
            assert !analyzedInstruction.getPredecessors().isEmpty();
            AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

            while (!foundLayoutResID) {

                Instruction pred = predecessor.getInstruction();

                // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                if (pred instanceof NarrowLiteralInstruction
                        && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                        || pred.getOpcode() == Opcode.CONST_16 || pred.getOpcode() == Opcode.CONST_HIGH16)
                        && predecessor.setsRegister(layoutResIDRegister)) {
                    int resourceID = ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                    return "0x" + Integer.toHexString(resourceID);
                }

                if (predecessor.getPredecessors().isEmpty()) {
                    // couldn't find layout resource id
                    LOGGER.warn("Couldn't derive resource ID for class " + classDef);
                    return null;
                } else {
                    predecessor = predecessor.getPredecessors().first();
                }
            }
        } else if (methodReference.contains("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;")) {
            LOGGER.warn("Couldn't derive resource ID for class " + classDef);
        } else if (methodReference.contains("Landroid/view/LayoutInflater;->" +
                "inflate(Lorg/xmlpull/v1/XmlPullParser;Landroid/view/ViewGroup;")) {
            LOGGER.warn("Couldn't derive resource ID for class " + classDef);
        } else if (methodReference.contains("Landroid/view/LayoutInflater;->" +
                "inflate(Lorg/xmlpull/v1/XmlPullParser;Landroid/view/ViewGroup;Z")) {
            LOGGER.warn("Couldn't derive resource ID for class " + classDef);
        }
        return null;
    }

    /**
     * Returns the object type of a given reference. A typical reference looks as follows:
     * <p>
     * Lcom/base/application/MainActivity;->serviceConnection:Lcom/base/application/MainActivity$MyServiceConnection;
     * <p>
     * The above example represents a reference to the instance variable 'serviceConnection' of the 'MainActivity'
     * class, where the object type (class) is 'MyServiceConnection'.
     *
     * @param reference The reference.
     * @return Returns the object type (class) of the reference.
     */
    public static String getObjectType(String reference) {
        return reference.split(":")[1];
    }

    // TODO: Check whether there is any difference to method.toString()!

    /**
     * Generates patterns of classes which should be excluded from the instrumentation.
     *
     * @return The pattern representing classes that should not be instrumented.
     */
    public static Pattern readExcludePatterns() {

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(EXCLUSION_PATTERN_FILE);

        if (inputStream == null) {
            LOGGER.warn("Couldn't find exclusion file!");
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
     * Checks whether a block statement contains an invoke instruction.
     *
     * @param blockStatement The block statement to be checked.
     * @return Returns {@code true} if the block statement contains an
     * invoke instruction, otherwise {@code false} is returned.
     */
    public static boolean containsInvoke(final BlockStatement blockStatement) {

        for (Statement statement : blockStatement.getStatements()) {
            if (statement instanceof BasicStatement) {
                if (InstructionUtils.isInvokeInstruction(((BasicStatement) statement).getInstruction())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Backtracks a reflection call, i.e. newInstance(), in order to get the invoked class.
     *
     * @param apk The APK file.
     * @param invokeStmt The invoke statement representing the reflection call.
     * @return Returns the class name that is invoked by the reflection call, or {@code null}
     *          if the class can't be derived.
     */
    public static String backtrackReflectionCall(final APK apk, final BasicStatement invokeStmt) {

        LOGGER.debug("Backtracking reflection call in method: " + invokeStmt.getMethod());

        /*
        * A call looks as follows:
        *       invoke-virtual {v0}, Ljava/lang/Class;->newInstance()Ljava/lang/Object;
        *
        * Hence, we need to backtrack the register v0 (C) for its last write. Typically, this is
        * a const-class instruction, but in case the specified register is a parameter register,
        * we need to backtrack the method invocation.
         */
        AnalyzedInstruction analyzedInstruction = invokeStmt.getInstruction();
        int registerC = ((Instruction35c) analyzedInstruction.getInstruction()).getRegisterC();
        AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

        while (predecessor.getInstructionIndex() != -1) {
            // check for const class instruction
            if (predecessor.getInstruction().getOpcode() == Opcode.CONST_CLASS && predecessor.setsRegister(registerC)) {
                Instruction21c constClassInstruction = (Instruction21c) predecessor.getInstruction();
                return constClassInstruction.getReference().toString();
            } else {
                predecessor = predecessor.getPredecessors().first();
            }
        }

        /*
        * If there is no const-class instruction, we need to check the method parameters next.
        * The method parameter referring to the class type can be either primitive or generic.
        * In the former case, the type can be extracted directly, otherwise the annotations
        * need to be inspected.
         */
        Optional<Method> method = MethodUtils.searchForTargetMethod(apk, invokeStmt.getMethod());
        if (method.isEmpty()) {
            LOGGER.warn("Method " + invokeStmt.getMethod() + " not present in dex files!");
            return null;
        }

        Method targetMethod = method.get();

        // extract the method parameter referring to the specified register in the invoke instruction
        int localRegisters = MethodUtils.getLocalRegisterCount(targetMethod);
        int paramRegisterIndex = registerC - localRegisters;

        // the param registers don't contain p0 (implicit parameter)
        if (paramRegisterIndex == 0) {
            // return class corresponding to 'this' reference
            return MethodUtils.getClassName(invokeStmt.getMethod());
        } else {

            // param registers start at p1
            paramRegisterIndex = paramRegisterIndex - 1;

            MethodParameter parameter = targetMethod.getParameters().get(paramRegisterIndex);
            LOGGER.debug("Type: " + parameter.getType());

            if (parameter.getType().equals("Ljava/lang/Class;")) {

                AtomicReference<String> clazz = new AtomicReference<>(null);

                LOGGER.debug("Inspecting method annotations!");
                int finalParamRegisterIndex = paramRegisterIndex;
                targetMethod.getAnnotations().forEach(annotation -> {
                    annotation.getElements().forEach(annotationElement -> {

                        LOGGER.debug("Annotation element value: " + annotationElement.getValue());

                        /*
                        * An annotation value is nothing else than a string array of the following form:
                        * Array["(", "Landroid/support/v4/app/FragmentActivity;",
                        *       "Ljava/lang/Class<", "+", "Lcom/woefe/shoppinglist/dialog/TextInputDialog;", ">;)V"]
                        *
                        * We need to remove the 'Array[]' wrapper and split it into tokens. Afterwards, we can
                        * assign those annotations to the individual method parameters. In particular, every
                        * annotation starts with a 'L' symbol and ends with ';' like a regular dex class name does.
                        *
                        * NOTE: According to https://source.android.com/devices/tech/dalvik/dex-format#dalvik-signature,
                        * the signature has no particular format, it's sole purpose is meant for debuggers.
                         */
                        String annotationValue = annotationElement.getValue().toString().replaceAll("\"", "");
                        String[] tokens = annotationValue
                                .substring("Array[".length() + 1, annotationValue.lastIndexOf("]") + 1)
                                .split(", ");

                        List<String> parameterAnnotations = new ArrayList<>();
                        int lastIndex = 0;
                        boolean generic = false;
                        for (String token : tokens) {
                            LOGGER.debug("Token: " + token);
                            if (generic) {
                                if (token.startsWith(">;")) {
                                    String lastValue = parameterAnnotations.get(lastIndex);
                                    parameterAnnotations.remove(lastIndex);
                                    parameterAnnotations.add(lastIndex, lastValue
                                            + token.substring(0, token.indexOf(";") + 1));
                                    generic = false;
                                    lastIndex++;
                                } else {
                                    String lastValue = parameterAnnotations.get(lastIndex);
                                    parameterAnnotations.remove(lastIndex);
                                    parameterAnnotations.add(lastIndex, lastValue + token);
                                }
                            } else if (token.startsWith("L") && token.endsWith(";")) {
                                parameterAnnotations.add(lastIndex, token);
                                lastIndex++;
                            } else if (token.startsWith("L") && token.endsWith("<")) {
                                parameterAnnotations.add(lastIndex, token);
                                generic = true;
                            }
                        }

                        LOGGER.debug("Parsed parameter annotations: " + parameterAnnotations);
                        String parameterAnnotation = parameterAnnotations.get(finalParamRegisterIndex);

                        /*
                        * TODO: We need to consider sub classes as well. A generic type information may
                        *  refer to some base class, but the underlying method might be called with some
                        *  sub type as parameter. The control-flow graph should actually insert edges
                        *  for every sub class, i.e. an edge from newInstance() to each sub class constructor.
                         */
                        if (parameterAnnotation.contains("Ljava/lang/Class<") && parameterAnnotation.contains(">")) {
                            // generic type annotation
                            String genericType = parameterAnnotation.split("Ljava/lang/Class<")[1].split(">")[0];
                            LOGGER.debug("Generic type information: " + genericType);
                            // remove optional attributes, e.g. '+'
                            genericType = genericType.substring(genericType.indexOf('L'));
                            clazz.set(genericType);
                        } else {
                            clazz.set(parameterAnnotation);
                        }
                    });
                });
                if (clazz.get() != null) {
                    return clazz.get();
                } else {
                    LOGGER.warn("No method annotations present!");
                    // TODO: check method usages and backtrack the concrete parameters
                }
            } else {
                return parameter.getType();
            }
        }
        return null;
    }

}
