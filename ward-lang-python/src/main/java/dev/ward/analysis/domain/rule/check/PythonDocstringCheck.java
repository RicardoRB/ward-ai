package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reports a docstring problem of a Python module, class or function: pylint's {@code
 * missing-module-docstring}, {@code missing-class-docstring}, {@code missing-function-docstring}
 * and {@code empty-docstring}, and pydocstyle's {@code D400} and {@code D417} in the Google
 * convention. A name that starts with an underscore needs no docstring.
 */
public final class PythonDocstringCheck implements RuleCheck {

  /** The problem the check reports, one per rule. */
  public enum Problem {
    /** The module has statements but no docstring. */
    MISSING_MODULE,
    /** A public class has no docstring. */
    MISSING_CLASS,
    /** A public function has no docstring. */
    MISSING_FUNCTION,
    /** A docstring has no text. */
    EMPTY,
    /** The summary line of a docstring does not end with a period. */
    TRAILING_PERIOD,
    /** A parameter is missing from the Args section of a function's docstring. */
    UNDOCUMENTED_PARAM
  }

  private static final String BLOCK = "block";
  private static final String TYPED_PARAMETER = "tfpdef";
  private static final Set<String> IMPLICIT_PARAMETERS = Set.of("self", "cls");
  private static final String PRIVATE_PREFIX = "_";
  private static final String ARGS_SECTION = "Args:";
  private static final String IDENTIFIER = "\\w+";
  private static final int NAME_INDEX = 1;

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the docstring problem to report
   * @param message the message of each finding
   */
  public PythonDocstringCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return switch (problem) {
      case MISSING_MODULE -> missingModule(root);
      case MISSING_CLASS -> missingIn(root, "classdef");
      case MISSING_FUNCTION -> missingIn(root, "funcdef");
      case EMPTY -> atEach(DocstringText.docstrings(root), DocstringText::isBlankBody);
      case TRAILING_PERIOD -> atEach(DocstringText.docstrings(root), DocstringText::missingPeriod);
      case UNDOCUMENTED_PARAM -> undocumentedParams(root);
    };
  }

  private List<RuleMatch> missingModule(final SyntaxNode root) {
    final Optional<SyntaxNode> first = DocstringText.firstLeaf(root);
    if (first.isEmpty() || DocstringText.isDocstring(first.get())) {
      return List.of();
    }
    return List.of(new RuleMatch(first.get().span(), message));
  }

  private List<RuleMatch> missingIn(final SyntaxNode root, final String kind) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(kind))) {
      final SyntaxNode node = visit.node();
      if (!isPrivate(node) && DocstringText.docstring(node).isEmpty()) {
        found.add(new RuleMatch(node.span(), message));
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> undocumentedParams(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("funcdef"))) {
      final SyntaxNode node = visit.node();
      final Optional<SyntaxNode> doc = DocstringText.docstring(node);
      if (doc.isPresent()) {
        found.addAll(undocumented(node, doc.get()));
      }
    }
    return List.copyOf(found);
  }

  /** One finding per parameter of {@code function} that the Args section of its docstring lacks. */
  private List<RuleMatch> undocumented(final SyntaxNode function, final SyntaxNode doc) {
    final String text = DocstringText.body(doc);
    if (!text.contains(ARGS_SECTION)) {
      return List.of();
    }
    final Set<String> documented = DocstringText.documented(text);
    final List<RuleMatch> found = new ArrayList<>();
    for (final String parameter : parameters(function)) {
      if (!documented.contains(parameter) && !IMPLICIT_PARAMETERS.contains(parameter)) {
        found.add(new RuleMatch(doc.span(), message));
      }
    }
    return found;
  }

  /** The names of the parameters of {@code function}, from its signature before the body. */
  private static List<String> parameters(final SyntaxNode function) {
    final List<String> names = new ArrayList<>();
    final int bodyStart = bodyStart(function);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(function, Set.of(TYPED_PARAMETER))) {
      final SyntaxNode parameter = visit.node();
      if (parameter.span().startOffset() < bodyStart) {
        firstIdentifier(parameter).ifPresent(names::add);
      }
    }
    return names;
  }

  private static int bodyStart(final SyntaxNode function) {
    return function.children().stream()
        .filter(child -> BLOCK.equals(child.kind()))
        .findFirst()
        .map(child -> child.span().startOffset())
        .orElse(Integer.MAX_VALUE);
  }

  private static Optional<String> firstIdentifier(final SyntaxNode parameter) {
    return SyntaxWalk.leaves(parameter).stream()
        .map(SyntaxWalk::text)
        .filter(text -> text.matches(IDENTIFIER))
        .findFirst();
  }

  private static boolean isPrivate(final SyntaxNode scope) {
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(scope);
    return leaves.size() > NAME_INDEX
        && SyntaxWalk.text(leaves.get(NAME_INDEX)).startsWith(PRIVATE_PREFIX);
  }

  private List<RuleMatch> atEach(
      final List<SyntaxNode> leaves, final Predicate<SyntaxNode> reported) {
    return leaves.stream()
        .filter(reported)
        .map(leaf -> new RuleMatch(leaf.span(), message))
        .toList();
  }
}
