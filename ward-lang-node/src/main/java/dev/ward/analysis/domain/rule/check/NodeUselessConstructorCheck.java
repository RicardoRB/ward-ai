package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each TypeScript constructor that only forwards its parameters to {@code super}, like
 * typescript-eslint's no-useless-constructor. Parameter properties, decorated parameters, default
 * values, private and protected constructors, overload signatures and any body beyond the single
 * super call are left alone.
 */
public final class NodeUselessConstructorCheck implements RuleCheck {

  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String PARAMETERS = "formalParameterList";
  private static final String BODY = "functionBody";
  private static final String PARAMETER = "formalParameterArg";
  private static final String REST = "lastFormalParameterArg";
  private static final String ACCESSIBILITY = "accessibilityModifier";
  private static final String OPEN_BRACE = "'{'";
  private static final String IDENTIFIER = "Identifier";
  private static final Set<String> SHAPE_MODIFIERS =
      Set.of("public", "private", "protected", "readonly");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeUselessConstructorCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if (CONSTRUCTOR.equals(node.kind()) && useless(node)) {
      found.add(new RuleMatch(node.span(), message));
    }
    node.children().forEach(child -> collect(child, found));
  }

  /** Whether the constructor has a body, keeps the default visibility, and only forwards. */
  private static boolean useless(final SyntaxNode constructor) {
    final List<SyntaxNode> parts = NodeSyntax.code(constructor);
    if (parts.stream().noneMatch(part -> OPEN_BRACE.equals(part.kind()))) {
      return false;
    }
    if (parts.stream()
        .anyMatch(
            part -> ACCESSIBILITY.equals(part.kind()) && !"public".equals(NodeSyntax.text(part)))) {
      return false;
    }
    final Optional<List<String>> forwarded = forwardedParameters(parts);
    final String body =
        parts.stream()
            .filter(part -> BODY.equals(part.kind()))
            .findFirst()
            .map(NodeSyntax::text)
            .orElse("");
    if (forwarded.isEmpty()) {
      return false;
    }
    final List<String> args = forwarded.get();
    if (args.isEmpty()) {
      return body.isEmpty() || "super()".equals(body) || "super();".equals(body);
    }
    final String call = "super(" + String.join(",", args) + ")";
    return body.equals(call) || body.equals(call + ";");
  }

  /** The forwarded arguments of each parameter, or empty when a parameter is not plain. */
  private static Optional<List<String>> forwardedParameters(final List<SyntaxNode> parts) {
    final List<String> args = new ArrayList<>();
    for (final SyntaxNode part : parts) {
      if (PARAMETERS.equals(part.kind())) {
        for (final SyntaxNode parameter : NodeSyntax.code(part)) {
          if (parameter.children().isEmpty()) {
            continue;
          }
          final Optional<String> arg = forwardedParameter(parameter);
          if (arg.isEmpty()) {
            return Optional.empty();
          }
          args.add(arg.get());
        }
      }
    }
    return Optional.of(args);
  }

  /** The argument text a plain parameter forwards: its name, or the spread name for a rest one. */
  private static Optional<String> forwardedParameter(final SyntaxNode parameter) {
    if (!PARAMETER.equals(parameter.kind()) && !REST.equals(parameter.kind())) {
      return Optional.empty();
    }
    if (!plain(leaves(parameter))) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> name = firstIdentifier(parameter);
    if (name.isEmpty()) {
      return Optional.empty();
    }
    final String text = NodeSyntax.text(name.get());
    return Optional.of(REST.equals(parameter.kind()) ? "..." + text : text);
  }

  /** Whether the parameter has no modifier, decorator or default value. */
  private static boolean plain(final List<String> leaves) {
    return !leaves.isEmpty()
        && !SHAPE_MODIFIERS.contains(leaves.get(0))
        && !leaves.get(0).startsWith("@")
        && !leaves.contains("=")
        && !leaves.contains("@");
  }

  private static Optional<SyntaxNode> firstIdentifier(final SyntaxNode node) {
    if (IDENTIFIER.equals(node.kind())) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = firstIdentifier(child);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static List<String> leaves(final SyntaxNode node) {
    final List<String> out = new ArrayList<>();
    collectLeaves(node, out);
    return out;
  }

  private static void collectLeaves(final SyntaxNode node, final List<String> out) {
    if (node.children().isEmpty()) {
      out.add(node.fields().getOrDefault("text", ""));
      return;
    }
    node.children().forEach(child -> collectLeaves(child, out));
  }
}
