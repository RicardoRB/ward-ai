package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the members of classes. */
final class ClassMemberReader {
  private static final String PROPERTY_NAME = "propertyName";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final String CONSTRUCTOR = "constructor";

  private final MemberSink sink = new MemberSink();

  List<Member> members() {
    return sink.members();
  }

  List<Sig> constructs() {
    return sink.constructs();
  }

  /** Reads the elements of a {@code classTail}. */
  void classTail(final SyntaxNode tail) {
    for (final SyntaxNode element : TreeKit.children(tail, "classElement")) {
      final List<String> decorators =
          MemberSink.decorators(TreeKit.child(element, "decoratorList"));
      for (final SyntaxNode part : TreeKit.parts(element)) {
        switch (part.kind()) {
          case "constructorDeclaration" -> constructor(part);
          case "propertyMemberDeclaration" -> classMember(part, decorators);
          case "indexMemberDeclaration" ->
              sink.indexSignature(TreeKit.child(part, "indexSignature"));
          case "methodDefinition" -> scriptMethod(part, decorators);
          default -> {
            // decorators were read above; statements in a class body declare no members
          }
        }
      }
    }
  }

  /** Reads a method of a JavaScript class, whose parameters carry no types. */
  private void scriptMethod(final SyntaxNode node, final List<String> decorators) {
    final String name = TreeText.text(TreeKit.child(node, "classElementName"));
    final Sig sig = new Sig(List.of(), SigReader.params(node), TsType.UNKNOWN);
    if (CONSTRUCTOR.equals(name)) {
      sink.addConstruct(sig);
    } else {
      sink.method(name, sig, TreeKit.hasToken(node, "static") ? Member.STATIC : 0, decorators);
    }
  }

  private void constructor(final SyntaxNode node) {
    sink.addConstruct(new Sig(List.of(), SigReader.params(node), TsType.UNKNOWN));
    for (final SyntaxNode list : TreeKit.children(node, "formalParameterList")) {
      for (final SyntaxNode argument : TreeKit.children(list, "formalParameterArg")) {
        ClassFieldReader.parameterProperty(sink, argument);
      }
    }
  }

  private void classMember(final SyntaxNode node, final List<String> decorators) {
    final SyntaxNode base = TreeKit.child(node, "propertyMemberBase");
    final int flags = base == null ? 0 : ClassFieldReader.modifiers(TreeText.leaves(base));
    final SyntaxNode name = TreeKit.child(node, PROPERTY_NAME);
    final SyntaxNode signature = TreeKit.child(node, CALL_SIGNATURE);
    if (name != null && signature != null) {
      sink.method(
          TreeText.unquote(TreeText.text(name)), SigReader.of(signature), flags, decorators);
    } else if (name != null) {
      ClassFieldReader.property(sink, node, name, flags, decorators);
    } else {
      ClassFieldReader.other(sink, node, flags, decorators);
    }
  }
}
