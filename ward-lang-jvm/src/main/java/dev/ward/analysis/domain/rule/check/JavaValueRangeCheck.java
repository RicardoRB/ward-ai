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
import java.util.regex.Pattern;

/**
 * Reports a comparison of an integer local or parameter with a literal whose outcome the enclosing
 * conditions already decide, as SpotBugs' UC_USELESS_CONDITION does for {@code if (x > 5) { if (x >
 * 3) ... }}. Only {@code if} and {@code while} conditions are tracked, combined with {@code &&},
 * {@code ||} and {@code !}. A variable assigned inside the region a condition guards is no longer
 * narrowed there, so a value the analysis cannot follow never produces a finding.
 */
public final class JavaValueRangeCheck implements RuleCheck {

  private static final Pattern LITERAL = Pattern.compile("^-?\\d{1,9}$");
  private static final Set<String> INTEGRAL = Set.of("int", "long", "short", "byte");
  private static final Set<String> COMPARISONS =
      Set.of("'>'", "'>='", "'<'", "'<='", "'=='", "'!='");
  private static final Set<String> DECLARATIONS =
      Set.of("formalParameter", "localVariableDeclaration");
  private static final String CLASS_BODY = "classBody";
  private static final int BINARY_PARTS = 3;
  private static final int CONDITION = 2;
  private static final int BRANCH = 4;
  private static final int ELSE_BRANCH = 6;

  private final String message;

