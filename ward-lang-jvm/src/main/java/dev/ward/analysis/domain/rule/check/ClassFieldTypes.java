package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists the fields of a class with their type texts, split by whether they are static. */
final class ClassFieldTypes {
  private ClassFieldTypes() {}

  /** Returns the fields of the class that are static, or that are not, by name to type text. */
  static Map<String, String> of(final SyntaxNode clazz, final boolean staticFields) {
    return of(clazz, staticFields, true);
  }

  /**
   * Like {@link #of(SyntaxNode, boolean)}; with {@code withEmptyArrays} false it leaves out the
   * fields that hold a zero-length array, which nobody can change.
   */
  static Map<String, String> of(
      final SyntaxNode clazz,
      final boolean staticFields,
      final boolean withEmptyArrays
  ) {
    final Map<String, String> fields = new LinkedHashMap<>();
    for (final SyntaxNode member : ClassMembers.fieldMembers(clazz)) {
      final boolean isStatic =
          MemberDeclarations.modifiers(member).contains(MemberDeclarations.STATIC);
      final SyntaxNode field = ClassMembers.declarationOf(member);
      if (isStatic == staticFields && (withEmptyArrays || !ClassFields.emptyArray(field))) {
        final String type = ClassMembers.typeText(field).orElse("");
        final List<String> names = ClassMembers.fieldNames(field);
        names.forEach(name -> fields.put(name, type));
      }
    }
    return fields;
  }
}
