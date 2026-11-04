package dev.ward.analysis.domain.python;

import static dev.ward.analysis.domain.python.SummaryFixtures.names;
import static dev.ward.analysis.domain.python.SummaryFixtures.specifiers;
import static dev.ward.analysis.domain.python.SummaryFixtures.summarize;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FileSummary;
import org.junit.jupiter.api.Test;

/** Summarizes parsed Python source and checks each import form and exported name. */
final class PythonFileSummarizerTest {

  @Test
  void recordsAbsoluteImportsOfDottedModules() {
    final FileSummary summary = summarize("import shop.repository.store\nimport json as j\n");

    assertThat(specifiers(summary)).containsExactly("abs:shop.repository.store", "abs:json");
  }

  @Test
  void recordsFromImportsWithLevelModuleAndName() {
    final FileSummary summary =
        summarize("from shop.repository import store, Store as S\nfrom . import views\n");

    assertThat(specifiers(summary))
        .containsExactly(
            "from:0:shop.repository:store", "from:0:shop.repository:Store", "from:1::views");
  }

  @Test
  void countsEachDotAndEllipsisAsOneLevelAndThreeLevels() {
    final FileSummary summary =
        summarize("from ..repository.store import Store\nfrom ... import base\n");

    assertThat(specifiers(summary))
        .containsExactly("from:2:repository.store:Store", "from:3::base");
  }

  @Test
  void recordsStarImportsAsStar() {
    assertThat(specifiers(summarize("from shop.price import *\n")))
        .containsExactly("from:0:shop.price:*");
  }

  @Test
  void recordsImportsInsideFunctionsTryBlocksAndTypeCheckingBlocks() {
    final FileSummary summary =
        summarize(
            """
            from typing import TYPE_CHECKING
            if TYPE_CHECKING:
                from shop.a import A
            try:
                import shop.b
            except ImportError:
                pass
            def load():
                from shop.c import c
            """);

    assertThat(specifiers(summary))
        .containsExactly(
            "from:0:typing:TYPE_CHECKING", "from:0:shop.a:A", "abs:shop.b", "from:0:shop.c:c");
  }

  @Test
  void exportsTopLevelDefinitionsClassesAndAssignments() {
    final FileSummary summary =
        summarize(
            """
            LIMIT = 3
            total: int = 0
            total += 1
            def price(): ...
            class Store: ...
            @decorator
            def wrapped(): ...
            def outer():
                def inner(): ...
            """);

    assertThat(names(summary.exports()))
        .containsExactly("LIMIT", "total", "price", "Store", "wrapped", "outer");
  }

  @Test
  void followsALiteralAllListInsteadOfTheDefinitions() {
    final FileSummary summary =
        summarize("__all__ = ['Store', \"open_store\"]\nclass Store: ...\nhelper = 1\n");

    assertThat(names(summary.exports())).containsExactly("Store", "open_store");
  }

  @Test
  void ignoresAllWhenItIsNotALiteralListOfStrings() {
    final FileSummary summary = summarize("__all__ = build()\nclass Store: ...\n");

    assertThat(names(summary.exports())).containsExactly("__all__", "Store");
  }

  @Test
  void recordsNoImportsForAFileWithoutImports() {
    final FileSummary summary = summarize("x = 1\n");

    assertThat(summary.imports()).isEmpty();
    assertThat(summary.path()).isEqualTo(SummaryFixtures.PATH);
  }
}
