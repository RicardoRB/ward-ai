package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads class, interface, alias and enum declarations. */
final class TypeDeclReader {
  private static final String TYPE_PARAMETERS = "typeParameters";
  private static final String SINGLE = "singleExpression";
  private static final String EXTENDS = "extends";

  private TypeDeclReader() {}

  /** Returns the class that a class declaration or class expression declares. */
  static Decl clazz(final SyntaxNode node, final int flags) {
    final ClassMemberReader members = new ClassMemberReader();
    final SyntaxNode tail = TreeKit.child(node, "classTail");
    if (tail != null) {
      members.classTail(tail);
    }
    final List<TsType> extended = new ArrayList<>();
    final List<TsType> implemented = new ArrayList<>();
    heritage(node, tail, extended, implemented);
    final Decl.Header header =
        new Decl.Header(
            SigReader.typeParams(TreeKit.child(node, TYPE_PARAMETERS)),
            extended,
            implemented,
            List.of(),
            members.constructs(),
            MemberSink.decorators(TreeKit.child(node, "decoratorList")));
    final int abstractFlag = TreeKit.hasToken(node, "abstract") ? Decl.ABSTRACT : 0;
    final SyntaxNode name = TreeKit.child(node, "identifier");
    return new Decl(
        Decl.Kind.CLASS,
        name == null ? "default" : TreeText.text(name),
        header,
        members.members(),
        TsType.UNKNOWN,
        List.of(),
        flags | abstractFlag);
  }

  private static void heritage(
      final SyntaxNode node,
      final SyntaxNode tail,
      final List<TsType> extended,
      final List<TsType> implemented) {
    if (tail != null && TreeKit.hasToken(tail, EXTENDS)) {
      final SyntaxNode base = TreeKit.child(tail, SINGLE);
      extended.add(base == null ? TsType.UNKNOWN : TyParser.parse(TreeText.leaves(base)));
    }
    final SyntaxNode heritage = TreeKit.child(node, "classHeritage");
    if (heritage == null) {
      return;
    }
    final SyntaxNode extendsClause = TreeKit.child(heritage, "classExtendsClause");
    if (extendsClause != null) {
      extended.addAll(typeReferences(extendsClause));
    }
    final SyntaxNode implementsClause = TreeKit.child(heritage, "implementsClause");
    if (implementsClause != null) {
      implemented.addAll(typeReferences(implementsClause));
    }
  }

  private static List<TsType> typeReferences(final SyntaxNode clause) {
    return TreeKit.findAll(clause, "typeReference").stream()
        .map(reference -> TyParser.parse(TreeText.leaves(reference)))
        .toList();
  }

  /** Returns the interface that an interface declaration declares. */
  static Decl iface(final SyntaxNode node, final int flags) {
    final InterfaceMemberReader members = new InterfaceMemberReader();
    final SyntaxNode body = TreeKit.child(node, "objectType");
    if (body != null) {
      members.objectType(body);
    }
    final SyntaxNode extendsClause = TreeKit.child(node, "interfaceExtendsClause");
    final Decl.Header header =
        new Decl.Header(
            SigReader.typeParams(TreeKit.child(node, TYPE_PARAMETERS)),
            extendsClause == null ? List.of() : typeReferences(extendsClause),
            List.of(),
            members.calls(),
            members.constructs(),
            List.of());
    return new Decl(
        Decl.Kind.INTERFACE,
        TreeText.text(ValueDeclReader.identifier(node)),
        header,
        members.members(),
        TsType.UNKNOWN,
        List.of(),
        flags);
  }

  /** Returns the alias that a type alias declaration declares. */
  static Decl alias(final SyntaxNode node, final int flags) {
    final SyntaxNode type = TreeKit.child(node, "type_");
    final Decl.Header header =
        new Decl.Header(
            SigReader.typeParams(TreeKit.child(node, TYPE_PARAMETERS)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    return new Decl(
        Decl.Kind.ALIAS,
        TreeText.text(ValueDeclReader.identifier(node)),
        header,
        List.of(),
        type == null ? TsType.UNKNOWN : TyParser.parse(TreeText.leaves(type)),
        List.of(),
        flags);
  }

  /** Returns the enum that an enum declaration declares. */
  static Decl enumeration(final SyntaxNode node, final int flags) {
    final String name = TreeText.text(ValueDeclReader.identifier(node));
    final MemberSink sink = new MemberSink();
    sink.enumBody(TreeKit.child(node, "enumBody"), name);
    return new Decl(
        Decl.Kind.ENUM, name, Decl.Header.NONE, sink.members(), TsType.UNKNOWN, List.of(), flags);
  }
}
