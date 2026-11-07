package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads the fields, methods and initializer blocks that make up the body of a type. */
final class NullMembers {

  private static final Set<String> MEMBERS =
      Set.of(
          "methodDeclaration",
          "interfaceCommonBodyDeclaration",
          "fieldDeclaration",
          "constructorDeclaration");
  private static final String TYPE_TYPE = "typeType";
  private static final String IDENTIFIER = "identifier";
  private static final String FIELD = "fieldDeclaration";
  private static final String BLOCK = "block";

  private NullMembers() {}

  /** Reads one entry of a class or interface body into the field, method or initializer lists. */
  static void read(
      final SyntaxNode entry,
      final List<NullModel.Field> fields,
      final List<NullModel.Method> methods,
      final List<SyntaxNode> initializers) {
    final Optional<SyntaxNode> member = NullSyntax.scan(entry, MEMBERS).stream().findFirst();
    if (member.isPresent()) {
      readMember(
          member.get(),
          NullSyntax.notes(NullSyntax.annotations(entry)),
          modifiers(entry),
          fields,
          methods);
    } else {
      NullSyntax.first(entry, BLOCK).ifPresent(initializers::add);
    }
  }

  private static Set<String> modifiers(final SyntaxNode entry) {
    final Set<String> texts = new java.util.HashSet<>();
    entry.children().stream()
        .filter(child -> NullSyntax.HOLDERS.contains(child.kind()))
        .forEach(child -> texts.add(SyntaxWalk.text(child)));
    return texts;
  }

  private static void readMember(
      final SyntaxNode node,
      final NullModel.Notes held,
      final Set<String> modifiers,
      final List<NullModel.Field> fields,
      final List<NullModel.Method> methods) {
    if (FIELD.equals(node.kind())) {
      fields.addAll(fieldsOf(node, held, modifiers.contains("static")));
    } else {
      methods.add(method(node, held));
    }
  }

  private static List<NullModel.Field> fieldsOf(
      final SyntaxNode node, final NullModel.Notes held, final boolean isStatic) {
    final SyntaxNode type = NullSyntax.first(node, TYPE_TYPE).orElse(node);
    final NullModel.Notes notes = NullSyntax.merge(held, NullSyntax.annotations(type));
    final boolean primitive = NullSyntax.isPrimitive(type);
    final boolean array = NullSyntax.isArray(type);
    final List<NullModel.Field> out = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(node, Set.of("variableDeclarator"))) {
      final SyntaxNode declarator = visit.node();
      final String name = declaredName(declarator);
      final boolean init =
          declarator.children().stream().anyMatch(child -> "=".equals(SyntaxWalk.text(child)));
      out.add(new NullModel.Field(name, declarator, notes, primitive, array, init, isStatic));
    }
    return out;
  }

  private static NullModel.Method method(final SyntaxNode node, final NullModel.Notes held) {
    final boolean constructor = "constructorDeclaration".equals(node.kind());
    final Optional<SyntaxNode> returnType = NullSyntax.first(node, "typeTypeOrVoid");
    final Optional<SyntaxNode> type = returnType.flatMap(r -> NullSyntax.first(r, TYPE_TYPE));
    final NullModel.Notes notes = NullSyntax.merge(held, NullSyntax.annotations(node));
    final NullModel.Notes withType =
        type.map(t -> NullSyntax.merge(notes, NullSyntax.annotations(t))).orElse(notes);
    final NullModel.Returns returns =
        type.map(
                t ->
                    NullSyntax.isPrimitive(t)
                        ? NullModel.Returns.PRIMITIVE
                        : NullModel.Returns.REFERENCE)
            .orElse(NullModel.Returns.NOTHING);
    final String name = NullSyntax.first(node, IDENTIFIER).map(SyntaxWalk::text).orElse("");
    final SyntaxNode body =
        node.children().stream()
            .filter(child -> "methodBody".equals(child.kind()) || BLOCK.equals(child.kind()))
            .findFirst()
            .flatMap(
                child ->
                    BLOCK.equals(child.kind())
                        ? Optional.of(child)
                        : NullSyntax.first(child, BLOCK))
            .orElse(null);
    return new NullModel.Method(name, node, body, params(node), withType, constructor, returns);
  }

  private static List<NullModel.Param> params(final SyntaxNode method) {
    final List<NullModel.Param> out = new ArrayList<>();
    final Optional<SyntaxNode> list = NullSyntax.first(method, "formalParameters");
    if (list.isEmpty()) {
      return out;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(list.get(), Set.of("formalParameter"))) {
      final SyntaxNode node = visit.node();
      final SyntaxNode type = NullSyntax.first(node, TYPE_TYPE).orElse(node);
      final boolean varargs =
          node.children().stream().anyMatch(child -> "...".equals(SyntaxWalk.text(child)));
      final NullModel.Notes notes =
          NullSyntax.merge(
              NullSyntax.notes(NullSyntax.annotations(node)), NullSyntax.annotations(type));
      final String name = declaredName(node);
      out.add(
          new NullModel.Param(
              name, node, notes, NullSyntax.isPrimitive(type) && !varargs, varargs));
    }
    return out;
  }

  /** The name a variable declarator or parameter declares, without array brackets. */
  private static String declaredName(final SyntaxNode node) {
    return NullSyntax.first(node, "variableDeclaratorId")
        .flatMap(id -> NullSyntax.first(id, IDENTIFIER))
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
