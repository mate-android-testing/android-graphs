package de.uni_passau.fim.auermich.android_graphs.core.graphs;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.PostDominatorTree;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;

class PostDominatorTreeTest {
    private BaseCFG cfg;
    private final File DRAW_FILE = new File("src/test/resources");

    @BeforeEach
    void setUp() {
        cfg = BaseCFGTest.generateDummyCFG();
    }

    @Test
    public void testPostDominatorGeneration() {
        PostDominatorTree pdt = new PostDominatorTree(cfg);
        assertEquals(pdt.getOutgoingEdges(pdt.getEntry()).size(), 0);
        assertEquals(pdt.getIncomingEdges(pdt.getEntry()).size(), 1);
        assertEquals(pdt.getOutgoingEdges(pdt.getExit()).size(), 1);
        assertEquals(pdt.getIncomingEdges(pdt.getExit()).size(), 0);
        pdt.drawGraph(DRAW_FILE);
    }
}