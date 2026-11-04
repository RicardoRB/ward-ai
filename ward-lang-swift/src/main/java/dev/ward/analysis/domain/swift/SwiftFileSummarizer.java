package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Summarizes a Swift file from its syntax tree: the modules it imports and the types it declares,
 * with nested types qualified by their enclosing type.
 */
public final class SwiftFileSummarizer implements FileSummarizer {

  /** The specifier prefix of an import, so the resolver can tell modules from other references. */
  public static final String MODULE_PREFIX = "module:";

  private static final String IMPORT = "import_declaration";
  private static final String IMPORT_PATH = "import_path";
  private static final String IMPORT_IDENTIFIER = "import_path_identifier";
  private static final String TEXT = "text";
  private static final String BODY_SUFFIX = "_body";
  private static final String QUALIFIER = ".";
  private static final Set<String> TYPE_DECLARATIONS =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "enum_declaration",
          "protocol_declaration",
          "typealias_declaration");
  private static final Set<String> TYPE_NAMES =
      Set.of("class_name", "struct_name", "enum_name", "protocol_name", "typealias_name");

  /**
   * Summarizes the unit. Imports become {@code module:<Name>} references, and every declared type
   * is both an export and a declaration, since Swift exports all of them to the module.
   */
  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    final List<Reference> imports = new ArrayList<>();
    final List<Symbol> declared = new ArrayList<>();
    collect(tree, "", imports, declared);
    return new FileSummary(unit.path(), declared, imports, declared);
  }

  private static void collect(
      final SyntaxNode node,
      final String scope,
      final List<Reference> imports,
      final List<Symbol> declared) {
    if (IMPORT.equals(node.kind())) {
      importOf(node).ifPresent(imports::add);
      return;
    }
    final String inner = declare(node, scope, declared);
    for (final SyntaxNode child : node.children()) {
      collect(child, inner, imports, declared);
    }
  }

  /** Records the type that {@code node} declares and returns the scope of its members. */
  private static String declare(
      final SyntaxNode node, final String scope, final List<Symbol> declared) {
    if (!TYPE_DECLARATIONS.contains(node.kind())) {
      return scope;
    }
    final Optional<SyntaxNode> name = nameOf(node);
    if (name.isEmpty()) {
      return scope;
    }
    final String qualified =
        scope.isEmpty() ? textOf(name.get()) : scope + QUALIFIER + textOf(name.get());
    declared.add(new Symbol(qualified, name.get().span()));
    return qualified;
  }

  private static Optional<Reference> importOf(final SyntaxNode importDeclaration) {
    final Optional<SyntaxNode> path =
        importDeclaration.children().stream()
            .filter(child -> IMPORT_PATH.equals(child.kind()))
            .findFirst();
    return path.flatMap(SwiftFileSummarizer::moduleReference)
        .map(module -> new Reference(MODULE_PREFIX + module, importDeclaration.span()));
  }

  /** The first identifier of an import path is the module, whatever the path continues with. */
  private static Optional<String> moduleReference(final SyntaxNode importPath) {
    return importPath.children().stream()
        .filter(child -> IMPORT_IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SwiftFileSummarizer::textOf);
  }

  /** The name of a type declaration: the first name node, without entering its body. */
  private static Optional<SyntaxNode> nameOf(final SyntaxNode declaration) {
    for (final SyntaxNode child : declaration.children()) {
      if (TYPE_NAMES.contains(child.kind())) {
        return Optional.of(child);
      }
      if (!child.kind().endsWith(BODY_SUFFIX)) {
        final Optional<SyntaxNode> nested = nameOf(child);
        if (nested.isPresent()) {
          return nested;
        }
      }
    }
    return Optional.empty();
  }

  /** The source text of a subtree, concatenated from its terminals in order. */
  private static String textOf(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    appendText(node, text);
    return text.toString();
  }

  private static void appendText(final SyntaxNode node, final StringBuilder text) {
    final String token = node.fields().get(TEXT);
    if (token != null) {
      text.append(token);
    }
    for (final SyntaxNode child : node.children()) {
      appendText(child, text);
    }
  }
}
