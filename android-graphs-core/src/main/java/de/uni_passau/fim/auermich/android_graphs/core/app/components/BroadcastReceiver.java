package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import com.android.tools.smali.dexlib2.iface.ClassDef;

public class BroadcastReceiver extends AbstractComponent {

    private boolean isDynamicReceiver;

    /**
     * The action to which the broadcast reacts.
     */
    private String action;

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
        return getSuperClasses().stream()
                .anyMatch(superClass -> superClass.equals("Landroid/appwidget/AppWidgetProvider;"));
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

    /**
     * Sets an action through which the broadcast receiver can be triggered.
     * .
     * @param action The action to which the broadcast receiver reacts.
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Gets the action to which the broadcast receiver reacts if any.
     *
     * @return Returns the action to which the broadcast receiver reacts if any.
     */
    public String getAction() {
        return this.action;
    }

    /**
     * Checks whether the broadcast receiver has specified an action.
     *
     * @return Returns {@code true} if there is an action, otherwise {@code false}.
     */
    public boolean hasAction() {
        return this.action != null;
    }
}
