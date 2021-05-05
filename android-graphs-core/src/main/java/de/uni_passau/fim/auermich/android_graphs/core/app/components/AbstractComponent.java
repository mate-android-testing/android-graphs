package de.uni_passau.fim.auermich.android_graphs.core.app.components;

public abstract class AbstractComponent implements Component {

    private final String name;
    private final ComponentType type;

    public AbstractComponent(String name, ComponentType type) {
        this.name = name;
        this.type = type;
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
}
