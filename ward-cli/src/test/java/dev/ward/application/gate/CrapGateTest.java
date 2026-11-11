package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.config.CrapOptions;
import dev.ward.domain.gate.CoverageFiles;
import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.LineHits;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The {@code crap} gate over in-memory sources and fake report ports. */
class CrapGateTest {

  private static final int FIVE = 5;
  private static final int SIX = 6;
  private static final int NINE = 9;

  /** The last line of the method that {@code method(SIX)} writes. */
  private static final int SIX_END = 9;

  /** The score of an untested method of complexity six. */
  private static final int SIX_SCORE = 42;

  private static final Path ROOT = Path.of("/proj");
  private static final String REPORT = "target/site/jacoco/jacoco.xml";
  private static final String CART = "src/main/java/com/shop/Cart.java";
  private static final String JACOCO_NAME = "com/shop/Cart.java";
  private static final Instant BEFORE = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant AFTER = Instant.parse("2026-01-02T00:00:00Z");

  /** A class with one method of the given complexity, at lines 2 to the end. */
  private static String method(final int complexity) {
    final StringBuilder body = new StringBuilder("class Cart {\n  int total(int a) {\n");
    IntStream.range(1, complexity)
        .forEach(i -> body.append("    if (a > ").append(i).append(") { a++; }\n"));
    return body.append("    return a;\n  }\n}\n").toString();
  }

  private final Map<String, LineHits> hits = new HashMap<>();
  private final Map<String, Instant> modified = new HashMap<>();
  private final Set<String> files = new TreeSet<>();
  private final List<SourceUnit> units = new ArrayList<>();

  private LineHits.Entry entry(final int first, final int last, final Set<Integer> covered) {
    return new LineHits.Entry(
        LineHits.Kind.PACKAGE_FILE,
        JACOCO_NAME,
        new FileLines(new TreeSet<>(range(first, last)), new TreeSet<>(covered)));
  }

  private static Set<Integer> range(final int first, final int last) {
    return new TreeSet<>(IntStream.rangeClosed(first, last).boxed().toList());
  }

  private void report(final String path, final Instant when, final LineHits.Entry... entries) {
    files.add(path);
    modified.put(path, when);
    hits.put(path, new LineHits(ROOT.resolve(path), List.of(entries)));
  }

  private void source(final String path, final String text, final Instant when) {
    units.add(new SourceUnit(path, AnalysisLanguage.JAVA, text));
    modified.put(path, when);
  }

