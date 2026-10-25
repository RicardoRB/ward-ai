package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.FindingExtractionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** StAX reading with DTDs and external entities disabled, so reports cannot trigger XXE. */
final class SafeXml {
  private SafeXml() {}

  /** Receives each start element; the reader is positioned on that element. */
  @FunctionalInterface
  interface ElementVisitor {
    void startElement(XMLStreamReader reader) throws XMLStreamException;
  }

  static void read(final Path file, final ElementVisitor visitor) {
    try (InputStream input = Files.newInputStream(file)) {
      final XMLStreamReader reader = factory().createXMLStreamReader(input);
      visit(reader, visitor);
    } catch (XMLStreamException exception) {
      throw new MalformedReportException(file, exception);
    } catch (IOException exception) {
      throw new FindingExtractionException("Cannot read report", exception);
    }
  }

  static void read(final String xml, final ElementVisitor visitor) {
    try {
      visit(factory().createXMLStreamReader(new StringReader(xml)), visitor);
    } catch (XMLStreamException exception) {
      throw new MalformedReportException(exception);
    }
  }

  private static XMLInputFactory factory() {
    final XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
    return factory;
  }

  private static void visit(final XMLStreamReader reader, final ElementVisitor visitor)
      throws XMLStreamException {
    try {
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamReader.START_ELEMENT) {
          visitor.startElement(reader);
        }
      }
    } finally {
      reader.close();
    }
  }

  static Integer intAttribute(final XMLStreamReader reader, final String name) {
    final String value = reader.getAttributeValue(null, name);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      final int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
