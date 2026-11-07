package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the native Swift type facts against the scenarios of the native type resolution spec. */
final class SwiftTypeFactsTest {

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
  void finalClassIsReportedAsFinal() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(Map.of("App/Token.swift", "final class Token {}\n"));

    assertThat(
            SwiftTypeProject.shapeOf(
                project, "App/Token.swift", "class_declaration", "finalclassToken"))
        .hasValueSatisfying(
            shape -> {
              assertThat(shape.kind()).isEqualTo(TypeShape.Kind.CLASS);
              assertThat(shape.isFinal()).isTrue();
            });
  }
}
