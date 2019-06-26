package de.uni_passau.fim.auermich.utility;

import java.io.File;
import java.io.FilenameFilter;


public final class Utility {

    private Utility() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Returns the dex files in the given {@param directory}.
     *
     * @param directory The directory to search for the dex files.
     * @return Returns a list of dex files found in the given directory.
     */
    public static File[] getDexFiles(File directory) {

        File[] matches = directory.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.startsWith("classes") && name.endsWith(".dex");
            }
        });
        return matches;
    }
}
