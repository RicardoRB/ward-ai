package dev.ward.analysis.domain.python;

import static dev.ward.analysis.domain.python.ResolverFixtures.file;
import static dev.ward.analysis.domain.python.ResolverFixtures.resolve;
import static dev.ward.analysis.domain.python.ResolverFixtures.targets;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks which file each Python import form resolves to, on a src layout and a uv workspace. */
final class PythonImportResolutionTest {

  private static final PythonProjectLayout SRC = new PythonProjectLayout(List.of("src/"));

  @Test
  void resolvesAnImportOfASubmoduleToItsFile() {
    final List<DependencyEdge> edges =
        resolve(
            SRC,
            file("src/shop/__init__.py"),
            file("src/shop/repository/__init__.py"),
            file("src/shop/repository/store.py"),
            file("src/shop/app.py", "from:0:shop.repository:store"));

    assertThat(targets(edges)).containsExactly("src/shop/repository/store.py");
  }

  @Test
  void resolvesAnImportOfANameToThePackageThatDefinesIt() {
    final List<DependencyEdge> edges =
        resolve(
            SRC,
            file("src/shop/repository/__init__.py"),
            file("src/shop/app.py", "from:0:shop.repository:Store"));

    assertThat(targets(edges)).containsExactly("src/shop/repository/__init__.py");
  }

  @Test
  void resolvesAnAbsoluteImportOfADottedModule() {
    final List<DependencyEdge> edges =
        resolve(
            SRC,
            file("src/shop/repository/store.py"),
            file("src/shop/app.py", "abs:shop.repository.store"));

    assertThat(targets(edges)).containsExactly("src/shop/repository/store.py");
  }

  @Test
  void givesNoEdgeToStandardLibraryOrThirdPartyModules() {
    final List<DependencyEdge> edges =
        resolve(SRC, file("src/shop/app.py", "abs:json", "from:0:django.db:models"));

    assertThat(edges).isEmpty();
  }

  @Test
  void resolvesASiblingRelativeImport() {
    final List<DependencyEdge> edges =
        resolve(
            SRC,
            file("src/shop/controller/views.py"),
            file("src/shop/controller/clean.py", "from:1::views"));

    assertThat(targets(edges)).containsExactly("src/shop/controller/views.py");
  }

  @Test
  void resolvesAParentRelativeImportAgainstThePackage() {
    final List<DependencyEdge> edges =
        resolve(
            SRC,
            file("src/shop/repository/store.py"),
            file("src/shop/controller/clean.py", "from:2:repository.store:Store"));

    assertThat(targets(edges)).containsExactly("src/shop/repository/store.py");
  }

  @Test
  void resolvesARelativeImportFromAPackageInitAgainstItself() {
    final List<DependencyEdge> edges =
        resolve(SRC, file("src/shop/views.py"), file("src/shop/__init__.py", "from:1::views"));

    assertThat(targets(edges)).containsExactly("src/shop/views.py");
  }

  @Test
  void prefersThePackageWhenAModuleAndAPackageShareAName() {
    final List<DependencyEdge> edges =
        resolve(
            SRC,
            file("src/shop/price/__init__.py"),
            file("src/shop/price.py"),
            file("src/shop/app.py", "abs:shop.price"));

    assertThat(targets(edges)).containsExactly("src/shop/price/__init__.py");
  }

  @Test
  void resolvesAUvWorkspaceMemberImportAcrossMembers() {
    final PythonProjectLayout workspace =
        new PythonProjectLayout(List.of("packages/billing/src/", "packages/shop/src/"));
    final List<DependencyEdge> edges =
        resolve(
            workspace,
            file("packages/billing/src/billing/invoice.py"),
            file("packages/shop/src/shop/checkout.py", "abs:billing.invoice"));

    assertThat(targets(edges)).containsExactly("packages/billing/src/billing/invoice.py");
  }
}
