package de.uni_passau.fim.auermich.android_graphs.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.ControlDependenceGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.PostDominatorTree;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;

class ControlDependenceGraphTest {
    private final File DRAW_FILE = new File("src/test/resources");
    private BaseCFG cfg;
    private PostDominatorTree pdt;

    @BeforeEach
    void setUp() {
        cfg = BaseCFGTest.generateDummyCFG();
        pdt = new PostDominatorTree(cfg);
    }

    @Test
    public void testControlDependenceGraphGeneration() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(cfg, pdt);
        cdg.drawGraph(DRAW_FILE);
        assertEquals(cdg.getOutgoingEdges(cdg.getEntry()).size(), 8);
        assertEquals(cdg.getIncomingEdges(cdg.getEntry()).size(), 0);
        assertEquals(cdg.getOutgoingEdges(cdg.getExit()).size(), 0);
        assertEquals(cdg.getIncomingEdges(cdg.getExit()).size(), 1);
    }
}