package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an array use that is out of range, for a local array whose length is known from its
 * declaration ({@code new T[3]} or an initializer) and that is never assigned again: an index
 * outside the array, or an offset or length passed to {@code write} or {@code read} that does not
 * fit it. Only integer literals are compared.
 */
public final class ArrayRangeCheck implements UnitCheck {

  /** What the check looks for. */
  public enum Mode {
    /** {@code a[i]} with a literal index outside the array. */
    INDEX,
    /** {@code write(a, off, len)} or {@code read(a, off, len)} with an offset outside the array. */
    OFFSET,
    /** {@code write(a, off, len)} or {@code read(a, off, len)} with a length that does not fit. */
    LENGTH
  }

  private static final Set<String> BODIES = Set.of("methodDeclaration", "constructorDeclaration");
  private static final Set<String> TRANSFERS = Set.of("write", "read");
  private static final String EXPRESSION = "expression";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String INITIALIZER = "variableInitializer";
  private static final int INDEXED_PARTS = 4;
  private static final int TRANSFER_ARGUMENTS = 3;
  private static final int MAX_DIGITS = 9;
  private static final Pattern NEW_ARRAY = Pattern.compile("^new[\\w.<>]+\\[(\\d+)\\]$");
  private static final Pattern LITERAL = Pattern.compile("^-?\\d+$");

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param message the message reported at the use
   */
  public ArrayRangeCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BODIES)) {
      final Map<String, Integer> lengths = lengths(visit.node());
      if (lengths.isEmpty()) {
        continue;
      }
      for (final SyntaxNode node : SyntaxWalk.descendants(visit.node())) {
        final boolean bad =
            mode == Mode.INDEX ? badIndex(node, lengths) : badTransfer(node, lengths);
        if (bad) {
          matches.add(CheckSupport.at(node.span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  /**
   * Returns the length of each local array that is declared with a known length and not reassigned.
   */
  private static Map<String, Integer> lengths(final SyntaxNode body) {
    final Set<String> reassigned = new HashSet<>();
    Assignments.in(body).forEach(assignment -> reassigned.add(Assignments.target(assignment)));
    final Map<String, Integer> lengths = new HashMap<>();
    SyntaxWalk.descendants(body).stream()
        .filter(node -> DECLARATOR.equals(node.kind()))
        .forEach(
            declarator ->
                declaredLength(declarator)
                    .ifPresent(
                        length -> {
                          final String name = LocalMasks.name(declarator);
                          if (!reassigned.contains(name)) {
                            lengths.put(name, length);
                          }
                        }));
    return lengths;
  }

  private static Optional<Integer> declaredLength(final SyntaxNode declarator) {
    final Optional<SyntaxNode> init =
        declarator.children().stream().filter(c -> INITIALIZER.equals(c.kind())).findFirst();
    if (init.isEmpty()) {
      return Optional.empty();
    }
    final Matcher created = NEW_ARRAY.matcher(SyntaxWalk.text(init.get()));
    if (created.matches()) {
      return Optional.of(Integer.parseInt(created.group(1)));
    }
    return init.get().children().stream()
        .filter(c -> "arrayInitializer".equals(c.kind()))
        .findFirst()
        .map(
            array ->
                (int) array.children().stream().filter(c -> INITIALIZER.equals(c.kind())).count());
  }

  private static boolean badIndex(final SyntaxNode node, final Map<String, Integer> lengths) {
    final List<SyntaxNode> parts = node.children();
    if (!EXPRESSION.equals(node.kind())
        || parts.size() != INDEXED_PARTS
        || !"[".equals(SyntaxWalk.text(parts.get(1)))) {
      return false;
    }
    final Integer length = lengths.get(SyntaxWalk.text(parts.get(0)));
    final Optional<Integer> index = literal(parts.get(2));
    return length != null && index.isPresent() && (index.get() < 0 || index.get() >= length);
  }

  private boolean badTransfer(final SyntaxNode node, final Map<String, Integer> lengths) {
    final List<SyntaxNode> arguments = transferArguments(node);
    if (arguments.size() != TRANSFER_ARGUMENTS) {
      return false;
    }
    final Integer length = lengths.get(SyntaxWalk.text(arguments.get(0)));
    final Optional<Integer> offset = literal(arguments.get(1));
    final Optional<Integer> count = literal(arguments.get(2));
    if (length == null || offset.isEmpty() || count.isEmpty()) {
      return false;
    }
    final boolean offsetBad = offset.get() < 0 || offset.get() > length;
    return mode == Mode.OFFSET
        ? offsetBad
        : !offsetBad && (count.get() < 0 || offset.get() + (long) count.get() > length);
  }

  /** Returns the arguments of a call of {@code write} or {@code read}, or none for another node. */
  private static List<SyntaxNode> transferArguments(final SyntaxNode node) {
    final boolean transfer =
        "methodCall".equals(node.kind())
            && node.children().stream()
                .anyMatch(
                    c -> "identifier".equals(c.kind()) && TRANSFERS.contains(SyntaxWalk.text(c)));
    if (!transfer) {
      return List.of();
    }
    return SyntaxWalk.descendants(node).stream()
        .filter(n -> "expressionList".equals(n.kind()))
        .findFirst()
        .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
        .orElse(List.of());
  }

  private static Optional<Integer> literal(final SyntaxNode node) {
    final String text = SyntaxWalk.text(node);
    return LITERAL.matcher(text).matches() && text.length() < MAX_DIGITS
        ? Optional.of(Integer.parseInt(text))
        : Optional.empty();
  }
}
