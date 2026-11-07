package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads one Java type declaration into a {@link JvmClass} and its members. */
final class JavaClassReader {
  private static final Set<String> TYPE_KINDS =
      Set.of(
          "classDeclaration",
          "interfaceDeclaration",
          "enumDeclaration",
          "recordDeclaration",
          "annotationTypeDeclaration");
  private static final String OBJECT = "java.lang.Object";
  private static final String IDENTIFIER = "identifier";
  private static final String EXTENDS = "extends";
  private static final String PERMITS = "permits";
  private static final String TYPE_TYPE = "typeType";
  private static final String TYPE_LIST = "typeList";
  private static final String COMPACT = "compactConstructorDeclaration";

  private JavaClassReader() {}

  /**
   * A declaration with its modifiers.
   *
   * @param outer the node whose span includes the modifiers
   * @param modifiers the modifier nodes
   * @param declaration the declaration node
   */
  record Entry(SyntaxNode outer, List<SyntaxNode> modifiers, SyntaxNode declaration) {
    String name() {
      return Syntax.child(declaration, IDENTIFIER).map(Syntax::text).orElse("");
    }
  }

  static boolean isType(final SyntaxNode node) {
    return TYPE_KINDS.contains(node.kind());
  }

  /** Returns the entry of a top-level {@code typeDeclaration}. */
  static Optional<Entry> topLevel(final SyntaxNode holder) {
    return holder.children().stream()
        .filter(JavaClassReader::isType)
        .findFirst()
        .map(
            declaration ->
                new Entry(
                    holder, Syntax.children(holder, "classOrInterfaceModifier"), declaration));
  }

  /** Returns the entries of the body of a type declaration. */
  static List<Entry> bodyEntries(final SyntaxNode type) {
    final List<Entry> entries = new ArrayList<>();
    final List<SyntaxNode> declarations = new ArrayList<>();
    for (final SyntaxNode child : type.children()) {
      if (Set.of("classBody", "interfaceBody", "recordBody", "enumBodyDeclarations")
          .contains(child.kind())) {
        declarations.addAll(child.children());
      }
    }
    for (final SyntaxNode member : declarations) {
      entryOf(member).ifPresent(entries::add);
    }
    return entries;
  }

  private static Optional<Entry> entryOf(final SyntaxNode member) {
    if (COMPACT.equals(member.kind())) {
      return Optional.of(new Entry(member, Syntax.children(member, "modifier"), member));
    }
    final List<SyntaxNode> modifiers = Syntax.children(member, "modifier");
    final Optional<SyntaxNode> block = Syntax.child(member, "block");
    if (block.isPresent()) {
      return Optional.of(new Entry(member, modifiers, block.get()));
    }
    return member.children().stream()
        .filter(
            child ->
                child.kind().endsWith("MemberDeclaration")
                    || "memberDeclaration".equals(child.kind()))
        .findFirst()
        .flatMap(inner -> inner.children().stream().findFirst())
        .map(declaration -> new Entry(member, modifiers, declaration));
  }

  /** Reads {@code entry}, whose binary name is {@code prefix} followed by its name. */
  static SourceModel.ClassDecl read(final Entry entry, final String prefix, final TypeScope outerScope) {
    final SyntaxNode declaration = entry.declaration();
    final String binary = prefix + entry.name();
    final List<String> variables =
        Syntax.child(declaration, "typeParameters").stream()
            .flatMap(node -> Syntax.children(node, "typeParameter").stream())
            .map(parameter -> Syntax.text(Syntax.child(parameter, IDENTIFIER).orElse(parameter)))
            .toList();
    final TypeScope scope = outerScope.inClass(binary, variables);
    final JvmClass.Kind kind = kindOf(declaration);
    final List<JType.ClassType> supertypes = new ArrayList<>();
    final Optional<JType.ClassType> superclass =
        supers(declaration, kind, binary, scope, supertypes);
    final JavaMemberReader members = new JavaMemberReader(scope, binary, kind);
    final List<SourceModel.Member> read = members.read(declaration);
    final List<SourceModel.ClassDecl> nested = new ArrayList<>();
    for (final Entry inner : bodyEntries(declaration)) {
      if (isType(inner.declaration())) {
        nested.add(read(inner, binary + '$', scope));
      }
    }
    int flags = JavaModifiers.flags(entry.modifiers());
    if (kind == JvmClass.Kind.RECORD) {
      flags |= Flags.FINAL;
    }
    final JvmClass jvm =
        new JvmClass(
            binary,
            kind,
            flags,
            superclass,
            supertypes,
            JavaTypes.typeParameters(declaration, scope),
            read.stream().flatMap(member -> member.field().stream()).toList(),
            members.methods(read, kind == JvmClass.Kind.CLASS),
            JavaModifiers.annotations(entry.modifiers(), scope),
            nested.stream().map(inner -> inner.jvm().name()).toList(),
            JavaModifiers.has(entry.modifiers(), "sealed"),
            false);
    return new SourceModel.ClassDecl(jvm, declaration, entry.outer(), scope, read, nested);
  }

  private static JvmClass.Kind kindOf(final SyntaxNode declaration) {
    return switch (declaration.kind()) {
      case "interfaceDeclaration" -> JvmClass.Kind.INTERFACE;
      case "enumDeclaration" -> JvmClass.Kind.ENUM;
      case "recordDeclaration" -> JvmClass.Kind.RECORD;
      case "annotationTypeDeclaration" -> JvmClass.Kind.ANNOTATION;
      default -> JvmClass.Kind.CLASS;
    };
  }

  private static Optional<JType.ClassType> supers(
      final SyntaxNode declaration,
      final JvmClass.Kind kind,
      final String binary,
      final TypeScope scope,
      final List<JType.ClassType> interfaces) {
    Optional<JType.ClassType> superclass = Optional.empty();
    String keyword = "";
    for (final SyntaxNode child : declaration.children()) {
      final String token = Syntax.token(child);
      if (token.isEmpty()) {
        final boolean extending = EXTENDS.equals(keyword);
        if (TYPE_TYPE.equals(child.kind()) && extending) {
          superclass = classTypeOf(child, scope);
        } else if (TYPE_LIST.equals(child.kind()) && !PERMITS.equals(keyword)) {
          JavaTypes.typeList(child, scope).stream()
              .filter(JType.ClassType.class::isInstance)
              .map(JType.ClassType.class::cast)
              .forEach(interfaces::add);
        }
      } else {
        keyword = token;
      }
    }
    return implicitSuperclass(kind, binary, superclass);
  }

  private static Optional<JType.ClassType> classTypeOf(
      final SyntaxNode written, final TypeScope scope) {
    return JavaTypes.type(written, scope)
        .filter(JType.ClassType.class::isInstance)
        .map(JType.ClassType.class::cast);
  }

  private static Optional<JType.ClassType> implicitSuperclass(
      final JvmClass.Kind kind, final String binary, final Optional<JType.ClassType> declared) {
    return switch (kind) {
      case CLASS -> declared.or(() -> Optional.of(JType.ClassType.of(OBJECT)));
      case ENUM ->
          Optional.of(new JType.ClassType("java.lang.Enum", List.of(JType.ClassType.of(binary))));
      case RECORD -> Optional.of(JType.ClassType.of("java.lang.Record"));
      default -> Optional.empty();
    };
  }
}
