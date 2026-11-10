package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a use of a function, class or variable that comes textually before its declaration, like
 * ESLint {@code no-use-before-define}. The options choose the kinds to check. Parameters, imports
 * and types are not checked, and the exports and initializer rules of ESLint are not applied.
 */
public final class NodeUseBeforeDefineCheck implements RuleCheck {

  private static final String ENUM = "enumDeclaration";
  private static final String TYPE_ALIAS = "typeAliasDeclaration";
  private static final String INTERFACE = "interfaceDeclaration";
  private static final String TYPE_REFERENCE = "typeReference";
  private static final String EXPORT_ITEMS = "exportModuleItems";
  private static final String IDENTIFIER_LEAF = "Identifier";

  private final Kinds kinds;
  private final boolean allowNamedExports;
  private final Types types;
  private final String message;

  /** The kinds of declaration the rule checks, as ESLint's options name them. */
  public record Kinds(boolean functions, boolean classes, boolean variables) {}

  /** The TypeScript options: enums, type aliases and interfaces, and the type positions. */
  public record Types(boolean enums, boolean typedefs, boolean ignoreTypeReferences) {}

  /**
   * Creates the check.
   *
   * @param kinds the kinds of declaration to check
   * @param allowNamedExports whether a name listed in an export clause may be used before it
   * @param types the TypeScript options
   * @param message the message of each finding, with {@code {value}} as the name
   */
  public NodeUseBeforeDefineCheck(
      final Kinds kinds, final boolean allowNamedExports, final Types types, final String message) {
    this.kinds = Objects.requireNonNull(kinds, "kinds");
    this.allowNamedExports = allowNamedExports;
    this.types = Objects.requireNonNull(types, "types");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the use-before-define check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final Map<Integer, String> declared = Declarations.declaredKinds(root);
    final List<Span> typeSpans = Declarations.spansOf(root, TYPE_REFERENCE);
    final List<Span> exportSpans =
        allowNamedExports ? Declarations.spansOf(root, EXPORT_ITEMS) : List.of();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final NameReference reference : scopes.references()) {
      if (reference.resolved().isPresent()
          && checked(scopes, reference, reference.resolved().get(), declared)
          && precedes(reference, reference.resolved().get())
          && !(types.ignoreTypeReferences() && Declarations.within(reference.span(), typeSpans))
          && !Declarations.within(reference.span(), exportSpans)) {
        matches.add(new RuleMatch(reference.span(), message.replace("{value}", reference.name())));
      }
    }
    return List.copyOf(matches);
  }

  /** A use textually before the declaration; hoisted functions are checked this way too. */
  private static boolean precedes(final NameReference reference, final Binding binding) {
    return reference.span().startOffset() < binding.declaration().startOffset();
  }

  private boolean checked(
      final UnitScopes scopes,
      final NameReference reference,
      final Binding binding,
      final Map<Integer, String> declared) {
    return switch (binding.kind()) {
      case FUNCTION -> kinds.functions();
      case CLASS -> kinds.classes() || !upperFunction(scopes, reference, binding);
      case VARIABLE, CONSTANT -> kinds.variables() || !upperFunction(scopes, reference, binding);
      case TYPE -> typeChecked(declared.get(binding.declaration().startOffset()));
      default -> false;
    };
  }

  /**
   * Whether the reference is in a function nested in the scope of the declaration. ESLint lets the
   * classes and variables options skip only those references: a use in the declaring scope is kept.
   */
  private static boolean upperFunction(
      final UnitScopes scopes, final NameReference reference, final Binding binding) {
    Optional<Scope> current =
        scopes.scopes().stream().filter(scope -> scope.id() == reference.scope()).findFirst();
    while (current.isPresent() && current.get().id() != binding.scope()) {
      if (current.get().kind() == ScopeKind.FUNCTION) {
        return true;
      }
      current = current.flatMap(scopes::parent);
    }
    return false;
  }

  private boolean typeChecked(final String declaration) {
    if (ENUM.equals(declaration)) {
      return types.enums();
    }
    return (TYPE_ALIAS.equals(declaration) || INTERFACE.equals(declaration)) && types.typedefs();
  }

  /** The tree queries that find the declarations, the type positions and the export clauses. */
  private static final class Declarations {

    /**
     * The kind of each named declaration, by the offset of its name: enum, type alias or interface.
     */
    static Map<Integer, String> declaredKinds(final SyntaxNode root) {
      final Map<Integer, String> found = new HashMap<>();
      collectDeclared(root, found);
      return found;
    }

    static void collectDeclared(final SyntaxNode node, final Map<Integer, String> found) {
      if (Set.of(ENUM, TYPE_ALIAS, INTERFACE).contains(node.kind())) {
        nameOffset(node).ifPresent(offset -> found.put(offset, node.kind()));
      }
      node.children().forEach(child -> collectDeclared(child, found));
    }

    static Optional<Integer> nameOffset(final SyntaxNode node) {
      if (node.children().isEmpty()) {
        return IDENTIFIER_LEAF.equals(node.kind())
            ? Optional.of(node.span().startOffset())
            : Optional.empty();
      }
      for (final SyntaxNode child : node.children()) {
        final Optional<Integer> found = nameOffset(child);
        if (found.isPresent()) {
          return found;
        }
      }
      return Optional.empty();
    }

    static List<Span> spansOf(final SyntaxNode node, final String kind) {
      final List<Span> spans = new ArrayList<>();
      collectSpans(node, kind, spans);
      return spans;
    }

    static void collectSpans(final SyntaxNode node, final String kind, final List<Span> spans) {
      if (kind.equals(node.kind())) {
        spans.add(node.span());
      }
      node.children().forEach(child -> collectSpans(child, kind, spans));
    }

    static boolean within(final Span reference, final List<Span> spans) {
      return spans.stream()
          .anyMatch(
              span ->
                  span.startOffset() <= reference.startOffset()
                      && reference.endOffset() <= span.endOffset());
    }
  }
}
