package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the native Swift type facts against the scenarios of the native type resolution spec. */
final class NativeSwiftTypesTest {

  @Test
  void annotatedBindingResolvesToTypeInAnotherModule() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of(
                "Core/User.swift", "struct User {}\n",
                "App/Screen.swift", "import Core\nlet user: User = User()\n"));

    assertThat(SwiftTypeProject.binding(project, "App/Screen.swift", "user"))
        .map(TypeRef::name)
        .contains("Core.User");
  }

  @Test
  void extensionConformanceAddsSupertypeAcrossFiles() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of(
                "App/Repo.swift", "class Repo {}\n",
                "App/Store.swift", "protocol Store {}\n",
                "App/Ext.swift", "extension Repo: Store {}\n"));

    assertThat(
            SwiftTypeProject.supertypesOf(
                project, "App/Repo.swift", "class_declaration", "classRepo"))
        .extracting(TypeRef::name)
        .containsExactly("App.Store");
    assertThat(
            SwiftTypeProject.shapeOf(
                project, "App/Store.swift", "protocol_declaration", "protocolStore"))
        .map(TypeShape::kind)
        .contains(TypeShape.Kind.INTERFACE);
  }

  @Test
  void protocolConformanceResolvesAcrossModules() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of(
                "Core/Renderable.swift", "protocol Renderable {}\n",
                "App/View.swift", "import Core\nclass View: Renderable {}\n"));

    assertThat(
            SwiftTypeProject.supertypesOf(
                project, "App/View.swift", "class_declaration", "classView"))
        .extracting(TypeRef::name)
        .containsExactly("Core.Renderable");
  }

  @Test
  void nestedTypeResolvesInItsEnclosingScope() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of(
                "App/Outer.swift",
                "struct Outer {\n  struct Inner {}\n  let inner: Inner = Inner()\n}\n"));

    assertThat(SwiftTypeProject.binding(project, "App/Outer.swift", "inner"))
        .map(TypeRef::name)
        .contains("App.Outer.Inner");
  }

  @Test
  void declarationShapesReportKindFinalnessAndAbstractness() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of("App/Kinds.swift", "enum Mode {\n  case a\n}\nprotocol Shape {}\n"));

    assertThat(SwiftTypeProject.shapeOf(project, "App/Kinds.swift", "enum_declaration", "enumMode"))
        .hasValueSatisfying(
            shape -> {
              assertThat(shape.kind()).isEqualTo(TypeShape.Kind.ENUM);
              assertThat(shape.isFinal()).isTrue();
            });
    assertThat(
            SwiftTypeProject.shapeOf(
                project, "App/Kinds.swift", "protocol_declaration", "protocolShape"))
        .hasValueSatisfying(
            shape -> {
              assertThat(shape.kind()).isEqualTo(TypeShape.Kind.INTERFACE);
              assertThat(shape.isAbstract()).isTrue();
            });
  }

  @Test
  void nativeProviderStartsFromProjectFilesWithoutAProcess(@TempDir final Path root)
      throws IOException {
    Files.writeString(root.resolve("Session.swift"), "class Session {}\nlet s = Session()\n");

    final Optional<TypeFacts> facts = new NativeSwiftTypes(root).start();

    assertThat(facts).isPresent();
    final SourceUnit unit =
        new SourceUnit(
            "Session.swift",
            AnalysisLanguage.SWIFT,
            Files.readString(root.resolve("Session.swift")));
    final SyntaxNode tree = new AntlrLanguageFrontend().parse(unit).tree();
    final SyntaxNode binding = SwiftTypeProject.firstNode(tree, "pattern_initializer", "s");
    assertThat(
            facts
                .orElseThrow()
                .factsOf(unit, List.of(binding.span()), EnumSet.of(FactKind.TYPE))
                .typeOf(binding.span())
                .map(TypeRef::name))
        .contains("Session");
  }
}
