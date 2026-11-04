package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.file;
import static dev.ward.analysis.domain.jvm.KotlinResolverFixtures.resolve;
import static dev.ward.analysis.domain.jvm.KotlinResolverFixtures.targets;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.List;
import org.junit.jupiter.api.Test;

class KotlinDependencyResolverTest {
  @Test
  void singleImportResolvesToTheDeclaringFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.DOMAIN_PRICE,
                List.of("shop.domain.Price"),
                List.of("package:shop.domain")),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(
                    KotlinResolverFixtures.PACKAGE_SERVICE,
                    "import:shop.domain.Price",
                    "name:Price")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.DOMAIN_PRICE);
  }

  @Test
  void aliasedImportResolvesTheAliasToTheDeclaringFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(KotlinResolverFixtures.DOMAIN_PRICE, List.of("shop.domain.Price"), List.of()),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(
                    KotlinResolverFixtures.PACKAGE_SERVICE,
                    "import:shop.domain.Price",
                    "alias:Money=shop.domain.Price",
                    "name:Money")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.DOMAIN_PRICE);
  }

  @Test
  void importOfTopLevelFunctionResolvesToItsFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.UTIL_FORMAT,
                List.of("shop.util.formatPrice", "shop.util.FormatKt"),
                List.of()),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE, "import:shop.util.formatPrice")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.UTIL_FORMAT);
  }

  @Test
  void starImportCountsOnlyTheNamesTheFileUses() {
    final List<DependencyEdge> edges =
        resolve(
            file(KotlinResolverFixtures.DOMAIN_PRICE, List.of("shop.domain.Price"), List.of()),
            file(
                KotlinResolverFixtures.DOMAIN_DISCOUNT, List.of("shop.domain.Discount"), List.of()),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(
                    KotlinResolverFixtures.PACKAGE_SERVICE,
                    "on-demand:shop.domain",
                    "name:Price")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.DOMAIN_PRICE);
  }

  @Test
  void samePackageReferenceResolvesWithoutAnImport() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.SERVICE_CART,
                List.of("shop.service.Cart"),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE)),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE, "name:Cart")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.SERVICE_CART);
  }

  @Test
  void sameFileExtensionCallResolvesToItsDeclaringFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.UTIL_FORMAT,
                List.of("shop.service.toMoney"),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE)),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(
                    KotlinResolverFixtures.PACKAGE_SERVICE,
                    "name:total.toMoney",
                    "member:toMoney")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.UTIL_FORMAT);
  }

  @Test
  void explicitImportWinsOverTheSamePackage() {
    final List<DependencyEdge> edges =
        resolve(
            file(KotlinResolverFixtures.DOMAIN_PRICE, List.of("shop.domain.Cart"), List.of()),
            file(
                KotlinResolverFixtures.SERVICE_CART,
                List.of("shop.service.Cart"),
                List.of(KotlinResolverFixtures.PACKAGE_SERVICE)),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(
                    KotlinResolverFixtures.PACKAGE_SERVICE,
                    "import:shop.domain.Cart",
                    "name:Cart")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.DOMAIN_PRICE);
  }

  @Test
  void nestedAndCompanionNamesResolveToTheFileOfTheOuterType() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                KotlinResolverFixtures.DOMAIN_PRICE,
                List.of("shop.domain.Order", "shop.domain.Order.Line"),
                List.of()),
            file(
                KotlinResolverFixtures.SERVICE_CHECKOUT,
                List.of(KotlinResolverFixtures.CHECKOUT_DECLARATION),
                List.of(
                    KotlinResolverFixtures.PACKAGE_SERVICE,
                    "import:shop.domain.Order",
                    "name:Order.Line",
                    "name:Order.Companion")));

    assertThat(targets(edges, KotlinResolverFixtures.SERVICE_CHECKOUT))
        .containsExactly(KotlinResolverFixtures.DOMAIN_PRICE);
  }
}
