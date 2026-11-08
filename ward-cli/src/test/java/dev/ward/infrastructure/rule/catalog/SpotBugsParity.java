package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * Compiles the fixtures to class files, each in its own package {@code parity.pN}, and runs
 * SpotBugs once over all of them at the maximum effort and the lowest confidence. SpotBugs reports
 * lines only. A fixture that does not compile is not comparable.
 */
final class SpotBugsParity implements ParityTool {

  private static final Pattern PACKAGE = Pattern.compile("^parity\\.p(\\d+)\\.");

  @Override
  public String name() {
    return "spotbugs";
  }

  @Override
  public boolean reportsColumns() {
    return false;
  }

  @Override
  public ParityRun run(final ParityEnvironment environment, final List<ParityTarget> targets)
      throws IOException, InterruptedException {
    final List<String> fixtures = new ArrayList<>();
    for (final ParityTarget target : targets) {
      if (!target.idsOf(name()).isEmpty()) {
        fixtures.add(target.name() + "/violating");
        fixtures.add(target.name() + "/passing");
      }
    }
    final Path classes = environment.workFolder("spotbugs-classes");
    final ParityJavac.Result compiled =
        ParityJavac.compile(environment, "classes", fixtures, List.of(), classes);
    final ParityRun run = new ParityRun();
    compiled
        .failed()
        .forEach(fixture -> run.fixtureNotComparable(fixture, "the fixture does not compile"));
    final Path folder = environment.workFolder("spotbugs");
    final Path report = folder.resolve("report.xml");
    ParityEnvironment.run(
        List.of(
            ParityEnvironment.java(),
            "-Xmx3g",
            "-cp",
            environment.classpath("spotbugs"),
            "edu.umd.cs.findbugs.FindBugs2",
            "-xml:withMessages",
            "-effort:max",
            "-low",
            "-output",
            report.toString(),
            "-auxclasspath",
            environment.classpath("errorprone"),
            classes.toString()),
        folder.resolve("stdout.txt"),
        folder.resolve("stderr.txt"));
    if (Files.exists(report)) {
      read(report, fixtures, run);
    }
    return run;
  }

  private static void read(final Path report, final List<String> fixtures, final ParityRun run)
      throws IOException {
    for (final Element bug :
        ParityXml.elements(ParityXml.parse(report).getDocumentElement(), "BugInstance")) {
      final Element line = primaryLine(bug);
      if (line == null) {
        continue;
      }
      final Matcher owner = PACKAGE.matcher(line.getAttribute("classname"));
      if (owner.find()) {
        run.add(
            fixtures.get(Integer.parseInt(owner.group(1))),
            bug.getAttribute("type"),
            ParityXml.number(line, "start"),
            0);
      }
    }
  }

  private static Element primaryLine(final Element bug) {
    final Element[] lines = ParityXml.elements(bug, "SourceLine");
    for (final Element line : lines) {
      if ("true".equals(line.getAttribute("primary")) && line.hasAttribute("start")) {
        return line;
      }
    }
    return lines.length > 0 && lines[0].hasAttribute("start") ? lines[0] : null;
  }
}
