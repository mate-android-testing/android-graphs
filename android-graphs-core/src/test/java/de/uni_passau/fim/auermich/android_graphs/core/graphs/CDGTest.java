package de.uni_passau.fim.auermich.android_graphs.core.graphs;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.CDG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CDGTest {
    private final File DRAW_FILE = new File("src/test/resources");
    private BaseCFG cfg;

    @BeforeEach
    void setUp() {
        cfg = BaseCFGTest.generateDummyCFG();
    }

    @Test
    public void testControlDependenceGraphGeneration() {
        CDG cdg = new CDG(cfg);
        cdg.drawGraph(DRAW_FILE);
        assertEquals(cdg.size(), 19);
        assertEquals(cdg.getEdges().size(), 12);
    }
}