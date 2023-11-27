package de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.*;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.LayoutFile;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.Manifest;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.ReturnStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;
import de.uni_passau.fim.auermich.android_graphs.core.utility.Properties;
import de.uni_passau.fim.auermich.android_graphs.core.utility.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents an inter procedural control flow graph.
 */
public class InterCFG extends BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(InterCFG.class);
    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    /**
     * Properties relevant for the construction process, e.g. whether basic blocks should be used.
     */
    private Properties properties;

    /**
     * Maintains the set of components, i.e. activities, services and fragments.
     */
    private final Set<Component> components = new HashSet<>();

    /**
     * Maintains the class relation between the application classes in both directions.
     * This includes the super class, the interfaces and the sub classes of a class.
     */
    private final ClassHierarchy classHierarchy = new ClassHierarchy();

    /**
     * Maintains a reference to the individual intra CFGs.
     * NOTE: Only a reference to the entry and exit vertex is hold!
     */
    private final Map<String, BaseCFG> intraCFGs = new HashMap<>();

    /**
     * The APK file.
     */
    private APK apk = null;

    // the set of discovered Android callbacks
    private final Set<String> callbacks = new HashSet<>();

    // necessary for the copy constructor
    public InterCFG(String graphName) {
        super(graphName);
    }

    public InterCFG(String graphName, APK apk, boolean useBasicBlocks,
                    boolean excludeARTClasses, boolean resolveOnlyAUTClasses) {
        super(graphName);
        this.properties = new Properties(useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
        this.apk = apk;
        constructCFG(apk);
        removeDisconnectedVertices(); // ensures that lookup fails for disconnected vertices
    }

    /**
     * Returns the class hierarchy among the application classes.
     *
     * @return Returns the class hierarchy.
     */
    public ClassHierarchy getClassHierarchy() {
        return classHierarchy;
    }

    /**
     * Returns the APK.
     *
     * @return Returns the APK.
     */
    public APK getApk() {
        return apk;
    }

    /**
     * Returns the set of discovered components.
     *
     * @return Returns the components.
     */
    public Set<Component> getComponents() {
        return Collections.unmodifiableSet(components);
    }

    /**
     * Retrieves the mapping to the intra CFGs.
     *
     * @return Returns a mapping to the entry and exit vertices of the individual intra CFGs.
     */
    public Map<String, BaseCFG> getIntraCFGs() {
        return Collections.unmodifiableMap(intraCFGs);
    }

    private void constructCFG(APK apk) {

        // decode APK to access manifest and other resource files
        apk.decodeAPK();

        // parse manifest
        apk.setManifest(Manifest.parse(new File(apk.getDecodingOutputPath(), "AndroidManifest.xml")));

        // parse the resource strings
        apk.setResourceStrings(ResourceUtils.parseStringsXMLFile(apk.getDecodingOutputPath()));

        // create the individual intraCFGs and add them as sub graphs
        constructIntraCFGs(apk, properties.useBasicBlocks);

        // track relations between components
        ComponentUtils.checkComponentRelations(apk, components, classHierarchy);

        // add for each component a callback graph
        Map<String, BaseCFG> callbackGraphs = addCallbackGraphs();

        // add lifecycle of components + global entry points
        addLifecycleAndGlobalEntryPoints(callbackGraphs);

        // add the callbacks specified either through XML or directly in code
        addCallbacks(apk, callbackGraphs);

        LOGGER.debug("Removing decoded APK files: " + Utility.removeFile(apk.getDecodingOutputPath()));

        if (properties.useBasicBlocks) {
            constructCFGWithBasicBlocks(apk);
        } else {
            constructCFGNoBasicBlocks(apk);
        }
    }

    /**
     * Removes all vertices that are not reachable, i.e. that are not connected with the entry node of the CFG.
     */
    private void removeDisconnectedVertices() {
        final Set<CFGVertex> reachableByEntry = (Set<CFGVertex>) getTransitiveSuccessors(getEntry());
        final Set<CFGVertex> toDelete = getVertices()
                .stream()
                .filter(vertex -> !vertex.equals(getEntry()) && !reachableByEntry.contains(vertex))
                .collect(Collectors.toSet());
        // We basically remove here a complete subgraph and thus need to update the intraCFGs reference, otherwise a
        // lookup of a trace will succeed although the actual vertex has been removed.
        toDelete.stream().forEach(vertex -> {
            LOGGER.debug("Removing method: " + vertex.getMethod());
            intraCFGs.remove(vertex.getMethod());
        });
        graph.removeAllVertices(toDelete);
    }

    /**
     * Constructs the inter CFG using basic blocks for a given app.
     *
     * @param apk The APK file describing the app.
     */
    private void constructCFGWithBasicBlocks(APK apk) {

        LOGGER.debug("Constructing Inter CFG with basic blocks!");

        final String packageName = apk.getManifest().getPackageName();

        // resolve the invoke vertices and connect the sub graphs with each other
        for (CFGVertex invokeVertex : getInvokeVertices()) {

            BlockStatement blockStatement = (BlockStatement) invokeVertex.getStatement();

            // split vertex into blocks (split after each invoke instruction + insert virtual return statement)
            List<List<Statement>> blocks = splitBlockStatement(blockStatement, packageName);

            if (blocks.size() == 1) {
                LOGGER.debug("Unchanged vertex: " + invokeVertex + " [" + invokeVertex.getMethod() + "]");
                // the vertex is not split, no need to delete and re-insert the vertex
                continue;
            }

            LOGGER.debug("Invoke Vertex: " + invokeVertex + " [" + invokeVertex.getMethod() + "]");

            // save the predecessors and successors as we remove the vertex
            Set<CFGVertex> predecessors = getIncomingEdges(invokeVertex).stream().map(CFGEdge::getSource).collect(Collectors.toSet());
            Set<CFGVertex> successors = getOutgoingEdges(invokeVertex).stream().map(CFGEdge::getTarget).collect(Collectors.toSet());

            // remove original vertex, inherently removes edges
            removeVertex(invokeVertex);

            List<CFGVertex> blockVertices = new ArrayList<>();
            List<List<CFGVertex>> exitVertices = new ArrayList<>();

            for (int i = 0; i < blocks.size(); i++) {

                // create a new vertex for each block
                List<Statement> block = blocks.get(i);
                Statement blockStmt = new BlockStatement(invokeVertex.getMethod(), block);
                CFGVertex blockVertex = new CFGVertex(blockStmt);
                blockVertices.add(blockVertex);

                // add modified block vertex to graph
                addVertex(blockVertex);

                // first block, add original predecessors to first block
                if (i == 0) {
                    for (CFGVertex predecessor : predecessors) {
                        // handle self-references afterwards
                        if (!predecessor.equals(invokeVertex)) {
                            addEdge(predecessor, blockVertex);
                        }
                    }
                }

                // last block, add original successors to the last block
                if (i == blocks.size() - 1) {
                    // LOGGER.debug("Number of successors: " + successors.size());
                    for (CFGVertex successor : successors) {
                        // handle self-references afterwards
                        if (!successor.equals(invokeVertex)) {
                            addEdge(blockVertex, successor);
                        }
                    }
                    // the last block doesn't contain any invoke instruction -> no target CFG
                    break;
                }

                // look up the CFGs matching the invocation target (multiple for overridden methods)
                BasicStatement invokeStmt = (BasicStatement) ((BlockStatement) blockStmt).getLastStatement();
                Set<BaseCFG> targetCFGs = lookupTargetCFGs(apk, invokeStmt);

                // the invoke vertex defines an edge to each target CFG (invocation target)
                targetCFGs.forEach(targetCFG -> addEdge(blockVertex, targetCFG.getEntry()));

                // there is an edge from each target CFC's exit vertex to the virtual return statement (next block)
                exitVertices.add(targetCFGs.stream().map(BaseCFG::getExit).collect(Collectors.toList()));
            }

            // connect each target CFC's exit with the corresponding virtual return vertex
            for (int i = 0; i < exitVertices.size(); i++) {
                for (CFGVertex exitVertex : exitVertices.get(i)) {
                    addEdge(exitVertex, blockVertices.get(i + 1));
                }
            }

            /*
             * If an invoke vertex defines a self-reference, it appears as both predecessor and successor.
             * However, as we split the vertex after each invoke statement, the self-reference gets corrupted.
             * Thus, we add only those predecessors and successors that don't constitute a self-reference,
             * and handle self-references afterwards. We only need to add a single edge between the last block
             * and the first block of the original invoke vertex.
             */
            if (predecessors.contains(invokeVertex) || successors.contains(invokeVertex)) {
                LOGGER.debug("Self-Reference for vertex: " + invokeVertex);
                // add edge from last block to first block
                addEdge(blockVertices.get(blockVertices.size() - 1), blockVertices.get(0));
            }
        }
    }

    /**
     * Adds for each component except fragments and broadcast receivers a callback entry point.
     *
     * @return Returns a mapping of the components and its callback entry point.
     */
    private Map<String, BaseCFG> addCallbackGraphs() {

        LOGGER.debug("Adding callbacks sub graphs...");

        Map<String, BaseCFG> callbackGraphs = new HashMap<>();

        /*
         * Fragments share the callback entry point with the surrounding activity, while broadcast receivers
         * do not define any callbacks at all.
         */
        components.stream().filter(c -> c.getComponentType() != ComponentType.FRAGMENT
                && c.getComponentType() != ComponentType.BROADCAST_RECEIVER).forEach(component -> {
            BaseCFG callbackGraph = dummyIntraCFG("callbacks " + component.getName());
            addSubGraph(callbackGraph);
            callbackGraphs.put(component.getName(), callbackGraph);
        });
        return callbackGraphs;
    }

    /**
     * Looks up the target CFG matching the invoke statement. As a side effect, certain invocations are resolved, e.g.
     * a call to startActivity() links the call site to the constructor of the respective activity.
     *
     * @param apk        The APK file.
     * @param invokeStmt The invoke statement defining the target.
     * @return Returns the CFGs matching the given invoke target.
     */
    private Set<BaseCFG> lookupTargetCFGs(final APK apk, final BasicStatement invokeStmt) {

        final Set<BaseCFG> targetCFGs = new HashSet<>();

        final Instruction instruction = invokeStmt.getInstruction().getInstruction();
        final String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
        final String callingClass = MethodUtils.getClassName(invokeStmt.getMethod());

        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        LOGGER.debug("Lookup target CFGs for " + targetMethod);

        /*
         * We can't distinguish whether the given method is invoked or any method
         * that overrides the given method. Thus, we need to over-approximate in this case
         * and connect the invoke with each overridden method (CFG) as well.
         */
        final Set<String> overriddenMethods = classHierarchy.getOverriddenMethods(callingClass, targetMethod,
                apk.getManifest().getPackageName(), mainActivityPackage, properties);

        for (String overriddenMethod : overriddenMethods) {

            if (MethodUtils.isLambdaClassConstructorCall(overriddenMethod)) {

                /*
                 * Invocations of lambda constructs and method references are awkwardly handled
                 * at the bytecode level. In particular, the method representing the lambda construct
                 * or method reference is not directly called. Thus, we have to manually link those
                 * methods in the graph. Each lambda class defines next to the constructor exactly
                 * one public method, which defines the actual logic. We link this method to the exit
                 * vertex of the constructor. For more details, see Issue #37.
                 * NOTE: We only consider methods that don't represent Android callbacks, e.g. onClick().
                 * Those callbacks are directly integrated in the callbacks subgraph by another procedure.
                 * Likewise, we don't handle the run method of a lambda class here.
                 */
                ClassDef classDef = classHierarchy.getClass(MethodUtils.getClassName(overriddenMethod));
                assert Lists.newArrayList(classDef.getVirtualMethods()).size() == 1;
                String method = MethodUtils.deriveMethodSignature(classDef.getVirtualMethods().iterator().next());

                if (!MethodUtils.isCallback(method) && !ThreadUtils.isThreadMethod(classHierarchy, method)) {
                    LOGGER.debug("Lambda method: " + method);
                    BaseCFG lambdaConstructor = intraCFGs.get(overriddenMethod);
                    BaseCFG lambdaMethod = intraCFGs.get(method);
                    addEdge(lambdaConstructor.getExit(), lambdaMethod.getEntry());
                    targetCFGs.add(lambdaConstructor);
                } else {
                    // Android callbacks and run methods are handled separately
                    targetCFGs.add(intraCFGs.get(overriddenMethod));
                }
            } else if (ComponentUtils.isComponentInvocation(components, overriddenMethod)) {
                LOGGER.debug("Component invocation detected: " + overriddenMethod);
                /*
                 * If there is a component invocation, e.g. a call to startActivity(), we
                 * replace the targetCFG with the constructor of the respective component.
                 */
                String componentConstructor = ComponentUtils.isComponentInvocation(components, invokeStmt.getInstruction());
                if (componentConstructor != null) {
                    if (intraCFGs.containsKey(componentConstructor)) {
                        targetCFGs.add(intraCFGs.get(componentConstructor));
                    } else {
                        LOGGER.warn("Constructor " + componentConstructor + " not contained in dex files!");
                        targetCFGs.add(dummyCFG(overriddenMethod));
                    }
                } else {
                    LOGGER.warn("Couldn't derive component constructor for method: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (MethodUtils.isReflectionCall(overriddenMethod)) {
                LOGGER.debug("Reflection invocation detected: " + overriddenMethod);

                // replace the reflection call with the internally invoked class constructor
                String clazz = Utility.backtrackReflectionCall(apk, invokeStmt);
                if (clazz != null) {
                    LOGGER.debug("Class invoked by reflection: " + clazz);
                    String constructor = ClassUtils.getDefaultConstructor(clazz);
                    if (intraCFGs.containsKey(constructor)) {
                        targetCFGs.add(intraCFGs.get(constructor));
                    } else {
                        LOGGER.warn("Constructor " + constructor + " not contained in dex files!");
                        targetCFGs.add(dummyCFG(overriddenMethod));
                    }
                } else {
                    LOGGER.warn("Couldn't backtrack reflection call within method: " + invokeStmt.getMethod());
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (DialogUtils.isDialogInvocation(overriddenMethod)) {
                LOGGER.debug("Dialog invocation detected: " + overriddenMethod);

                /*
                 * When any showDialog() method is invoked, the respective onCreateDialog() method
                 * is called. We need to check the activity class itself and super classes for the
                 * implementation of onCreateDialog().
                 */
                final String onCreateDialogMethod = DialogUtils.getOnCreateDialogMethod(overriddenMethod);
                String onCreateDialog = MethodUtils.getClassName(overriddenMethod) + "->" + onCreateDialogMethod;
                onCreateDialog = classHierarchy.invokedByCurrentClassOrAnySuperClass(onCreateDialog);

                if (onCreateDialog != null) {
                    if (intraCFGs.containsKey(onCreateDialog)) {
                        targetCFGs.add(intraCFGs.get(onCreateDialog));
                    } else {
                        LOGGER.warn("Method " + onCreateDialog + " not contained in dex files!");
                        targetCFGs.add(dummyCFG(overriddenMethod));
                    }
                } else {
                    LOGGER.warn("OnCreateDialog() not defined by any class for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (AsyncTaskUtils.isAsyncTaskInvocation(overriddenMethod)) {
                LOGGER.debug("AsyncTask invocation detected: " + overriddenMethod);

                /*
                 * When an AsyncTask is started by calling the execute() method, the four methods
                 * onPreExecute(), doInBackground(), onProgressUpdate() and onPostExecute() are invoked.
                 * The parameters to these methods depends on the specified generic type attributes.
                 * However, at the bytecode-level so-called bridge methods are inserted, which simplifies
                 * the construction here. In particular, the parameters are fixed and are of type 'Object'.
                 * Only the doInBackground() method is mandatory.
                 */

                final String asyncTaskClass = AsyncTaskUtils.getAsyncTaskClass(callingClass, invokeStmt.getInstruction(), classHierarchy);
                if (asyncTaskClass != null) {

                    // TODO: Make the AsyncTask callback unique (not unique due to inheritance)!
                    BaseCFG asyncTaskCFG = emptyCFG("callbacks " + asyncTaskClass);
                    CFGVertex last = asyncTaskCFG.getEntry();

                    // optional
                    final String onPreExecuteMethod = classHierarchy
                            .invokedByCurrentClassOrAnySuperClass(AsyncTaskUtils.getOnPreExecuteMethod(asyncTaskClass));
                    if (onPreExecuteMethod != null && intraCFGs.containsKey(onPreExecuteMethod)) {
                        BaseCFG onPreExecuteCFG = intraCFGs.get(onPreExecuteMethod);
                        addEdge(asyncTaskCFG.getEntry(), onPreExecuteCFG.getEntry());
                        last = onPreExecuteCFG.getExit();
                    }

                    // mandatory
                    final String doInBackgroundMethod = classHierarchy
                            .invokedByCurrentClassOrAnySuperClass(AsyncTaskUtils.getDoInBackgroundMethod(asyncTaskClass));
                    BaseCFG doInBackgroundCFG = intraCFGs.get(doInBackgroundMethod);

                    if (doInBackgroundCFG == null || !intraCFGs.containsKey(doInBackgroundMethod)) {
                        throw new IllegalStateException("AsyncTask without doInBackgroundTask() method: " + overriddenMethod);
                    }

                    addEdge(last, doInBackgroundCFG.getEntry());
                    last = doInBackgroundCFG.getExit();

                    // optional
                    final String onProgressUpdateMethod = classHierarchy
                            .invokedByCurrentClassOrAnySuperClass(AsyncTaskUtils.getOnProgressUpdateMethod(asyncTaskClass));
                    if (onProgressUpdateMethod != null && intraCFGs.containsKey(onProgressUpdateMethod)) {
                        BaseCFG onProgressUpdateCFG = intraCFGs.get(onProgressUpdateMethod);
                        addEdge(last, onProgressUpdateCFG.getEntry());
                        last = onProgressUpdateCFG.getExit();
                    }

                    // optional
                    final String onPostExecuteMethod = classHierarchy
                            .invokedByCurrentClassOrAnySuperClass(AsyncTaskUtils.getOnPostExecuteMethod(asyncTaskClass));
                    if (onPostExecuteMethod != null && intraCFGs.containsKey(onPostExecuteMethod)) {
                        BaseCFG onPostExecuteCFG = intraCFGs.get(onPostExecuteMethod);
                        addEdge(last, onPostExecuteCFG.getEntry());
                        last = onPostExecuteCFG.getExit();
                    }

                    // optional
                    final String onCancelledMethod = classHierarchy
                            .invokedByCurrentClassOrAnySuperClass(AsyncTaskUtils.getOnCancelledMethod(asyncTaskClass));
                    if (onCancelledMethod != null && intraCFGs.containsKey(onCancelledMethod)) {
                        BaseCFG onCancelledCFG = intraCFGs.get(onCancelledMethod);
                        addEdge(last, onCancelledCFG.getEntry());
                        last = onCancelledCFG.getExit();
                    }

                    addEdge(last, asyncTaskCFG.getExit());
                    targetCFGs.add(asyncTaskCFG);
                } else {
                    LOGGER.warn("Couldn't resolve AsyncTask for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (AnimationUtils.isAnimationInvocation(overriddenMethod)) {

                LOGGER.debug("Animation invocation detected: " + overriddenMethod);

                final String animationListenerClassName = AnimationUtils.getAnimationListener(invokeStmt.getInstruction());
                final ClassDef animationListener = classHierarchy.getClass(animationListenerClassName);

                if (animationListener != null && AnimationUtils.isAnimationListener(animationListener)) {

                    final BaseCFG animationListenerCFG = emptyCFG("callbacks " + animationListenerClassName);

                    // TODO: Add missing callback functions.
                    // https://developer.android.com/reference/android/animation/Animator.AnimatorListener#summary

                    final String onAnimationEndMethod = AnimationUtils.getOnAnimationEndMethod(animationListenerClassName);
                    if (intraCFGs.containsKey(onAnimationEndMethod)) {
                        BaseCFG onAnimationEndCFG = intraCFGs.get(onAnimationEndMethod);
                        addEdge(animationListenerCFG.getEntry(), onAnimationEndCFG.getEntry());
                        addEdge(onAnimationEndCFG.getExit(), animationListenerCFG.getExit());
                    }

                    final String onAnimationStartMethod = AnimationUtils.getOnAnimationStartMethod(animationListenerClassName);
                    if (intraCFGs.containsKey(onAnimationStartMethod)) {
                        BaseCFG onAnimationStartCFG = intraCFGs.get(onAnimationStartMethod);
                        addEdge(animationListenerCFG.getEntry(), onAnimationStartCFG.getEntry());
                        addEdge(onAnimationStartCFG.getExit(), animationListenerCFG.getExit());
                    }

                    final String onAnimationCancelMethod = AnimationUtils.getOnAnimationCancelMethod(animationListenerClassName);
                    if (intraCFGs.containsKey(onAnimationCancelMethod)) {
                        BaseCFG onAnimationCancelCFG = intraCFGs.get(onAnimationCancelMethod);
                        addEdge(animationListenerCFG.getEntry(), onAnimationCancelCFG.getEntry());
                        addEdge(onAnimationCancelCFG.getExit(), animationListenerCFG.getExit());
                    }

                    final String onAnimationRepeatMethod = AnimationUtils.getOnAnimationRepeatMethod(animationListenerClassName);
                    if (intraCFGs.containsKey(onAnimationRepeatMethod)) {
                        BaseCFG onAnimationRepeatCFG = intraCFGs.get(onAnimationRepeatMethod);
                        addEdge(animationListenerCFG.getEntry(), onAnimationRepeatCFG.getEntry());
                        addEdge(onAnimationRepeatCFG.getExit(), animationListenerCFG.getExit());
                    }

                    // the callbacks can be executed in arbitrary order multiple times
                    addEdge(animationListenerCFG.getExit(), animationListenerCFG.getEntry());

                    targetCFGs.add(animationListenerCFG);
                } else {
                    LOGGER.warn("Couldn't resolve animation listener for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }

            } else if (ReceiverUtils.isReceiverInvocation(overriddenMethod)) {

                LOGGER.debug("BroadcastReceiver invocation detected: " + overriddenMethod);
                List<BroadcastReceiver> receivers = ReceiverUtils.isReceiverInvocation(components, invokeStmt.getInstruction());

                if (receivers != null) {

                    /*
                     * Depending on whether the receiver is a static or dynamic receiver, the control flow differs.
                     * In the first case, the respective constructor is called, followed by the call to the onReceive()
                     * method, while in the latter case only the onReceive() method is called. Since we only support
                     * static receivers so far, we always include the constructor. Note that there might be multiple
                     * broadcast receivers invoked if we deal with an implicit intent! Furthermore, we need to choose
                     * a unique name for the CFG, otherwise subsequent sendBroadcast() invocations are mapped to the
                     * same CFG, which is not what we want!
                     */
                    int instructionIndex = invokeStmt.getInstructionIndex();
                    String callingMethod = invokeStmt.getMethod();
                    BaseCFG sendBroadcastCFG = emptyCFG(callingMethod + "->"
                            + instructionIndex + "->sendBroadcast()");

                    for (final BroadcastReceiver receiver : receivers) {

                        // TODO: Do not invoke the constructor for dynamic/local receivers!

                        // integrate constructor of receiver
                        BaseCFG receiverConstructor = intraCFGs.get(receiver.getConstructors().get(0));
                        addEdge(sendBroadcastCFG.getEntry(), receiverConstructor.getEntry());

                        if (receiver.isAppWidgetProvider()) {

                            /*
                             * AppWidgetProvider is a special broadcast receiver that listens potentially to multiple different
                             * broadcasts and triggers a specific listener method for each broadcast. Moreover, the onReceive()
                             * method needs not be overridden at all. Since we can't distinguish which broadcast was sent, we
                             * integrate each available listener method in the subgraph.
                             */

                            BaseCFG onReceiveCFG = intraCFGs.get(receiver.onReceiveMethod());
                            if (onReceiveCFG != null) {
                                addEdge(receiverConstructor.getExit(), onReceiveCFG.getEntry());
                                addEdge(onReceiveCFG.getExit(), sendBroadcastCFG.getExit());
                            }

                            BaseCFG onDeletedCFG = intraCFGs.get(receiver.onDeletedMethod());
                            if (onDeletedCFG != null) {
                                addEdge(receiverConstructor.getExit(), onDeletedCFG.getEntry());
                                addEdge(onDeletedCFG.getExit(), sendBroadcastCFG.getExit());
                            }

                            BaseCFG onEnabledCFG = intraCFGs.get(receiver.onEnabledMethod());
                            if (onEnabledCFG != null) {
                                addEdge(receiverConstructor.getExit(), onEnabledCFG.getEntry());
                                addEdge(onEnabledCFG.getExit(), sendBroadcastCFG.getExit());
                            }

                            BaseCFG onDisabledCFG = intraCFGs.get(receiver.onDisabledMethod());
                            if (onDisabledCFG != null) {
                                addEdge(receiverConstructor.getExit(), onDisabledCFG.getEntry());
                                addEdge(onDisabledCFG.getExit(), sendBroadcastCFG.getExit());
                            }

                            BaseCFG onUpdateCFG = intraCFGs.get(receiver.onUpdateMethod());
                            if (onUpdateCFG != null) {
                                addEdge(receiverConstructor.getExit(), onUpdateCFG.getEntry());
                                addEdge(onUpdateCFG.getExit(), sendBroadcastCFG.getExit());
                            }

                            // TODO: Add further methods of AppWidgetProvider, e.g. onAppWidgetOptionsChanged().

                        } else {
                            // integrate onReceive() after constructor
                            BaseCFG onReceiveCFG = intraCFGs.get(receiver.onReceiveMethod());
                            addEdge(receiverConstructor.getExit(), onReceiveCFG.getEntry());
                            addEdge(onReceiveCFG.getExit(), sendBroadcastCFG.getExit());
                        }
                    }

                    targetCFGs.add(sendBroadcastCFG);
                } else {
                    LOGGER.warn("Couldn't resolve broadcast receiver for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (FileUtils.isListFilesInvocation(overriddenMethod)) {
                LOGGER.debug("File.listFiles() invocation detected: " + overriddenMethod);
                final String acceptMethod = FileUtils.isListFilesInvocation(invokeStmt.getInstruction());
                if (acceptMethod != null && intraCFGs.containsKey(acceptMethod)) {
                    targetCFGs.add(intraCFGs.get(acceptMethod));
                } else {
                    LOGGER.warn("Couldn't resolve FileFilter for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (MediaPlayerUtils.isMediaPlayerListenerInvocation(overriddenMethod)) {
                LOGGER.debug("MediaPlayer.setListener() invocation detected: " + overriddenMethod);
                final String callback = MediaPlayerUtils.getListenerCallback(overriddenMethod,
                        invokeStmt.getInstruction(), classHierarchy);
                if (callback != null && intraCFGs.containsKey(callback)) {
                    targetCFGs.add(intraCFGs.get(callback));
                } else {
                    LOGGER.warn("Couldn't resolve MediaPlayer callback for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (AudioManagerUtils.isAudioManagerInvocation(overriddenMethod)) {
                LOGGER.debug("AudioManager.requestAudioFocus() invocation detected: " + overriddenMethod);
                final String callback = AudioManagerUtils.getAudioManagerCallback(invokeStmt.getInstruction(), classHierarchy);
                if (callback != null && intraCFGs.containsKey(callback)) {
                    targetCFGs.add(intraCFGs.get(callback));
                } else {
                    LOGGER.warn("Couldn't resolve AudioManager callback for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (PopupMenuUtils.isPopupMenuCreation(overriddenMethod)) {
                LOGGER.debug("PopupMenu creation detected: " + overriddenMethod);
                final String callback = PopupMenuUtils.getPopupMenuCallback(invokeStmt.getInstruction(), classHierarchy);
                if (callback != null && intraCFGs.containsKey(callback)) {
                    targetCFGs.add(intraCFGs.get(callback));
                } else {
                    LOGGER.warn("Couldn't resolve PopupMenu callback for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (overriddenMethod.endsWith("->getWritableDatabase()Landroid/database/sqlite/SQLiteDatabase;")
                    || overriddenMethod.endsWith("->getReadableDatabase()Landroid/database/sqlite/SQLiteDatabase;")) {
                LOGGER.debug("Database creation detected: " + overriddenMethod);

                final String className = MethodUtils.getClassName(overriddenMethod);
                final BaseCFG callbacks = emptyCFG("callbacks " + overriddenMethod);

                final String onCreateMethod = className + "->" + "onCreate(Landroid/database/sqlite/SQLiteDatabase;)V";
                if (intraCFGs.containsKey(onCreateMethod)) {
                    final BaseCFG onCreateCFG = intraCFGs.get(onCreateMethod);
                    addEdge(callbacks.getEntry(), onCreateCFG.getEntry());
                    addEdge(onCreateCFG.getExit(), callbacks.getExit());
                }

                final String onOpenMethod = className + "->" + "onOpen(Landroid/database/sqlite/SQLiteDatabase;)V";
                if (intraCFGs.containsKey(onOpenMethod)) {
                    final BaseCFG onOpenCFG = intraCFGs.get(onOpenMethod);
                    addEdge(callbacks.getEntry(), onOpenCFG.getEntry());
                    addEdge(onOpenCFG.getExit(), callbacks.getExit());
                }

                final String onUpgradeMethod = className + "->" + "onUpgrade(Landroid/database/sqlite/SQLiteDatabase;II)V";
                if (intraCFGs.containsKey(onUpgradeMethod)) {
                    final BaseCFG onUpgradeCFG = intraCFGs.get(onUpgradeMethod);
                    addEdge(callbacks.getEntry(), onUpgradeCFG.getEntry());
                    addEdge(onUpgradeCFG.getExit(), callbacks.getExit());
                }

                final String onDowngradeMethod = className + "->" + "onDowngrade(Landroid/database/sqlite/SQLiteDatabase;II)V";
                if (intraCFGs.containsKey(onDowngradeMethod)) {
                    final BaseCFG onDowngradeCFG = intraCFGs.get(onDowngradeMethod);
                    addEdge(callbacks.getEntry(), onDowngradeCFG.getEntry());
                    addEdge(onDowngradeCFG.getExit(), callbacks.getExit());
                }

                targetCFGs.add(callbacks);
            } else if (ThreadUtils.isPostDelayMethod(overriddenMethod)) {
                LOGGER.debug("View.postDelay() invocation detected: " + overriddenMethod);
                final String callback = ThreadUtils.getPostDelayCallback(invokeStmt.getInstruction(), classHierarchy);
                if (callback != null && intraCFGs.containsKey(callback)) {
                    targetCFGs.add(intraCFGs.get(callback));
                } else {
                    LOGGER.warn("Couldn't resolve Thread callback for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (ThreadUtils.isScheduleMethod(overriddenMethod)) {
                LOGGER.debug("TimerTask.schedule() invocation detected: " + overriddenMethod);
                final String callback = ThreadUtils.getTimerTaskCallback(invokeStmt.getInstruction(), classHierarchy);
                if (callback != null && intraCFGs.containsKey(callback)) {
                    targetCFGs.add(intraCFGs.get(callback));
                } else {
                    LOGGER.warn("Couldn't resolve TimerTask callback for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (JobSchedulerUtils.isScheduleMethod(overriddenMethod)) {
                LOGGER.debug("JobScheduler.schedule() invocation detected: " + overriddenMethod);
                final String jobServiceClassName
                        = JobSchedulerUtils.getJobServiceClassName(invokeStmt.getInstruction(), classHierarchy);
                if (jobServiceClassName != null) {

                    final BaseCFG callbacks = emptyCFG("callbacks " + jobServiceClassName);

                    // mandatory
                    final String onJobStartMethod = JobSchedulerUtils.getOnJobStartMethod(jobServiceClassName);
                    if (intraCFGs.containsKey(onJobStartMethod)) {
                        final BaseCFG onJobStartCFG = intraCFGs.get(onJobStartMethod);
                        addEdge(callbacks.getEntry(), onJobStartCFG.getEntry());

                        // mandatory
                        final String onJobStopMethod = JobSchedulerUtils.getOnJobStopMethod(jobServiceClassName);
                        if (intraCFGs.containsKey(onJobStopMethod)) {
                            final BaseCFG onJobStopCFG = intraCFGs.get(onJobStopMethod);
                            addEdge(onJobStartCFG.getExit(), onJobStopCFG.getEntry());
                            addEdge(onJobStopCFG.getExit(), callbacks.getExit());
                        } else {
                            LOGGER.warn("Job without onJobStop() method: " + jobServiceClassName);
                            addEdge(onJobStartCFG.getExit(), callbacks.getExit());
                        }
                    } else {
                        LOGGER.warn("Job without onJobStart() method: " + jobServiceClassName);
                        addEdge(callbacks.getEntry(), callbacks.getExit());
                    }

                    targetCFGs.add(callbacks);
                } else {
                    LOGGER.warn("Couldn't resolve Job class for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else if (ServiceUtils.isJobIntentServiceInvocation(overriddenMethod)) {
                LOGGER.debug("JobIntentService.enqueueWork() invocation detected: " + overriddenMethod);
                final String callback = ServiceUtils.getJobIntentServiceCallback(invokeStmt.getInstruction());
                if (callback != null && intraCFGs.containsKey(callback)) {
                    targetCFGs.add(intraCFGs.get(callback));
                } else {
                    LOGGER.warn("Couldn't resolve JobIntentService.onHandleWork() callback for invocation: " + overriddenMethod);
                    targetCFGs.add(dummyCFG(overriddenMethod));
                }
            } else {

                if (intraCFGs.containsKey(overriddenMethod)) {
                    targetCFGs.add(intraCFGs.get(overriddenMethod));
                } else {
                    /*
                     * There are some Android specific classes, e.g. android/view/View, which are
                     * not included in the classes.dex file, or which we don't want to resolve.
                     */
                    if (!overriddenMethod.equals(targetMethod)) {
                        LOGGER.warn("Method " + overriddenMethod + " not contained in dex files!");
                    }
                    targetCFGs.add(dummyCFG(overriddenMethod)); // create dummy if not present yet
                }
            }
        }

        if (targetCFGs.isEmpty()) {
            LOGGER.error("Couldn't derive target CFG for target method " + targetMethod);
            throw new IllegalStateException("Couldn't derive target CFG for target method " + targetMethod);
        }

        return targetCFGs;
    }

    /**
     * Adds the lifecycle to the given components. In addition, the global entry points are defined for activities.
     *
     * @param callbackGraphs A mapping of component to its callback graph.
     */
    private void addLifecycleAndGlobalEntryPoints(Map<String, BaseCFG> callbackGraphs) {

        LOGGER.debug("Adding lifecycle to components...");

        // add activity and fragment lifecycle as well as global entry point for activities
        components.stream().filter(c -> c.getComponentType() == ComponentType.ACTIVITY).forEach(activityComponent -> {
            Activity activity = (Activity) activityComponent;
            addAndroidActivityLifecycle(activity, callbackGraphs.get(activity.getName()));
            addGlobalEntryPoint(activity);
        });

        // add service lifecycle as well as global entry point for services
        components.stream().filter(c -> c.getComponentType() == ComponentType.SERVICE).forEach(serviceComponent -> {
            Service service = (Service) serviceComponent;
            addAndroidServiceLifecycle(service, callbackGraphs.get(service.getName()));
            addGlobalEntryAndExitPoint(service);
        });

        // add application lifecycle as well as global entry point for application class
        components.stream().filter(c -> c.getComponentType() == ComponentType.APPLICATION).forEach(applicationComponent -> {
            Application application = (Application) applicationComponent;
            addAndroidApplicationLifecycle(application, callbackGraphs.get(application.getName()));
            addGlobalEntryAndExitPoint(application);
        });

        // connect broadcast receivers reacting to system events with global entry
        components.stream().filter(c -> c.getComponentType() == ComponentType.BROADCAST_RECEIVER).forEach(receiverComponent -> {
            BroadcastReceiver receiver = (BroadcastReceiver) receiverComponent;
            if (ReceiverUtils.isSystemEventReceiver(receiver)) {
                addGlobalEntryAndExitPoint(receiver);
            }
        });

        // connect broadcast receivers declared in manifest with global entry
        apk.getManifest().getReceivers().forEach(receiverName -> {
            final Optional<BroadcastReceiver> receiverComponent
                    = ComponentUtils.getBroadcastReceiverByName(components, ClassUtils.convertDottedClassName(receiverName));
            if (receiverComponent.isPresent()) {
                BroadcastReceiver receiver = receiverComponent.get();
                addGlobalEntryAndExitPoint(receiver);
            }
        });
    }

    /**
     * Retrieves the main activity.
     *
     * @return Returns the main activity.
     */
    public Activity getMainActivity() {
        String mainActivityName = apk.getManifest().getMainActivity();
        if (mainActivityName == null) {
            return null;
        } else {
            return ComponentUtils.getActivityByName(components, ClassUtils.convertDottedClassName(mainActivityName)).orElseThrow();
        }
    }

    /**
     * Connects the global entry and exit with the constructor and onReceive() method of the given broadcast receiver.
     *
     * @param receiver The broadcast receiver that should be integrated.
     */
    private void addGlobalEntryAndExitPoint(BroadcastReceiver receiver) {
        for (String constructor : receiver.getConstructors()) {
            final BaseCFG receiverConstructor = intraCFGs.get(constructor);
            if (receiverConstructor != null) {

                addEdge(getEntry(), receiverConstructor.getEntry());

                final String onReceiveMethod = receiver.onReceiveMethod();
                final BaseCFG onReceiveCFG = intraCFGs.get(onReceiveMethod);
                if (onReceiveCFG != null) {
                    addEdge(receiverConstructor.getExit(), onReceiveCFG.getEntry());
                    addEdge(onReceiveCFG.getExit(), getExit());
                } else {
                    addEdge(receiverConstructor.getExit(), getExit());
                    LOGGER.warn("Couldn't locate onReceive() method for receiver: " + receiver);
                }

            } else {
                LOGGER.warn("Not integrated class constructor: " + constructor);
            }
        }
    }

    /**
     * Connects the global entry and exit point to the application's constructor methods.
     *
     * @param application The application class.
     */
    private void addGlobalEntryAndExitPoint(Application application) {
        for (String constructor : application.getConstructors()) {
            final BaseCFG applicationConstructor = intraCFGs.get(constructor);
            if (applicationConstructor != null) {
                addEdge(getEntry(), applicationConstructor.getEntry());
                addEdge(applicationConstructor.getExit(), getExit());
            } else {
                LOGGER.warn("Not integrated class constructor: " + constructor);
            }
        }
    }

    /**
     * Adds the application lifecycle. This includes the callbacks.
     *
     * @param application   The application class.
     * @param callbackGraph The callbacks sub graph of the application class.
     */
    private void addAndroidApplicationLifecycle(Application application, BaseCFG callbackGraph) {

        BaseCFG constructor = intraCFGs.get(application.getDefaultConstructor());

        BaseCFG nextLifecycleMethod = callbackGraph;

        // the onCreate() method is optional
        if (intraCFGs.containsKey(application.onCreateMethod())) {
            nextLifecycleMethod = intraCFGs.get(application.onCreateMethod());
            // add an edge from the exit of onCreate() to the callbacks sub graph
            addEdge(nextLifecycleMethod.getExit(), callbackGraph.getEntry());
        }

        // link the constructor to either the onCreate or directly to the callbacks sub graph
        addEdge(constructor.getExit(), nextLifecycleMethod.getEntry());

        // multiple callbacks might be invoked consecutively
        addEdge(callbackGraph.getExit(), callbackGraph.getEntry());

        if (intraCFGs.containsKey(application.onLowMemoryMethod())) {
            BaseCFG onLowMemoryCFG = intraCFGs.get(application.onLowMemoryMethod());
            addEdge(callbackGraph.getEntry(), onLowMemoryCFG.getEntry());
            addEdge(onLowMemoryCFG.getExit(), callbackGraph.getExit());
        }

        if (intraCFGs.containsKey(application.onTerminateMethod())) {
            BaseCFG onTerminateCFG = intraCFGs.get(application.onTerminateMethod());
            addEdge(callbackGraph.getEntry(), onTerminateCFG.getEntry());
            addEdge(onTerminateCFG.getExit(), callbackGraph.getExit());
        }

        if (intraCFGs.containsKey(application.onTrimMemoryMethod())) {
            BaseCFG onTrimMemoryCFG = intraCFGs.get(application.onTrimMemoryMethod());
            addEdge(callbackGraph.getEntry(), onTrimMemoryCFG.getEntry());
            addEdge(onTrimMemoryCFG.getExit(), callbackGraph.getExit());
        }

        if (intraCFGs.containsKey(application.onConfigurationChangedMethod())) {
            BaseCFG onConfigurationChangedCFG = intraCFGs.get(application.onConfigurationChangedMethod());
            addEdge(callbackGraph.getEntry(), onConfigurationChangedCFG.getEntry());
            addEdge(onConfigurationChangedCFG.getExit(), callbackGraph.getExit());
        }
    }

    /**
     * Adds the lifecycle of started and/or bound services. The lifecycle can be depicted as follows:
     * <p>
     * Started Service:
     * constructor -> onCreate() -> [onStartCommand()] -> [onStart()] -> callbacks -> [onDestroy()]
     * <p>
     * Bound Service:
     * constructor -> onCreate() -> callbacks -> onServiceConnected() -> [onDestroy()]
     * <p>
     * We include a dummy representation of the onCreate() method if not present, although that lifecycle
     * method is not mandatory! Moreover, since a service can be used both as a started and bound service,
     * we don't enforce everywhere the restrictions coming with the respective service type, e.g. a bound
     * service never calls onStartCommand(). Thus, the lifecycle so such 'dual'-used service might look
     * strange at the first sight.
     *
     * @param service       The service for which the lifecycle should be added.
     * @param callbackGraph The callback sub graph of the service component.
     */
    private void addAndroidServiceLifecycle(Service service, BaseCFG callbackGraph) {

        String constructor = service.getDefaultConstructor();

        if (!intraCFGs.containsKey(constructor)) {
            LOGGER.warn("Service without explicit constructor: " + service);
        } else {

            // TODO: onCreate() is optional, it's not mandatory to overwrite it, may neglect it
            BaseCFG ctr = intraCFGs.get(constructor);
            String onCreateMethod = service.onCreateMethod();

            // the constructor directly invokes onCreate()
            BaseCFG onCreate = addLifecycle(onCreateMethod, ctr);

            /*
             * The method onStartCommand() is only invoked if the service is invoked via Context.startService(), see:
             * https://developer.android.com/reference/android/app/Service#onStartCommand(android.content.Intent,%20int,%20int)
             * If there is a custom onStartCommand() present, we include it, otherwise it's ignored.
             */
            BaseCFG nextLifeCycle = onCreate;
            String onStartCommandMethod = service.onStartCommandMethod();
            if (intraCFGs.containsKey(onStartCommandMethod)) {
                nextLifeCycle = addLifecycle(onStartCommandMethod, onCreate);

                if (service.isStarted()) {
                    // a started service calls directly onStartCommand() if the service has been already initialised
                    BaseCFG onStartCommand = nextLifeCycle;
                    getIncomingEdges(ctr.getEntry()).forEach(e -> {

                        Statement invokeStmt = e.getSource().getStatement();
                        BasicStatement basicStmt = null;

                        if (invokeStmt.getType() == Statement.StatementType.BASIC_STATEMENT) {
                            basicStmt = (BasicStatement) invokeStmt;
                        } else {
                            // basic block -> last stmt contains the invoke instruction
                            basicStmt = (BasicStatement) ((BlockStatement) invokeStmt).getLastStatement();
                        }

                        Instruction invoke = basicStmt.getInstruction().getInstruction();
                        String method = MethodUtils.getMethodName(((ReferenceInstruction) invoke).getReference().toString());
                        if (method.equals("startService(Landroid/content/Intent;)Landroid/content/ComponentName;")) {
                            addEdge(e.getSource(), onStartCommand.getEntry());
                        }
                    });
                }
            }

            /*
             * The method onStart() is deprecated and only invoked by onStartCommand() for backward compatibility, see
             * https://developer.android.com/reference/android/app/Service#onStart(android.content.Intent,%20int)
             * If there is a custom onStart() present, we include it, otherwise it's ignored.
             */
            String onStartMethod = service.onStartMethod();
            if (intraCFGs.containsKey(onStartMethod)) {
                nextLifeCycle = addLifecycle(onStartMethod, nextLifeCycle);
            }

            // multiple callbacks might be invoked consecutively
            addEdge(callbackGraph.getExit(), callbackGraph.getEntry());

            // there is always an edge from onCreate()/onStartCommand()/onStart() to the callbacks entry point
            addEdge(nextLifeCycle.getExit(), callbackGraph.getEntry());

            /*
             * If the service is a bound service, i.e. called by bindService(), there is an edge
             * from onCreate() to the callbacks entry point. In particular, bindService() doesn't invoke
             * onStartCommand() nor onStart().
             */
            if (service.isBound()) {
                addEdge(onCreate.getExit(), callbackGraph.getEntry());
            }

            // onBind() is optional, at least for started services
            String onBindMethod = service.onBindMethod();
            if (intraCFGs.containsKey(onBindMethod)) {
                BaseCFG onBind = intraCFGs.get(onBindMethod);
                addEdge(callbackGraph.getEntry(), onBind.getEntry());
                addEdge(onBind.getExit(), callbackGraph.getExit());
            }

            /*
             * If we deal with a bound service, onBind() invokes directly onServiceConnected() of
             * the service connection object.
             */
            if (service.isBound() && service.getServiceConnection() != null) {

                String serviceConnection = service.getServiceConnection();
                BaseCFG serviceConnectionConstructor = intraCFGs.get(ClassUtils.getDefaultConstructor(serviceConnection));

                if (serviceConnectionConstructor == null) {
                    LOGGER.warn("Service connection '" + serviceConnection + "' for " + service.getName() + " has no intra CFG!");
                } else {
                    // add callbacks subgraph
                    BaseCFG serviceConnectionCallbacks = dummyIntraCFG("callbacks " + serviceConnection);
                    addSubGraph(serviceConnectionCallbacks);
                    addEdge(serviceConnectionCallbacks.getExit(), serviceConnectionCallbacks.getEntry());

                    // connect constructor with callback entry point
                    addEdge(serviceConnectionConstructor.getExit(), serviceConnectionCallbacks.getEntry());

                    // integrate callback methods onServiceConnected() and onServiceDisconnected()
                    BaseCFG onServiceConnected = intraCFGs.get(serviceConnection
                            + "->onServiceConnected(Landroid/content/ComponentName;Landroid/os/IBinder;)V");
                    BaseCFG onServiceDisconnected = intraCFGs.get(serviceConnection
                            + "->onServiceDisconnected(Landroid/content/ComponentName;)V");
                    addEdge(serviceConnectionCallbacks.getEntry(), onServiceConnected.getEntry());
                    addEdge(serviceConnectionCallbacks.getEntry(), onServiceDisconnected.getEntry());
                    addEdge(onServiceConnected.getExit(), serviceConnectionCallbacks.getExit());
                    addEdge(onServiceDisconnected.getExit(), serviceConnectionCallbacks.getExit());

                    // connect onBind() with onServiceConnected()
                    BaseCFG onBind = intraCFGs.get(onBindMethod);
                    addEdge(onBind.getExit(), onServiceConnected.getEntry());
                }
            }

            // the service may overwrite onUnBind()
            String onUnbindMethod = service.onUnbindMethod();
            if (intraCFGs.containsKey(onUnbindMethod)) {
                BaseCFG onUnbind = intraCFGs.get(onUnbindMethod);
                addEdge(callbackGraph.getEntry(), onUnbind.getEntry());
                addEdge(onUnbind.getExit(), callbackGraph.getExit());
            }

            // the service may overwrite onDestroy()
            String onDestroyMethod = service.onDestroyMethod();
            if (intraCFGs.containsKey(onDestroyMethod)) {
                BaseCFG onDestroy = intraCFGs.get(onDestroyMethod);
                addEdge(callbackGraph.getExit(), onDestroy.getEntry());
            }
        }
    }

    /**
     * Adds for each component (activity) a global entry point to the respective constructor.
     *
     * @param activity The activity for which a global entry point should be defined.
     */
    private void addGlobalEntryPoint(Activity activity) {
        for (String constructor: activity.getConstructors()) {
            addEdge(getEntry(), intraCFGs.get(constructor).getEntry());
        }
    }

    /**
     * Adds for each component (service) a global entry point and exit point to the respective constructors'
     * entry and exit if the service gets not directly invoked from an activity, i.e. the service's entry point is not
     * connected to the CFG. This may happen if the given application provides a service that is only accessible from
     * other applications via intents.
     *
     * @param service The service for which a global entry point should be defined.
     */
    private void addGlobalEntryAndExitPoint(Service service) {
        for (String constructor: service.getConstructors()) {
            final BaseCFG serviceConstructor = intraCFGs.get(constructor);

            if (serviceConstructor == null) {
                LOGGER.warn("Not integrated service class constructor: " + constructor);
                continue;
            }

            if (getPredecessors(serviceConstructor.getEntry()).isEmpty()) {
                addEdge(getEntry(), serviceConstructor.getEntry());
                addEdge(serviceConstructor.getExit(), getExit());
            }
        }
    }

    /**
     * Connects all lifecycle methods with each other for a given activity. Also
     * integrates the fragment lifecycle.
     *
     * @param activity The activity for which the lifecycle should be added.
     */
    private void addAndroidActivityLifecycle(final Activity activity, BaseCFG callbackGraph) {

        final Set<Fragment> fragments = activity.getHostingFragments();
        LOGGER.debug("Activity " + activity + " defines the following fragments: " + fragments);

        String onCreateMethod = activity.onCreateMethod();

        // although every activity should overwrite onCreate, there are rare cases that don't follow this rule
        if (!intraCFGs.containsKey(onCreateMethod)) {
            BaseCFG onCreate = dummyIntraCFG(onCreateMethod);
            intraCFGs.put(onCreateMethod, onCreate);
            addSubGraph(onCreate);
        }

        BaseCFG onCreateCFG = intraCFGs.get(onCreateMethod);

        for (String constructor : activity.getConstructors()) {
            // connect onCreate with the each constructor
            addEdge(intraCFGs.get(constructor).getExit(), onCreateCFG.getEntry());
        }

        // if there are fragments, onCreate invokes onAttach, onCreate and onCreateView
        for (Fragment fragment : fragments) {

            // connect the onCreate of the activity with the fragment constructor
            final String constructor = fragment.getConstructors().get(0);
            BaseCFG fragmentConstructorCFG = intraCFGs.get(constructor);
            addEdge(onCreateCFG.getExit(), fragmentConstructorCFG.getEntry());

            BaseCFG lastFragmentLifecycleCFG = fragmentConstructorCFG;

            // TODO: there is a deprecated onAttach using an activity instance as parameter
            String onAttachFragment = fragment.onAttachMethod();
            if (intraCFGs.containsKey(onAttachFragment)) { // optional
                lastFragmentLifecycleCFG = addLifecycle(onAttachFragment, onCreateCFG);
            }

            String onCreateFragment = fragment.onCreateMethod();
            lastFragmentLifecycleCFG = addLifecycle(onCreateFragment, lastFragmentLifecycleCFG);

            /*
             * If the fragment is a DialogFragment, it may override onCreateDialog(), see:
             * https://developer.android.com/reference/android/app/DialogFragment#onCreateDialog(android.os.Bundle)
             */
            String onCreateDialogFragment = fragment.onCreateDialogMethod();
            if (intraCFGs.containsKey(onCreateDialogFragment)) {
                lastFragmentLifecycleCFG = addLifecycle(onCreateDialogFragment, lastFragmentLifecycleCFG);
            }

            /*
             * If the fragment is a PreferenceFragmentCompat or PreferenceFragment, it may override onCreatePreferences():
             * https://developer.android.com/reference/androidx/preference/PreferenceFragmentCompat#onCreatePreferences(android.os.Bundle,%20java.lang.String)
             */
            String onCreatePreferencesFragment = fragment.onCreatePreferencesMethod();
            if (intraCFGs.containsKey(onCreatePreferencesFragment)) {
                lastFragmentLifecycleCFG = addLifecycle(onCreatePreferencesFragment, lastFragmentLifecycleCFG);
            }

            String onCreateViewFragment = fragment.onCreateViewMethod();
            BaseCFG onCreateViewFragmentCFG = addLifecycle(onCreateViewFragment, lastFragmentLifecycleCFG);

            String onActivityCreatedFragment = fragment.onActivityCreatedMethod();
            BaseCFG onActivityCreatedFragmentCFG = addLifecycle(onActivityCreatedFragment, onCreateViewFragmentCFG);

            // according to https://developer.android.com/reference/android/app/Fragment -> onViewStateRestored
            String onViewStateRestoredFragment = fragment.onViewStateRestoredMethod();
            BaseCFG onViewStateRestoredFragmentCFG = addLifecycle(onViewStateRestoredFragment, onActivityCreatedFragmentCFG);

            // go back to onCreate() exit
            addEdge(onViewStateRestoredFragmentCFG.getExit(), onCreateCFG.getExit());
        }

        // onCreate() directly invokes onStart()
        String onStart = activity.onStartMethod();
        BaseCFG onStartCFG = addLifecycle(onStart, onCreateCFG);

        // if there are fragments, onStart() is invoked
        for (Fragment fragment : fragments) {
            String onStartFragment = fragment.onStartMethod();
            BaseCFG onStartFragmentCFG = addLifecycle(onStartFragment, onStartCFG);

            // go back to onStart() exit
            addEdge(onStartFragmentCFG.getExit(), onStartCFG.getExit());
        }

        // onStart() invokes onRestoreInstanceState()
        String onRestoreInstanceState = activity.onRestoreInstanceStateOverloadedMethod();

        if (!intraCFGs.containsKey(onRestoreInstanceState)) {
            // use the default onRestoreInstanceState()
            onRestoreInstanceState = activity.onRestoreInstanceStateMethod();
        }

        BaseCFG onRestoreInstanceStateCFG = addLifecycle(onRestoreInstanceState, onStartCFG);

        // onRestoreInstanceState() invokes onPostCreate()
        String onPostCreate = activity.onPostCreateOverloadedMethod();

        if (!intraCFGs.containsKey(onPostCreate)) {
            // use the default onPostCreate()
            onPostCreate = activity.onPostCreateMethod();
        }

        BaseCFG onPostCreateCFG = addLifecycle(onPostCreate, onRestoreInstanceStateCFG);

        // onRestoreInstanceState() is not always safely called, thus we add an direct edge from onStart() to onPostCreate()
        addEdge(onStartCFG.getExit(), onPostCreateCFG.getEntry());

        String onResume = activity.onResumeMethod();
        BaseCFG onResumeCFG = addLifecycle(onResume, onPostCreateCFG);

        // if there are fragments, onResume() is invoked
        for (Fragment fragment : fragments) {
            String onResumeFragment = fragment.onResumeMethod();
            BaseCFG onResumeFragmentCFG = addLifecycle(onResumeFragment, onResumeCFG);

            // go back to onResume() exit
            addEdge(onResumeFragmentCFG.getExit(), onResumeCFG.getExit());
        }

        BaseCFG lastLifecycle = onResumeCFG;
        String onPostResume = activity.onPostResumeMethod();

        // onPostResume() is optional
        if (intraCFGs.containsKey(onPostResume)) {
            lastLifecycle = addLifecycle(onPostResume, onResumeCFG);
        }

        /*
         * Each component may define several listeners for certain events, e.g. a button click,
         * which causes the invocation of a callback function. Those callbacks are active as
         * long as the corresponding component (activity) is in the onResume state. Thus, in our
         * graph we have an additional sub-graph 'callbacks' that is directly linked to the end
         * of 'onResume()/onPostResume()' and can either call one of the specified listeners or directly invoke
         * the onPause() method (indirectly through the entry-exit edge). Each listener function
         * points back to the 'callbacks' entry node.
         */

        // callbacks can be invoked after onResume()/onPostResume() has finished
        addEdge(lastLifecycle.getExit(), callbackGraph.getEntry());

        // there can be a sequence of callbacks (loop)
        addEdge(callbackGraph.getExit(), callbackGraph.getEntry());

        // onPause() can be invoked after some callback
        String onPause = activity.onPauseMethod();
        BaseCFG onPauseCFG = addLifecycle(onPause, callbackGraph);

        // if there are fragments, onPause() is invoked
        for (Fragment fragment : fragments) {

            String onPauseFragment = fragment.onPauseMethod();
            BaseCFG onPauseFragmentCFG = addLifecycle(onPauseFragment, onPauseCFG);

            // go back to onPause() exit
            addEdge(onPauseFragmentCFG.getExit(), onPauseCFG.getExit());
        }

        /*
         * According to https://developer.android.com/reference/android/app/Activity#onSaveInstanceState(android.os.Bundle)
         * the onSaveInstanceState() method is called either prior or after onStop() depending on the API version.
         * Prior to API 28, onSaveInstanceState() is called before onStop() and starting with API 28 it is called
         * afterwards. We stick here to the first choice. Moreover, as also mentioned in above reference,
         * onSaveInstanceState() is not always called, thus we directly add an additional edge from onPause()
         * to onStop().
         */
        String onSaveInstanceState = activity.onSaveInstanceStateOverloadedMethod();

        if (!intraCFGs.containsKey(onSaveInstanceState)) {
            // use the default onSaveInstanceState()
            onSaveInstanceState = activity.onSaveInstanceStateMethod();
        }

        BaseCFG onSaveInstanceStateCFG = addLifecycle(onSaveInstanceState, onPauseCFG);

        String onStop = activity.onStopMethod();
        BaseCFG onStopCFG = addLifecycle(onStop, onSaveInstanceStateCFG);
        addEdge(onPauseCFG.getExit(), onStopCFG.getEntry());

        // if there are fragments, onStop() is invoked
        for (Fragment fragment : fragments) {

            String onStopFragment = fragment.onStopMethod();
            BaseCFG onStopFragmentCFG = addLifecycle(onStopFragment, onStopCFG);

            String onSaveInstanceStateFragment = fragment.onSaveInstanceStateMethod();
            BaseCFG onSaveInstanceStateFragmentCFG = addLifecycle(onSaveInstanceStateFragment, onStopFragmentCFG);

            // go back to onStop() exit
            addEdge(onSaveInstanceStateFragmentCFG.getExit(), onStopCFG.getExit());
        }

        String onDestroy = activity.onDestroyMethod();
        BaseCFG onDestroyCFG = addLifecycle(onDestroy, onStopCFG);

        // The application may terminate if the last activity has been destroyed.
        addEdge(onDestroyCFG.getExit(), getExit());

        // if there are fragments, onDestroy, onDestroyView and onDetach are invoked
        for (Fragment fragment : fragments) {

            String onDestroyViewFragment = fragment.onDestroyViewMethod();
            BaseCFG onDestroyViewFragmentCFG = addLifecycle(onDestroyViewFragment, onDestroyCFG);

            // onDestroyView() can also invoke onCreateView()
            String onCreateViewFragment = fragment.onCreateViewMethod();
            BaseCFG onCreateViewFragmentCFG = intraCFGs.get(onCreateViewFragment);
            addEdge(onDestroyViewFragmentCFG.getExit(), onCreateViewFragmentCFG.getEntry());

            String onDestroyFragment = fragment.onDestroyMethod();
            BaseCFG onDestroyFragmentCFG = addLifecycle(onDestroyFragment, onDestroyViewFragmentCFG);

            String onDetachFragment = fragment.onDetachMethod();
            BaseCFG onDetachFragmentCFG = addLifecycle(onDetachFragment, onDestroyFragmentCFG);

            // go back to onDestroy() exit
            addEdge(onDetachFragmentCFG.getExit(), onDestroyCFG.getExit());
        }

        // onPause can also invoke onResume()
        addEdge(onPauseCFG.getExit(), onResumeCFG.getEntry());

        // onStop can also invoke onRestart()
        String onRestart = activity.onRestartMethod();
        BaseCFG onRestartCFG = addLifecycle(onRestart, onStopCFG);

        // onRestart invokes onStart()
        addEdge(onRestartCFG.getExit(), onStartCFG.getEntry());
    }

    /**
     * Connects two life cycle methods with each other, e.g. onCreate directly
     * calls onStart.
     *
     * @param newLifecycle The name of the next lifecycle event, e.g. onStart.
     * @param predecessor  The previous lifecycle, e.g. onCreate.
     * @return Returns the sub graph representing the newly added lifecycle.
     */
    private BaseCFG addLifecycle(String newLifecycle, BaseCFG predecessor) {

        BaseCFG lifecyle = null;

        if (intraCFGs.containsKey(newLifecycle)) {
            lifecyle = intraCFGs.get(newLifecycle);
        } else {
            // use custom lifecycle CFG
            lifecyle = dummyIntraCFG(newLifecycle);
            intraCFGs.put(newLifecycle, lifecyle);
            addSubGraph(lifecyle);
        }

        addEdge(predecessor.getExit(), lifecyle.getEntry());
        return lifecyle;
    }

    /**
     * Adds callbacks to the respective activity. We both consider callbacks defined via XML
     * as well as programmatically defined callbacks. Note that an activity shares with its hosting fragments
     * the 'callback' subgraph.
     *
     * @param apk            The APK file describing the app.
     * @param callbackGraphs Maintains a mapping between a component and its callback graph.
     */
    private void addCallbacks(APK apk, Map<String, BaseCFG> callbackGraphs) {

        LOGGER.debug("Adding callbacks to activities...");

        // retrieve callbacks declared in code and XML
        Multimap<String, BaseCFG> callbacks = lookUpCallbacks(apk);
        Multimap<String, BaseCFG> callbacksXML = lookUpCallbacksXML(apk);

        // add callbacks directly from activity itself and its hosted fragments
        components.stream().filter(c -> c.getComponentType() == ComponentType.ACTIVITY).forEach(activity -> {
            BaseCFG callbackGraph = callbackGraphs.get(activity.getName());

            // callbacks directly declared in activity
            attachCallbacks(callbacks.get(activity.getName()), callbacksXML.get(activity.getName()), callbackGraph);

            // callbacks declared in hosted fragments
            Set<Fragment> fragments = ((Activity) activity).getHostingFragments();
            for (Fragment fragment : fragments) {
                attachCallbacks(callbacks.get(fragment.getName()), callbacksXML.get(fragment.getName()), callbackGraph);
            }
        });

        Set<Fragment> fragmentsWithoutHost = ComponentUtils.getFragmentsWithoutHost(components);

        if (!fragmentsWithoutHost.isEmpty()) {
            LOGGER.warn("There are fragments without a host! Callbacks will be lost for " + fragmentsWithoutHost);
        }
    }

    /**
     * Attaches the derived callbacks both from code and XML to the callback sub graph.
     *
     * @param callbacks The callbacks derived from the code.
     * @param callbacksXML The callbacks derived from the XML files.
     * @param callbackGraph The component's virtual callback sub graph, i.e. the entry point of all callbacks of the
     *                      component.
     */
    private void attachCallbacks(Collection<BaseCFG> callbacks, Collection<BaseCFG> callbacksXML, BaseCFG callbackGraph) {

        List<BaseCFG> allCallbacks = Stream.concat(callbacks.stream(), callbacksXML.stream()).collect(Collectors.toList());

        allCallbacks.forEach(callback -> {
            /*
            * Menu callbacks call each other in a specific sequence. We try to accurately model this in the callbacks
            * subgraph by stitching those callbacks together in the right order. Every other callback is attached to
            * the callbacks entry point directly.
             */
            BaseCFG callbackRoot = getCallbackParentRecursively(callback, allCallbacks).orElse(callbackGraph);
            addEdge(callbackRoot.getEntry(), callback.getEntry());
            addEdge(callback.getExit(), callbackRoot.getExit());
        });
    }

    /**
     * Retrieves the next present callback parent from the callback parent sequence. This is only relevant for menu
     * callbacks, which form a callback sequence, e.g.:
     * onOptionsItemSelected -> onMenuOpened -> onPrepareOptionsMenu -> onCreateOptionsMenu.
     *
     * @param callback The child callback.
     * @param allCallbacks The list of all callbacks.
     * @return Returns the next callback parent if present, otherwise an empty optional.
     */
    private Optional<BaseCFG> getCallbackParentRecursively(BaseCFG callback, Collection<BaseCFG> allCallbacks) {

        String callbackClass = MethodUtils.getClassName(callback.getMethodName());

        // iterate over the parent chain
        return Stream.iterate(MethodUtils.getMethodName(callback.getMethodName()), MethodUtils.ANDROID_CALLBACK_TO_PARENT::get)
                .skip(1) // skip the child callback
                .takeWhile(Objects::nonNull) // reached root of parent chain
                .map(parentMethod -> getGraphByMethodName(callbackClass + "->" + parentMethod, allCallbacks))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Retrieves the graph corresponding to the given fully-qualified method name if present.
     *
     * @param fullyQualifiedMethodName The given method name.
     * @param graphs The list of graphs.
     * @return Returns the graph corresponding to the method name or an empty optional if not present.
     */
    private Optional<BaseCFG> getGraphByMethodName(String fullyQualifiedMethodName, final Collection<BaseCFG> graphs) {

        Set<BaseCFG> candidates = graphs.stream()
                .filter(b -> b.getMethodName().equals(fullyQualifiedMethodName)).collect(Collectors.toSet());

        if (candidates.isEmpty()) {
            return Optional.empty();
        } else if (candidates.size() > 1) {
            throw new IllegalStateException("Method name is not unique, multiple graphs found: " + fullyQualifiedMethodName);
        } else {
            return candidates.stream().findAny();
        }
    }

    /**
     * Returns for each ui component, i.e. an activity or a fragment, its associated callbacks.
     *
     * @param apk The APK file.
     * @return Returns a mapping between a component and its associated callbacks (can be multiple per instance).
     */
    private Multimap<String, BaseCFG> lookUpCallbacks(final APK apk) {

        /*
         * Rather than backtracking calls like setOnClickListener() to a certain component, we
         * directly search for the callback methods, e.g., onClick(), and track which class defines it.
         * Only if the callback is not directly declared in an activity or fragment, we look up it usages.
         */

        Multimap<String, BaseCFG> classToCallbacksMapping = TreeMultimap.create();

        for (String callback : callbacks) {

            boolean assignedCallbackToClass = false;

            BaseCFG callbackGraph = intraCFGs.get(callback);
            String className = MethodUtils.getClassName(callback);

            /*
             * We need to check where the callback is declared. There are two options here:
             *
             * (1)
             * If the class containing the callback represents an inner class, then the callback might belong
             * to the outer class, e.g. an activity defines the OnClickListener (onClick() callback) as inner class.
             * But, the outer class might be just a wrapper class containing multiple widgets and its callbacks.
             * In this case, we have to backtrack the usages of the (outer) class to the respective component,
             * i.e. the activity or fragment.
             *
             * (2)
             * If the class represents a top-level class, it might be either an activity/fragment implementing
             * a listener or a (wrapper) class representing a top-level listener. In the latter case, we need to
             * backtrack the usages to the respective component.
             */
            if (ClassUtils.isInnerClass(className)) {

                String outerClass = ClassUtils.getOuterClass(className);
                Optional<Component> component = ComponentUtils.getComponentByName(components, outerClass);

                if (component.isPresent()) {
                    // component declares directly callback
                    classToCallbacksMapping.put(outerClass, callbackGraph);
                    assignedCallbackToClass = true;
                } else {
                    // callback is declared by some wrapper class

                    // check which application classes make use of the wrapper class
                    Set<UsageSearch.Usage> usages = UsageSearch.findClassUsages(apk, outerClass);

                    // check whether any found class represents a (ui) component
                    for (UsageSearch.Usage usage : usages) {

                        String clazzName = usage.getClazz().toString();
                        Optional<Component> uiComponent = ComponentUtils.getComponentByName(components, clazzName);

                        if (uiComponent.isPresent()) {
                            /*
                             * The class that makes use of the wrapper class represents a component, thus
                             * the callback should be assigned to this class.
                             */
                            classToCallbacksMapping.put(clazzName, callbackGraph);
                            assignedCallbackToClass = true;
                        }

                        /*
                         * The usage may point to an inner class, e.g. an anonymous class representing
                         * a callback. However, the ui component is typically the outer class. Thus,
                         * we have to check whether the outer class represents a ui component.
                         */
                        if (ClassUtils.isInnerClass(clazzName)) {

                            String outerClassName = ClassUtils.getOuterClass(clazzName);
                            uiComponent = ComponentUtils.getComponentByName(components, outerClassName);

                            if (uiComponent.isPresent()) {
                                classToCallbacksMapping.put(outerClassName, callbackGraph);
                                assignedCallbackToClass = true;
                            }
                        }
                    }
                }
            } else {
                // top-level class

                // an activity/fragment might implement a listener interface
                Optional<Component> component = ComponentUtils.getComponentByName(components, className);

                if (component.isPresent()) {
                    // component declares directly callback
                    classToCallbacksMapping.put(className, callbackGraph);
                    assignedCallbackToClass = true;
                } else {
                    // callback is declared by some top-level listener or wrapper class

                    Set<UsageSearch.Usage> usages = UsageSearch.findClassUsages(apk, className);

                    // check whether any found class represents a (ui) component
                    for (UsageSearch.Usage usage : usages) {

                        String clazzName = usage.getClazz().toString();
                        Optional<Component> uiComponent = ComponentUtils.getComponentByName(components, clazzName);

                        if (uiComponent.isPresent()) {
                            /*
                             * The class that makes use of the top-level listener (wrapper) class represents a
                             * component, thus the callback should be assigned to this class.
                             */
                            classToCallbacksMapping.put(clazzName, callbackGraph);
                            assignedCallbackToClass = true;
                        }

                        /*
                         * The usage may point to an inner class, e.g. an anonymous class representing
                         * a callback. However, the ui component is typically the outer class. Thus,
                         * we have to check whether the outer class represents a ui component.
                         */
                        if (ClassUtils.isInnerClass(clazzName)) {

                            String outerClassName = ClassUtils.getOuterClass(clazzName);
                            uiComponent = ComponentUtils.getComponentByName(components, outerClassName);

                            if (uiComponent.isPresent()) {
                                classToCallbacksMapping.put(outerClassName, callbackGraph);
                                assignedCallbackToClass = true;
                            }
                        }
                    }
                }
            }

            if (!assignedCallbackToClass) {
                LOGGER.debug("Couldn't assign callback to class: " + callback);
            }
        }

        LOGGER.debug("Callbacks declared via Code: ");
        classToCallbacksMapping.forEach((c, g) -> LOGGER.debug(c + " -> " + g.getMethodName()));
        return classToCallbacksMapping;
    }

    /**
     * Looks up callbacks declared in XML layout files and associates them to its defining component.
     * First, the onCreate() and/or onCreateView() methods of activities and fragments are looked up for invocations
     * of setContentView() or inflate(), respectively. Next, the resource ids are extracted by backtracking the
     * former invocations. Then, we map the resource ids to layout files and parse the callbacks within those files.
     * Finally, the declared callbacks are looked up in the graph and mapped to its defining component.
     *
     * @return Returns a mapping between a component (its class name) and its callbacks (actually the
     * corresponding intra CFGs). Each component may define multiple callbacks.
     */
    private Multimap<String, BaseCFG> lookUpCallbacksXML(APK apk) {

        // stores for each component its resource id in hexadecimal representation
        Map<String, String> componentResourceID = new HashMap<>();

        Set<ClassDef> viewComponents = components.stream()
                .filter(c -> c.getComponentType() == ComponentType.ACTIVITY || c.getComponentType() == ComponentType.FRAGMENT)
                .map(Component::getClazz)
                .collect(Collectors.toSet());

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                if (viewComponents.contains(classDef)) {
                    // only activities and fragments of the application can define callbacks in XML

                    for (Method method : classDef.getMethods()) {

                        MethodImplementation methodImplementation = method.getImplementation();

                        if (methodImplementation != null
                                // we can speed up search for looking only for onCreate(..) and onCreateView(..)
                                // this assumes that only these two methods declare the layout via setContentView()/inflate()!
                                && method.getName().contains("onCreate")) {

                            for (AnalyzedInstruction analyzedInstruction : MethodUtils.getAnalyzedInstructions(dexFile, method)) {

                                Instruction instruction = analyzedInstruction.getInstruction();

                                /*
                                 * We need to search for calls to setContentView(..) and inflate(..).
                                 * Both of them are of type invoke-virtual.
                                 */
                                if (instruction.getOpcode() == Opcode.INVOKE_VIRTUAL) {
                                    String resourceID = Utility.getLayoutResourceID(classDef, analyzedInstruction);

                                    if (resourceID != null) {
                                        componentResourceID.put(classDef.toString(), resourceID);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LOGGER.debug("Component-to-Resource-ID-mapping: " + componentResourceID);

        /*
         * We now need to find the layout file for a given activity or fragment. Then, we need to
         * parse it in order to get possible callbacks.
         */
        Multimap<String, String> componentCallbacks = TreeMultimap.create();

        // search layout file + parse callbacks
        componentResourceID.forEach(
                (component, resourceID) -> {

                    LayoutFile layoutFile = LayoutFile.findLayoutFile(apk.getDecodingOutputPath(), resourceID);

                    if (layoutFile != null) {
                        componentCallbacks.putAll(component, layoutFile.parseCallbacks());

                        Set<Fragment> hostedFragments = layoutFile.parseFragments().stream()
                                .map(dottedFragmentName -> {
                                    var fragment = ComponentUtils.getFragmentByName(components, ClassUtils.convertDottedClassName(dottedFragmentName));

                                    if (fragment.isEmpty()) {
                                        LOGGER.warn("Was not able to find fragment " + dottedFragmentName);
                                    }
                                    return fragment;
                                })
                                .flatMap(Optional::stream)
                                .collect(Collectors.toSet());

                        if (!hostedFragments.isEmpty()) {
                            Optional<Activity> activity = ComponentUtils.getActivityByName(components, component);

                            if (activity.isPresent()) {
                                hostedFragments.forEach(activity.get()::addHostingFragment);
                            } else {
                                LOGGER.warn("Cannot attach " + hostedFragments.size() + " hosted fragments to any " + component);
                            }
                        }
                    }
                });

        Multimap<String, BaseCFG> callbacks = TreeMultimap.create();

        // lookup which class defines the callback method
        for (String component : componentCallbacks.keySet()) {
            for (String callbackName : componentCallbacks.get(component)) {
                // callbacks can have a custom method name but the rest of the method signature is fixed
                String callback = component + "->" + callbackName + "(Landroid/view/View;)V";

                // check whether the callback is defined within the component itself
                if (intraCFGs.containsKey(callback)) {
                    callbacks.put(component, intraCFGs.get(callback));
                } else {
                    // it is possible that the outer class defines the callback
                    if (ClassUtils.isInnerClass(component)) {
                        String outerClassName = ClassUtils.getOuterClass(component);
                        callback = callback.replace(component, outerClassName);
                        if (intraCFGs.containsKey(callback)) {
                            callbacks.put(outerClassName, intraCFGs.get(callback));
                        } else {
                            LOGGER.warn("Couldn't derive defining component class for callback: " + callback);
                        }
                    } else {
                        LOGGER.warn("Couldn't derive defining component class for callback: " + callback);
                    }
                }
            }
        }
        LOGGER.debug("Callbacks declared via XML: ");
        callbacks.forEach((c, g) -> LOGGER.debug(c + " -> " + g.getMethodName()));
        return callbacks;
    }

    /**
     * Checks whether the given method represents the start() or run() method of the Thread/Runnable class
     * and if the invocation should be resolved or not.
     *
     * @param definingMethod The method in which the given target method is called.
     * @param targetMethod   The target method which should be checked.
     * @return Returns {@code true} if the method should be resolved, otherwise {@code false} is returned.
     */
    private boolean resolveThreadMethod(final String definingMethod, final String targetMethod) {
        /*
         * We don't want to resolve method invocations of 'Ljava/lang/Runnable;->run()V' within the
         * run() method of the custom thread class. Otherwise, we could introduce an unwanted cycle in
         * our graph, since the resolving the 'Ljava/lang/Runnable;->run()V' invocation would lead to
         * the run() itself.
         */
        return !ThreadUtils.isThreadMethod(classHierarchy, definingMethod)
                && ThreadUtils.isThreadMethod(classHierarchy, targetMethod);
    }

    /**
     * Splits a block statement after each invocation and adds a virtual return statement to
     * the next block. Ignores certain invocations, e.g. ART methods.
     *
     * @param blockStatement The given block statement.
     * @param packageName    The package name of the AUT.
     * @return Returns a list of block statements, where a block statement is described by a list
     * of single statements.
     */
    private List<List<Statement>> splitBlockStatement(BlockStatement blockStatement, String packageName) {

        List<List<Statement>> blocks = new ArrayList<>();
        List<Statement> block = new ArrayList<>();
        List<Statement> statements = blockStatement.getStatements();
        final String method = blockStatement.getMethod();
        final Pattern exclusionPattern = properties.exclusionPattern;

        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        for (Statement statement : statements) {

            BasicStatement basicStatement = (BasicStatement) statement;
            AnalyzedInstruction analyzedInstruction = basicStatement.getInstruction();

            if (!InstructionUtils.isInvokeInstruction(analyzedInstruction)) {
                // statement belongs to current block
                block.add(statement);
            } else {

                // invoke instruction belongs to current block
                block.add(statement);

                // get the target method of the invocation
                Instruction instruction = analyzedInstruction.getInstruction();
                String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
                String className = ClassUtils.dottedClassName(MethodUtils.getClassName(targetMethod));

                /*
                 * We don't want to resolve every invocation. In particular, we don't resolve
                 * most invocations outside of the application package as well as ART methods.
                 * However, we need to resolve component invocation, reflection calls, and
                 * overridden methods in any case.
                 */
                if (((properties.resolveOnlyAUTClasses && !ClassUtils.isApplicationClass(packageName, className)
                        && (mainActivityPackage == null || !className.startsWith(mainActivityPackage)))
                        || ClassUtils.isArrayType(className)
                        || (MethodUtils.isARTMethod(targetMethod) && properties.excludeARTClasses)
                        || MethodUtils.isJavaObjectMethod(targetMethod)
                        || (exclusionPattern != null && exclusionPattern.matcher(className).matches()))
                        // we have to resolve component invocations in any case, see the code below
                        && !ComponentUtils.isComponentInvocation(components, targetMethod)
                        // we need to resolve calls using reflection in any case
                        && !MethodUtils.isReflectionCall(targetMethod)
                        // we need to resolve calls of start() or run() in any case
                        && !resolveThreadMethod(method, targetMethod)
                        // we need to resolve sendBroadcast() in any case
                        && !ReceiverUtils.isReceiverInvocation(targetMethod)
                        // we want to resolve listFiles() in any case
                        && !FileUtils.isListFilesInvocation(targetMethod)
                        // we want to resolve animations in any case
                        && !AnimationUtils.isAnimationInvocation(targetMethod)
                        // we want to resolve media player invocations in any case
                        && !MediaPlayerUtils.isMediaPlayerListenerInvocation(targetMethod)
                        // we want to resolve an audio manager invocation in any case
                        && !AudioManagerUtils.isAudioManagerInvocation(targetMethod)
                        // we want to resolve pop menu invocations in any case
                        && !PopupMenuUtils.isPopupMenuCreation(targetMethod)
                        // we want to resolve thread invocations in any case
                        && !ThreadUtils.isPostDelayMethod(targetMethod)
                        // we want to resolve thread invocations in any case
                        && !ThreadUtils.isScheduleMethod(targetMethod)
                        // we want to resolve JobScheduler invocations in any case
                        && !JobSchedulerUtils.isScheduleMethod(targetMethod)
                        // we want to resolve JobIntentService invocations in any case
                        && !ServiceUtils.isJobIntentServiceInvocation(targetMethod)
                    // TODO: may use second getOverriddenMethods() that only returns overridden methods not the method itself
                    // we need to resolve overridden methods in any case (the method itself is always returned, thus < 2)
                    // && classHierarchy.getOverriddenMethods(targetMethod, packageName, properties).size() < 2) {
                ) {
                    continue;
                }

                // save block
                blocks.add(block);

                // reset block
                block = new ArrayList<>();

                /*
                 * If we deal with a component invocation, the target method should be replaced
                 * with the constructor of the component. Here, the virtual return statement should also
                 * reflect this change.
                 */
                if (ComponentUtils.isComponentInvocation(components, targetMethod)) {
                    String componentConstructor = ComponentUtils.isComponentInvocation(components, analyzedInstruction);
                    if (componentConstructor != null && intraCFGs.containsKey(componentConstructor)) {
                        targetMethod = componentConstructor;
                    }
                } else if (MethodUtils.isReflectionCall(targetMethod)) {
                    /*
                     * If we deal with a reflective call, i.e. newInstance(), the target method
                     * should be replaced with the constructor. Here particular, the virtual return statement
                     * should also reflect this change.
                     */
                    // TODO: Replace target method with corresponding constructor.
                    LOGGER.debug("Reflection call detected!");
                } else if (FileUtils.isListFilesInvocation(targetMethod)) {
                    /*
                    * If we deal with an invocation of listFiles() the target method should be replaced by the
                    * accept() method of the respective FileFilter class.
                     */
                    // TODO: Replace target method with corresponding accept method of FileFilter class.
                }

                /*
                 * TODO: combine virtual return statements for overridden methods
                 * Since we need to over-approximate method calls, i.e. we add for each
                 * method that overrides the given method an edge, we would have actually
                 * multiple virtual return statements, but this would require here a redundant
                 * lookup of the overridden methods, which we ignore right now.
                 */

                // add return statement to next block
                block.add(new ReturnStatement(blockStatement.getMethod(), targetMethod,
                        analyzedInstruction.getInstructionIndex()));
            }
        }

        // add last block
        if (!block.isEmpty()) {
            blocks.add(block);
        }

        return blocks;
    }

    /**
     * Constructs the inter CFG without basic blocks.
     *
     * @param apk The APK file describing the app.
     */
    private void constructCFGNoBasicBlocks(APK apk) {

        LOGGER.debug("Constructing Inter CFG!");

        // exclude certain classes and methods from graph
        Pattern exclusionPattern = properties.exclusionPattern;

        final String packageName = apk.getManifest().getPackageName();

        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        // resolve the invoke vertices and connect the sub graphs with each other
        for (CFGVertex invokeVertex : getInvokeVertices()) {

            // every (invoke) statement is a basic statement (no basic blocks here)
            BasicStatement invokeStmt = (BasicStatement) invokeVertex.getStatement();

            // get target method CFG
            Instruction instruction = invokeStmt.getInstruction().getInstruction();
            String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
            String className = ClassUtils.dottedClassName(MethodUtils.getClassName(targetMethod));

            // TODO: Update exclusion rules to be consistent with basic block interCFG!

            // don't resolve non AUT classes if requested
            if (properties.resolveOnlyAUTClasses && !ClassUtils.isApplicationClass(packageName, className)
                    && (mainActivityPackage == null || !className.startsWith(mainActivityPackage))
                    // we have to resolve component invocations in any case, see the code below
                    && !ComponentUtils.isComponentInvocation(components, targetMethod)
                    // we have to resolve reflection calls in any case
                    && !MethodUtils.isReflectionCall(targetMethod)) {
                continue;
            }

            // don't resolve certain classes/methods, e.g. ART methods
            if ((exclusionPattern != null && exclusionPattern.matcher(className).matches()
                    || ClassUtils.isArrayType(className)
                    || MethodUtils.isARTMethod(targetMethod) && properties.excludeARTClasses)
                    // we have to resolve component invocations in any case
                    && !ComponentUtils.isComponentInvocation(components, targetMethod)
                    // we have to resolve reflection calls in any case
                    && !MethodUtils.isReflectionCall(targetMethod)) {
                continue;
            }

            // save the original successor vertices
            Set<CFGVertex> successors = getOutgoingEdges(invokeVertex).stream().map(CFGEdge::getTarget).collect(Collectors.toSet());

            // get the CFGs matching the invocation target (multiple for overriden methods)
            Set<BaseCFG> targetCFGs = lookupTargetCFGs(apk, invokeStmt);

            // remove edges between invoke vertex and original successors
            removeEdges(getOutgoingEdges(invokeVertex));

            // the invocation vertex defines an edge to each target CFG
            targetCFGs.forEach(targetCFG -> addEdge(invokeVertex, targetCFG.getEntry()));

            // TODO: replace target method of virtual return statement in case of component invocation or reflection call
            // TODO: handle multiple virtual return vertices for overridden methods

            // insert virtual return vertex
            ReturnStatement returnStmt = new ReturnStatement(invokeVertex.getMethod(), targetMethod,
                    invokeStmt.getInstructionIndex());
            CFGVertex returnVertex = new CFGVertex(returnStmt);
            addVertex(returnVertex);

            // add edge from exit of each target CFG to virtual return vertex
            targetCFGs.forEach(targetCFG -> addEdge(targetCFG.getExit(), returnVertex));

            // add edge from virtual return vertex to each original successor
            for (CFGVertex successor : successors) {
                addEdge(returnVertex, successor);
            }
        }
    }

    /**
     * Constructs the intra CFGs and adds them as sub graphs. In addition,
     * the name of activities and fragments are tracked. Also tracks vertices
     * containing invocations.
     *
     * @param apk            The APK file describing the app.
     * @param useBasicBlocks Whether to use basic blocks or not when constructing
     *                       the intra CFGs.
     */
    private void constructIntraCFGs(APK apk, boolean useBasicBlocks) {

        LOGGER.debug("Constructing IntraCFGs!");
        final Pattern exclusionPattern = properties.exclusionPattern;

        // we maintain the static initializers as a dedicated sub graph linked to the global entry point
        BaseCFG staticInitializersCFG = dummyIntraCFG("static initializers");
        addSubGraph(staticInitializersCFG);
        addEdge(getEntry(), staticInitializersCFG.getEntry());
        addEdge(staticInitializersCFG.getExit(), getExit());

        // track binder classes and attach them to the corresponding service
        Set<String> binderClasses = new HashSet<>();
        List<ClassDef> classes = apk.getDexFiles().stream()
                .map(DexFile::getClasses)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        final String packageName = apk.getManifest().getPackageName();
        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                String className = ClassUtils.dottedClassName(classDef.toString());

                if (properties.resolveOnlyAUTClasses && (!ClassUtils.isApplicationClass(packageName, className)
                        && (mainActivityPackage == null || !className.startsWith(mainActivityPackage)))) {
                    // don't resolve classes not belonging to AUT
                    continue;
                }

                if (ClassUtils.isResourceClass(classDef) || ClassUtils.isBuildConfigClass(classDef)) {
                    LOGGER.debug("Skipping resource/build class: " + className);
                    // skip R + BuildConfig classes
                    continue;
                }

                // as a side effect track whether the given class represents an activity, service or fragment
                if (exclusionPattern != null && !exclusionPattern.matcher(className).matches()) {

                    // re-assemble the class hierarchy
                    updateClassHierarchy(dexFile, classDef);

                    if (ComponentUtils.isActivity(classes, classDef)) {
                        components.add(new Activity(classDef, ComponentType.ACTIVITY));
                    } else if (ComponentUtils.isFragment(classes, classDef)) {
                        components.add(new Fragment(classDef, ComponentType.FRAGMENT));
                    } else if (ComponentUtils.isService(classes, classDef)) {
                        components.add(new Service(classDef, ComponentType.SERVICE));
                    } else if (ComponentUtils.isBinder(classes, classDef)) {
                        binderClasses.add(classDef.toString());
                    } else if (ComponentUtils.isApplication(classes, classDef)) {
                        components.add(new Application(classDef, ComponentType.APPLICATION));
                    } else if (ComponentUtils.isBroadcastReceiver(classes, classDef)) {
                        components.add(new BroadcastReceiver(classDef, ComponentType.BROADCAST_RECEIVER));
                    }
                }

                for (Method method : classDef.getMethods()) {

                    String methodSignature = MethodUtils.deriveMethodSignature(method);

                    // track the Android callbacks
                    if (MethodUtils.isCallback(methodSignature)) {
                        callbacks.add(methodSignature);
                    }

                    if (MenuUtils.isOnCreateMenu(methodSignature)) {
                        List<MenuItemWithResolvedTitle> menuItems = MenuUtils.getDefinedMenuItems(apk, dexFile, method)
                                .collect(Collectors.toList());
                        var component = ComponentUtils.getComponentByName(components, classDef.toString());

                        if (component.isPresent()) {
                            if (component.get() instanceof Activity) {
                                ((Activity) component.get()).addMenu(method, menuItems);
                            } else {
                                LOGGER.warn("Found menu that belongs to " + component.get().getComponentType());
                            }
                        } else {
                            LOGGER.warn("Failed to find component owning menu at " + methodSignature);
                        }
                    }

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || MethodUtils.isARTMethod(methodSignature)) {
                        // only construct dummy CFG for non ART classes
                        if (!properties.excludeARTClasses) {
                            BaseCFG intraCFG = dummyIntraCFG(method);
                            addSubGraph(intraCFG);
                            intraCFGs.put(methodSignature, intraCFG);
                        }
                    } else {
                        // exclude methods from java.lang.Object, e.g. notify()
                        if (!MethodUtils.isJavaObjectMethod(methodSignature)) {
                            LOGGER.debug("Method: " + methodSignature);

                            BaseCFG intraCFG = new IntraCFG(method, dexFile, useBasicBlocks);
                            addSubGraph(intraCFG);
                            addInvokeVertices(intraCFG.getInvokeVertices());
                            // only hold a reference to the entry and exit vertex
                            intraCFGs.put(methodSignature, new DummyCFG(intraCFG));

                            // add static initializers to dedicated sub graph
                            if (MethodUtils.isStaticInitializer(methodSignature)) {
                                addEdge(staticInitializersCFG.getEntry(), intraCFG.getEntry());
                                addEdge(intraCFG.getExit(), staticInitializersCFG.getExit());
                            }
                        }
                    }
                }
            }
        }

        LOGGER.debug("Class Hierarchy: ");
        LOGGER.debug(classHierarchy);

        // assign binder class to respective service
        for (String binderClass : binderClasses) {
            // typically binder classes are inner classes of the service
            if (ClassUtils.isInnerClass(binderClass)) {
                String serviceName = ClassUtils.getOuterClass(binderClass);
                Optional<Component> component = ComponentUtils.getComponentByName(components, serviceName);
                if (component.isPresent() && component.get().getComponentType() == ComponentType.SERVICE) {
                    Service service = (Service) component.get();
                    service.setBinder(binderClass);
                }
            }
        }

        LOGGER.debug("Generated CFGs: " + intraCFGs.size());
        LOGGER.debug("List of application classes: " + components.stream()
                .filter(c -> c.getComponentType() == ComponentType.APPLICATION).collect(Collectors.toList()));
        LOGGER.debug("List of activities: " + components.stream()
                .filter(c -> c.getComponentType() == ComponentType.ACTIVITY).collect(Collectors.toList()));
        LOGGER.debug("List of fragments: " + components.stream()
                .filter(c -> c.getComponentType() == ComponentType.FRAGMENT).collect(Collectors.toList()));
        LOGGER.debug("List of services: " + components.stream()
                .filter(c -> c.getComponentType() == ComponentType.SERVICE).collect(Collectors.toList()));
        LOGGER.debug("List of broadcast receivers: " + components.stream()
                .filter(c -> c.getComponentType() == ComponentType.BROADCAST_RECEIVER).collect(Collectors.toList()));
        LOGGER.debug("Invoke Vertices: " + getInvokeVertices().size());

        for (Component component : components) {
            final List<String> superClasses = classHierarchy.getSuperClasses(component.getClazz());
            LOGGER.debug("Super classes of component " + component + " are: " + superClasses);
            component.setSuperClasses(superClasses);
        }
    }

    /**
     * Updates the class hierarchy map with information of the given class and its super class
     * and interfaces, respectively.
     *
     * @param dexFile  The dex file containing the current class.
     * @param classDef The given class.
     */
    private void updateClassHierarchy(final DexFile dexFile, final ClassDef classDef) {

        // TODO: Speed up class hierarchy construction. At least inner classes can be derived iteratively.
        ClassDef superClass = ClassUtils.getSuperClass(dexFile, classDef);
        Set<ClassDef> interfaces = ClassUtils.getInterfaces(dexFile, classDef);
        Set<ClassDef> innerClasses = ClassUtils.getInnerClasses(dexFile, classDef);
        classHierarchy.addClass(classDef, superClass, interfaces, innerClasses);
    }

    /**
     * Creates and adds a dummy CFG for the given method.
     *
     * @param targetMethod The method for which a dummy CFG should be generated.
     * @return Returns the constructed dummy CFG.
     */
    private BaseCFG dummyCFG(String targetMethod) {
        BaseCFG targetCFG = dummyIntraCFG(targetMethod);
        intraCFGs.put(targetMethod, targetCFG);
        addSubGraph(targetCFG);
        return targetCFG;
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraCFG(Method targetMethod) {
        BaseCFG cfg = new DummyCFG(MethodUtils.deriveMethodSignature(targetMethod));
        cfg.addEdge(cfg.getEntry(), cfg.getExit());
        return cfg;
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraCFG(String targetMethod) {
        BaseCFG cfg = new DummyCFG(targetMethod);
        cfg.addEdge(cfg.getEntry(), cfg.getExit());
        return cfg;
    }

    /**
     * Constructs a CFG solely consisting of entry and exit vertex.
     * As a side effect, the CFG is added to the inter CFG.
     *
     * @param descriptor The name of the CFG.
     * @return Returns the created CFG.
     */
    private BaseCFG emptyCFG(String descriptor) {
        BaseCFG cfg = new DummyCFG(descriptor);
        intraCFGs.put(descriptor, cfg);
        addSubGraph(cfg);
        return cfg;
    }

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    // TODO: check if deep copy of vertices and edges is necessary
    @Override
    @SuppressWarnings("unused")
    public BaseCFG copy() {
        BaseCFG clone = new InterCFG(getMethodName());

        Graph<CFGVertex, CFGEdge> graphClone = GraphTypeBuilder
                .<CFGVertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                .edgeClass(CFGEdge.class).buildGraph();

        Set<CFGVertex> vertices = graph.vertexSet();
        Set<CFGEdge> edges = graph.edgeSet();

        Cloner cloner = new Cloner();

        for (CFGVertex vertex : vertices) {
            graphClone.addVertex(cloner.deepClone(vertex));
        }

        for (CFGEdge edge : edges) {
            CFGVertex src = cloner.deepClone(edge.getSource());
            CFGVertex dest = cloner.deepClone(edge.getTarget());
            graphClone.addEdge(src, dest);
        }

        clone.graph = graphClone;
        return clone;
    }

    /**
     * Searches for the vertex described by the given trace in the graph.
     * <p>
     * Searching an entry/exit vertex can be satisfied in O(1).
     * When a search of an intermediate vertex is requested, all directed
     * paths from the subgraph are traversed in a parallel manner.
     *
     * @param trace The trace describing the vertex, i.e. className->methodName->(entry|exit|instructionIndex).
     * @return Returns the vertex corresponding to the given trace.
     */
    @Override
    public CFGVertex lookUpVertex(String trace) {

        /*
         * A trace has the following form:
         *   className -> methodName -> ([entry|exit|if|switch])? -> (index)?
         *
         * The first two components are always fixed, while the instruction type and the instruction index
         * are optional, but not both at the same time:
         *
         * Making the instruction type optional allows to search (by index) for a custom instruction, e.g. a branch.
         * Making the index optional allows to look up virtual entry and exit vertices as well as if and switch vertices.
         */
        String[] tokens = trace.split("->");

        // retrieve fully qualified method name (class name + method name)
        final String method = tokens[0] + "->" + tokens[1];

        // check whether method belongs to graph
        if (!intraCFGs.containsKey(method)) {
            throw new IllegalArgumentException("Given trace refers to a method not part of the graph: " + method);
        }

        if (tokens.length == 3) {

            if (tokens[2].equals("entry")) {
                return intraCFGs.get(method).getEntry();
            } else if (tokens[2].equals("exit")) {
                return intraCFGs.get(method).getExit();
            } else {
                // lookup of a branch
                int instructionIndex = Integer.parseInt(tokens[2]);
                CFGVertex entry = intraCFGs.get(method).getEntry();
                return lookUpVertex(method, instructionIndex, entry);
            }

        } else if (tokens.length == 4) {

            // String instructionType = tokens[2];
            int instructionIndex = Integer.parseInt(tokens[3]);
            CFGVertex entry = intraCFGs.get(method).getEntry();
            return lookUpVertex(method, instructionIndex, entry);

        } else if (tokens.length == 5) { // basic block coverage trace

            int instructionIndex = Integer.parseInt(tokens[2]);
            CFGVertex entry = intraCFGs.get(method).getEntry();
            return lookUpVertex(method, instructionIndex, entry);

        } else {
            throw new IllegalArgumentException("Unrecognized trace: " + trace);
        }
    }

    /**
     * Looks up a vertex in the graph.
     *
     * @param method The method the vertex belongs to.
     * @param instructionIndex The instruction index.
     * @param entry The entry vertex of the given method (bound for the look up).
     * @param exit The exit vertex of the given method (bound for the look up).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     *         a {@link IllegalArgumentException} is thrown.
     */
    @SuppressWarnings("unused")
    private CFGVertex lookUpVertex(String method, int instructionIndex, CFGVertex entry, CFGVertex exit) {

        /*
         * If the 'AllDirectedPaths' algorithm appears to be too slow, we could alternatively use
         * some traversal strategy supplied by JGraphT, see
         * https://jgrapht.org/javadoc-1.4.0/org/jgrapht/traverse/package-summary.html.
         * If this is still not good enough, we can roll out our own search algorithm. One could
         * perform a parallel forward/backward search starting from the entry and exit vertex, respectively.
         * If a forward/backward step falls out of the given method, i.e. a vertex of a different method is reached,
         * we can directly jump from the entry vertex to the virtual return vertex in case of a forward step,
         * otherwise (a backward step was performed) we can directly jump to the invoke vertex leading to
         * the entry of the different method.
         */

        AllDirectedPaths<CFGVertex, CFGEdge> allDirectedPaths = new AllDirectedPaths<>(graph);

        /*
         * In general this algorithm is really fast, however, when dealing with cycles in the graph, the algorithm
         * fails to find the desired vertex. If we adjust the 'simplePathsOnly' parameter to handle cycles the
         * algorithm can be really slow.
         */
        List<GraphPath<CFGVertex, CFGEdge>> paths = allDirectedPaths.getAllPaths(entry, exit, true, null);

        // https://stackoverflow.com/questions/64929090/nested-parallel-stream-execution-in-java-findany-randomly-fails
        return paths.parallelStream().flatMap(path -> path.getEdgeList().parallelStream()
                .map(edge -> {
                    CFGVertex source = edge.getSource();
                    CFGVertex target = edge.getTarget();

                    LOGGER.debug("Inspecting source vertex: " + source);
                    LOGGER.debug("Inspecting target vertex: " + target);


                    if (source.containsInstruction(method, instructionIndex)) {
                        return source;
                    } else if (target.containsInstruction(method, instructionIndex)) {
                        return target;
                    } else {
                        return null;
                    }
                }).filter(Objects::nonNull)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Given trace refers to no vertex in graph!"));
    }

    /**
     * Performs a depth first search for looking up the vertex.
     *
     * @param method The method describing the vertex.
     * @param instructionIndex The instruction index of the vertex (the wrapped instruction).
     * @param entry The entry vertex of the method (limits the search).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     *         a {@link IllegalArgumentException} is thrown.
     */
    @SuppressWarnings("unused")
    private CFGVertex lookUpVertexDFS(String method, int instructionIndex, CFGVertex entry) {

        DepthFirstIterator<CFGVertex, CFGEdge> dfs = new DepthFirstIterator<>(graph, entry);

        while (dfs.hasNext()) {
            CFGVertex vertex = dfs.next();
            if (vertex.containsInstruction(method, instructionIndex)) {
                return vertex;
            }
        }

        throw new IllegalArgumentException("Given trace refers to no vertex in graph!");
    }

    /**
     * Performs a breadth first search for looking up the vertex.
     *
     * @param method The method describing the vertex.
     * @param instructionIndex The instruction index of the vertex (the wrapped instruction).
     * @param entry The entry vertex of the method (limits the search).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     *         a {@link IllegalArgumentException} is thrown.
     */
    private CFGVertex lookUpVertex(String method, int instructionIndex, CFGVertex entry) {

        BreadthFirstIterator<CFGVertex, CFGEdge> bfs = new BreadthFirstIterator<>(graph, entry);

        while (bfs.hasNext()) {
            CFGVertex vertex = bfs.next();
            if (vertex.containsInstruction(method, instructionIndex)) {
                return vertex;
            }
        }

        throw new IllegalArgumentException("Given trace refers to no vertex in graph!");
    }

    /**
     * Prints the isolated sub graphs (methods). Solely for debugging.
     */
    @SuppressWarnings("debug")
    public void printIsolatedSubGraphs() {

        Set<String> isolatedSubGraphs = new HashSet<>();

        int constructors = 0;
        int callbacks = 0;
        int resourceClassMethods = 0;

        final String packageName = apk.getManifest().getPackageName();
        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        for (BaseCFG cfg : intraCFGs.values()) {
            String className = ClassUtils.dottedClassName(MethodUtils.getClassName(cfg.getMethodName()));
            if (getIncomingEdges(cfg.getEntry()).isEmpty()
                    && (ClassUtils.isApplicationClass(packageName, className)
                    || (mainActivityPackage != null && className.startsWith(mainActivityPackage)))) {

                String methodName = cfg.getMethodName();

                LOGGER.debug("Isolated method: " + methodName);
                isolatedSubGraphs.add(methodName);

                if (MethodUtils.isConstructorCall(methodName)) {
                    LOGGER.debug("Isolated constructor!");
                    constructors++;
                } else if (MethodUtils.isCallback(methodName)) {
                    LOGGER.debug("Isolated callback!");
                    callbacks++;
                } else if (ClassUtils.isResourceClass(MethodUtils.getClassName(methodName))) {
                    LOGGER.debug("Isolated resource class method!");
                    resourceClassMethods++;
                }
            }
        }

        LOGGER.debug("Number of isolated sub graphs: " + isolatedSubGraphs.size());
        LOGGER.debug("Number of isolated constructors: " + constructors);
        LOGGER.debug("Number of isolated callbacks: " + callbacks);
        LOGGER.debug("Number of isolated resource class methods: " + resourceClassMethods);

        /*
         * As an alternative one could print all methods that are not reachable from the global entry vertex.
         * However, the computation is very expensive in terms of time. If you really like to have this information,
         * the code could look as follows:
         *
         * for (Vertex vertex : getVertices()) {
         *     if (getShortestDistance(getEntry(), vertex) == -1) {
         *         if (vertex.isEntryVertex() && className.startsWith(apk.getManifest().getPackageName())) {
         *               LOGGER.debug("Isolated method: " + methodName);
         */
    }
}
