package de.uni_passau.fim.auermich.android_graphs.core.app.components;

public class Fragment extends AbstractComponent {

    public Fragment(String name, ComponentType type) {
        super(name, type);
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
}
