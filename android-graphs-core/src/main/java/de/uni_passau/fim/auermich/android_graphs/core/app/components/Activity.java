package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Activity extends AbstractComponent {

    // the fragments that are hosted by the activity
    private Set<Fragment> hostingFragments = new HashSet<>();

    public Activity(String name, ComponentType type) {
        super(name, type);
    }

    public void addHostingFragment(Fragment fragment) {
        hostingFragments.add(fragment);
    }

    public Set<Fragment> getHostingFragments() {
        return Collections.unmodifiableSet(hostingFragments);
    }
}
