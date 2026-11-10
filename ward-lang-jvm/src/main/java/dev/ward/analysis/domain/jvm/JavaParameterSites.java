package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the flow sites of the formal or lambda parameters of one Java body. Each parameter's
 * declared type is recorded for the receiver types of later calls, and each annotated parameter is
 * a site that carries the annotation names.
 */
final class JavaParameterSites {

  private static final String ANNOTATION = "annotation";
  private static final String QUALIFIED_NAME = "qualifiedName";
  private static final String VARIABLE_DECLARATOR_ID = "variableDeclaratorId";
  private static final String TYPE_TYPE = "typeType";
  private static final String FORMAL_PARAMETER = "formalParameter";

  private final JavaTypeNames types;
  private final Map<String, String> declared;

  /**
   * Creates the finder of one body.
   *
   * @param types the imports and package of the file
   * @param declared the declared types of the body, which the parameters are added to
   */
  JavaParameterSites(final JavaTypeNames types, final Map<String, String> declared) {
    this.types = types;
    this.declared = declared;
  }

  /**
   * Returns the annotated parameters among a parameter list, and records every parameter's type.
   *
   * @param node the formal or lambda parameter list
   * @return one site per annotated parameter, in source order
   */
  List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    for (final SyntaxNode formal : formalsIn(node, new ArrayList<>())) {
      parameter(formal, found);
    }
    return found;
  }

  private void parameter(final SyntaxNode formal, final List<FlowSite> found) {
    final SyntaxNode id =
        JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(formal), VARIABLE_DECLARATOR_ID);
    if (id == null) {
      return;
    }
    final String name = JavaFlowSyntax.text(JavaSyntaxNames.identifierOf(id));
    declareType(name, JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(formal), TYPE_TYPE));
    final List<String> annotations = annotationsOf(formal);
    if (!annotations.isEmpty()) {
      found.add(
          new FlowSite(
              FlowSiteKind.PARAMETER,
              name,
              "",
              Optional.empty(),
              annotations,
              List.of(),
              formal.span()));
    }
  }

  private void declareType(final String name, final SyntaxNode type) {
    if (type != null) {
      declared.put(name, JavaFlowSyntax.text(type));
    }
  }

  private static List<SyntaxNode> formalsIn(final SyntaxNode node, final List<SyntaxNode> found) {
    for (final SyntaxNode child : node.children()) {
      if (FORMAL_PARAMETER.equals(child.kind())) {
        found.add(child);
      } else {
        formalsIn(child, found);
      }
    }
    return found;
  }

  /** The names of the annotations of a parameter, resolved through the file's imports. */
  private List<String> annotationsOf(final SyntaxNode formal) {
    final List<String> found = new ArrayList<>();
    collectAnnotations(formal, found);
    return found;
  }

  private void collectAnnotations(final SyntaxNode node, final List<String> found) {
    for (final SyntaxNode child : node.children()) {
      if (ANNOTATION.equals(child.kind())) {
        addAnnotation(child, found);
      } else {
        collectAnnotations(child, found);
      }
    }
  }

  private void addAnnotation(final SyntaxNode annotation, final List<String> found) {
    final SyntaxNode name =
        JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(annotation), QUALIFIED_NAME);
    if (name != null) {
      found.add(types.annotation(JavaFlowSyntax.text(name).replaceAll("\\s", "")));
    }
  }
}
