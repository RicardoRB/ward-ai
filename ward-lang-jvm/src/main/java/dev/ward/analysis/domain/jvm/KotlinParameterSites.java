package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the flow sites of the parameters of one Kotlin function. Each parameter's declared type is
 * recorded for the receiver types of later calls, and each annotated parameter is a site that
 * carries the names of its annotations resolved through the imports, such as the Spring {@code
 * RequestParam} of a handler.
 */
final class KotlinParameterSites {

  private static final String PARAMETER = "parameter";
  private static final String MODIFIER_LIST = "modifierList";
  private static final String ANNOTATION = "annotation";
  private static final String UNESCAPED = "unescapedAnnotation";
  private static final String VALUE_PARAMETER = "functionValueParameter";
  private static final String AT = "@";

  private final JavaTypeNames types;
  private final Map<String, String> declared;

  /**
   * Creates the finder of one body.
   *
   * @param types the imports and package of the file
   * @param declared the declared types of the body, which the parameters are added to
   */
  KotlinParameterSites(final JavaTypeNames types, final Map<String, String> declared) {
    this.types = types;
    this.declared = declared;
  }

  /**
   * Returns the annotated parameters of a parameter list and records every parameter's type.
   *
   * @param list the {@code functionValueParameters} node
   * @return one site per annotated parameter, in source order
   */
  List<FlowSite> of(final SyntaxNode list) {
    final List<FlowSite> found = new ArrayList<>();
    for (final SyntaxNode parameter : KotlinFlowSyntax.children(list, VALUE_PARAMETER)) {
      parameter(parameter, found);
    }
    return found;
  }

  private void parameter(final SyntaxNode node, final List<FlowSite> found) {
    final SyntaxNode parameter = KotlinFlowSyntax.child(node, PARAMETER);
    final SyntaxNode name =
        parameter == null ? null : KotlinFlowSyntax.child(parameter, "simpleIdentifier");
    if (name == null) {
      return;
    }
    final String identifier = KotlinFlowSyntax.text(name);
    final SyntaxNode type = KotlinFlowSyntax.child(parameter, "type");
    if (type != null) {
      declared.put(identifier, KotlinFlowSites.erasedNullability(KotlinFlowSyntax.text(type)));
    }
    final List<String> annotations = annotationsOf(node);
    if (!annotations.isEmpty()) {
      found.add(
          new FlowSite(
              FlowSiteKind.PARAMETER,
              identifier,
              "",
              Optional.empty(),
              annotations,
              List.of(),
              node.span()));
    }
  }

  private List<String> annotationsOf(final SyntaxNode parameter) {
    final List<String> found = new ArrayList<>();
    final SyntaxNode modifiers = KotlinFlowSyntax.child(parameter, MODIFIER_LIST);
    if (modifiers != null) {
      collect(modifiers, found);
    }
    return found;
  }

  private void collect(final SyntaxNode node, final List<String> found) {
    for (final SyntaxNode child : node.children()) {
      if (ANNOTATION.equals(child.kind()) || UNESCAPED.equals(child.kind())) {
        found.add(types.annotation(nameOf(child)));
      } else {
        collect(child, found);
      }
    }
  }

  /** The annotation name, without its {@code @}, arguments and use-site target. */
  private static String nameOf(final SyntaxNode annotation) {
    final StringBuilder name = new StringBuilder();
    for (final SyntaxNode part : KotlinFlowSyntax.parts(annotation)) {
      if ("valueArguments".equals(part.kind()) || "typeArguments".equals(part.kind())) {
        break;
      }
      if (UNESCAPED.equals(part.kind())) {
        return nameOf(part);
      }
      if (!"annotationUseSiteTarget".equals(part.kind())
          && !":".equals(KotlinFlowSyntax.text(part))) {
        name.append(KotlinFlowSyntax.text(part));
      }
    }
    return name.toString().replaceAll("\\s", "").replaceFirst("^" + AT, "");
  }
}