  private record Comparison(String variable, String op, long constant) {}

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the comparison
   */
  public JavaValueRangeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of("methodDeclaration", "constructorDeclaration"))) {
      visit(visit.node(), Map.of(), tracked(visit.node()), matches);
    }
    return List.copyOf(matches);
  }

  private void visit(
      final SyntaxNode node,
      final Map<String, ValueRange> env,
      final Set<String> tracked,
      final List<RuleMatch> matches) {
    if (CLASS_BODY.equals(node.kind())) {
      return;
    }
    if (isGuard(node)) {
      guard(node, env, tracked, matches);
      return;
    }
    node.children().forEach(child -> visit(child, env, tracked, matches));
  }

  private static boolean isGuard(final SyntaxNode node) {
    return "statement".equals(node.kind())
        && node.children().size() > BRANCH
        && Set.of("'if'", "'while'").contains(node.children().get(0).kind());
  }

  private void guard(
      final SyntaxNode statement,
      final Map<String, ValueRange> env,
      final Set<String> tracked,
      final List<RuleMatch> matches) {
    final List<SyntaxNode> parts = statement.children();
    final SyntaxNode condition = parts.get(CONDITION);
    evaluate(condition, env, tracked, matches);
    final SyntaxNode body = parts.get(BRANCH);
    visit(body, narrowed(condition, true, env, tracked, body), tracked, matches);
    if (parts.size() > ELSE_BRANCH) {
      final SyntaxNode other = parts.get(ELSE_BRANCH);
      visit(other, narrowed(condition, false, env, tracked, other), tracked, matches);
    }
  }

  private void evaluate(
      final SyntaxNode node,
      final Map<String, ValueRange> env,
      final Set<String> tracked,
      final List<RuleMatch> matches) {
    final SyntaxNode condition = unwrap(node);
    final List<SyntaxNode> parts = condition.children();
    final String op = parts.size() == BINARY_PARTS ? parts.get(1).kind() : "";
    if ("'&&'".equals(op) || "'||'".equals(op)) {
      evaluate(parts.get(0), env, tracked, matches);
      final Map<String, ValueRange> next = narrow(parts.get(0), "'&&'".equals(op), env, tracked);
      evaluate(parts.get(2), next, tracked, matches);
    } else if (isNegation(condition)) {
      evaluate(parts.get(1), env, tracked, matches);
    } else {
      comparison(condition, tracked)
          .filter(
              cmp ->
                  env.getOrDefault(cmp.variable(), ValueRange.ANY)
                      .decides(cmp.op(), cmp.constant())
                      .isPresent())
          .ifPresent(
              cmp ->
                  matches.add(
                      CheckSupport.at(
                          condition.span(),
                          CheckSupport.fill(message, SyntaxWalk.text(condition), ""))));
    }
  }

  private Map<String, ValueRange> narrowed(
      final SyntaxNode condition,
      final boolean truth,
      final Map<String, ValueRange> env,
      final Set<String> tracked,
      final SyntaxNode region) {
    final Map<String, ValueRange> next = new HashMap<>(narrow(condition, truth, env, tracked));
    final Set<String> assigned = assigned(region);
    assigned.addAll(assigned(condition));
    next.keySet().removeAll(assigned);
    return next;
  }

  private Map<String, ValueRange> narrow(
      final SyntaxNode node,
      final boolean truth,
      final Map<String, ValueRange> env,
      final Set<String> tracked) {
    final SyntaxNode condition = unwrap(node);
    final List<SyntaxNode> parts = condition.children();
    final String op = parts.size() == BINARY_PARTS ? parts.get(1).kind() : "";
    if (isNegation(condition)) {
      return narrow(parts.get(1), !truth, env, tracked);
    }
    if (truth && "'&&'".equals(op) || !truth && "'||'".equals(op)) {
      return narrow(parts.get(2), truth, narrow(parts.get(0), truth, env, tracked), tracked);
    }
    final Optional<Comparison> cmp = comparison(condition, tracked);
    if (cmp.isEmpty()) {
      return env;
    }
    final Map<String, ValueRange> next = new HashMap<>(env);
    final String held = truth ? cmp.get().op() : ValueRange.negate(cmp.get().op());
    next.put(
        cmp.get().variable(),
        env.getOrDefault(cmp.get().variable(), ValueRange.ANY).holding(held, cmp.get().constant()));
    return next;
  }

  private static boolean isNegation(final SyntaxNode node) {
    return node.children().size() == 2 && "'!'".equals(node.children().get(0).kind());
  }

  private static SyntaxNode unwrap(final SyntaxNode node) {
    SyntaxNode current = node;
    while (current.children().size() == 1
        || current.children().size() == BINARY_PARTS
            && "'('".equals(current.children().get(0).kind())) {
      current = current.children().get(current.children().size() / 2);
    }
    return current;
  }

  private static Optional<Comparison> comparison(final SyntaxNode node, final Set<String> tracked) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() != BINARY_PARTS || !COMPARISONS.contains(parts.get(1).kind())) {
      return Optional.empty();
    }
    final String left = SyntaxWalk.text(parts.get(0));
    final String right = SyntaxWalk.text(parts.get(2));
    final String op = parts.get(1).kind();
    if (tracked.contains(left) && LITERAL.matcher(right).matches()) {
      return Optional.of(new Comparison(left, op, Long.parseLong(right)));
    }
    if (tracked.contains(right) && LITERAL.matcher(left).matches()) {
      return Optional.of(new Comparison(right, ValueRange.mirror(op), Long.parseLong(left)));
    }
    return Optional.empty();
  }

  private static Set<String> assigned(final SyntaxNode region) {
    final Set<String> names = new HashSet<>();
    SyntaxWalk.descendants(region).stream()
        .filter(Assignments::isAssignment)
        .forEach(node -> names.add(Assignments.target(node)));
    SyntaxWalk.descendants(region).stream()
        .filter(node -> node.children().size() == 2)
        .filter(
            node ->
                Set.of("'++'", "'--'").contains(node.children().get(1).kind())
                    || Set.of("'++'", "'--'").contains(node.children().get(0).kind()))
        .forEach(node -> names.add(SyntaxWalk.text(node).replaceAll("[+-]", "")));
    return names;
  }

  private static Set<String> tracked(final SyntaxNode method) {
    final Map<String, Integer> counts = new HashMap<>();
    final Set<String> integral = new HashSet<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(method)) {
      if (DECLARATIONS.contains(node.kind())) {
        final boolean wanted = ClassMembers.typeText(node).filter(INTEGRAL::contains).isPresent();
        SyntaxWalk.descendants(node).stream()
            .filter(id -> "variableDeclaratorId".equals(id.kind()))
            .map(SyntaxWalk::text)
            .forEach(
                name -> {
                  counts.merge(name, 1, Integer::sum);
                  if (wanted) {
                    integral.add(name);
                  }
                });
      }
    }
    integral.removeIf(name -> counts.get(name) > 1);
    return integral;
  }
}
