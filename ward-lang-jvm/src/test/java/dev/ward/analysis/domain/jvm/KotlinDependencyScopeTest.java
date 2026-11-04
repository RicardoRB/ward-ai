package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.file;
import static dev.ward.analysis.domain.jvm.KotlinResolverFixtures.resolve;
import static dev.ward.analysis.domain.jvm.KotlinResolverFixtures.targets;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.List;
import org.junit.jupiter.api.Test;

class KotlinDependencyScopeTest {
  @Test
  void defaultImportsGiveNoEdge() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(
                    KotlinResolverFixtures.PACKAGE_SERVICE,
                    "name:List",
                    "name:println",
                    "name:String")));

    assertThat(edges).isEmpty();
  }

  @Test
  void libraryImportGivesNoEdge() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(
                    KotlinResolverFixtures.PACKAGE_SERVICE,
                    "import:kotlinx.coroutines.launch",
                    "import:org.slf4j.Logger")));

    assertThat(edges).isEmpty();
  }

  @Test
  void fileHasNoEdgeToItsOwnDeclaration() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.SERVICE_CART,
                List.of("shop.service.Cart"),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE, "name:Cart")));

    assertThat(edges).isEmpty();
  }

  @Test
  void overloadsInTwoFilesOfOnePackageLinkBoth() {
    final List<DependencyEdge> edges =
        resolve(
            file(KotlinResolverFixtures.UTIL_FORMAT, List.of("shop.util.formatPrice"), List.of()),
            file(KotlinResolverFixtures.UTIL_OTHER, List.of("shop.util.formatPrice"), List.of()),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE, "import:shop.util.formatPrice")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.UTIL_FORMAT, KotlinResolverFixtures.UTIL_OTHER);
  }

  @Test
  void kotlinFileGetsAnEdgeToAJavaDeclaration() {
    final List<DependencyEdge> edges =
        resolve(
            file(KotlinResolverFixtures.JAVA_ORDER, List.of("shop.domain.Order"), List.of()),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE, "import:shop.domain.Order")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.JAVA_ORDER);
  }

  @Test
  void javaFileGetsAnEdgeToAKotlinClassAndFacade() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.UTIL_FORMAT,
                List.of("shop.util.formatPrice", "shop.util.FormatKt"),
                List.of()),
            file(
                KotlinResolverFixtures.JAVA_REPORT,
                List.of("shop.legacy.Report"),
                List.of("package:shop.legacy", "import:shop.util.FormatKt")));

    assertThat(targets(edges, KotlinResolverFixtures.JAVA_REPORT))
        .containsExactly(KotlinResolverFixtures.UTIL_FORMAT);
  }

  @Test
  void mainSourceDoesNotSeeTestSource() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.TEST_FIXTURE,
                List.of("shop.service.Fixture"),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE)),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE, "name:Fixture")));

    assertThat(edges).isEmpty();
  }
}
