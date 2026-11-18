package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The reads and writes of the fields of the class that a body names alone, such as {@code data} for
 * {@code this.data}, as member sites of {@code this}. A name that the body declares itself, as a
 * local variable, a parameter, a catch or a lambda parameter, hides the field and gives no site.
 */
final class JavaFieldSites {
  private static final String THIS = "this";
  private static final Pattern BARE_NAME = Pattern.compile("[A-Za-z_$][\\w$]*");
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String CATCH_CLAUSE = "catchClause";
  private static final String LAMBDA_PARAMETERS = "lambdaParameters";
  private static final String IDENTIFIER = "identifier";

  private final Set<String> fieldNames = new HashSet<>();
  private final Set<String> shadowed = new HashSet<>();

  JavaFieldSites(final List<JavaClassFields.Field> fields) {
    fields.forEach(field -> fieldNames.add(field.name()));
  }

  /** Records the variables that the roots declare, which hide the fields of the same name. */
  void shadowedBy(final SyntaxNode... roots) {
    for (final SyntaxNode root : roots) {
      if (root != null) {
        collectDeclared(root);
      }
    }
  }

  private void collectDeclared(final SyntaxNode node) {
    if (DECLARATOR_ID.equals(node.kind())) {
      shadowed.add(JavaFlowSyntax.text(JavaSyntaxNames.identifierOf(node)));
    } else if (CATCH_CLAUSE.equals(node.kind()) || LAMBDA_PARAMETERS.equals(node.kind())) {
      collectIdentifiers(node);
    }
    node.children().forEach(this::collectDeclared);
  }

  private void collectIdentifiers(final SyntaxNode node) {
    if (IDENTIFIER.equals(node.kind())) {
      shadowed.add(JavaFlowSyntax.text(node));
    }
    node.children().forEach(this::collectIdentifiers);
  }

  /** Whether the node is the bare name of a field that no declaration of the body hides. */
  boolean isBare(final SyntaxNode node) {
    final String text = JavaFlowSyntax.text(node);
    return BARE_NAME.matcher(text).matches()
        && fieldNames.contains(text)
        && !shadowed.contains(text);
  }

  /** The member read of a bare field name. */
  FlowSite read(final SyntaxNode name) {
    return site(FlowSiteKind.MEMBER_READ, name, List.of(), name.span());
  }

  /** The member write of an assignment to a bare field name. */
  FlowSite write(final SyntaxNode name, final SyntaxNode value, final Span span) {
    return site(FlowSiteKind.MEMBER_WRITE, name, List.of(value.span()), span);
  }

  private static FlowSite site(
      final FlowSiteKind kind,
      final SyntaxNode name,
      final List<Span> arguments,
      final Span span
  ) {
    return new FlowSite(
        kind,
        JavaFlowSyntax.text(name),
        THIS,
        Optional.empty(),
        List.of(),
        arguments,
        span
    );
  }
}
