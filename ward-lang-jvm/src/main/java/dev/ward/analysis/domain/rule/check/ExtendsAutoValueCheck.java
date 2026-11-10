package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a class that extends a class annotated {@code @AutoValue}, as Error Prone's
 * ExtendsAutoValue does, unless its name starts with {@code AutoValue_}, the generated subclass. A
 * superclass whose annotations are unknown is never reported.
 */
public final class ExtendsAutoValueCheck extends FactsCheck {

  private static final String AUTO_VALUE = "com.google.auto.value.AutoValue";
  private static final String GENERATED_PREFIX = "AutoValue_";
  private static final String EXTENDS = "'extends'";
  private static final FactNeed NEED =
      new FactNeed(Set.of(MemberDeclarations.CLASS), Set.of(FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the class; {@code {value}} is the superclass
   */
  public ExtendsAutoValueCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(clazz -> !ClassHeaders.name(clazz).startsWith(GENERATED_PREFIX))
        .flatMap(clazz -> reportOf(clazz, types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode clazz, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = clazz.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (EXTENDS.equals(parts.get(index).kind())) {
        final SyntaxNode superclass = parts.get(index + 1);
        final Optional<TypeMembers> members = types.membersOf(superclass.span());
        if (members.filter(known -> known.annotations().contains(AUTO_VALUE)).isPresent()) {
          return List.of(
              CheckSupport.at(
                  clazz.span(), CheckSupport.fill(message, SyntaxWalk.text(superclass), "")));
        }
      }
    }
    return List.of();
  }
}
