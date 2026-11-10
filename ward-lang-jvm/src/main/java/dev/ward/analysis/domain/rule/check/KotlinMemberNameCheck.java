package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a function or property named like its class or object, ignoring case, as detekt's {@code
 * member-name-equals-class-name} does. A companion object's function that builds the class (its
 * return type is the class, or it has an expression body) is a factory and is not reported.
 */
public final class KotlinMemberNameCheck implements RuleCheck {

  private static final Set<String> OWNERS = Set.of("classDeclaration", "objectDeclaration");
  private static final String CLASS = "classDeclaration";
  private static final String INTERFACE = "'interface'";
  private static final String COMPANION = "companionObject";
  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String FUNCTION = "functionDeclaration";
  private static final String PROPERTY = "propertyDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String VARIABLE = "variableDeclaration";
  private static final String SIMPLE = "simpleIdentifier";
  private static final String VALUE_PARAMETERS = "functionValueParameters";
  private static final String TYPE = "type";
  private static final String FUNCTION_BODY = "functionBody";
  private static final String BLOCK = "block";
  private static final String OVERRIDE = "override";
  private static final Set<String> MODIFIERS = Set.of("modifierList", "functionModifierList");

  private final boolean ignoreOverridden;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoreOverridden whether overriding members are not reported
   * @param message the message reported at the member's name
   */
  public KotlinMemberNameCheck(final boolean ignoreOverridden, final String message) {
    this.ignoreOverridden = ignoreOverridden;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, OWNERS)) {
      final SyntaxNode owner = visit.node();
      final Optional<SyntaxNode> name = child(owner, SIMPLE);
      final boolean isInterface =
          owner.children().stream().anyMatch(c -> INTERFACE.equals(c.kind()));
      if (name.isPresent() && !isInterface) {
        checkBody(owner, SyntaxWalk.text(name.get()), matches);
      }
    }
    return List.copyOf(matches);
  }

  private void checkBody(final SyntaxNode owner, final String name, final List<RuleMatch> out) {
    child(owner, CLASS_BODY)
        .ifPresent(
            classBody -> {
              for (final SyntaxNode member : members(classBody)) {
                checkMember(member, name, false, out);
              }
              if (CLASS.equals(owner.kind())) {
                checkCompanion(classBody, name, out);
              }
            });
  }

  private void checkCompanion(
      final SyntaxNode classBody, final String name, final List<RuleMatch> out) {
    for (final SyntaxNode member : members(classBody)) {
      child(member, COMPANION)
          .flatMap(companion -> child(companion, CLASS_BODY))
          .ifPresent(
              body -> {
                for (final SyntaxNode companionMember : members(body)) {
                  checkMember(companionMember, name, true, out);
                }
              });
    }
  }

  private void checkMember(
      final SyntaxNode member,
      final String className,
      final boolean companion,
      final List<RuleMatch> out) {
    final SyntaxNode declaration = memberDeclaration(member);
    if (declaration == null || (ignoreOverridden && isOverride(declaration))) {
      return;
    }
    final Optional<SyntaxNode> name = memberName(declaration);
    if (name.isEmpty()
        || !SyntaxWalk.text(name.get()).equalsIgnoreCase(className)
        || (companion
            && FUNCTION.equals(declaration.kind())
            && isFactory(declaration, className))) {
      return;
    }
    out.add(CheckSupport.at(name.get().span(), message));
  }

  private static SyntaxNode memberDeclaration(final SyntaxNode member) {
    if (!MEMBER.equals(member.kind())) {
      return null;
    }
    return member.children().stream()
        .filter(c -> FUNCTION.equals(c.kind()) || PROPERTY.equals(c.kind()))
        .findFirst()
        .orElse(null);
  }

  private static Optional<SyntaxNode> memberName(final SyntaxNode declaration) {
    if (FUNCTION.equals(declaration.kind())) {
      return child(declaration, IDENTIFIER);
    }
    return child(declaration, VARIABLE).flatMap(variable -> child(variable, SIMPLE));
  }

  private static boolean isFactory(final SyntaxNode function, final String className) {
    SyntaxNode returnType = null;
    boolean afterParameters = false;
    for (final SyntaxNode child : function.children()) {
      afterParameters = afterParameters || VALUE_PARAMETERS.equals(child.kind());
      if (afterParameters && TYPE.equals(child.kind())) {
        returnType = child;
      }
    }
    if (returnType != null) {
      return SyntaxWalk.text(returnType).equals(className);
    }
    return child(function, FUNCTION_BODY).flatMap(body -> child(body, BLOCK)).isEmpty();
  }

  private static boolean isOverride(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(c -> MODIFIERS.contains(c.kind()))
        .anyMatch(c -> SyntaxWalk.text(c).contains(OVERRIDE));
  }

  private static List<SyntaxNode> members(final SyntaxNode classBody) {
    return classBody.children().stream().filter(c -> MEMBER.equals(c.kind())).toList();
  }

  private static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst();
  }
}
