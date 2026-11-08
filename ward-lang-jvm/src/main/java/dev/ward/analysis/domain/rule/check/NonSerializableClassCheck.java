package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reports an instance field of a {@code Serializable} class whose declared type is a concrete class
 * that is not serializable, as PMD's NonSerializableClass does. A transient or static field is not
 * reported, nor is a class that writes or reads its own form. An interface, abstract or unresolved
 * field type is not judged, because a serializable value may stand behind it.
 */
public final class NonSerializableClassCheck extends FactsCheck {

  private static final String SERIALIZABLE = "java.io.Serializable";
  private static final String TYPE_TYPE = "typeType";
  private static final String TRANSIENT = "transient";
  private static final Set<String> CUSTOM_FORMS =
      Set.of("writeObject", "readObject", "writeReplace", "readResolve");
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(MemberDeclarations.CLASS, TYPE_TYPE),
          Set.of(FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field; {@code {value}} is its name
   */
  public NonSerializableClassCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(clazz -> serializable(clazz.span(), types) && !writesOwnForm(clazz))
        .flatMap(clazz -> ClassMembers.fieldMembers(clazz).stream())
        .filter(NonSerializableClassCheck::isInstanceField)
        .flatMap(member -> offending(member, types).stream())
        .toList();
  }

  private List<RuleMatch> offending(final SyntaxNode member, final UnitTypeFacts types) {
    final SyntaxNode field = ClassMembers.declarationOf(member);
    final boolean bad =
        field.children().stream()
            .filter(child -> TYPE_TYPE.equals(child.kind()))
            .findFirst()
            .map(type -> notSerializable(type, types))
            .orElse(false);
    return bad
        ? List.of(
            CheckSupport.at(
                field.span(),
                CheckSupport.fill(message, String.join(", ", ClassMembers.fieldNames(field)), "")))
        : List.of();
  }

  private static boolean serializable(final Span span, final UnitTypeFacts types) {
    return above(span, types).anyMatch(SERIALIZABLE::equals);
  }

  private static Stream<String> above(final Span span, final UnitTypeFacts types) {
    return Stream.concat(
        types.typeOf(span).map(TypeRef::name).stream(),
        types.membersOf(span).map(TypeMembers::ancestors).stream()
            .flatMap(List::stream)
            .map(TypeRef::name));
  }

  private static boolean notSerializable(final SyntaxNode type, final UnitTypeFacts types) {
    final boolean concrete =
        types
            .shapeOf(type.span())
            .filter(shape -> shape.kind() == TypeShape.Kind.CLASS && !shape.isAbstract())
            .isPresent();
    final boolean known = types.membersOf(type.span()).isPresent();
    final boolean primitive = types.typeOf(type.span()).filter(TypeRef::primitive).isPresent();
    return concrete && known && !primitive && !serializable(type.span(), types);
  }

  private static boolean writesOwnForm(final SyntaxNode clazz) {
    return ClassMembers.callableMembers(clazz).stream()
        .map(ClassMembers::declarationOf)
        .anyMatch(method -> CUSTOM_FORMS.contains(MemberDeclarations.name(method)));
  }

  private static boolean isInstanceField(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return !modifiers.contains(MemberDeclarations.STATIC) && !modifiers.contains(TRANSIENT);
  }
}
