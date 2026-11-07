package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reports an assignment, in a constructor, to a static field of the class that is not final, as
 * PMD's AssignmentToNonFinalStatic does. The field is named bare or with the class name; a field of
 * another class, or one reached through a local that hides it, is not seen.
 */
public final class AssignmentToNonFinalStaticCheck implements UnitCheck {

  private static final Pattern TARGET =
      Pattern.compile("^(?:(\\w+)\\.)?(\\w+)(?:[-+*/%&|^]|<<|>>>?)?=(?!=)");
  private static final String EXPRESSION = "expression";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the assignment; {@code {value}} is the field name
   */
  public AssignmentToNonFinalStaticCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportsOf(clazz).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode clazz) {
    final Set<String> statics =
        ClassMembers.members(clazz).stream()
            .filter(AssignmentToNonFinalStaticCheck::isStaticNonFinal)
            .flatMap(member -> ClassMembers.fieldNames(ClassMembers.declarationOf(member)).stream())
            .collect(Collectors.toSet());
    final String className = ClassMembers.nameOf(clazz);
    return ClassMembers.members(clazz).stream()
        .map(ClassMembers::declarationOf)
        .filter(declaration -> MemberDeclarations.CONSTRUCTOR.equals(declaration.kind()))
        .flatMap(constructor -> SyntaxWalk.visits(constructor, Set.of(EXPRESSION)).stream())
        .map(SyntaxWalk.Visit::node)
        .flatMap(expression -> reportOf(expression, statics, className).stream())
        .toList();
  }

  private Optional<RuleMatch> reportOf(
      final SyntaxNode expression, final Set<String> statics, final String className) {
    final Matcher target = TARGET.matcher(SyntaxWalk.text(expression));
    if (!target.find() || !statics.contains(target.group(2))) {
      return Optional.empty();
    }
    final String qualifier = target.group(1);
    if (qualifier != null && !qualifier.equals(className)) {
      return Optional.empty();
    }
    return Optional.of(
        CheckSupport.at(expression.span(), CheckSupport.fill(message, target.group(2), "")));
  }

  private static boolean isStaticNonFinal(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return MemberDeclarations.FIELD.equals(ClassMembers.declarationOf(member).kind())
        && modifiers.contains(MemberDeclarations.STATIC)
        && !modifiers.contains(MemberDeclarations.FINAL);
  }
}
