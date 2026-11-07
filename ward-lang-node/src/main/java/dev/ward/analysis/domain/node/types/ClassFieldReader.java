package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/** Reads the properties, accessors and parameter properties of classes. */
final class ClassFieldReader {
  private static final String TYPE_ANNOTATION = "typeAnnotation";

  private ClassFieldReader() {}

  /** Reads a property declaration. */
  static void property(
      final MemberSink sink,
      final SyntaxNode node,
      final SyntaxNode name,
      final int flags,
      final List<String> decorators) {
    final int all = flags | (TreeKit.hasToken(node, "?") ? Member.OPTIONAL : 0);
    TsType type = SigReader.annotated(TreeKit.child(node, TYPE_ANNOTATION));
    final SyntaxNode initializer = TreeKit.child(node, "initializer");
    if (TreeKit.child(node, TYPE_ANNOTATION) == null && initializer != null) {
      type =
          SimpleInit.of(
              TreeKit.child(initializer, "singleExpression"), (all & Member.READONLY) != 0);
    }
    sink.add(new Member(TreeText.unquote(TreeText.text(name)), type, List.of(), all, decorators));
  }

  /** Reads an accessor or an abstract declaration. */
  static void other(
      final MemberSink sink,
      final SyntaxNode node,
      final int flags,
      final List<String> decorators) {
    final SyntaxNode getAccessor = TreeKit.child(node, "getAccessor");
    final SyntaxNode abstractMember = TreeKit.child(node, "abstractDeclaration");
    if (getAccessor != null) {
      final SyntaxNode getter = TreeKit.child(getAccessor, "getter");
      final String name = TreeText.text(TreeKit.child(getter, "classElementName"));
      sink.add(new Member(name, SigReader.returns(getAccessor), List.of(), flags, decorators));
    } else if (abstractMember != null) {
      abstractMethod(sink, abstractMember, decorators);
    }
  }

  private static void abstractMethod(
      final MemberSink sink, final SyntaxNode node, final List<String> decorators) {
    final SyntaxNode name = TreeKit.child(node, "identifier");
    final SyntaxNode signature = TreeKit.child(node, "callSignature");
    if (name != null && signature != null) {
      sink.method(TreeText.text(name), SigReader.of(signature), Member.ABSTRACT, decorators);
    }
  }

  /** Reads the property that a constructor parameter with a modifier declares. */
  static void parameterProperty(final MemberSink sink, final SyntaxNode argument) {
    final SyntaxNode modifier = TreeKit.child(argument, "accessibilityModifier");
    if (modifier == null) {
      return;
    }
    final int hidden = "public".equals(TreeText.text(modifier)) ? 0 : Member.HIDDEN;
    final SyntaxNode list =
        new SyntaxNode("formalParameterList", argument.span(), Map.of(), List.of(argument));
    final SyntaxNode holder = new SyntaxNode("holder", argument.span(), Map.of(), List.of(list));
    SigReader.params(holder)
        .forEach(
            param ->
                sink.add(new Member(param.name(), param.type(), List.of(), hidden, List.of())));
  }

  /** Returns the member flags that the modifier tokens give. */
  static int modifiers(final List<String> tokens) {
    int flags = 0;
    for (final String token : tokens) {
      flags |=
          switch (token) {
            case "static" -> Member.STATIC;
            case "readonly" -> Member.READONLY;
            case "abstract" -> Member.ABSTRACT;
            case "private", "protected" -> Member.HIDDEN;
            default -> 0;
          };
    }
    return flags;
  }
}
