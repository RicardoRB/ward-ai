package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The facts of declarations: their shape and supertypes, decorators and overridden members. */
final class DeclarationFacts {
  private static final String CLASS = "classDeclaration";
  private static final String CLASS_ELEMENT = "classElement";
  private static final String MEMBER = "propertyMemberDeclaration";
  private static final String DECORATORS = "decoratorList";

  private final UnitModel model;
  private final ExprTyper typer;

  DeclarationFacts(final UnitModel model, final ExprTyper typer) {
    this.model = model;
    this.typer = typer;
  }

  /** Returns the shape of a type declaration, or of the type of an expression. */
  TypeShape shape(final SyntaxNode node) {
    final Sym sym;
    switch (node.kind()) {
      case CLASS, "interfaceDeclaration", "enumDeclaration" -> sym = declared(node);
      default -> sym = Exprs.isExpression(node.kind()) ? ofType(typer.typeOf(node)) : null;
    }
    return sym == null ? null : shapeOf(sym);
  }

  private Sym declared(final SyntaxNode node) {
    final SyntaxNode name = TreeKit.child(node, "identifier");
    return name == null ? null : model.module().own(TreeKit.text(name));
  }

  private static Sym ofType(final Ty type) {
    return type instanceof Ty.Named named && named.ref() != null ? named.ref().sym() : null;
  }

  private static TypeShape shapeOf(final Sym sym) {
    for (final Sym.Located located : sym.types()) {
      final Decl decl = located.decl();
      final TypeShape.Kind kind = kindOf(decl.kind());
      if (kind != null) {
        final boolean isAbstract = decl.has(Decl.ABSTRACT) || kind == TypeShape.Kind.INTERFACE;
        return new TypeShape(kind, isAbstract, false, false, supertypes(decl, located.scope()));
      }
    }
    return null;
  }

  private static TypeShape.Kind kindOf(final Decl.Kind kind) {
    return switch (kind) {
      case CLASS -> TypeShape.Kind.CLASS;
      case INTERFACE -> TypeShape.Kind.INTERFACE;
      case ENUM -> TypeShape.Kind.ENUM;
      default -> null;
    };
  }

  private static List<TypeRef> supertypes(final Decl decl, final Lookup scope) {
    final List<TypeRef> supertypes = new ArrayList<>();
    for (final Ty base : bases(decl, scope)) {
      if (base instanceof Ty.Named named && named.ref() != null && named.ref().sym() != null) {
        supertypes.add(TypeRefs.of(base));
      }
    }
    return supertypes;
  }

  private static List<Ty> bases(final Decl decl, final Lookup scope) {
    final List<Ty> bases = new ArrayList<>();
    for (final Ty base : decl.header().extendsTypes()) {
      bases.add(Inst.apply(base, Map.of(), scope));
    }
    for (final Ty base : decl.header().implementsTypes()) {
      bases.add(Inst.apply(base, Map.of(), scope));
    }
    return bases;
  }

  /** Returns the decorators of a class or member declaration. */
  List<String> decorators(final SyntaxNode node) {
    if (CLASS.equals(node.kind()) || CLASS_ELEMENT.equals(node.kind())) {
      return MemberReader.decorators(TreeKit.child(node, DECORATORS));
    }
    final SyntaxNode parent = MEMBER.equals(node.kind()) ? model.parent(node) : null;
    return parent != null && CLASS_ELEMENT.equals(parent.kind())
        ? MemberReader.decorators(TreeKit.child(parent, DECORATORS))
        : List.of();
  }

  /** Returns the decorators of the classes that enclose a node, nearest first. */
  List<String> enclosing(final SyntaxNode node) {
    final List<String> names = new ArrayList<>();
    SyntaxNode current = model.parent(node);
    while (current != null) {
      if (CLASS.equals(current.kind()) && !CLASS.equals(node.kind())) {
        names.addAll(MemberReader.decorators(TreeKit.child(current, DECORATORS)));
      }
      current = model.parent(current);
    }
    return names;
  }

  /** Returns the members of the supertypes that the method declared by {@code node} overrides. */
  List<CallTarget> overrides(final SyntaxNode node) {
    final SyntaxNode name = MEMBER.equals(node.kind()) ? TreeKit.child(node, "propertyName") : null;
    final SyntaxNode owner = name == null ? null : enclosingClass(node);
    final Sym sym = owner == null || isStatic(node) ? null : declared(owner);
    final List<CallTarget> found = new ArrayList<>();
    if (sym != null) {
      for (final Sym.Located located : sym.of(Decl.Kind.CLASS)) {
        for (final Ty base : bases(located.decl(), located.scope())) {
          final Members.Found inherited = typer.members().find(base, TreeKit.text(name));
          if (inherited != null) {
            found.add(inheritedTarget(inherited, TreeKit.text(name)));
          }
        }
      }
    }
    return found;
  }

  private static boolean isStatic(final SyntaxNode member) {
    final SyntaxNode base = TreeKit.child(member, "propertyMemberBase");
    return base != null && TreeKit.hasToken(base, "static");
  }

  private static CallTarget inheritedTarget(final Members.Found found, final String name) {
    final Sig sig =
        !found.sigs().isEmpty() ? found.sigs().get(0) : new Sig(List.of(), List.of(), found.type());
    return TypeRefs.target(found.owner(), name, sig, false);
  }

  private SyntaxNode enclosingClass(final SyntaxNode node) {
    SyntaxNode current = model.parent(node);
    while (current != null && !CLASS.equals(current.kind())) {
      current = model.parent(current);
    }
    return current;
  }
}
