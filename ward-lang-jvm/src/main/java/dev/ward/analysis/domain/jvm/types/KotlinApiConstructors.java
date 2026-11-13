package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Reads the constructors of a Kotlin class and the members the compiler generates: the {@code
 * componentN} and {@code copy} functions of a {@code data class} and the entries of an enum.
 */
final class KotlinApiConstructors {
  private static final String PRIMARY = "primaryConstructor";
  private static final String NAME = "simpleIdentifier";

  private final String path;
  private final KotlinApiRefs refs;

  KotlinApiConstructors(final String path, final KotlinApiRefs refs) {
    this.path = path;
    this.refs = refs;
  }

  /** Returns the parameters of the primary constructor of {@code node}. */
  static List<SyntaxNode> parameters(final SyntaxNode node) {
    return Syntax.child(node, PRIMARY)
        .flatMap(primary -> Syntax.child(primary, "classParameters"))
        .map(list -> Syntax.children(list, "classParameter"))
        .orElse(List.of());
  }

  /** Returns whether a constructor parameter declares a property. */
  static boolean isProperty(final SyntaxNode parameter) {
    return Syntax.hasToken(parameter, "val") || Syntax.hasToken(parameter, "var");
  }

  /**
   * Returns the constructors of a class: the primary one, the secondary ones, or the implicit one
   * when the class writes none.
   */
  List<ApiMember> of(final SyntaxNode node, final List<SyntaxNode> body) {
    final Optional<SyntaxNode> primary = Syntax.child(node, PRIMARY);
    final List<SyntaxNode> secondaries =
        body.stream()
            .filter(declaration -> "secondaryConstructor".equals(declaration.kind()))
            .toList();
    final List<ApiMember> found = new ArrayList<>();
    if (primary.isPresent() || secondaries.isEmpty()) {
      found.add(
          constructor(
              primary.orElse(node),
              primary
                  .map(KotlinApiModifiers::of)
                  .map(KotlinApiModifiers::visibility)
                  .orElse(ApiVisibility.PUBLIC),
              primaryParameters(node)));
    }
    secondaries.stream().map(this::secondary).forEach(found::add);
    return found;
  }

  private List<ApiParameter> primaryParameters(final SyntaxNode node) {
    return parameters(node).stream()
        .map(parameter -> KotlinApiMembers.parameter(parameter, refs))
        .toList();
  }

  private ApiMember secondary(final SyntaxNode secondary) {
    return constructor(
        secondary,
        KotlinApiModifiers.of(secondary).visibility(),
        Syntax.children(secondary, "functionValueParameters").stream()
            .flatMap(list -> Syntax.children(list, "functionValueParameter").stream())
            .map(holder -> KotlinApiMembers.parameter(holder, refs))
            .toList());
  }

  private ApiMember constructor(
      final SyntaxNode at, final ApiVisibility visibility, final List<ApiParameter> parameters) {
    return new ApiMember(
        ApiMember.Kind.CONSTRUCTOR,
        ApiMember.CONSTRUCTOR,
        visibility,
        false,
        false,
        false,
        false,
        false,
        List.of(),
        parameters,
        KotlinApiTypeRefs.unit(),
        location(at));
  }

  /** The {@code componentN} functions and the {@code copy} function of a data class. */
  List<ApiMember> dataMembers(final SyntaxNode node, final String binary) {
    final List<ApiParameter> copied =
        primaryParameters(node).stream()
            .map(parameter -> new ApiParameter(parameter.type(), true))
            .toList();
    final List<ApiMember> found =
        new ArrayList<>(
            IntStream.range(0, copied.size())
                .mapToObj(
                    index ->
                        generated(
                            "component" + (index + 1), List.of(), copied.get(index).type(), node))
                .toList());
    found.add(generated("copy", copied, KotlinApiTypeRefs.named(binary), node));
    return found;
  }

  private ApiMember generated(
      final String name,
      final List<ApiParameter> parameters,
      final ApiTypeRef type,
      final SyntaxNode at) {
    return new ApiMember(
        ApiMember.Kind.METHOD,
        name,
        ApiVisibility.PUBLIC,
        false,
        true,
        false,
        false,
        false,
        List.of(),
        parameters,
        type,
        location(at));
  }

  /** Returns the entries of an enum class as constants of the enum. */
  List<ApiMember> entries(final SyntaxNode node, final String binary) {
    return Syntax.children(node, "enumClassBody").stream()
        .flatMap(body -> Syntax.children(body, "enumEntries").stream())
        .flatMap(list -> Syntax.children(list, "enumEntry").stream())
        .map(entry -> entry(entry, binary))
        .toList();
  }

  private ApiMember entry(final SyntaxNode entry, final String binary) {
    return new ApiMember(
        ApiMember.Kind.FIELD,
        Syntax.child(entry, NAME).map(Syntax::text).orElse(""),
        ApiVisibility.PUBLIC,
        true,
        true,
        false,
        false,
        false,
        List.of(),
        List.of(),
        KotlinApiTypeRefs.named(binary),
        location(entry));
  }

  private ApiLocation location(final SyntaxNode node) {
    return new ApiLocation(path, node.span().startLine(), node.span().startColumn());
  }
}
