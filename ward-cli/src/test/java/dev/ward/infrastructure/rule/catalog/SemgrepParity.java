package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs Semgrep with the {@code p/java} registry pack over a copy of the fixtures, since Semgrep
 * skips folders named {@code test}. The registry changes over time, so its rows are reported but
 * the committed report does not pin them.
 */
final class SemgrepParity implements ParityTool {

  private static final Pattern FINDING =
      Pattern.compile("^(.+?\\.java):(\\d+):(\\d+):[A-Z]:([\\w.-]+):");
  private static final int LINE_GROUP = 2;

  @Override
  public String name() {
    return "semgrep";
  }

  @Override
  public boolean reportsColumns() {
    return true;
  }

  @Override
  public ParityRun run(final ParityEnvironment environment, final List<ParityTarget> targets)
      throws IOException, InterruptedException {
    final ParityRun run = new ParityRun();
    final Path folder = environment.workFolder("semgrep");
    final Path stage = Files.createDirectories(folder.resolve("stage"));
    try (Stream<Path> files = Files.walk(environment.fixtures())) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        if (environment.fixtureOf(file).isPresent()) {
          final Path copy = stage.resolve(environment.fixtures().relativize(file));
          Files.createDirectories(copy.getParent());
          Files.copy(file, copy);
        }
      }
    }
    final Path report = folder.resolve("report.vim");
    ParityEnvironment.run(
        List.of(
            environment.semgrep().orElseThrow(),
            "scan",
            "--config",
            "p/java",
            "--metrics",
            "off",
            "--quiet",
            "--no-git-ignore",
            "--disable-version-check",
            "--vim",
            "-o",
            report.toString(),
            stage.toString()),
        folder.resolve("stdout.txt"),
        folder.resolve("stderr.txt"));
    for (final String line : Files.readAllLines(report, StandardCharsets.UTF_8)) {
      final Matcher finding = FINDING.matcher(line);
      if (finding.find()) {
        final Path file = Path.of(finding.group(1));
        final String fixture =
            file.getParent().getFileName()
                + "/"
                + file.getFileName().toString().replace(".java", "");
        run.add(
            fixture,
            finding.group(LINE_GROUP + 2),
            Integer.parseInt(finding.group(LINE_GROUP)),
            Integer.parseInt(finding.group(LINE_GROUP + 1)));
      }
    }
    return run;
  }
}
