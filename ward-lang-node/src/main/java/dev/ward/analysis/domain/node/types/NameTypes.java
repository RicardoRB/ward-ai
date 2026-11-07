package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Types names: variables, parameters, functions, classes, imports and globals. */
final class NameTypes {
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final String UNDEFINED = "undefined";

  private final Function<SyntaxNode, TsType> typer;
  private final UnitModel model;
  private final Program program;
  private final ConstructTyper calls;
  private final Map<SyntaxNode, TsType> contextual = new IdentityHashMap<>();

  NameTypes(
      final Function<SyntaxNode, TsType> typer,
      final UnitModel model,
      final Program program,
      final ConstructTyper calls) {
    this.typer = typer;
    this.model = model;
    this.program = program;
    this.calls = calls;
  }

  /** Records the type that the context gives to the parameter declared by {@code node}. */
  void contextualize(final SyntaxNode parameter, final TsType type) {
    contextual.putIfAbsent(parameter, type);
  }

  /** Returns the type of a name used as an expression. */
  TsType identifier(final SyntaxNode node) {
    final String name = TreeText.text(node);
    if ("true".equals(name) || "false".equals(name) || "null".equals(name)) {
      return TsType.named(name);
    }
    final NameReference reference = model.reference(node.span());
    if (reference != null && reference.resolved().isPresent()) {
      return bindingValue(reference.resolved().get(), name);
    }
    return switch (name) {
      case UNDEFINED -> TsType.named(UNDEFINED);
      case "NaN", "Infinity" -> TsType.named(Literals.NUMBER);
      case "this" -> calls.thisType(node);
      default -> MemberValues.valueOf(program.globals().find(name), name);
    };
  }

  private TsType bindingValue(final Binding binding, final String name) {
    if (binding.kind() == BindingKind.IMPORT) {
      return MemberValues.valueOf(model.module().find(name), name);
    }
    final UnitModel.Site site = model.site(binding.declaration());
    if (site == null) {
      return TsType.UNKNOWN;
    }
    return switch (site.kind()) {
      case VARIABLE -> variable(site);
      case PARAMETER -> parameter(site.node());
      case FUNCTION, CLASS -> MemberValues.valueOf(model.module().find(name), name);
    };
  }

  /** Returns the type of the variable that a {@code variableDeclaration} declares. */
  TsType variable(final UnitModel.Site site) {
    final SyntaxNode annotation = TreeKit.child(site.node(), TYPE_ANNOTATION);
    if (annotation != null) {
      return Instantiate.apply(SigReader.annotated(annotation), Map.of(), model.module());
    }
    final List<SyntaxNode> values = TreeKit.children(site.node(), Exprs.SINGLE);
    if (!TreeKit.hasToken(site.node(), "=") || values.isEmpty()) {
      return TsType.UNKNOWN;
    }
    final TsType type = typer.apply(values.get(values.size() - 1));
    return site.isConst() ? type : Literals.widen(type);
  }

  /** Returns the type of the parameter that {@code node} declares. */
  TsType parameter(final SyntaxNode node) {
    final SyntaxNode annotation = TreeKit.child(node, TYPE_ANNOTATION);
    if (annotation != null) {
      return Instantiate.apply(SigReader.annotated(annotation), Map.of(), model.module());
    }
    final List<SyntaxNode> values = TreeKit.children(node, Exprs.SINGLE);
    if (TreeKit.hasToken(node, "=") && !values.isEmpty()) {
      return Literals.widen(typer.apply(values.get(values.size() - 1)));
    }
    return contextual.getOrDefault(node, TsType.UNKNOWN);
  }
}
