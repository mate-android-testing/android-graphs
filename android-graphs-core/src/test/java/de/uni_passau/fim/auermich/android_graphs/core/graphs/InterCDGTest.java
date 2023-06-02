package de.uni_passau.fim.auermich.android_graphs.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg.InterCDG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;

class InterCDGTest {
    private final File DRAW_FILE = new File("src/test/resources");
    private BaseCFG cfg;

    @BeforeEach
    void setUp() {
        cfg = BaseCFGTest.generateDummyCFG();
    }

    @Test
    public void testControlDependenceGraphGeneration() {
        InterCDG cdg = new InterCDG(cfg);
        cdg.drawGraph(DRAW_FILE);
        assertEquals(cdg.getOutgoingEdges(cdg.getEntry()).size(), 8);
        assertEquals(cdg.getIncomingEdges(cdg.getEntry()).size(), 0);
        assertEquals(cdg.getOutgoingEdges(cdg.getExit()).size(), 0);
        assertEquals(cdg.getIncomingEdges(cdg.getExit()).size(), 1);
    }
}