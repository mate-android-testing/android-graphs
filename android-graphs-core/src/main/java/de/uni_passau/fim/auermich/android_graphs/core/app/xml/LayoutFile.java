package de.uni_passau.fim.auermich.android_graphs.core.app.xml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
     *
     * @return
     */
    public List<String> parseCallbacks() {

        List<String> callbacks = new ArrayList<>();
        SAXReader reader = new SAXReader();
        Document document = null;

        try {

            document = reader.read(layoutFile);
            Element rootElement = document.getRootElement();

            Iterator<Element> itr = rootElement.elementIterator();
            while (itr.hasNext()) {

                // each node is a widget, e.g. a button, textView, ...
                // TODO: we may can exclude some sort of widgets
                Node node = (Node) itr.next();
                Element element = (Element) node;
                // LOGGER.debug(element.getName());

                // TODO: check if there are further callbacks than onClick, e.g. onLongClick
                // NOTE: we need to access the attribute WITHOUT its namespace -> can't use android:onClick!
                String onClickCallback = element.attributeValue("onClick");
                if (onClickCallback != null) {
                    LOGGER.debug(onClickCallback);
                    callbacks.add(onClickCallback);
                }
            }
        } catch (DocumentException e) {
            LOGGER.error("Reading layout file " + layoutFile.getName() + " failed");
            LOGGER.error(e.getMessage());
        }
        return callbacks;
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
                    if ("layout".equals(element.attributeValue("type"))) {
                        LOGGER.debug("Associated layout name: " + layoutFile);
                        return new LayoutFile(new File(decodingOutputPath,
                                Paths.get("res", "layout", layoutFile + ".xml").toString()));
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
