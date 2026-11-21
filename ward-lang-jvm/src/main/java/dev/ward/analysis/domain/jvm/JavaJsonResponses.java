package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the bodies that answer with JSON: every {@code setContentType} call of the body names a
 * JSON type and no header call sets a content type by name. A write of the response body in such a
 * body gives a site named {@link #NAME} whose arguments are the written values, so a taint
 * specification of cross-site scripting can list it as a sanitizer: a JSON response is not
 * rendered as HTML.
 */
final class JavaJsonResponses {
  /** The name of the site of a write of the response body in a body that answers with JSON. */
  static final String NAME = "<json-body>";

  private static final String METHOD_CALL = "methodCall";
  private static final String SET_CONTENT_TYPE = "setContentType";
  private static final String QUOTED_CONTENT_TYPE = "\"content-type\"";
  private static final Set<String> HEADER_CALLS = Set.of("setHeader", "addHeader", "header");
  private static final Set<String> WRITES =
      Set.of("write", "print", "println", "append", "printf", "format");
  private static final Set<String> BODY_TYPES =
      Set.of(
          "java.io.PrintWriter",
          "javax.servlet.ServletOutputStream",
          "jakarta.servlet.ServletOutputStream"
      );
  private static final Pattern JSON_LITERAL =
      Pattern.compile("^\"application/(?:[\\w.-]+\\+)?json(?:;[^\"]*)?\"$");
  private static final Pattern JSON_CONSTANT =
      Pattern.compile("^(?:[\\w.]*\\.)?MediaType\\.APPLICATION_(?:PROBLEM_)?JSON(?:_UTF8)?(?:_VALUE)?$");

  private JavaJsonResponses() {}

  /**
   * Returns whether a body sets a content type, and every one it sets is JSON.
   *
   * @param body the block of the body
   */
  static boolean isJson(final SyntaxNode body) {
    final List<SyntaxNode> calls = new ArrayList<>();
    collect(body, calls);
    boolean json = false;
    for (final SyntaxNode call : calls) {
      final SyntaxNode identifier = JavaSyntaxNames.identifierOf(call);
      if (identifier == null) {
        continue;
      }
      final String name = JavaFlowSyntax.text(identifier);
      final List<SyntaxNode> arguments = JavaCallArguments.of(call);
      if (HEADER_CALLS.contains(name) && namesContentType(arguments)) {
        return false;
      }
      if (SET_CONTENT_TYPE.equals(name)) {
        if (arguments.size() != 1 || !jsonType(JavaFlowSyntax.text(arguments.get(0)))) {
          return false;
        }
        json = true;
      }
    }
    return json;
  }

  /**
   * Returns whether a call writes the response body: a write on the writer or the output stream of
   * a response, written as a call, or typed.
   *
   * @param name the called name
   * @param qualifier the text of the receiver
   * @param receiverType the resolved type of the receiver
   */
  static boolean writesBody(
      final String name,
      final String qualifier,
      final Optional<String> receiverType
  ) {
    return WRITES.contains(name)
        && (qualifier.endsWith(".getWriter()")
            || qualifier.endsWith(".getOutputStream()")
            || receiverType.filter(BODY_TYPES::contains).isPresent());
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> calls) {
    if (METHOD_CALL.equals(node.kind())) {
      calls.add(node);
    }
    node.children().forEach(child -> collect(child, calls));
  }

  private static boolean namesContentType(final List<SyntaxNode> arguments) {
    return !arguments.isEmpty()
        && QUOTED_CONTENT_TYPE.equalsIgnoreCase(JavaFlowSyntax.text(arguments.get(0)));
  }

  private static boolean jsonType(final String argument) {
    return JSON_LITERAL.matcher(argument).matches() || JSON_CONSTANT.matcher(argument).matches();
  }
}
