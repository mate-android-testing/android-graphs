package de.uni_passau.fim.auermich.graphs.cfg;

import com.mxgraph.layout.*;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.orthogonal.mxOrthogonalLayout;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import de.uni_passau.fim.auermich.graphs.Vertex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.AbstractGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public abstract class BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(BaseCFG.class);

    protected AbstractGraph graph = new DirectedMultigraph(DefaultEdge.class);
    private Vertex entry = new Vertex(-1, null);
    private Vertex exit = new Vertex(-2, null);

    /**
     * Used to initialize an inter-procedural CFG.
     */
    public BaseCFG() {
        graph.addVertex(entry);
        graph.addVertex(exit);
    }

    public void addEdge(Vertex src, Vertex dest) {
        graph.addEdge(src, dest);
    }

    public void addVertex(Vertex vertex) {
        graph.addVertex(vertex);
    }

    public Vertex getEntry() {
        return entry;
    }

    public Vertex getExit() {
        return exit;
    }

    public Set<Vertex> getVertices() {
        return graph.vertexSet();
    }

    @Override
    public String toString() {
        return graph.toString();
    }

    public void drawGraph() {

        JGraphXAdapter<Vertex, DefaultEdge> graphXAdapter
                = new JGraphXAdapter<>(graph);
        graphXAdapter.getStylesheet().getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, "1");

        // this layout orders the vertices in a sequence from top to bottom (entry -> v1...vn -> exit)
        // mxIGraphLayout layout = new mxHierarchicalLayout(graphXAdapter);

        mxIGraphLayout layout = new mxCircleLayout(graphXAdapter);
        ((mxCircleLayout) layout).setRadius(((mxCircleLayout) layout).getRadius()*2.5);
        layout.execute(graphXAdapter.getDefaultParent());

        BufferedImage image =
                mxCellRenderer.createBufferedImage(graphXAdapter, null, 1, Color.WHITE, true, null);

        Path resourceDirectory = Paths.get("src","test","resources");
        File file = new File(resourceDirectory.toFile(), "graph.png");
        LOGGER.debug(file.getPath());

        try {
            file.createNewFile();
            ImageIO.write(image, "PNG", file);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
    }
}
