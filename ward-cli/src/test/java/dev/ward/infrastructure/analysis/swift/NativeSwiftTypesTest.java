package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.swift.SwiftProjectLayout;
import dev.ward.analysis.domain.swift.SwiftTarget;
import dev.ward.analysis.domain.swift.SwiftTypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the native Swift type facts against the scenarios of the native type resolution spec. */
final class NativeSwiftTypesTest {

  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();
  private static final SwiftProjectLayout LAYOUT =
      new SwiftProjectLayout(
          List.of(
              new SwiftTarget("Core", List.of("Core"), List.of(), List.of()),
              new SwiftTarget("Util", List.of("Util"), List.of(), List.of()),
              new SwiftTarget("App", List.of("App"), List.of(), List.of())));

  /** A parsed project: its units and trees by path, and the facts over them. */
  private record Project(
      Map<String, SourceUnit> units, Map<String, SyntaxNode> trees, TypeFacts facts) {}

  @Test
  void annotatedBindingResolvesToTypeInAnotherModule() {
    final Project project =
        project(
            Map.of(
                "Core/User.swift", "struct User {}\n",
                "App/Screen.swift", "import Core\nlet user: User = User()\n"));

    assertThat(typeOf(project, "App/Screen.swift", "pattern_initializer", "user"))
        .map(TypeRef::name)
        .contains("Core.User");
  }

  @Test
  void initializersInferDefaultTypesAndDeclaredConstructors() {
    final Project project =
        project(
            Map.of(
                "App/Values.swift",
                "class Session {}\nlet count = 1\nlet title = \"x\"\nlet ratio = 1.5\n"
                    + "let flag = true\nlet session = Session()\n"));

    assertThat(typeOf(project, "App/Values.swift", "pattern_initializer", "count"))
        .map(TypeRef::name)
        .contains("Int");
    assertThat(typeOf(project, "App/Values.swift", "pattern_initializer", "title"))
        .map(TypeRef::name)
        .contains("String");
    assertThat(typeOf(project, "App/Values.swift", "pattern_initializer", "ratio"))
        .map(TypeRef::name)
        .contains("Double");
    assertThat(typeOf(project, "App/Values.swift", "pattern_initializer", "flag"))
        .map(TypeRef::name)
        .contains("Bool");
    assertThat(typeOf(project, "App/Values.swift", "pattern_initializer", "session"))
        .map(TypeRef::name)
        .contains("App.Session");
  }

  @Test
  void foundationAndUikitTypesStayUnknownWithoutDeclarations() {
    final Project project =
        project(
            Map.of(
                "App/Net.swift",
                "import Foundation\nimport UIKit\nlet date: Date = Date()\n"
                    + "class Screen: UIViewController {}\n"));

    assertThat(typeOf(project, "App/Net.swift", "pattern_initializer", "date")).isEmpty();
    assertThat(supertypesOf(project, "App/Net.swift", "class_declaration", "classScreen"))
        .isEmpty();
  }

  @Test
  void extensionConformanceAddsSupertypeAcrossFiles() {
    final Project project =
        project(
            Map.of(
                "App/Repo.swift", "class Repo {}\n",
                "App/Store.swift", "protocol Store {}\n",
                "App/Ext.swift", "extension Repo: Store {}\n"));

    assertThat(supertypesOf(project, "App/Repo.swift", "class_declaration", "classRepo"))
        .extracting(TypeRef::name)
        .containsExactly("App.Store");
    assertThat(shapeOf(project, "App/Store.swift", "protocol_declaration", "protocolStore"))
        .map(TypeShape::kind)
        .contains(TypeShape.Kind.INTERFACE);
  }

  @Test
  void protocolConformanceResolvesAcrossModules() {
    final Project project =
        project(
            Map.of(
                "Core/Renderable.swift", "protocol Renderable {}\n",
                "App/View.swift", "import Core\nclass View: Renderable {}\n"));

    assertThat(supertypesOf(project, "App/View.swift", "class_declaration", "classView"))
        .extracting(TypeRef::name)
        .containsExactly("Core.Renderable");
  }

  @Test
  void nameDeclaredByTwoImportedModulesStaysUnknown() {
    final Project project =
        project(
            Map.of(
                "Core/Logger.swift", "struct Logger {}\n",
                "Util/Logger.swift", "struct Logger {}\n",
                "App/Use.swift", "import Core\nimport Util\nlet log: Logger = Logger()\n"));

    assertThat(typeOf(project, "App/Use.swift", "pattern_initializer", "log")).isEmpty();
  }

  @Test
  void nestedTypeResolvesInItsEnclosingScope() {
    final Project project =
        project(
            Map.of(
                "App/Outer.swift",
                "struct Outer {\n  struct Inner {}\n  let inner: Inner = Inner()\n}\n"));

    assertThat(typeOf(project, "App/Outer.swift", "pattern_initializer", "inner"))
        .map(TypeRef::name)
        .contains("App.Outer.Inner");
  }

