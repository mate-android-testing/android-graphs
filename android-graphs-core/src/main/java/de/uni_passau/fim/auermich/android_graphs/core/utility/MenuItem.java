package de.uni_passau.fim.auermich.android_graphs.core.utility;

/**
 * Represents a menu item.
 */
public class MenuItem {

    /**
     * The menu item resource id.
     */
    private final String id;

    /**
     * The menu item title id.
     */
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
