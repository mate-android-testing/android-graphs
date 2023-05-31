package de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.uni_passau.fim.auermich.android_graphs.core.statements.EntryStatement;

class BaseCFGTest {
    private BaseCFG subject;
    private final File DRAW_FILE = new File("src/test/resources");

    public static BaseCFG generateDummyCFG() {
        BaseCFG dummyCFG  = new DummyCFG("Test");
        List<CFGVertex> vertices = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            CFGVertex cfgVertex = new CFGVertex(new EntryStatement(String.valueOf(i)));
            dummyCFG.addVertex(cfgVertex);
            vertices.add(cfgVertex);
        }

        dummyCFG.addEdge(dummyCFG.getEntry(), vertices.get(0));
        dummyCFG.addEdge(vertices.get(0), vertices.get(1));
        dummyCFG.addEdge(vertices.get(1), vertices.get(2));
        dummyCFG.addEdge(vertices.get(2), vertices.get(3));
        dummyCFG.addEdge(vertices.get(3), vertices.get(4));
        dummyCFG.addEdge(vertices.get(4), vertices.get(5));     // 5 ---> 110

        // 17 ---> 300
        dummyCFG.addEdge(vertices.get(5), vertices.get(16));
        dummyCFG.addEdge(vertices.get(16), dummyCFG.getExit());

        // 6 ---> 120
        dummyCFG.addEdge(vertices.get(5), vertices.get(6));
        dummyCFG.addEdge(vertices.get(6), vertices.get(7));
        dummyCFG.addEdge(vertices.get(7), vertices.get(8));     // 8 ----> 140

        // 9 ---> 150
        dummyCFG.addEdge(vertices.get(8), vertices.get(9));
        dummyCFG.addEdge(vertices.get(9), vertices.get(10));    // 10 ----> 160

        // 11 ---> 170
        dummyCFG.addEdge(vertices.get(10), vertices.get(11));
        dummyCFG.addEdge(vertices.get(11), vertices.get(12));
        dummyCFG.addEdge(vertices.get(12), vertices.get(13));   // 13 ----> 190
        dummyCFG.addEdge(vertices.get(13), vertices.get(8));    // 190 ---> 140

        dummyCFG.addEdge(vertices.get(10), vertices.get(13));   // 160 ---> 190

        // 14 ----> 200
        dummyCFG.addEdge(vertices.get(8), vertices.get(14));    // 140 ---> 200
        dummyCFG.addEdge(vertices.get(14), vertices.get(15));
        dummyCFG.addEdge(vertices.get(15), vertices.get(5));    // 210 ---> 110

        return dummyCFG;
    }

    @BeforeEach
    void setUp() {
        subject = generateDummyCFG();
    }

    @Test
    public void reverseGraphTest(){
        BaseCFG reversed = subject.reverseGraph();

        reversed.drawGraph(DRAW_FILE);
        assertEquals(reversed.getVertices().size(), subject.getVertices().size());
        assertEquals(reversed.getEdges().size(), subject.getEdges().size());

        assertEquals(reversed.getIncomingEdges(reversed.getEntry()).size(), subject.getOutgoingEdges(subject.getEntry()).size());
        assertEquals(reversed.getOutgoingEdges(reversed.getExit()).size(), subject.getIncomingEdges(subject.getExit()).size());
    }

}