package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

import java.util.List;

/**
 * The interface for an android component, e.g. activity.
 */
public interface Component {

    ClassDef getClazz();

    String getName();

    ComponentType getComponentType();

    String getDefaultConstructor();

    List<String> getConstructors();

    List<String> getSuperClasses();

    void setSuperClasses(List<String> superClasses);
}
