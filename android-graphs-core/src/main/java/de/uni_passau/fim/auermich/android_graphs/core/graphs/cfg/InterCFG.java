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
import de.uni_passau.fim.auermich.android_graphs.core.utility.Properties;
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

    private Properties properties;

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
        this.properties = new Properties(useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
        constructCFG(apk);
    }

    private void constructCFG(APK apk) {

        // decode APK to access manifest and other resource files
        apk.decodeAPK();

        // parse manifest
        apk.setManifest(Manifest.parse(new File(apk.getDecodingOutputPath(), "AndroidManifest.xml")));

        // create the individual intraCFGs and add them as sub graphs
        constructIntraCFGs(apk, properties.useBasicBlocks);

        // track relations between components
        Utility.checkComponentRelations(apk, components);

        if (properties.useBasicBlocks) {
            constructCFGWithBasicBlocks(apk);
        } else {
            constructCFGNoBasicBlocks(apk);
        }

        // add for each component a callback graph
        Map<String, BaseCFG> callbackGraphs = addCallbackGraphs();

        // add lifecycle of components + global entry points
        addLifecycle(callbackGraphs);

        // add the UI callbacks specified either through XML or directly in code
        addUICallbacks(apk, callbackGraphs);

        LOGGER.debug("Removing decoded APK files: " + Utility.removeFile(apk.getDecodingOutputPath()));
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
        for (Vertex invokeVertex : getInvokeVertices()) {

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
                BaseCFG targetCFG = lookupTargetCFG(apk, invokeStmt);

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
    }

    /**
     * Adds for each component except fragments a callback entry point.
     *
     * @return Returns a mapping of the components and its callback entry point.
     */
    private Map<String, BaseCFG> addCallbackGraphs() {
        Map<String, BaseCFG> callbackGraphs = new HashMap<>();

        // fragments share the callback entry point with the surrounding activity
        components.stream().filter(c -> c.getComponentType() != ComponentType.FRAGMENT).forEach(component -> {
            BaseCFG callbackGraph = dummyIntraCFG("callbacks " + component.getName());
            addSubGraph(callbackGraph);
            callbackGraphs.put(component.getName(), callbackGraph);
        });
        return callbackGraphs;
    }

    /**
     * Looks up the target CFG matching the invoke statement.
     * As a side effect, component invocation, e.g. calls to startActivity(), are replaced
     * by the invoked component's constructor.
     *
     * @param apk The APK file.
     * @param invokeStmt The invoke statement defining the target.
     * @return Returns the CFG matching the given invoke target.
     */
    private BaseCFG lookupTargetCFG(final APK apk, final BasicStatement invokeStmt) {

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
            } else if (Utility.isReflectionCall(targetMethod)) {
                // replace the reflection call with the internally invoked class constructor
                String clazz = Utility.backtrackReflectionCall(apk, invokeStmt);
                if (clazz != null) {
                    // TODO: derive constructor and check if graph contains it
                    LOGGER.debug("Class invoked by reflection: " + clazz);
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
     * Adds the lifecycle to the given components. In addition, the global entry points are defined for activities.
     *
     * @param callbackGraphs A mapping of component to its callback graph.
     */
    private void addLifecycle(Map<String, BaseCFG> callbackGraphs) {

        // add activity and fragment lifecycle as well as global entry point for activities
        components.stream().filter(c -> c.getComponentType() == ComponentType.ACTIVITY).forEach(activityComponent -> {
            Activity activity = (Activity) activityComponent;
            addAndroidActivityLifecycle(activity, callbackGraphs.get(activity.getName()));
            addGlobalEntryPoint(activity);
        });

        // add service lifecycle
        components.stream().filter(c -> c.getComponentType() == ComponentType.SERVICE).forEach(service -> {
            addAndroidServiceLifecycle((Service) service, callbackGraphs.get(service.getName()));
        });
    }

    /**
     * Adds the lifecycle of started and/or bound services.
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

            // Every service must provide overwrite onBind() independent whether it is a started or bound service.
            String onBindMethod = service.onBindMethod();
            BaseCFG onBind = intraCFGs.get(onBindMethod);
            addEdge(callbackGraph.getEntry(), onBind.getEntry());
            addEdge(onBind.getExit(), callbackGraph.getExit());

            /*
             * If we deal with a bound service, onBind() invokes directly onServiceConnected() of
             * the service connection object.
             */
            if (service.isBound() && service.getServiceConnection() != null) {

                String serviceConnection = service.getServiceConnection();
                BaseCFG serviceConnectionConstructor = intraCFGs.get(Utility.getDefaultConstructor(serviceConnection));

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
                addEdge(onBind.getExit(), onServiceConnected.getEntry());
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
        BaseCFG activityConstructor = intraCFGs.get(activity.getDefaultConstructor());
        addEdge(getEntry(), activityConstructor.getEntry());
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

        // connect onCreate with the default constructor
        addEdge(intraCFGs.get(activity.getDefaultConstructor()).getExit(), onCreateCFG.getEntry());

        // if there are fragments, onCreate invokes onAttach, onCreate and onCreateView
        for (Fragment fragment : fragments) {

            // TODO: there is a deprecated onAttach using an activity instance as parameter
            String onAttachFragment = fragment.onAttachMethod();
            BaseCFG onAttachFragmentCFG = addLifecycle(onAttachFragment, onCreateCFG);

            String onCreateFragment = fragment.onCreateMethod();
            BaseCFG onCreateFragmentCFG = addLifecycle(onCreateFragment, onAttachFragmentCFG);

            /*
            * If the fragment is a DialogFragment, it may override onCreateDialog(), see:
            * https://developer.android.com/reference/android/app/DialogFragment#onCreateDialog(android.os.Bundle)
             */
            BaseCFG lastFragmentLifecycleCFG = onCreateFragmentCFG;
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
            activity.onRestoreInstanceStateMethod();
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

        /*
         * Each component may define several listeners for certain events, e.g. a button click,
         * which causes the invocation of a callback function. Those callbacks are active as
         * long as the corresponding component (activity) is in the onResume state. Thus, in our
         * graph we have an additional sub-graph 'callbacks' that is directly linked to the end
         * of 'onResume()' and can either call one of the specified listeners or directly invoke
         * the onPause() method (indirectly through the entry-exit edge). Each listener function
         * points back to the 'callbacks' entry node.
         */

        // callbacks can be invoked after onResume() has finished
        addEdge(onResumeCFG.getExit(), callbackGraph.getEntry());

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
    private void addUICallbacks(APK apk, Map<String, BaseCFG> callbackGraphs) {

        // retrieve callbacks declared in code and XML
        Multimap<String, BaseCFG> callbacks = lookUpCallbacks(apk);
        Multimap<String, BaseCFG> callbacksXML = lookUpCallbacksXML(apk);

        // add callbacks directly from activity itself and its hosted fragments
        components.stream().filter(c -> c.getComponentType() == ComponentType.ACTIVITY).forEach(activity -> {
            BaseCFG callbackGraph = callbackGraphs.get(activity.getName());

            // callbacks directly declared in activity
            callbacks.get(activity.getName()).forEach(callback -> {
                addEdge(callbackGraph.getEntry(), callback.getEntry());
                addEdge(callback.getExit(), callbackGraph.getExit());
            });
            callbacksXML.get(activity.getName()).forEach(callback -> {
                addEdge(callbackGraph.getEntry(), callback.getEntry());
                addEdge(callback.getExit(), callbackGraph.getExit());
            });

            // callbacks declared in hosted fragments
            Set<Fragment> fragments = ((Activity ) activity).getHostingFragments();
            for (Fragment fragment : fragments) {
                callbacks.get(fragment.getName()).forEach(callback -> {
                    addEdge(callbackGraph.getEntry(), callback.getEntry());
                    addEdge(callback.getExit(), callbackGraph.getExit());
                });
                callbacksXML.get(fragment.getName()).forEach(callback -> {
                    addEdge(callbackGraph.getEntry(), callback.getEntry());
                    addEdge(callback.getExit(), callbackGraph.getExit());
                });
            }
        });
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

        Multimap<String, BaseCFG> callbacks = TreeMultimap.create();
        final Pattern exclusionPattern = properties.exclusionPattern;
        Map<String, Set<ClassDef>> cachedUsagesPerClass = new HashMap<>();

        // iterate over all methods in graph
        for (Map.Entry<String, BaseCFG> intraCFG : intraCFGs.entrySet()) {

            String methodSignature = intraCFG.getKey();
            String className = Utility.getClassName(methodSignature);

            // check for method representing a callback
            if (exclusionPattern != null && !exclusionPattern.matcher(Utility.dottedClassName(className)).matches()
                    // TODO: add missing callbacks for each event listener
                    // see: https://developer.android.com/guide/topics/ui/ui-events
                    // TODO: check whether there can be other custom event listeners
                    && Utility.isCallback(methodSignature)) {

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
                if (Utility.isInnerClass(className)) {

                    String outerClass = Utility.getOuterClass(className);
                    Optional<Component> component = Utility.getComponentByName(components, outerClass);

                    if (component.isPresent()) {
                        // component declares directly callback
                        callbacks.put(outerClass, intraCFG.getValue());
                    } else {
                        // callback is declared by some wrapper class

                        // check which application classes make use of the wrapper class
                        Set<ClassDef> usages;

                        // avoid redundant usage lookups of same class
                        if (cachedUsagesPerClass.containsKey(outerClass)) {
                            usages = cachedUsagesPerClass.get(outerClass);
                        } else {
                            usages = Utility.findClassUsages(apk, outerClass);
                            cachedUsagesPerClass.put(outerClass, usages);
                        }

                        // check whether any found class represents a (ui) component
                        for (ClassDef usage : usages) {

                            String clazzName = usage.toString();
                            Optional<Component> uiComponent = Utility.getComponentByName(components, clazzName);

                            if (uiComponent.isPresent()) {
                                /*
                                * The class that makes use of the wrapper class represents a component, thus
                                * the callback should be assigned to this class.
                                 */
                                callbacks.put(clazzName, intraCFG.getValue());
                            }
                        }
                    }
                } else {
                    // top-level class

                    // an activity/fragment might implement a listener interface
                    Optional<Component> component = Utility.getComponentByName(components, className);

                    if (component.isPresent()) {
                        // component declares directly callback
                        callbacks.put(className, intraCFG.getValue());
                    } else {
                        // callback is declared by some top-level listener or wrapper class

                        // check which application classes make use of the top level class
                        Set<ClassDef> usages;

                        // avoid redundant usage lookups of same class
                        if (cachedUsagesPerClass.containsKey(className)) {
                            usages = cachedUsagesPerClass.get(className);
                        } else {
                            usages = Utility.findClassUsages(apk, className);
                            cachedUsagesPerClass.put(className, usages);
                        }

                        // check whether any found class represents a (ui) component
                        for (ClassDef usage : usages) {

                            String clazzName = usage.toString();
                            Optional<Component> uiComponent = Utility.getComponentByName(components, clazzName);

                            if (uiComponent.isPresent()) {
                                /*
                                 * The class that makes use of the top-level listener (wrapper) class represents a
                                 * component, thus the callback should be assigned to this class.
                                 */
                                callbacks.put(clazzName, intraCFG.getValue());
                            }
                        }
                    }
                }
            }
        }
        LOGGER.debug("Callbacks declared via Code: ");
        callbacks.forEach((c, g) -> LOGGER.debug(c + " -> " + g.getMethodName()));
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

        // TODO: outer/inner class relation is not further used!
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
                    // only activities and fragments of the application can define callbacks in XML

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

        LOGGER.debug("Outer/Inner Class Relations: " + classRelations);
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
                    if (Utility.isInnerClass(component)) {
                        String outerClassName = Utility.getOuterClass(component);
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
        final Pattern exclusionPattern = properties.exclusionPattern;

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
                if (properties.resolveOnlyAUTClasses && !className.startsWith(packageName)
                        // we have to resolve component invocations in any case, see the code below
                        && !Utility.isComponentInvocation(components, targetMethod)
                        // we need to resolve calls using reflection in any case
                        && !Utility.isReflectionCall(targetMethod)) {
                    continue;
                }

                // don't resolve certain classes/methods, e.g. ART methods
                if ((exclusionPattern != null && exclusionPattern.matcher(className).matches()
                        || Utility.isArrayType(className)
                        || Utility.isARTMethod(targetMethod) && properties.excludeARTClasses)
                        // we have to resolve component invocations in any case, see the code below
                        && !Utility.isComponentInvocation(components, targetMethod)
                        // we need to resolve calls using reflection in any case
                        && !Utility.isReflectionCall(targetMethod)) {
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
                if (Utility.isComponentInvocation(components, targetMethod)) {
                    String componentConstructor = Utility.isComponentInvocation(components, analyzedInstruction);
                    if (componentConstructor != null && intraCFGs.containsKey(componentConstructor)) {
                        targetMethod = componentConstructor;
                    }
                } else if (Utility.isReflectionCall(targetMethod)) {
                    /*
                    * If we deal with a reflective call, i.e. newInstance(), the target method
                    * should be replaced with the constructor. Here particular, the virtual return statement
                    * should also reflect this change.
                     */
                    // TODO: replace target method with corresponding constructor
                    LOGGER.debug("Reflection call detected!");
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
    private void constructCFGNoBasicBlocks(APK apk) {

        LOGGER.debug("Constructing Inter CFG!");

        // exclude certain classes and methods from graph
        Pattern exclusionPattern = properties.exclusionPattern;

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
            if (properties.resolveOnlyAUTClasses && !className.startsWith(packageName)
                    // we have to resolve component invocations in any case, see the code below
                    && !Utility.isComponentInvocation(components, targetMethod)
                    // we have to resolve reflection calls in any case
                    && !Utility.isReflectionCall(targetMethod)) {
                continue;
            }

            // don't resolve certain classes/methods, e.g. ART methods
            if ((exclusionPattern != null && exclusionPattern.matcher(className).matches()
                    || Utility.isArrayType(className)
                    || Utility.isARTMethod(targetMethod) && properties.excludeARTClasses)
                    // we have to resolve component invocations in any case
                    && !Utility.isComponentInvocation(components, targetMethod)
                    // we have to resolve reflection calls in any case
                    && !Utility.isReflectionCall(targetMethod)) {
                continue;
            }

            // there might be multiple successors in a try-catch block
            Set<Vertex> successors = getOutgoingEdges(invokeVertex).stream().map(Edge::getTarget).collect(Collectors.toSet());

            // get the CFG matching the invocation target
            BaseCFG targetCFG = lookupTargetCFG(apk, invokeStmt);

            // remove edges between invoke vertex and original successors
            removeEdges(getOutgoingEdges(invokeVertex));

            // the invocation vertex defines an edge to the target CFG
            addEdge(invokeVertex, targetCFG.getEntry());

            // TODO: replace target method of virtual return statement in case of component invocation or reflection call

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

        // track binder classes and attach them to the corresponding service
        Set<String> binderClasses = new HashSet<>();

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                String className = Utility.dottedClassName(classDef.toString());

                if (properties.resolveOnlyAUTClasses && !className.startsWith(apk.getManifest().getPackageName())) {
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
                        components.add(new Activity(classDef, ComponentType.ACTIVITY));
                    } else if (Utility.isFragment(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        components.add(new Fragment(classDef, ComponentType.FRAGMENT));
                    } else if (Utility.isService(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        components.add(new Service(classDef, ComponentType.SERVICE));
                    } else if (Utility.isBinder(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        binderClasses.add(classDef.toString());
                    }
                }

                for (Method method : classDef.getMethods()) {

                    String methodSignature = Utility.deriveMethodSignature(method);

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || Utility.isARTMethod(methodSignature)) {
                        // only construct dummy CFG for non ART classes
                        if (!properties.excludeARTClasses) {
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

                        // add static initializers to dedicated sub graph
                        if (Utility.isStaticInitializer(methodSignature)) {
                            addEdge(staticInitializersCFG.getEntry(), intraCFG.getEntry());
                            addEdge(intraCFG.getExit(), staticInitializersCFG.getExit());
                        }
                    }
                }
            }
        }

        // assign binder class to respective service
        for (String binderClass : binderClasses) {
            // typically binder classes are inner classes of the service
            if (Utility.isInnerClass(binderClass)) {
                String serviceName = Utility.getOuterClass(binderClass);
                Optional<Component> component = Utility.getComponentByName(components, serviceName);
                if (component.isPresent() && component.get().getComponentType() == ComponentType.SERVICE) {
                    Service service = (Service) component.get();
                    service.setBinder(binderClass);
                }
            }
        }

        LOGGER.debug("Generated CFGs: " + intraCFGs.size());
        LOGGER.debug("List of activities: " + components.stream()
                .filter(c -> c.getComponentType() == ComponentType.ACTIVITY).collect(Collectors.toList()));
        LOGGER.debug("List of fragments: " + components.stream()
                .filter(c -> c.getComponentType() == ComponentType.FRAGMENT).collect(Collectors.toList()));
        LOGGER.debug("List of services: " + components.stream()
                .filter(c -> c.getComponentType() == ComponentType.SERVICE).collect(Collectors.toList()));
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
