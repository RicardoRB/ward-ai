package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Turns the classes, interfaces and objects of one Kotlin file into API types. */
final class KotlinApiTypeReader {
  private static final String CLASS = "classDeclaration";
  private static final String OBJECT = "objectDeclaration";
  private static final String COMPANION = "companionObject";
  private static final String NAME = "simpleIdentifier";
  private static final Set<String> TYPE_KINDS = Set.of(CLASS, OBJECT, COMPANION);
  private static final Set<String> IMPLICIT_SUPERTYPES = Set.of("java.lang.Object");

  private final String path;

  KotlinApiTypeReader(final String path) {
    this.path = path;
  }

  /** Returns whether {@code node} declares a class, interface or object. */
  static boolean isType(final SyntaxNode node) {
    return TYPE_KINDS.contains(node.kind());
  }

  /** Returns the simple name of a class, interface or object declaration. */
  static String nameOf(final SyntaxNode node) {
    return Syntax.child(node, NAME)
        .map(Syntax::text)
        .orElse(COMPANION.equals(node.kind()) ? "Companion" : "");
  }

  /** Returns the declarations in the body of a class, object or enum, in source order. */
  static List<SyntaxNode> declarations(final SyntaxNode type) {
    return Stream.concat(
            Syntax.children(type, "classBody").stream(),
            Syntax.children(type, "enumClassBody").stream())
        .flatMap(body -> Syntax.children(body, "classMemberDeclaration").stream())
        .map(member -> member.children().getFirst())
        .toList();
  }

  /**
   * Adds the API type of {@code node}, then its member types, to {@code found}.
   *
   * @param binary the binary name, with {@code $} before a member type
   * @param outer the references of the enclosing scope
   */
  void read(
      final SyntaxNode node,
      final String binary,
      final KotlinApiRefs outer,
      final List<ApiType> found) {
    final KotlinApiModifiers modifiers = KotlinApiModifiers.of(node);
    final ApiType.Kind kind = kind(node, modifiers);
    final KotlinApiRefs scoped =
        outer.withScope(outer.scope().inClass(binary, KotlinApiTypeParameters.names(node)));
    final KotlinApiTypeParameters.Declared declared =
        KotlinApiTypeParameters.declare(node, "C", scoped);
    final boolean isInterface = kind == ApiType.Kind.INTERFACE;
    final List<SyntaxNode> body = declarations(node);
    final boolean sealed = modifiers.has("sealed");
    found.add(
        new ApiType(
            ApiTypeRefs.source(binary),
            kind,
            modifiers.visibility(),
            isFinal(kind, modifiers),
            sealed,
            modifiers.has("abstract") || sealed || isInterface,
            declared.parameters(),
            supertypes(node, declared.refs()),
            members(node, binary, kind, body, declared.refs()),
            location(node)));
    body.stream()
        .filter(KotlinApiTypeReader::isType)
        .forEach(nested -> read(nested, binary + '$' + nameOf(nested), declared.refs(), found));
  }

  private static ApiType.Kind kind(final SyntaxNode node, final KotlinApiModifiers modifiers) {
    if (!CLASS.equals(node.kind())) {
      return ApiType.Kind.OBJECT;
    }
    if (Syntax.hasToken(node, "interface")) {
      return ApiType.Kind.INTERFACE;
    }
    if (modifiers.has("enum")) {
      return ApiType.Kind.ENUM;
    }
    return modifiers.has("annotation") ? ApiType.Kind.ANNOTATION : ApiType.Kind.CLASS;
  }

  /** A class is closed unless {@code open}, {@code abstract} or {@code sealed}. */
  private static boolean isFinal(final ApiType.Kind kind, final KotlinApiModifiers modifiers) {
    return switch (kind) {
      case INTERFACE -> false;
      case CLASS ->
          !(modifiers.has("open") || modifiers.has("abstract") || modifiers.has("sealed"));
      default -> true;
    };
  }

  private List<ApiTypeRef> supertypes(final SyntaxNode node, final KotlinApiRefs refs) {
    return Syntax.child(node, "delegationSpecifiers").stream()
        .flatMap(list -> Syntax.children(list, "delegationSpecifier").stream())
        .flatMap(specifier -> Syntax.find(specifier, "userType").stream())
        .map(refs::of)
        .filter(type -> !IMPLICIT_SUPERTYPES.contains(type.erasure()))
        .toList();
  }

  private List<ApiMember> members(
      final SyntaxNode node,
      final String binary,
      final ApiType.Kind kind,
      final List<SyntaxNode> body,
      final KotlinApiRefs refs) {
    final KotlinApiMembers reader =
        new KotlinApiMembers(path, refs, kind == ApiType.Kind.INTERFACE);
    final KotlinApiConstructors generated = new KotlinApiConstructors(path, refs);
    final List<ApiMember> found = new ArrayList<>();
    if (kind == ApiType.Kind.CLASS) {
      found.addAll(generated.of(node, body));
      KotlinApiConstructors.parameters(node).stream()
          .filter(KotlinApiConstructors::isProperty)
          .forEach(parameter -> found.addAll(reader.constructorProperty(parameter)));
      if (KotlinApiModifiers.of(node).has("data")) {
        found.addAll(generated.dataMembers(node, binary));
      }
    }
    if (kind == ApiType.Kind.ENUM) {
      found.addAll(generated.entries(node, binary));
    }
    body.forEach(declaration -> found.addAll(reader.of(declaration)));
    return found;
  }

  private ApiLocation location(final SyntaxNode node) {
    return new ApiLocation(path, node.span().startLine(), node.span().startColumn());
  }
}