  private GateResult run(final CrapOptions options) {
    final LineCoverageReport reader =
        new LineCoverageReport() {
          @Override
          public boolean accepts(final String head) {
            return true;
          }

          @Override
          public LineHits read(final Path report) {
            final LineHits found = hits.get(ROOT.relativize(report).toString());
            if (found == null) {
              throw new LineCoverageException("bad " + report.getFileName());
            }
            return found;
          }
        };
    final CoverageFiles fake =
        new CoverageFiles() {
          @Override
          public List<Path> moduleDirectories(final Path root) {
            return List.of(root);
          }

          @Override
          public boolean isFile(final Path file) {
            return files.contains(ROOT.relativize(file).toString());
          }

          @Override
          public Instant modified(final Path file) {
            return modified.getOrDefault(ROOT.relativize(file).toString(), Instant.MIN);
          }
        };
    final AnalysisPorts ports =
        new AnalysisPorts(
            new ClasspathRuleSource(),
            BundledRuleMatchers.create(),
            (root, languages) -> units,
            List.of(
                new FrontendSupport(
                    AnalysisLanguage.JAVA,
                    new AntlrLanguageFrontend(),
                    Set.of(RuleCapability.SYNTAX))),
            root -> AnalysisCacheFixtures.NO_CACHE,
            AnalysisCacheFixtures.codec(),
            "test",
            DependencyAdapters.none(),
            (unused, classpath) -> Map.of());
    return new CrapGate("crap", options, ports, new CrapPorts(reader, fake))
        .execute(new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
  }

  private GateResult run() {
    return run(CrapOptions.defaults());
  }

  @Test
  void anUntestedComplexMethodFailsWithOneFindingAtItsLocation() {
    source(CART, method(SIX), BEFORE);
    report(REPORT, AFTER, entry(2, SIX_END, Set.of()));

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo("crap");
              assertThat(finding.file()).isEqualTo(CART);
              assertThat(finding.line()).isEqualTo(2);
              assertThat(finding.message()).contains("42", "30", "complexity 6", "coverage 0%");
            });
    assertThat(result.summary()).contains("1 function(s) scored", "1 above 30");
  }

  @Test
  void aTestedMethodPassesAndTheSummaryCountsFunctions() {
    source(CART, method(SIX), BEFORE);
    report(REPORT, AFTER, entry(2, SIX_END, range(2, SIX_END)));

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("1 function(s) scored", "0 above");
  }

  @Test
  void aCustomLimitIsUsed() {
    source(CART, method(SIX), BEFORE);
    report(REPORT, AFTER, entry(2, SIX_END, Set.of()));

    assertThat(run(new CrapOptions(List.of(), SIX_SCORE)).status()).isEqualTo(GateStatus.PASSED);
    assertThat(run(new CrapOptions(List.of(), SIX_SCORE - 1)).status())
        .isEqualTo(GateStatus.FAILED);
  }

  @Test
  void aFileTheReportsDoNotNameIsScoredFromComplexityAlone() {
    source(CART, method(SIX), BEFORE);
    source("src/main/java/com/shop/Other.java", method(NINE).replace("Cart", "Other"), BEFORE);
    report(REPORT, AFTER, entry(2, SIX_END, range(2, SIX_END)));

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("2 function(s) scored");
  }

  @Test
  void aMissingConventionalReportIsAnExecutionErrorNamingIt() {
    source(CART, method(2), BEFORE);

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(REPORT, "coverage check must run first");
  }

  @Test
  void aMissingConfiguredReportIsAnExecutionErrorNamingIt() {
    source(CART, method(2), BEFORE);

    final GateResult result = run(new CrapOptions(List.of("coverage/lcov.info"), 30));

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("coverage/lcov.info");
  }

  @Test
  void anUnparseableReportIsAnExecutionErrorAndNoScoreIsUsed() {
    source(CART, method(NINE), BEFORE);
    files.add(REPORT);

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("jacoco.xml", "cannot be parsed");
    assertThat(result.findings().items()).isEmpty();
  }

  @Test
  void aReportThatNamesNoProjectSourceFileIsAnExecutionError() {
    source(CART, method(2), BEFORE);
    report(
        REPORT,
        AFTER,
        new LineHits.Entry(
            LineHits.Kind.PACKAGE_FILE,
            "org/other/Thing.java",
            new FileLines(new TreeSet<>(Set.of(1)), new TreeSet<>())));

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("name no project source file");
  }

  @Test
  void aReportOlderThanASourceItNamesIsAnExecutionError() {
    source(CART, method(2), AFTER);
    report(REPORT, BEFORE, entry(2, FIVE, range(2, FIVE)));

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(REPORT, CART, "older");
  }

  @Test
  void aNamedFileThatDoesNotParseIsAnExecutionErrorButAnUnnamedOneIsLeftToTheWardGate() {
    source(CART, "class Cart { int total( {{{ \n", BEFORE);
    source("src/main/java/com/shop/Broken.java", "class Broken {{{ \n", BEFORE);
    report(REPORT, AFTER, entry(1, 1, Set.of()));

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(CART).doesNotContain("Broken");
  }

  @Test
  void logsTheReportsReadAndTheFunctionsScored() {
    final List<String> messages = new ArrayList<>();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord record) {
            messages.add(record.getLevel() + " " + record.getMessage());
          }

          @Override
          public void flush() {
            // Nothing is buffered.
          }

          @Override
          public void close() {
            // Nothing is held open.
          }
        };
    final Logger reports = Logger.getLogger(CrapReports.class.getName());
    final Logger gate = Logger.getLogger(CrapGate.class.getName());
    reports.addHandler(handler);
    gate.addHandler(handler);
    source(CART, method(SIX), BEFORE);
    report(REPORT, AFTER, entry(2, SIX_END, Set.of()));
    try {
      run();
    } finally {
      reports.removeHandler(handler);
      gate.removeHandler(handler);
    }

    assertThat(messages)
        .contains(Level.INFO + " event=crap-reports gate=crap reports=1")
        .anyMatch(
            message ->
                message.startsWith(
                    Level.INFO + " event=crap-scored gate=crap functions=1 above=1"));
  }
}
