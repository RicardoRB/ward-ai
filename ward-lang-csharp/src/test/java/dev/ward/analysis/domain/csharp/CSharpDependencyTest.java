package dev.ward.analysis.domain.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class CSharpDependencyTest {
  @Test
  void aNamespaceBlockAndAFileScopedNamespaceAreExportsAndNestedOnesAreJoined() {
    final FileSummary block =
        summarize("a.cs", "namespace Shop { namespace Domain { class A {} } }");
    final FileSummary scoped = summarize("b.cs", "namespace Shop.Infra;\nclass B {}\n");

    assertThat(block.exports().stream().map(Symbol::name))
        .containsExactly("Shop", "Shop.Domain");
    assertThat(scoped.exports().stream().map(Symbol::name)).containsExactly("Shop.Infra");
  }

  @Test
  void everyUsingOfANamespaceIsAnImportButAnAliasIsNot() {
    final FileSummary summary =
        summarize(
            "a.cs",
            "using System;\nusing static Shop.Util;\nusing Money = Shop.Money;\n"
                + "global using Shop.Domain;\nnamespace Shop.App;\nclass A {}\n"
        );

    assertThat(summary.imports().stream().map(Reference::specifier))
        .containsExactly("System", "Shop.Util", "Shop.Domain");
  }

  @Test
  void aUsingIsAnEdgeToTheOtherFilesOfTheNamespaceAndAStaticUsingResolvesByPrefix() {
    final List<DependencyEdge> edges =
        new CSharpNamespaceResolver()
            .resolve(
                List.of(
                    summarize(
                        "App.cs",
                        "using Shop.Domain;\nusing static Shop.Infra.Db;\nclass A {}"
                    ),
                    summarize("D1.cs", "namespace Shop.Domain;\nclass D1 {}"),
                    summarize("D2.cs", "namespace Shop.Domain;\nusing Shop.Domain;\nclass D2 {}"),
                    summarize("Db.cs", "namespace Shop.Infra;\nclass Db {}")
                )
            );

    assertThat(edges.stream().map(edge -> edge.from() + " -> " + edge.to()))
        .containsExactly("App.cs -> D1.cs", "App.cs -> D2.cs", "App.cs -> Db.cs", "D2.cs -> D1.cs");
  }

  @Test
  void aNamespaceNoFileDeclaresIsNoEdge() {
    assertThat(
        new CSharpNamespaceResolver()
            .resolve(List.of(summarize("a.cs", "using System.Text;\nclass A {}")))
    )
        .isEmpty();
  }

  private static FileSummary summarize(final String path, final String source) {
    final SourceUnit unit = new SourceUnit(path, AnalysisLanguage.CSHARP, source);
    return new CSharpFileSummarizer()
        .summarize(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }
}
