package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The facts of declarations: their shape and supertypes, decorators and overridden members. */
final class DeclarationFacts {
  private static final String CLASS = "classDeclaration";
  private static final Set<String> DECLARATIONS =
      Set.of(CLASS, "interfaceDeclaration", "enumDeclaration");
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
    final Sym sym = isDeclaration(node) ? declared(node) : ofExpression(node);
    return sym == null ? null : ShapeFacts.shapeOf(sym);
  }

  private static boolean isDeclaration(final SyntaxNode node) {
    return DECLARATIONS.contains(node.kind());
  }

  private Sym ofExpression(final SyntaxNode node) {
    return Exprs.isExpression(node.kind()) ? ShapeFacts.ofType(typer.typeOf(node)) : null;
  }

  private Sym declared(final SyntaxNode node) {
    final SyntaxNode name = TreeKit.child(node, "identifier");
    return name == null ? null : model.module().own(TreeText.text(name));
  }

  /** Returns the decorators of a class or member declaration. */
  List<String> decorators(final SyntaxNode node) {
    if (CLASS.equals(node.kind()) || CLASS_ELEMENT.equals(node.kind())) {
      return MemberSink.decorators(TreeKit.child(node, DECORATORS));
    }
    final SyntaxNode parent = MEMBER.equals(node.kind()) ? model.parent(node) : null;
    return parent != null && CLASS_ELEMENT.equals(parent.kind())
        ? MemberSink.decorators(TreeKit.child(parent, DECORATORS))
        : List.of();
  }

  /** Returns the decorators of the classes that enclose a node, nearest first. */
  List<String> enclosing(final SyntaxNode node) {
    final List<String> names = new ArrayList<>();
    SyntaxNode current = model.parent(node);
    while (current != null) {
      if (CLASS.equals(current.kind()) && !CLASS.equals(node.kind())) {
        names.addAll(MemberSink.decorators(TreeKit.child(current, DECORATORS)));
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
        for (final TsType base : ShapeFacts.bases(located.decl(), located.scope())) {
          final Members.Found inherited = typer.members().find(base, TreeText.text(name));
          if (inherited != null) {
            found.add(inheritedTarget(inherited, TreeText.text(name)));
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
        found.sigs().isEmpty() ? new Sig(List.of(), List.of(), found.type()) : found.sigs().get(0);
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
