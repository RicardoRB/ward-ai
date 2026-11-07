package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.types.TypeShape;

/**
 * A type the project declares, identified by its module and its name within that module.
 *
 * @param module the module that declares it, or {@link SwiftProjectLayout#IMPLICIT_MODULE}
 * @param file the project-relative path of the declaring file
 * @param qualifiedName the name within the module, with nested types joined by dots
 * @param parentScope the qualified name of the enclosing type, empty at file level
 * @param kind the kind of declaration; a struct and an actor are classes, a protocol an interface
 * @param isFinal whether no type can inherit from it
 * @param isAbstract whether it is a protocol
 * @param isGeneric whether it declares generic parameters
 */
record SwiftTypeDecl(
    String module,
    String file,
    String qualifiedName,
    String parentScope,
    TypeShape.Kind kind,
    boolean isFinal,
    boolean isAbstract,
    boolean isGeneric) {}
