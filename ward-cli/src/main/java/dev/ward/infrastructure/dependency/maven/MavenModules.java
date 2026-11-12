package dev.ward.infrastructure.dependency.maven;

import dev.ward.domain.dependency.DependencyInventoryException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Finds the POMs of a Maven reactor: each module's {@code groupId:artifactId} and its path. */
final class MavenModules {
  private static final String POM = "pom.xml";

  private final Path root;
  private final Map<String, String> paths = new LinkedHashMap<>();
  private final List<Path> poms = new ArrayList<>();

  private MavenModules(final Path root) {
    this.root = root;
  }

  /**
   * Reads the reactor of {@code root}, following {@code <modules>} recursively.
   *
   * @throws DependencyInventoryException if a POM cannot be read
   */
  static MavenModules read(final Path root) {
    final MavenModules modules = new MavenModules(root);
    modules.visit(root, "", "");
    return modules;
  }

  /** Returns the module path (empty for the root) by {@code groupId:artifactId}. */
  Map<String, String> paths() {
    return paths;
  }

  /** Returns every POM of the reactor, the root's first. */
  List<Path> poms() {
    return poms;
  }

  private void visit(final Path dir, final String path, final String inheritedGroup) {
    final Path pom = dir.resolve(POM);
    if (!Files.isRegularFile(pom) || poms.contains(pom)) {
      return;
    }
    poms.add(pom);
    final Element project = parse(pom).getDocumentElement();
    final String group =
        text(project, "groupId", text(child(project, "parent"), "groupId", inheritedGroup));
    paths.put(group + ":" + text(project, "artifactId", ""), path);
    final Element modules = child(project, "modules");
    if (modules != null) {
      final NodeList listed = modules.getElementsByTagName("module");
      for (int index = 0; index < listed.getLength(); index++) {
        final String module = listed.item(index).getTextContent().trim();
        final String directory =
            module.endsWith("/" + POM)
                ? module.substring(0, module.length() - POM.length() - 1)
                : module;
        visit(dir.resolve(directory).normalize(), join(path, directory), group);
      }
    }
  }

  private Document parse(final Path pom) {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      final DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(null);
      return builder.parse(pom.toFile());
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new DependencyInventoryException(
          root.toString(),
          root.relativize(pom).toString().replace('\\', '/'),
          "it is not a readable POM",
          "fix the POM so that Maven can read it");
    }
  }

  private static Element child(final Element parent, final String name) {
    if (parent == null) {
      return null;
    }
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && element.getTagName().equals(name)) {
        return element;
      }
    }
    return null;
  }

  private static String text(final Element parent, final String name, final String fallback) {
    final Element element = child(parent, name);
    return element == null ? fallback : element.getTextContent().trim();
  }

  private static String join(final String first, final String second) {
    return first.isEmpty() ? second : first + "/" + second;
  }
}
