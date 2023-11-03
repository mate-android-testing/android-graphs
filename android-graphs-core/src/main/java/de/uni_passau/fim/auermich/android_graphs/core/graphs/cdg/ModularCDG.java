package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.app.components.*;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.LayoutFile;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.Manifest;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGEdge;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGVertex;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.IntraCFG;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.EntryStatement;
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

public class ModularCDG extends BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(ModularCDG.class);
    private static final GraphType GRAPH_TYPE = GraphType.MODULARCDG;

    /**
     * Properties relevant for the construction process, e.g. whether basic blocks should be used.
     */
    private Properties properties;

    /**
     * Maintains a reference to the individual intra CDGs.
     * NOTE: Only a reference to the entry and exit vertex is hold!
     */
    private final Map<String, BaseCFG> intraCDGs = new HashMap<>();

    /**
     * Maintains the set of components, i.e. activities, services and fragments.
     */
    private final Set<Component> components = new HashSet<>();

    /**
     * Maintains the class relation between the application classes in both directions.
     * This includes the super class, the interfaces and the sub classes of a class.
     */
    private final ClassHierarchy classHierarchy = new ClassHierarchy();

    // the set of discovered Android callbacks
    private final Set<String> callbacks = new HashSet<>();

    /**
     * The APK file.
     */
    private APK apk = null;

    // necessary for the copy constructor
    public ModularCDG(String graphName) {
        super(graphName);
    }

    public ModularCDG(String graphName, APK apk, boolean useBasicBlocks,
                      boolean excludeARTClasses, boolean resolveOnlyAUTClasses) {
        super(graphName);
        this.properties = new Properties(useBasicBlocks, excludeARTClasses, resolveOnlyAUTClasses);
        this.apk = apk;
        addEdge(getEntry(), getExit()); // exit is always control-dependent on entry
        constructCDG(apk);
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
     * Retrieves the mapping to the intra CDGs.
     *
     * @return Returns a mapping to the entry and exit vertices of the individual intra CDGs.
     */
    public Map<String, BaseCFG> getIntraCDGs() {
        return Collections.unmodifiableMap(intraCDGs);
    }

    private void constructCDG(APK apk) {

        // decode APK to access manifest and other resource files
        apk.decodeAPK();

        // parse manifest
        apk.setManifest(Manifest.parse(new File(apk.getDecodingOutputPath(), "AndroidManifest.xml")));

        // parse the resource strings
        apk.setResourceStrings(ResourceUtils.parseStringsXMLFile(apk.getDecodingOutputPath()));

        // create the individual intraCDGs and add them as sub graphs
        constructIntraCDGs(apk, properties.useBasicBlocks);

        // track relations between components
        ComponentUtils.checkComponentRelations(apk, components, classHierarchy);

        // add for each component a callback graph
        Map<String, BaseCFG> callbackGraphs = addCallbackGraphs();

        // Connect component lifecycle methods.
        connectComponentLifecycle(callbackGraphs);

        // Connect callbacks specified either through XML or directly in code
        connectCallbacks(apk, callbackGraphs);

        LOGGER.debug("Removing decoded APK files: " + Utility.removeFile(apk.getDecodingOutputPath()));

        if (properties.useBasicBlocks) {
            constructCDGWithBasicBlocks(apk);
        } else {
            constructCDGNoBasicBlocks(apk);
        }
    }

    private void constructIntraCDGs(APK apk, boolean useBasicBlocks) {

        LOGGER.debug("Constructing IntraCDGs!");
        final Pattern exclusionPattern = properties.exclusionPattern;

        // Track binder classes and attach them to the corresponding service
        Set<String> binderClasses = new HashSet<>();
        List<ClassDef> classes = apk.getDexFiles().stream()
                .map(DexFile::getClasses)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                final String className = ClassUtils.dottedClassName(classDef.toString());

                if (properties.resolveOnlyAUTClasses && !className.startsWith(apk.getManifest().getPackageName())
                        && !className.startsWith(mainActivityPackage)) {
                    // don't resolve classes not belonging to AUT
                    continue;
                }

                if (ClassUtils.isResourceClass(classDef) || ClassUtils.isBuildConfigClass(classDef)) {
                    LOGGER.debug("Skipping resource/build class: " + className);
                    // skip R + BuildConfig classes
                    continue;
                }

                for (Method method : classDef.getMethods()) {

                    final String methodSignature = MethodUtils.deriveMethodSignature(method);

                    // track the Android callbacks
                    if (MethodUtils.isCallback(methodSignature)) {
                        callbacks.add(methodSignature);
                    }

                    // Add menus to components.
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
                            final BaseCFG intraCDG = dummyIntraCDG(method);
                            addSubGraph(intraCDG);
                            intraCDGs.put(methodSignature, intraCDG);
                        }
                    } else {
                        // exclude methods from java.lang.Object, e.g. notify()
                        if (!MethodUtils.isJavaObjectMethod(methodSignature)) {
                            LOGGER.debug("Method: " + methodSignature);

                            final BaseCFG intraCDG = new IntraCDG(new IntraCFG(method, dexFile, useBasicBlocks));
                            addSubGraph(intraCDG);
                            addInvokeVertices(intraCDG.getInvokeVertices());
                            // only hold a reference to the entry and exit vertex
                            intraCDGs.put(methodSignature, new DummyCDG(intraCDG));
                        }
                    }
                }

                // Track whether a given class represents  an activity, service or fragment
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
            }
        }

        // Assign binder class to respective service
        for (String binderClass : binderClasses) {
            // Typically binder classes are inner classes of the service
            if (ClassUtils.isInnerClass(binderClass)) {
                String serviceName = ClassUtils.getOuterClass(binderClass);
                Optional<Component> component = ComponentUtils.getComponentByName(components, serviceName);
                if (component.isPresent() && component.get().getComponentType() == ComponentType.SERVICE) {
                    Service service = (Service) component.get();
                    service.setBinder(binderClass);
                }
            }
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
            BaseCFG callbackGraph = dummyIntraCDG("callbacks " + component.getName());
            addSubGraph(callbackGraph);
            callbackGraphs.put(component.getName(), callbackGraph);
        });
        return callbackGraphs;
    }

    /**
     * Connects lifecycle methods of activities, services and application components.
     *
     * @param callbackGraphs A mapping of component to its callback graph.
     */
    private void connectComponentLifecycle(Map<String, BaseCFG> callbackGraphs) {

        LOGGER.debug("Adding lifecycle to components...");

        // Connect activity and fragment lifecycles.
        components.stream().filter(c -> c.getComponentType() == ComponentType.ACTIVITY).forEach(activityComponent -> {
            Activity activity = (Activity) activityComponent;
            connectActivityLifecycle(activity, callbackGraphs.get(activity.getName()));
        });

        // Connect service lifecycles.
        components.stream().filter(c -> c.getComponentType() == ComponentType.SERVICE).forEach(serviceComponent -> {
            Service service = (Service) serviceComponent;
            connectServiceLifecycle(service, callbackGraphs.get(service.getName()));
        });

        // Connect application lifecycles.
        components.stream().filter(c -> c.getComponentType() == ComponentType.APPLICATION).forEach(applicationComponent -> {
            Application application = (Application) applicationComponent;
            connectApplicationLifecycle(application, callbackGraphs.get(application.getName()));
        });
    }

    /**
     * Adds lifecycle methods to the given activity.
     *
     * @param activity      The activity to which lifecycle methods are to be added.
     * @param callbackGraph The callbackGraph used for connecting some lifecycle methods.
     */
    private void connectActivityLifecycle(final Activity activity, BaseCFG callbackGraph) {
        final Set<Fragment> fragments = activity.getHostingFragments();
        LOGGER.debug("Activity " + activity + " defines the following fragments: " + fragments);

        // Connect activity entry to global entry
        for (String constructor : activity.getConstructors()) {
            addEdge(getEntry(), intraCDGs.get(constructor).getEntry());
        }

        // If there are fragments, onCreate invokes several fragment creation callbacks.
        // Since they depend on the activity's onCreate(), we model them as children of the onCreate() method.
        for (Fragment fragment : fragments) {
            addLifecycle(fragment.onAttachMethod(), activity.onCreateMethod());
            addLifecycle(fragment.onCreateMethod(), activity.onCreateMethod());
            addLifecycle(fragment.onCreateDialogMethod(), activity.onCreateMethod());
            addLifecycle(fragment.onCreatePreferencesMethod(), activity.onCreateMethod());
            addLifecycle(fragment.onCreateViewMethod(), activity.onCreateMethod());
            addLifecycle(fragment.onActivityCreatedMethod(), activity.onCreateMethod());
            addLifecycle(fragment.onViewStateRestoredMethod(), activity.onCreateMethod());
        }

        // Activity's onStart invokes Fragment's onStart()
        for (Fragment fragment : fragments) {
            addLifecycle(fragment.onStartMethod(), activity.onStartMethod());
        }

        // Activity's onResume() invokes Fragment's onResume()
        for (Fragment fragment : fragments) {
            addLifecycle(fragment.onResumeMethod(), activity.onResumeMethod());
        }

        // Add lifecycle methods responsible for getting a running activity if implemented in the AUT.
        // Since we assume that these lifecycle methods are called one after another as soon as the
        // activity's constructor is invoked, we model them as children of the activity's constructor.
        for (String constructor : activity.getConstructors()) {
            addLifecycle(activity.onCreateMethod(), constructor);
            addLifecycle(activity.onStartMethod(), constructor);
            addLifecycle(activity.onResumeMethod(), constructor);
            addLifecycle(activity.onRestoreInstanceStateOverloadedMethod(), constructor);
            addLifecycle(activity.onPostCreateOverloadedMethod(), constructor);
            addLifecycle(activity.onPostResumeMethod(), constructor);
        }

        // Callbacks may be invoked after onResume()/onPostResume().
        if (intraCDGs.containsKey(activity.onResumeMethod())) {
            addEdge(intraCDGs.get(activity.onResumeMethod()).getExit(), callbackGraph.getEntry());
        } else if (intraCDGs.containsKey(activity.onPostResumeMethod())) {
            addEdge(intraCDGs.get(activity.onPostResumeMethod()).getExit(), callbackGraph.getEntry());
        } else {
            // If neither onResume() nor onPostResume() has been implemented in the AUT.
            // We model the callbacks being dependent on the activity's constructor
            // since we can safely assume no control dependencies in between the lifecycle methods.
            for (String constructor : activity.getConstructors()) {
                addEdge(intraCDGs.get(constructor).getExit(), callbackGraph.getEntry());
            }
        }


        // Activity's onPause() invokes Fragment's onPause()
        for (Fragment fragment : fragments) {
            addLifecycle(fragment.onPauseMethod(), activity.onPauseMethod());
        }

        // Activity's onStop() invokes Fragment's onStop() & onSaveInstanceStateMethod()
        for (Fragment fragment : fragments) {
            addLifecycle(fragment.onStopMethod(), activity.onStopMethod());
            addLifecycle(fragment.onSaveInstanceStateMethod(), activity.onStopMethod());
        }

        // Activity's onDestroy() invokes Fragment's onDestroy(), onDestroyView() & onDetach()
        for (Fragment fragment : fragments) {
            addLifecycle(fragment.onDestroyViewMethod(), activity.onDestroyMethod());
            addLifecycle(fragment.onDestroyMethod(), activity.onDestroyMethod());
            addLifecycle(fragment.onDetachMethod(), activity.onDestroyMethod());
        }

        // We model an activity's onPause(), onStop() and onDestroy() to be dependent on the callback graph.
        addLifecycle(activity.onPauseMethod(), callbackGraph);
        addLifecycle(activity.onStopMethod(), callbackGraph);
        addLifecycle(activity.onDestroyMethod(), callbackGraph);
        addLifecycle(activity.onSaveInstanceStateOverloadedMethod(), callbackGraph);
    }

    /**
     * Adds service lifecycle methods to the given service.
     *
     * @param service       The service for which the lifecycle should be added.
     * @param callbackGraph The callback sub graph of the service component.
     */
    private void connectServiceLifecycle(Service service, BaseCFG callbackGraph) {

        String constructor = service.getDefaultConstructor();

        // If there is not implementation present in the AUT for the service constructor, use a dummy CDG.
        if (!intraCDGs.containsKey(constructor)){
            BaseCFG constructorDummy = dummyCDG(constructor);
            intraCDGs.put(constructor, constructorDummy);
            addSubGraph(constructorDummy);
        }

        // Connect Service entry to global entry
        addEdge(getEntry(), intraCDGs.get(constructor).getEntry());

        if (!intraCDGs.containsKey(constructor)) {
            LOGGER.warn("Service without explicit constructor: " + service);
        } else {

            // Connect onCreate() with constructor.
            addLifecycle(service.onCreateMethod(), constructor);

            // Connect onStartCommand() and deprecated onStartMethod() with constructor
            // since we assume no control dependencies for lifecycle methods.
            addLifecycle(service.onStartCommandMethod(), constructor);
            addLifecycle(service.onStartMethod(), constructor);

            // Connect service callbacks
            addEdge(intraCDGs.get(constructor).getExit(), callbackGraph.getEntry());

            // Connect onBind() if implemented in AUT.
            if (intraCDGs.containsKey(service.onBindMethod())) {
                addEdge(callbackGraph.getEntry(), intraCDGs.get(service.onBindMethod()).getEntry());
            }

            /*
             * If we deal with a bound service, onBind() invokes directly onServiceConnected() of
             * the service connection object.
             */
            if (service.isBound() && service.getServiceConnection() != null) {

                String serviceConnection = service.getServiceConnection();
                BaseCFG serviceConnectionConstructor = intraCDGs.get(ClassUtils.getDefaultConstructor(serviceConnection));

                if (serviceConnectionConstructor == null) {
                    LOGGER.warn("Service connection '" + serviceConnection + "' for " + service.getName() + " has no intra CFG!");
                } else {
                    // Add callbacks subgraph
                    BaseCFG serviceConnectionCallbacks = dummyIntraCDG("callbacks " + serviceConnection);
                    addSubGraph(serviceConnectionCallbacks);

                    // Connect constructor with callback entry point
                    addEdge(serviceConnectionConstructor.getExit(), serviceConnectionCallbacks.getEntry());

                    // integrate callback methods onServiceConnected() and onServiceDisconnected()
                    BaseCFG onServiceConnected = intraCDGs.get(serviceConnection
                            + "->onServiceConnected(Landroid/content/ComponentName;Landroid/os/IBinder;)V");
                    BaseCFG onServiceDisconnected = intraCDGs.get(serviceConnection
                            + "->onServiceDisconnected(Landroid/content/ComponentName;)V");
                    addEdge(serviceConnectionCallbacks.getEntry(), onServiceConnected.getEntry());
                    addEdge(serviceConnectionCallbacks.getEntry(), onServiceDisconnected.getEntry());

                    // connect onBind() with onServiceConnected()
                    BaseCFG onBind = intraCDGs.get(service.onBindMethod());
                    addEdge(onBind.getExit(), onServiceConnected.getEntry());
                }
            }

            // Connect onUnBind() and onDestroy()
            addLifecycle(service.onUnbindMethod(), callbackGraph);
            addLifecycle(service.onDestroyMethod(), callbackGraph);
        }
    }

    /**
     * Adds application lifecycle methods to the given application.
     *
     * @param application   The application class.
     * @param callbackGraph The callbacks sub graph of the application class.
     */
    private void connectApplicationLifecycle(Application application, BaseCFG callbackGraph) {
        BaseCFG constructor = intraCDGs.get(application.getDefaultConstructor());

        // Connect onCreateMethod() if implemented in the AUT.
        addLifecycle(application.onCreateMethod(), constructor);
        addLifecycle(application.onCreateMethod(), callbackGraph);

        // Connect onLowMemoryMethod(), onTerminateMethod(), onTrimMemoryMethod() and onConfigurationChangedMethod()
        // if implemented in the AUT.
        addLifecycle(application.onLowMemoryMethod(), callbackGraph);
        addLifecycle(application.onTerminateMethod(), callbackGraph);
        addLifecycle(application.onTrimMemoryMethod(), callbackGraph);
        addLifecycle(application.onConfigurationChangedMethod(), callbackGraph);
    }

    /**
     * Adds a lifecycle method to the ModularCDG graph if the method is overwritten within the AUT
     * and creates the predecessor graph if necessary.
     *
     * @param lifeCycleIdentifier The identifier for the lifecycle method to add.
     * @param predecessorId       The identifier of the preceding subgraph.
     */
    private void addLifecycle(String lifeCycleIdentifier, String predecessorId) {
        if (intraCDGs.containsKey(lifeCycleIdentifier)) {
            BaseCFG lifecycleMethod = intraCDGs.get(lifeCycleIdentifier);
            if (!intraCDGs.containsKey(predecessorId)) {
                BaseCFG predecessorStub = dummyCDG(predecessorId);
                intraCDGs.put(predecessorId, predecessorStub);
                addSubGraph(predecessorStub);
            }
            BaseCFG predecessor = intraCDGs.get(predecessorId);
            addEdge(predecessor.getExit(), lifecycleMethod.getEntry());
        }
    }

    /**
     * Adds a lifecycle method to the ModularCDG if the lifecycle method gets overwritten in the AUT.
     *
     * @param lifeCycleIdentifier The identifier for the lifecycle method to add.
     * @param predecessor         The preceding subgraph.
     */
    private void addLifecycle(String lifeCycleIdentifier, BaseCFG predecessor) {
        if (intraCDGs.containsKey(lifeCycleIdentifier)) {
            BaseCFG lifecycleMethod = intraCDGs.get(lifeCycleIdentifier);
            addEdge(predecessor.getExit(), lifecycleMethod.getEntry());
        }
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraCDG(Method targetMethod) {
        final BaseCFG cdg = new DummyCDG(MethodUtils.deriveMethodSignature(targetMethod));
        cdg.addEdge(cdg.getEntry(), cdg.getExit());
        return cdg;
    }

    /**
     * Connects callbacks to the respective activity. We both consider callbacks defined via XML
     * as well as programmatically defined callbacks. Note that an activity shares with its hosting fragments
     * the 'callback' subgraph.
     *
     * @param apk            The APK file describing the app.
     * @param callbackGraphs Maintains a mapping between a component and its callback graph.
     */
    private void connectCallbacks(APK apk, Map<String, BaseCFG> callbackGraphs) {

        // Extract callbacks declared in code and XML
        Multimap<String, BaseCFG> callbacks = lookUpCallbacks(apk);
        Multimap<String, BaseCFG> callbacksXML = lookUpCallbacksXML(apk);

        // Add callbacks directly from activity itself and its hosted fragments
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
     * @param callbacks     The callbacks derived from the code.
     * @param callbacksXML  The callbacks derived from the XML files.
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
     * @param callback     The child callback.
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
     * @param graphs                   The list of graphs.
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

            BaseCFG callbackGraph = intraCDGs.get(callback);
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
                if (intraCDGs.containsKey(callback)) {
                    callbacks.put(component, intraCDGs.get(callback));
                } else {
                    // it is possible that the outer class defines the callback
                    if (ClassUtils.isInnerClass(component)) {
                        String outerClassName = ClassUtils.getOuterClass(component);
                        callback = callback.replace(component, outerClassName);
                        if (intraCDGs.containsKey(callback)) {
                            callbacks.put(outerClassName, intraCDGs.get(callback));
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
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraCDG(String targetMethod) {
        final BaseCFG cdg = new DummyCDG(targetMethod);
        cdg.addEdge(cdg.getEntry(), cdg.getExit());
        return cdg;
    }

    /**
     * Creates and adds a dummy CDG for the given method.
     *
     * @param targetMethod The method for which a dummy CFG should be generated.
     * @return Returns the constructed dummy CFG.
     */
    private BaseCFG dummyCDG(String targetMethod) {
        final BaseCFG targetCDG = dummyIntraCDG(targetMethod);
        intraCDGs.put(targetMethod, targetCDG);
        addSubGraph(targetCDG);
        return targetCDG;
    }

    private void constructCDGNoBasicBlocks(APK apk) {
        // TODO: Implement.
    }

    /**
     * Constructs the inter CFG using basic blocks for a given app.
     *
     * @param apk The APK file describing the app.
     */
    private void constructCDGWithBasicBlocks(APK apk) {

        LOGGER.debug("Constructing modular CDG with basic blocks!");

        final String packageName = apk.getManifest().getPackageName();

        final String mainActivity = apk.getManifest().getMainActivity();
        final String mainActivityPackage = mainActivity != null
                ? mainActivity.substring(0, mainActivity.lastIndexOf('.')) : null;

        // resolve the invoke vertices and connect the sub graphs with each other
        for (CFGVertex invokeVertex : getInvokeVertices()) {

            BlockStatement blockStatement = (BlockStatement) invokeVertex.getStatement();

            for (Statement statement : blockStatement.getStatements()) {

                if (statement instanceof BasicStatement
                        && InstructionUtils.isInvokeInstruction(((BasicStatement) statement).getInstruction())) {
                    final BasicStatement invokeStmt = (BasicStatement) statement;
                    final BaseCFG targetCDG = lookupTargetCDG(packageName, mainActivityPackage, invokeStmt);
                    if (targetCDG != null) {
                        addEdge(invokeVertex, targetCDG.getEntry());
                    }
                }
            }
        }

        for (Map.Entry<String, BaseCFG> entry : intraCDGs.entrySet()) {
            if (getIncomingEdges(entry.getValue().getEntry()).size() == 0) {
                // connect all non-connected sub graphs with global entry by inserting a virtual vertex
                LOGGER.warn("Connecting Method " + entry.getKey() + " virtually to Entry");
                CFGVertex virtual = new CFGVertex(new EntryStatement("Connect->" + entry.getKey()));
                addVertex(virtual);
                addEdge(getEntry(), virtual);
                addEdge(virtual, entry.getValue().getEntry());
            }
        }
    }

    private BaseCFG lookupTargetCDG(final String packageName, final String mainActivityPackage, final BasicStatement invokeStmt) {

        final Instruction instruction = invokeStmt.getInstruction().getInstruction();
        final String targetMethod = ((ReferenceInstruction) instruction).getReference().toString();
        final String className = MethodUtils.getClassName(targetMethod);

        if (properties.resolveOnlyAUTClasses && !ClassUtils.dottedClassName(className).startsWith(packageName)
                && !ClassUtils.dottedClassName(className).startsWith(mainActivityPackage)) {
            // don't resolve invocation to non AUT classes
            return null;
        }

        if (intraCDGs.containsKey(targetMethod)) {
            return intraCDGs.get(targetMethod);
        } else {
            return dummyCDG(targetMethod);
        }
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
        if (!intraCDGs.containsKey(method)) {
            throw new IllegalArgumentException("Given trace refers to a method not part of the graph: " + method);
        }

        if (tokens.length == 3) {

            if (tokens[2].equals("entry")) {
                return intraCDGs.get(method).getEntry();
            } else if (tokens[2].equals("exit")) {
                return intraCDGs.get(method).getExit();
            } else {
                // lookup of a branch
                int instructionIndex = Integer.parseInt(tokens[2]);
                CFGVertex entry = intraCDGs.get(method).getEntry();
                return lookUpVertex(method, instructionIndex, entry);
            }

        } else if (tokens.length == 4) {

            // String instructionType = tokens[2];
            int instructionIndex = Integer.parseInt(tokens[3]);
            CFGVertex entry = intraCDGs.get(method).getEntry();
            return lookUpVertex(method, instructionIndex, entry);

        } else if (tokens.length == 5) { // basic block coverage trace

            int instructionIndex = Integer.parseInt(tokens[2]);
            CFGVertex entry = intraCDGs.get(method).getEntry();
            return lookUpVertex(method, instructionIndex, entry);

        } else {
            throw new IllegalArgumentException("Unrecognized trace: " + trace);
        }
    }

    /**
     * Looks up a vertex in the graph.
     *
     * @param method           The method the vertex belongs to.
     * @param instructionIndex The instruction index.
     * @param entry            The entry vertex of the given method (bound for the look up).
     * @param exit             The exit vertex of the given method (bound for the look up).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     * a {@link IllegalArgumentException} is thrown.
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
     * @param method           The method describing the vertex.
     * @param instructionIndex The instruction index of the vertex (the wrapped instruction).
     * @param entry            The entry vertex of the method (limits the search).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     * a {@link IllegalArgumentException} is thrown.
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
     * @param method           The method describing the vertex.
     * @param instructionIndex The instruction index of the vertex (the wrapped instruction).
     * @param entry            The entry vertex of the method (limits the search).
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     * a {@link IllegalArgumentException} is thrown.
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

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    // TODO: check if deep copy of vertices and edges is necessary
    @Override
    @SuppressWarnings("unused")
    public ModularCDG copy() {
        ModularCDG clone = new ModularCDG(getMethodName());

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
}
