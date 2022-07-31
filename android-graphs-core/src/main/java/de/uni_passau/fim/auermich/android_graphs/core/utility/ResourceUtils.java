package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.value.IntEncodedValue;

import java.util.List;
import java.util.Optional;

public class ResourceUtils {
    /**
     * Looks up the name of the string resource by its id
     *
     * @param id The string resource id
     * @param dexFiles The dex files containing the string resource class
     * @return The name of the string resource
     */
    public static Optional<String> lookupResourceStringId(long id, List<DexFile> dexFiles) {
        for (DexFile dexFile : dexFiles) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (classDef.toString().endsWith("/R$string;")) {
                    for (Field field : classDef.getFields()) {
                        if (field.getType().equals("I")) {
                            IntEncodedValue resourceId = (IntEncodedValue) field.getInitialValue();

                            if (resourceId.getValue() == id) {
                                return Optional.of(field.getName());
                            }
                        }
                    }
                }
            }
        }
        return Optional.of(String.valueOf(id));
    }
}
