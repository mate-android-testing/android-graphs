package de.uni_passau.fim.auermich.android_graphs.core.app;

import brut.androlib.ApkDecoder;
import brut.androlib.Config;
import brut.common.BrutException;
import com.android.tools.smali.dexlib2.iface.DexFile;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.Manifest;
import de.uni_passau.fim.auermich.android_graphs.core.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Handler;

/**
 * A wrapper around an APK file.
 */
public class APK {

    private static final Logger LOGGER = LogManager.getLogger(APK.class);

    /**
     * The default decoding directory. This conforms to the directory when you invoke
     * 'apktool d' without the optional parameter '-o <output-dir>'.
     */
    private static final String DEFAULT_DECODING_DIR = "out-" + new Random().nextInt();

    /**
     * The path to the APK file itself.
     */
    private final File apkFile;

    /**
     * References to the dex files contained in the APK.
     */
    private final List<DexFile> dexFiles;

    /**
     * The path to which the APK was decoded.
     */
    private File decodingOutputPath;

    /**
     * References the AndroidManifest.xml.
     */
    private Manifest manifest;

    /**
     * Defines a mapping from resource name to actual text, e.g. 'app_name' -> 'BMI Calculator'.
     */
    private Map<String, String> resourceStrings;

    /**
     * Constructs a new APK.
     *
     * @param apkFile The path to the APK file.
     * @param dexFiles The dex files contained in the APK.
     */
    public APK(File apkFile, List<DexFile> dexFiles) {
        this.apkFile = apkFile;
        this.dexFiles = dexFiles;
    }

    /**
     * Updates the manifest.
     *
     * @param manifest The new manifest.
     */
    public void setManifest(final Manifest manifest) {
        this.manifest = manifest;
    }

    /**
     * Returns the manifest. Only call this method after you have set
     * the manifest via {@link #setManifest(Manifest)}!
     *
     * @return Returns the manifest.
     */
    public Manifest getManifest() {
        if (manifest == null) {
            throw new IllegalStateException("Manifest was not (properly) parsed previously!");
        }
        return manifest;
    }

    public void setResourceStrings(Map<String, String> resourceStrings) {
        this.resourceStrings = resourceStrings;
    }

    public Map<String, String> getResourceStrings() {
        if (resourceStrings == null) {
            throw new IllegalStateException("Resource strings were not parsed!");
        }
        return resourceStrings;
    }

    /**
     * Decodes the APK in the same directory as the APK file.
     */
    public void decodeAPK() {

        // set 3rd party library (apktool) logging to 'WARNING'
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.setLevel(java.util.logging.Level.WARNING);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(java.util.logging.Level.WARNING);
        }

        final Config config = Config.getDefaultConfig();
        config.forceDelete = true; // overwrites existing dir: -f

        try {
            // do not decode dex classes to smali: -s
            config.setDecodeSources(Config.DECODE_SOURCES_NONE);

            final ApkDecoder decoder = new ApkDecoder(config, apkFile);

            // check whether we can access the default framework dir
            if (Utility.isLinux()) {

                // check https://ibotpeaches.github.io/Apktool/documentation/ for the default location
                File homeDir = new File(Utility.getHomeDirectory());
                File defaultFrameworksDir = new File(homeDir, ".local/share/apktool");
                LOGGER.debug("Default frameworks dir: " + defaultFrameworksDir);

                try {
                    if (!defaultFrameworksDir.exists() || !defaultFrameworksDir.canRead()) {

                        LOGGER.warn("Can't access default frameworks dir! Using tmp dir!");

                        /*
                         * This is only necessary on a constrained environment like on our local cluster. Unfortunately,
                         * the default framework dir location can't be used due to a permission issue. Moreover, we can't
                         * properly delete this folder after decoding completed due to a file locking bug.
                         */
                        config.frameworkDirectory = "tmp";
                    }
                } catch (SecurityException e) {
                    LOGGER.warn("Can't access default frameworks dir! Using tmp dir!");
                    LOGGER.warn(e.getMessage());
                    config.frameworkDirectory = "tmp";
                }
            }

            // path where we want to decode the APK (the same directory as the APK is in)
            decodingOutputPath = new File(apkFile.getParent(), DEFAULT_DECODING_DIR);

            LOGGER.debug("Decoding Output Dir: " + decodingOutputPath);
            decoder.decode(decodingOutputPath);

        } catch (BrutException | IOException e) {
            LOGGER.error("Failed to decode APK file!");
            LOGGER.error(e.getMessage());
            decodingOutputPath = null;
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the path of the APK file.
     *
     * @return Returns the path of the APK file.
     */
    public File getApkFile() {
        return apkFile;
    }

    /**
     * Returns the dex files contained in the APK file.
     *
     * @return Returns a read-only view on the dex files.
     */
    public List<DexFile> getDexFiles() {
        return Collections.unmodifiableList(dexFiles);
    }

    /**
     * Returns the output path of the decoding. Only call this method after
     * {@link #decodeAPK()} has been invoked successfully!
     *
     * @return Returns the output path of the decoding.
     */
    public File getDecodingOutputPath() {

        if (decodingOutputPath == null) {
            throw new IllegalStateException("APK was not (properly) decoded previously!");
        }

        return decodingOutputPath;
    }
}
