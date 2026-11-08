package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Reads the XML reports of the tools. */
final class ParityXml {

  private ParityXml() {}

  /** Parses {@code file} without external entities or document types. */
  static Document parse(final Path file) throws IOException {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(file.toFile());
    } catch (final ParserConfigurationException | SAXException exception) {
      throw new IOException("cannot read " + file, exception);
    }
  }

  /** Returns the elements named {@code tag} below {@code parent}. */
  static Element[] elements(final Element parent, final String tag) {
    final NodeList nodes = parent.getElementsByTagName(tag);
    final Element[] elements = new Element[nodes.getLength()];
    for (int index = 0; index < elements.length; index++) {
      elements[index] = (Element) nodes.item(index);
    }
    return elements;
  }

  /** Returns the integer attribute, or {@code 0} when it is missing or not a number. */
  static int number(final Element element, final String attribute) {
    final String text = element.getAttribute(attribute);
    return text.matches("\\d+") ? Integer.parseInt(text) : 0;
  }
}
