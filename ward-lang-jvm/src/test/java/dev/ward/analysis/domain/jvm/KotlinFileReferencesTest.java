package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinFileSummaries.specifiers;
import static dev.ward.analysis.domain.jvm.KotlinFileSummaries.summarize;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

class KotlinFileReferencesTest {
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
  void aBuildScriptHasAnEmptySummary() {
    final SourceUnit unit =
        new SourceUnit("build.gradle.kts", AnalysisLanguage.KOTLIN, "plugins { kotlin(\"jvm\") }");
    final FileSummary summary =
        new KotlinFileSummarizer().summarize(unit, new AntlrLanguageFrontend().parse(unit).tree());

    assertThat(summary.declared()).isEmpty();
    assertThat(summary.imports()).isEmpty();
  }
}
