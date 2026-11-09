package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an object converted to a string in a template placeholder when its type neither declares
 * nor inherits its own {@code toString}, so it prints as {@code [object Object]}. A {@code
 * String(...)} call is not read yet: the grammar does not parse a bare identifier call as a call.
 */
public final class BaseToStringCheck extends TypedCheck {

  private static final String TEMPLATE_OPEN = "${";
  private static final String TO_STRING = "toString";

  private final String message;

  /** Creates the check with the message of the rule. */
  public BaseToStringCheck(final String message) {
    super();
    this.message = message;
  }

  @Override
  protected Set<FactKind> kinds() {
    return Set.of(FactKind.TYPE, FactKind.MEMBERS);
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode atom : SyntaxText.nodesOf(root, TEMPLATE)) {
      final List<SyntaxNode> parts = atom.children();
      if (parts.size() == SyntaxText.INFIX_PARTS
          && SyntaxText.isToken(parts.get(0), TEMPLATE_OPEN)) {
        reportIfBase(types, parts.get(1), out);
      }
    }
    return out;
  }

  private void reportIfBase(
      final UnitTypeFacts types, final SyntaxNode value, final List<RuleMatch> out) {
    if (isBase(types, value)) {
      out.add(new RuleMatch(value.span(), message));
    }
  }

  /** Reports an object type whose members are known and have no {@code toString}. */
  private static boolean isBase(final UnitTypeFacts types, final SyntaxNode value) {
    final Optional<TypeRef> type = typeOf(types, value);
    final Optional<TypeMembers> members = types.membersOf(value.span());
    return type.isPresent()
        && ValueKinds.of(type.get()) == ValueKind.OBJECT
        && members.isPresent()
        && !declaresToString(members.get());
  }

  private static boolean declaresToString(final TypeMembers members) {
    return hasToString(members.methods()) || hasToString(members.inheritedMethods());
  }

  private static boolean hasToString(final List<CallTarget> methods) {
    return methods.stream().anyMatch(method -> TO_STRING.equals(method.name()));
  }
}
