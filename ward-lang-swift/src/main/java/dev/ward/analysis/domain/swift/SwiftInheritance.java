package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the declared name and the inherited type names of a Swift type declaration, by simple name,
 * for the project checks that follow inheritance and conformance across files.
 *
 * <p>shortcut: types are matched by simple name, so two types of that name in different modules or
 * enclosing types are one; upgrade with a project-level symbol table.
 */
final class SwiftInheritance {
  /** The kinds of declaration that can inherit from or conform to other types. */
  static final Set<String> KINDS =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "enum_declaration",
          "actor_declaration",
          "extension_declaration",
          "protocol_declaration"
      );

  private static final String NAME_SUFFIX = "_name";
  private static final String EXTENSION = "extension_declaration";
  private static final String EXTENDED = "type_identifier";
  private static final String INHERITANCE = "type_inheritance_clause";
  private static final String ITEM = "type_inheritance_item";
  private static final String GENERICS = "<[^<>]*>";

  private SwiftInheritance() {}

  /** The simple name of the type {@code declaration} declares or, for an extension, extends. */
  static Optional<String> nameOf(final SyntaxNode declaration) {
    final Optional<SyntaxNode> name =
        EXTENSION.equals(declaration.kind())
            ? SwiftTrees.child(declaration, EXTENDED)
            : declaration.children().stream()
                .filter(child -> child.kind().endsWith(NAME_SUFFIX))
                .findFirst();
    return name.map(SwiftInheritance::simple);
  }

  /** The simple names after the colon of the declaration, in source order. */
  static List<String> inherited(final SyntaxNode declaration) {
    final List<String> names = new ArrayList<>();
    SwiftTrees.child(declaration, INHERITANCE)
        .ifPresent(
            clause -> SwiftTrees.descendants(clause, ITEM).forEach(item -> names.add(simple(item)))
        );
    return names;
  }

  private static String simple(final SyntaxNode node) {
    String text = SwiftTrees.compact(node);
    String previous;
    do {
      previous = text;
      text = text.replaceAll(GENERICS, "");
    } while (!text.equals(previous));
    return text.substring(text.lastIndexOf('.') + 1);
  }
}
