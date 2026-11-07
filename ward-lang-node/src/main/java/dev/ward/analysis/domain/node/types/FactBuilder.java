package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Answers the facts of the nodes of one unit. */
final class FactBuilder {
  private static final String CLASS = "classDeclaration";
  private static final String CLASS_ELEMENT = "classElement";
  private static final String MEMBER = "propertyMemberDeclaration";

  private final UnitModel model;
  private final ExprTyper typer;
  private final Program program;

  FactBuilder(final UnitModel model, final ExprTyper typer, final Program program) {
    this.model = model;
    this.typer = typer;
    this.program = program;
  }

  /** Returns what is known about {@code span}, or null when nothing is. */
  SpanFacts at(final Span span, final Set<FactKind> kinds) {
    TypeRef type = null;
    TypeShape shape = null;
    CallTarget call = null;
    List<String> annotations = List.of();
    List<String> scopes = List.of();
    List<CallTarget> overrides = List.of();
    ConstantValue constant = null;
    for (final SyntaxNode node : model.nodesAt(span)) {
      if (kinds.contains(FactKind.TYPE) && type == null) {
        type = type(node);
      }
      if (kinds.contains(FactKind.SHAPE) && shape == null) {
        shape = shape(node);
      }
      if (kinds.contains(FactKind.CALL) && call == null) {
        call = call(node);
      }
      if (kinds.contains(FactKind.ANNOTATIONS) && annotations.isEmpty()) {
        annotations = decorators(node);
        scopes = enclosing(node);
      }
      if (kinds.contains(FactKind.OVERRIDES) && overrides.isEmpty()) {
        overrides = overrides(node);
      }
      if (kinds.contains(FactKind.CONSTANT) && constant == null) {
        constant = constant(node);
      }
    }
    final boolean any =
        type != null
            || shape != null
            || call != null
            || !annotations.isEmpty()
            || !scopes.isEmpty()
            || !overrides.isEmpty()
            || constant != null;
    return any
        ? new SpanFacts(type, shape, call, annotations, scopes, overrides, List.of(), constant)
        : null;
  }

  private TypeRef type(final SyntaxNode node) {
    if (Exprs.isExpression(node.kind())) {
      return TypeRefs.of(typer.typeOf(node));
    }
    if ("variableDeclaration".equals(node.kind())) {
      final UnitModel.Site site = siteOf(node);
      return site == null ? null : TypeRefs.of(typer.variable(site));
    }
    return null;
  }

  private UnitModel.Site siteOf(final SyntaxNode declaration) {
    final SyntaxNode name = TreeKit.child(declaration, "identifierOrKeyWord");
    final SyntaxNode identifier = name == null ? null : TreeKit.find(name, "identifier");
    return identifier == null ? null : model.site(identifier.span());
  }

  private TypeShape shape(final SyntaxNode node) {
    final Sym sym =
        switch (node.kind()) {
          case CLASS, "interfaceDeclaration", "enumDeclaration" -> declared(node);
          default -> Exprs.isExpression(node.kind()) ? ofType(typer.typeOf(node)) : null;
        };
    return sym == null ? null : shapeOf(sym);
  }

  private Sym declared(final SyntaxNode node) {
    final SyntaxNode name = TreeKit.child(node, "identifier");
    return name == null ? null : model.module().own(TreeKit.text(name));
  }

  private static Sym ofType(final Ty type) {
    return type instanceof Ty.Named named && named.ref() != null ? named.ref().sym() : null;
  }

  private TypeShape shapeOf(final Sym sym) {
    for (final Sym.Located located : sym.types()) {
      final Decl decl = located.decl();
      final TypeShape.Kind kind =
          switch (decl.kind()) {
            case CLASS -> TypeShape.Kind.CLASS;
            case INTERFACE -> TypeShape.Kind.INTERFACE;
            case ENUM -> TypeShape.Kind.ENUM;
            default -> null;
          };
      if (kind != null) {
        final List<TypeRef> supertypes = new ArrayList<>();
        for (final Ty base : bases(decl, located.scope())) {
          if (base instanceof Ty.Named named && named.ref() != null && named.ref().sym() != null) {
            supertypes.add(TypeRefs.of(base));
          }
        }
        final boolean isAbstract = decl.has(Decl.ABSTRACT) || kind == TypeShape.Kind.INTERFACE;
        return new TypeShape(kind, isAbstract, false, false, supertypes);
      }
    }
    return null;
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

  private CallTarget call(final SyntaxNode node) {
    if (!Exprs.SINGLE.equals(node.kind())) {
      return null;
    }
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.size() < 2) {
      return null;
    }
    final boolean constructs = "new".equals(Exprs.token(parts.get(0)));
    final var resolved = constructs ? typer.calls().constructed(node) : callOf(node, parts);
    return resolved
        .map(call -> TypeRefs.target(ownerName(call), call.name(), call.sig(), call.isStatic()))
        .orElse(null);
  }

