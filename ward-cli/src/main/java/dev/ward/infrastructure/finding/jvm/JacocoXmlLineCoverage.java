package dev.ward.infrastructure.finding.jvm;

import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.FindingExtractionException;
import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.LineHits;
import dev.ward.infrastructure.finding.SafeXml;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamReader;

/** Reads the per-line instruction counts of the {@code sourcefile} elements of a JaCoCo report. */
public final class JacocoXmlLineCoverage implements LineCoverageReport {
  private static final Logger LOGGER = Logger.getLogger(JacocoXmlLineCoverage.class.getName());
  private static final String ROOT = "report";
  private static final String PACKAGE = "package";
  private static final String SOURCE_FILE = "sourcefile";
  private static final String LINE = "line";
  private static final String NAME = "name";
  private static final String XML = "<?xml";

  /** Creates the stateless reader that Dagger injects. */
  @Inject
  public JacocoXmlLineCoverage() {}

  @Override
  public boolean accepts(final String head) {
    final String start = head.stripLeading();
    return start.startsWith(XML) || start.startsWith("<" + ROOT);
  }

  @Override
  public LineHits read(final Path report) {
    final Collector collector = new Collector();
    try {
      SafeXml.read(
          report,
          reader -> {
            final String element = reader.getLocalName();
            if (collector.first && !ROOT.equals(element)) {
              throw new LineCoverageException(
                  report.getFileName() + " is not a JaCoCo report: the root is " + element);
            }
            collector.first = false;
            collector.visit(element, reader.getAttributeValue(null, NAME), reader);
          });
    } catch (FindingExtractionException e) {
      throw new LineCoverageException(report.getFileName() + " is not a readable XML file", e);
    }
    collector.flush();
    LOGGER.log(Level.FINE, () -> "event=jacoco-read entries=" + collector.entries.size());
    return new LineHits(report, collector.entries);
  }

  /** Accumulates the entry of the {@code sourcefile} element being read. */
  private static final class Collector {
    private final List<LineHits.Entry> entries = new ArrayList<>();
    private final TreeSet<Integer> coverable = new TreeSet<>();
    private final TreeSet<Integer> covered = new TreeSet<>();
    private boolean first = true;
    private String directory = "";
    private String file;

    void visit(final String element, final String name, final XMLStreamReader reader) {
      switch (element) {
        case PACKAGE -> enter(name);
        case SOURCE_FILE -> begin(name);
        case LINE -> mark(reader);
        default -> {
          // Other elements carry totals, not lines.
        }
      }
    }

    private void enter(final String name) {
      flush();
      directory = name == null ? "" : name;
    }

    private void begin(final String name) {
      flush();
      file = name;
    }

    private void mark(final XMLStreamReader reader) {
      final Integer number = SafeXml.intAttribute(reader, "nr");
      if (file == null || number == null) {
        return;
      }
      coverable.add(number);
      if (SafeXml.intAttribute(reader, "ci") != null) {
        covered.add(number);
      }
    }

    void flush() {
      if (file != null) {
        final String name = directory.isEmpty() ? file : directory + "/" + file;
        entries.add(
            new LineHits.Entry(
                LineHits.Kind.PACKAGE_FILE, name, new FileLines(coverable, covered)));
      }
      file = null;
      coverable.clear();
      covered.clear();
    }
  }
}
