package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a parameter annotated {@code @Nullable} that its method only ever uses as the receiver of
 * a member access, so the method needs it to be non-null. A parameter that the body also compares,
 * passes on or assigns is left alone.
 */
public final class NullableParameterCheck implements RuleCheck {
  private static final String OPEN_BRACKET = "[";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the parameter name
   */
  public NullableParameterCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit method : SyntaxWalk.visits(root, Set.of("methodDeclaration"))) {
      final List<SyntaxNode> bodies =
          method.node().children().stream().filter(c -> "methodBody".equals(c.kind())).toList();
      for (final SyntaxNode parameter : SyntaxWalk.descendants(method.node())) {
        if ("formalParameter".equals(parameter.kind()) && nullable(parameter)) {
          report(parameter, bodies, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode parameter,
      final List<SyntaxNode> bodies,
      final List<RuleMatch> matches
  ) {
    final List<String> names = ClassMembers.fieldNames(parameter);
    if (names.size() != 1 || bodies.isEmpty()) {
      return;
    }
    final String name = names.get(0);
    final List<NullTokens.Occurrence> uses = NullTokens.uses(NullTokens.tokens(bodies), name);
    if (!uses.isEmpty()
        && uses.stream().allMatch(use -> use.use() == NullTokens.Use.RECEIVER)
        && !NullTokens.assigns(NullTokens.tokens(bodies), name)) {
      matches.add(CheckSupport.at(parameter.span(), CheckSupport.fill(message, name, "")));
    }
  }

  /**
   * Tells whether a {@code Nullable} annotation applies to the parameter itself: one on the
   * parameter or in front of its type when the type is no array (on an array it annotates the
   * component), one on the outermost array brackets, or one on the varargs ellipsis. An annotation
   * in a type argument or a qualified type's inner part annotates another type.
   */
  private static boolean nullable(final SyntaxNode parameter) {
    final Optional<SyntaxNode> type = NullSyntax.first(parameter, "typeType");
    final List<SyntaxNode> notes = new ArrayList<>(NullSyntax.annotations(parameter));
    final List<SyntaxNode> inType = type.map(NullSyntax::annotations).orElse(List.of());
    if (type.isPresent() && NullSyntax.isArray(type.get())) {
      notes.clear();
      notes.addAll(outerBrackets(type.get()));
    } else {
      notes.addAll(inType);
    }
    parameter.children().stream().filter(c -> "annotation".equals(c.kind())).forEach(notes::add);
    return notes.stream().anyMatch(note -> "Nullable".equals(NullSyntax.simpleName(note)));
  }

  /** The annotations between the element type and the first array bracket of {@code type}. */
  private static List<SyntaxNode> outerBrackets(final SyntaxNode type) {
    final List<SyntaxNode> out = new ArrayList<>();
    boolean element = false;
    for (final SyntaxNode child : type.children()) {
      if (OPEN_BRACKET.equals(SyntaxWalk.text(child))) {
        break;
      }
      if (element && "annotation".equals(child.kind())) {
        out.add(child);
      }
      element |= "classOrInterfaceType".equals(child.kind()) || "primitiveType".equals(
          child.kind()
      );
    }
    return out;
  }
}
