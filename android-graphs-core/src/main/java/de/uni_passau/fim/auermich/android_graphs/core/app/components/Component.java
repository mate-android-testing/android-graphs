package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

public interface Component {

    ClassDef getClazz();

    String getName();

    ComponentType getComponentType();

    String getDefaultConstructor();
}
