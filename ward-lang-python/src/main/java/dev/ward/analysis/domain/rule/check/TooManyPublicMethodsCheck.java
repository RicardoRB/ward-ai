package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reports a class that defines more public methods than {@code max}, like Pylint R0904. */
public final class TooManyPublicMethodsCheck implements RuleCheck {

  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> METHODS = Set.of("funcdef");
  private static final Pattern METHOD_NAME = Pattern.compile("^def(\\w+)");

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest allowed number of public methods
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public TooManyPublicMethodsCheck(final int max, final String message) {
    this.max = max;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final int count = publicMethods(visit.node());
      if (count > max) {
        found.add(new RuleMatch(visit.node().span(), fill(count)));
      }
    }
    return List.copyOf(found);
  }

  private static int publicMethods(final SyntaxNode cls) {
    int count = 0;
    for (final SyntaxNode method :
        FunctionBodies.own(cls, METHODS, Set.of("funcdef", "classdef"))) {
      final Matcher name = METHOD_NAME.matcher(SyntaxWalk.text(method));
      if (name.find() && !name.group(1).startsWith("_")) {
        count++;
      }
    }
    return count;
  }

  private String fill(final int count) {
    return message.replace("{value}", String.valueOf(count)).replace("{max}", String.valueOf(max));
  }
}
