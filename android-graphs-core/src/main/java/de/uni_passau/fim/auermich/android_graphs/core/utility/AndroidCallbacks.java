package de.uni_passau.fim.auermich.android_graphs.core.utility;

import java.util.HashSet;
import java.util.Set;

public final class AndroidCallbacks {

    private AndroidCallbacks() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Returns the set of possible Android-specific callbacks.
     *
     * @return Returns the set of possible callbacks.
     */
    public static Set<String> getCallbacks() {
        return new HashSet<>() {{
            // TODO: add further callbacks
            add("onDraw(Landroid/graphics/Canvas;)V");
            add("onSizeChanged(IIII)V");
            add("onPreferenceChange(Landroid/preference/Preference;Ljava/lang/Object;)Z");
        }};
    }
}
