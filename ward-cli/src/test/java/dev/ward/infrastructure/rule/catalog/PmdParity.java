package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.w3c.dom.Element;

/** Runs PMD once over every fixture with all of its Java rule categories. */
final class PmdParity implements ParityTool {

  private static final String CATEGORIES =
      String.join(
          ",",
          List.of(
                  "bestpractices",
                  "codestyle",
                  "design",
                  "documentation",
                  "errorprone",
                  "multithreading",
                  "performance",
                  "security")
              .stream()
              .map(category -> "category/java/" + category + ".xml")
              .toList());

  @Override
  public String name() {
    return "pmd";
  }

  @Override
  public boolean reportsColumns() {
    return true;
  }

  @Override
  public ParityRun run(final ParityEnvironment environment, final List<ParityTarget> targets)
      throws IOException, InterruptedException {
    final Path folder = environment.workFolder("pmd");
    final Path report = folder.resolve("report.xml");
    ParityEnvironment.run(
        List.of(
            ParityEnvironment.java(),
            "-cp",
            environment.classpath("pmd"),
            "net.sourceforge.pmd.cli.PmdCli",
            "check",
            "--no-cache",
            "--no-progress",
            "-d",
            environment.fixtures().toString(),
            "-R",
            CATEGORIES,
            "-f",
            "xml",
            "-r",
            report.toString(),
            "--no-fail-on-violation",
            "--no-fail-on-error"),
        folder.resolve("stdout.txt"),
        folder.resolve("stderr.txt"));
    final ParityRun run = new ParityRun();
    for (final Element file :
        ParityXml.elements(ParityXml.parse(report).getDocumentElement(), "file")) {
      final String fixture = environment.fixtureOf(Path.of(file.getAttribute("name"))).orElse("");
      for (final Element violation : ParityXml.elements(file, "violation")) {
        run.add(
            fixture,
            violation.getAttribute("rule"),
            ParityXml.number(violation, "beginline"),
            ParityXml.number(violation, "begincolumn"));
      }
    }
    return run;
  }
}
