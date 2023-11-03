package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import de.uni_passau.fim.auermich.android_graphs.core.utility.MethodUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractComponent implements Component {

    private final ClassDef clazz;
    private final String name;
    private final ComponentType type;
    private final List<String> constructors;
    private List<String> superClasses;

    public AbstractComponent(ClassDef clazz, ComponentType type) {
        this.clazz = clazz;
        this.name = clazz.toString();
        this.type = type;
        constructors = new ArrayList<>();
        for (Method method : clazz.getDirectMethods()) {
            final String methodSignature = method.toString();
            if (MethodUtils.isConstructorCall(methodSignature)) {
                constructors.add(methodSignature);
            }
        }
    }

    @Override
    public List<String> getSuperClasses() {
        if (superClasses == null) {
            throw new IllegalStateException("Retrieving super classes before being set!");
        } else {
            return superClasses;
        }
    }

    @Override
    public void setSuperClasses(List<String> superClasses) {
        if (this.superClasses == null) {
            this.superClasses = superClasses;
        } else {
            throw new IllegalStateException("The super classes can be only set once!");
        }
    }

    @Override
    public List<String> getConstructors() {
        return constructors;
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
