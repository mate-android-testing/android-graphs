package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.CDG;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphUtilsTest {

    private final File APK_FILE = new File("src/test/resources/com.zola.bmi.apk");
    private final File DRAW_FILE = new File("src/test/resources/");

    @Test
    void constructInterCDG() {
        CDG cdg = GraphUtils.constructInterCDG(APK_FILE, true, false, true);
        cdg.drawGraph(DRAW_FILE);
        assertTrue(cdg.getVertices().size() > 0);
        assertTrue(cdg.getEdges().size() > 0);
        assertEquals(0, cdg.getPredecessors(cdg.getEntry()).size());
        assertTrue(cdg.getSuccessors(cdg.getEntry()).size() > 0);
        assertEquals(0, cdg.getSuccessors(cdg.getExit()).size());
    }
}