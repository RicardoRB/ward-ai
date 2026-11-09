package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  private static final String STRING = "STRING";
  private static final String BLOCK = "block";
  private static final String TYPED_PARAMETER = "tfpdef";
  private static final Set<String> IMPLICIT_PARAMETERS = Set.of("self", "cls");
  private static final String ARGS_HEADER = "Args:";
  private static final String PRIVATE_PREFIX = "_";
  private static final String PERIOD = ".";
  private static final Pattern STRING_PREFIX = Pattern.compile("^[rRuUbB]*");
  private static final Pattern IDENTIFIER = Pattern.compile("\\w+");
  private static final Pattern ENTRY_NAME = Pattern.compile("^\\*{0,2}(\\w+)");
  private static final String TRIPLE_QUOTE = "\"\"\"";
  private static final String TRIPLE_APOSTROPHE = "'''";
  private static final int TRIPLE_LENGTH = 3;
  private static final int QUOTE_LENGTH = 1;
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
      case EMPTY -> atEach(docstrings(root), leaf -> body(leaf).isBlank());
      case TRAILING_PERIOD ->
          atEach(docstrings(root), leaf -> !body(leaf).isBlank() && !periodEnds(body(leaf)));
      case UNDOCUMENTED_PARAM -> undocumentedParams(root);
    };
  }

  private List<RuleMatch> missingModule(final SyntaxNode root) {
    final Optional<SyntaxNode> first = firstLeaf(root);
    if (first.isEmpty() || isDocstring(first.get())) {
      return List.of();
    }
    return List.of(new RuleMatch(first.get().span(), message));
  }

  private List<RuleMatch> missingIn(final SyntaxNode root, final String kind) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(kind))) {
      final SyntaxNode node = visit.node();
      if (!isPrivate(node) && docstring(node).isEmpty()) {
        found.add(new RuleMatch(node.span(), message));
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> undocumentedParams(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("funcdef"))) {
      final SyntaxNode node = visit.node();
      final Optional<SyntaxNode> doc = docstring(node);
      if (doc.isPresent()) {
        found.addAll(undocumented(node, doc.get()));
      }
    }
    return List.copyOf(found);
  }

  /** One finding per parameter of {@code function} that the Args section of its docstring lacks. */
  private List<RuleMatch> undocumented(final SyntaxNode function, final SyntaxNode doc) {
    final String text = body(doc);
    if (!text.contains(ARGS_HEADER)) {
      return List.of();
    }
    final Set<String> documented = documentedArguments(text);
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
        .filter(text -> IDENTIFIER.matcher(text).matches())
        .findFirst();
  }

  /** The names an Args section documents: the entries at the first entry's indentation. */
  private static Set<String> documentedArguments(final String text) {
    final Set<String> names = new HashSet<>();
    final String[] lines = text.split("\n");
    for (int header = 0; header < lines.length; header++) {
      if (lines[header].strip().equals(ARGS_HEADER)) {
        addEntries(lines, header, names);
        break;
      }
    }
    return names;
  }

  private static void addEntries(final String[] lines, final int header, final Set<String> names) {
    final int headerIndent = indent(lines[header]);
    int entryIndent = -1;
    for (int line = header + 1; line < lines.length; line++) {
      if (lines[line].isBlank()) {
        continue;
      }
      final int indent = indent(lines[line]);
      if (indent <= headerIndent) {
        return;
      }
      entryIndent = entryIndent < 0 ? indent : entryIndent;
      if (indent == entryIndent) {
        final Matcher entry = ENTRY_NAME.matcher(lines[line].strip());
        if (entry.find()) {
          names.add(entry.group(1));
        }
      }
    }
  }

  private static int indent(final String line) {
    int count = 0;
    while (count < line.length() && line.charAt(count) == ' ') {
      count++;
    }
    return count;
  }

  private static boolean periodEnds(final String text) {
    final String summary = text.strip().split("\n", 2)[0].strip();
    return summary.endsWith(PERIOD);
  }

  private static boolean isPrivate(final SyntaxNode scope) {
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(scope);
    return leaves.size() > NAME_INDEX
        && SyntaxWalk.text(leaves.get(NAME_INDEX)).startsWith(PRIVATE_PREFIX);
  }

  /** The docstring leaf of a module, class or function: its first leaf when that is a string. */
  private static Optional<SyntaxNode> docstring(final SyntaxNode scope) {
    return scope.children().stream()
        .filter(child -> BLOCK.equals(child.kind()))
        .findFirst()
        .flatMap(PythonDocstringCheck::firstLeaf)
        .filter(PythonDocstringCheck::isDocstring);
  }

  private static Optional<SyntaxNode> firstLeaf(final SyntaxNode block) {
    return SyntaxWalk.leaves(block).stream()
        .filter(leaf -> !SyntaxWalk.text(leaf).isBlank())
        .findFirst();
  }

  /** Every docstring of the unit: the module's and those of its classes and functions. */
  private static List<SyntaxNode> docstrings(final SyntaxNode root) {
    final List<SyntaxNode> found = new ArrayList<>();
    firstLeaf(root).filter(PythonDocstringCheck::isDocstring).ifPresent(found::add);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classdef", "funcdef"))) {
      docstring(visit.node()).ifPresent(found::add);
    }
    return found;
  }

  private static boolean isDocstring(final SyntaxNode leaf) {
    return STRING.equals(leaf.kind());
  }

  private List<RuleMatch> atEach(
      final List<SyntaxNode> leaves, final Predicate<SyntaxNode> reported) {
    return leaves.stream()
        .filter(reported)
        .map(leaf -> new RuleMatch(leaf.span(), message))
        .toList();
  }

  /** The text inside the quotes of a string token, without its prefix. */
  private static String body(final SyntaxNode leaf) {
    final String token = SyntaxWalk.text(leaf);
    final String text = STRING_PREFIX.matcher(token).replaceFirst("");
    final boolean triple = text.startsWith(TRIPLE_QUOTE) || text.startsWith(TRIPLE_APOSTROPHE);
    final int quote = triple ? TRIPLE_LENGTH : QUOTE_LENGTH;
    if (text.length() < 2 * quote) {
      return "";
    }
    return text.substring(quote, text.length() - quote);
  }
}
