package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compares the ward graph of the mixed Java/Kotlin fixture with its committed ArchUnit golden edges
 * on every build. The one golden edge that ward does not give is the dependency that only type
 * inference shows: {@code Inferred.kt} never names {@code Order}.
 */
class KotlinResolutionGraphTest {
  private static final String INFERRED =
      "api/src/main/kotlin/shop/api/Inferred.kt -> domain/src/main/kotlin/shop/domain/Order.kt";
  private static final Set<AnalysisLanguage> JVM =
      Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN);

  @TempDir Path kotlinOnly;

  @Test
  void wardEdgesEqualTheGoldenEdgesExceptTheInferredDependency() throws IOException {
    final Path project = KotlinFixtures.project();
    final Set<String> golden = KotlinFixtures.goldenEdges(project.resolve(KotlinFixtures.GOLDEN));
    final Set<String> expected = new TreeSet<>(golden);
    expected.remove(INFERRED);

    assertThat(golden).contains(INFERRED);
    assertThat(WardJavaEdges.of(project, JVM, Optional.empty())).isEqualTo(expected);
  }

  @Test
  void kotlinOnlyProjectGetsEdgesBetweenItsFiles() throws IOException {
    write(
        "src/main/kotlin/shop/domain/Price.kt",
        "package shop.domain\n\nclass Price(val cents: Long)\n");
    write(
        "src/main/kotlin/shop/service/Checkout.kt",
        "package shop.service\n\nimport shop.domain.Price\n\nclass Checkout(val price: Price)\n");

    assertThat(WardJavaEdges.of(kotlinOnly, Set.of(AnalysisLanguage.KOTLIN), Optional.empty()))
        .containsExactly(
            "src/main/kotlin/shop/service/Checkout.kt -> src/main/kotlin/shop/domain/Price.kt");
  }

  private void write(final String relative, final String text) throws IOException {
    final Path file = kotlinOnly.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
