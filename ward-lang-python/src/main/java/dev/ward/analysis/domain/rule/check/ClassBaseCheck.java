package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a class whose method resolution order is inconsistent, like Pylint {@code
 * inconsistent-mro}, or that inherits from a literal or a module function, like {@code
 * inherit-non-class}. A module-level name counts as a literal only when the module assigns it once,
 * to a literal, and does not define it otherwise.
 */
public final class ClassBaseCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** The C3 linearization of the class's bases does not exist. */
    INCONSISTENT_MRO,
    /** A base is a literal value or a function, not a class. */
    NON_CLASS_BASE
  }

  private static final Set<String> EXPRESSIONS = Set.of("expr_stmt");
  private static final Pattern BINDING = Pattern.compile("^(\\w+)=(.+)$");
  private static final Pattern LITERAL =
      Pattern.compile("^(?:[-+]?\\d[\\w.]*|'[^']*'|\"[^\"]*\"|None|True|False|\\[.*\\]|\\{.*\\})$");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public ClassBaseCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final PythonClassModel model = PythonClassModel.of(root);
    return switch (problem) {
      case INCONSISTENT_MRO -> inconsistent(model);
      case NON_CLASS_BASE -> nonClass(root, model);
    };
  }

  private List<RuleMatch> inconsistent(final PythonClassModel model) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final PythonClassModel.ClassInfo info : model.classes().values()) {
      if (model.order(info.name()).status() == PythonClassModel.Status.INCONSISTENT) {
        found.add(new RuleMatch(info.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> nonClass(final SyntaxNode root, final PythonClassModel model) {
    final Map<String, SyntaxNode> definitions = ModuleDefinitions.of(root);
    final Set<String> literals = literalNames(root, definitions);
    final List<RuleMatch> found = new ArrayList<>();
    for (final PythonClassModel.ClassInfo info : model.classes().values()) {
      for (final String base : info.rawBases()) {
        if (isLiteral(base) || literals.contains(base) || isFunction(definitions, base)) {
          found.add(new RuleMatch(info.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private static boolean isLiteral(final String base) {
    return LITERAL.matcher(base).matches();
  }

  private static boolean isFunction(final Map<String, SyntaxNode> definitions, final String base) {
    final SyntaxNode definition = definitions.get(base);
    return definition != null && SyntaxKinds.FUNCDEF.equals(definition.kind());
  }

  /** The module-level names that the module binds once, to a literal, and nothing else. */
  private static Set<String> literalNames(
      final SyntaxNode root, final Map<String, SyntaxNode> definitions) {
    final Map<String, Integer> bindings = new HashMap<>();
    final Map<String, String> values = new HashMap<>();
    for (final SyntaxNode statement : FunctionBodies.ownScope(root, EXPRESSIONS)) {
      final Matcher binding = BINDING.matcher(SyntaxWalk.text(statement));
      if (binding.matches()) {
        bindings.merge(binding.group(1), 1, Integer::sum);
        values.put(binding.group(1), binding.group(2));
      }
    }
    final Set<String> literals = new HashSet<>();
    for (final Map.Entry<String, Integer> entry : bindings.entrySet()) {
      final String name = entry.getKey();
      if (entry.getValue() == 1
          && !definitions.containsKey(name)
          && LITERAL.matcher(values.get(name)).matches()) {
        literals.add(name);
      }
    }
    return literals;
  }
}
