package de.uni_passau.fim.auermich.android_graphs.core.utility;

public class MenuItem {
    private final String id;
    private final String titleId;

    public MenuItem(String id, String titleId) {
        this.id = id;
        this.titleId = titleId;
    }

    public String getId() {
        return id;
    }

    public String getTitleId() {
        return titleId;
    }
}
