package de.uni_passau.fim.auermich.app.xml;

import de.uni_passau.fim.auermich.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class LayoutFile {

    private static final Logger LOGGER = LogManager.getLogger(LayoutFile.class);

    private final File layoutFile;

    private LayoutFile(File layoutFile) {
        this.layoutFile = layoutFile;
    }

    public List<String> parseCallbacks() {

        List<String> callbacks = new ArrayList<>();
        SAXReader reader = new SAXReader();
        Document document = null;

        try {

            document = reader.read(layoutFile);
            Element rootElement = document.getRootElement();

            Iterator itr = rootElement.elementIterator();
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

    public static LayoutFile findLayoutFile(String decodingOutputPath, String resourceID) {

        final String publicXMLPath = decodingOutputPath + File.separator + "res" + File.separator
                + "values" + File.separator + "public.xml";

        SAXReader reader = new SAXReader();
        Document document = null;

        try {
            document = reader.read(new File(publicXMLPath));
            Element rootElement = document.getRootElement();

            Iterator itr = rootElement.elementIterator();
            while (itr.hasNext()) {

                // each node is a <public ... /> xml tag
                Node node = (Node) itr.next();
                Element element = (Element) node;
                // LOGGER.debug("ElementName: " + node.getName());

                // each <public /> tag contains the attributes type,name,id
                String layoutFile = element.attributeValue("name");
                String layoutResourceID = element.attributeValue("id");

                // TODO: we could add a check for attribute type == layout

                if (layoutResourceID.equals(resourceID)) {
                    LOGGER.debug("Associated layout name: " + layoutFile);
                    return new LayoutFile(new File(decodingOutputPath + File.separator + "res"
                            + File.separator + "layout" + File.separator + layoutFile + ".xml"));
                }
            }
        } catch (DocumentException e) {
            LOGGER.error("Reading public.xml failed");
            LOGGER.error(e.getMessage());
        }
        return null;
    }
}
