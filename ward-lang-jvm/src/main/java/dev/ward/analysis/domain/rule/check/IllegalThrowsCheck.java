package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method whose throws clause names one of the illegal exception types. Overriding methods
 * are skipped while {@code ignore-overridden-methods} holds, and so are the methods named in {@code
 * ignored-method-names}. The user options are {@code illegal-classes}, {@code ignored-method-names}
 * and {@code ignore-overridden-methods}.
 */
public final class IllegalThrowsCheck implements UnitCheck {

  private static final String QUALIFIED_NAME = "qualifiedName";
  private static final String QUALIFIED_NAME_LIST = "qualifiedNameList";
  private static final String CLASS_BODY_DECLARATION = "classBodyDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String ANNOTATION = "annotation";
  private static final String MODIFIER = "odifier";
  private static final Set<String> OVERRIDE = Set.of("@Override", "@java.lang.Override");

  private final Set<String> illegalClasses;
  private final Set<String> ignoredMethodNames;
  private final boolean ignoreOverridden;
  private final String message;

  /**
   * Creates the check.
   *
   * @param illegalClasses the exception type names that must not be declared
   * @param ignoredMethodNames the method names that are not checked
   * @param ignoreOverridden whether methods marked {@code @Override} are not checked
   * @param message the message; {@code {value}} is the illegal name
   */
  public IllegalThrowsCheck(
      final Set<String> illegalClasses,
      final Set<String> ignoredMethodNames,
      final boolean ignoreOverridden,
      final String message) {
    this.illegalClasses = Set.copyOf(illegalClasses);
    this.ignoredMethodNames = Set.copyOf(ignoredMethodNames);
    this.ignoreOverridden = ignoreOverridden;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("methodDeclaration"))) {
      if (!skipped(visit)) {
        addIllegal(visit.node(), matches);
      }
    }
    return List.copyOf(matches);
  }

  private boolean skipped(final SyntaxWalk.Visit visit) {
    final String name =
        visit.node().children().stream()
            .filter(c -> IDENTIFIER.equals(c.kind()))
            .map(SyntaxWalk::text)
            .findFirst()
            .orElse("");
    return ignoredMethodNames.contains(name) || ignoreOverridden && overrides(visit);
  }

  private static boolean overrides(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    for (int index = up.size() - 1; index >= 0; index--) {
      if (CLASS_BODY_DECLARATION.equals(up.get(index).kind())) {
        return hasOverride(up.get(index));
      }
    }
    return false;
  }

  private static boolean hasOverride(final SyntaxNode declaration) {
    return declaration.children().stream()
        .anyMatch(
            c ->
                ANNOTATION.equals(c.kind())
                    ? OVERRIDE.contains(SyntaxWalk.text(c))
                    : c.kind().endsWith(MODIFIER) && hasOverride(c));
  }

  private void addIllegal(final SyntaxNode method, final List<RuleMatch> matches) {
    for (final SyntaxNode list : method.children()) {
      if (QUALIFIED_NAME_LIST.equals(list.kind())) {
        list.children().stream()
            .filter(c -> QUALIFIED_NAME.equals(c.kind()))
            .filter(c -> illegalClasses.contains(SyntaxWalk.text(c)))
            .forEach(
                c ->
                    matches.add(
                        CheckSupport.at(
                            c.span(), CheckSupport.fill(message, SyntaxWalk.text(c), ""))));
      }
    }
  }
}
