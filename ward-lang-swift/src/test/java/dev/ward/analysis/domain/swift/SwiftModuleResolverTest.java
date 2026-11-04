package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwiftModuleResolverTest {

  private static final Span AT = new Span(1, 1, 1, 10, 0, 9);
  private static final SwiftProjectLayout LAYOUT =
      new SwiftProjectLayout(
          List.of(
              new SwiftTarget("Shop", List.of("Sources/Shop"), List.of(), List.of("Domain")),
              new SwiftTarget("Domain", List.of("Sources/Domain"), List.of(), List.of()),
              new SwiftTarget(
                  "ShopTests", List.of("Tests/ShopTests"), List.of(), List.of("Shop"))));

  @Test
  void anImportIsAnEdgeToEveryFileOfTheModule() {
    final List<DependencyEdge> edges =
        new SwiftModuleResolver(LAYOUT)
            .resolve(
                List.of(
                    summary("Tests/ShopTests/ShopTests.swift", "Shop"),
                    summary("Sources/Shop/Cart.swift"),
                    summary("Sources/Shop/Order.swift")));

    assertThat(edges.stream().map(edge -> edge.from() + " -> " + edge.to()))
        .containsExactly(
            "Tests/ShopTests/ShopTests.swift -> Sources/Shop/Cart.swift",
            "Tests/ShopTests/ShopTests.swift -> Sources/Shop/Order.swift");
  }

  @Test
  void importsOfUndeclaredModulesAndTheImplicitModuleGiveNoEdge() {
    final List<DependencyEdge> edges =
        new SwiftModuleResolver(LAYOUT)
            .resolve(List.of(summary("Sources/Shop/Cart.swift", "Foundation", "Harmonize")));

    assertThat(edges).isEmpty();
  }

  @Test
  void aFileNeverHasAnEdgeToItself() {
    final List<DependencyEdge> edges =
        new SwiftModuleResolver(LAYOUT)
            .resolve(List.of(summary("Sources/Shop/Cart.swift", "Shop")));

    assertThat(edges).isEmpty();
  }

  @Test
  void aUsedTypeNameIsAnEdgeToTheOtherFileOfItsModuleThatDeclaresIt() {
    final List<DependencyEdge> edges =
        new SwiftModuleResolver(LAYOUT)
            .resolve(
                List.of(
                    usingNames("Sources/Shop/Controller.swift", "OrderService"),
                    declaring("Sources/Shop/Service.swift", "OrderService")));

    assertThat(edges.stream().map(edge -> edge.from() + " -> " + edge.to()))
        .containsExactly("Sources/Shop/Controller.swift -> Sources/Shop/Service.swift");
  }

  @Test
  void aNestedTypeNameResolvesToTheFileThatDeclaresTheEnclosingType() {
    final List<DependencyEdge> edges =
        new SwiftModuleResolver(LAYOUT)
            .resolve(
                List.of(
                    usingNames("Sources/Shop/Cart.swift", "Line"),
                    declaring("Sources/Shop/Order.swift", "Order", "Order.Line")));

    assertThat(edges.stream().map(edge -> edge.from() + " -> " + edge.to()))
        .containsExactly("Sources/Shop/Cart.swift -> Sources/Shop/Order.swift");
  }

  @Test
  void aTypeNameDeclaredOnlyInAnotherModuleGivesNoNameEdge() {
    final List<DependencyEdge> edges =
        new SwiftModuleResolver(LAYOUT)
            .resolve(
                List.of(
                    usingNames("Tests/ShopTests/ShopTests.swift", "Cart"),
                    declaring("Sources/Domain/Order.swift", "Cart")));

    assertThat(edges).isEmpty();
  }

  private static FileSummary summary(final String path, final String... modules) {
    final List<Reference> imports =
        List.of(modules).stream()
            .map(module -> new Reference(SwiftFileSummarizer.MODULE_PREFIX + module, AT))
            .toList();
    return new FileSummary(path, List.of(), imports, List.of());
  }

  private static FileSummary usingNames(final String path, final String... names) {
    final List<Reference> imports =
        List.of(names).stream()
            .map(name -> new Reference(SwiftFileSummarizer.NAME_PREFIX + name, AT))
            .toList();
    return new FileSummary(path, List.of(), imports, List.of());
  }

  private static FileSummary declaring(final String path, final String... names) {
    final List<Symbol> declared =
        List.of(names).stream().map(name -> new Symbol(name, AT)).toList();
    return new FileSummary(path, declared, List.of(), declared);
  }
}
