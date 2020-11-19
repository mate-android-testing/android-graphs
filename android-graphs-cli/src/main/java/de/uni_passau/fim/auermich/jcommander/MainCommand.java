package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.Parameter;

import java.io.File;

public class MainCommand {

    @Parameter(names = { "-f", "-file"}, description = "File path to the APK file we want to analyze.",
            required = true, converter = CustomFileConverter.class)
    private File apkFile;

    @Parameter(names = { "-e", "-exceptional" }, description = "Whether the graph should contain edges from try-catch blocks.")
    private boolean exceptionalFlow = false;

    @Parameter(names = { "-d", "-debug" }, description = "Debug mode.")
    private boolean debug = false;

    @Parameter(names = { "-h", "--help" }, help = true)
    private boolean help;

    @Parameter(names = { "-l", "-lookup"}, description = "A trace referring to a vertex.")
    private String trace;

    // TODO: use file path instead, fallback to default path is none is specified
    @Parameter(names = {"-draw"}, description = "Whether the graph should be drawn.")
    private boolean draw = false;

    public boolean isDraw() {
        return draw;
    }

    public String getTrace() {
        return trace;
    }

    public boolean lookup() {
        return trace != null;
    }

    public File getAPKFile() {
        return apkFile;
    }

    public boolean isExceptionalFlow() {
        return exceptionalFlow;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isHelp() {
        return help;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setApkFile(String apkFile) {
        this.apkFile = new File(apkFile);
    }
}
