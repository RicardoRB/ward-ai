package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.JavaDependencyAdapters;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs {@code ward:architecture/layers} through the ward gate on the java-resolution fixture. */
class LayerGateTest {
  private static final String FIXTURE = "src/test/resources/fixtures/java-resolution";
  private static final String MAIN = "src/main/java/";
  private static final String TEST = "src/test/java/";
  private static final String COUPON = MAIN + "shop/legacy/Coupon.java";
  private static final int COUPON_IMPORT_LINE = 3;

  @TempDir Path root;

  @Test
  void reportsTheForbiddenDependencyOnlyInTheFileThatCreatesIt() throws IOException {
    copyFixture();

    final List<Finding> findings = run(new LayerBoundary(List.of("legacy"), List.of("domain")));

    assertThat(findings).extracting(Finding::file).containsExactly(COUPON);
    assertThat(findings.getFirst().ruleId()).isEqualTo("ward:architecture/layers");
    assertThat(findings.getFirst().message())
        .startsWith("Layer legacy must not depend on domain: ")
        .endsWith("shop/domain/Discount.java");
    assertThat(findings.getFirst().line()).isEqualTo(COUPON_IMPORT_LINE);
  }

  @Test
  void aJavaTestSourceIsNotChecked() throws IOException {
    copyFixture();
    final Path testSource = root.resolve(TEST + "shop/legacy/CouponTest.java");
    Files.createDirectories(testSource.getParent());
    Files.writeString(
        testSource,
        "package shop.legacy;\n\nimport shop.domain.Discount;\n\nclass CouponTest {}\n");

    final List<Finding> findings = run(new LayerBoundary(List.of("legacy"), List.of("domain")));

    assertThat(findings).extracting(Finding::file).containsExactly(COUPON);
  }

  @Test
  void theOppositeDirectionIsAllowed() throws IOException {
    copyFixture();

    assertThat(run(new LayerBoundary(List.of("domain"), List.of("legacy")))).isEmpty();
  }

  private List<Finding> run(final LayerBoundary boundary) {
    return LayerPorts.findings(
        new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
        boundary,
        new ProjectDependencyAdapters(List.of(new JavaDependencyAdapters())));
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
