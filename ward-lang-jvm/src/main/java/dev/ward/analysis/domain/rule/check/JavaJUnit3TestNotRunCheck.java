package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method of a {@code TestCase} subclass whose name starts with {@code test} but that
 * JUnit 3 cannot run, because it is not public, is static, takes parameters or returns a value, as
 * Error Prone's JUnit3TestNotRun does for the plain case.
 */
public final class JavaJUnit3TestNotRunCheck implements RuleCheck {

  private static final String TEST_CASE = "TestCase";
  private static final String VOID = "void";
  private static final String PREFIX = "test";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public JavaJUnit3TestNotRunCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      if (extendsTestCase(visit.node())) {
        JavaNodes.members(visit.node()).stream()
            .filter(member -> "methodDeclaration".equals(member.node().kind()))
            .filter(member -> JavaNodes.name(member.node()).startsWith(PREFIX))
            .filter(member -> !runnable(member))
            .forEach(
                member ->
                    matches.add(
                        CheckSupport.at(
                            member.node().span(),
                            CheckSupport.fill(message, JavaNodes.name(member.node()), ""))));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean extendsTestCase(final SyntaxNode type) {
    return JavaNodes.kid(type, "typeType")
        .map(SyntaxWalk::text)
        .filter(name -> TEST_CASE.equals(name) || name.endsWith("." + TEST_CASE))
        .isPresent();
  }

  private static boolean runnable(final JavaNodes.Member member) {
    final SyntaxNode method = member.node();
    final boolean returnsVoid =
        VOID.equals(JavaNodes.kid(method, "typeTypeOrVoid").map(SyntaxWalk::text).orElse(""));
    final boolean noParameters =
        JavaNodes.kid(method, "formalParameters")
            .map(parameters -> JavaNodes.kid(parameters, "formalParameter").isEmpty())
            .orElse(true);
    return member.has("public") && !member.has("static") && returnsVoid && noParameters;
  }
}
