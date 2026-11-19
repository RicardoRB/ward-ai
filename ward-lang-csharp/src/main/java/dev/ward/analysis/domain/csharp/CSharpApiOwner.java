package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiVisibility;

/**
 * The type whose members are read.
 *
 * @param path the file that declares it
 * @param isInterface whether it is an interface, whose members are public unless stated
 * @param isStruct whether it is a struct, which no member of can be overridden
 * @param refs reads the types of its members, with its type variables in scope
 */
record CSharpApiOwner(String path, boolean isInterface, boolean isStruct, CSharpApiRefs refs) {
  /** Returns the accessibility of a member with no accessibility modifier. */
  ApiVisibility defaultVisibility() {
    return isInterface ? ApiVisibility.PUBLIC : ApiVisibility.PRIVATE;
  }
}
