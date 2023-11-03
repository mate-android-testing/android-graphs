package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

/**
 * Parses stuff from resource files/classes.
 */
public class ResourceUtils {

    /**
     * Looks up a field name in the R$id resource class.
     *
     * @param dexFiles The list of dex files.
     * @param resourceId The resource id.
     * @return Returns an optional possible containing the field name.
     */
    public static Optional<String> lookupIdName(List<DexFile> dexFiles, long resourceId) {
        return lookupName(dexFiles, "/R$id;", resourceId);
    }

    /**
     * Looks up a field name in the R$string resource class.
     *
     * @param dexFiles The list of dex files.
     * @param resourceId The resource id.
     * @return Returns an optional possible containing the field name.
     */
    public static Optional<String> lookupStringIdName(List<DexFile> dexFiles, long resourceId) {
        return lookupName(dexFiles, "/R$string;", resourceId);
    }

    /**
     * Looks up the name of a field in the given resource class by resource id.
     *
     * @param dexFiles The list of dex files.
     * @param resourceClassSuffix The suffix of the resource class name.
     * @param resourceId The resource id.
     * @return Returns an optional possible containing the field name.
     */
    private static Optional<String> lookupName(List<DexFile> dexFiles, String resourceClassSuffix, long resourceId) {
        for (DexFile dexFile : dexFiles) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (classDef.toString().endsWith(resourceClassSuffix)) {
                    for (Field field : classDef.getFields()) {
                        if (field.getType().equals("I")) {
                            IntEncodedValue encodedResourceId = (IntEncodedValue) field.getInitialValue();

                            if (encodedResourceId != null && encodedResourceId.getValue() == resourceId) {
                                return Optional.of(field.getName());
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Parses the strings.xml file within the res/values folder.
     *
     * @param decodedAPKPath The decoded APK path.
     * @return Returns a mapping from resource name to the actual text, e.g. 'app_name' -> 'BMI Calculator'.
     */
    public static Map<String, String> parseStringsXMLFile(File decodedAPKPath) {

        final File stringsXMLPath = new File(decodedAPKPath,
                Paths.get("res", "values", "strings.xml").toString());

        SAXReader reader = new SAXReader();
        Document document;

        try {
            document = reader.read(stringsXMLPath);
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }

        Element rootElement = document.getRootElement();

        Map<String, String> translations = new HashMap<>();
        Iterator<Element> itr = rootElement.elementIterator();
        while (itr.hasNext()) {
            Element element = itr.next();

            String name = element.attributeValue("name");
            String translation = element.getText();

            translations.put(name, translation);
        }

        return translations;
    }
}
