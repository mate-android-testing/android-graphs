package de.uni_passau.fim.auermich.android_graphs.core.app.xml;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class LayoutFileTest {

    @DisplayName("Testing parsing of fragments.")
    @Test
    public void testParsingFragments() {
        final Path resourceDirectory = Paths.get("src","test","resources");
        final LayoutFile layoutFile = LayoutFile.findLayoutFile(resourceDirectory.toFile(), "0x7f0b001c");
        Assertions.assertNotNull(layoutFile, "Layout file not found!");
        final Set<String> fragments = layoutFile.parseFragments();
        Assertions.assertEquals(1, fragments.size());
    }

    @DisplayName("Testing parsing of navigation fragments.")
    @Test
    public void testParsingNavigationFragments() {
        final Path resourceDirectory = Paths.get("src","test","resources");
        final LayoutFile layoutFile = LayoutFile.findLayoutFile(resourceDirectory.toFile(), "0x7f0b001c");
        Assertions.assertNotNull(layoutFile, "Layout file not found!");
        final Set<String> fragments = layoutFile.parseFragments();
        Assertions.assertEquals(1, fragments.size());
        final String navigationGraph = layoutFile.parseNavigationGraphOfFragment(fragments.stream().findFirst().get());
        final LayoutFile navigationLayoutFile = LayoutFile.findNavigationLayoutFile(resourceDirectory.toFile(), navigationGraph);
        Assertions.assertNotNull(navigationLayoutFile, "Navigation layout file not found!");
        final Set<String> navigationFragments = navigationLayoutFile.parseFragments();
        Assertions.assertEquals(3, navigationFragments.size());
    }
}
