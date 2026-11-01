package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.ProjectDirectories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the production line coverage of the JaCoCo XML reports that AGP writes for the debug unit
 * tests, summed over every module that has one.
 */
public final class AndroidJacocoCoverageReport implements CoverageReport {
  private static final Logger LOGGER =
      Logger.getLogger(AndroidJacocoCoverageReport.class.getName());
  // shortcut: AGP's debug unit-test JaCoCo XML is assumed at this path, not checked against a live
  // AGP build; confirm it before relying on the gate in a release.
  private static final String REPORT = "build/reports/coverage/test/debug/report.xml";
  private static final String COUNTER = "counter";
  private static final String LINE = "LINE";
  private static final String RUN_TESTS_FIRST =
      " Run the android-coverage check's command, which writes the report.";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public AndroidJacocoCoverageReport() {}

  @Override
  public LineCoverage read(final Project project, final String stdout) {
    final List<Path> reports =
        ProjectDirectories.below(project.root()).stream()
            .map(dir -> dir.resolve(REPORT))
            .filter(Files::isRegularFile)
            .toList();
    if (reports.isEmpty()) {
      return LineCoverage.unusable(
          "No JaCoCo XML report was found under build/reports." + RUN_TESTS_FIRST);
    }
    long covered = 0;
    long missed = 0;
    for (final Path report : reports) {
      final Optional<LineTotals> totals = lineTotals(report);
      if (totals.isEmpty()) {
        return LineCoverage.unusable(
            report.getFileName() + " cannot be parsed as a JaCoCo report.");
      }
      covered += totals.get().covered();
      missed += totals.get().missed();
    }
    if (covered + missed == 0) {
      return LineCoverage.unusable(
          "The JaCoCo report has no executable lines. Enable unit test coverage for the debug"
              + " build type.");
    }
    return LineCoverage.of(covered, covered + missed);
  }

  /** The covered and missed LINE counts of a report, or empty when the report is unreadable. */
  private static Optional<LineTotals> lineTotals(final Path report) {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      final DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(null);
      final Document document = builder.parse(report.toFile());
      return Optional.of(lineTotals(document.getDocumentElement()));
    } catch (ParserConfigurationException | SAXException | IOException | NumberFormatException e) {
      LOGGER.log(Level.INFO, "event=findings-parse-failed gate=android-coverage");
      return Optional.empty();
    }
  }

  /** The direct LINE counter of the report element, which holds the totals of every package. */
  private static LineTotals lineTotals(final Element report) {
    final NodeList children = report.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node child = children.item(i);
      if (child instanceof Element counter
          && COUNTER.equals(counter.getTagName())
          && LINE.equals(counter.getAttribute("type"))) {
        return new LineTotals(
            Long.parseLong(counter.getAttribute("covered")),
            Long.parseLong(counter.getAttribute("missed")));
      }
    }
    return new LineTotals(0, 0);
  }

  /** The covered and missed line counts of one report. */
  private record LineTotals(long covered, long missed) {}
}
