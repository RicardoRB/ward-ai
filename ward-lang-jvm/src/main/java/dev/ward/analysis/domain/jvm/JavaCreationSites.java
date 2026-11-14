package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/**
 * Reads the object creations of a Java body as call sites. {@code new T(args)} is a call named
 * {@code <init>} whose receiver is the created type, so a rule can name a constructor such as
 * {@code java.io.File}. An array creation or an anonymous class body is not a call and gives no
 * site.
 */
final class JavaCreationSites {

  private static final String NEW = "new";
  private static final String CREATED_NAME = "createdName";
  private static final String CLASS_CREATOR_REST = "classCreatorRest";
  private static final String CONSTRUCTOR = "<init>";

  private JavaCreationSites() {}

  /**
   * Returns the call site of one creation.
   *
   * @param types the imports and package of the file
   * @param expression the {@code new} expression, whose span the site takes
   * @param creator the creator part of the expression
   * @return the site, or empty when the creation is an array
   */
  static Optional<FlowSite> of(
      final JavaTypeNames types, final SyntaxNode expression, final SyntaxNode creator) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(creator);
    final SyntaxNode created = JavaFlowSyntax.firstOf(parts, CREATED_NAME);
    final SyntaxNode rest = JavaFlowSyntax.firstOf(parts, CLASS_CREATOR_REST);
    if (created == null || rest == null) {
      return Optional.empty();
    }
    final String written = JavaFlowSyntax.text(created);
    return Optional.of(
        new FlowSite(
            FlowSiteKind.CALL,
            CONSTRUCTOR,
            written,
            types.resolve(written),
            List.of(),
            JavaCallArguments.spans(JavaCallArguments.of(rest)),
            expression.span()));
  }

  /**
   * Returns the fully qualified type that a {@code new} expression creates, for a plain class
   * creation: not an array, and not an anonymous class, whose body may override methods.
   */
  static Optional<String> createdType(final JavaTypeNames types, final SyntaxNode creator) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(creator);
    final SyntaxNode created = JavaFlowSyntax.firstOf(parts, CREATED_NAME);
    final SyntaxNode rest = JavaFlowSyntax.firstOf(parts, CLASS_CREATOR_REST);
    if (created == null
        || rest == null
        || JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(rest), "classBody") != null) {
      return Optional.empty();
    }
    return types.resolve(JavaFlowSyntax.text(created));
  }

  /** Returns the argument nodes of a creation; empty for an array. */
  static List<SyntaxNode> arguments(final SyntaxNode creator) {
    final SyntaxNode rest =
        JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(creator), CLASS_CREATOR_REST);
    return rest == null ? List.of() : JavaCallArguments.of(rest);
  }

  /** Reports whether the parts of an expression are the {@code new} keyword and a creator. */
  static boolean isCreation(final List<SyntaxNode> parts) {
    return parts.size() == 2 && NEW.equals(JavaFlowSyntax.text(parts.get(0)));
  }
}
