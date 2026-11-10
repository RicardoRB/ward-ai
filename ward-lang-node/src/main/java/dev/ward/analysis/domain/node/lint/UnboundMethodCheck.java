package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a method read from an object without being called, such as {@code const f = obj.method},
 * which loses the object as {@code this}. A property with the same name is not a method.
 */
public final class UnboundMethodCheck extends TypedCheck {

  private static final String MEMBER = "identifierName";
  private static final String DOT = ".";
  private static final String ARGUMENTS = "arguments";

  private final String message;

  /** Creates the check with the message of the rule. */
  public UnboundMethodCheck(final String message) {
    super();
    this.message = message;
  }

  @Override
  protected Set<FactKind> kinds() {
    return Set.of(FactKind.TYPE, FactKind.MEMBERS);
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final Set<Span> callees = callees(root);
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == SyntaxText.INFIX_PARTS
          && SyntaxText.isToken(parts.get(1), DOT)
          && MEMBER.equals(parts.get(2).kind())
          && !callees.contains(node.span())
          && isMethod(types, parts.get(0), SyntaxText.of(parts.get(2)))) {
        out.add(new RuleMatch(node.span(), message));
      }
    }
    return out;
  }

  /** Returns the spans of the expressions that are called, so their methods are bound. */
  private static Set<Span> callees(final SyntaxNode root) {
    final Set<Span> spans = new HashSet<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == 2 && ARGUMENTS.equals(parts.get(1).kind())) {
        spans.add(parts.get(0).span());
      }
    }
    return spans;
  }

  /** Reports whether the name is a method of the object, and not one of its fields. */
  private static boolean isMethod(
      final UnitTypeFacts types, final SyntaxNode object, final String name) {
    final Optional<TypeMembers> members = types.membersOf(object.span());
    return members.isPresent()
        && (hasMethod(members.get().methods(), name)
            || hasMethod(members.get().inheritedMethods(), name))
        && !hasField(members.get(), name);
  }

  private static boolean hasMethod(final List<CallTarget> methods, final String name) {
    return methods.stream().anyMatch(method -> name.equals(method.name()));
  }

  private static boolean hasField(final TypeMembers members, final String name) {
    final List<FieldRef> fields = new ArrayList<>(members.fields());
    fields.addAll(members.inheritedFields());
    return fields.stream().anyMatch(field -> name.equals(field.name()));
  }
}
