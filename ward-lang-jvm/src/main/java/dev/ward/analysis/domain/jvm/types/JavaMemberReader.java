package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the fields, methods and constructors of one Java type declaration. */
final class JavaMemberReader {
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_TYPE = "typeType";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String FIELD = "fieldDeclaration";
  private static final String CONSTANT = "constDeclaration";
  private static final String BLOCK = "block";
  private static final String COMPACT = "compactConstructorDeclaration";
  private static final String BRACKET = "[";

  private final TypeScope scope;
  private final String owner;
  private final JvmClass.Kind kind;

  JavaMemberReader(final TypeScope scope, final String owner, final JvmClass.Kind kind) {
    this.scope = scope;
    this.owner = owner;
    this.kind = kind;
  }

  List<SourceModel.Member> read(final SyntaxNode type) {
    final List<SourceModel.Member> members = new ArrayList<>();
    members.addAll(JavaImplicitMembers.enumConstants(type, scope, owner, kind));
    members.addAll(JavaImplicitMembers.recordComponents(type, scope, kind));
    for (final JavaClassReader.Entry entry : JavaClassReader.bodyEntries(type)) {
      declared(entry).ifPresent(members::add);
    }
    return members;
  }

  private Optional<SourceModel.Member> declared(final JavaClassReader.Entry entry) {
    final SyntaxNode node = entry.declaration();
    final String nodeKind = node.kind();
    if (FIELD.equals(nodeKind) || CONSTANT.equals(nodeKind)) {
      return Optional.of(fields(entry));
    }
    if (BLOCK.equals(nodeKind)) {
      return Optional.of(
          new SourceModel.Member(
              node,
              entry.outer(),
              scope,
              Optional.empty(),
              List.of(),
              List.of(),
              Optional.of(node)));
    }
    if (COMPACT.equals(nodeKind)) {
      return Optional.of(
          new SourceModel.Member(
              node,
              entry.outer(),
              scope,
              Optional.empty(),
              List.of(),
              List.of(),
              Syntax.child(node, BLOCK)));
    }
    return JavaClassReader.isType(node)
        ? Optional.empty()
        : JavaMethodReader.method(entry, scope, inInterface());
  }

  private boolean inInterface() {
    return kind == JvmClass.Kind.INTERFACE || kind == JvmClass.Kind.ANNOTATION;
  }

  private SourceModel.Member fields(final JavaClassReader.Entry entry) {
    final SyntaxNode node = entry.declaration();
    final Optional<JType> base =
        Syntax.child(node, TYPE_TYPE).flatMap(type -> JavaTypes.type(type, scope));
    final int flags =
        inInterface()
            ? JavaModifiers.flags(entry.modifiers()) | Flags.PUBLIC | Flags.STATIC | Flags.FINAL
            : JavaModifiers.flags(entry.modifiers());
    final List<String> annotations = JavaModifiers.annotations(entry.modifiers(), scope);
    final List<JvmField> fields = new ArrayList<>();
    final List<SourceModel.Variable> variables = new ArrayList<>();
    for (final SyntaxNode declarator : declarators(node)) {
      final SyntaxNode id = Syntax.child(declarator, DECLARATOR_ID).orElse(declarator);
      final SyntaxNode name = Syntax.child(id, IDENTIFIER).orElse(declarator);
      final Optional<JType> type = base.map(found -> JavaTypes.arrayOf(found, dimensions(id)));
      variables.add(new SourceModel.Variable(name, type));
      final Optional<Object> value =
          Flags.has(flags, Flags.FINAL) ? constantOf(declarator) : Optional.empty();
      type.map(found -> new JvmField(Syntax.text(name), flags, found, value, annotations))
          .ifPresent(fields::add);
    }
    return new SourceModel.Member(
        node, entry.outer(), scope, Optional.empty(), fields, variables, Optional.of(node));
  }

  private static List<SyntaxNode> declarators(final SyntaxNode node) {
    return node.children().stream()
        .flatMap(
            child ->
                switch (child.kind()) {
                  case "variableDeclarators" ->
                      Syntax.children(child, "variableDeclarator").stream();
                  case "constantDeclarator" -> java.util.stream.Stream.of(child);
                  default -> java.util.stream.Stream.<SyntaxNode>empty();
                })
        .toList();
  }

  static int dimensions(final SyntaxNode id) {
    return (int)
        id.children().stream().filter(child -> BRACKET.equals(Syntax.token(child))).count();
  }

  private static Optional<Object> constantOf(final SyntaxNode declarator) {
    return Syntax.find(declarator, "expression").flatMap(Constants::literalValue);
  }

  /** Returns the methods of the members, with the implicit ones of the kind of class. */
  List<JvmMethod> methods(
      final List<SourceModel.Member> members, final boolean defaultConstructor) {
    return JavaImplicitMembers.methods(members, owner, kind, defaultConstructor);
  }
}
