package de.uni_passau.fim.auermich.android_graphs.core.app.components;

public class Service extends AbstractComponent {

    private boolean isBound;
    private boolean isStarted;
    private String binder;

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
}
