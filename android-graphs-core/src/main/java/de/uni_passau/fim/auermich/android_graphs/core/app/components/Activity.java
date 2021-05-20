package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Activity extends AbstractComponent {

    // the fragments that are hosted by the activity
    private Set<Fragment> hostingFragments = new HashSet<>();

    public Activity(ClassDef clazz, ComponentType type) {
        super(clazz, type);
    }

    public void addHostingFragment(Fragment fragment) {
        hostingFragments.add(fragment);
    }

    public Set<Fragment> getHostingFragments() {
        return Collections.unmodifiableSet(hostingFragments);
    }

    public String onCreateMethod() {
        return getName() + "->onCreate(Landroid/os/Bundle;)V";
    }

    public String onStartMethod() {
        return getName() + "->onStart()V";
    }

    public String onResumeMethod() {
        return getName() + "->onResume()V";
    }

    public String onPauseMethod() {
        return getName() + "->onPause()V";
    }

    public String onStopMethod() {
        return getName() + "->onStop()V";
    }

    public String onDestroyMethod() {
        return getName() + "->onDestroy()V";
    }

    public String onRestartMethod() {
        return getName() + "->onRestart()V";
    }

    public String onRestoreInstanceStateMethod() { return getName() + "->onRestoreInstanceState(Landroid/os/Bundle;)V"; }

    public String onRestoreInstanceStateOverloadedMethod() {
        return getName() + "->onRestoreInstanceState(Landroid/os/Bundle;Landroid/os/PersistableBundle;)V"; }

    public String onPostCreateMethod() { return getName() + "->onPostCreate(Landroid/os/Bundle;)V"; }

    public String onPostCreateOverloadedMethod() {
        return getName() + "->onPostCreate(Landroid/os/Bundle;Landroid/os/PersistableBundle;)V"; }

    public String onSaveInstanceStateMethod() {
        return getName() + "->onSaveInstanceState(Landroid/os/Bundle;)V"; }

    public String onSaveInstanceStateOverloadedMethod() {
        return getName() + "->onSaveInstanceState(Landroid/os/Bundle;Landroid/os/PersistableBundle;)V"; }
}
