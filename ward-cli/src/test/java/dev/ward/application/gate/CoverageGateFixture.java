package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.domain.config.CoverageOptions;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineHits;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

/** In-memory reports and files, and the {@code coverage} gate over them. */
final class CoverageGateFixture {
  static final Path ROOT = Path.of("/proj");
  static final String REPORT = "target/site/jacoco/jacoco.xml";
  static final Instant OLD = Instant.parse("2020-01-01T00:00:00Z");
  static final Instant NEW = Instant.parse("2999-01-01T00:00:00Z");

  private final Map<String, LineHits> hits = new HashMap<>();
  private final Map<String, Instant> modified = new HashMap<>();
  private final Set<String> files = new TreeSet<>();
  private final List<SourceUnit> units = new ArrayList<>();
  private final List<Path> modules = new ArrayList<>(List.of(ROOT));
  private boolean commandRan;

  /**
   * A file of {@code lines} coverable lines, {@code covered} of them covered, and branches on line
   * 1.
   */
  static LineHits.Entry entry(
      final String name,
      final int lines,
      final int covered,
      final int branches,
      final int coveredBranches) {
    final SortedMap<Integer, Integer> all = new TreeMap<>();
    final SortedMap<Integer, Integer> taken = new TreeMap<>();
    if (branches > 0) {
      all.put(1, branches);
      taken.put(1, coveredBranches);
    }
    return new LineHits.Entry(
        LineHits.Kind.PACKAGE_FILE,
        name,
        new FileLines(range(1, lines), range(1, covered), all, taken));
  }

  /** The lines {@code first} to {@code last}, or none when {@code last} is below {@code first}. */
  static SortedSet<Integer> range(final int first, final int last) {
    return new TreeSet<>(IntStream.rangeClosed(first, last).boxed().toList());
  }

  void module(final String directory) {
    modules.add(ROOT.resolve(directory));
  }

  void report(
      final String path,
      final Instant when,
      final boolean recordsBranches,
      final LineHits.Entry... entries) {
    files.add(path);
    modified.put(path, when);
    hits.put(path, new LineHits(ROOT.resolve(path), List.of(entries), recordsBranches));
  }

  /** Adds a report file that exists but that the reader cannot parse. */
  void unreadable(final String path) {
    files.add(path);
    modified.put(path, NEW);
  }

  void source(final String path, final Instant when) {
    units.add(new SourceUnit(path, AnalysisLanguage.JAVA, "class A {}\n"));
    modified.put(path, when);
  }

  boolean commandRan() {
    return commandRan;
  }

  /** Runs the gate; {@code exit} is the command's exit code, or {@code null} for no command. */
  GateResult run(final int minimum, final Integer minimumBranches, final Integer exit) {
    return runWith(minimum, new CoverageOptions(List.of(), minimumBranches), exit);
  }

  GateResult runWith(final int minimum, final CoverageOptions options, final Integer exit) {
    final QualityGate command =
        exit == null
            ? null
            : new QualityGate() {
              @Override
              public String name() {
                return "coverage";
              }

              @Override
              public GateResult execute(final Project project) {
                commandRan = true;
                return GateResult.builder(
                        "coverage", exit == 0 ? GateStatus.PASSED : GateStatus.FAILED)
                    .exitCode(exit)
                    .duration(Duration.ZERO)
                    .build();
              }
            };
    return new CoverageReportGate(
            check(minimum, options),
            command,
            CoverageGateFakes.ports(hits, modified, files, modules),
            CoverageGateFakes.analysis(units))
        .execute(new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
  }

  private static GateConfiguration check(final int minimum, final CoverageOptions options) {
    return new GateConfiguration(
        "coverage",
        "coverage",
        true,
        List.of(),
        Set.of(),
        null,
        null,
        null,
        minimum,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        options,
        null);
  }
}
