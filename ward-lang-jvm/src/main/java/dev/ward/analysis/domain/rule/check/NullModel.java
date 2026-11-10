package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The declarations of one Java file as the nullness checks read them: types, their fields and
 * methods, and which of those are annotated as nullable. Everything is read from the syntax of the
 * one file, so a declaration in another file is unknown and a check reports nothing about it.
 */
final class NullModel {

  private static final String TYPE_TYPE = "typeType";
  private static final String IDENTIFIER = "identifier";

  /** A type declaration with the members the checks read. */
  record Type(
      String name,
      SyntaxNode node,
      boolean marked,
      List<String> supertypes,
      List<Field> fields,
      List<Method> methods,
      List<SyntaxNode> initializers) {}

  /** A field declarator. */
  record Field(
      String name,
      SyntaxNode node,
      Notes notes,
      boolean primitive,
      boolean array,
      boolean init,
      boolean isStatic,
      SyntaxNode type) {}

  /** What a method returns. */
  enum Returns {
    /** A constructor or a void method. */
    NOTHING,
    /** A reference type. */
    REFERENCE,
    /** A primitive type. */
    PRIMITIVE
  }

  /** A method or constructor. */
  record Method(
      String name,
      SyntaxNode node,
      SyntaxNode body,
      List<Param> params,
      Notes notes,
      boolean constructor,
      Returns returns) {}

  /** A formal parameter. */
  record Param(String name, SyntaxNode node, Notes notes, boolean primitive, boolean varargs) {}

  /** The annotations of one declaration: their nodes and their simple names. */
  record Notes(List<SyntaxNode> nodes, Set<String> names) {

    /** Tells whether the declaration carries an annotation named {@code name}. */
    boolean has(final String name) {
      return names.contains(name);
    }

    /** Tells whether an annotation says the declaration may be null. */
    boolean nullable() {
      return names.contains("Nullable") || names.contains("CheckForNull");
    }

    /** Tells whether an annotation says the declaration is never null. */
    boolean nonNull() {
      return names.contains("NonNull") || names.contains("Nonnull") || names.contains("NotNull");
    }
  }

  private NullModel() {}

  /** Returns the types of the file, outer types before the types nested in them. */
  static List<Type> types(final SyntaxNode root) {
    final List<Type> out = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, NullSyntax.TYPES)) {
      out.add(type(visit, root));
    }
    return out;
  }

  private static Type type(final SyntaxWalk.Visit visit, final SyntaxNode root) {
    final SyntaxNode node = visit.node();
    final boolean marked = marked(visit);
    final List<Field> fields = new ArrayList<>();
    final List<Method> methods = new ArrayList<>();
    final List<SyntaxNode> initializers = new ArrayList<>();
    for (final SyntaxNode body : node.children()) {
      if (!"classBody".equals(body.kind()) && !"interfaceBody".equals(body.kind())) {
        continue;
      }
      for (final SyntaxNode member : body.children()) {
        NullMembers.read(member, fields, methods, initializers);
      }
    }
    final String name = NullSyntax.first(node, IDENTIFIER).map(SyntaxWalk::text).orElse("");
    return new Type(
        name, node, marked || packageMarked(root), supertypes(node), fields, methods, initializers);
  }

  /** Tells whether the type or a type around it is annotated {@code @NullMarked}. */
  private static boolean marked(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> chain = new ArrayList<>(visit.ancestors());
    chain.add(visit.node());
    for (int index = 1; index < chain.size(); index++) {
      if (NullSyntax.TYPES.contains(chain.get(index).kind())
          && isNullMarked(holder(chain, index))) {
        return true;
      }
    }
    return false;
  }

  /** The annotations before the type at {@code index}, kept in its wrapper or class body entry. */
  private static List<SyntaxNode> holder(final List<SyntaxNode> chain, final int index) {
    final SyntaxNode wrapper = chain.get(index - 1);
    if ("memberDeclaration".equals(wrapper.kind()) && index > 1) {
      return NullSyntax.annotations(chain.get(index - 2));
    }
    return NullSyntax.annotations(wrapper);
  }

  private static boolean isNullMarked(final List<SyntaxNode> annotations) {
    return annotations.stream().map(NullSyntax::simpleName).anyMatch("NullMarked"::equals);
  }

  private static boolean packageMarked(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of("packageDeclaration")).stream()
        .anyMatch(visit -> isNullMarked(NullSyntax.annotations(visit.node())));
  }

  private static List<String> supertypes(final SyntaxNode type) {
    final List<String> names = new ArrayList<>();
    final List<SyntaxNode> children = type.children();
    for (int index = 0; index < children.size(); index++) {
      final String kind = children.get(index).kind();
      if ("typeList".equals(kind) || TYPE_TYPE.equals(kind)) {
        final String previous = SyntaxWalk.text(children.get(index - 1));
        if ("extends".equals(previous) || "implements".equals(previous)) {
          SyntaxWalk.visits(children.get(index), Set.of("classType")).stream()
              .map(visit -> typeName(visit.node()))
              .forEach(names::add);
        }
      }
    }
    return names;
  }

  private static String typeName(final SyntaxNode classType) {
    final String text = SyntaxWalk.text(classType);
    final int generic = text.indexOf('<');
    return generic < 0 ? text : text.substring(0, generic);
  }
}
