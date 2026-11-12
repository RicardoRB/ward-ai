package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.config.CrapOptions;
import dev.ward.domain.gate.CoverageFiles;
import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.GateResult;
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
import java.util.stream.IntStream;

/** In-memory sources, fake report ports and the {@code crap} gate over them. */
final class CrapGateFixture {

  private static final int STRING_BUILDER_SIZE = 256;
  static final int FIVE = 5;
  static final int SIX = 6;
  static final int NINE = 9;

  /** The last line of the method that {@code method(SIX)} writes. */
  static final int SIX_END = 9;

  /** The score of an untested method of complexity six. */
  static final int SIX_SCORE = 42;

  static final Path ROOT = Path.of("/proj");
  static final String REPORT = "target/site/jacoco/jacoco.xml";
  static final String CART = "src/main/java/com/shop/Cart.java";
  static final String JACOCO_NAME = "com/shop/Cart.java";
  static final Instant BEFORE = Instant.parse("2026-01-01T00:00:00Z");
  static final Instant AFTER = Instant.parse("2026-01-02T00:00:00Z");

  private final Map<String, LineHits> hits = new HashMap<>();
  private final Map<String, Instant> modified = new HashMap<>();
  private final Set<String> files = new TreeSet<>();
  private final List<SourceUnit> units = new ArrayList<>();

  /** A class with one method of the given complexity, at lines 2 to the end. */
  static String method(final int complexity) {
    final StringBuilder body =
        new StringBuilder(STRING_BUILDER_SIZE).append("class Cart {\n  int total(int a) {\n");
    IntStream.range(1, complexity)
        .forEach(i -> body.append("    if (a > ").append(i).append(") { a++; }\n"));
    return body.append("    return a;\n  }\n}\n").toString();
  }

  LineHits.Entry entry(final int first, final int last, final Set<Integer> covered) {
    return new LineHits.Entry(
        LineHits.Kind.PACKAGE_FILE,
        JACOCO_NAME,
        new FileLines(new TreeSet<>(range(first, last)), new TreeSet<>(covered)));
  }

  static Set<Integer> range(final int first, final int last) {
    return new TreeSet<>(IntStream.rangeClosed(first, last).boxed().toList());
  }

  void report(final String path, final Instant when, final LineHits.Entry... entries) {
    files.add(path);
    modified.put(path, when);
    hits.put(path, new LineHits(ROOT.resolve(path), List.of(entries)));
  }

  /** Adds a report file that exists but that the reader cannot parse. */
  void unreadable(final String path) {
    files.add(path);
  }

  void source(final String path, final String text, final Instant when) {
    units.add(new SourceUnit(path, AnalysisLanguage.JAVA, text));
    modified.put(path, when);
  }

  GateResult run(final CrapOptions options) {
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

  GateResult run() {
    return run(CrapOptions.defaults());
  }
}
