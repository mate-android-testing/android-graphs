package de.uni_passau.fim.auermich.android_graphs.core.app.xml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mirrors the AndroidManifest.xml file contained within each APK.
 */
public class Manifest {

    private static final Logger LOGGER = LogManager.getLogger(Manifest.class);

    private final String mainActivity;
    private final String packageName;
    private final List<String> activities;
    private final List<String> services;
    private final List<String> receivers;

    /**
     * Creates a new manifest with the given package name and main activity.
     *
     * @param packageName The package name.
     * @param mainActivity The name of the main activity.
     * @param activities The list of activities.
     * @param services The list of services.
     * @param receivers The list of receivers.
     */
    private Manifest(final String packageName, final String mainActivity, final List<String> activities,
                     final List<String> services, final List<String> receivers) {
        this.packageName = packageName;
        this.mainActivity = mainActivity;
        this.activities = activities;
        this.services = services;
        this.receivers = receivers;
    }

    /**
     * Parses the AndroidManifest.xml file!
     *
     * @param manifestFile The path to the manifest file.
     * @return Returns the parsed manifest file.
     */
    public static Manifest parse(File manifestFile) {

        // TODO: Parse intent filters (actions, categories, etc) per component from manifest!

        assert manifestFile.exists();
        LOGGER.debug("Parsing AndroidManifest.xml!");

        SAXReader reader = new SAXReader();
        Document document = null;

        try {
            document = reader.read(manifestFile);
        } catch (DocumentException e) {
            LOGGER.error("Couldn't load AndroidManifest.xml!");
            throw new IllegalStateException("Couldn't load AndroidManifest.xml!");
        }

        final Element rootElement = document.getRootElement();

        String packageName = rootElement.attributeValue("package");
        if (packageName != null) {
            LOGGER.debug("Package name: " + packageName);
        }

        final List<String> activities = new ArrayList<>();
        final AtomicReference<String> mainActivity = new AtomicReference<>(null);

        List<Node> activityNodes = rootElement.selectNodes("application/activity");
        activityNodes.forEach(activityNode -> {

            final Element activityTag = (Element) activityNode;
            final String activityName = getFullyQualifiedName(packageName, activityTag.attributeValue("name"));
            activities.add(activityName);

            // only traverse activities unless we found main activity
            if (mainActivity.get() == null) {

                activityNode.selectNodes("intent-filter").forEach(intentFilterNode -> {

                    AtomicBoolean containsMainActivityAction = new AtomicBoolean(false);
                    AtomicBoolean containsMainActivityCategory = new AtomicBoolean(false);

                    // parse action tags
                    intentFilterNode.selectNodes("action").forEach(actionNode -> {
                        Element actionTag = (Element) actionNode;
                        String action = actionTag.attributeValue("name");
                        if (action != null && action.equals("android.intent.action.MAIN")) {
                            containsMainActivityAction.set(true);
                        }
                    });

                    // parse category tags
                    intentFilterNode.selectNodes("category").forEach(categoryNode -> {
                        Element categoryTag = (Element) categoryNode;
                        String category = categoryTag.attributeValue("name");
                        if (category != null && category.equals("android.intent.category.LAUNCHER")) {
                            containsMainActivityCategory.set(true);
                        }
                    });

                    if (containsMainActivityAction.get() && containsMainActivityCategory.get()) {
                        LOGGER.debug("MainActivity: " + activityName);
                        mainActivity.set(activityName);
                    }
                });
            }
        });

        // if main activity not yet found, then parse activity-alias tags
        if (mainActivity.get() == null) {

            List<Node> activityAliases = rootElement.selectNodes("application/activity-alias");
            activityAliases.forEach(activityAliasNode -> {

                // only traverse activity-aliases unless we found main activity
                if (mainActivity.get() == null) {

                    activityAliasNode.selectNodes("intent-filter").forEach(intentFilterNode -> {

                        AtomicBoolean containsMainActivityAction = new AtomicBoolean(false);
                        AtomicBoolean containsMainActivityCategory = new AtomicBoolean(false);

                        // parse action tags
                        intentFilterNode.selectNodes("action").forEach(actionNode -> {
                            Element actionTag = (Element) actionNode;
                            String action = actionTag.attributeValue("name");
                            if (action != null && action.equals("android.intent.action.MAIN")) {
                                containsMainActivityAction.set(true);
                            }
                        });

                        // parse category tags
                        intentFilterNode.selectNodes("category").forEach(categoryNode -> {
                            Element categoryTag = (Element) categoryNode;
                            String category = categoryTag.attributeValue("name");
                            if (category != null && category.equals("android.intent.category.LAUNCHER")) {
                                containsMainActivityCategory.set(true);
                            }
                        });

                        if (containsMainActivityAction.get() && containsMainActivityCategory.get()) {
                            Element activityAliasTag = (Element) activityAliasNode;
                            // the activity alias tag defines a reference via targetActivity
                            String activityName = activityAliasTag.attributeValue("targetActivity");
                            if (activityName != null) {
                                activityName = getFullyQualifiedName(packageName, activityName);
                                LOGGER.debug("MainActivity: " + activityName);
                                mainActivity.set(activityName);
                            }
                        }
                    });
                }
            });
        }

        // parse services
        final List<String> services = new ArrayList<>();
        List<Node> serviceNodes = rootElement.selectNodes("application/service");
        serviceNodes.forEach(serviceNode -> {
            final Element serviceTag = (Element) serviceNode;
            final String serviceName = getFullyQualifiedName(packageName, serviceTag.attributeValue("name"));
            services.add(serviceName);
        });

        // parse broadcast receivers
        final List<String> receivers = new ArrayList<>();
        List<Node> receiverNodes = rootElement.selectNodes("application/receiver");
        receiverNodes.forEach(receiverNode -> {
            final Element receiverTag = (Element) receiverNode;
            final String receiverName = getFullyQualifiedName(packageName, receiverTag.attributeValue("name"));
            receivers.add(receiverName);
        });

        // NOTE: There can be apps without a dedicated main activity.
        return new Manifest(packageName, mainActivity.get(), activities, services, receivers);
    }

    /**
     * Retrieves the fully-qualified name.
     *
     * @param packageName The package name of the AUT.
     * @param name The name of the component, potentially not fully-qualified.
     * @return Returns the FQN for the given component.
     */
    private static String getFullyQualifiedName(final String packageName, final String name) {
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (!name.contains(".")) {
            // Sometimes solely the blank activity class name is given.
            return packageName + "." + name;
        } else {
            return name;
        }
    }

    /**
     * Returns the name of the main activity.
     *
     * @return Returns the main activity.
     */
    public String getMainActivity() {
        return mainActivity;
    }

    /**
     * Returns the package name.
     *
     * @return Returns the package name.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Returns the activities declared in the manifest.
     *
     * @return Returns the activities declared in the manifest.
     */
    public List<String> getActivities() {
        return activities;
    }

    /**
     * Returns the services declared in the manifest.
     *
     * @return Returns the services declared in the manifest.
     */
    public List<String> getServices() {
        return services;
    }

    /**
     * Returns the broadcast receivers declared in the manifest.
     *
     * @return Returns the broadcast receivers declared in the manifest.
     */
    public List<String> getReceivers() {
        return receivers;
    }
}
