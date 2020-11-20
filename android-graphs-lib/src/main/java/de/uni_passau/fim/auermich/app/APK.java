package de.uni_passau.fim.auermich.app;

import brut.androlib.ApkDecoder;
import brut.common.BrutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.iface.DexFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Handler;

public class APK {

    private static final Logger LOGGER = LogManager.getLogger(APK.class);

    private final File apkFile;
    private final List<DexFile> dexFiles;
    private final String decodingOutputPath;

    public APK(File apkFile, List<DexFile> dexFiles) {
        this(apkFile, dexFiles, apkFile.getParent() + File.separator + "out");
    }

    public APK(File apkFile, List<DexFile> dexFiles, String decodingOutputPath) {
        this.apkFile = apkFile;
        this.dexFiles = dexFiles;
        this.decodingOutputPath = decodingOutputPath;
    }

    // TODO: provide overloaded decodeAPK methods (params: outputdir, decodeSources, decodeResources, decodeManifest,..)
    public boolean decodeAPK() {

        // java.util.logging.LogManager.getLogManager().reset();

        // set 3rd party library (apktool) logging to 'WARNING'
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.setLevel(java.util.logging.Level.WARNING);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(java.util.logging.Level.WARNING);
        }

        try {
            ApkDecoder decoder = new ApkDecoder(apkFile);

            // path where we want to decode the APK
            String outputDir = decodingOutputPath;

            LOGGER.debug("Decoding Output Dir: " + outputDir);
            decoder.setOutDir(new File(outputDir));

            // whether to decode classes.dex into smali files: -s
            decoder.setDecodeSources(ApkDecoder.DECODE_SOURCES_NONE);

            // overwrites existing dir: -f
            decoder.setForceDelete(true);

            // FIXME: the APKDecoder has some issue with the file path length on Windows!
            decoder.decode();
        } catch (BrutException | IOException e) {
            LOGGER.warn("Failed to decode APK file!");
            LOGGER.warn(e.getMessage());
            return false;
        }
        return true;
    }

    public File getApkFile() {
        return apkFile;
    }

    public List<DexFile> getDexFiles() {
        return dexFiles;
    }

    public String getDecodingOutputPath() {
        return decodingOutputPath;
    }
}