  @Test
  void optionalAndArrayAnnotationsAreWrapped() {
    final Project project = project(Map.of("App/Shapes.swift", "let names: [String]? = nil\n"));

    final Optional<TypeRef> type =
        typeOf(project, "App/Shapes.swift", "pattern_initializer", "names");
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
    final Project project = project(Map.of("App/Box.swift", "struct Box<T> {}\nlet box = Box()\n"));

    assertThat(typeOf(project, "App/Box.swift", "pattern_initializer", "box")).isEmpty();
  }

  @Test
  void declarationShapesReportKindFinalnessAndAbstractness() {
    final Project project =
        project(Map.of("App/Kinds.swift", "enum Mode {\n  case a\n}\nprotocol Shape {}\n"));

    assertThat(shapeOf(project, "App/Kinds.swift", "enum_declaration", "enumMode"))
        .hasValueSatisfying(
            shape -> {
              assertThat(shape.kind()).isEqualTo(TypeShape.Kind.ENUM);
              assertThat(shape.isFinal()).isTrue();
            });
    assertThat(shapeOf(project, "App/Kinds.swift", "protocol_declaration", "protocolShape"))
        .hasValueSatisfying(
            shape -> {
              assertThat(shape.kind()).isEqualTo(TypeShape.Kind.INTERFACE);
              assertThat(shape.isAbstract()).isTrue();
            });
  }

  @Test
  void typesDeclaredInFunctionBodiesAreNotResolvedElsewhere() {
    final Project project =
        project(
            Map.of(
                "App/Local.swift",
                "func run() {\n  struct Temp {}\n  let t: Temp = Temp()\n}\n",
                "App/Other.swift",
                "let t: Temp = Temp()\n"));

    assertThat(typeOf(project, "App/Local.swift", "pattern_initializer", "t")).isEmpty();
    assertThat(typeOf(project, "App/Other.swift", "pattern_initializer", "t")).isEmpty();
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
    final SyntaxNode tree = FRONTEND.parse(unit).tree();
    final SyntaxNode binding = firstNode(tree, "pattern_initializer", "s");
    assertThat(
            facts
                .orElseThrow()
                .factsOf(unit, List.of(binding.span()), EnumSet.of(FactKind.TYPE))
                .typeOf(binding.span())
                .map(TypeRef::name))
        .contains("Session");
  }

  private static Project project(final Map<String, String> sources) {
    final Map<String, SourceUnit> units = new LinkedHashMap<>();
    final Map<String, SyntaxNode> trees = new HashMap<>();
    sources.forEach(
        (path, text) -> {
          final SourceUnit unit = new SourceUnit(path, AnalysisLanguage.SWIFT, text);
          units.put(path, unit);
          trees.put(path, FRONTEND.parse(unit).tree());
        });
    return new Project(units, trees, new SwiftTypeFacts(LAYOUT, trees));
  }

  private static Optional<TypeRef> typeOf(
      final Project project, final String path, final String kind, final String prefix) {
    final SyntaxNode node = firstNode(project.trees().get(path), kind, prefix);
    return project
        .facts()
        .factsOf(project.units().get(path), List.of(node.span()), EnumSet.of(FactKind.TYPE))
        .typeOf(node.span());
  }

  private static Optional<TypeShape> shapeOf(
      final Project project, final String path, final String kind, final String prefix) {
    final SyntaxNode node = firstNode(project.trees().get(path), kind, prefix);
    return project
        .facts()
        .factsOf(project.units().get(path), List.of(node.span()), EnumSet.of(FactKind.SHAPE))
        .shapeOf(node.span());
  }

  private static List<TypeRef> supertypesOf(
      final Project project, final String path, final String kind, final String prefix) {
    return shapeOf(project, path, kind, prefix).map(TypeShape::supertypes).orElse(List.of());
  }

  /** The first node of {@code kind} whose text starts with {@code prefix}. */
  private static SyntaxNode firstNode(
      final SyntaxNode root, final String kind, final String prefix) {
    return findNode(root, kind, prefix)
        .orElseThrow(() -> new IllegalStateException("no " + kind + " starting with " + prefix));
  }

  private static Optional<SyntaxNode> findNode(
      final SyntaxNode node, final String kind, final String prefix) {
    if (kind.equals(node.kind()) && text(node).startsWith(prefix)) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = findNode(child, kind, prefix);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static String text(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    final String token = node.fields().get("text");
    if (token != null) {
      text.append(token);
    }
    for (final SyntaxNode child : node.children()) {
      text.append(text(child));
    }
    return text.toString();
  }
}