  private java.util.Optional<CallTyper.Call> callOf(
      final SyntaxNode node, final List<SyntaxNode> parts) {
    final boolean named = Exprs.IDENTIFIER_NAME.equals(parts.get(0).kind());
    final boolean viaParen =
        named
            && parts.size() == 2
            && "("
                .equals(
                    Exprs.token(
                        TreeKit.parts(parts.get(1)).isEmpty()
                            ? parts.get(1)
                            : TreeKit.parts(parts.get(1)).get(0)));
    return typer.calls().isCall(parts) || viaParen
        ? typer.calls().call(node)
        : java.util.Optional.empty();
  }

  private String ownerName(final CallTyper.Call call) {
    return call.owner().isEmpty() ? model.unit().path() : call.owner();
  }

  private List<String> decorators(final SyntaxNode node) {
    return switch (node.kind()) {
      case CLASS -> MemberReader.decorators(TreeKit.child(node, "decoratorList"));
      case CLASS_ELEMENT -> MemberReader.decorators(TreeKit.child(node, "decoratorList"));
      case MEMBER -> {
        final SyntaxNode parent = model.parent(node);
        yield parent != null && CLASS_ELEMENT.equals(parent.kind())
            ? MemberReader.decorators(TreeKit.child(parent, "decoratorList"))
            : List.of();
      }
      default -> List.of();
    };
  }

  private List<String> enclosing(final SyntaxNode node) {
    final List<String> names = new ArrayList<>();
    SyntaxNode current = model.parent(node);
    while (current != null) {
      if (CLASS.equals(current.kind()) && !CLASS.equals(node.kind())) {
        names.addAll(MemberReader.decorators(TreeKit.child(current, "decoratorList")));
      }
      current = model.parent(current);
    }
    return names;
  }

  private List<CallTarget> overrides(final SyntaxNode node) {
    final SyntaxNode member = MEMBER.equals(node.kind()) ? node : null;
    final SyntaxNode name = member == null ? null : TreeKit.child(member, "propertyName");
    final SyntaxNode owner = member == null ? null : enclosingClass(member);
    if (name == null
        || owner == null
        || TreeKit.hasToken(
            TreeKit.child(member, "propertyMemberBase") == null
                ? member
                : TreeKit.child(member, "propertyMemberBase"),
            "static")) {
      return List.of();
    }
    final Sym sym = declared(owner);
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

  private ConstantValue constant(final SyntaxNode node) {
    if (!Exprs.isExpression(node.kind())) {
      return null;
    }
    final Ty type = typer.typeOf(node);
    if (!(type instanceof Ty.Named named) || !named.args().isEmpty()) {
      return null;
    }
    final String name = named.name();
    if (name.startsWith("\"")) {
      return new ConstantValue(name.substring(1, name.length() - 1));
    }
    if ("true".equals(name) || "false".equals(name)) {
      return new ConstantValue(Boolean.parseBoolean(name));
    }
    return number(name);
  }

  private static ConstantValue number(final String text) {
    final String cleaned = text.replace("_", "");
    try {
      if (cleaned.matches("-?\\d+")) {
        return new ConstantValue(Long.parseLong(cleaned));
      }
      if (cleaned.matches("-?0[xX][0-9a-fA-F]+")) {
        return new ConstantValue(Long.decode(cleaned));
      }
      return cleaned.matches("-?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")
          ? new ConstantValue(Double.parseDouble(cleaned))
          : null;
    } catch (NumberFormatException overflow) {
      return null;
    }
  }

  Program program() {
    return program;
  }
}
