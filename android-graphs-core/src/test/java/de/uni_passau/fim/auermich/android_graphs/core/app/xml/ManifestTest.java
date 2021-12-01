package de.uni_passau.fim.auermich.android_graphs.core.app.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ManifestTest {

    @DisplayName("Testing simple manifest.")
    @Test
    void testSimpleManifest() {
        Path resourceDirectory = Paths.get("src","test","resources");
        Path manifestFile = resourceDirectory.resolve("AndroidManifest1.xml");
        Manifest manifest = Manifest.parse(manifestFile.toFile());
        assertEquals(manifest.getPackageName(), "bbc.mobile.news.ww");
        assertEquals(manifest.getMainActivity(), "bbc.mobile.news.v3.app.TopLevelActivity");
    }

    @DisplayName("Testing activity-alias manifest.")
    @Test
    void testActivityAliasManifest() {
        Path resourceDirectory = Paths.get("src","test","resources");
        Path manifestFile = resourceDirectory.resolve("AndroidManifest2.xml");
        Manifest manifest = Manifest.parse(manifestFile.toFile());
        assertEquals(manifest.getPackageName(), "com.simplemobiletools.calendar");
        assertEquals(manifest.getMainActivity(), "com.simplemobiletools.calendar.activities.SplashActivity");
    }
}
