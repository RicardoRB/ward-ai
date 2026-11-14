package dev.ward.domain.apicompat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Runs japicmp on two jars and reads the types and members it reports as incompatible. */
final class JapicmpRun {
  private static final String CLASSPATH = "ward.parity.japicmp";
  private static final String CHANGE_LIST = "compatibilityChanges";
  private static final String CLASS_REMOVED = "CLASS_REMOVED";
  private static final String BINARY = "binaryCompatible";
  private static final String SOURCE = "sourceCompatible";
  private static final String CONSTRUCTORS = "constructors";

  private JapicmpRun() {}

  /** Returns {@code type} or {@code type#member} for every incompatible change. */
  static Set<String> changes(
      final boolean kotlin, final Path old, final Path revised, final Path xml)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    final List<String> command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty(CLASSPATH),
                "japicmp.JApiCmp",
                "-o",
                old.toString(),
                "-n",
                revised.toString(),
                "-x",
                xml.toString(),
                "--ignore-missing-classes"));
    if (kotlin) {
      final String stdlib = JapicmpFixtureBuild.stdlib().toString();
      command.addAll(List.of("--old-classpath", stdlib, "--new-classpath", stdlib));
    }
    JapicmpFixtureBuild.run(command, xml.getParent());
    final NodeList classes =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xml.toFile())
            .getElementsByTagName("class");
    final Set<String> keys = new TreeSet<>();
    for (int index = 0; index < classes.getLength(); index++) {
      collect((Element) classes.item(index), keys);
    }
    return keys;
  }

  private static void collect(final Element type, final Set<String> keys) {
    final String name = type.getAttribute("fullyQualifiedName");
    if (breaking(type) || breakingSupertype(type)) {
      keys.add(name);
      if (hasChange(type, CLASS_REMOVED)) {
        return;
      }
    }
    for (final String kind : List.of("methods", "fields", CONSTRUCTORS)) {
      for (final Element member : members(type, kind)) {
        if (breaking(member)) {
          keys.add(
              name + "#" + (CONSTRUCTORS.equals(kind) ? "<init>" : member.getAttribute("name")));
        }
      }
    }
  }

  /** The {@code method}, {@code field} or {@code constructor} elements of a list of the type. */
  private static List<Element> members(final Element type, final String list) {
    final List<Element> found = new ArrayList<>();
    for (Node node = type.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && list.equals(element.getTagName())) {
        found.addAll(elements(element.getChildNodes()));
      }
    }
    return found;
  }

  private static List<Element> elements(final NodeList nodes) {
    final List<Element> found = new ArrayList<>();
    for (int index = 0; index < nodes.getLength(); index++) {
      if (nodes.item(index) instanceof Element element) {
        found.add(element);
      }
    }
    return found;
  }

  /** Whether a superclass or interface of the type was removed or changed incompatibly. */
  private static boolean breakingSupertype(final Element type) {
    for (final String tag : List.of("superclass", "interface")) {
      for (final Element supertype : elements(type.getElementsByTagName(tag))) {
        if (breaking(supertype)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean breaking(final Element element) {
    return changes(element).stream()
        .anyMatch(
            change ->
                !Boolean.parseBoolean(change.getAttribute(BINARY))
                    || !Boolean.parseBoolean(change.getAttribute(SOURCE)));
  }

  private static boolean hasChange(final Element element, final String type) {
    return changes(element).stream().anyMatch(change -> type.equals(change.getAttribute("type")));
  }

  /** The {@code compatibilityChange} elements that belong to the element itself. */
  private static List<Element> changes(final Element element) {
    final List<Element> found = new ArrayList<>();
    for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element list && CHANGE_LIST.equals(list.getTagName())) {
        found.addAll(elements(list.getChildNodes()));
      }
    }
    return found;
  }
}
