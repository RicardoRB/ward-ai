package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles the fixtures with javac and Error Prone, or with NullAway when {@code nullaway} is set.
 * A fixture that does not compile is not comparable. NullAway names its errors by kind, not by
 * check, so each of its diagnostics counts for every NullAway id of the rule.
 */
final class ErrorProneParity implements ParityTool {

  private static final Pattern CHECK = Pattern.compile("^\\[(\\w+)\\]");
  private static final String NULLAWAY = "nullaway";
  private static final String NULLAWAY_CHECK = "NullAway";
  private static final String VIOLATING = "/violating";
  private static final String PASSING = "/passing";

  private final boolean nullaway;

  ErrorProneParity(final boolean nullaway) {
    this.nullaway = nullaway;
  }

  @Override
  public String name() {
    return nullaway ? NULLAWAY : "errorprone";
  }

  @Override
  public boolean reportsColumns() {
    return !nullaway;
  }

  @Override
  public ParityRun run(final ParityEnvironment environment, final List<ParityTarget> targets)
      throws IOException, InterruptedException {
    final List<String> fixtures = new ArrayList<>();
    final Set<String> checks = new TreeSet<>();
    for (final ParityTarget target : targets) {
      if (!target.idsOf(name()).isEmpty()) {
        fixtures.add(target.name() + VIOLATING);
        fixtures.add(target.name() + PASSING);
        checks.addAll(target.idsOf(name()));
      }
    }
    final ParityJavac.Result result =
        ParityJavac.compile(
            environment,
            nullaway ? NULLAWAY : "ep",
            fixtures,
            List.copyOf(checks),
            environment.workFolder("unused"));
    final ParityRun run = new ParityRun();
    result
        .failed()
        .forEach(fixture -> run.fixtureNotComparable(fixture, "the fixture does not compile"));
    for (final ParityJavac.Diagnostic diagnostic : result.diagnostics()) {
      final Matcher check = CHECK.matcher(diagnostic.message());
      if (check.find()) {
        record(run, targets, diagnostic, check.group(1));
      }
    }
    return run;
  }

  private void record(
      final ParityRun run,
      final List<ParityTarget> targets,
      final ParityJavac.Diagnostic diagnostic,
      final String check) {
    if (nullaway) {
      recordNullAway(run, targets, diagnostic, check);
    } else {
      run.add(diagnostic.fixture(), check, diagnostic.line(), diagnostic.column());
    }
  }

  private static void recordNullAway(
      final ParityRun run,
      final List<ParityTarget> targets,
      final ParityJavac.Diagnostic diagnostic,
      final String check) {
    if (!NULLAWAY_CHECK.equals(check)) {
      return;
    }
    final String rule = diagnostic.fixture().substring(0, diagnostic.fixture().indexOf('/'));
    targets.stream()
        .filter(target -> target.name().equals(rule))
        .flatMap(target -> target.idsOf(NULLAWAY).stream())
        .forEach(id -> run.add(diagnostic.fixture(), id, diagnostic.line(), 0));
  }
}
