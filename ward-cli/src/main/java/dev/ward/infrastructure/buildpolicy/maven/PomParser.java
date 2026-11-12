package dev.ward.infrastructure.buildpolicy.maven;

import dev.ward.domain.buildpolicy.BuildFileException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Reads a POM into a tree with line numbers. DTDs and external entities are never resolved. */
final class PomParser {
  private final Deque<OpenElement> stack = new ArrayDeque<>();
  private final String shown;
  private PomNode root;

  private PomParser(final String shown) {
    this.shown = shown;
  }

  /**
   * Parses {@code pom}, named {@code shown} in errors.
   *
   * @throws BuildFileException if the file is unreadable, malformed or declares a DTD
   */
  static PomNode parse(final Path pom, final String shown) {
    final XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    try (InputStream input = Files.newInputStream(pom)) {
      final XMLStreamReader reader = factory.createXMLStreamReader(input);
      try {
        return new PomParser(shown).read(reader);
      } finally {
        reader.close();
      }
    } catch (final IOException | XMLStreamException exception) {
      throw new BuildFileException(shown, "the file is not well-formed XML", exception);
    }
  }

  private PomNode read(final XMLStreamReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      event(reader, reader.next());
    }
    if (root == null) {
      throw new BuildFileException(shown, "the file has no root element");
    }
    return root;
  }

  private void event(final XMLStreamReader reader, final int event) {
    switch (event) {
      case XMLStreamConstants.DTD, XMLStreamConstants.ENTITY_REFERENCE ->
          throw new BuildFileException(shown, "a DTD or entity is not allowed and is not resolved");
      case XMLStreamConstants.START_ELEMENT ->
          stack.push(new OpenElement(reader.getLocalName(), reader.getLocation().getLineNumber()));
      case XMLStreamConstants.CHARACTERS -> text(reader.getText());
      case XMLStreamConstants.END_ELEMENT -> close();
      default -> {
        // comments and processing instructions carry nothing a rule reads
      }
    }
  }

  private void text(final String text) {
    if (!stack.isEmpty()) {
      stack.peek().texts.add(text);
    }
  }

  private void close() {
    final PomNode done = stack.pop().build();
    if (stack.isEmpty()) {
      root = done;
    } else {
      stack.peek().children.add(done);
    }
  }

  /** An element being read. */
  private static final class OpenElement {
    private final String name;
    private final int line;
    private final List<String> texts = new ArrayList<>();
    private final List<PomNode> children = new ArrayList<>();

    OpenElement(final String name, final int line) {
      this.name = name;
      this.line = line;
    }

    PomNode build() {
      return new PomNode(name, String.join("", texts), line, children);
    }
  }
}
