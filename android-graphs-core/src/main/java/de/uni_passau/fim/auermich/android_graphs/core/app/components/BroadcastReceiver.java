package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

public class BroadcastReceiver extends AbstractComponent {

    private boolean isDynamicReceiver;

    public BroadcastReceiver(ClassDef clazz, ComponentType type) {
        super(clazz, type);
    }

    public boolean isDynamicReceiver() {
        return isDynamicReceiver;
    }

    public void setDynamicReceiver(boolean dynamicReceiver) {
        isDynamicReceiver = dynamicReceiver;
    }

    public String onReceiveMethod() {
        return getName() + "->onReceive(Landroid/content/Context;Landroid/content/Intent;)V";
    }
}
