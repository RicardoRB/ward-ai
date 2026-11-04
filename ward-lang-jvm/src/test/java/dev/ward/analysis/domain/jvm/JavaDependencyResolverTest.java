package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.file;
import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.resolve;
import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.targets;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Name lookup in Java's order: imports, the file itself, the package, then on-demand imports. */
class JavaDependencyResolverTest {
  private static final String PACKAGE_SERVICE = "package:shop.service";
  private static final String PACKAGE_A = "package:a";
  private static final String MAIN = "src/main/java/";
  private static final String ORDER = MAIN + "a/Order.java";
  private static final String PRICE = MAIN + "shop/domain/Price.java";
  private static final String CHECKOUT = MAIN + "shop/service/Checkout.java";
  private static final String CART = MAIN + "shop/service/Cart.java";
  private static final String ORDER_DOMAIN = MAIN + "shop/domain/Order.java";

  @Test
  void singleTypeImportResolvesToTheDeclaringFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(ORDER, List.of(), List.of(PACKAGE_A, "import:shop.domain.Price")),
            file(PRICE, List.of("shop.domain.Price"), List.of()));

    assertThat(targets(edges, ORDER)).containsExactly(PRICE);
  }

  @Test
  void onDemandImportCountsOnlyTheTypesTheFileNames() {
    final List<DependencyEdge> edges =
        resolve(
            file(ORDER, List.of(), List.of(PACKAGE_A, "on-demand:shop.domain", "name:Price")),
            file(PRICE, List.of("shop.domain.Price"), List.of()),
            file(MAIN + "shop/domain/Discount.java", List.of("shop.domain.Discount"), List.of()));

    assertThat(targets(edges, ORDER)).containsExactly(PRICE);
  }

  @Test
  void staticImportResolvesToTheTypeThatDeclaresTheMember() {
    final List<DependencyEdge> edges =
        resolve(
            file(CHECKOUT, List.of(), List.of(PACKAGE_SERVICE, "static:shop.domain.Prices.ZERO")),
            file(MAIN + "shop/domain/Prices.java", List.of("shop.domain.Prices"), List.of()));

    assertThat(targets(edges, CHECKOUT)).containsExactly(MAIN + "shop/domain/Prices.java");
  }

  @Test
  void samePackageReferenceResolvesWithoutAnImport() {
    final List<DependencyEdge> edges =
        resolve(
            file(CHECKOUT, List.of(), List.of(PACKAGE_SERVICE, "name:Cart")),
            file(CART, List.of("shop.service.Cart"), List.of()));

    assertThat(targets(edges, CHECKOUT)).containsExactly(CART);
  }

  @Test
  void explicitImportWinsOverTheSamePackage() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                CHECKOUT,
                List.of(),
                List.of(PACKAGE_SERVICE, "import:shop.domain.Cart", "name:Cart")),
            file(MAIN + "shop/domain/Cart.java", List.of("shop.domain.Cart"), List.of()),
            file(CART, List.of("shop.service.Cart"), List.of()));

    assertThat(targets(edges, CHECKOUT)).containsExactly(MAIN + "shop/domain/Cart.java");
  }

  @Test
  void aTypeDeclaredInTheFileShadowsOtherFiles() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                CHECKOUT,
                List.of("shop.service.Checkout.Cart"),
                List.of(PACKAGE_SERVICE, "name:Cart")),
            file(CART, List.of("shop.service.Cart"), List.of()));

    assertThat(targets(edges, CHECKOUT)).isEmpty();
  }

  @Test
  void aFullyQualifiedNameResolvesToItsDeclaringFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(MAIN + "a/Pricing.java", List.of(), List.of(PACKAGE_A, "name:shop.domain.Price")),
            file(PRICE, List.of("shop.domain.Price"), List.of()));

    assertThat(targets(edges, MAIN + "a/Pricing.java")).containsExactly(PRICE);
  }

  @Test
  void aNestedTypeNamedThroughAnImportResolvesToTheTopLevelFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                CHECKOUT,
                List.of(),
                List.of(PACKAGE_SERVICE, "import:shop.domain.Order", "name:Order.Line")),
            file(ORDER_DOMAIN, List.of("shop.domain.Order", "shop.domain.Order.Line"), List.of()));

    assertThat(targets(edges, CHECKOUT)).containsExactly(ORDER_DOMAIN);
  }

  @Test
  void aNestedTypeImportedByItsQualifiedNameResolvesToTheTopLevelFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(CHECKOUT, List.of(), List.of(PACKAGE_SERVICE, "import:shop.domain.Order.Line")),
            file(ORDER_DOMAIN, List.of("shop.domain.Order", "shop.domain.Order.Line"), List.of()));

    assertThat(targets(edges, CHECKOUT)).containsExactly(ORDER_DOMAIN);
  }

  @Test
  void typesOutsideTheProjectProduceNoEdge() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                ORDER,
                List.of(),
                List.of(
                    PACKAGE_A, "import:java.util.List", "import:org.slf4j.Logger", "name:List")));

    assertThat(edges).isEmpty();
  }
}
