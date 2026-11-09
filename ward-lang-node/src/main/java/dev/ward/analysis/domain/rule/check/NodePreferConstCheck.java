package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reports each {@code let} variable that is never assigned again after its declaration, like
 * ESLint's prefer-const. A variable declared without an initializer is reported at its single plain
 * assignment, when that assignment is in the variable's own scope. Exported bindings are left
 * alone.
 *
 * <p>Options: {@code destructuring} ({@code any} reports each destructured variable, {@code all}
 * only a destructuring whose variables all qualify) and {@code ignoreReadBeforeAssign}, which skips
 * a variable read before its single assignment instead of reporting its declaration.
 */
public final class NodePreferConstCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final int SINGLE_WRITE = 1;

  private final boolean allDestructured;
  private final boolean ignoreReadBeforeAssign;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allDestructured whether a destructuring is reported only when all its variables qualify
   * @param ignoreReadBeforeAssign whether a variable read before its single assignment is skipped
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public NodePreferConstCheck(
      final boolean allDestructured, final boolean ignoreReadBeforeAssign, final String message) {
    this.allDestructured = allDestructured;
    this.ignoreReadBeforeAssign = ignoreReadBeforeAssign;
    this.message = message;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the prefer-const check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    final Map<Span, List<Optional<RuleMatch>>> groups = new LinkedHashMap<>();
    for (final Scope scope : scopes.scopes()) {
      for (final Binding binding : scope.bindings()) {
        if (candidateKind(binding)) {
          collect(verdict(root, unit, scopes, binding), found, groups);
        }
      }
    }
    groups.values().forEach(group -> addGroup(group, found));
    found.sort(Comparator.comparingInt(match -> match.span().startOffset()));
    return List.copyOf(found);
  }

  private static boolean candidateKind(final Binding binding) {
    return binding.kind() == BindingKind.VARIABLE
        && binding.flags().contains(BindingFlag.LEXICAL)
        && !binding.flags().contains(BindingFlag.EXPORTED);
  }

  /** What the check decides for one binding: the destructuring it belongs to, and its finding. */
  private record Verdict(Optional<Span> destructuring, Optional<RuleMatch> match) {}

  private Verdict verdict(
      final SyntaxNode root,
      final SourceUnit unit,
      final UnitScopes scopes,
      final Binding binding) {
    final Optional<SyntaxNode> declaration =
        PreferConstSyntax.enclosingDeclaration(root, binding.declaration());
    final String pattern = declaration.map(PreferConstSyntax::patternText).orElse("");
    final boolean initialized =
        pattern.isEmpty()
            ? PreferConstSyntax.assignsAfter(
                binding.declaration().startOffset() + binding.name().length(), unit)
            : PreferConstSyntax.initializedAfterPattern(pattern);
    final Optional<Span> host =
        pattern.isEmpty() ? Optional.empty() : declaration.map(SyntaxNode::span);
    return new Verdict(host, report(binding, initialized, scopes, unit));
  }

  private static void collect(
      final Verdict verdict,
      final List<RuleMatch> found,
      final Map<Span, List<Optional<RuleMatch>>> groups) {
    if (verdict.destructuring().isEmpty()) {
      verdict.match().ifPresent(found::add);
    } else {
      groups
          .computeIfAbsent(verdict.destructuring().get(), key -> new ArrayList<>())
          .add(verdict.match());
    }
  }

  private void addGroup(final List<Optional<RuleMatch>> group, final List<RuleMatch> found) {
    if (!allDestructured || group.stream().allMatch(Optional::isPresent)) {
      group.stream().flatMap(Optional::stream).forEach(found::add);
    }
  }

  private Optional<RuleMatch> report(
      final Binding binding,
      final boolean initialized,
      final UnitScopes scopes,
      final SourceUnit unit) {
    final List<NameReference> uses = scopes.usesOf(binding);
    final List<NameReference> writes =
        uses.stream()
            .filter(use -> use.access() == ReferenceAccess.WRITE)
            .filter(use -> use.span().startOffset() != binding.declaration().startOffset())
            .toList();
    if (initialized) {
      return writes.isEmpty()
          ? Optional.of(match(binding, binding.declaration()))
          : Optional.empty();
    }
    if (writes.size() != SINGLE_WRITE) {
      return Optional.empty();
    }
    return singleWrite(binding, uses, writes.get(0), unit);
  }

  private Optional<RuleMatch> singleWrite(
      final Binding binding,
      final List<NameReference> uses,
      final NameReference write,
      final SourceUnit unit) {
    if (write.scope() != binding.scope()
        || !PreferConstSyntax.plainAssignment(write.span(), unit)) {
      return Optional.empty();
    }
    if (readBefore(uses, write.span())) {
      return ignoreReadBeforeAssign
          ? Optional.empty()
          : Optional.of(match(binding, binding.declaration()));
    }
    return Optional.of(match(binding, write.span()));
  }

  private static boolean readBefore(final List<NameReference> uses, final Span write) {
    return uses.stream()
        .anyMatch(
            use ->
                use.access() != ReferenceAccess.WRITE
                    && use.span().startOffset() < write.startOffset());
  }

  private RuleMatch match(final Binding binding, final Span at) {
    return new RuleMatch(at, message.replace(NAME, binding.name()));
  }
}
