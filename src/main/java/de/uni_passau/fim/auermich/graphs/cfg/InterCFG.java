package de.uni_passau.fim.auermich.graphs.cfg;

import com.google.common.collect.Lists;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.app.APK;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import de.uni_passau.fim.auermich.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public class InterCFG extends BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(InterCFG.class);
    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    // whether for ART classes solely a dummy intra CFG should be generated
    private boolean excludeARTClasses;

    // track the set of activities
    private Set<String> activities = new HashSet<>();

    // track the set of fragments
    private Set<String> fragments = new HashSet<>();

    // maintain the individual intra CFGs
    Map<String, BaseCFG> intraCFGs = new HashMap<>();

    // copy constructor
    public InterCFG(String graphName) {
        super(graphName);
    }

    public InterCFG(String graphName, APK apk, boolean useBasicBlocks, boolean excludeARTClasses) {
        super(graphName);
        this.excludeARTClasses = excludeARTClasses;
        constructCFG(apk, useBasicBlocks);
    }

    private void constructCFG(APK apk, boolean useBasicBlocks) {

        // create the individual intraCFGs
        constructIntraCFGs(apk, useBasicBlocks);

        if (useBasicBlocks) {
            constructCFGWithBasicBlocks(apk);
        } else {
            constructCFG(apk);
        }
    }

    private void constructCFGWithBasicBlocks(APK apk) {



    }

    private void constructCFG(APK apk) {



    }

    private void constructIntraCFGs(APK apk, boolean useBasicBlocks) {

        LOGGER.debug("Constructing IntraCFGs!");
        final Pattern exclusionPattern = Utility.readExcludePatterns();

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {

                String className = Utility.dottedClassName(classDef.toString());

                if (Utility.isResourceClass(classDef) || Utility.isBuildConfigClass(classDef)) {
                    LOGGER.debug("Skipping resource/build class: " + className);
                    // skip R + BuildConfig classes
                    continue;
                }

                // as a side effect track whether the given class represents an activity or fragment
                if (exclusionPattern != null && !exclusionPattern.matcher(className).matches()) {
                    if (Utility.isActivity(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                            activities.add(classDef.toString());
                    } else if (Utility.isFragment(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                            fragments.add(classDef.toString());
                    }
                }

                for (Method method : classDef.getMethods()) {

                    String methodSignature = Utility.deriveMethodSignature(method);

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || Utility.isARTMethod(methodSignature)) {
                        // only construct dummy CFG for non ART classes
                        if (!excludeARTClasses) {
                            // dummy CFG consisting only of entry, exit vertex and edge between
                            intraCFGs.put(methodSignature, dummyIntraProceduralCFG(method));
                        }
                    } else {
                        LOGGER.debug("Method: " + methodSignature);
                        intraCFGs.put(methodSignature, new IntraCFG(methodSignature, dexFile, useBasicBlocks));
                    }
                }
            }
        }

        LOGGER.debug("Generated CFGs: " + intraCFGs.size());
        LOGGER.debug("List of activities: " + activities);
        LOGGER.debug("List of fragments: " + fragments);

        // add intraCFGs as sub graphs
        intraCFGs.forEach((name,intraCFG) -> addSubGraph(intraCFG));
    }

    private void constructIntraCFGsParallel(APK apk, boolean useBasicBlocks) {

        LOGGER.debug("Constructing IntraCFGs!");

        final Pattern exclusionPattern = Utility.readExcludePatterns();

        apk.getDexFiles().parallelStream().forEach(dexFile -> {

            dexFile.getClasses().parallelStream().forEach(classDef -> {

                String className = Utility.dottedClassName(classDef.toString());

                // as a side effect track whether the given class represents an activity or fragment
                if (exclusionPattern != null && !exclusionPattern.matcher(className).matches()) {
                    if (Utility.isActivity(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        synchronized (this) {
                            LOGGER.debug("Activity detected!");
                            activities.add(classDef.toString());
                        }
                    } else if (Utility.isFragment(Lists.newArrayList(dexFile.getClasses()), classDef)) {
                        synchronized (this) {
                            LOGGER.debug("Fragment detected!");
                            fragments.add(classDef.toString());
                        }
                    }
                }

                StreamSupport.stream(classDef.getMethods().spliterator(), true).forEach(method -> {

                    String methodSignature = Utility.deriveMethodSignature(method);
                    LOGGER.debug("Method: " + methodSignature);

                    if (exclusionPattern != null && exclusionPattern.matcher(className).matches()
                            || Utility.isARTMethod(methodSignature)) {
                        if (!excludeARTClasses) {
                            // dummy CFG consisting only of entry, exit vertex and edge between
                            synchronized (this) {
                                BaseCFG intraCFG = dummyIntraProceduralCFG(method);
                                addSubGraph(intraCFG);
                                intraCFGs.put(methodSignature, intraCFG);
                            }
                        }
                    } else {
                        synchronized (this) {
                            BaseCFG intraCFG = new IntraCFG(methodSignature, dexFile, useBasicBlocks);
                            addSubGraph(intraCFG);
                            intraCFGs.put(methodSignature, intraCFG);
                        }
                    }
                });
            });
        });
    }

    /**
     * Constructs a dummy CFG only consisting of the virtual entry and exit vertices
     * and an edge between. This CFG is used to model Android Runtime methods (ART).
     *
     * @param targetMethod The ART method.
     * @return Returns a simplified CFG.
     */
    private BaseCFG dummyIntraProceduralCFG(Method targetMethod) {

        BaseCFG cfg = new IntraCFG(Utility.deriveMethodSignature(targetMethod));
        cfg.addEdge(cfg.getEntry(), cfg.getExit());
        return cfg;
    }

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    @Override
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
}
