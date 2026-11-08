package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a non-empty void method that only works on its own parameters and locals, as SpotBugs'
 * UC_USELESS_VOID_METHOD does: it calls nothing, creates nothing and writes no field, so it has no
 * effect. A method annotated {@code @Override} is left alone.
 */
public final class JavaUselessVoidMethodCheck implements RuleCheck {

  private static final Pattern UPDATE =
      Pattern.compile("^(\\w+)(?:\\+\\+|--|(?:[-+*/%&|^]|<<|>>>?)?=(?!=)(.*))?;$");
  private static final Pattern RETURN = Pattern.compile("^return;$");
  private static final int VALUE = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public JavaUselessVoidMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of(MemberDeclarations.METHOD))) {
      final SyntaxNode method = visit.node();
      if (isUseless(method) && !overrides(visit)) {
        matches.add(
            CheckSupport.at(
                method.span(), CheckSupport.fill(message, MemberDeclarations.name(method), "")));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isUseless(final SyntaxNode method) {
    final boolean isVoid =
        JavaNodes.kid(method, "typeTypeOrVoid")
            .map(SyntaxWalk::text)
            .filter("void"::equals)
            .isPresent();
    final List<SyntaxNode> statements =
        JavaNodes.kid(method, "methodBody")
            .flatMap(body -> JavaNodes.kid(body, "block"))
            .map(JavaNodes::statements)
            .orElse(List.of());
    final Set<String> names = ClassShapes.declaredNames(method);
    return isVoid
        && statements.stream()
            .anyMatch(statement -> !RETURN.matcher(SyntaxWalk.text(statement)).matches())
        && statements.stream().allMatch(statement -> isLocalWork(statement, names));
  }

  private static boolean isLocalWork(final SyntaxNode statement, final Set<String> names) {
    final String text = SyntaxWalk.text(statement);
    if (RETURN.matcher(text).matches()) {
      return true;
    }
    if (JavaNodes.kid(statement, JavaNodes.LOCAL_VARIABLE).isPresent()) {
      return pure(text);
    }
    final Matcher update = UPDATE.matcher(text);
    return update.matches()
        && names.contains(update.group(1))
        && pure(update.group(VALUE) == null ? "" : update.group(VALUE));
  }

  private static boolean pure(final String text) {
    return !text.contains("(") && !text.contains("new") && !text.contains("->");
  }

  private static boolean overrides(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    return up.size() > 1
        && up.get(up.size() - 2).children().stream()
            .anyMatch(child -> SyntaxWalk.text(child).startsWith("@Override"));
  }
}
