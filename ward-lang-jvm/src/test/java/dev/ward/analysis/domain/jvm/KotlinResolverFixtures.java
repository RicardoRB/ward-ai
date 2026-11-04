package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import java.util.List;

/** The source roots and paths that the Kotlin resolver tests share, and the resolve helper. */
final class KotlinResolverFixtures {
  static final String DOMAIN_PRICE = "src/main/kotlin/shop/domain/Price.kt";
  static final String DOMAIN_DISCOUNT = "src/main/kotlin/shop/domain/Discount.kt";
  static final String SERVICE_CHECKOUT = "src/main/kotlin/shop/service/Checkout.kt";
  static final String SERVICE_CART = "src/main/kotlin/shop/service/Cart.kt";
  static final String UTIL_FORMAT = "src/main/kotlin/shop/util/Format.kt";
  static final String UTIL_OTHER = "src/main/kotlin/shop/util/Other.kt";
  static final String JAVA_ORDER = "src/main/java/shop/domain/Order.java";
  static final String JAVA_REPORT = "src/main/java/shop/legacy/Report.java";
  static final String TEST_FIXTURE = "src/test/kotlin/shop/service/Fixture.kt";
  static final String PACKAGE_SERVICE = "package:shop.service";
  static final String CHECKOUT_DECLARATION = "shop.service.Checkout";
  static final List<JavaSourceRoot> ROOTS =
      List.of(
          new JavaSourceRoot("src/main/kotlin", "", false),
          new JavaSourceRoot("src/main/java", "", false),
          new JavaSourceRoot("src/test/kotlin", "", true));

  private KotlinResolverFixtures() {}

  /** Resolves the summaries with the Kotlin test roots. */
  static List<DependencyEdge> resolve(final FileSummary... summaries) {
    return new JvmDependencyResolver(ROOTS).resolve(List.of(summaries));
  }

  /** The sorted targets of the edges leaving {@code from}. */
  static List<String> targets(final List<DependencyEdge> edges, final String from) {
    return JavaResolverFixtures.targets(edges, from);
  }
}
