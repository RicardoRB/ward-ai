package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reports one NullAway-style nullness rule from the annotations and syntax of a single file. It
 * does not run a flow analysis: a name that the method checks against null, tests with {@code
 * instanceof}, or assigns is never reported, and a declaration in another file is unknown, so the
 * rules err toward silence.
 */
public final class NullnessCheck implements RuleCheck {
  private static final Map<String, NullFinder> FINDERS = finders();
  private static final FactNeed NEED = new FactNeed(NullSyntax.TYPES, Set.of(FactKind.ANNOTATIONS));

  private final NullFinder finder;
  private final String message;

  private NullnessCheck(final NullFinder finder, final String message) {
    this.finder = finder;
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Returns the names of the rules this check implements. */
  public static Set<String> names() {
    return FINDERS.keySet();
  }

  /**
   * Creates the check for the rule {@code name}.
   *
   * @param name one of {@link #names()}
   * @param message the message; {@code {value}} is the name, value or type the hit is about
   * @throws IllegalArgumentException if {@code name} is not a nullness rule
   */
  public static NullnessCheck of(final String name, final String message) {
    final NullFinder found = FINDERS.get(name);
    if (found == null) {
      throw new IllegalArgumentException("unknown nullness rule: " + name);
    }
    return new NullnessCheck(found, message);
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return matches(root, UnitTypeFacts.none());
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit
  ) {
    return matches(root, types);
  }

  /** Reads the package default from the facts: the scopes of the file's first type. */
  private List<RuleMatch> matches(final SyntaxNode root, final UnitTypeFacts facts) {
    final boolean packageMarked =
        SyntaxWalk.visits(root, NullSyntax.TYPES).stream()
            .findFirst()
            .map(visit -> NullPackages.marked(facts.at(visit.node().span()).scopes()))
            .orElse(false);
    final NullFinder.Unit unit = new NullFinder.Unit(root, NullModel.types(root, packageMarked));
    return finder.find(unit).stream()
        .map(hit -> CheckSupport.at(hit.node().span(), CheckSupport.fill(message, hit.value(), "")))
        .toList();
  }

  private static Map<String, NullFinder> finders() {
    final Map<String, NullFinder> map = new TreeMap<>();
    NullValueFinders.each(map::put);
    NullCallFinders.each(map::put);
    NullGenericFinders.each(map::put);
    NullGenericOverrides.each(map::put);
    NullGenericUses.each(map::put);
    NullInitFinders.each(map::put);
    NullOverrideFinders.each(map::put);
    NullContracts.each(map::put);
    NullMiscFinders.each(map::put);
    return Map.copyOf(map);
  }
}
