package de.uni_passau.fim.auermich.android_graphs.core.utility;

public class TranslatedMenuItem extends MenuItem {
    private final String title;

    public TranslatedMenuItem(String id, String titleId, String title) {
        super(id, titleId);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
