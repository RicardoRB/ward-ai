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

class KotlinFileSummarizerTest {
  private static final String PATH = "shop/service/Checkout.kt";

  @Test
  void declaresTypesNestedTypesAndCompanions() {
    final FileSummary summary =
        summarize(
            """
            package shop.domain

            class Order {
              class Line
              companion object {}
            }
            object Prices
            typealias Money = Long
            interface Priced
            enum class Level { LOW }
            """);

    assertThat(names(summary.declared()))
        .containsExactly(
            "shop.domain.Order",
            "shop.domain.Order.Line",
            "shop.domain.Order.Companion",
            "shop.domain.Prices",
            "shop.domain.Money",
            "shop.domain.Priced",
            "shop.domain.Level");
    assertThat(summary.exports()).isEqualTo(summary.declared());
  }

  @Test
  void topLevelFunctionsAndPropertiesAreDeclaredWithTheFileFacade() {
    final FileSummary summary =
        summarize(
            """
            package shop.util

            fun formatPrice(amount: Long): String = ""
            val TAX = 0.2
            """);

    assertThat(names(summary.declared()))
        .containsExactly("shop.util.formatPrice", "shop.util.TAX", "shop.util.CheckoutKt");
  }

  @Test
  void aJvmNameAnnotationRenamesTheFacade() {
    final FileSummary summary =
        summarize(
            """
            @file:JvmName("Formats")
            package shop.util

            fun formatPrice(amount: Long): String = ""
            """);

    assertThat(names(summary.declared())).contains("shop.util.Formats");
    assertThat(names(summary.declared())).doesNotContain("shop.util.CheckoutKt");
  }

  @Test
  void membersAreNotDeclaredAsTopLevelNames() {
    final FileSummary summary =
        summarize(
            """
            package shop.domain

            class Order {
              val total = 0L
              fun price() = total
            }
            """);

    assertThat(names(summary.declared())).containsExactly("shop.domain.Order");
  }

  @Test
  void readsThePackageAndEveryImportForm() {
    final FileSummary summary =
        summarize(
            """
            package shop.service

            import shop.domain.Price
            import shop.domain.*
            import shop.domain.Price as Money
            import shop.util.formatPrice

            class Checkout
            """);

    assertThat(specifiers(summary))
        .contains(
            "package:shop.service",
            "import:shop.domain.Price",
            "alias:Money=shop.domain.Price",
            "on-demand:shop.domain",
            "import:shop.util.formatPrice");
  }

  @Test
  void readsTypeNamesCallChainsMemberCallsAndAnnotations() {
    final FileSummary summary =
        summarize(
            """
            package shop.service

            @Audited
            class Checkout(private val cart: Cart) : Base() {
              fun run(total: Money): Price {
                val order = Order(total)
                Prices.ZERO.plus(shop.util.formatPrice(total))
                return total.toMoney()
              }
            }
            """);

    assertThat(specifiers(summary))
        .contains(
            "name:Audited",
            "name:Cart",
            "name:Base",
            "name:Money",
            "name:Price",
            "name:Order",
            "name:Prices.ZERO.plus",
            "name:shop.util.formatPrice",
            "name:total.toMoney",
            "member:toMoney");
  }

  @Test
  void readsCallableReferencesAndSkipsKotlinFunctionTypes() {
    final FileSummary summary =
        summarize(
            """
            package shop.service

            val format = ::formatPrice
            val sorter = Sorter::compare
            """);

    assertThat(specifiers(summary)).contains("name:formatPrice", "name:Sorter");
  }

  @Test
  void readsNamesUsedInStringTemplates() {
    final FileSummary summary =
        summarize(
            """
            package shop.util

            val label = "$CURRENCY ${amount.describe()}"
            """);

    assertThat(specifiers(summary)).contains("name:CURRENCY");
  }

  @Test
  void aFileWithoutTopLevelMembersHasNoFacade() {
    final FileSummary summary =
        summarize(
            """
            package shop.domain

            class Price
            """);

    assertThat(names(summary.declared())).containsExactly("shop.domain.Price");
  }

  @Test
  void aBuildScriptHasAnEmptySummary() {
    final SourceUnit unit =
        new SourceUnit("build.gradle.kts", AnalysisLanguage.KOTLIN, "plugins { kotlin(\"jvm\") }");
    final FileSummary summary =
        new KotlinFileSummarizer().summarize(unit, new AntlrLanguageFrontend().parse(unit).tree());

    assertThat(summary.declared()).isEmpty();
    assertThat(summary.imports()).isEmpty();
  }

  private static FileSummary summarize(final String text) {
    final SourceUnit unit = new SourceUnit(PATH, AnalysisLanguage.KOTLIN, text);
    return new KotlinFileSummarizer()
        .summarize(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  private static List<String> specifiers(final FileSummary summary) {
    return summary.imports().stream().map(Reference::specifier).toList();
  }

  private static List<String> names(final List<Symbol> symbols) {
    return symbols.stream().map(Symbol::name).toList();
  }
}
