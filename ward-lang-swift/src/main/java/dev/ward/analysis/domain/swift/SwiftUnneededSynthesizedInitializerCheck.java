package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reports the only explicit initializer of a struct when it does exactly what the compiler would
 * synthesize: one parameter per stored property, in order, with the same name and type, whose body
 * assigns each property from its parameter, like SwiftLint's {@code unneeded_synthesized_initializer}.
 * Only stored properties without a default value count; a failable or throwing initializer is never
 * reported.
 */
public final class SwiftUnneededSynthesizedInitializerCheck implements RuleCheck {

  private static final String STRUCT = "struct_declaration";
  private static final String MEMBER = "struct_member";
  private static final String BODY_OF_STRUCT = "struct_body";
  private static final String LOCAL_NAME = "local_parameter_name";
  private static final String INITIALIZER = "initializer_declaration";
  private static final String HEAD = "initializer_head";
  private static final String PARAMETER_CLAUSE = "parameter_clause";
  private static final String PARAMETER = "parameter";
  private static final String BODY = "initializer_body";
  private static final String CODE_BLOCK = "code_block";
  private static final String STATEMENT = "statement";
  private static final String STATEMENT_LIST = "statements_impl";
  private static final String STATEMENTS = "statements";
  private static final String DECLARATION = "declaration";
  private static final String VARIABLE = "variable_declaration";
  private static final String CONSTANT = "constant_declaration";
  private static final String TYPE_ANNOTATION = "type_annotation";
  private static final String TYPE = "type_";
  private static final String IDENTIFIER = "declaration_identifier";
  private static final String INITIALIZER_KEYWORD = "init";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftUnneededSynthesizedInitializerCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode struct : SwiftTrees.descendants(root, STRUCT)) {
      explicitInitializer(struct)
          .filter(init -> isSynthesized(init, storedProperties(struct)))
          .ifPresent(init -> matches.add(SwiftTrees.at(init, message)));
    }
    return List.copyOf(matches);
  }

  /** The members declared directly in the struct's body, not those of nested types. */
  private static List<SyntaxNode> members(final SyntaxNode struct) {
    return SwiftTrees.child(struct, BODY_OF_STRUCT)
        .map(body -> body.children().stream().filter(child -> MEMBER.equals(child.kind())).toList())
        .orElse(List.of());
  }

  /** The only initializer of the struct, when there is exactly one. */
  private static Optional<SyntaxNode> explicitInitializer(final SyntaxNode struct) {
    final List<SyntaxNode> initializers = new ArrayList<>();
    for (final SyntaxNode member : members(struct)) {
      member.children().stream()
          .filter(child -> DECLARATION.equals(child.kind()))
          .flatMap(declaration -> SwiftTrees.child(declaration, INITIALIZER).stream())
          .forEach(initializers::add);
    }
    return initializers.size() == 1 ? Optional.of(initializers.get(0)) : Optional.empty();
  }

  /** The stored properties, as name and type text, in declaration order. */
  private static List<String[]> storedProperties(final SyntaxNode struct) {
    final List<String[]> properties = new ArrayList<>();
    for (final SyntaxNode member : members(struct)) {
      for (final SyntaxNode declaration : member.children()) {
        if (DECLARATION.equals(declaration.kind())) {
          for (final SyntaxNode binding : declaration.children()) {
            if ((VARIABLE.equals(binding.kind()) || CONSTANT.equals(binding.kind())) && isStoredWithoutDefault(binding)) {
              properties.add(new String[] {nameOf(binding), typeOf(binding)});
            }
          }
        }
      }
    }
    return properties;
  }

  private static boolean isStoredWithoutDefault(final SyntaxNode binding) {
    final boolean computed = binding.children().stream().anyMatch(child -> CODE_BLOCK.equals(child.kind()));
    return !computed && !SwiftTrees.hasToken(binding, "=");
  }

  private static String nameOf(final SyntaxNode binding) {
    return SwiftTrees.descendants(binding, IDENTIFIER).stream().findFirst().map(SwiftTrees::compact).orElse("");
  }

  private static String typeOf(final SyntaxNode binding) {
    return SwiftTrees.descendants(binding, TYPE_ANNOTATION).stream()
        .findFirst()
        .flatMap(annotation -> SwiftTrees.child(annotation, TYPE))
        .map(SwiftTrees::compact)
        .orElse("");
  }

  private static boolean isSynthesized(final SyntaxNode init, final List<String[]> properties) {
    if (!INITIALIZER_KEYWORD.equals(headText(init)) || SwiftTrees.hasToken(init, "throws")) {
      return false;
    }
    final List<SyntaxNode> parameters = parameters(init);
    if (parameters.size() != properties.size() || properties.isEmpty()) {
      return false;
    }
    for (int i = 0; i < properties.size(); i++) {
      final String[] property = properties.get(i);
      final SyntaxNode parameter = parameters.get(i);
      final String name = SwiftTrees.child(parameter, LOCAL_NAME).map(SwiftTrees::compact).orElse("");
      if (!property[0].equals(name) || !property[1].equals(typeOf(parameter))) {
        return false;
      }
    }
    return assignsEachFromItsParameter(init, properties);
  }

  private static String headText(final SyntaxNode init) {
    return SwiftTrees.child(init, HEAD).map(SwiftTrees::compact).orElse("");
  }

  private static List<SyntaxNode> parameters(final SyntaxNode init) {
    return SwiftTrees.child(init, PARAMETER_CLAUSE)
        .map(clause -> SwiftTrees.descendants(clause, PARAMETER))
        .orElse(List.of());
  }

  private static boolean assignsEachFromItsParameter(final SyntaxNode init, final List<String[]> properties) {
    final List<String> expected = new ArrayList<>();
    for (final String[] property : properties) {
      expected.add("self." + property[0] + "=" + property[0]);
    }
    final List<String> actual = new ArrayList<>();
    SwiftTrees.child(init, BODY)
        .flatMap(body -> SwiftTrees.child(body, CODE_BLOCK))
        .ifPresent(block -> statements(block, actual));
    return expected.equals(actual);
  }

  private static void statements(final SyntaxNode node, final List<String> out) {
    for (final SyntaxNode child : node.children()) {
      if (STATEMENT.equals(child.kind())) {
        out.add(SwiftTrees.compact(child));
      } else if (STATEMENTS.equals(child.kind()) || STATEMENT_LIST.equals(child.kind())) {
        statements(child, out);
      }
    }
  }
}
