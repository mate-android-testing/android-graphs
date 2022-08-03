package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.value.IntEncodedValue;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

public class ResourceUtils {
    public static Optional<String> lookupIdName(List<DexFile> dexFiles, long encodedId) {
        return lookupName(dexFiles, "/R$id;", encodedId);
    }

    public static Optional<String> lookupStringIdName(List<DexFile> dexFiles, long encodedId) {
        return lookupName(dexFiles, "/R$string;", encodedId);
    }

    private static Optional<String> lookupName(List<DexFile> dexFiles, String resourceClassSuffix, long encodedId) {
        for (DexFile dexFile : dexFiles) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (classDef.toString().endsWith(resourceClassSuffix)) {
                    for (Field field : classDef.getFields()) {
                        if (field.getType().equals("I")) {
                            IntEncodedValue resourceId = (IntEncodedValue) field.getInitialValue();

                            if (resourceId.getValue() == encodedId) {
                                return Optional.of(field.getName());
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static Map<String, String> parseTranslations(File decodingOutputPath) {
        final File publicXMLPath = new File(decodingOutputPath,
                Paths.get("res", "values", "strings.xml").toString());

        SAXReader reader = new SAXReader();
        Document document;

        try {
            document = reader.read(publicXMLPath);
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
