package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a view component, e.g. a button. The base class is android.view.View:
 * https://developer.android.com/reference/android/view/View
 */
public class View extends AbstractComponent {

    public View(ClassDef clazz, ComponentType type) {
        super(clazz, type);
    }

    /**
     * Returns the possible callbacks that can be defined by a view component.
     *
     * @return Returns the set of possible callbacks.
     */
    public static Set<String> getCallbacks() {
        return new HashSet<>() {{
            // TODO: add further callbacks
            add("onDraw(Landroid/graphics/Canvas;)V");
            add("onSizeChanged(IIII)V");
        }};
    }
}
