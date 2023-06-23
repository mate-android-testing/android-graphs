package de.uni_passau.fim.auermich.android_graphs.core.graphs;


import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.PDT;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PDTTest {
    private BaseCFG cfg;
    private final File DRAW_FILE = new File("src/test/resources");

    @BeforeEach
    void setUp() {
        cfg = BaseCFGTest.generateDummyCFG();
    }

    @Test
    public void testPostDominatorGeneration() {
        PDT pdt = new PDT(cfg);
        pdt.drawGraph(DRAW_FILE);
        assertEquals(pdt.size(), 19);
        assertEquals(pdt.getEdges().size(), 18);
        assertEquals(pdt.getOutgoingEdges(pdt.getEntry()).size(), 0);
        assertEquals(pdt.getIncomingEdges(pdt.getEntry()).size(), 1);
        assertEquals(pdt.getOutgoingEdges(pdt.getExit()).size(), 1);
        assertEquals(pdt.getIncomingEdges(pdt.getExit()).size(), 0);
    }
}