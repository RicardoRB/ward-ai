package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a module-level name that is not UPPER_CASE, is assigned only once, and is assigned a
 * literal, like pylint's {@code invalid-name} for constants. A name assigned more than once is a
 * variable and is left alone, as pylint does, and so are functions and classes. Pylint holds only
 * names whose value infers to a constant to the constant style, so {@code app = Flask(__name__)} or
 * {@code urlpatterns = [...]} are variables. A tool configuration module ({@code *.conf.py}) is
 * not checked, because the tool fixes the names of its settings.
 */
public final class PythonConstantNameCheck implements RuleCheck {
  private static final String NAME = "{value}";
  private static final Pattern CONSTANT = Pattern.compile("[A-Z_][A-Z0-9_]*|__.*__");
  private static final String ASSIGNMENT = "expr_stmt";
  private static final String EQUALS = "=";
  private static final Set<String> OPENERS = Set.of("(", "[", "{");
  private static final Set<String> CLOSERS = Set.of(")", "]", "}");
  // A module such as gunicorn.conf.py holds settings that a tool reads by their lower-case names.
  private static final String TOOL_CONFIG = ".conf.py";

  /**
   * A token of a literal value: a number, a plain string, {@code True}, {@code False}, {@code
   * None}, a sign or a parenthesis. An f-string is not a constant for pylint.
   */
  private static final Pattern LITERAL_TOKEN =
      Pattern.compile(
          "\\d[\\w.+-]*|\\.\\d\\w*|[rRbBuU]{0,2}['\"].*|True|False|None|[-+()]",
          Pattern.DOTALL
      );

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public PythonConstantNameCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the module-constant-name check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView view,
      final SourceUnit unit
  ) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    if (unit.path().endsWith(TOOL_CONFIG)) {
      return found;
    }
    for (final Binding binding : scopes.module().bindings()) {
      if (isConstantCandidate(binding, scopes) && assignsLiteral(root, binding)) {
        found.add(new RuleMatch(binding.declaration(), message.replace(NAME, binding.name())));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  /** A variable of the module, not UPPER_CASE, with exactly one assignment. */
  private static boolean isConstantCandidate(final Binding binding, final UnitScopes scopes) {
    return binding.kind() == BindingKind.VARIABLE
        && !CONSTANT.matcher(binding.name()).matches()
        && assignments(binding, scopes) == 1;
  }

  // shortcut: only a literal counts as a constant, so a value pylint infers to a constant through
  // a name or a call is not checked; upgrade when the check can read inferred constant values.
  private static boolean assignsLiteral(final SyntaxNode root, final Binding binding) {
    final int offset = binding.declaration().startOffset();
    return SyntaxWalk.descendants(root).stream()
        .filter(node -> ASSIGNMENT.equals(node.kind()))
        .filter(node -> node.span().startOffset() <= offset && offset < node.span().endOffset())
        .findFirst()
        .map(PythonConstantNameCheck::valueIsLiteral)
        .orElse(false);
  }

  private static boolean valueIsLiteral(final SyntaxNode assignment) {
    final List<String> tokens =
        SyntaxWalk.leaves(assignment).stream().map(SyntaxWalk::text).toList();
    final int assign = lastAssignment(tokens);
    final List<String> value = tokens.subList(assign + 1, tokens.size());
    return assign >= 0
        && !value.isEmpty()
        && value.stream().allMatch(token -> LITERAL_TOKEN.matcher(token).matches());
  }

  /** The last {@code =} outside any bracket, so a keyword argument of a call is not the assignment. */
  private static int lastAssignment(final List<String> tokens) {
    int depth = 0;
    int last = -1;
    for (int index = 0; index < tokens.size(); index++) {
      final String token = tokens.get(index);
      if (OPENERS.contains(token)) {
        depth++;
      } else if (CLOSERS.contains(token)) {
        depth--;
      } else if (depth == 0 && EQUALS.equals(token)) {
        last = index;
      }
    }
    return last;
  }

  private static long assignments(final Binding binding, final UnitScopes scopes) {
    return scopes.usesOf(binding).stream()
        .filter(reference -> reference.access() == ReferenceAccess.WRITE)
        .count();
  }
}
