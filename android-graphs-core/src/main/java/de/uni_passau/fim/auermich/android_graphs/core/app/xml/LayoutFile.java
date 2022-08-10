package de.uni_passau.fim.auermich.android_graphs.core.app.xml;

import de.uni_passau.fim.auermich.android_graphs.core.utility.MenuItem;
import de.uni_passau.fim.auermich.android_graphs.core.utility.MenuUtils;
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
import java.util.stream.Stream;

/**
 * Mirrors a layout file (XML) contained in the res/layout/ folder of an APK.
 */
public class LayoutFile {

    private static final Logger LOGGER = LogManager.getLogger(LayoutFile.class);

    /**
     * The file path to the layout file in the res/layout/ folder.
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

        return rootElement.elements().stream()
                .map(element -> {
                    if (element.getName().equals("item")) {
                        String fullId = element.attributeValue("id");
                        String fullTitle = element.attributeValue("title");

                        if (fullId == null || fullTitle == null) {
                            LOGGER.warn("Having trouble parsing element of menu " + layoutFile.getName());
                            return Optional.<MenuItem>empty();
                        }

                        String id = fullId.split("@id/|@android:id/")[1];
                        String[] fullTitleParts = fullTitle.split("@string/|@android:string/");
                        String title = fullTitleParts.length == 2 ? fullTitleParts[1] : fullTitleParts[0];

                        return Optional.of(new MenuItem(id, title));
                    } else {
                        throw new IllegalStateException("Unknown tag for menu item: " + element.getName());
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * Searches for a layout file based on a resource id. Traverses the public.xml file
     * to find a match.
     *
     * @param decodingOutputPath The path where the APK was decoded.
     * @param resourceID The resource id.
     * @return Returns a layout file corresponding to the given resource id. If no such
     *          file could be found, {@code null} is returned.
     */
    public static LayoutFile findLayoutFile(File decodingOutputPath, String resourceID) {
        return findFile(decodingOutputPath, resourceID, "layout");
    }

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
}
