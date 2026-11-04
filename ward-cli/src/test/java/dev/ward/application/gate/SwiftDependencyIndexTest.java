package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.swift.SwiftDependencyAdapters;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SwiftDependencyIndexTest {

  private static final Path SWIFT_RESOLUTION =
      Path.of("src/test/resources/fixtures/swift-resolution");
  private static final String VERSION = "0.6.0";

  @Test
  void aCleanRunOfASwiftPackageStoresTheCrossTargetEdgesInTheReverseIndex() throws IOException {
    final Map<String, String> entries = new HashMap<>();
    final UnitResultCache results =
        new UnitResultCache(
            new RuleEngine(BundledRuleMatchers.create()),
            AnalysisCacheFixtures.codec(),
            VERSION,
            new SwiftDependencyAdapters().forProject(SWIFT_RESOLUTION, units(SWIFT_RESOLUTION)));

    results.run(
        AnalysisCacheFixtures.memory(entries),
        units(SWIFT_RESOLUTION),
        List.of(
            new FrontendSupport(
                AnalysisLanguage.SWIFT,
                new AntlrLanguageFrontend(),
                Set.of(RuleCapability.SYNTAX))),
        List.of());

    final String index = entries.get(AnalysisCacheKey.index());
    assertThat(index)
        .contains("Tests/ShopTests/ShopTests.swift")
        .contains("Modules/Shop/Cart.swift")
        .contains("Sources/Domain/Order.swift");
  }

  private static List<SourceUnit> units(final Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      final List<SourceUnit> units = new ArrayList<>();
      for (final Path file : files.filter(f -> f.toString().endsWith(".swift")).sorted().toList()) {
        final String path = root.relativize(file).toString().replace('\\', '/');
        units.add(new SourceUnit(path, AnalysisLanguage.SWIFT, Files.readString(file)));
      }
      return units;
    }
  }
}
