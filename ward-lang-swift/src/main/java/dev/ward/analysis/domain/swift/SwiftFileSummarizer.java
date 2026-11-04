package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Summarizes a Swift file from its syntax tree: the modules it imports, the identifiers it uses and
 * the types it declares, with nested types qualified by their enclosing type.
 */
public final class SwiftFileSummarizer implements FileSummarizer {

  /** The specifier prefix of an import, so the resolver can tell modules from other references. */
  public static final String MODULE_PREFIX = "module:";

  /** The specifier prefix of a name use, which the resolver matches against declared types. */
  public static final String NAME_PREFIX = "name:";

  private static final String IDENTIFIER = "Identifier";
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
   * is both an export and a declaration, since Swift exports all of them to the module. Each
   * distinct identifier the file uses becomes a {@code name:<Identifier>} reference, except its own
   * declared names; comments and string literals hold no identifier nodes, so they add none.
   */
  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    final List<Reference> imports = new ArrayList<>();
    final List<Symbol> declared = new ArrayList<>();
    collect(tree, "", imports, declared);
    final Set<String> own =
        declared.stream().map(symbol -> lastComponent(symbol.name())).collect(Collectors.toSet());
    final Map<String, Span> names = new LinkedHashMap<>();
    collectNames(tree, own, names);
    names.forEach((name, span) -> imports.add(new Reference(NAME_PREFIX + name, span)));
    return new FileSummary(unit.path(), declared, imports, declared);
  }

  /** Records the first use of each identifier outside imports and the file's own names. */
  private static void collectNames(
      final SyntaxNode node, final Set<String> own, final Map<String, Span> names) {
    if (IMPORT.equals(node.kind())) {
      return;
    }
    final String name = node.fields().get(TEXT);
    if (IDENTIFIER.equals(node.kind()) && name != null && !own.contains(name)) {
      names.putIfAbsent(name, node.span());
    }
    for (final SyntaxNode child : node.children()) {
      collectNames(child, own, names);
    }
  }

  /** The last dot-separated part of a qualified name: the name a use of the type writes. */
  static String lastComponent(final String qualified) {
    return qualified.substring(qualified.lastIndexOf(QUALIFIER) + 1);
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
