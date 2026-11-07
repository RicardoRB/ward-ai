package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a hashed collection whose key type defines {@code equals(Object)} but no {@code
 * hashCode()} of its own, so the collection hashes by identity and equal keys land apart. The check
 * reads the key class from the type facts and reports nothing when they are unknown.
 */
public final class UnhashableKeyCheck implements RuleCheck {

  /** Where the hashed collection appears. */
  public enum Use {
    /** A {@code new HashSet<Key>()} or similar creation. */
    CREATION,
    /** A field, parameter or return type such as {@code Set<Key>}. */
    SIGNATURE
  }

  private static final String TYPE_TYPE = "typeType";
  private static final String CREATED_NAME = "createdName";
  private static final String TYPE_ARGUMENTS = "typeArguments";
  private static final String TYPE_ARGUMENT = "typeArgument";
  private static final String INTERFACE_TYPE = "classOrInterfaceType";
  private static final Set<String> HASHED =
      Set.of(
          "HashSet",
          "HashMap",
          "Hashtable",
          "LinkedHashSet",
          "LinkedHashMap",
          "WeakHashMap",
          "ConcurrentHashMap");
  private static final Set<String> SIGNATURE_TYPES =
      Set.of("HashSet", "HashMap", "Hashtable", "LinkedHashSet", "LinkedHashMap", "Set", "Map");
  private static final Set<String> SIGNATURE_PARENTS =
      Set.of("fieldDeclaration", "formalParameter", "typeTypeOrVoid");
  private static final String OBJECT = "java.lang.Object";
  private static final FactNeed NEED = new FactNeed(Set.of(TYPE_TYPE), Set.of(FactKind.MEMBERS));

  private final Use use;
  private final String message;

  /**
   * Creates the check.
   *
   * @param use where the hashed collection appears
   * @param message the message reported at the key type
   */
  public UnhashableKeyCheck(final Use use, final String message) {
    this.use = Objects.requireNonNull(use, "use");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    walk(root, null, types, matches);
    return List.copyOf(matches);
  }

  private void walk(
      final SyntaxNode node,
      final SyntaxNode parent,
      final UnitTypeFacts types,
      final List<RuleMatch> out) {
    final Optional<SyntaxNode> collection = collection(node, parent);
    collection
        .flatMap(UnhashableKeyCheck::keyType)
        .filter(key -> unhashable(types.membersOf(key.span())))
        .ifPresent(key -> out.add(CheckSupport.at(key.span(), message)));
    node.children().forEach(child -> walk(child, node, types, out));
  }

  /** Returns the node that names the collection type, if {@code node} is one to look at. */
  private Optional<SyntaxNode> collection(final SyntaxNode node, final SyntaxNode parent) {
    if (use == Use.CREATION) {
      return CREATED_NAME.equals(node.kind()) && named(node, HASHED)
          ? Optional.of(node)
          : Optional.empty();
    }
    final boolean inSignature =
        TYPE_TYPE.equals(node.kind())
            && parent != null
            && SIGNATURE_PARENTS.contains(parent.kind());
    return inSignature
        ? node.children().stream()
            .filter(child -> INTERFACE_TYPE.equals(child.kind()))
            .filter(child -> named(child, SIGNATURE_TYPES))
            .findFirst()
        : Optional.empty();
  }

  private static boolean named(final SyntaxNode node, final Set<String> names) {
    final String text = SyntaxWalk.text(node);
    final int arguments = text.indexOf('<');
    final String raw = arguments < 0 ? text : text.substring(0, arguments);
    return names.contains(raw.substring(raw.lastIndexOf('.') + 1));
  }

  private static Optional<SyntaxNode> keyType(final SyntaxNode collection) {
    return SyntaxWalk.descendants(collection).stream()
        .filter(node -> TYPE_ARGUMENTS.equals(node.kind()))
        .findFirst()
        .flatMap(
            arguments ->
                arguments.children().stream()
                    .filter(child -> TYPE_ARGUMENT.equals(child.kind()))
                    .findFirst())
        .flatMap(
            argument ->
                argument.children().stream()
                    .filter(child -> TYPE_TYPE.equals(child.kind()))
                    .findFirst());
  }

  /** Returns whether the type has its own equals(Object) but no hashCode() besides Object's. */
  private static boolean unhashable(final Optional<TypeMembers> members) {
    return members.isPresent()
        && members.get().methods().stream().anyMatch(UnhashableKeyCheck::isEquals)
        && members.get().methods().stream().noneMatch(UnhashableKeyCheck::isHashCode)
        && members.get().inheritedMethods().stream()
            .noneMatch(m -> isHashCode(m) && !OBJECT.equals(m.declaringType().name()));
  }

  private static boolean isEquals(final CallTarget method) {
    return "equals".equals(method.name())
        && method.parameters().size() == 1
        && OBJECT.equals(method.parameters().get(0).name());
  }

  private static boolean isHashCode(final CallTarget method) {
    return "hashCode".equals(method.name()) && method.parameters().isEmpty();
  }
}
