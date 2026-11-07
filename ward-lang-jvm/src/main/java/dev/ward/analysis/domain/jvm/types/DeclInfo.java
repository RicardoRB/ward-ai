package dev.ward.analysis.domain.jvm.types;

import java.util.List;
import java.util.Optional;

/**
 * A declaration found in the unit.
 *
 * @param type the class, when the declaration is a class
 * @param method the method, when it is one
 * @param field the field, when it is one
 * @param owner the class that declares a method or field
 * @param scopes the annotations of the enclosing declarations, nearest first
 */
record DeclInfo(
    Optional<JvmClass> type,
    Optional<JvmMethod> method,
    Optional<JvmField> field,
    Optional<JvmClass> owner,
    List<String> scopes) {}
