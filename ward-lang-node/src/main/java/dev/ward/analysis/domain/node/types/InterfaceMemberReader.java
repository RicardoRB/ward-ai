package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the members of interfaces. */
final class InterfaceMemberReader {
  private static final String PROPERTY_NAME = "propertyName";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final String QUESTION = "?";

  private final MemberSink sink = new MemberSink();

  List<Member> members() {
    return sink.members();
  }

  List<Sig> constructs() {
    return sink.constructs();
  }

  List<Sig> calls() {
    return sink.calls();
  }

  /** Reads the members of an interface body. */
  void objectType(final SyntaxNode objectType) {
    final SyntaxNode body = TreeKit.child(objectType, "typeBody");
    final SyntaxNode list = body == null ? null : TreeKit.child(body, "typeMemberList");
    if (list != null) {
      TreeKit.children(list, "typeMember").forEach(this::typeMember);
    }
  }

  private void typeMember(final SyntaxNode member) {
    for (final SyntaxNode part : TreeKit.parts(member)) {
      switch (part.kind()) {
        case "propertySignatur" -> propertySignature(part);
        case "methodSignature" -> methodSignature(part);
        case CALL_SIGNATURE -> sink.addCall(SigReader.of(part));
        case "constructSignature" -> sink.addConstruct(SigReader.of(part));
        case "indexSignature" -> sink.indexSignature(part);
        case "getter" -> accessor(member);
        default -> {
          // separators and setters add nothing the getter did not
        }
      }
    }
  }

  private void accessor(final SyntaxNode member) {
    final SyntaxNode getter = TreeKit.child(member, "getter");
    final String name = TreeText.text(TreeKit.child(getter, "classElementName"));
    sink.add(new Member(name, SigReader.returns(member), List.of(), Member.READONLY, List.of()));
  }

  private void propertySignature(final SyntaxNode node) {
    final int flags =
        (TreeKit.hasToken(node, "readonly") ? Member.READONLY : 0)
            | (TreeKit.hasToken(node, QUESTION) ? Member.OPTIONAL : 0);
    sink.add(
        new Member(
            TreeText.unquote(TreeText.text(TreeKit.child(node, PROPERTY_NAME))),
            SigReader.annotated(TreeKit.child(node, "typeAnnotation")),
            List.of(),
            flags,
            List.of()));
  }

  private void methodSignature(final SyntaxNode node) {
    final int flags = TreeKit.hasToken(node, QUESTION) ? Member.OPTIONAL : 0;
    sink.method(
        TreeText.unquote(TreeText.text(TreeKit.child(node, PROPERTY_NAME))),
        SigReader.of(TreeKit.child(node, CALL_SIGNATURE)),
        flags,
        List.of());
  }
}
