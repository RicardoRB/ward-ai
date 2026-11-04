package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.file;
import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.resolve;
import static dev.ward.analysis.domain.jvm.JavaResolverFixtures.targets;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which files see which: main sources see main sources, test sources also their module's tests. */
class JavaSourceVisibilityTest {
  private static final String PACKAGE_A = "package:a";
  private static final String MAIN = "src/main/java/";
  private static final String TEST = "src/test/java/";
  private static final String CHECKOUT = MAIN + "a/Checkout.java";
  private static final String FIXTURES = TEST + "a/Fixtures.java";

  @Test
  void aFileHasNoEdgeToItself() {
    assertThat(resolve(file(CHECKOUT, List.of("a.Checkout"), List.of(PACKAGE_A, "name:Checkout"))))
        .isEmpty();
  }

  @Test
  void mainSourcesDoNotSeeTestSources() {
    final List<DependencyEdge> edges =
        resolve(
            file(CHECKOUT, List.of(), List.of(PACKAGE_A, "name:Fixtures")),
            file(FIXTURES, List.of("a.Fixtures"), List.of(PACKAGE_A)));

    assertThat(edges).isEmpty();
  }

  @Test
  void testSourcesSeeMainSourcesAndTheirOwnModuleOnly() {
    final List<JavaSourceRoot> modules =
        List.of(
            new JavaSourceRoot("api/src/main/java", "api", false),
            new JavaSourceRoot("api/src/test/java", "api", true),
            new JavaSourceRoot("domain/src/main/java", "domain", false),
            new JavaSourceRoot("domain/src/test/java", "domain", true));
    final String apiTest = "api/src/test/java/a/ApiTest.java";
    final String domainPrice = "domain/src/main/java/shop/domain/Price.java";
    final String apiFixtures = "api/src/test/java/a/Fixtures.java";
    final List<DependencyEdge> edges =
        new JavaDependencyResolver(modules)
            .resolve(
                List.of(
                    file(
                        apiTest,
                        List.of(),
                        List.of(
                            PACKAGE_A,
                            "name:shop.domain.Price",
                            "name:a.Fixtures",
                            "name:shop.domain.Fixtures")),
                    file(domainPrice, List.of("shop.domain.Price"), List.of()),
                    file(
                        "domain/src/test/java/shop/domain/Fixtures.java",
                        List.of("shop.domain.Fixtures"),
                        List.of()),
                    file(apiFixtures, List.of("a.Fixtures"), List.of(PACKAGE_A))));

    assertThat(targets(edges, apiTest)).containsExactly(apiFixtures, domainPrice);
  }

  @Test
  void aStaticImportOfAMemberOfAnotherModuleIsAnEdge() {
    final List<DependencyEdge> edges =
        resolve(
            file(
                "api/Checkout.java",
                List.of(),
                List.of(PACKAGE_A, "static:shop.domain.Prices.ZERO")),
            file("domain/Prices.java", List.of("shop.domain.Prices"), List.of()));

    assertThat(targets(edges, "api/Checkout.java")).containsExactly("domain/Prices.java");
  }
}
