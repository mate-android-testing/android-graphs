package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

/**
 * The interface for an android component, e.g. activity.
 */
public interface Component {

    ClassDef getClazz();

    String getName();

    ComponentType getComponentType();

    String getDefaultConstructor();
}
