package de.uni_passau.fim.auermich.graphs.cfg;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import de.uni_passau.fim.auermich.statement.BasicStatement;
import de.uni_passau.fim.auermich.statement.ReturnStatement;
import de.uni_passau.fim.auermich.statement.Statement;
import de.uni_passau.fim.auermich.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
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
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;
import org.jf.dexlib2.iface.instruction.formats.Instruction3rc;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InterProceduralCFG extends BaseCFG implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(InterProceduralCFG.class);

    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    public InterProceduralCFG(String methodName) {
        super(methodName);
    }

    public InterProceduralCFG(String methodName, List<DexFile> dexFiles, boolean useBasicBlocks, File apkFile) {
        super(methodName);
        constructCFG(dexFiles, useBasicBlocks, apkFile);
    }


    private void constructCFG(List<DexFile> dexFiles, boolean useBasicBlocks, File apkFile) {
        if (useBasicBlocks) {
            constructCFGWithBasicBlocks(dexFiles, apkFile);
        } else {
            constructCFG(dexFiles, apkFile);
        }
    }

    private void constructCFGWithBasicBlocks(List<DexFile> dexFiles, File apkFile) {

    }

    private void constructCFG(List<DexFile> dexFiles, File apkFile) {

        // construct for each method firs the intra-procedural CFG (key: method signature)
        Map<String, BaseCFG> intraCFGs = new HashMap<>();
        constructIntraCFGs(dexFiles, intraCFGs, false);

        // stores the relevant onCreate methods
        List<BaseCFG> onCreateMethods = new ArrayList<>();

        // avoid concurrent modification exception
        Map<String, BaseCFG> intraCFGsCopy = new HashMap<>(intraCFGs);

        // store graphs already inserted into inter-procedural CFG
        Set<BaseCFG> coveredGraphs = new HashSet<>();

        // exclude certain methods/classes
        Pattern exclusionPattern = Utility.readExcludePatterns();

        // compute inter-procedural CFG by connecting intra CFGs
        for (Map.Entry<String, BaseCFG> entry : intraCFGsCopy.entrySet()) {

            BaseCFG cfg = entry.getValue();
            IntraProceduralCFG intraCFG = (IntraProceduralCFG) cfg;
            LOGGER.debug(intraCFG.getMethodName());

            if (intraCFG.getMethodName().startsWith("Lcom/zola/bmi/BMIMain")) {
                if (!coveredGraphs.contains(cfg)) {
                    // add first source graph
                    addSubGraph(intraCFG);
                    coveredGraphs.add(cfg);
                }
            }

            // the method signature (className->methodName->params->returnType)
            String method = intraCFG.getMethodName();
            String className = Utility.dottedClassName(Utility.getClassName(method));
            LOGGER.debug("ClassName: " + className);

            // we need to model the android lifecycle as well -> collect onCreate methods
            if (method.contains("onCreate(Landroid/os/Bundle;)V") &&
                    exclusionPattern != null && !exclusionPattern.matcher(className).matches()) {
                // copy necessary, otherwise the sub graph misses some vertices
                onCreateMethods.add(intraCFG.copy());
            }

            LOGGER.debug("Searching for invoke instructions!");

            // TODO: may track previously all call instructions when computing intraCFG
            for (Vertex vertex : cfg.getVertices()) {

                if (vertex.isEntryVertex() || vertex.isExitVertex()) {
                    // entry and exit vertices do not have instructions attached, skip
                    continue;
                }

                // all vertices apart entry, exit contain basic statements
                BasicStatement statement = (BasicStatement) vertex.getStatement();
                Instruction instruction = statement.getInstruction().getInstruction();

                // check for invoke/invoke-range instruction
                if (instruction instanceof ReferenceInstruction
                        && (instruction instanceof Instruction3rc
                        || instruction instanceof Instruction35c)) {

                    // search for target CFG by reference of invoke instruction (target method)
                    String methodSignature = ((ReferenceInstruction) instruction).getReference().toString();

                    LOGGER.debug("Invoke: " + methodSignature);

                    BaseCFG targetCFG;

                    if (intraCFGs.containsKey(methodSignature)) {
                        targetCFG = intraCFGs.get(methodSignature);
                        LOGGER.debug("Target CFG: " + ((IntraProceduralCFG) targetCFG).getMethodName());
                    } else {

                        /*
                         * There are some Android specific classes, e.g. android/view/View, which are
                         * not included in the classes.dex file for yet unknown reasons. Basically,
                         * these classes should be just treated like other classes from the ART.
                         */
                        LOGGER.warn("Target CFG for method: " + methodSignature + " not found!");
                        targetCFG = dummyIntraProceduralCFG(methodSignature);
                        intraCFGs.put(methodSignature, targetCFG);
                    }

                    if (intraCFG.getMethodName().startsWith("Lcom/zola/bmi/BMIMain")) {

                        /*
                         * Store the original outgoing edges first, since we add further
                         * edges later.
                         *
                         */
                        Set<Edge> outgoingEdges = intraCFG.getOutgoingEdges(vertex);
                        LOGGER.debug("Outgoing edges of vertex " + vertex + ": " + outgoingEdges);

                        if (!coveredGraphs.contains(targetCFG)) {
                            // add target graph to inter CFG
                            addSubGraph(targetCFG);
                            coveredGraphs.add(targetCFG);
                        }

                        if (containsVertex(vertex) && containsVertex(targetCFG.getEntry())) {
                            // add edge from invoke instruction to entry vertex of target CFG
                            LOGGER.debug("Source: " + vertex);
                            LOGGER.debug("Target: " + targetCFG.getEntry());
                            addEdge(vertex, targetCFG.getEntry());
                        }

                        // TODO: need unique return vertices (multiple call within method to same target method)
                        // insert dummy return vertex
                        Statement returnStmt = new ReturnStatement(vertex.getMethod(), targetCFG.getMethodName());
                        Vertex returnVertex = new Vertex(returnStmt);
                        addVertex(returnVertex);

                        // remove edge from invoke to its successor instruction(s)
                        removeEdges(outgoingEdges);

                        // add edge from exit of target CFG to dummy return vertex
                        addEdge(targetCFG.getExit(), returnVertex);

                        // add edge from dummy return vertex to the original successor(s) of the invoke instruction
                        for (Edge edge : outgoingEdges) {
                            addEdge(returnVertex, edge.getTarget());
                        }
                    }
                }
            }
        }

        // add the android lifecycle methods
        Map<String, BaseCFG> callbackEntryPoints = addAndroidLifecycleMethods(intraCFGs, onCreateMethods);

        // add global entry point to each constructor and link constructor to onCreate method
        addGlobalEntryPoints(intraCFGs, onCreateMethods);

        // add the callbacks specified either through XML or directly in code
        addCallbacks(intraCFGs, callbackEntryPoints, dexFiles, apkFile);

    }

    /**
     * Adds for each component (activity) a global entry point to the respective constructor. Additionally, an edge
     * is created between constructor CFG and the onCreate CFG since the constructor is called prior to onCreate().
     *
     * @param intraCFGs The set of intra-procedural CFGs.
     * @param onCreateMethods The set of onCreate methods (the respective CFGs).
     */
    private void addGlobalEntryPoints(Map<String, BaseCFG> intraCFGs, List<BaseCFG> onCreateMethods) {

        for (BaseCFG onCreate : onCreateMethods) {

            // each component defines a default constructor, which is called prior to onCreate()
            String className = Utility.getClassName(onCreate.getMethodName());
            String constructorName = className + "-><init>()V";

            if (intraCFGs.containsKey(constructorName)) {
                BaseCFG constructor = intraCFGs.get(constructorName);
                addEdge(constructor.getExit(), onCreate.getEntry());

                // add global entry point to constructor
                addEdge(getEntry(), constructor.getEntry());
            }
        }
    }

    private void addCallbacks(Map<String, BaseCFG> intraCFGs,
                                     Map<String, BaseCFG> callbackEntryPoints, List<DexFile> dexFiles,
                              File apkFile) {

        // get callbacks directly declared in code
        Multimap<String, BaseCFG> callbacks = lookUpCallbacks(intraCFGs);

        // add for each android component, e.g. activity, its callbacks/listeners to its callbacks subgraph (the callback entry point)
        for (Map.Entry<String, BaseCFG> callbackEntryPoint : callbackEntryPoints.entrySet()) {
            callbacks.get(callbackEntryPoint.getKey()).forEach(cfg -> {
                addEdge(callbackEntryPoint.getValue().getEntry(), cfg.getEntry());
                addEdge(cfg.getExit(), callbackEntryPoint.getValue().getExit());
            });
        }

        // get callbacks declared in XML files
        Multimap<String, BaseCFG> callbacksXML = lookUpCallbacksXML(dexFiles, intraCFGs, apkFile);

        // add for each android component callbacks declared in XML to its callbacks subgraph (the callback entry point)
        for (Map.Entry<String, BaseCFG> callbackEntryPoint : callbackEntryPoints.entrySet()) {
            callbacksXML.get(callbackEntryPoint.getKey()).forEach(cfg -> {
                addEdge(callbackEntryPoint.getValue().getEntry(), cfg.getEntry());
                addEdge(cfg.getExit(), callbackEntryPoint.getValue().getExit());
            });
        }
    }

    /**
     * Looks up callbacks declared in XML layout files and associates them to its defining component.
     *
     * @param dexFiles The list of dex files.
     * @param intraCFGs The set of intra CFGs.
     * @return Returns a mapping between a component (its class name) and its callbacks (actually the
     *          corresponding intra CFGs). Each component may define multiple callbacks.
     */
    private Multimap<String, BaseCFG> lookUpCallbacksXML(List<DexFile> dexFiles, Map<String, BaseCFG> intraCFGs, File apkFile) {

        // return value, key: name of component
        Multimap<String, BaseCFG> callbacks = TreeMultimap.create();

        // stores the relation between outer and inner classes
        Multimap<String, String> classRelations = TreeMultimap.create();

        // stores for each component its resource id in hexadecimal representation
        Map<String, String> componentResourceID = new HashMap<>();

        Pattern exclusionPattern = Utility.readExcludePatterns();

        for (DexFile dexFile : dexFiles) {

            for (ClassDef classDef : dexFile.getClasses()) {

                String className = Utility.dottedClassName(classDef.toString());

                if (exclusionPattern != null && !exclusionPattern.matcher(className).matches()) {

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

                                    Instruction35c invokeVirtual = (Instruction35c) instruction;

                                    // the method that is invoked by this instruction
                                    String methodReference = invokeVirtual.getReference().toString();

                                    if (methodReference.endsWith("setContentView(I)V")
                                            // ensure that setContentView() refers to the given class
                                            && classDef.toString().equals(Utility.getClassName(methodReference))) {
                                        // TODO: there are multiple overloaded setContentView() implementations
                                        // we assume here only setContentView(int layoutResID)
                                        // link: https://developer.android.com/reference/android/app/Activity.html#setContentView(int)

                                        /*
                                         * We need to find the resource id located in one of the registers. A typicall call to
                                         * setContentView(int layoutResID) looks as follows:
                                         *     invoke-virtual {p0, v0}, Lcom/zola/bmi/BMIMain;->setContentView(I)V
                                         * Here, v0 contains the resource id, thus we need to search backwards for the last
                                         * change of v0. This is typically the previous instruction and is of type 'const'.
                                         */

                                        LOGGER.debug("ClassName: " + classDef);
                                        LOGGER.debug("Method Reference: " + methodReference);
                                        LOGGER.debug("LayoutResID Register: " + invokeVirtual.getRegisterD());

                                        // the id of the register, which contains the layoutResID
                                        int layoutResIDRegister = invokeVirtual.getRegisterD();

                                        boolean foundLayoutResID = false;
                                        AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

                                        while (!foundLayoutResID) {

                                            LOGGER.debug("Predecessor: " + predecessor.getInstruction().getOpcode());
                                            Instruction pred = predecessor.getInstruction();

                                            // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                                            if (pred instanceof NarrowLiteralInstruction
                                                    && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                                                    || pred.getOpcode() == Opcode.CONST_16) && predecessor.setsRegister(layoutResIDRegister)) {
                                                foundLayoutResID = true;
                                                LOGGER.debug("XML ID: " + (((NarrowLiteralInstruction) pred).getNarrowLiteral()));
                                                int resourceID = ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                                                componentResourceID.put(classDef.toString(), "0x" + Integer.toHexString(resourceID));
                                            }

                                            predecessor = predecessor.getPredecessors().first();
                                        }
                                    } else if (methodReference.endsWith("setContentView(Landroid/view/View;)V")
                                            // ensure that setContentView() refers to the given class
                                            && classDef.toString().equals(Utility.getClassName(methodReference))) {

                                        /*
                                         * A typical example of this call looks as follows:
                                         * invoke-virtual {v2, v3}, Landroid/widget/PopupWindow;->setContentView(Landroid/view/View;)V
                                         *
                                         * Here, register v2 is the PopupWindow instance while v3 refers to the View object param.
                                         * Thus, we need to search for the call of setContentView/inflate() on the View object
                                         * in order to retrieve its layout resource ID.
                                         */

                                        LOGGER.debug("Class " + className + " makes use of setContentView(View v)!");

                                        /*
                                         * TODO: are we interested in calls to setContentView(..) that don't refer to the this object?
                                         * The primary goal is to derive the layout ID of a given component (class). However, it seems
                                         * like classes (components) can define the layout of other (sub) components. Are we interested
                                         * in getting the layout ID of those (sub) components?
                                         */

                                        // we need to resolve the layout ID of the given View object parameter


                                    } else if (methodReference.contains("Landroid/view/LayoutInflater;->inflate(")) {
                                        // TODO: there are multiple overloaded inflate() implementations
                                        // see: https://developer.android.com/reference/android/view/LayoutInflater.html#inflate(org.xmlpull.v1.XmlPullParser,%20android.view.ViewGroup,%20boolean)
                                        // we assume here inflate(int resource,ViewGroup root, boolean attachToRoot)

                                        /*
                                         * A typical call of inflate(int resource,ViewGroup root, boolean attachToRoot) looks as follows:
                                         *   invoke-virtual {p1, v0, p2, v1}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;
                                         * Here, v0 contains the resource id, thus we need to search backwards for the last change of v0.
                                         * This is typically the previous instruction and is of type 'const'.
                                         */

                                        LOGGER.debug("ClassName: " + classDef);
                                        LOGGER.debug("Method Reference: " + methodReference);
                                        LOGGER.debug("LayoutResID Register: " + invokeVirtual.getRegisterD());

                                        // the id of the register, which contains the layoutResID
                                        int layoutResIDRegister = invokeVirtual.getRegisterD();

                                        boolean foundLayoutResID = false;
                                        AnalyzedInstruction predecessor = analyzedInstruction.getPredecessors().first();

                                        while (!foundLayoutResID) {

                                            LOGGER.debug("Predecessor: " + predecessor.getInstruction().getOpcode());
                                            Instruction pred = predecessor.getInstruction();

                                            // the predecessor should be either const, const/4 or const/16 and holds the XML ID
                                            if (pred instanceof NarrowLiteralInstruction
                                                    && (pred.getOpcode() == Opcode.CONST || pred.getOpcode() == Opcode.CONST_4
                                                    || pred.getOpcode() == Opcode.CONST_16) && predecessor.setsRegister(layoutResIDRegister)) {
                                                foundLayoutResID = true;
                                                LOGGER.debug("XML ID: " + (((NarrowLiteralInstruction) pred).getNarrowLiteral()));
                                                int resourceID = ((NarrowLiteralInstruction) pred).getNarrowLiteral();
                                                componentResourceID.put(classDef.toString(), "0x" + Integer.toHexString(resourceID));
                                            }

                                            predecessor = predecessor.getPredecessors().first();
                                        }
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
         * to the 'callbacks' sub graph of the respecitve component.
         */

        // we need to first decode the APK to access its resource files
        String outputPath = Utility.decodeAPK(apkFile);

        // we want to link a component to its associated layout file -> (parsing the public.xml file)
        // a typical entry has the following form: <className,layoutFileName>, e.g. <L../BMIMain;,activity_bmimain>
        Map<String, String> componentLayoutFile = retrieveComponentLayoutFile(componentResourceID, outputPath);

        // we search in each component's layout file for specific tags describing callbacks
        // a typical entry has the following form: <className,callbackName>
        Multimap<String, String> componentCallbacks = findCallbacksXML(componentLayoutFile, outputPath);
        LOGGER.debug(componentCallbacks);

        // associate each component with its intraCFGs representing callbacks
        for (String component : componentCallbacks.keySet()) {
            for (String callbackName : componentCallbacks.get(component)) {
                // TODO: may need to distinguish between different callbacks, e.g. onClick, onLongClick, ...
                // callbacks can have a custom method name but the rest of the method signature is fixed
                String callback = component + "->" + callbackName + "(Landroid/view/View;)V";

                // first check whether the callback is declared directly in its defining component
                if (intraCFGs.containsKey(callback)) {
                    callbacks.put(component, intraCFGs.get(callback));
                } else {
                    // check for outer class defining the callback in its code base
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
     * Returns a mapping between a component (its class name) and associated callbacks declared in the component's
     * layout file. Each component can define multiple callbacks.
     *
     * @param componentLayoutFile A mapping between a component (its class name) and its associated layout file.
     * @return Returns a mapping between a component and its associated callbacks declared in its layout file.
     */
    private static Multimap<String, String> findCallbacksXML(Map<String, String> componentLayoutFile, String decodingOutputPath) {

        Multimap<String, String> componentCallbacks = TreeMultimap.create();

        for (Map.Entry<String, String> component : componentLayoutFile.entrySet()) {

            LOGGER.debug("Parsing layout file of component: " + component.getKey());

            String layoutFileName = component.getValue();

            final String layoutFilePath = decodingOutputPath + File.separator + "res" + File.separator
                    + "layout" + File.separator + layoutFileName + ".xml";

            SAXReader reader = new SAXReader();
            Document document = null;

            try {

                document = reader.read(new File(layoutFilePath));
                Element rootElement = document.getRootElement();

                Iterator itr = rootElement.elementIterator();
                while (itr.hasNext()) {

                    // each node is a widget, e.g. a button, textView, ...
                    // TODO: we may can exclude some sort of widgets
                    Node node = (Node) itr.next();
                    Element element = (Element) node;
                    // LOGGER.debug(element.getName());

                    // NOTE: we need to access the attribute WITHOUT its namespace -> can't use android:onClick!
                    String onClickCallback = element.attributeValue("onClick");
                    if (onClickCallback != null) {
                        LOGGER.debug(onClickCallback);
                        componentCallbacks.put(component.getKey(), onClickCallback);
                    }
                }
            } catch (DocumentException e) {
                LOGGER.error("Reading layout file " + layoutFileName + " failed");
                LOGGER.error(e.getMessage());
            }
        }
        return componentCallbacks;
    }

    /**
     * Parses the public.xml file to retrieve the layout file names associated with certain layout resource ids.
     * Returns a mapping between a component (its class name) and the associated layout file name.
     *
     * @param componentResourceID A map associating a component (its class name) with its resource id.
     * @return Returns a mapping between a component (its class name) and the associated layout file name.
     */
    private Map<String, String> retrieveComponentLayoutFile(Map<String, String> componentResourceID, String decodingOutputPath) {

        Map<String, String> componentLayoutFile = new HashMap<>();

        final String publicXMLPath = decodingOutputPath + File.separator + "res" + File.separator
                + "values" + File.separator + "public.xml";

        LOGGER.debug("Path to public.xml file: " + publicXMLPath);

        SAXReader reader = new SAXReader();
        Document document = null;

        try {
            document = reader.read(new File(publicXMLPath));
            Element rootElement = document.getRootElement();
            // LOGGER.debug(rootElement.getName());

            Iterator itr = rootElement.elementIterator();
            while (itr.hasNext()) {

                // each node is a <public ... /> xml tag
                Node node = (Node) itr.next();
                Element element = (Element) node;
                // LOGGER.debug("ElementName: " + node.getName());

                // each <public /> tag contains the attributes type,name,id
                String layoutFile = element.attributeValue("name");
                String layoutResourceID = element.attributeValue("id");

                /*
                LOGGER.debug("Type: " + element.attributeValue("type"));
                LOGGER.debug("Name: " + element.attributeValue("name"));
                LOGGER.debug("ID: " + element.attributeValue("id"));
                */

                // check whether componentResourceID contains a mapping for the given resource ID
                List<String> componentName = componentResourceID
                        .entrySet()
                        .stream()
                        .filter(entry -> layoutResourceID.equals(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                if (!componentName.isEmpty()) {
                    // each component can have only one associated layout file/id
                    String className = componentName.get(0);
                    LOGGER.debug("Class: " + className + " has associated the following layout file: " + layoutFile);
                    componentLayoutFile.put(className, layoutFile);
                }
            }

        } catch (DocumentException e) {
            LOGGER.error("Reading public.xml failed");
            LOGGER.error(e.getMessage());
        }
        return componentLayoutFile;
    }

    /**
     * Returns for each component, e.g. an activity, its associated callbacks. It goes through all
     * intra CFGs looking for a specific callback by its full-qualified name. If there is a match,
     * we extract the defining component, which is typically the outer class, and the CFG representing
     * the callback.
     *
     * @param intraCFGs The set of all generated intra CFGs.
     * @return Returns a mapping between a component and its associated callbacks (can be multiple per instance).
     * @throws IOException Should never happen.
     */
    private static Multimap<String, BaseCFG> lookUpCallbacks(Map<String, BaseCFG> intraCFGs) {

        /*
         * Rather than searching for the call of e.g. setOnClickListener() and following
         * the invocation to the corresponding onClick() method defined by some inner class,
         * we can directly search for the onClick() method and query the outer class (the component
         * defining the callback). We don't even need to go through the code, we can actually
         * look up in the set of intra CFGs for a specific listener through its FQN. To get
         * the outer class, we need to inspect the FQN of the inner class, which is of the following form:
         *       Lmy/package/OuterClassName$InnerClassName;
         * This means, we need to split the FQN at the '$' symbol to retrieve the name of the outer class.
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

    private Map<String, BaseCFG> addAndroidLifecycleMethods(Map<String, BaseCFG> intraCFGs,
                                                                   List<BaseCFG> onCreateMethods) {

        // stores the entry points of the callbacks, which can happen between onResume and onPause
        Map<String, BaseCFG> callbacksCFGs = new HashMap<>();

        for (BaseCFG onCreateMethod : onCreateMethods) {

            String methodName = onCreateMethod.getMethodName();
            String className = Utility.getClassName(methodName);

            // onCreate directly invokes onStart()
            String onStart = className + "->onStart()V";
            BaseCFG onStartCFG = addLifeCycle(onStart, intraCFGs, onCreateMethod);

            // onStart directly invokes onResume()
            String onResume = className + "->onResume()V";
            BaseCFG onResumeCFG = addLifeCycle(onResume, intraCFGs, onStartCFG);

            /*
             * Each component may define several listeners for certain events, e.g. a button click,
             * which causes the invocation of a callback function. Those callbacks are active as
             * long as the corresponding component (activity) is in the onResume state. Thus, in our
             * graph we have an additional sub-graph 'callbacks' that is directly linked to the end
             * of 'onResume()' and can either call one of the specified listeners or directly invoke
             * the onPause() method (indirectly through the entry-exit edge). Each listener function
             * points back to the 'callbacks' entry node.
             */

            // add callbacks sub graph
            BaseCFG callbacksCFG = dummyIntraProceduralCFG("callbacks");
            addSubGraph(callbacksCFG);
            callbacksCFGs.put(className, callbacksCFG);

            // callbacks can be invoked after onResume() has finished
            addEdge(onResumeCFG.getExit(), callbacksCFG.getEntry());

            // there can be a sequence of callbacks (loop)
            addEdge(callbacksCFG.getExit(), callbacksCFG.getEntry());

            // onPause() can be invoked after some callback
            String onPause = className + "->onPause()V";
            BaseCFG onPauseCFG = addLifeCycle(onPause, intraCFGs, callbacksCFG);

            // onPause can either invoke onStop() or onResume()
            addEdge(onPauseCFG.getExit(), onResumeCFG.getEntry());
            String onStop = className + "->onStop()V";
            BaseCFG onStopCFG = addLifeCycle(onStop, intraCFGs, onPauseCFG);

            // onStop can either invoke onRestart() or onDestroy()
            String onRestart = className + "->onRestart()V";
            String onDestroy = className + "->onDestroy()V";
            BaseCFG onRestartCFG = addLifeCycle(onRestart, intraCFGs, onStopCFG);
            BaseCFG onDestroyCFG = addLifeCycle(onDestroy, intraCFGs, onStopCFG);

            // onRestart invokes onStart()
            addEdge(onRestartCFG.getExit(), onStartCFG.getEntry());
        }
        return callbacksCFGs;
    }

    /**
     * Adds a new lifecycle CFG to the existing graph and connects it to the lifecycle's predecessor.
     * Uses the custom lifecycle CFG if available, otherwise creates a dummy lifecycle CFG.
     *
     * @param method      The FQN of the lifecycle, e.g. MAIN_ACTIVTY_FQN->onStop()V.
     * @param intraCFGs   The set of derived intra CFGs.
     * @param predecessor The lifecycle's predecessor.
     * @return Returns the new lifecycle CFG.
     */
    private BaseCFG addLifeCycle(String method, Map<String, BaseCFG> intraCFGs, BaseCFG predecessor) {

        BaseCFG lifeCyle = null;

        if (intraCFGs.containsKey(method)) {
            lifeCyle = intraCFGs.get(method);
        } else {
            // use custom lifecycle CFG
            lifeCyle = dummyIntraProceduralCFG(method);
            addSubGraph(lifeCyle);
        }

        addEdge(predecessor.getExit(), lifeCyle.getEntry());
        return lifeCyle;
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraProceduralCFG(String targetMethod) {

        BaseCFG cfg = new IntraProceduralCFG(targetMethod);
        cfg.addEdge(cfg.getEntry(), cfg.getExit());
        return cfg;
    }

    /**
     * Constructs the intra procedural CFG for every method inlcuded in the {@param dexFile}. Since
     * a regular {@param dexFile} includes > 30 000 methods, the graph would explode. To reduce the
     * size of the graph, we only model for all the ART classes, more precisely for all classes matched
     * by a certain pattern, a simplistic CFG consisting only of the virtual start and end vertex plus
     * an edge in between those vertices.
     *
     * @param dexFiles   The list of dex files.
     * @param intraCFGs A map containing for each method (key: method signature) its CFG.
     * @param useBasicBlocks Whether to use basic blocks or not.
     */
    private void constructIntraCFGs(List<DexFile> dexFiles, Map<String, BaseCFG> intraCFGs,
                                           boolean useBasicBlocks) {

        Pattern exclusionPattern = Utility.readExcludePatterns();

        // track for how many methods we computed the complete CFG (no dummy CFGs)
        AtomicInteger realMethods = new AtomicInteger(0);

        for(DexFile dexFile : dexFiles) {

            // construct for ART classes only a dummy CFG consisting of virtual start and end vertex
            dexFile.getClasses().forEach(classDef ->
                    classDef.getMethods().forEach(method -> {

                        String methodSignature = Utility.deriveMethodSignature(method);
                        String className = Utility.dottedClassName(classDef.toString());

                        if (exclusionPattern != null && exclusionPattern.matcher(className).matches()) {
                            // dummy CFG consisting only of entry, exit vertex and edge between
                            intraCFGs.put(methodSignature, dummyIntraProceduralCFG(method));
                        } else {
                            intraCFGs.put(methodSignature, new IntraProceduralCFG(methodSignature, dexFile, useBasicBlocks));
                            realMethods.incrementAndGet();
                        }
                    }));
        }
        LOGGER.debug("Number of completely constructed CFGs: " + realMethods.get());
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraProceduralCFG(Method targetMethod) {

        LOGGER.info("Method Signature: " + Utility.deriveMethodSignature(targetMethod));

        BaseCFG cfg = new IntraProceduralCFG(Utility.deriveMethodSignature(targetMethod));
        cfg.addEdge(cfg.getEntry(), cfg.getExit());
        return cfg;
    }


    public InterProceduralCFG clone() {

        InterProceduralCFG cloneCFG = (InterProceduralCFG) super.clone();
        return cloneCFG;
    }

    public BaseCFG copy() {

        BaseCFG clone = new InterProceduralCFG(getMethodName());

        Graph<Vertex, Edge> graphClone = GraphTypeBuilder
                .<Vertex, DefaultEdge> directed().allowingMultipleEdges(true).allowingSelfLoops(true)
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

}
