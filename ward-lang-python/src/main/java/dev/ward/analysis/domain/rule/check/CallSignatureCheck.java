package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a call of a function or class that is defined once at the top of the same module, when
 * the call does not fit the signature: too many positional arguments, a keyword the signature does
 * not accept, or a keyword for a parameter that a positional argument already fills. Calls that
 * unpack arguments, callees with decorators or bases, and names defined twice are left alone,
 * because their signature is not known here.
 */
public final class CallSignatureCheck implements RuleCheck {

  /** The problem a call is reported for. */
  public enum Problem {
    /** More positional arguments than the signature takes. */
    TOO_MANY_POSITIONAL,
    /** A keyword argument the signature does not accept. */
    UNEXPECTED_KEYWORD,
    /** A keyword argument for a parameter that a positional argument already fills. */
    REDUNDANT_KEYWORD
  }

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the problem to report
   * @param message the message of each finding
   */
  public CallSignatureCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, SyntaxNode> definitions = ModuleDefinitions.of(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("atom_expr"))) {
      final SyntaxNode call = visit.node();
      final Optional<String> callee = CallSites.calleeName(call);
      if (callee.isPresent() && definitions.containsKey(callee.get())) {
        final Optional<SignatureReader.Signature> signature =
            SignatureReader.of(definitions.get(callee.get()));
        final CallSites.Arguments arguments = CallSites.arguments(call);
        if (signature.isPresent() && !arguments.unpacks() && violates(signature.get(), arguments)) {
          found.add(new RuleMatch(call.span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private boolean violates(
      final SignatureReader.Signature signature, final CallSites.Arguments arguments) {
    return switch (problem) {
      case TOO_MANY_POSITIONAL ->
          !signature.varargs() && arguments.positional() > signature.positional().size();
      case UNEXPECTED_KEYWORD ->
          arguments.keywords().stream().anyMatch(keyword -> !signature.accepts(keyword));
      case REDUNDANT_KEYWORD ->
          arguments.keywords().stream()
              .anyMatch(keyword -> signature.filledByPosition(keyword, arguments.positional()));
    };
  }
}
