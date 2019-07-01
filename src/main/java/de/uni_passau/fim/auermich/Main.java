package de.uni_passau.fim.auermich;

import com.beust.jcommander.JCommander;
import com.google.common.collect.Lists;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import de.uni_passau.fim.auermich.graphs.cfg.IntraProceduralCFG;
import de.uni_passau.fim.auermich.jcommander.InterCFGCommand;
import de.uni_passau.fim.auermich.jcommander.IntraCFGCommand;
import de.uni_passau.fim.auermich.jcommander.MainCommand;
import de.uni_passau.fim.auermich.utility.Utility;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.DexClassProvider;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    // the dalvik bytecode level (Android API version)
    public static final Opcodes API_OPCODE = Opcodes.forApi(28);

    // the set of possible commands
    private static final MainCommand mainCmd = new MainCommand();
    private static final InterCFGCommand interCFGCmd = new InterCFGCommand();
    private static final IntraCFGCommand intraCFGCmd = new IntraCFGCommand();

    private Main() {
        throw new UnsupportedOperationException("Utility class!");
    }

    public static void main(String[] args) throws IOException {

        /*
        * TODO: allow custom order of arguments
        * The current implementation only allows to specify
        * the cmd-line args in a pre-defined order, i.e.
        * first comes the option of the main command
        * and afterwards the remaining arguments of
        * sub-commands like 'intra'. However, we would
        * like to specify the main arguments in any order
        * without having to define any prefix.
         */

        JCommander commander = JCommander.newBuilder()
                .addObject(mainCmd)
                .addCommand("intra", intraCFGCmd)
                .addCommand("inter", interCFGCmd)
                .build();

        // the program name displayed in the help/usage cmd.
        commander.setProgramName("Android-Graphs");

        // parse command line arguments
        commander.parse(args);

        LOGGER.debug("Command input: " + commander.getParsedCommand());

        // determine which logging level should be used
        if(mainCmd.isDebug()) {
            LOGGER.debug("Debug mode is enabled!");
            Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.DEBUG);
        } else {
            Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.INFO);
        }

        // check whether help command is executed
        if (mainCmd.isHelp()) {
            commander.usage();
        } else {
            boolean exceptionalFlow = false;

            // check whether we want to model edges from try-catch blocks
            if (mainCmd.isExceptionalFlow()) {
                exceptionalFlow = true;
            }

            // process apk and construct desired graph
            run(commander, exceptionalFlow);
        }
    }

    /**
     * Verifies that the given arguments are valid.
     * @param cmd
     */
    private static boolean checkArguments(IntraCFGCommand cmd) {
        assert cmd.getGraphType() == GraphType.INTRACFG;
        // Objects.requireNonNull(cmd.getMetric());
        // Objects.requireNonNull(cmd.getTarget());
        return true;
    }

    /**
     *
     * @param cmd
     */
    private static boolean checkArguments(InterCFGCommand cmd) {
        assert cmd.getGraphType() == GraphType.INTERCFG;
        Objects.requireNonNull(cmd.getMetric());
        return true;
    }


    private static void run(JCommander commander, boolean exceptionalFlow) throws IOException {

        /*
        * TODO: define some result data type
        * We basically want to return something, e.g. a distance between two nodes. This
        * should be stored in some result data type. Since mandatory options are missing
        * potentially, we may want to return an empty result -> Optional.
         */

        LOGGER.debug("Determining which action to take dependent on given command");

        LOGGER.info(mainCmd.getDexFile().getAbsolutePath());

        if (!mainCmd.getDexFile().exists()) {
            LOGGER.warn("Path to classes.dex file not valid!");
            return;
        }

        String selectedCommand = commander.getParsedCommand();
        Optional<GraphType> graphType = GraphType.fromString(selectedCommand);

        if (!graphType.isPresent()) {
            LOGGER.warn("Enter a valid command please!");
            commander.usage();
        } else {

            DexFile dexFile = DexFileFactory.loadDexFile(mainCmd.getDexFile(), API_OPCODE);

            // determine which sub-commando was executed
            switch (graphType.get()) {
                case INTRACFG:
                    if(checkArguments(intraCFGCmd)) {
                        computeIntraProceduralCFG(dexFile, intraCFGCmd.getTarget());
                    }
                    break;
                case INTERCFG:
                    if(checkArguments(interCFGCmd)) {
                        computeInterProceduralCFG(dexFile);
                    }
                    break;
            }
        }
    }

    /*
    * TODO: add params for metric and return some result datatype
    *
     */

    private static void computeInterProceduralCFG(DexFile dexFile) {

    }

    private static void computeIntraProceduralCFG(DexFile dexFile, String methodName) {

        Optional<Method> method = Utility.searchForTargetMethod(dexFile, methodName);

        if (!method.isPresent()) {
            LOGGER.warn("Couldn't find target method! Provide the fully-qualified name!");
        } else {

            IntraProceduralCFG cfg = new IntraProceduralCFG(methodName);

            LOGGER.info("Method: " + methodName);

            Method targetMethod = method.get();
            MethodImplementation methodImplementation = targetMethod.getImplementation();
            List<Instruction> instructions = Lists.newArrayList(methodImplementation.getInstructions());

            MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                    true, ClassPath.NOT_ART), targetMethod,
                    null, false);

            List<AnalyzedInstruction> analyzedInstructions = analyzer.getAnalyzedInstructions();

            List<Vertex> vertices = new ArrayList<>();

            // pre-create vertices
            for(int index=0; index < instructions.size(); index++) {
                Vertex vertex = new Vertex(index, instructions.get(index));
                cfg.addVertex(vertex);
                vertices.add(vertex);
            }

            for(int index=0; index < instructions.size(); index++) {

                LOGGER.info("Instruction: " + vertices.get(index));

                Instruction instruction = instructions.get(index);
                AnalyzedInstruction analyzedInstruction = analyzedInstructions.get(index);

                // the current instruction represented as vertex
                Vertex vertex = vertices.get(index);

                // special treatment for first instruction (virtual entry node as predecessor)
                if (analyzedInstruction.isBeginningInstruction()) {
                    cfg.addEdge(cfg.getEntry(),vertex);
                }

                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();

                Iterator<AnalyzedInstruction> iterator = predecessors.iterator();

                // add for each predecessor an incoming edge to the current vertex
                while (iterator.hasNext()) {
                    AnalyzedInstruction predecessor = iterator.next();
                    Vertex src = new Vertex(predecessor.getInstructionIndex(), predecessor.getInstruction());
                    LOGGER.info("Predecessor: " + src);
                    if (!src.isEntryVertex()) {
                        cfg.addEdge(src, vertex);
                    }
                }

                List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();

                if (successors.isEmpty()) {
                    // must be a return statement, thus we need to insert an edge to the exit vertex
                    cfg.addEdge(vertex, cfg.getExit());
                } else {
                    // add for each successor an outgoing each from the current vertex
                    for (AnalyzedInstruction successor: successors) {
                        Vertex dest = new Vertex(successor.getInstructionIndex(), successor.getInstruction());
                        LOGGER.info("Successor: " + dest);
                        cfg.addEdge(vertex, dest);
                    }
                }

                /*
                switch (instruction.getOpcode()) {
                    case IF_EQ:
                    case IF_EQZ:
                    case IF_GE:
                    case IF_GEZ:
                    case IF_GT:
                    case IF_GTZ:
                    case IF_LE:
                    case IF_LEZ:
                    case IF_LT:
                    case IF_LTZ:
                    case IF_NE:
                    case IF_NEZ:
                        break;
                    case GOTO:
                    case GOTO_16:
                    case GOTO_32:
                        break;

                        default:

                }
                */
            }
            LOGGER.info(cfg.toString());
        }
    }
}
