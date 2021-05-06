package de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.*;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.LayoutFile;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.Manifest;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Edge;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Vertex;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.ReturnStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;
import de.uni_passau.fim.auermich.android_graphs.core.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.DexClassProvider;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InterCFG extends BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(InterCFG.class);
    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    // whether for ART classes solely a dummy intra CFG should be generated
    private boolean excludeARTClasses;

    // whether only classes of AUT should be included and resolved
    private boolean resolveOnlyAUTClasses;

    // track the set of components
    private Set<Component> components = new HashSet<>();

    /**
     * Maintains a reference to the individual intra CFGs.
     * NOTE: Only a reference to the entry and exit vertex is hold!
     */
    Map<String, BaseCFG> intraCFGs = new HashMap<>();

    // necessary for the copy constructor
    public InterCFG(String graphName) {
        super(graphName);
    }

    public InterCFG(String graphName, APK apk, boolean useBasicBlocks,
                    boolean excludeARTClasses, boolean resolveOnlyAUTClasses) {
        super(graphName);
        this.excludeARTClasses = excludeARTClasses;
        this.resolveOnlyAUTClasses = resolveOnlyAUTClasses;
        constructCFG(apk, useBasicBlocks);
    }

    private void constructCFG(APK apk, boolean useBasicBlocks) {

        // decode APK to access manifest and other resource files
        apk.decodeAPK();

        // parse manifest
        apk.setManifest(Manifest.parse(new File(apk.getDecodingOutputPath(), "AndroidManifest.xml")));

        // create the individual intraCFGs and add them as sub graphs
        constructIntraCFGs(apk, useBasicBlocks);

        if (useBasicBlocks) {
            constructCFGWithBasicBlocks(apk);
        } else {
            constructCFG(apk);
        }

        LOGGER.debug("Removing decoded APK files: " + Utility.removeFile(apk.getDecodingOutputPath()));
    }

    /**
     * Constructs the inter CFG using basic blocks for a given app.
     *
     * @param apk The APK file describing the app.
     */
    private void constructCFGWithBasicBlocks(APK apk) {

        LOGGER.debug("Constructing Inter CFG with basic blocks!");

        // exclude certain classes and methods from graph
        Pattern exclusionPattern = Utility.readExcludePatterns();

        // collect the callback entry points
        Map<String, BaseCFG> callbackEntryPoints = new HashMap<>();

        final String packageName = apk.getManifest().getPackageName();

        // resolve the invoke vertices and connect the sub graphs with each other
        for (Vertex invokeVertex : getInvokeVertices()) {

            BlockStatement blockStatement = (BlockStatement) invokeVertex.getStatement();

            // record which activity is hosting which fragments
            for (Statement statement : blockStatement.getStatements()) {
                BasicStatement basicStmt = (BasicStatement) statement;
                checkForFragmentInvocation(components, basicStmt);
            }

            // split vertex into blocks (split after each invoke instruction + insert virtual return statement)
            List<List<Statement>> blocks = splitBlockStatement(blockStatement, packageName, exclusionPattern);

            if (blocks.size() == 1) {
                LOGGER.debug("Unchanged vertex: " + invokeVertex + " [" + invokeVertex.getMethod() + "]");
                // the vertex is not split, no need to delete and re-insert the vertex
                continue;
            }

            LOGGER.debug("Invoke Vertex: " + invokeVertex + " [" + invokeVertex.getMethod() + "]");

            // save the predecessors and successors as we remove the vertex
            Set<Vertex> predecessors = getIncomingEdges(invokeVertex).stream().map(Edge::getSource).collect(Collectors.toSet());
            Set<Vertex> successors = getOutgoingEdges(invokeVertex).stream().map(Edge::getTarget).collect(Collectors.toSet());

            // remove original vertex, inherently removes edges
            removeVertex(invokeVertex);

            List<Vertex> blockVertices = new ArrayList<>();
            List<Vertex> exitVertices = new ArrayList<>();

            for (int i = 0; i < blocks.size(); i++) {

                // create a new vertex for each block
                List<Statement> block = blocks.get(i);
                Statement blockStmt = new BlockStatement(invokeVertex.getMethod(), block);
                Vertex blockVertex = new Vertex(blockStmt);
                blockVertices.add(blockVertex);

                // add modified block vertex to graph
                addVertex(blockVertex);

                // first block, add original predecessors to first block
                if (i == 0) {
                    for (Vertex predecessor : predecessors) {
                        // handle self-references afterwards
                        if (!predecessor.equals(invokeVertex)) {
                            addEdge(predecessor, blockVertex);
                        }
                    }
                }

                // last block, add original successors to the last block
                if (i == blocks.size() - 1) {
                    // LOGGER.debug("Number of successors: " + successors.size());
                    for (Vertex successor : successors) {
                        // handle self-references afterwards
                        if (!successor.equals(invokeVertex)) {
                            addEdge(blockVertex, successor);
                        }
                    }
                    // the last block doesn't contain any invoke instruction -> no target CFG
                    break;
                }

                // look up the CFG matching the invocation target
                BasicStatement invokeStmt = (BasicStatement) ((BlockStatement) blockStmt).getLastStatement();
                BaseCFG targetCFG = lookupTargetCFG(invokeStmt);

                // the invoke vertex defines an edge to the invocation target
                addEdge(blockVertex, targetCFG.getEntry());

                // save exit vertex -> there is an edge to the return vertex part of the next basic block
                exitVertices.add(targetCFG.getExit());
            }

            // connect the exit of the invocation target with the virtual return vertex
            for (int i = 0; i < exitVertices.size(); i++) {
                addEdge(exitVertices.get(i), blockVertices.get(i + 1));
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

        // add lifecycle of components + global entry points
        addLifecycle(components, callbackEntryPoints);

        // add the callbacks specified either through XML or directly in code
        addCallbacks(apk, callbackEntryPoints);
    }

    /**
     * Looks up the target CFG matching the invoke statement.
     * As a side effect, component invocation, e.g. calls to startActivity(), are replaced
     * by the invoked component's constructor.
     *
     * @param invokeStmt The invoke statement defining the target.
     * @return Returns the CFG matching the given invoke target.
     */
    private BaseCFG lookupTargetCFG(final BasicStatement invokeStmt) {

        Instruction instruction = invokeStmt.getInstruction().getInstruction();
        String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

        if (intraCFGs.containsKey(targetMethod)) {
            return intraCFGs.get(targetMethod);
        } else {
            /*
             * If there is a component invocation, e.g. a call to startActivity(), we
             * replace the targetCFG with the constructor of the respective component.
             */
            if (Utility.isComponentInvocation(components, targetMethod)) {
                String componentConstructor = Utility.isComponentInvocation(components, invokeStmt.getInstruction());
                if (componentConstructor != null) {
                    if (intraCFGs.containsKey(componentConstructor)) {
                        return intraCFGs.get(componentConstructor);
                    } else {
                        LOGGER.warn("Constructor " + componentConstructor + " not contained in dex files!");
                    }
                } else {
                    LOGGER.warn("Couldn't derive component constructor for method: " + targetMethod);
                }
            } else {
                /*
                 * There are some Android specific classes, e.g. android/view/View, which are
                 * not included in the classes.dex file.
                 */
                LOGGER.warn("Method " + targetMethod + " not contained in dex files!");
            }
        }

        // we provide a dummy CFG for those invocations we couldn't derive the target
        BaseCFG targetCFG = dummyIntraCFG(targetMethod);
        intraCFGs.put(targetMethod, targetCFG);
        addSubGraph(targetCFG);
        return targetCFG;
    }

    /**
     * Tracks whether the given statement refers to a fragment invocation. If this is the case,
     * the activity hosting the fragment is updated with this information.
     *
     * @param components The set of components.
     * @param basicStmt The statement wrapping a single instruction.
     */
    private void checkForFragmentInvocation(final Set<Component> components, BasicStatement basicStmt) {

        if (Utility.isInvokeInstruction(basicStmt.getInstruction())) {

            Instruction instruction = basicStmt.getInstruction().getInstruction();
            String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();

            // track which fragments are hosted by which activity
            if (Utility.isFragmentInvocation(targetMethod)) {
                // try to derive the fragment name
                String fragmentName = Utility.isFragmentInvocation(basicStmt.getInstruction());

                if (fragmentName != null) {
                    String activityName = Utility.getClassName(basicStmt.getMethod());

                    Optional<Component> activityComponent = Utility.getComponentByName(components, activityName);
                    Optional<Component> fragmentComponent = Utility.getComponentByName(components, fragmentName);

                    if (activityComponent.isPresent() && fragmentComponent.isPresent()) {
                        Activity activity = (Activity) activityComponent.get();
                        Fragment fragment = (Fragment) fragmentComponent.get();
                        activity.addHostingFragment(fragment);
                    }
                } else {
                    LOGGER.warn("Couldn't derive fragment for target method: " + targetMethod);
                }
            }
        }
    }

    /**
     * Adds the lifecycle to the given components. In addition, the callback entry points are tracked
     * and the global entry points are defined for activities.
     *
     * @param components The set of components.
     * @param callbackEntryPoints The set of callback entry points.
     */
    private void addLifecycle(final Set<Component> components, Map<String, BaseCFG> callbackEntryPoints) {

        // add activity and fragment lifecycle as well as global entry point for activities
        components.stream().filter(c -> c.getComponentType() == ComponentType.ACTIVITY).forEach(activityComponent -> {
            Activity activity = (Activity) activityComponent;
            BaseCFG callbackEntryPoint = addAndroidActivityLifecycle(activity, activity.getHostingFragments());
            callbackEntryPoints.put(activity.getName(), callbackEntryPoint);
            String onCreateMethod = activity.getName() + "->onCreate(Landroid/os/Bundle;)V";
            addGlobalEntryPoint(intraCFGs.get(onCreateMethod));
        });

        // add service lifecycle
        components.stream().filter(c -> c.getComponentType() == ComponentType.SERVICE).forEach(service -> {
            addAndroidServiceLifecycle((Service) service);
        });
    }

    /**
     * Adds the lifecycle of started and/or bound services.
     *
     * @param service The service for which the lifecycle should be added.
     */
    private void addAndroidServiceLifecycle(Service service) {

        String constructor = service.getName() + "-><init>()V";

        if (!intraCFGs.containsKey(constructor)) {
            LOGGER.warn("Service without explicit constructor: " + service);
        } else {

            // TODO: onCreate() is optional, it's not mandatory to overwrite it, may neglect it
            BaseCFG ctr = intraCFGs.get(constructor);
            String onCreateMethod = service.getName() + "->onCreate()V";

            // the constructor directly invokes onCreate()
            BaseCFG onCreate = addLifecycle(onCreateMethod, ctr);

            /*
            * The method onStartCommand() is only invoked if the service is invoked via Context.startService(), see:
            * https://developer.android.com/reference/android/app/Service#onStartCommand(android.content.Intent,%20int,%20int)
            * If there is a custom onStartCommand() present, we include it, otherwise it's ignored.
             */
            BaseCFG nextLifeCycle = onCreate;
            String onStartCommandMethod = service.getName() + "->onStartCommand(Landroid/content/Intent;II)I";
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
                        String method = Utility.getMethodName(((ReferenceInstruction) invoke).getReference().toString());
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
            String onStartMethod = service.getName() + "->onStart(Landroid/content/Intent;I)V";
            if (intraCFGs.containsKey(onStartMethod)) {
                nextLifeCycle = addLifecycle(onStartMethod, nextLifeCycle);
            }

            BaseCFG callbacksCFG = dummyIntraCFG("callbacks " + service.getName());
            addSubGraph(callbacksCFG);

            // there is always an edge from onCreate()/onStartCommand()/onStart() to the callbacks entry point
            addEdge(nextLifeCycle.getExit(), callbacksCFG.getEntry());

            /*
            * If the service is a bound service, i.e. called by bindService(), there is an edge
            * from onCreate() to the callbacks entry point. In particular, bindService() doesn't invoke
            * onStartCommand() nor onStart().
             */
            if (service.isBound()) {
                addEdge(onCreate.getExit(), callbacksCFG.getEntry());
            }

            // Every service must provide overwrite onBind() independent whether it is a started or bound service.
            String onBindMethod = service.getName() + "->onBind(Landroid/content/Intent;)Landroid/os/IBinder;";
            BaseCFG onBind = intraCFGs.get(onBindMethod);
            addEdge(callbacksCFG.getEntry(), onBind.getEntry());
            addEdge(onBind.getExit(), callbacksCFG.getExit());

            // the service may overwrite onUnBind()
            String onUnbindMethod = service.getName() + "->onUnbind(Landroid/content/Intent;)Z";
            if (intraCFGs.containsKey(onUnbindMethod)) {
                BaseCFG onUnbind = intraCFGs.get(onUnbindMethod);
                addEdge(callbacksCFG.getEntry(), onUnbind.getEntry());
                addEdge(onUnbind.getExit(), callbacksCFG.getExit());
            }

            // multiple callbacks might be invoked consecutively
            addEdge(callbacksCFG.getExit(), callbacksCFG.getEntry());

            // the service may overwrite onDestroy()
            String onDestroyMethod = service.getName() + "->onDestroy()V";
            if (intraCFGs.containsKey(onDestroyMethod)) {
                BaseCFG onDestroy = intraCFGs.get(onDestroyMethod);
                addEdge(callbacksCFG.getExit(), onDestroy.getEntry());
            }

            // TODO: integrate binder class for bound services

            // TODO: If onStartCommand() is present, we need to add another incoming edge to it from
            //  the startService() call. This is the case, if the service is invoked multiple times.
            //  Check predecessor of onStartCommand entry for 'startService() invoke call!
        }
    }

    /**
     * Adds for each component (activity) a global entry point to the respective constructor. Additionally, an edge
     * is created between constructor CFG and the onCreate CFG since the constructor is called prior to onCreate().
     *
     * @param onCreateCFG The set of onCreate methods (the respective CFGs).
     */
    private void addGlobalEntryPoint(BaseCFG onCreateCFG) {

        // each component defines a default constructor, which is called prior to onCreate()
        String className = Utility.getClassName(onCreateCFG.getMethodName());
        String constructorName = className + "-><init>()V";

        if (intraCFGs.containsKey(constructorName)) {
            BaseCFG constructor = intraCFGs.get(constructorName);
            addEdge(constructor.getExit(), onCreateCFG.getEntry());

            // add global entry point to constructor
            addEdge(getEntry(), constructor.getEntry());
        }
    }

    /**
     * Connects all lifecycle methods with each other for a given activity. Also
     * integrates the fragment lifecycle.
     *
     * @param activity The activity for which the lifecycle should be added.
     * @param fragments   The name of fragments hosted by the given activity.
     * @return Returns the sub graph defining the callbacks, which are either declared
     * directly inside the code or statically via the layout files.
     */
    private BaseCFG addAndroidActivityLifecycle(final Activity activity, final Set<Fragment> fragments) {

        String onCreateMethod = activity.getName() + "->onCreate(Landroid/os/Bundle;)V";

        // although every activity should overwrite onCreate, there are rare cases that don't follow this rule
        if (!intraCFGs.containsKey(onCreateMethod)) {
            BaseCFG onCreate = dummyIntraCFG(onCreateMethod);
            intraCFGs.put(onCreateMethod, onCreate);
            addSubGraph(onCreate);
        }

        BaseCFG onCreateCFG = intraCFGs.get(onCreateMethod);
        LOGGER.debug("Activity " + activity + " defines the following fragments: " + fragments);

        String methodName = onCreateCFG.getMethodName();
        String className = Utility.getClassName(methodName);

        // if there are fragments, onCreate invokes onAttach, onCreate and onCreateView
        for (Fragment fragment : fragments) {

            // TODO: there is a deprecated onAttach using an activity instance as parameter
            String onAttachFragment = fragment.getName() + "->onAttach(Landroid/content/Context;)V";
            BaseCFG onAttachFragmentCFG = addLifecycle(onAttachFragment, onCreateCFG);

            String onCreateFragment = fragment.getName() + "->onCreate(Landroid/os/Bundle;)V";
            BaseCFG onCreateFragmentCFG = addLifecycle(onCreateFragment, onAttachFragmentCFG);

            String onCreateViewFragment = fragment.getName() + "->onCreateView(Landroid/view/LayoutInflater;" +
                    "Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;";
            BaseCFG onCreateViewFragmentCFG = addLifecycle(onCreateViewFragment, onCreateFragmentCFG);

            String onActivityCreatedFragment = fragment.getName() + "->onActivityCreated(Landroid/os/Bundle;)V";
            BaseCFG onActivityCreatedFragmentCFG = addLifecycle(onActivityCreatedFragment, onCreateViewFragmentCFG);

            // according to https://developer.android.com/reference/android/app/Fragment -> onViewStateRestored
            String onViewStateRestoredFragment = fragment.getName() + "->onViewStateRestored(Landroid/os/Bundle;)V";
            BaseCFG onViewStateRestoredFragmentCFG = addLifecycle(onViewStateRestoredFragment, onActivityCreatedFragmentCFG);

            // go back to onCreate() exit
            addEdge(onViewStateRestoredFragmentCFG.getExit(), onCreateCFG.getExit());
        }

        // onCreate directly invokes onStart()
        String onStart = className + "->onStart()V";
        BaseCFG onStartCFG = addLifecycle(onStart, onCreateCFG);

        // if there are fragments, onStart() is invoked
        for (Fragment fragment : fragments) {
            String onStartFragment = fragment.getName() + "->onStart()V";
            BaseCFG onStartFragmentCFG = addLifecycle(onStartFragment, onStartCFG);

            // go back to onStart() exit
            addEdge(onStartFragmentCFG.getExit(), onStartCFG.getExit());
        }

        String onResume = className + "->onResume()V";
        BaseCFG onResumeCFG = addLifecycle(onResume, onStartCFG);

        // if there are fragments, onResume() is invoked
        for (Fragment fragment : fragments) {
            String onResumeFragment = fragment.getName() + "->onResume()V";
            BaseCFG onResumeFragmentCFG = addLifecycle(onResumeFragment, onResumeCFG);

            // go back to onResume() exit
            addEdge(onResumeFragmentCFG.getExit(), onResumeCFG.getExit());
        }

        /*
         * Each component may define several listeners for certain events, e.g. a button click,
         * which causes the invocation of a callback function. Those callbacks are active as
         * long as the corresponding component (activity) is in the onResume state. Thus, in our
         * graph we have an additional sub-graph 'callbacks' that is directly linked to the end
         * of 'onResume()' and can either call one of the specified listeners or directly invoke
         * the onPause() method (indirectly through the entry-exit edge). Each listener function
         * points back to the 'callbacks' entry node.
         */

        // TODO: right now all callbacks are handled central, no distinction between callbacks from activities and fragments

        // add callbacks sub graph
        BaseCFG callbacksCFG = dummyIntraCFG("callbacks " + className);
        addSubGraph(callbacksCFG);

        // callbacks can be invoked after onResume() has finished
        addEdge(onResumeCFG.getExit(), callbacksCFG.getEntry());

        // there can be a sequence of callbacks (loop)
        addEdge(callbacksCFG.getExit(), callbacksCFG.getEntry());

        // onPause() can be invoked after some callback
        String onPause = className + "->onPause()V";
        BaseCFG onPauseCFG = addLifecycle(onPause, callbacksCFG);

        // if there are fragments, onPause() is invoked
        for (Fragment fragment : fragments) {

            String onPauseFragment = fragment.getName() + "->onPause()V";
            BaseCFG onPauseFragmentCFG = addLifecycle(onPauseFragment, onPauseCFG);

            // go back to onPause() exit
            addEdge(onPauseFragmentCFG.getExit(), onPauseCFG.getExit());
        }

        String onStop = className + "->onStop()V";
        BaseCFG onStopCFG = addLifecycle(onStop, onPauseCFG);

        // if there are fragments, onStop() is invoked
        for (Fragment fragment : fragments) {

            String onStopFragment = fragment.getName() + "->onStop()V";
            BaseCFG onStopFragmentCFG = addLifecycle(onStopFragment, onStopCFG);

            // go back to onStop() exit
            addEdge(onStopFragmentCFG.getExit(), onStopCFG.getExit());
        }

        String onDestroy = className + "->onDestroy()V";
        BaseCFG onDestroyCFG = addLifecycle(onDestroy, onStopCFG);

        // if there are fragments, onDestroy, onDestroyView and onDetach are invoked
        for (Fragment fragment : fragments) {

            String onDestroyViewFragment = fragment.getName() + "->onDestroyView()V";
            BaseCFG onDestroyViewFragmentCFG = addLifecycle(onDestroyViewFragment, onDestroyCFG);

            // onDestroyView() can also invoke onCreateView()
            String onCreateViewFragment = fragment.getName() + "->onCreateView(Landroid/view/LayoutInflater;" +
                    "Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;";
            BaseCFG onCreateViewFragmentCFG = intraCFGs.get(onCreateViewFragment);
            addEdge(onDestroyViewFragmentCFG.getExit(), onCreateViewFragmentCFG.getEntry());

            String onDestroyFragment = fragment.getName() + "->onDestroy()V";
            BaseCFG onDestroyFragmentCFG = addLifecycle(onDestroyFragment, onDestroyViewFragmentCFG);

            String onDetachFragment = fragment.getName() + "->onDetach()V";
            BaseCFG onDetachFragmentCFG = addLifecycle(onDetachFragment, onDestroyFragmentCFG);

            // go back to onDestroy() exit
            addEdge(onDetachFragmentCFG.getExit(), onDestroyCFG.getExit());
        }

        // onPause can also invoke onResume()
        addEdge(onPauseCFG.getExit(), onResumeCFG.getEntry());

        // onStop can also invoke onRestart()
        String onRestart = className + "->onRestart()V";
        BaseCFG onRestartCFG = addLifecycle(onRestart, onStopCFG);

        // onRestart invokes onStart()
        addEdge(onRestartCFG.getExit(), onStartCFG.getEntry());

        return callbacksCFG;
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
     * Adds callbacks to the respective components. We both consider callbacks defined
     * inside layout files as well as programmatically defined callbacks.
     *
     * @param apk                 The APK file describing the app.
     * @param callbackEntryPoints Maintains a mapping between a component and its callback entry point.
     */
    private void addCallbacks(APK apk, Map<String, BaseCFG> callbackEntryPoints) {

        // retrieve callbacks declared in code
        Multimap<String, BaseCFG> callbacks = lookUpCallbacks();

        // add for each android component callbacks declared in code to its 'callbacks' subgraph
        for (Map.Entry<String, BaseCFG> callbackEntryPoint : callbackEntryPoints.entrySet()) {
            callbacks.get(callbackEntryPoint.getKey()).forEach(callback -> {
                addEdge(callbackEntryPoint.getValue().getEntry(), callback.getEntry());
                addEdge(callback.getExit(), callbackEntryPoint.getValue().getExit());
            });
        }

        // retrieve callbacks declared in XML files
        Multimap<String, BaseCFG> callbacksXML = lookUpCallbacksXML(apk);

        // add for each android component callbacks declared in XML to its 'callbacks' subgraph
        for (Map.Entry<String, BaseCFG> callbackEntryPoint : callbackEntryPoints.entrySet()) {
            callbacksXML.get(callbackEntryPoint.getKey()).forEach(callback -> {
                addEdge(callbackEntryPoint.getValue().getEntry(), callback.getEntry());
                addEdge(callback.getExit(), callbackEntryPoint.getValue().getExit());
            });
        }
    }

    /**
     * Returns for each component, e.g. an activity, its associated callbacks. It goes through all
     * intra CFGs looking for a specific callback by its full-qualified name. If there is a match,
     * we extract the defining component, which is typically the outer class, and the CFG representing
     * the callback.
     *
     * @return Returns a mapping between a component and its associated callbacks (can be multiple per instance).
     */
    private Multimap<String, BaseCFG> lookUpCallbacks() {

        /*
         * Rather than searching for the call of e.g. setOnClickListener() and following
         * the invocation to the corresponding onClick() method defined by some inner class,
         * we can directly search for the onClick() method and query the outer class (the component
         * defining the callback). We don't even need to go through the code, we can actually
         * look up in the set of intra CFGs for a specific listener through its FQN. To get
         * the outer class, we need to inspect the FQN of the inner class, which is of the following form:
         *       Lmy/package/OuterClassName$InnerClassName;
         * This means, we need to split the FQN at the '$' symbol to retrieve the name of the outer class.
         *
         * NOTE: This is solely an heuristic, we assume that the listener is defined within the class
         * that makes use of it, which is the typical behaviour.
         */

        // key: FQN of component defining a callback (may define several ones)
        Multimap<String, BaseCFG> callbacks = TreeMultimap.create();

        Pattern exclusionPattern = Utility.readExcludePatterns();

        for (Map.Entry<String, BaseCFG> intraCFG : intraCFGs.entrySet()) {
            String methodName = intraCFG.getKey();
            String className = Utility.getClassName(methodName);

            if (exclusionPattern != null && !exclusionPattern.matcher(Utility.dottedClassName(className)).matches()
                    // TODO: add missing callbacks for each event listener
                    // see: https://developer.android.com/guide/topics/ui/ui-events
                    // TODO: check whether there can be other custom event listeners
                    && (methodName.endsWith("onClick(Landroid/view/View;)V")
                    || methodName.endsWith("onLongClick(Landroid/view/View;)Z")
                    || methodName.endsWith("onFocusChange(Landroid/view/View;Z)V")
                    || methodName.endsWith("onKey(Landroid/view/View;ILandroid/view/KeyEvent;)Z")
                    || methodName.endsWith("onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z")
                    || methodName.endsWith("onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V"))) {
                // TODO: is it always an inner class???
                if (Utility.isInnerClass(methodName)) {
                    String outerClass = Utility.getOuterClass(className);
                    callbacks.put(outerClass, intraCFG.getValue());
                }
            }
        }
        return callbacks;
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

        // return value, key: name of component
        Multimap<String, BaseCFG> callbacks = TreeMultimap.create();

        // stores the relation between outer and inner classes
        Multimap<String, String> classRelations = TreeMultimap.create();

        // stores for each component its resource id in hexadecimal representation
        Map<String, String> componentResourceID = new HashMap<>();

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                String className = Utility.dottedClassName(classDef.toString());

                if (className.startsWith(apk.getManifest().getPackageName())
                        && (Utility.isActivity(Lists.newArrayList(dexFile.getClasses()), classDef)
                        || Utility.isFragment(Lists.newArrayList(dexFile.getClasses()), classDef))) {
                    // only activities and fragments of the application can define callbacks

                    // track outer/inner class relations
                    if (Utility.isInnerClass(classDef.toString())) {
                        classRelations.put(Utility.getOuterClass(classDef.toString()), classDef.toString());
                    }

                    for (Method method : classDef.getMethods()) {

                        MethodImplementation methodImplementation = method.getImplementation();

                        if (methodImplementation != null
                                // we can speed up search for looking only for onCreate(..) and onCreateView(..)
                                // this assumes that only these two methods declare the layout via setContentView()/inflate()!
                                && method.getName().contains("onCreate")) {

                            MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                                    true, ClassPath.NOT_ART), method,
                                    null, false);

                            for (AnalyzedInstruction analyzedInstruction : analyzer.getAnalyzedInstructions()) {

                                Instruction instruction = analyzedInstruction.getInstruction();

                                /*
                                 * We need to search for calls to setContentView(..) and inflate(..).
                                 * Both of them are of type invoke-virtual.
                                 * TODO: check if there are cases where invoke-virtual/range is used
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

        LOGGER.debug(classRelations);
        LOGGER.debug(componentResourceID);

        /*
         * We now need to find the layout file for a given component. Then, we need to
         * parse it in order to get possible callbacks. Finally, we need to add these callbacks
         * to the 'callbacks' sub graph of the respective component.
         */

        Multimap<String, String> componentCallbacks = TreeMultimap.create();

        // derive for each component the callbacks declared in the component's layout file
        componentResourceID.forEach(
                (component, resourceID) -> {

                    LayoutFile layoutFile = LayoutFile.findLayoutFile(apk.getDecodingOutputPath(), resourceID);

                    if (layoutFile != null) {
                        componentCallbacks.putAll(component, layoutFile.parseCallbacks());
                    }
                });

        LOGGER.debug("Declared Callbacks via XML: " + componentCallbacks);

        // lookup the which class defines the callback method
        for (String component : componentCallbacks.keySet()) {
            for (String callbackName : componentCallbacks.get(component)) {
                // TODO: may need to distinguish between different callbacks, e.g. onClick, onLongClick, ...
                // callbacks can have a custom method name but the rest of the method signature is fixed
                String callback = component + "->" + callbackName + "(Landroid/view/View;)V";

                // check whether the callback is defined within the component
                if (intraCFGs.containsKey(callback)) {
                    callbacks.put(component, intraCFGs.get(callback));
                } else {
                    // it is possible that the outer class defines the callback
                    if (Utility.isInnerClass(component)) {
                        String outerClassName = Utility.getOuterClass(component);
                        callback = callback.replace(component, outerClassName);
                        if (intraCFGs.containsKey(callback)) {
                            callbacks.put(outerClassName, intraCFGs.get(callback));
                        }
                    }
                }
            }
        }
        return callbacks;
    }

    /**
     * Splits a block statement after each invocation and adds a virtual return statement to
     * the next block. Ignores certain invocations, e.g. ART methods.
     *
     * @param blockStatement   The given block statement.
     * @param packageName      The package name of the AUT.
     * @param exclusionPattern Describes which invocations should be ignored for the splitting.
     * @return Returns a list of block statements, where a block statement is described by a list
     * of single statements.
     */
    private List<List<Statement>> splitBlockStatement(BlockStatement blockStatement, String packageName,
                                                      Pattern exclusionPattern) {

        List<List<Statement>> blocks = new ArrayList<>();

        List<Statement> block = new ArrayList<>();

        List<Statement> statements = blockStatement.getStatements();

        for (Statement statement : statements) {

            BasicStatement basicStatement = (BasicStatement) statement;
            AnalyzedInstruction analyzedInstruction = basicStatement.getInstruction();

            if (!Utility.isInvokeInstruction(analyzedInstruction)) {
                // statement belongs to current block
                block.add(statement);
            } else {

                // invoke instruction belongs to current block
                block.add(statement);

                // get the target method of the invocation
                Instruction instruction = analyzedInstruction.getInstruction();
                String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
                String className = Utility.dottedClassName(Utility.getClassName(targetMethod));

                // don't resolve non AUT classes if requested
                if (resolveOnlyAUTClasses && !className.startsWith(packageName)
                        // we have to resolve component invocations in any case, see the code below
                        && !Utility.isComponentInvocation(components, targetMethod)) {
                    continue;
                }

                // don't resolve certain classes/methods, e.g. ART methods
                if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                        || Utility.isArrayType(className)
                        || (Utility.isARTMethod(targetMethod) && excludeARTClasses
                        // we have to resolve component invocations in any case, see the code below
                        && !Utility.isComponentInvocation(components, targetMethod))) {
                    continue;
                }

                // save block
                blocks.add(block);

                // reset block
                block = new ArrayList<>();

                /*
                 * If we deal with a component invocation, the target method should be replaced
                 * with the constructor of the component. Here, the return statement should also
                 * reflect this change.
                 */
                if (Utility.isComponentInvocation(components, targetMethod)) {
                    String componentConstructor = Utility.isComponentInvocation(components, analyzedInstruction);
                    if (componentConstructor != null && intraCFGs.containsKey(componentConstructor)) {
                        targetMethod = componentConstructor;
                    }
                }

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
    private void constructCFG(APK apk) {

        LOGGER.debug("Constructing Inter CFG!");

        // exclude certain classes and methods from graph
        Pattern exclusionPattern = Utility.readExcludePatterns();

        // collect the callback entry points
        Map<String, BaseCFG> callbackEntryPoints = new HashMap<>();

        final String packageName = apk.getManifest().getPackageName();

        // resolve the invoke vertices and connect the sub graphs with each other
        for (Vertex invokeVertex : getInvokeVertices()) {

            // every (invoke) statement is a basic statement (no basic blocks here)
            BasicStatement invokeStmt = (BasicStatement) invokeVertex.getStatement();

            // get target method CFG
            Instruction instruction = invokeStmt.getInstruction().getInstruction();
            String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
            String className = Utility.dottedClassName(Utility.getClassName(targetMethod));

            // don't resolve non AUT classes if requested
            if (resolveOnlyAUTClasses && !className.startsWith(packageName)
                    // we have to resolve component invocations in any case, see the code below
                    && !Utility.isComponentInvocation(components, targetMethod)) {
                continue;
            }

            // don't resolve certain classes/methods, e.g. ART methods
            if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                    || Utility.isArrayType(className)
                    || (Utility.isARTMethod(targetMethod) && excludeARTClasses
                    // we have to resolve component invocations in any case
                    && !Utility.isComponentInvocation(components, targetMethod))) {
                continue;
            }

            // track which fragments are hosted by which activity
            checkForFragmentInvocation(components, invokeStmt);

            // there might be multiple successors in a try-catch block
            Set<Vertex> successors = getOutgoingEdges(invokeVertex).stream().map(Edge::getTarget).collect(Collectors.toSet());

            // get the CFG matching the invocation target
            BaseCFG targetCFG = lookupTargetCFG(invokeStmt);

            // remove edges between invoke vertex and original successors
            removeEdges(getOutgoingEdges(invokeVertex));

            // the invocation vertex defines an edge to the target CFG
            addEdge(invokeVertex, targetCFG.getEntry());

            // insert virtual return vertex
            ReturnStatement returnStmt = new ReturnStatement(invokeVertex.getMethod(), targetCFG.getMethodName(),
                    invokeStmt.getInstructionIndex());
            Vertex returnVertex = new Vertex(returnStmt);
            addVertex(returnVertex);

            // add edge from exit of target CFG to virtual return vertex
            addEdge(targetCFG.getExit(), returnVertex);

            // add edge from virtual return vertex to each original successor
            for (Vertex successor : successors) {
                addEdge(returnVertex, successor);
            }
        }

        // add lifecycle of components + global entry points
        addLifecycle(components, callbackEntryPoints);

        // add the callbacks specified either through XML or directly in code
        addCallbacks(apk, callbackEntryPoints);
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
        final Pattern exclusionPattern = Utility.readExcludePatterns();

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                String className = Utility.dottedClassName(classDef.toString());

                if (resolveOnlyAUTClasses && !className.startsWith(apk.getManifest().getPackageName())) {
                    // don't resolve classes not belonging to AUT
                    continue;
                }

                if (Utility.isResourceClass(classDef) || Utility.isBuildConfigClass(classDef)) {
                    LOGGER.debug("Skipping resource/build class: " + className);
                    // skip R + BuildConfig classes
                    continue;
                }

                // as a side effect track whether the given class represents an activity, service or fragment
                if (exclusionPattern != null && !exclusionPattern.matcher(className).matches()) {
                    if (Utility.isActivity(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        components.add(new Activity(classDef.toString(), ComponentType.ACTIVITY));
                    } else if (Utility.isFragment(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        components.add(new Fragment(classDef.toString(), ComponentType.FRAGMENT));
                    } else if (Utility.isService(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        components.add(new Service(classDef.toString(), ComponentType.SERVICE));
                    }
                }
                
                for (Method method : classDef.getMethods()) {

                    String methodSignature = Utility.deriveMethodSignature(method);

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || Utility.isARTMethod(methodSignature)) {
                        // only construct dummy CFG for non ART classes
                        if (!excludeARTClasses) {
                            BaseCFG intraCFG = dummyIntraCFG(method);
                            addSubGraph(intraCFG);
                            intraCFGs.put(methodSignature, intraCFG);
                        }
                    } else {
                        LOGGER.debug("Method: " + methodSignature);
                        BaseCFG intraCFG = new IntraCFG(methodSignature, dexFile, useBasicBlocks);
                        addSubGraph(intraCFG);
                        addInvokeVertices(intraCFG.getInvokeVertices());
                        // only hold a reference to the entry and exit vertex
                        intraCFGs.put(methodSignature, new DummyCFG(intraCFG));
                    }
                }
            }
        }

        LOGGER.debug("Generated CFGs: " + intraCFGs.size());
        LOGGER.debug("List of activities: " + components.stream().filter(c -> c.getComponentType() == ComponentType.ACTIVITY));
        LOGGER.debug("List of fragments: " + components.stream().filter(c -> c.getComponentType() == ComponentType.FRAGMENT));
        LOGGER.debug("List of services: " + components.stream().filter(c -> c.getComponentType() == ComponentType.SERVICE));
        LOGGER.debug("Invoke Vertices: " + getInvokeVertices().size());
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraCFG(Method targetMethod) {

        BaseCFG cfg = new DummyCFG(Utility.deriveMethodSignature(targetMethod));
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

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    // TODO: check if deep copy of vertices and edges is necessary
    @Override
    @SuppressWarnings("unused")
    public BaseCFG copy() {
        BaseCFG clone = new InterCFG(getMethodName());

        Graph<Vertex, Edge> graphClone = GraphTypeBuilder
                .<Vertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                .edgeClass(Edge.class).buildGraph();

        Set<Vertex> vertices = graph.vertexSet();
        Set<Edge> edges = graph.edgeSet();

        Cloner cloner = new Cloner();

        for (Vertex vertex : vertices) {
            graphClone.addVertex(cloner.deepClone(vertex));
        }

        for (Edge edge : edges) {
            Vertex src = cloner.deepClone(edge.getSource());
            Vertex dest = cloner.deepClone(edge.getTarget());
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
    public Vertex lookUpVertex(String trace) {

        // TODO: may support lookup of a virtual return vertex

        // decompose trace into class, method  and instruction index
        String[] tokens = trace.split("->");

        // class + method + entry|exit|instruction-index
        assert tokens.length == 3;

        // retrieve fully qualified method name (class name + method name)
        String method = tokens[0] + "->" + tokens[1];

        // check whether method belongs to graph
        if (!intraCFGs.containsKey(method)) {
            throw new IllegalArgumentException("Given trace refers to a method not part of the graph!");
        }

        if (tokens[2].equals("entry")) {
            return intraCFGs.get(method).getEntry();
        } else if (tokens[2].equals("exit")) {
            return intraCFGs.get(method).getExit();
        } else {
            // iterate over all paths between entry and exit vertex
            int instructionIndex = Integer.parseInt(tokens[2]);

            Vertex entry = intraCFGs.get(method).getEntry();
            Vertex exit = intraCFGs.get(method).getExit();

            /*
             * If the 'AllDirectedPaths' algorithm appears to be too slow, we could alternatively use
             * some traversal strategy supplied by JGraphT, see https://jgrapht.org/javadoc-1.4.0/org/jgrapht/traverse/package-summary.html.
             * If this is still not good enough, we can roll out our own search algorithm. One could
             * perform a parallel forward/backward search starting from the entry and exit vertex, respectively.
             * If a forward/backward step falls out of the given method, i.e. a vertex of a different method is reached,
             * we can directly jump from the entry vertex to the virtual return vertex in case of a forward step,
             * otherwise (a backward step was performed) we can directly jump to the invoke vertex leading to
             * the entry of the different method.
             */

            AllDirectedPaths<Vertex, Edge> allDirectedPaths = new AllDirectedPaths<>(graph);
            // TODO: verify how the flag 'simplePathsOnly' affects the traversal
            List<GraphPath<Vertex, Edge>> paths = allDirectedPaths.getAllPaths(entry, exit, true, null);

            /*
            Set<Vertex> vertices = Collections.newSetFromMap(new ConcurrentHashMap<Vertex, Boolean>());

            paths.parallelStream().forEach(path -> path.getEdgeList().parallelStream().forEach(edge -> {
                vertices.add(edge.getSource());
                vertices.add(edge.getTarget());
            }));

            return vertices.parallelStream()
                    .filter(vertex -> vertex.containsInstruction(method, instructionIndex))
                    .findAny().orElseThrow(() -> new IllegalArgumentException("Given trace refers to no vertex in graph!"));
             */

            // https://stackoverflow.com/questions/64929090/nested-parallel-stream-execution-in-java-findany-randomly-fails
            return paths.parallelStream().flatMap(path -> path.getEdgeList().parallelStream()
                    .map(edge -> {
                        Vertex source = edge.getSource();
                        Vertex target = edge.getTarget();

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
    }
}
