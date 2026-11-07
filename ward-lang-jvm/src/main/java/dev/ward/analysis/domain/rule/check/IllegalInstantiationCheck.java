package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each {@code new} expression that creates an instance of one of the listed classes, as
 * Checkstyle's IllegalInstantiation does. The class is compared by the spelling the source uses,
 * simple or qualified, without its type arguments.
 */
public final class IllegalInstantiationCheck implements UnitCheck {

  private static final String NEW = "new";
  private static final String EXPRESSION = "expression";
  private static final String CREATOR_STOPS = "(<[{";

  private final Set<String> classes;
  private final String message;

  /**
   * Creates the check.
   *
   * @param classes the classes that may not be instantiated, simple or qualified
   * @param message the message of each finding; {@code {value}} stands for the class name
   */
  public IllegalInstantiationCheck(final Set<String> classes, final String message) {
    this.classes = Set.copyOf(Objects.requireNonNull(classes, "classes"));
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode expression : SyntaxWalk.descendants(root)) {
      if (EXPRESSION.equals(expression.kind()) && startsWithNew(expression)) {
        final String name = className(expression.children().get(1));
        if (classes.contains(name)) {
          matches.add(
              CheckSupport.at(
                  expression.children().get(0).span(), CheckSupport.fill(message, name, "")));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean startsWithNew(final SyntaxNode expression) {
    return expression.children().size() >= 2
        && NEW.equals(SyntaxWalk.text(expression.children().get(0)));
  }

  private static String className(final SyntaxNode creator) {
    final String text = SyntaxWalk.text(creator);
    int end = text.length();
    for (int index = 0; index < text.length(); index++) {
      if (CREATOR_STOPS.indexOf(text.charAt(index)) >= 0) {
        end = index;
        break;
      }
    }
    return text.substring(0, end);
  }
}
