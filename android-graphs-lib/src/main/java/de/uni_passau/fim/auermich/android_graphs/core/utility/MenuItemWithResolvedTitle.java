package de.uni_passau.fim.auermich.android_graphs.core.utility;

/**
 * Represents a menu item with a resolved menu item text.
 */
public class MenuItemWithResolvedTitle extends MenuItem {

    /**
     * The menu item title text.
     */
    private final String title;

    public MenuItemWithResolvedTitle(String id, String titleId, String title) {
        super(id, titleId);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
