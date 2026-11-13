package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the parameters of a Kotlin function or constructor. */
final class KotlinApiParameters {
  private static final String LIST = "functionValueParameters";
  private static final String HOLDER = "functionValueParameter";

  private KotlinApiParameters() {}

  /** Returns the parameters of a function or secondary constructor, in order. */
  static List<ApiParameter> of(final SyntaxNode function, final KotlinApiRefs inner) {
    return Syntax.children(function, LIST).stream()
        .flatMap(list -> Syntax.children(list, HOLDER).stream())
        .map(holder -> parameter(holder, inner))
        .toList();
  }

  /** Returns a parameter; the type of a {@code vararg} is the array of its element type. */
  static ApiParameter parameter(final SyntaxNode holder, final KotlinApiRefs inner) {
    final ApiTypeRef type =
        Syntax.find(holder, "type").map(inner::of).orElseGet(KotlinApiTypeRefs::inferred);
    final boolean vararg = KotlinApiModifiers.of(holder).has("vararg");
    return new ApiParameter(
        vararg ? KotlinApiTypeRefs.array(type) : type, Syntax.hasToken(holder, "="));
  }

  /** Returns whether a parameter of {@code function} is a {@code vararg}. */
  static boolean hasVararg(final SyntaxNode function) {
    return Syntax.children(function, LIST).stream()
        .flatMap(list -> Syntax.children(list, HOLDER).stream())
        .anyMatch(holder -> KotlinApiModifiers.of(holder).has("vararg"));
  }
}
