package de.uni_passau.fim.auermich.android_graphs.core.app.xml;

import de.uni_passau.fim.auermich.android_graphs.core.utility.MenuItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Mirrors a layout file (XML) contained in the res folder of an APK.
 */
public class LayoutFile {

    private static final Logger LOGGER = LogManager.getLogger(LayoutFile.class);

    /**
     * The file path to the layout file in the res folder.
     */
    private final File layoutFile;

    private LayoutFile(File layoutFile) {
        this.layoutFile = layoutFile;
    }

    /**
     * Looks up a layout file for declared callbacks. A callback is defined through the
     * tag 'onClick' and its value references the method name. A layout file hosts multiple views, e.g. buttons,
     * thus there can be multiple callbacks.
     *
     * @return Returns the callbacks (method names) declared in the layout file.
     */
    public List<String> parseCallbacks() {

        LOGGER.debug("Parsing callbacks in " + layoutFile.getName() + "!");

        List<String> callbacks = new ArrayList<>();
        SAXReader reader = new SAXReader();
        Document document = null;

        try {
            document = reader.read(layoutFile);
            Element rootElement = document.getRootElement();

            Queue<Element> queue = new LinkedList<>();
            queue.add(rootElement);

            // inspect each element
            while (!queue.isEmpty()) {
                Element element = queue.poll();
                queue.addAll(element.elements());

                if (element.getName().equals("include")) {
                    /*
                    * We need to inspect the sub layout file(s) as well.
                     */
                    String attribute = element.attributeValue("layout");
                    if (attribute != null) {
                        String filePath = layoutFile.getParent() + File.separator + attribute.split("@layout/")[1] + ".xml";
                        LayoutFile subLayoutFile = new LayoutFile(new File(filePath));
                        callbacks.addAll(subLayoutFile.parseCallbacks());
                    }
                }

                // TODO: check for further callbacks
                String onClickCallback = element.attributeValue("onClick");
                if (onClickCallback != null) {
                    callbacks.add(onClickCallback);
                }

                String onLongClickCallback = element.attributeValue("onLongClick");
                if (onLongClickCallback != null) {
                    callbacks.add(onLongClickCallback);
                }
            }
        } catch (DocumentException e) {
            LOGGER.error("Reading layout file " + layoutFile.getName() + " failed");
            LOGGER.error(e.getMessage());
        }
        return callbacks;
    }

    /**
     * Retrieves the navigation graph from the given fragment.
     *
     * @param fragmentName The name of the fragment from which the navigation graph should be derived.
     * @return Returns the name of the navigation graph or {@code null} if no such graph exists.
     */
    public String parseNavigationGraphOfFragment(final String fragmentName) {

        final SAXReader reader = new SAXReader();
        Document document = null;

        try {
            document = reader.read(layoutFile);
            final Element rootElement = document.getRootElement();

            final Queue<Element> queue = new LinkedList<>();
            queue.add(rootElement);

            // inspect each element
            while (!queue.isEmpty()) {

                final Element element = queue.poll();
                queue.addAll(element.elements());

                // search for given fragment
                if (element.getName().equals("fragment")
                        && (Objects.equals(fragmentName, element.attributeValue("name"))
                        || Objects.equals(fragmentName, element.attributeValue("class")))) {
                    // check for attribute navGraph, e.g., app:navGraph="@navigation/nav_garden"
                    final String navGraph = element.attributeValue("navGraph");
                    return navGraph != null
                            ? navGraph.split("/")[1]
                            : null;
                }
            }
        } catch (DocumentException e) {
            LOGGER.error("Reading layout file " + layoutFile.getName() + " failed");
            LOGGER.error(e.getMessage());
        }
        return null;
    }

    /**
     * Parses the fragments from the underlying layout file.
     *
     * @return Returns the fragment names contained in the layout file.
     */
    public Set<String> parseFragments() {
        SAXReader reader = new SAXReader();

        try {
            return parseFragmentNames(reader.read(layoutFile).getRootElement()).collect(Collectors.toSet());
        } catch (DocumentException e) {
            LOGGER.error("Reading layout file " + layoutFile.getName() + " failed");
            LOGGER.error(e.getMessage());
            return Set.of();
        }
    }

    /**
     * Parses the fragment names from the layout file.
     *
     * @param element The root element of the layout file.
     * @return Returns the fragment names contained in the layout file.
     */
    private Stream<String> parseFragmentNames(Element element) {
        // https://stackoverflow.com/questions/10162983/activity-layout-fragment-class-vs-androidname-attributes
        Stream<String> thisElement = element.getName().equals("fragment")
                ? element.attributeValue("name") != null
                ? Stream.of(element.attributeValue("name")) : Stream.of(element.attributeValue("class"))
                : Stream.empty();
        return Stream.concat(thisElement, element.elements().stream().flatMap(this::parseFragmentNames));
    }

    /**
     * Parses the menu items contained in the underlying layout file.
     *
     * @return Returns the extracted menu items.
     */
    public Stream<MenuItem> parseMenuItems() {

        LOGGER.debug("Parsing menus in " + layoutFile.getName() + "!");

        SAXReader reader = new SAXReader();
        Document document;

        try {
            document = reader.read(layoutFile);
        } catch (DocumentException e) {
            LOGGER.error("Reading layout file " + layoutFile.getName() + " failed");
            LOGGER.error(e.getMessage());
            return Stream.empty();
        }

        Element rootElement = document.getRootElement();
        return parseAllMenuItems(rootElement);
    }

