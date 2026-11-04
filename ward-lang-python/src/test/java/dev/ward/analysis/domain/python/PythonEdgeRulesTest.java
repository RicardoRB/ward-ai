package dev.ward.analysis.domain.python;

import static dev.ward.analysis.domain.python.ResolverFixtures.edgeKeys;
import static dev.ward.analysis.domain.python.ResolverFixtures.file;
import static dev.ward.analysis.domain.python.ResolverFixtures.resolve;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks when the Python resolver gives no edge, gives an edge to a test file, and is stable. */
final class PythonEdgeRulesTest {

  private static final PythonProjectLayout SRC = new PythonProjectLayout(List.of("src/"));

  @Test
  void givesNoEdgeForARelativeImportAboveTheTopLevelPackage() {
    final List<DependencyEdge> edges =
        resolve(
            SRC,
            file("src/shop/price.py"),
            file("src/shop/controller/clean.py", "from:3::price"),
            file("src/shop/__init__.py", "from:2::price"));

    assertThat(edges).isEmpty();
  }

  @Test
  void givesNoEdgeForAModuleNoFileProvides() {
    assertThat(resolve(SRC, file("src/shop/app.py", "from:0:shop.missing:Thing"))).isEmpty();
  }

  @Test
  void addsEdgesFromAFileOutsideAnyRootToTheModulesItImports() {
    final List<DependencyEdge> edges =
        resolve(
            SRC,
            file("src/shop/price.py"),
            file("tests/test_price.py", "abs:shop.price", "from:1::price"));

    assertThat(edgeKeys(edges)).containsExactly("tests/test_price.py -> src/shop/price.py");
  }

  @Test
  void givesNoEdgeFromAFileOutsideAnyRootThroughARelativeImport() {
    final List<DependencyEdge> edges =
        resolve(SRC, file("src/shop/price.py"), file("scripts/run.py", "from:1::price"));

    assertThat(edges).isEmpty();
  }

  @Test
  void givesNoSelfEdge() {
    assertThat(resolve(SRC, file("src/shop/price.py", "abs:shop.price"))).isEmpty();
  }

  @Test
  void resolvesTheSameGraphForAnyOrderOfTheSummaries() {
    final List<FileSummary> summaries =
        List.of(
            file("src/shop/__init__.py"),
            file("src/shop/price.py", "abs:shop.repository"),
            file("src/shop/repository/__init__.py"),
            file("src/shop/app.py", "from:0:shop:price", "from:0:shop.repository:Store"));
    final List<FileSummary> reversed = new ArrayList<>(summaries);
    Collections.reverse(reversed);

    assertThat(edgeKeys(new PythonDependencyResolver(SRC).resolve(reversed)))
        .containsExactlyInAnyOrderElementsOf(
            edgeKeys(new PythonDependencyResolver(SRC).resolve(summaries)));
    assertThat(edgeKeys(resolve(SRC, summaries.toArray(FileSummary[]::new))))
        .contains("src/shop/app.py -> src/shop/repository/__init__.py");
  }
}
