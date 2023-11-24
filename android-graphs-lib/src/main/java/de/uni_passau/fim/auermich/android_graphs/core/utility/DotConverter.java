package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGVertex;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.ReturnStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;

/**
 * A converter from a {@link CFGVertex} to a dot node or label, respectively.
 */
public class DotConverter {
    /**
     * Converts a vertex into a DOT node matching the dot language specification.
     *
     * @param vertex The vertex to be converted.
     * @return Returns a DOT node representing the given vertex.
     */
    public static String convertVertexToDOTNode(final CFGVertex vertex) {

        /*
         * TODO: Simplify the entire conversion process. We need to comply to the following specification:
         *  https://graphviz.org/doc/info/lang.html. In particular, this holds true for the node identifiers,
         *  while the label can be assigned any name.
         */

        // TODO: handle vertices used for non-basic inter CFG

        String methodSignature = vertex.getMethod();

        if (methodSignature.equals("global")) {
            if (vertex.isEntryVertex()) {
                return "entry_" + methodSignature;
            } else {
                return "exit_" + methodSignature;
            }
        }

        if (methodSignature.equals("static initializers")) {
            if (vertex.isEntryVertex()) {
                return "entry_static_initializers";
            } else {
                return "exit_static_initializers";
            }
        }

        if (methodSignature.startsWith("callbacks")) {
            String className = methodSignature.split("callbacks")[1].trim();
            if (vertex.isEntryVertex()) {
                return "<entry_callbacks_" + className + ">";
            } else {
                return "<exit_callbacks_" + className + ">";
            }
        }

        if (methodSignature.endsWith("sendBroadcast()")) {
            String[] tokens = methodSignature.split("->");

            String className = ClassUtils.dottedClassName(tokens[0])
                    .replace("$", "_")
                    .replace(".", "_");

            String methodName = tokens[1].split("\\(")[0].replace("<init>", "ctr");
            ;
            String instructionIndex = tokens[2];

            if (vertex.isEntryVertex()) {
                return "<entry_" + className + "_" + methodName + "_" + instructionIndex + "_sendBroadcast>";
            } else {
                return "<exit_" + className + "_" + methodName + "_" + instructionIndex + "_sendBroadcast>";
            }
        }

        String className = ClassUtils.dottedClassName(MethodUtils.getClassName(methodSignature))
                .replace("$", "_").replace(".", "_");
        String method = MethodUtils.getMethodName(methodSignature).split("\\(")[0];
        method = method.replace("<init>", "ctr");

        String label = "";
        String signature = className + "_" + method;

        if (vertex.isEntryVertex()) {
            label = "entry_" + signature;
        } else if (vertex.isExitVertex()) {
            label = "exit_" + signature;
        } else if (vertex.isReturnVertex()) {
            label = "return_" + signature;
        } else {
            BlockStatement blockStatement = (BlockStatement) vertex.getStatement();
            Statement firstStmt = blockStatement.getFirstStatement();
            Statement lastStmt = blockStatement.getLastStatement();
            Integer begin = null;
            Integer end = null;

            if (firstStmt instanceof ReturnStatement) {

                if (blockStatement.getStatements().size() == 1) {
                    // isolated return vertex, no begin and end index
                    return "<return_" + signature + ">";
                }

                BasicStatement second = (BasicStatement) blockStatement.getStatements().get(1);
                begin = second.getInstructionIndex();

            } else {
                // must be a basic block statement
                begin = ((BasicStatement) firstStmt).getInstructionIndex();
            }

            end = ((BasicStatement) lastStmt).getInstructionIndex();
            label = signature + "_" + begin + "_" + end;
        }

        // DOT supports html tags and those seem to require little to no escaping inside
        return "<" + label + ">";
    }

    /**
     * Converts a vertex into a DOT label. This label is what we see when
     * we render the graph.
     *
     * @param vertex The vertex to be converted into a DOT label.
     * @return Returns the DOT label representing the given vertex.
     */
    public static String convertVertexToDOTLabel(final CFGVertex vertex) {

        // TODO: handle vertices used for non-basic inter CFG

        // TODO: Display the instructions actually instead of solely the instruction indices.
        String label = "";
        String methodSignature = vertex.getMethod();

        if (methodSignature.equals("global")) {
            if (vertex.isEntryVertex()) {
                label = "entry_global";
            } else {
                label = "exit_global";
            }
        } else if (methodSignature.startsWith("callbacks")) {
            if (vertex.isEntryVertex()) {
                label = "entry_callbacks";
            } else {
                label = "exit_callbacks";
            }
        } else if (methodSignature.startsWith("static initializers")) {
            if (vertex.isEntryVertex()) {
                label = "entry_static_initializers";
            } else {
                label = "exit_static_initializers";
            }
        } else if (methodSignature.endsWith("sendBroadcast()")) {
            String[] tokens = methodSignature.split("->");

            String className = ClassUtils.dottedClassName(tokens[0])
                    .replace("$", "_")
                    .replace(".", "_");

            String methodName = tokens[1].split("\\(")[0].replace("<init>", "ctr");
            ;
            String instructionIndex = tokens[2];

            if (vertex.isEntryVertex()) {
                label = "<entry_" + className + "_" + methodName + "_" + instructionIndex + "_sendBroadcast>";
            } else {
                label = "<exit_" + className + "_" + methodName + "_" + instructionIndex + "_sendBroadcast>";
            }
        } else {

            String className = ClassUtils.dottedClassName(MethodUtils.getClassName(methodSignature));
            String method = MethodUtils.getMethodName(methodSignature).split("\\(")[0];
            String signature = "<" + className + "_" + method + ">";

            if (vertex.isEntryVertex()) {
                label = "entry_" + signature;
            } else if (vertex.isExitVertex()) {
                label = "exit_" + signature;
            } else if (vertex.isReturnVertex()) {
                label = "return_" + signature;
            } else {
                BlockStatement blockStatement = (BlockStatement) vertex.getStatement();
                Statement firstStmt = blockStatement.getFirstStatement();
                Statement lastStmt = blockStatement.getLastStatement();
                Integer begin = null;
                Integer end = null;

                if (firstStmt instanceof ReturnStatement) {

                    if (blockStatement.getStatements().size() == 1) {
                        // isolated return statement
                        label = "return_" + signature;
                    } else {
                        BasicStatement second = (BasicStatement) blockStatement.getStatements().get(1);
                        begin = second.getInstructionIndex();
                    }
                } else {
                    // must be a basic block statement
                    begin = ((BasicStatement) firstStmt).getInstructionIndex();
                }

                if (blockStatement.getStatements().size() > 1) {
                    // only check for last index if vertex is not an isolated return vertex
                    end = ((BasicStatement) lastStmt).getInstructionIndex();
                    label = signature + "_" + begin + "_" + end;
                }
            }
        }
        return label;
    }
}
