package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the name of each function against a pattern, as detekt's {@code FunctionNaming} does. A
 * factory function, named after the type it returns or builds, is allowed as the Kotlin conventions say.
 */
public final class KotlinFunctionNameCheck implements RuleCheck {
  private static final String FUNCTION = "functionDeclaration";
  private static final String NAME = "simpleIdentifier";
  private static final String TYPE = "type";
  private static final String COLON = ":";
  private static final String BODY = "functionBody";
  private static final Set<String> SKIP =
      Set.of(
          "modifierList",
          "functionModifierList",
          "annotation",
          "typeParameters",
          "receiverType",
          TYPE,
          "contextParameters"
      );

  private final Pattern pattern;
  private final String message;

  /**
   * Creates the check.
   *
   * @param pattern the regular expression the whole name must match
   * @param message the message; {@code {value}} is the name
   */
  public KotlinFunctionNameCheck(final String pattern, final String message) {
    this.pattern = Pattern.compile(Objects.requireNonNull(pattern, "pattern"));
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FUNCTION))) {
      final Optional<SyntaxNode> name = SyntaxWalk.firstDescendant(visit.node(), NAME, SKIP);
      if (name.isPresent()) {
        final String text = SyntaxWalk.text(name.get());
        if (!pattern.matcher(text).matches() && !isFactory(visit.node(), text)) {
          found.add(new RuleMatch(name.get().span(), CheckSupport.fill(message, text, "")));
        }
      }
    }
    return List.copyOf(found);
  }

  /** A factory returns the type of its name, declared or built by its expression body. */
  private static boolean isFactory(final SyntaxNode function, final String name) {
    return returnsItsName(function, name) || buildsItsName(function, name);
  }

  private static boolean buildsItsName(final SyntaxNode function, final String name) {
    final Pattern call = Pattern.compile("^=\\s*" + Pattern.quote(name) + "\\s*\\(");
    return KotlinDocs.childrenOf(function, BODY).stream()
        .anyMatch(body -> call.matcher(SyntaxWalk.spacedText(body)).find());
  }

  /** Tells whether the declared result type, without arguments or nullability, is the name. */
  private static boolean returnsItsName(final SyntaxNode function, final String name) {
    final List<SyntaxNode> parts = function.children();
    for (int index = 1; index < parts.size(); index++) {
      if (TYPE.equals(parts.get(index).kind())
          && COLON.equals(SyntaxWalk.text(parts.get(index - 1)))) {
        return name.equals(SyntaxWalk.text(parts.get(index)).replaceAll("[<?\\s].*$", ""));
      }
    }
    return false;
  }
}
