package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The fields of one class and how its members use them, in source order. A name counts as a field
 * access when it is {@code x} or {@code this.x} for a field {@code x} of the class and no parameter
 * or local variable of the member is also named {@code x}; the value of an assignment is read
 * before its target is written. Nested classes are walked as part of the member that holds them.
 */
final class FieldAccesses {

  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String FIELD_DECLARATION = "fieldDeclaration";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String VARIABLE_DECLARATOR = "variableDeclarator";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String INITIALIZER = "variableInitializer";
  private static final String MODIFIER = "modifier";
  private static final String NULL = "null";

  private final List<Field> fields;
  private final List<Member> members;

  /** What a member of the class is. */
  enum MemberKind {
    /** A constructor that does not start with {@code this(...)}. */
    CONSTRUCTOR,
    /** A constructor that delegates to another constructor. */
    DELEGATING_CONSTRUCTOR,
    /** An instance or static initializer block. */
    INITIALIZER,
    /** Any other member. */
    OTHER
  }

  /** How an access uses a field. */
  enum Use {
    /** The value is read. */
    READ,
    /** The literal {@code null} is assigned. */
    WRITE_NULL,
    /** Another value is assigned. */
    WRITE
  }

  /** One field declarator of the class. */
  record Field(
      String name,
      SyntaxNode declarator,
      boolean initialized,
      boolean initializedWithNull,
      boolean isPrivate,
      boolean isStatic,
      boolean annotated) {}

  /**
   * One use of a field.
   *
   * @param field the name of the field
   * @param node the node of the access
   * @param use how the field is used
   * @param dereferenced whether a member of the value is selected, as in {@code x.size()}
   */
  record Access(String field, SyntaxNode node, Use use, boolean dereferenced) {}

  /** The accesses of one member, in source order; {@code name} is that of a method, else empty. */
  record Member(MemberKind kind, String name, List<Access> accesses) {

    /** Copies the accesses. */
    Member {
      accesses = List.copyOf(accesses);
    }
  }

  private FieldAccesses(final List<Field> fields, final List<Member> members) {
    this.fields = List.copyOf(fields);
    this.members = List.copyOf(members);
  }

  /** The fields of the class, in source order. */
  List<Field> fields() {
    return fields;
  }

  /** The members of the class, in source order. */
  List<Member> members() {
    return members;
  }

  /** Returns every access to {@code name}, in source order across the members. */
  List<Access> accessesTo(final String name) {
    return members.stream()
        .flatMap(member -> member.accesses().stream())
        .filter(access -> access.field().equals(name))
        .toList();
  }

  /** Reads the fields and the accesses of {@code classDeclaration}. */
  static FieldAccesses of(final SyntaxNode classDeclaration) {
    final Optional<SyntaxNode> body =
        classDeclaration.children().stream()
            .filter(child -> CLASS_BODY.equals(child.kind()))
            .findFirst();
    if (body.isEmpty()) {
      return new FieldAccesses(List.of(), List.of());
    }
    final List<SyntaxNode> declarations = JUnitMembers.members(body.get());
    final List<Field> fields = new ArrayList<>();
    declarations.forEach(declaration -> fields.addAll(fieldsOf(declaration)));
    final Set<String> names = new HashSet<>();
    fields.forEach(field -> names.add(field.name()));
    final List<Member> members = new ArrayList<>();
    declarations.forEach(declaration -> members.add(member(declaration, names)));
    return new FieldAccesses(fields, members);
  }

  private static List<Field> fieldsOf(final SyntaxNode declaration) {
    final Optional<SyntaxNode> field = fieldDeclaration(declaration);
    if (field.isEmpty()) {
      return List.of();
    }
    final List<String> modifiers =
        declaration.children().stream()
            .filter(child -> MODIFIER.equals(child.kind()))
            .map(SyntaxWalk::text)
            .toList();
    return SyntaxWalk.descendants(field.get()).stream()
        .filter(node -> VARIABLE_DECLARATOR.equals(node.kind()))
        .map(node -> field(node, modifiers))
        .toList();
  }

  private static Field field(final SyntaxNode declarator, final List<String> modifiers) {
    final String name =
        SyntaxWalk.descendants(declarator).stream()
            .filter(node -> DECLARATOR_ID.equals(node.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    final Optional<SyntaxNode> initializer =
        declarator.children().stream().filter(c -> INITIALIZER.equals(c.kind())).findFirst();
    return new Field(
        name,
        declarator,
        initializer.isPresent(),
        initializer.map(SyntaxWalk::text).filter(NULL::equals).isPresent(),
        modifiers.contains("private"),
        modifiers.contains("static"),
        modifiers.stream().anyMatch(modifier -> modifier.startsWith("@")));
  }

  private static Optional<SyntaxNode> fieldDeclaration(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(child -> MEMBER_DECLARATION.equals(child.kind()))
        .flatMap(child -> child.children().stream())
        .filter(child -> FIELD_DECLARATION.equals(child.kind()))
        .findFirst();
  }

  private static Member member(final SyntaxNode declaration, final Set<String> fieldNames) {
    final boolean isField = fieldDeclaration(declaration).isPresent();
    final Set<String> locals = isField ? Set.of() : localNames(declaration);
    final List<Access> accesses = new ArrayList<>();
    FieldWalk.walk(declaration, null, fieldNames, locals, accesses);
    final String name = JUnitMembers.method(declaration).map(JUnitMembers::name).orElse("");
    return new Member(kindOf(declaration), name, accesses);
  }

  private static MemberKind kindOf(final SyntaxNode declaration) {
    final Optional<SyntaxNode> constructor =
        declaration.children().stream()
            .filter(child -> MEMBER_DECLARATION.equals(child.kind()))
            .flatMap(child -> child.children().stream())
            .filter(child -> CONSTRUCTOR.equals(child.kind()))
            .findFirst();
    if (constructor.isPresent()) {
      return SyntaxWalk.text(constructor.get()).contains("{this(")
          ? MemberKind.DELEGATING_CONSTRUCTOR
          : MemberKind.CONSTRUCTOR;
    }
    final boolean block = declaration.children().stream().anyMatch(c -> "block".equals(c.kind()));
    return block ? MemberKind.INITIALIZER : MemberKind.OTHER;
  }

  private static Set<String> localNames(final SyntaxNode declaration) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(declaration)) {
      if (DECLARATOR_ID.equals(node.kind())) {
        names.add(SyntaxWalk.text(node));
      }
    }
    return names;
  }
}
