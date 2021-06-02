package de.uni_passau.fim.auermich.android_graphs.core.app.components;

import org.jf.dexlib2.iface.ClassDef;

public class Fragment extends AbstractComponent {

    public Fragment(ClassDef clazz, ComponentType type) {
        super(clazz, type);
    }

    public String onAttachMethod() {
        return getName() + "->onAttach(Landroid/content/Context;)V";
    }

    public String onCreateMethod() {
        return getName() + "->onCreate(Landroid/os/Bundle;)V";
    }

    public String onCreateViewMethod() {
        return getName() + "->onCreateView(Landroid/view/LayoutInflater;" +
                "Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;";
    }

    public String onActivityCreatedMethod() {
        return getName() + "->onActivityCreated(Landroid/os/Bundle;)V";
    }

    public String onViewStateRestoredMethod() {
        return getName() + "->onViewStateRestored(Landroid/os/Bundle;)V";
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

    public String onDestroyViewMethod() {
        return getName() + "->onDestroyView()V";
    }

    public String onDestroyMethod() {
        return getName() + "->onDestroy()V";
    }

    public String onDetachMethod() {
        return getName() + "->onDetach()V";
    }

    public String onSaveInstanceStateMethod() { return  getName() + "->onSaveInstanceState(Landroid/os/Bundle;)V"; }

    // only for DialogFragment classes
    public String onCreateDialogMethod() { return getName() + "->onCreateDialog(Landroid/os/Bundle;)Landroid/app/Dialog;"; }

    // only for PreferenceFragmentCompat and PreferenceFragment classes
    public String onCreatePreferencesMethod() { return getName() + "->onCreatePreferences(Landroid/os/Bundle;Ljava/lang/String;)V"; }
}
