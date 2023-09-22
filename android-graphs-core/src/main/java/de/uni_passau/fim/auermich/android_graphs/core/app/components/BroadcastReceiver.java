package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

import java.util.Objects;

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

    // AppWidgetProvider -> https://developer.android.com/reference/android/appwidget/AppWidgetProvider
    public boolean isAppWidgetProvider() {
        return Objects.equals(getClazz().getSuperclass(), "Landroid/appwidget/AppWidgetProvider;");
    }

    public String onEnabledMethod() {
        return getName() + "->onEnabled(Landroid/content/Context;)V";
    }

    public String onDisabledMethod() {
        return getName() + "->onDisabled(Landroid/content/Context;)V";
    }

    public String onDeletedMethod() {
        return getName() + "->onDeleted(Landroid/content/Context;[I)V";
    }

    public String onUpdateMethod() {
        return getName() + "->onUpdate(Landroid/content/Context;Landroid/appwidget/AppWidgetManager;[I)V";
    }
}
