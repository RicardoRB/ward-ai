package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.gate.CoverageFiles;
import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.LineHits;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The report reader, file facts and analysis ports that the {@code coverage} gate tests fake. */
final class CoverageGateFakes {
  private CoverageGateFakes() {}

  /** Ports that answer from the given reports, modification times, files and module directories. */
  static CrapPorts ports(
      final Map<String, LineHits> hits,
      final Map<String, Instant> modified,
      final Set<String> files,
      final List<Path> modules) {
    final LineCoverageReport reader =
        new LineCoverageReport() {
          @Override
          public boolean accepts(final String head) {
            return true;
          }

          @Override
          public LineHits read(final Path report) {
            final LineHits found = hits.get(CoverageGateFixture.ROOT.relativize(report).toString());
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
            return List.copyOf(modules);
          }

          @Override
          public boolean isFile(final Path file) {
            return files.contains(CoverageGateFixture.ROOT.relativize(file).toString());
          }

          @Override
          public Instant modified(final Path file) {
            return modified.getOrDefault(
                CoverageGateFixture.ROOT.relativize(file).toString(), Instant.MIN);
          }
        };
    return new CrapPorts(reader, fake);
  }

  /** Analysis ports that read the given sources. */
  static AnalysisPorts analysis(final List<SourceUnit> units) {
    return new AnalysisPorts(
        new ClasspathRuleSource(),
        BundledRuleMatchers.create(),
        (root, languages) -> units,
        List.of(
            new FrontendSupport(
                AnalysisLanguage.JAVA, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX))),
        root -> AnalysisCacheFixtures.NO_CACHE,
        AnalysisCacheFixtures.codec(),
        "test",
        DependencyAdapters.none(),
        (unused, classpath) -> Map.of());
  }
}
