package dev.ward.infrastructure.finding.dotnet;

import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.FindingExtractionException;
import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.LineHits;
import dev.ward.infrastructure.finding.SafeXml;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads the per-line hits of the {@code class} elements of a Cobertura report, which Coverlet writes
 * for .NET. A class that is nested or compiler-generated names the same file as its owner, so the
 * entries are merged by file name downstream.
 */
public final class CoberturaXmlLineCoverage implements LineCoverageReport {
  private static final Logger LOGGER = Logger.getLogger(CoberturaXmlLineCoverage.class.getName());
  private static final String ROOT = "coverage";
  private static final String CLASS = "class";
  private static final String LINE = "line";
  private static final String NO_FILE = "";
  private static final Pattern CONDITIONS = Pattern.compile("\\((\\d+)/(\\d+)\\)");

  /** Creates the stateless reader that Dagger injects. */
  @Inject
  public CoberturaXmlLineCoverage() {}

  @Override
  public boolean accepts(final String head) {
    return head.contains("<" + ROOT + " ") || head.contains("<" + ROOT + ">");
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
                  report.getFileName() + " is not a Cobertura report: the root is " + element
              );
            }
            collector.first = false;
            collector.visit(element, reader);
          }
      );
    } catch (FindingExtractionException e) {
      throw new LineCoverageException(report.getFileName() + " is not a readable XML file", e);
    }
    collector.flush();
    LOGGER.log(Level.FINE, () -> "event=cobertura-read entries=" + collector.entries.size());
    return new LineHits(report, collector.entries, true);
  }

  /** Accumulates the entry of the {@code class} element being read. */
  private static final class Collector {
    private final List<LineHits.Entry> entries = new ArrayList<>();
    private final SortedSet<Integer> coverable = new TreeSet<>();
    private final SortedSet<Integer> covered = new TreeSet<>();
    private final SortedMap<Integer, Integer> branches = new TreeMap<>();
    private final SortedMap<Integer, Integer> coveredBranches = new TreeMap<>();
    private boolean first = true;
    private String file = NO_FILE;

    void visit(final String element, final XMLStreamReader reader) {
      if (CLASS.equals(element)) {
        flush();
        final String name = reader.getAttributeValue(null, "filename");
        file = name == null ? NO_FILE : name;
      } else if (LINE.equals(element)) {
        mark(reader);
      }
    }

    private void mark(final XMLStreamReader reader) {
      final Integer number = SafeXml.intAttribute(reader, "number");
      if (NO_FILE.equals(file) || number == null) {
        return;
      }
      coverable.add(number);
      final Integer hits = SafeXml.intAttribute(reader, "hits");
      if (hits != null && hits > 0) {
        covered.add(number);
      }
      final Matcher conditions =
          CONDITIONS.matcher(String.valueOf(reader.getAttributeValue(null, "condition-coverage")));
      if (conditions.find()) {
        coveredBranches.put(number, Integer.parseInt(conditions.group(1)));
        branches.put(number, Integer.parseInt(conditions.group(2)));
      }
    }

    void flush() {
      if (!NO_FILE.equals(file)) {
        entries.add(
            new LineHits.Entry(
                LineHits.Kind.PATH,
                file,
                new FileLines(coverable, covered, branches, coveredBranches)
            )
        );
      }
      file = NO_FILE;
      coverable.clear();
      covered.clear();
      branches.clear();
      coveredBranches.clear();
    }
  }
}
