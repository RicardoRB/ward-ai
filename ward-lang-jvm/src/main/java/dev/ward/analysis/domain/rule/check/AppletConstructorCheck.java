package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a constructor of an applet that calls a method which needs the applet stub: the stub is
 * set after construction, so {@code getParameter}, {@code getCodeBase}, {@code getDocumentBase} and
 * {@code getAppletContext} fail there. Put the code in {@code init()}.
 */
public final class AppletConstructorCheck implements UnitCheck {
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String METHOD_CALL = "methodCall";
  private static final String EXTENDS = "'extends'";
  private static final Pattern APPLET =
      Pattern.compile("^(?:java\\.applet\\.|javax\\.swing\\.)?J?Applet$");
  private static final Set<String> NEED_STUB =
      Set.of("getParameter", "getCodeBase", "getDocumentBase", "getAppletContext");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public AppletConstructorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION))) {
      if (extendsApplet(visit.node())) {
        SyntaxWalk.descendants(visit.node()).stream()
            .filter(node -> CONSTRUCTOR.equals(node.kind()))
            .flatMap(constructor -> SyntaxWalk.descendants(constructor).stream())
            .filter(node -> METHOD_CALL.equals(node.kind()) && needsStub(node))
            .forEach(call -> matches.add(CheckSupport.at(call.span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean extendsApplet(final SyntaxNode type) {
    final List<SyntaxNode> parts = type.children();
    for (int i = 0; i + 1 < parts.size(); i++) {
      if (EXTENDS.equals(parts.get(i).kind())) {
        return APPLET.matcher(SyntaxWalk.text(parts.get(i + 1))).matches();
      }
    }
    return false;
  }

  private static boolean needsStub(final SyntaxNode call) {
    return call.children().stream()
        .filter(child -> "identifier".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .filter(NEED_STUB::contains)
        .isPresent();
  }
}
