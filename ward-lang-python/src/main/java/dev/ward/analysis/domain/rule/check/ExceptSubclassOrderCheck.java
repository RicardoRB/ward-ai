package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an except clause that catches a class after a clause for one of its base classes in the
 * same try, like Pylint's {@code bad-except-order} (the subclass case): the later clause can never
 * run. The classes are the builtin exceptions and the classes of the same module; a name that is
 * neither gives no finding. Shortcut: a clause that catches a tuple of classes is not read.
 */
public final class ExceptSubclassOrderCheck implements RuleCheck {

  private static final Set<String> TRIES = Set.of("try_stmt");
  private static final Set<String> CLASSES = Set.of("classdef");
  private static final String EXCEPT_KEYWORD = "except";
  private static final String EXCEPT_CLAUSE = "except_clause";
  private static final Pattern IDENTIFIER = Pattern.compile("\\w+");
  private static final Pattern BASE_NAME = Pattern.compile(".*?(\\w+)$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ExceptSubclassOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, List<String>> moduleBases = moduleBases(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TRIES)) {
      addFindings(visit.node(), moduleBases, found);
    }
    return List.copyOf(found);
  }

  /** The base class names of each class of the module, by class name. */
  private static Map<String, List<String>> moduleBases(final SyntaxNode root) {
    final Map<String, List<String>> bases = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final SyntaxNode definition = visit.node();
      nameOf(definition).ifPresent(name -> bases.putIfAbsent(name, baseNames(definition)));
    }
    return bases;
  }

  /** Reports each clause whose class is a subclass of a class caught by an earlier clause. */
  private void addFindings(
      final SyntaxNode tryStatement,
      final Map<String, List<String>> moduleBases,
      final List<RuleMatch> found) {
    final List<String> caught = new ArrayList<>();
    for (final SyntaxNode clause : tryStatement.children()) {
      if (!EXCEPT_CLAUSE.equals(clause.kind())) {
        continue;
      }
      final SyntaxNode type = handlerType(clause);
      if (type == null || !IDENTIFIER.matcher(SyntaxWalk.text(type)).matches()) {
        continue;
      }
      final String name = ExceptionHierarchy.canonical(SyntaxWalk.text(type));
      final Set<String> ancestors = ExceptionHierarchy.ancestors(name, moduleBases);
      if (caught.stream().anyMatch(ancestors::contains)) {
        found.add(new RuleMatch(type.span(), message));
      }
      caught.add(name);
    }
  }

  /** The exception expression of a clause, the child after the {@code except} keyword, or null. */
  private static SyntaxNode handlerType(final SyntaxNode clause) {
    final List<SyntaxNode> children = clause.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (EXCEPT_KEYWORD.equals(SyntaxWalk.text(children.get(index)))) {
        final SyntaxNode next = children.get(index + 1);
        final String text = SyntaxWalk.text(next);
        return "as".equals(text) || ":".equals(text) ? null : next;
      }
    }
    return null;
  }

  private static java.util.Optional<String> nameOf(final SyntaxNode definition) {
    return definition.children().stream()
        .filter(child -> "name".equals(child.kind()) || "NAME".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text);
  }

  /** The simple names of the base classes in the class header, dotted names by their last part. */
  private static List<String> baseNames(final SyntaxNode definition) {
    final String text = SyntaxWalk.text(definition);
    final int open = text.indexOf('(');
    final int colon = text.indexOf(':');
    if (open < 0 || colon < open) {
      return List.of();
    }
    final List<String> names = new ArrayList<>();
    for (final String base :
        ThreadInstantiationCheck.topLevelArguments(
            text.substring(open + 1, text.lastIndexOf(')', colon)))) {
      final Matcher matcher = BASE_NAME.matcher(base);
      if (matcher.matches()) {
        names.add(matcher.group(1));
      }
    }
    return names;
  }
}
