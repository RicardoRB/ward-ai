package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.config.WardException;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
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

/** Runs the ward gate on a copy of the java-resolution fixture and reads the stored index. */
class JavaResolutionGateTest {
  private static final String FIXTURES = "src/test/resources/fixtures/";
  private static final String MAIN = "src/main/java/";
  private static final String TEST = "src/test/java/";

  @TempDir Path root;

  /** The stored reverse index in the codec's own shape: one entry per depended-on file. */
  public static final class StoredIndex {
    public List<StoredEntry> entries;
  }

  /** One depended-on file and the files that depend on it. */
  public static final class StoredEntry {
    public String path;
    public List<String> dependents;
  }

  @Test
  void theStoredReverseIndexHoldsTheJavaEdgesOfTheFixture() throws IOException {
    copyFixture("java-resolution");
    gate().execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT));

    final Map<String, List<String>> dependents = storedDependents();

    assertThat(dependents.get(MAIN + "shop/domain/Price.java"))
        .contains(
            MAIN + "shop/service/Checkout.java",
            MAIN + "shop/service/Pricing.java",
            MAIN + "shop/domain/Order.java",
            MAIN + "shop/domain/Prices.java");
    assertThat(dependents.get(MAIN + "shop/domain/Discount.java"))
        .containsExactlyInAnyOrder(
            MAIN + "shop/service/Pricing.java", MAIN + "shop/legacy/Coupon.java");
    assertThat(dependents.get(MAIN + "shop/domain/Order.java"))
        .contains(
            MAIN + "shop/service/Cart.java",
            MAIN + "shop/service/Checkout.java",
            TEST + "shop/service/CheckoutTest.java");
    assertThat(dependents.get(MAIN + "shop/domain/Prices.java"))
        .containsExactly(MAIN + "shop/service/Checkout.java");
    assertThat(dependents).doesNotContainKey(MAIN + "shop/legacy/Discount.java");
  }

  @Test
  void theClasspathAddsTheInferredEdgeToTheStoredIndex() throws IOException {
    copyFixture("java-resolution-inferred");
    final Path classpath = root.resolve("classpath.txt");
    Files.writeString(classpath, System.getProperty("java.class.path"));

    gate("classpath.txt")
        .execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE));

    assertThat(storedDependents().get(MAIN + "shop/domain/Order.java"))
        .contains(MAIN + "shop/service/Report.java");
  }

  @Test
  void withoutTheClasspathTheInferredEdgeIsAbsent() throws IOException {
    copyFixture("java-resolution-inferred");

    gate(null).execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE));

    assertThat(storedDependents().get(MAIN + "shop/domain/Order.java"))
        .doesNotContain(MAIN + "shop/service/Report.java");
  }

  @Test
  void anUnreadableClasspathFileIsAConfigurationErrorNamingTheKeyAndTheFile() throws IOException {
    copyFixture("java-resolution-inferred");

    assertThatThrownBy(
            () ->
                gate("missing/classpath.txt")
                    .execute(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("java-classpath")
        .hasMessageContaining("missing/classpath.txt");
  }

  private AnalysisGate gate() {
    return gate(null);
  }

  private AnalysisGate gate(final String javaClasspath) {
    return new AnalysisGate(
        "ward",
        List.of(),
        new AnalysisPorts(
            () -> new AnalysisRuleLoad(List.of(), List.of()),
            new MetricMatcher(),
            new FileSystemSourceUnits(new ProcessCommandExecutor()),
            List.of(
                new FrontendSupport(
                    AnalysisLanguage.JAVA,
                    new AntlrLanguageFrontend(),
                    Set.of(RuleCapability.SYNTAX))),
            projectRoot -> new FileAnalysisCache(projectRoot.resolve(".ward/cache/analysis")),
            AnalysisCacheFixtures.codec(),
            "test",
            new JvmDependencyAdapters(),
            (unused, classpath) -> Map.of()),
        javaClasspath);
  }

  /** Maps each depended-on file to the files that depend on it, as the gate stored them. */
  private Map<String, List<String>> storedDependents() {
    final String json =
        new FileAnalysisCache(root.resolve(".ward/cache/analysis"))
            .read(AnalysisCacheKey.index())
            .orElseThrow();
    final StoredIndex index = new ForyJsonCodec().fromJson(json, StoredIndex.class);
    return index.entries.stream()
        .collect(Collectors.toMap(entry -> entry.path, entry -> entry.dependents));
  }

  private void copyFixture(final String fixture) throws IOException {
    final Path sourceRoot = Path.of(FIXTURES, fixture);
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      for (final Path source : files.filter(Files::isRegularFile).toList()) {
        final Path target = root.resolve(sourceRoot.relativize(source).toString());
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
      }
    }
  }
}
