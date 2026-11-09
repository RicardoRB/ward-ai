package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reports a {@code let} or {@code var} declared without a value whose variable is read and never
 * written, like ESLint {@code no-unassigned-vars}. The declaration is reported at its name.
 * Constants, declarations with an initializer and destructuring are not checked.
 */
public final class NodeUnassignedVarsCheck implements RuleCheck {

  private static final String VARIABLE_LIST = "variableDeclarationList";
  private static final String VARIABLE_STATEMENT = "variableStatement";
  private static final String VAR_MODIFIER = "varModifier";
  private static final String DECLARATION = "variableDeclaration";
  private static final String CONST = "const";
  private static final String EQUALS = "=";
  private static final String IDENTIFIER_LEAF = "Identifier";
  private static final String ASSIGNABLE = "assignable";
  private static final String ARRAY = "arrayLiteral";
  private static final String OBJECT = "objectLiteral";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding, with {@code {value}} as the name
   */
  public NodeUnassignedVarsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unassigned-vars check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final Map<Integer, Span> declarators = new HashMap<>();
    collectDeclarators(root, declarators);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final Span identifier : declarators.values()) {
      findBinding(scopes, identifier.startOffset())
          .filter(binding -> readOnly(scopes, binding))
          .ifPresent(
              binding ->
                  matches.add(
                      new RuleMatch(identifier, message.replace("{value}", binding.name()))));
    }
    return List.copyOf(matches);
  }

  private static void collectDeclarators(final SyntaxNode node, final Map<Integer, Span> out) {
    if ((VARIABLE_LIST.equals(node.kind()) || VARIABLE_STATEMENT.equals(node.kind()))
        && isVariableNode(node)) {
      for (final SyntaxNode declaration : declarations(node)) {
        declarator(declaration).ifPresent(span -> out.put(span.startOffset(), span));
      }
    }
    node.children().forEach(child -> collectDeclarators(child, out));
  }

  /** The declarations of a list, or of a statement's lists; the modifier may sit on either. */
  private static List<SyntaxNode> declarations(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    if (DECLARATION.equals(node.kind())) {
      found.add(node);
    }
    for (final SyntaxNode child : node.children()) {
      if (VARIABLE_LIST.equals(child.kind()) || DECLARATION.equals(child.kind())) {
        found.addAll(declarations(child));
      }
    }
    return found;
  }

  /**
   * A let or var declaration, which holds its modifier on the statement or on its list; not a
   * const.
   */
  private static boolean isVariableNode(final SyntaxNode node) {
    return node.children().stream()
        .anyMatch(child -> VAR_MODIFIER.equals(child.kind()) && !CONST.equals(LeafText.of(child)));
  }

  /** The identifier of a declaration without an initializer, when it is a plain name. */
  private static Optional<Span> declarator(final SyntaxNode declaration) {
    final boolean valued =
        declaration.children().stream()
            .anyMatch(child -> child.children().isEmpty() && EQUALS.equals(LeafText.of(child)));
    final boolean pattern =
        declaration.children().stream()
            .anyMatch(
                child ->
                    ARRAY.equals(child.kind())
                        || OBJECT.equals(child.kind())
                        || ASSIGNABLE.equals(child.kind())
                            && child.children().stream()
                                .anyMatch(c -> ARRAY.equals(c.kind()) || OBJECT.equals(c.kind())));
    if (valued || pattern) {
      return Optional.empty();
    }
    return firstIdentifier(declaration);
  }

  private static Optional<Span> firstIdentifier(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return IDENTIFIER_LEAF.equals(node.kind()) ? Optional.of(node.span()) : Optional.empty();
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<Span> found = firstIdentifier(child);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static Optional<Binding> findBinding(final UnitScopes scopes, final int offset) {
    return scopes.scopes().stream()
        .flatMap(scope -> scope.bindings().stream())
        .filter(
            binding ->
                binding.kind() == BindingKind.VARIABLE
                    && binding.declaration().startOffset() == offset)
        .findFirst();
  }

  /** Whether the binding is read somewhere and written nowhere; its own name is not a use. */
  private static boolean readOnly(final UnitScopes scopes, final Binding binding) {
    boolean read = false;
    for (final NameReference reference : scopes.references()) {
      final boolean declaration =
          reference.span().startOffset() == binding.declaration().startOffset();
      if (!declaration
          && reference.resolved().isPresent()
          && reference.resolved().get().equals(binding)) {
        if (reference.access() == ReferenceAccess.WRITE) {
          return false;
        }
        read |= reference.access() == ReferenceAccess.READ;
      }
    }
    return read;
  }
}
