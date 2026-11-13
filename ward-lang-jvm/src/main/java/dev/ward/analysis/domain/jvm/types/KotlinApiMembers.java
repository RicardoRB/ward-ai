package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the functions and properties of a Kotlin class, object or file into API members. */
final class KotlinApiMembers {
  private static final String PARAMETER_LIST = "functionValueParameters";
  private static final String PARAMETER_HOLDER = "functionValueParameter";
  private static final String TYPE = "type";
  private static final String RECEIVER_TYPE = "receiverType";
  private static final String BODY = "functionBody";

  private final String path;
  private final KotlinApiRefs refs;
  private final KotlinApiModality modality;
  private final KotlinApiProperties properties;

  KotlinApiMembers(final String path, final KotlinApiRefs refs, final boolean inInterface) {
    this.path = path;
    this.refs = refs;
    this.modality = new KotlinApiModality(inInterface);
    this.properties = new KotlinApiProperties(path, refs, modality);
  }

  /** Returns the members a function or property declaration introduces; none for other nodes. */
  List<ApiMember> of(final SyntaxNode declaration) {
    return switch (declaration.kind()) {
      case "functionDeclaration" -> function(declaration).stream().toList();
      case "propertyDeclaration" -> property(declaration);
      default -> List.of();
    };
  }

  /** Returns the accessors of a {@code val} or {@code var} constructor parameter. */
  List<ApiMember> constructorProperty(final SyntaxNode parameter) {
    return properties.ofParameter(parameter);
  }

  private List<ApiMember> property(final SyntaxNode property) {
    return Syntax.child(property, "variableDeclaration")
        .map(
            variable ->
                properties.ofDeclaration(
                    property, variable, declared(property), receiver(property)))
        .orElse(List.of());
  }

  private Optional<ApiMember> function(final SyntaxNode function) {
    final String name = Syntax.child(function, "identifier").map(Syntax::text).orElse("");
    if (name.isEmpty()) {
      return Optional.empty();
    }
    final KotlinApiModifiers modifiers = KotlinApiModifiers.of(function);
    final KotlinApiTypeParameters.Declared declared = declared(function);
    final boolean body = Syntax.child(function, BODY).isPresent();
    final List<ApiParameter> parameters = new ArrayList<>();
    receiver(function)
        .map(declared.refs()::of)
        .map(type -> new ApiParameter(type, false))
        .ifPresent(parameters::add);
    parameters.addAll(parameters(function, declared.refs()));
    return Optional.of(
        new ApiMember(
            ApiMember.Kind.METHOD,
            name,
            modifiers.visibility(),
            false,
            modality.isFinal(modifiers),
            modality.isAbstract(modifiers, body),
            modality.isDefault(modifiers, body),
            hasVararg(function),
            declared.parameters(),
            parameters,
            returns(function, declared.refs()),
            location(function)));
  }

  private KotlinApiTypeParameters.Declared declared(final SyntaxNode owner) {
    final KotlinApiRefs scoped =
        refs.withScope(refs.scope().withTypeVariables(KotlinApiTypeParameters.names(owner)));
    return KotlinApiTypeParameters.declare(owner, "M", scoped);
  }

  private static Optional<SyntaxNode> receiver(final SyntaxNode function) {
    for (final SyntaxNode child : function.children()) {
      if (PARAMETER_LIST.equals(child.kind())) {
        return Optional.empty();
      }
      if (TYPE.equals(child.kind()) || RECEIVER_TYPE.equals(child.kind())) {
        return Optional.of(child);
      }
    }
    return Optional.empty();
  }

  private static List<ApiParameter> parameters(
      final SyntaxNode function, final KotlinApiRefs inner) {
    return Syntax.children(function, PARAMETER_LIST).stream()
        .flatMap(list -> Syntax.children(list, PARAMETER_HOLDER).stream())
        .map(holder -> parameter(holder, inner))
        .toList();
  }

  /** Returns a parameter; the type of a {@code vararg} is the array of its element type. */
  static ApiParameter parameter(final SyntaxNode holder, final KotlinApiRefs inner) {
    final ApiTypeRef type =
        Syntax.find(holder, TYPE).map(inner::of).orElseGet(KotlinApiTypeRefs::inferred);
    final boolean vararg = KotlinApiModifiers.of(holder).has("vararg");
    return new ApiParameter(
        vararg ? KotlinApiTypeRefs.array(type) : type, Syntax.hasToken(holder, "="));
  }

  private static boolean hasVararg(final SyntaxNode function) {
    return Syntax.children(function, PARAMETER_LIST).stream()
        .flatMap(list -> Syntax.children(list, PARAMETER_HOLDER).stream())
        .anyMatch(holder -> KotlinApiModifiers.of(holder).has("vararg"));
  }

  /** The declared return type; {@code Unit} for a block or no body, unknown for {@code = expr}. */
  private static ApiTypeRef returns(final SyntaxNode function, final KotlinApiRefs inner) {
    boolean afterParameters = false;
    for (final SyntaxNode child : function.children()) {
      if (PARAMETER_LIST.equals(child.kind())) {
        afterParameters = true;
      } else if (afterParameters && TYPE.equals(child.kind())) {
        return inner.of(child);
      }
    }
    final Optional<SyntaxNode> body = Syntax.child(function, BODY);
    final boolean expression = body.isPresent() && Syntax.child(body.get(), "block").isEmpty();
    return expression ? KotlinApiTypeRefs.inferred() : KotlinApiTypeRefs.unit();
  }

  private ApiLocation location(final SyntaxNode node) {
    return new ApiLocation(path, node.span().startLine(), node.span().startColumn());
  }
}
