package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.file;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class KotlinDependencyResolverTest {
  private static final String DOMAIN_PRICE = "src/main/kotlin/shop/domain/Price.kt";
  private static final String DOMAIN_DISCOUNT = "src/main/kotlin/shop/domain/Discount.kt";
  private static final String SERVICE_CHECKOUT = "src/main/kotlin/shop/service/Checkout.kt";
  private static final String SERVICE_CART = "src/main/kotlin/shop/service/Cart.kt";
  private static final String UTIL_FORMAT = "src/main/kotlin/shop/util/Format.kt";
  private static final String UTIL_OTHER = "src/main/kotlin/shop/util/Other.kt";
  private static final String JAVA_ORDER = "src/main/java/shop/domain/Order.java";
  private static final String JAVA_REPORT = "src/main/java/shop/legacy/Report.java";
  private static final String TEST_FIXTURE = "src/test/kotlin/shop/service/Fixture.kt";
  private static final String PACKAGE_SERVICE = "package:shop.service";
  private static final List<JavaSourceRoot> ROOTS =
      List.of(
          new JavaSourceRoot("src/main/kotlin", "", false),
          new JavaSourceRoot("src/main/java", "", false),
          new JavaSourceRoot("src/test/kotlin", "", true));

  @Test
  void singleImportResolvesToTheDeclaringFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(DOMAIN_PRICE, List.of("shop.domain.Price"), List.of("package:shop.domain")),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "import:shop.domain.Price", "name:Price")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(DOMAIN_PRICE);
  }

  @Test
  void aliasedImportResolvesTheAliasToTheDeclaringFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(DOMAIN_PRICE, List.of("shop.domain.Price"), List.of()),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(
                    PACKAGE_SERVICE,
                    "import:shop.domain.Price",
                    "alias:Money=shop.domain.Price",
                    "name:Money")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(DOMAIN_PRICE);
  }

  @Test
  void importOfTopLevelFunctionResolvesToItsFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(UTIL_FORMAT, List.of("shop.util.formatPrice", "shop.util.FormatKt"), List.of()),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "import:shop.util.formatPrice")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(UTIL_FORMAT);
  }

  @Test
  void starImportCountsOnlyTheNamesTheFileUses() {
    final List<DependencyEdge> edges =
        resolve(
            file(DOMAIN_PRICE, List.of("shop.domain.Price"), List.of()),
            file(DOMAIN_DISCOUNT, List.of("shop.domain.Discount"), List.of()),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "on-demand:shop.domain", "name:Price")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(DOMAIN_PRICE);
  }

  @Test
  void samePackageReferenceResolvesWithoutAnImport() {
    final List<DependencyEdge> edges =
        resolve(
            file(SERVICE_CART, List.of("shop.service.Cart"), List.of(PACKAGE_SERVICE)),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "name:Cart")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(SERVICE_CART);
  }

  @Test
  void sameFileExtensionCallResolvesToItsDeclaringFile() {
    final List<DependencyEdge> edges =
        resolve(
            file(UTIL_FORMAT, List.of("shop.service.toMoney"), List.of(PACKAGE_SERVICE)),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "name:total.toMoney", "member:toMoney")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(UTIL_FORMAT);
  }

  @Test
  void explicitImportWinsOverTheSamePackage() {
    final List<DependencyEdge> edges =
        resolve(
            file(DOMAIN_PRICE, List.of("shop.domain.Cart"), List.of()),
            file(SERVICE_CART, List.of("shop.service.Cart"), List.of(PACKAGE_SERVICE)),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "import:shop.domain.Cart", "name:Cart")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(DOMAIN_PRICE);
  }

  @Test
  void nestedAndCompanionNamesResolveToTheFileOfTheOuterType() {
    final List<DependencyEdge> edges =
        resolve(
            file(DOMAIN_PRICE, List.of("shop.domain.Order", "shop.domain.Order.Line"), List.of()),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(
                    PACKAGE_SERVICE,
                    "import:shop.domain.Order",
                    "name:Order.Line",
                    "name:Order.Companion")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(DOMAIN_PRICE);
  }

  @Test
  void defaultImportsGiveNoEdge() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "name:List", "name:println", "name:String")));

    assertThat(edges).isEmpty();
  }

  @Test
  void libraryImportGivesNoEdge() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(
                    PACKAGE_SERVICE,
                    "import:kotlinx.coroutines.launch",
                    "import:org.slf4j.Logger")));

    assertThat(edges).isEmpty();
  }

  @Test
  void fileHasNoEdgeToItsOwnDeclaration() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                SERVICE_CART, List.of("shop.service.Cart"), List.of(PACKAGE_SERVICE, "name:Cart")));

    assertThat(edges).isEmpty();
  }

  @Test
  void overloadsInTwoFilesOfOnePackageLinkBoth() {
    final List<DependencyEdge> edges =
        resolve(
            file(UTIL_FORMAT, List.of("shop.util.formatPrice"), List.of()),
            file(UTIL_OTHER, List.of("shop.util.formatPrice"), List.of()),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "import:shop.util.formatPrice")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(UTIL_FORMAT, UTIL_OTHER);
  }

  @Test
  void kotlinFileGetsAnEdgeToAJavaDeclaration() {
    final List<DependencyEdge> edges =
        resolve(
            file(JAVA_ORDER, List.of("shop.domain.Order"), List.of()),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "import:shop.domain.Order")));

    assertThat(targets(edges, SERVICE_CHECKOUT)).containsExactly(JAVA_ORDER);
  }

  @Test
  void javaFileGetsAnEdgeToAKotlinClassAndFacade() {
    final List<DependencyEdge> edges =
        resolve(
            file(UTIL_FORMAT, List.of("shop.util.formatPrice", "shop.util.FormatKt"), List.of()),
            file(
                JAVA_REPORT,
                List.of("shop.legacy.Report"),
                List.of("package:shop.legacy", "import:shop.util.FormatKt")));

    assertThat(targets(edges, JAVA_REPORT)).containsExactly(UTIL_FORMAT);
  }

  @Test
  void mainSourceDoesNotSeeTestSource() {
    final List<DependencyEdge> edges =
        resolve(
            file(TEST_FIXTURE, List.of("shop.service.Fixture"), List.of(PACKAGE_SERVICE)),
            file(
                SERVICE_CHECKOUT,
                List.of("shop.service.Checkout"),
                List.of(PACKAGE_SERVICE, "name:Fixture")));

    assertThat(edges).isEmpty();
  }

  private static List<DependencyEdge> resolve(final FileSummary... summaries) {
    return new JvmDependencyResolver(ROOTS).resolve(List.of(summaries));
  }

  private static List<String> targets(final List<DependencyEdge> edges, final String from) {
    return JavaResolverFixtures.targets(edges, from);
  }
}
