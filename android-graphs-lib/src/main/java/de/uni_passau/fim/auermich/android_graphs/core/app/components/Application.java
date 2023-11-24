package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import com.android.tools.smali.dexlib2.iface.ClassDef;

public class Application extends AbstractComponent {

    public Application(ClassDef clazz, ComponentType type) {
        super(clazz, type);
    }

    public String onCreateMethod() {
        return getName() + "->onCreate()V";
    }

    public String onLowMemoryMethod() { return getName() + "->onLowMemory()V"; }

    public String onTerminateMethod() { return getName() + "->onTerminate()V"; }

    public String onTrimMemoryMethod() { return getName() + "->onTrimMemory(I)V"; }

    public String onConfigurationChangedMethod() {
        return getName() + "->onConfigurationChanged(Landroid/content/res/Configuration;)V"; }
}
