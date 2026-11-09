package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call of a module function whose plain variable arguments name exactly the parameters of
 * the function, in another order, like Pylint {@code arguments-out-of-order}. Only calls with
 * positional name arguments only, to a function with positional parameters only, are checked.
 */
public final class ArgumentOrderCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern NAME = Pattern.compile("^[A-Za-z_]\\w*$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ArgumentOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, SyntaxNode> definitions = ModuleDefinitions.of(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final Optional<PlainCall> call = PlainCall.of(SyntaxWalk.text(visit.node()));
      if (call.isPresent() && outOfOrder(definitions.get(call.get().callee()), call.get())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean outOfOrder(final SyntaxNode definition, final PlainCall call) {
    if (definition == null || !SyntaxKinds.FUNCDEF.equals(definition.kind())) {
      return false;
    }
    final Optional<MethodSignature> signature = MethodSignature.of(definition);
    final List<String> arguments = ArgumentList.split(call.arguments());
    return signature.isPresent()
        && onlyPositional(signature.get())
        && !arguments.isEmpty()
        && arguments.stream().allMatch(argument -> NAME.matcher(argument).matches())
        && sameNames(arguments, signature.get().positionalNames())
        && !arguments.equals(signature.get().positionalNames());
  }

  private static boolean onlyPositional(final MethodSignature signature) {
    return !signature.varargs() && !signature.kwargs() && signature.keywordOnly().isEmpty();
  }

  private static boolean sameNames(final List<String> arguments, final List<String> parameters) {
    return arguments.size() == parameters.size()
        && new HashSet<>(arguments).equals(new HashSet<>(parameters));
  }
}
