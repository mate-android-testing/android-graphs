package de.uni_passau.fim.auermich.jcommander;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;

import java.io.File;

public class CustomFileConverter implements IStringConverter<File> {

    /**
     * Checks whether the given string {@param value} is a valid
     * path for a APK file.
     *
     * @param value The potential APK file path.
     * @return Returns {#link File} if valid.
     */
    @Override
    public File convert(String value) {
        File file = new File(value);
        if (file.exists() && file.isFile() && value.endsWith(".apk")) {
            return file;
        } else {
            throw new ParameterException("Value " + value + " is not a valid APK file path.");
        }
    }
}
