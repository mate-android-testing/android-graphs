package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import de.uni_passau.fim.auermich.android_graphs.core.utility.ClassUtils;
import org.jf.dexlib2.iface.ClassDef;

public abstract class AbstractComponent implements Component {

    private final ClassDef clazz;
    private final String name;
    private final ComponentType type;

    public AbstractComponent(ClassDef clazz, ComponentType type) {
        this.clazz = clazz;
        this.name = clazz.toString();
        this.type = type;
    }

    @Override
    public ClassDef getClazz() {
        return clazz;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getShortName() {
        String[] parts = ClassUtils.dottedClassName(name).split("\\.");
        return parts[parts.length - 1];
    }

    @Override
    public ComponentType getComponentType() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public String getDefaultConstructor() {
        return name + "-><init>()V";
    }
}
