package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reports each local export clause that exports only types, without {@code export type}, like
 * typescript-eslint's consistent-type-exports. A type is a local interface or type alias, or a
 * type-only import. Re-exports from another module, {@code export type} clauses and specifiers
 * written {@code type X} are left alone.
 */
public final class NodeTypeOnlyExportCheck implements RuleCheck {

  private static final String STATEMENT = "exportStatement";
  private static final String BLOCK = "exportFromBlock";
  private static final String ITEMS = "exportModuleItems";
  private static final String ALIAS = "exportAliasName";
  private static final String NAME = "moduleExportName";
  private static final String FROM = "importFrom";
  private static final String TYPE_MODIFIER = "typeOnlyModifier";
  private static final String PLURAL_NOUN = "exports";
  private static final String SINGULAR_NOUN = "export";
  private static final String PLURAL_VERB = "are";
  private static final String SINGULAR_VERB = "is";
  private static final int SINGLE = 1;

  private final String message;
  private final String partialMessage;

  /**
   * Creates the check.
   *
   * @param message the message of a clause whose exports are all types
   * @param partialMessage the message of a mixed clause; {@code {names}} lists the type-only names,
   *     {@code {noun}} is {@code export} or {@code exports} and {@code {verb}} is {@code is} or
   *     {@code are}, for the number of names
   */
  public NodeTypeOnlyExportCheck(final String message, final String partialMessage) {
    this.message = message;
    this.partialMessage = partialMessage;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the consistent-type-exports check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final Map<Integer, NameReference> resolved = new HashMap<>();
    for (final NameReference reference : scopes.references()) {
      if (reference.resolved().isPresent()) {
        resolved.put(reference.span().startOffset(), reference);
      }
    }
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, resolved, found);
    return List.copyOf(found);
  }

  private void collect(
      final SyntaxNode node,
      final Map<Integer, NameReference> resolved,
      final List<RuleMatch> out) {
    if (STATEMENT.equals(node.kind())) {
      clause(node, resolved).ifPresent(out::add);
    }
    node.children().forEach(child -> collect(child, resolved, out));
  }

  /** The finding of a local export clause that exports types, if the statement is one. */
  private Optional<RuleMatch> clause(
      final SyntaxNode statement, final Map<Integer, NameReference> resolved) {
    if (!localClause(statement)) {
      return Optional.empty();
    }
    final List<SyntaxNode> aliases = aliases(statement);
    final List<String> types = new ArrayList<>();
    int exports = 0;
    for (final SyntaxNode alias : aliases) {
      if (children(alias, TYPE_MODIFIER).isEmpty()) {
        exports++;
        typeName(alias, resolved).ifPresent(types::add);
      }
    }
    if (types.isEmpty()) {
      return Optional.empty();
    }
    final String text = types.size() == exports ? message : partial(types);
    return Optional.of(new RuleMatch(statement.span(), text));
  }

  /** Whether the statement is an {@code export {...}} clause without a module or a type keyword. */
  private static boolean localClause(final SyntaxNode statement) {
    final Optional<SyntaxNode> block = child(statement, BLOCK);
    return children(statement, TYPE_MODIFIER).isEmpty()
        && block.isPresent()
        && child(block.get(), FROM).isEmpty()
        && child(block.get(), ITEMS).isPresent();
  }

  private static List<SyntaxNode> aliases(final SyntaxNode statement) {
    final SyntaxNode block = child(statement, BLOCK).orElseThrow();
    return child(block, ITEMS).map(items -> children(items, ALIAS)).orElse(List.of());
  }

  /** The local name of an alias when the name resolves to a type-only binding. */
  private static Optional<String> typeName(
      final SyntaxNode alias, final Map<Integer, NameReference> resolved) {
    return child(alias, NAME)
        .map(name -> NodeSyntax.firstLeaf(name).span().startOffset())
        .map(resolved::get)
        .filter(
            reference -> reference.resolved().orElseThrow().flags().contains(BindingFlag.TYPE_ONLY))
        .map(NameReference::name);
  }

  private String partial(final List<String> types) {
    final boolean single = types.size() == SINGLE;
    return partialMessage
        .replace("{names}", TypeOnlyNames.quoted(types))
        .replace("{noun}", single ? SINGULAR_NOUN : PLURAL_NOUN)
        .replace("{verb}", single ? SINGULAR_VERB : PLURAL_VERB);
  }

  private static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }

  private static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }
}
