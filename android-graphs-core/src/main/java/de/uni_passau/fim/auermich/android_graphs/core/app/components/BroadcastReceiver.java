package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

public class BroadcastReceiver extends AbstractComponent {

    // TODO: differentiate between static and dynamic receivers

    public BroadcastReceiver(ClassDef clazz, ComponentType type) {
        super(clazz, type);
    }

    public String onReceiveMethod() {
        return getName() + "->onReceive(Landroid/content/Context;Landroid/content/Intent;)V";
    }
}
