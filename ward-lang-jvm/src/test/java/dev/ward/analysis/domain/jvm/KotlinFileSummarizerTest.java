package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinFileSummaries.names;
import static dev.ward.analysis.domain.jvm.KotlinFileSummaries.summarize;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FileSummary;
import org.junit.jupiter.api.Test;

class KotlinFileSummarizerTest {
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
  void aFileWithoutTopLevelMembersHasNoFacade() {
    final FileSummary summary =
        summarize(
            """
            package shop.domain

            class Price
            """);

    assertThat(names(summary.declared())).containsExactly("shop.domain.Price");
  }
}
