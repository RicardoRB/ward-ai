package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call of the builtin {@code exit()} or {@code quit()}, like Pylint's {@code
 * consider-using-sys-exit}. A unit that imports {@code exit} or {@code quit} from {@code sys} has
 * its calls exempt, since the name then is {@code sys.exit}.
 */
public final class SysExitCallCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern EXIT = Pattern.compile("^(exit|quit)\\(");
  private static final Set<String> EXIT_NAMES = Set.of("exit", "quit");
  private static final String SYS = "sys";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SysExitCallCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the sys-exit check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    if (scopes.scopes().stream().anyMatch(scope -> importsExit(scope.bindings()))) {
      return found;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      if (EXIT.matcher(SyntaxWalk.text(visit.node())).find()) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Whether one scope binds {@code exit} or {@code quit} to an import from {@code sys}. */
  static boolean importsExit(final List<Binding> bindings) {
    return bindings.stream()
        .anyMatch(
            binding ->
                binding.kind() == BindingKind.IMPORT
                    && EXIT_NAMES.contains(binding.name())
                    && binding
                        .origin()
                        .map(origin -> SYS.equals(origin.specifier()))
                        .orElse(false));
  }
}
