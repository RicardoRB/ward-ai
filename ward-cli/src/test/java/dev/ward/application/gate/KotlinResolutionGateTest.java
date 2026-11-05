package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.rule.FileAnalysisCache;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the ward gate on a copy of the mixed Java/Kotlin fixture and reads the stored index. */
class KotlinResolutionGateTest {
  private static final String FIXTURE = "src/test/resources/fixtures/kotlin-resolution";
  private static final String PRICE = "domain/src/main/kotlin/shop/domain/Price.kt";
  private static final String COUPON = "domain/src/main/java/shop/domain/Coupon.java";
  private static final String CHECKOUT = "api/src/main/kotlin/shop/api/Checkout.kt";
  private static final String REPORT = "domain/src/main/java/shop/legacy/Report.java";
  private static final String LEGACY_BRIDGE = "domain/src/main/java/shop/legacy/LegacyBridge.kt";
  private static final String CHECKOUT_TEST = "api/src/test/kotlin/shop/api/CheckoutTest.kt";

  @TempDir Path root;

  @Test
  void theStoredReverseIndexHoldsTheEdgesOfBothLanguagesInBothDirections() throws IOException {
    copyFixture();
    new AnalysisGate("ward", List.of(), ports())
        .execute(new Project(root, Language.KOTLIN, BuildTool.GRADLE, Framework.NONE));

    final Map<String, List<String>> dependents = storedDependents();

    assertThat(dependents.get(PRICE)).contains(CHECKOUT, COUPON, REPORT);
    assertThat(dependents.get(COUPON)).contains(CHECKOUT, LEGACY_BRIDGE);
    assertThat(dependents.get(CHECKOUT)).contains(CHECKOUT_TEST);
  }

  private AnalysisPorts ports() {
    return new AnalysisPorts(
        () -> new AnalysisRuleLoad(List.of(), List.of()),
        new MetricMatcher(),
        new FileSystemSourceUnits(),
        List.of(
            new FrontendSupport(
                AnalysisLanguage.JAVA, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX)),
            new FrontendSupport(
                AnalysisLanguage.KOTLIN,
                new AntlrLanguageFrontend(),
                Set.of(RuleCapability.SYNTAX))),
        projectRoot -> new FileAnalysisCache(projectRoot.resolve(".ward/cache/analysis")),
        AnalysisCacheFixtures.codec(),
        "test",
        new JvmDependencyAdapters(),
        unused -> Map.of());
  }

  /** Maps each depended-on file to the files that depend on it, as the gate stored them. */
  private Map<String, List<String>> storedDependents() {
    final String json =
        new FileAnalysisCache(root.resolve(".ward/cache/analysis"))
            .read(AnalysisCacheKey.index())
            .orElseThrow();
    final JavaResolutionGateTest.StoredIndex index =
        new ForyJsonCodec().fromJson(json, JavaResolutionGateTest.StoredIndex.class);
    return index.entries.stream()
        .collect(Collectors.toMap(entry -> entry.path, entry -> entry.dependents));
  }

  private void copyFixture() throws IOException {
    final Path sourceRoot = Path.of(FIXTURE);
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      for (final Path source : files.filter(Files::isRegularFile).toList()) {
        final Path target = root.resolve(sourceRoot.relativize(source).toString());
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
      }
    }
  }
}
