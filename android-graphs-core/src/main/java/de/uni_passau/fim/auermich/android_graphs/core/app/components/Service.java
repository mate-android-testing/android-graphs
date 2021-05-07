package de.uni_passau.fim.auermich.android_graphs.core.app.components;

public class Service extends AbstractComponent {

    // whether the service is invoked via startService() or bindService() or both
    private boolean isBound;
    private boolean isStarted;

    // only used in combination with bound services
    private String binder;
    private String serviceConnection;

    public Service(String name, ComponentType type) {
        super(name, type);
    }

    public boolean isBound() {
        return isBound;
    }

    public void setBound(boolean bound) {
        isBound = bound;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void setStarted(boolean started) {
        isStarted = started;
    }

    public String getBinder() {
        return binder;
    }

    public void setBinder(String binder) {
        this.binder = binder;
    }

    public String getServiceConnection() {
        return serviceConnection;
    }

    public void setServiceConnection(String serviceConnection) {
        this.serviceConnection = serviceConnection;
    }

    public String onCreateMethod() {
        return getName() + "->onCreate()V";
    }

    public String onStartCommandMethod() {
        return getName() + "->onStartCommand(Landroid/content/Intent;II)I";
    }

    public String onStartMethod() {
        return getName() + "->onStart(Landroid/content/Intent;I)V";
    }

    public String onBindMethod() {
        return getName() + "->onBind(Landroid/content/Intent;)Landroid/os/IBinder;";
    }

    public String onUnbindMethod() {
        return getName() + "->onUnbind(Landroid/content/Intent;)Z";
    }

    public String onDestroyMethod() {
        return getName() + "->onDestroy()V";
    }
}
