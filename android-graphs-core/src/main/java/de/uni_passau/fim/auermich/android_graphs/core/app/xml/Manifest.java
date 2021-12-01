package de.uni_passau.fim.auermich.android_graphs.core.app.xml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.util.Iterator;
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

    /**
     * Creates a new manifest with the given package name and main activity.
     *
     * @param packageName The package name.
     * @param mainActivity The name of the main activity.
     */
    private Manifest(final String packageName, final String mainActivity) {
        this.packageName = packageName;
        this.mainActivity = mainActivity;
    }

    /**
     * Parses the AndroidManifest.xml file!
     *
     * @param manifestFile The path to the manifest file.
     * @return Returns the parsed manifest file.
     */
    public static Manifest parse(File manifestFile) {
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
        Element rootElement = document.getRootElement();

        String packageName = rootElement.attributeValue("package");
        if (packageName != null) {
            LOGGER.debug("Package name: " + packageName);
        }

        AtomicReference<String> mainActivity = new AtomicReference<>("");

        List<Node> activities = rootElement.selectNodes("application/activity");
        activities.forEach(activityNode -> {

            // only traverse activities unless we found main activity
            if (mainActivity.get().isEmpty()) {

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
                        Element activityTag = (Element) activityNode;
                        String activityName = activityTag.attributeValue("name");
                        if (activityName != null) {
                            LOGGER.debug("MainActivity: " + activityName);
                            mainActivity.set(activityName);
                        }
                    }
                });
            }
        });

        // if main activity not yet found, then parse activity-alias tags
        if (mainActivity.get().isEmpty()) {

            List<Node> activityAliases = rootElement.selectNodes("application/activity-alias");
            activityAliases.forEach(activityAliasNode -> {

                // only traverse activity-aliases unless we found main activity
                if (mainActivity.get().isEmpty()) {

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
                                LOGGER.debug("MainActivity: " + activityName);
                                mainActivity.set(activityName);
                            }
                        }
                    });
                }
            });
        }

        if (mainActivity.get().isEmpty()) {
            throw new IllegalStateException("Couldn't parse main activity!");
        }

        return new Manifest(packageName, mainActivity.get());
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
}
