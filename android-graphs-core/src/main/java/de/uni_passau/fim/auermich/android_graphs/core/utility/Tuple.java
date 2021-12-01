package de.uni_passau.fim.auermich.android_graphs.core.utility;

/**
 * A simple tuple representation for generic types (x,y).
 *
 * @param <X> The generic type of the first entry.
 * @param <Y> The generic type of the second entry.
 */
public class Tuple<X, Y> {

    private final X x;
    private final Y y;

    public Tuple(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    public X getX() {
        return x;
    }

    public Y getY() {
        return y;
    }
}
