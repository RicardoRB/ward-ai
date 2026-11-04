package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaFileSummarizerTest {
  private static final String PATH = "shop/service/Checkout.java";

  @Test
  void declaresEveryTypeIncludingNestedOnes() {
    final FileSummary summary =
        summarize(
            """
            package shop.domain;

            public final class Order {
              public static final class Line {}
            }
            """);

    assertThat(names(summary.declared()))
        .containsExactly("shop.domain.Order", "shop.domain.Order.Line");
    assertThat(names(summary.exports())).isEqualTo(names(summary.declared()));
  }

  @Test
  void readsThePackageAndEveryImportForm() {
    final FileSummary summary =
        summarize(
            """
            package shop.service;

            import java.util.List;
            import shop.domain.*;
            import static shop.domain.Prices.ZERO;

            public final class Checkout {}
            """);

    assertThat(specifiers(summary))
        .containsExactly(
            "package:shop.service",
            "import:java.util.List",
            "on-demand:shop.domain",
            "static:shop.domain.Prices.ZERO");
  }

  @Test
  void readsTypeNamesFromTypePositionsAndNestedNames() {
    final FileSummary summary =
        summarize(
            """
            package shop.service;

            import java.util.List;

            public final class Checkout {
              private final Cart cart = new Cart();
              private final List<Order.Line> lines = null;

              Price total(Discount discount) throws IOFailure {
                return null;
              }
            }
            """);

    assertThat(specifiers(summary))
        .contains("name:Cart", "name:Order.Line", "name:Price", "name:Discount", "name:IOFailure");
  }

  @Test
  void readsFullyQualifiedNamesFromTypesAndCreators() {
    final FileSummary summary =
        summarize(
            """
            package shop.service;

            public final class Pricing {
              shop.domain.Price base() {
                return new shop.domain.Price(10);
              }
            }
            """);

    assertThat(specifiers(summary)).contains("name:shop.domain.Price");
  }

  @Test
  void readsQualifierOfAStaticMemberAccess() {
    final FileSummary summary =
        summarize(
            """
            package shop.service;

            class Uses {
              Object value = Prices.ZERO;
            }
            """);

    assertThat(specifiers(summary)).contains("name:Prices.ZERO");
  }

  @Test
  void readsAnnotationNamesWithoutTheirArguments() {
    final FileSummary summary =
        summarize(
            """
            package shop.service;

            @shop.Audited(Level.HIGH)
            class Annotated {}
            """);

    assertThat(specifiers(summary)).contains("name:shop.Audited", "name:Level.HIGH");
  }

  @Test
  void ignoresPrimitiveTypesAndJdkCommentsOnly() {
    final FileSummary summary =
        summarize(
            """
            package shop.service;

            class Numbers {
              // Ignored: Mystery
              int count = 0;
            }
            """);

    assertThat(specifiers(summary)).noneMatch(specifier -> specifier.contains("Mystery"));
    assertThat(specifiers(summary)).noneMatch("name:int"::equals);
  }

  private static FileSummary summarize(final String text) {
    final SourceUnit unit = new SourceUnit(PATH, AnalysisLanguage.JAVA, text);
    return new JavaFileSummarizer().summarize(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  private static List<String> specifiers(final FileSummary summary) {
    return summary.imports().stream().map(Reference::specifier).toList();
  }

  private static List<String> names(final List<Symbol> symbols) {
    return symbols.stream().map(Symbol::name).toList();
  }
}
