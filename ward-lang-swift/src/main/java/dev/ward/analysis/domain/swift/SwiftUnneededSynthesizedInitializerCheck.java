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
 * assigns each property from its parameter, like SwiftLint's {@code
 * unneeded_synthesized_initializer}. Only stored properties without a default value count; a
 * failable or throwing initializer is never reported.
 */
public final class SwiftUnneededSynthesizedInitializerCheck implements RuleCheck {

  private static final String STRUCT = "struct_declaration";
  private static final String DECLARATION = "declaration";
  private static final String INITIALIZER = "initializer_declaration";
  private static final String HEAD = "initializer_head";
  private static final String BODY = "initializer_body";
  private static final String CODE_BLOCK = "code_block";
  private static final String STATEMENT = "statement";
  private static final String STATEMENT_LIST = "statements_impl";
  private static final String STATEMENTS = "statements";
  private static final String INITIALIZER_KEYWORD = "init";
  private static final int SINGLE = 1;

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
          .filter(init -> isSynthesized(init, SwiftMemberwiseShape.storedProperties(struct)))
          .ifPresent(init -> matches.add(SwiftTrees.at(init, message)));
    }
    return List.copyOf(matches);
  }

  /** The only initializer of the struct, when there is exactly one. */
  private static Optional<SyntaxNode> explicitInitializer(final SyntaxNode struct) {
    final List<SyntaxNode> initializers =
        SwiftMemberwiseShape.members(struct).stream()
            .flatMap(member -> member.children().stream())
            .filter(child -> DECLARATION.equals(child.kind()))
            .flatMap(declaration -> SwiftTrees.child(declaration, INITIALIZER).stream())
            .toList();
    return initializers.size() == SINGLE ? Optional.of(initializers.get(0)) : Optional.empty();
  }

  private static boolean isSynthesized(final SyntaxNode init, final List<String[]> properties) {
    if (!INITIALIZER_KEYWORD.equals(headText(init)) || SwiftTrees.hasToken(init, "throws")) {
      return false;
    }
    final List<SyntaxNode> parameters = SwiftMemberwiseShape.parameters(init);
    if (parameters.size() != properties.size() || properties.isEmpty()) {
      return false;
    }
    for (int i = 0; i < properties.size(); i++) {
      final String[] property = properties.get(i);
      final SyntaxNode parameter = parameters.get(i);
      if (!property[0].equals(SwiftMemberwiseShape.localNameOf(parameter))
          || !property[1].equals(SwiftMemberwiseShape.typeOf(parameter))) {
        return false;
      }
    }
    return assignsEachFromItsParameter(init, properties);
  }

  private static String headText(final SyntaxNode init) {
    return SwiftTrees.child(init, HEAD).map(SwiftTrees::compact).orElse("");
  }

  private static boolean assignsEachFromItsParameter(
      final SyntaxNode init, final List<String[]> properties) {
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
