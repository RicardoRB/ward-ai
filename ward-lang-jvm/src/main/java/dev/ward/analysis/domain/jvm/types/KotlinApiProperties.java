package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads a Kotlin property as its accessors: a getter, and a setter for a {@code var}, so a {@code
 * var} that becomes a {@code val} loses a method.
 */
final class KotlinApiProperties {
  private static final String IS = "is";
  private static final String TYPE = "type";
  private static final String BLOCK = "block";
  private static final String NAME = "simpleIdentifier";

  private final String path;
  private final KotlinApiRefs refs;
  private final KotlinApiModality modality;

  KotlinApiProperties(
      final String path, final KotlinApiRefs refs, final KotlinApiModality modality) {
    this.path = path;
    this.refs = refs;
    this.modality = modality;
  }

  /** A property with what its accessors need. */
  private record Property(
      String name,
      ApiTypeRef type,
      boolean mutable,
      KotlinApiModifiers modifiers,
      ApiVisibility setterVisibility,
      List<ApiTypeParameter> typeParameters,
      boolean getterBody,
      boolean setterBody) {}

  /** Returns the accessors of a {@code val} or {@code var} constructor parameter. */
  List<ApiMember> ofParameter(final SyntaxNode parameter) {
    final KotlinApiModifiers modifiers = KotlinApiModifiers.of(parameter);
    final ApiTypeRef type =
        Syntax.child(parameter, TYPE).map(refs::of).orElseGet(KotlinApiTypeRefs::inferred);
    return accessors(
        new Property(
            Syntax.child(parameter, NAME).map(Syntax::text).orElse(""),
            type,
            Syntax.hasToken(parameter, "var"),
            modifiers,
            modifiers.visibility(),
            List.of(),
            false,
            false),
        List.of(),
        location(parameter));
  }

  /** Returns the accessors of a property declaration, whose variable is {@code variable}. */
  List<ApiMember> ofDeclaration(
      final SyntaxNode property,
      final SyntaxNode variable,
      final KotlinApiTypeParameters.Declared declared,
      final Optional<SyntaxNode> receiver) {
    final Optional<SyntaxNode> getter = Syntax.child(property, "getter");
    final Optional<SyntaxNode> setter = Syntax.child(property, "setter");
    final KotlinApiModifiers modifiers = KotlinApiModifiers.of(property);
    final ApiTypeRef type =
        Syntax.child(variable, TYPE)
            .or(() -> getter.flatMap(found -> Syntax.child(found, TYPE)))
            .map(declared.refs()::of)
            .orElseGet(KotlinApiTypeRefs::inferred);
    return accessors(
        new Property(
            Syntax.child(variable, NAME).map(Syntax::text).orElse(""),
            type,
            Syntax.hasToken(property, "var"),
            modifiers,
            setter
                .map(found -> KotlinApiModifiers.of(found).visibilityOr(modifiers.visibility()))
                .orElse(modifiers.visibility()),
            declared.parameters(),
            getter.map(KotlinApiProperties::hasBody).orElse(false),
            setter.map(KotlinApiProperties::hasBody).orElse(false)),
        receiver.map(declared.refs()::of).map(found -> new ApiParameter(found, false)).stream()
            .toList(),
        location(property));
  }

  private static boolean hasBody(final SyntaxNode accessor) {
    return Syntax.child(accessor, BLOCK).isPresent() || Syntax.hasToken(accessor, "=");
  }

  /** One accessor of a property: a getter or a setter. */
  private record Accessor(
      String name,
      List<ApiParameter> parameters,
      ApiTypeRef type,
      boolean body,
      ApiVisibility visibility) {}

  private List<ApiMember> accessors(
      final Property property, final List<ApiParameter> receiver, final ApiLocation at) {
    if (property.name().isEmpty()) {
      return List.of();
    }
    final List<ApiMember> found = new ArrayList<>();
    found.add(
        member(
            property,
            new Accessor(
                getterName(property.name()),
                receiver,
                property.type(),
                property.getterBody(),
                property.modifiers().visibility()),
            at));
    if (property.mutable()) {
      final List<ApiParameter> parameters = new ArrayList<>(receiver);
      parameters.add(new ApiParameter(property.type(), false));
      found.add(
          member(
              property,
              new Accessor(
                  setterName(property.name()),
                  parameters,
                  KotlinApiTypeRefs.unit(),
                  property.setterBody(),
                  property.setterVisibility()),
              at));
    }
    return found;
  }

  private ApiMember member(final Property property, final Accessor accessor, final ApiLocation at) {
    return new ApiMember(
        ApiMember.Kind.METHOD,
        accessor.name(),
        accessor.visibility(),
        false,
        modality.isFinal(property.modifiers()),
        modality.isAbstract(property.modifiers(), accessor.body()),
        modality.isDefault(property.modifiers(), accessor.body()),
        false,
        property.typeParameters(),
        accessor.parameters(),
        accessor.type(),
        at);
  }

  /** A property named {@code isX} has the getter {@code isX}; any other has {@code getX}. */
  private static String getterName(final String name) {
    return isBooleanName(name) ? name : "get" + capitalized(name);
  }

  private static String setterName(final String name) {
    return "set" + (isBooleanName(name) ? name.substring(IS.length()) : capitalized(name));
  }

  private static boolean isBooleanName(final String name) {
    return name.length() > IS.length()
        && name.startsWith(IS)
        && Character.isUpperCase(name.charAt(IS.length()));
  }

  private static String capitalized(final String name) {
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private ApiLocation location(final SyntaxNode node) {
    return new ApiLocation(path, node.span().startLine(), node.span().startColumn());
  }
}
