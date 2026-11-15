package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The type that a variable or field was created as, when the body shows it: {@code Base b = new
 * Impl()} makes {@code b.run()} a call of {@code Impl}'s method, not of the abstract {@code Base}.
 * A name counts only while every value assigned to it in the body so far is a creation of one
 * class, so a branch that assigns two types, or a value that is not a creation, leaves it unknown.
 *
 * <p>shortcut: the assignments are read in source order, not by flow, so a loop that creates
 * another type after the call can still see the first; upgrade by reading the reaching definitions
 * of the receiver.
 */
final class JavaCreationTypes {

  /** The prefix of the annotation that gives a call the type its receiver was created as. */
  static final String MARK = "ward:created-as:";

  private static final String UNKNOWN = "";
  private static final String THIS = "this.";
  private static final String VARIABLE_INITIALIZER = "variableInitializer";

  private final JavaTypeNames types;
  private final Map<String, String> created = new HashMap<>();

  JavaCreationTypes(final JavaTypeNames types) {
    this.types = types;
  }

  /** Starts a name with the creation of its declaration, or as unknown when there is none. */
  void declare(final String name, final SyntaxNode initializer) {
    created.put(name, initializer == null ? UNKNOWN : createdBy(initializer).orElse(UNKNOWN));
  }

  /**
   * Records an assigned value; two different types, or a value that is no creation, make it
   * unknown.
   */
  void assign(final String name, final SyntaxNode value) {
    created.merge(
        name, createdBy(value).orElse(UNKNOWN), (old, now) -> old.equals(now) ? old : UNKNOWN);
  }

  /** Records the creation of a final field, which no other body can assign again. */
  void field(final String name, final SyntaxNode initializer) {
    createdBy(initializer)
        .ifPresent(
            type -> {
              created.put(name, type);
              created.put(THIS + name, type);
            });
  }

  /** The annotations of a call: the creation type of its receiver, when it is known. */
  List<String> annotations(final SyntaxNode receiver, final String qualifier) {
    final Optional<String> type =
        createdBy(receiver).or(() -> Optional.ofNullable(created.get(qualifier)));
    return type.filter(name -> !name.isEmpty()).map(name -> List.of(MARK + name)).orElse(List.of());
  }

  private Optional<String> createdBy(final SyntaxNode value) {
    SyntaxNode node = value;
    if (VARIABLE_INITIALIZER.equals(node.kind()) && JavaFlowSyntax.parts(node).size() == 1) {
      node = JavaFlowSyntax.parts(node).get(0);
    }
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(JavaFlowSyntax.unwrap(node));
    return JavaCreationSites.isCreation(parts)
        ? JavaCreationSites.createdType(types, parts.get(1))
        : Optional.empty();
  }
}
