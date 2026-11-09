package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each function that takes more positional parameters than {@code max}, like Pylint {@code
 * too-many-positional-arguments} and Ruff {@code PLR0917}. Parameters after a {@code *} or {@code
 * **} marker are not positional, and a leading {@code self} or {@code cls} is not counted.
 */
public final class PositionalParameterCountCheck implements RuleCheck {

  private static final Set<String> KINDS = Set.of("funcdef");
  private static final Set<String> MARKERS = Set.of("*", "**");
  private static final String TFPDEF = "tfpdef";
  private static final String PARAMETERS = "parameters";
  private static final String TYPED_ARGS = "typedargslist";
  private static final Pattern RECEIVER = Pattern.compile("(?:self|cls)(?::.*)?");

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest allowed number of positional parameters
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public PositionalParameterCountCheck(final int max, final String message) {
    this.max = max;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, KINDS)) {
      final int count = positional(visit.node());
      if (count > max) {
        found.add(new RuleMatch(visit.node().span(), fill(count)));
      }
    }
    return List.copyOf(found);
  }

  private String fill(final int count) {
    return message.replace("{value}", String.valueOf(count)).replace("{max}", String.valueOf(max));
  }

  private static int positional(final SyntaxNode function) {
    int count = 0;
    boolean first = true;
    for (final SyntaxNode parameter : parameters(function)) {
      if (isToken(parameter) && MARKERS.contains(SyntaxWalk.text(parameter))) {
        break;
      }
      if (TFPDEF.equals(parameter.kind())) {
        count += counts(first, parameter) ? 1 : 0;
        first = false;
      }
    }
    return count;
  }

  private static boolean counts(final boolean first, final SyntaxNode parameter) {
    return !(first && RECEIVER.matcher(SyntaxWalk.text(parameter)).matches());
  }

  private static List<SyntaxNode> parameters(final SyntaxNode function) {
    for (final SyntaxNode child : function.children()) {
      if (PARAMETERS.equals(child.kind())) {
        return typedArgs(child);
      }
    }
    return List.of();
  }

  private static List<SyntaxNode> typedArgs(final SyntaxNode parameters) {
    for (final SyntaxNode child : parameters.children()) {
      if (TYPED_ARGS.equals(child.kind())) {
        return child.children();
      }
    }
    return List.of();
  }

  private static boolean isToken(final SyntaxNode node) {
    return node.children().isEmpty();
  }
}
