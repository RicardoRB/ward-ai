package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reports a {@code for} loop whose update changes a variable that an enclosing {@code for} loop
 * declares in its init, as PMD's JumbledIncrementer does. A loop that also declares that name in its
 * own init is not reported.
 */
public final class JumbledIncrementerCheck implements UnitCheck {

  private static final String FOR_CONTROL = "forControl";
  private static final String FOR_INIT = "forInit";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final String VARIABLE_ID = "variableDeclaratorId";
  private static final Pattern WRITE =
      Pattern.compile(
          "(?:\\+\\+|--)(\\w+)|(\\w+)(?:\\+\\+|--|(?:[-+*/%&|^]|<<|>>>?)?=(?!=))");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the inner loop
   */
  public JumbledIncrementerCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(FOR_CONTROL)).stream()
        .filter(this::isJumbled)
        .map(visit -> CheckSupport.at(loopOf(visit).span(), message))
        .toList();
  }

  private static SyntaxNode loopOf(final SyntaxWalk.Visit visit) {
    return visit.ancestors().get(visit.ancestors().size() - 1);
  }

  private boolean isJumbled(final SyntaxWalk.Visit visit) {
    final Set<String> own = initNames(visit.node());
    final List<SyntaxNode> enclosing =
        visit.ancestors().subList(0, visit.ancestors().size() - 1);
    final Set<String> outer =
        enclosing.stream()
            .flatMap(ancestor -> ancestor.children().stream())
            .filter(child -> FOR_CONTROL.equals(child.kind()))
            .flatMap(control -> initNames(control).stream())
            .collect(Collectors.toSet());
    return updatedNames(visit.node()).anyMatch(name -> outer.contains(name) && !own.contains(name));
  }

  private static Set<String> initNames(final SyntaxNode control) {
    return control.children().stream()
        .filter(child -> FOR_INIT.equals(child.kind()))
        .flatMap(init -> SyntaxWalk.descendants(init).stream())
        .filter(node -> VARIABLE_ID.equals(node.kind()))
        .map(SyntaxWalk::text)
        .collect(Collectors.toSet());
  }

  /** Returns the names the update part writes; it is the last expression list of the control. */
  private static Stream<String> updatedNames(final SyntaxNode control) {
    final List<SyntaxNode> children = control.children();
    if (children.isEmpty() || !EXPRESSION_LIST.equals(children.get(children.size() - 1).kind())) {
      return Stream.empty();
    }
    return Stream.of(SyntaxWalk.text(children.get(children.size() - 1)).split(","))
        .map(WRITE::matcher)
        .filter(Matcher::lookingAt)
        .map(matcher -> matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
  }
}
