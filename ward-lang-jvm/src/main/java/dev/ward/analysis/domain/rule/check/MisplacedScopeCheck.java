package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a scope annotation, such as {@code @Singleton}, on a parameter or on an injection site (a
 * member that also has {@code @Inject}). A scope belongs on a class or on a binding method, so it
 * does nothing here.
 */
public final class MisplacedScopeCheck implements RuleCheck {

  private static final Pattern SCOPE =
      Pattern.compile(
          "^@(?:[\\w.]*\\.)?(?:Singleton|Reusable|SessionScoped|RequestScoped|ApplicationScoped)"
              + "\\b");
  private static final Pattern INJECT = Pattern.compile("^@(?:[\\w.]*\\.)?Inject\\b");
  private static final String PARAMETER = "formalParameter";
  private static final String MEMBER = "classBodyDeclaration";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the declaration
   */
  public MisplacedScopeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(PARAMETER, MEMBER))) {
      final List<String> annotations = AnnotationTexts.of(visit.node());
      final boolean scoped = annotations.stream().anyMatch(a -> SCOPE.matcher(a).find());
      final boolean injected = annotations.stream().anyMatch(a -> INJECT.matcher(a).find());
      if (scoped && (PARAMETER.equals(visit.node().kind()) || injected)) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }
}