    /**
     * Parses the menu items in the underlying layout file.
     *
     * @param root The root element of the underlying layout file.
     * @return Returns the parsed menu items.
     */
    private Stream<MenuItem> parseAllMenuItems(Element root) {
        // https://developer.android.com/develop/ui/views/components/menus

        if (root.elements() == null) {
            return Stream.empty();
        } else {
            return root.elements().stream()
                    .flatMap(element -> {
                        if (element.getName().equals("item")) {
                            return Stream.concat(parseItem(element).stream(), parseAllMenuItems(element));
                        } else if (element.getName().equals("group")) {
                            return parseAllMenuItems(element);
                        } else if (element.getName().equals("menu")) {
                            return parseAllMenuItems(element);
                        } else {
                            throw new IllegalStateException("Unknown tag for menu item: " + element.getName());
                        }
                    });
        }
    }

    /**
     * Parses a menu item from the given element.
     *
     * @param element The xml representation of a menu item.
     * @return Returns the parsed menu item or an empty optional.
     */
    private Optional<MenuItem> parseItem(Element element) {

        final String fullId = element.attributeValue("id");
        final String fullTitle = element.attributeValue("title");

        if (fullId == null || fullTitle == null) {
            LOGGER.warn("Having troubles parsing element of menu " + layoutFile.getName());
            return Optional.empty();
        }

        /*
        * An entry looks as follows:
        *
        * <item android:id="@id/action_settings" android:title="@string/action_settings" ... />
         */
        final String resourceID = fullId.split("@id/|@android:id/")[1];
        final String[] fullTitleParts = fullTitle.split("@string/|@android:string/");
        final String titleID = fullTitleParts.length == 2 ? fullTitleParts[1] : fullTitleParts[0];

        return Optional.of(new MenuItem(resourceID, titleID));
    }

    /**
     * Searches for a navigation layout file based on the given name.
     *
     * @param decodingOutputPath The path where the APK was decoded.
     * @param name The name of the navigation layout file.
     * @return Returns a layout file corresponding to the given name, otherwise {@code null}.
     */
    public static LayoutFile findNavigationLayoutFile(final File decodingOutputPath, final String name) {
        final File navigationLayoutFilePath = new File(decodingOutputPath,
                Paths.get("res", "navigation", name + ".xml").toString());
        return navigationLayoutFilePath.exists() ? new LayoutFile(navigationLayoutFilePath) : null;
    }

    /**
     * Searches for a layout file based on a resource id. Traverses the public.xml file to find a match.
     *
     * @param decodingOutputPath The path where the APK was decoded.
     * @param resourceID The resource id.
     * @return Returns a layout file corresponding to the given resource id. If no such
     *          file could be found, {@code null} is returned.
     */
    public static LayoutFile findLayoutFile(File decodingOutputPath, String resourceID) {
        return findFile(decodingOutputPath, resourceID, "layout");
    }

    /**
     * Searches for a menu (layout) file based on a resource id. Traverses the public.xml file to find a match.
     *
     * @param decodingOutputPath The path where the APK was decoded.
     * @param resourceID The resource id.
     * @return Returns a menu (layout) file corresponding to the given resource id. If no such
     *          file could be found, {@code null} is returned.
     */
    public static LayoutFile findMenuFile(File decodingOutputPath, String resourceID) {
        return findFile(decodingOutputPath, resourceID, "menu");
    }

    private static LayoutFile findFile(File decodingOutputPath, String resourceID, String type) {
        final File publicXMLPath = new File(decodingOutputPath,
                Paths.get("res", "values", "public.xml").toString());

        SAXReader reader = new SAXReader();
        Document document = null;

        try {
            document = reader.read(publicXMLPath);
            Element rootElement = document.getRootElement();

            Iterator<Element> itr = rootElement.elementIterator();
            while (itr.hasNext()) {

                /*
                * Each node represents a 'public' tag of the following form:
                *
                *     <public type="layout" name="activity_main" id="0x7f09001c" />
                *     <public type="layout" name="activity_second" id="0x7f09001d" />
                 */
                Node node = (Node) itr.next();
                Element element = (Element) node;

                String layoutFile = element.attributeValue("name");
                String layoutResourceID = element.attributeValue("id");

                // check for match based on resource id
                if (layoutResourceID.equals(resourceID)) {
                    // ensure that the match refers to a layout file
                    if (type.equals(element.attributeValue("type"))) {
                        return new LayoutFile(new File(decodingOutputPath,
                                Paths.get("res", type, layoutFile + ".xml").toString()));
                    }
                }
            }
        } catch (DocumentException e) {
            LOGGER.error("Reading public.xml failed");
            LOGGER.error(e.getMessage());
            throw new IllegalStateException(e);
        }

        LOGGER.warn("Couldn't find a layout file for the resource id: " + resourceID);
        return null;
    }

    @Override
    public String toString() {
        return "LayoutFile{" +
                "layoutFile=" + layoutFile +
                '}';
    }
}
