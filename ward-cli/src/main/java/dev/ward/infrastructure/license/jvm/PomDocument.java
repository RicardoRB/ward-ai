package dev.ward.infrastructure.license.jvm;

import dev.ward.domain.license.DeclaredLicense;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** The parts of a POM that the license lookup reads. */
final class PomDocument {
  private final Element project;

  private PomDocument(final Element project) {
    this.project = project;
  }

  /** Parses a POM with DOCTYPE and external entities refused; empty when it cannot be read. */
  static Optional<PomDocument> parse(final Path file) {
    try (InputStream stream = Files.newInputStream(file)) {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return Optional.of(
          new PomDocument(factory.newDocumentBuilder().parse(stream).getDocumentElement()));
    } catch (IOException | SAXException | ParserConfigurationException exception) {
      return Optional.empty();
    }
  }

  /** Returns the artifact id of the POM, or empty text. */
  String artifactId() {
    return text(project, "artifactId");
  }

  /** Returns the version of the POM, or the parent's when it declares none, or empty text. */
  String version() {
    final String own = text(project, "version");
    return own.isEmpty() ? parent().map(Coordinates::version).orElse("") : own;
  }

  /** Returns the declared licenses; empty when the POM has no {@code <licenses>}. */
  List<DeclaredLicense.Entry> licenses() {
    return children(project, "licenses").stream()
        .flatMap(licenses -> children(licenses, "license").stream())
        .map(license -> new DeclaredLicense.Entry(text(license, "name"), text(license, "url")))
        .filter(entry -> !entry.name().isEmpty() || !entry.url().isEmpty())
        .toList();
  }

  /** Returns the coordinates of the parent POM, when it names all three parts. */
  Optional<Coordinates> parent() {
    return children(project, "parent").stream()
        .findFirst()
        .map(
            parent ->
                new Coordinates(
                    text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version")))
        .filter(
            parent ->
                !parent.group().isEmpty()
                    && !parent.artifact().isEmpty()
                    && !parent.version().isEmpty());
  }

  private static List<Element> children(final Element parent, final String name) {
    final List<Element> found = new ArrayList<>();
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element && name.equals(element.getLocalName())) {
        found.add(element);
      }
    }
    return found;
  }

  private static String text(final Element parent, final String name) {
    return children(parent, name).stream()
        .findFirst()
        .map(element -> element.getTextContent().trim())
        .orElse("");
  }

  /**
   * The coordinates of a POM.
   *
   * @param group the group id
   * @param artifact the artifact id
   * @param version the version
   */
  record Coordinates(String group, String artifact, String version) {
    @Override
    public String toString() {
      return group + ":" + artifact + ":" + version;
    }
  }
}
