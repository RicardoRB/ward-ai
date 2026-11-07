package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks the inferred and unknown native Swift types against the scenarios of the spec. */
final class NativeSwiftTypeInferenceTest {

  @Test
  void initializersInferDefaultTypesAndDeclaredConstructors() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of(
                "App/Values.swift",
                "class Session {}\nlet count = 1\nlet title = \"x\"\nlet ratio = 1.5\n"
                    + "let flag = true\nlet session = Session()\n"));

    assertThat(SwiftTypeProject.binding(project, "App/Values.swift", "count"))
        .map(TypeRef::name)
        .contains("Int");
    assertThat(SwiftTypeProject.binding(project, "App/Values.swift", "title"))
        .map(TypeRef::name)
        .contains("String");
    assertThat(SwiftTypeProject.binding(project, "App/Values.swift", "ratio"))
        .map(TypeRef::name)
        .contains("Double");
    assertThat(SwiftTypeProject.binding(project, "App/Values.swift", "flag"))
        .map(TypeRef::name)
        .contains("Bool");
    assertThat(SwiftTypeProject.binding(project, "App/Values.swift", "session"))
        .map(TypeRef::name)
        .contains("App.Session");
  }

  @Test
  void foundationAndUikitTypesStayUnknownWithoutDeclarations() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of(
                "App/Net.swift",
                "import Foundation\nimport UIKit\nlet date: Date = Date()\n"
                    + "class Screen: UIViewController {}\n"));

    assertThat(SwiftTypeProject.binding(project, "App/Net.swift", "date")).isEmpty();
    assertThat(
            SwiftTypeProject.supertypesOf(
                project, "App/Net.swift", "class_declaration", "classScreen"))
        .isEmpty();
  }

  @Test
  void nameDeclaredByTwoImportedModulesStaysUnknown() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of(
                "Core/Logger.swift", "struct Logger {}\n",
                "Util/Logger.swift", "struct Logger {}\n",
                "App/Use.swift", "import Core\nimport Util\nlet log: Logger = Logger()\n"));

    assertThat(SwiftTypeProject.binding(project, "App/Use.swift", "log")).isEmpty();
  }

  @Test
  void optionalAndArrayAnnotationsAreWrapped() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(Map.of("App/Shapes.swift", "let names: [String]? = nil\n"));

    final Optional<TypeRef> type = SwiftTypeProject.binding(project, "App/Shapes.swift", "names");
    assertThat(type).map(TypeRef::name).contains("Optional");
    assertThat(type.map(TypeRef::arguments).orElse(List.of()))
        .singleElement()
        .satisfies(
            array -> {
              assertThat(array.name()).isEqualTo("Array");
              assertThat(array.arguments())
                  .singleElement()
                  .satisfies(element -> assertThat(element.name()).isEqualTo("String"));
            });
  }

  @Test
  void constructingGenericTypeWithoutArgumentsStaysUnknown() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(Map.of("App/Box.swift", "struct Box<T> {}\nlet box = Box()\n"));

    assertThat(SwiftTypeProject.binding(project, "App/Box.swift", "box")).isEmpty();
  }

  @Test
  void typesDeclaredInFunctionBodiesAreNotResolvedElsewhere() {
    final SwiftTypeProject.Project project =
        SwiftTypeProject.project(
            Map.of(
                "App/Local.swift",
                "func run() {\n  struct Temp {}\n  let t: Temp = Temp()\n}\n",
                "App/Other.swift",
                "let t: Temp = Temp()\n"));

    assertThat(SwiftTypeProject.binding(project, "App/Local.swift", "t")).isEmpty();
    assertThat(SwiftTypeProject.binding(project, "App/Other.swift", "t")).isEmpty();
  }
}
