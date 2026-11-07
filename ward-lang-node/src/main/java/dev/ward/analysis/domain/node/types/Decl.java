package dev.ward.analysis.domain.node.types;

import java.util.List;

/**
 * One declaration of a name. Declarations of one name merge, as TypeScript merges interfaces,
 * overloads and namespaces.
 *
 * @param kind what the declaration is
 * @param name the declared name
 * @param header the type parameters, heritage and signatures
 * @param members the members of an interface, class, enum or object type
 * @param type the aliased type of an alias, the type of a variable, or {@link TsType#UNKNOWN}
 * @param body the declarations of a namespace, empty for any other kind
 * @param flags {@link #EXPORTED}, {@link #ABSTRACT} and {@link #DEFAULT}
 */
public record Decl(
    Kind kind,
    String name,
    Header header,
    List<Member> members,
    TsType type,
    List<Decl> body,
    int flags) {
  /** The declaration is exported. */
  public static final int EXPORTED = 1;

  /** The class is abstract. */
  public static final int ABSTRACT = EXPORTED << 1;

  /** The declaration is the default export. */
  public static final int DEFAULT = ABSTRACT << 1;

  /** The constant is declared with {@code const}. */
  public static final int CONST = DEFAULT << 1;

  /** The namespace stands for a whole module, imported with {@code * as}. */
  public static final int MODULE = CONST << 1;

  /** What a declaration declares. */
  public enum Kind {
    /** An interface. */
    INTERFACE,
    /** A class. */
    CLASS,
    /** An enum. */
    ENUM,
    /** A type alias. */
    ALIAS,
    /** A function; its signatures are in the header. */
    FUNCTION,
    /** A variable or constant. */
    VARIABLE,
    /** A namespace or module. */
    NAMESPACE
  }

  /**
   * What precedes the members of a declaration.
   *
   * @param typeParams the type parameter names
   * @param extendsTypes the superclass or extended interfaces
   * @param implementsTypes the implemented interfaces
   * @param calls the call signatures, or the overloads of a function
   * @param constructs the construct signatures, or the constructors of a class
   * @param decorators the decorator names, without {@code @}
   */
  public record Header(
      List<String> typeParams,
      List<TsType> extendsTypes,
      List<TsType> implementsTypes,
      List<Sig> calls,
      List<Sig> constructs,
      List<String> decorators) {
    /** The header of a declaration that has none. */
    public static final Header NONE =
        new Header(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    /** Copies the lists. */
    public Header {
      typeParams = List.copyOf(typeParams);
      extendsTypes = List.copyOf(extendsTypes);
      implementsTypes = List.copyOf(implementsTypes);
      calls = List.copyOf(calls);
      constructs = List.copyOf(constructs);
      decorators = List.copyOf(decorators);
    }
  }

  /** Copies the lists. */
  public Decl {
    members = List.copyOf(members);
    body = List.copyOf(body);
  }

  /** Returns whether the declaration has {@code flag}. */
  public boolean has(final int flag) {
    return (flags & flag) != 0;
  }

  /** Returns whether the name is a type: an interface, class, enum, alias or namespace. */
  public boolean isType() {
    return kind != Kind.FUNCTION && kind != Kind.VARIABLE;
  }

  /** Returns whether the name is a value: a class, enum, function, variable or namespace. */
  public boolean isValue() {
    return kind != Kind.INTERFACE && kind != Kind.ALIAS;
  }
}
