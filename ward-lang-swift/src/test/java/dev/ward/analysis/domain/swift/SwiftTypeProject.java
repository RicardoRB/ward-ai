package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds parsed in-memory Swift projects and looks up nodes and facts in them for the tests. */
final class SwiftTypeProject {

  private static final String BINDING = "pattern_initializer";
  private static final String TEXT = "text";
  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();
  private static final SwiftProjectLayout LAYOUT =
      new SwiftProjectLayout(
          List.of(
              new SwiftTarget("Core", List.of("Core"), List.of(), List.of()),
              new SwiftTarget("Util", List.of("Util"), List.of(), List.of()),
              new SwiftTarget("App", List.of("App"), List.of(), List.of())));

  /** A parsed project: its units and trees by path, and the facts over them. */
  record Project(
      Map<String, SourceUnit> units, Map<String, SyntaxNode> trees, SwiftTypeFacts facts) {}

  private SwiftTypeProject() {}

  static Project project(final Map<String, String> sources) {
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

  static Optional<TypeRef> typeOf(
      final Project project, final String path, final String kind, final String prefix) {
    final SyntaxNode node = firstNode(project.trees().get(path), kind, prefix);
    return project
        .facts()
        .factsOf(project.units().get(path), List.of(node.span()), EnumSet.of(FactKind.TYPE))
        .typeOf(node.span());
  }

  static Optional<TypeShape> shapeOf(
      final Project project, final String path, final String kind, final String prefix) {
    final SyntaxNode node = firstNode(project.trees().get(path), kind, prefix);
    return project
        .facts()
        .factsOf(project.units().get(path), List.of(node.span()), EnumSet.of(FactKind.SHAPE))
        .shapeOf(node.span());
  }

  static List<TypeRef> supertypesOf(
      final Project project, final String path, final String kind, final String prefix) {
    return shapeOf(project, path, kind, prefix).map(TypeShape::supertypes).orElse(List.of());
  }

  /** The first node of {@code kind} whose text starts with {@code prefix}. */
  static SyntaxNode firstNode(final SyntaxNode root, final String kind, final String prefix) {
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

  static String text(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    final String token = node.fields().get(TEXT);
    if (token != null) {
      text.append(token);
    }
    for (final SyntaxNode child : node.children()) {
      text.append(text(child));
    }
    return text.toString();
  }

  /** The type of the binding whose text starts with {@code prefix}. */
  static Optional<TypeRef> binding(final Project project, final String path, final String prefix) {
    return typeOf(project, path, BINDING, prefix);
  }
}
