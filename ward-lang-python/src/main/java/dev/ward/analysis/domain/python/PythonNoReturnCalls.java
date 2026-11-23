package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tells whether a statement is a call that never returns, such as {@code flask.abort(404)} or
 * {@code sys.exit(1)}. The call ends the path as a {@code raise} does. The callee is resolved
 * through the imports of the file, so a local function named {@code abort} does not count.
 */
final class PythonNoReturnCalls {
  private static final Set<String> NEVER_RETURN =
      Set.of("flask.abort", "werkzeug.abort", "werkzeug.exceptions.abort", "sys.exit");
  private static final Pattern CALL = Pattern.compile("^([A-Za-z_][\\w.]*)\\(.*\\)$");
  private static final String DOT = ".";

  private PythonNoReturnCalls() {}

  /** Tells whether {@code statement} is a bare call of a function that never returns. */
  static boolean isNoReturn(final SyntaxNode statement, final Map<String, String> imports) {
    final String text =
        PythonSyntax.tokens(statement).stream()
            .map(PythonSyntax::text)
            .collect(Collectors.joining());
    final Matcher call = CALL.matcher(text);
    if (!call.matches()) {
      return false;
    }
    final String callee = call.group(1);
    final int dot = callee.indexOf(DOT);
    final String root = dot < 0 ? callee : callee.substring(0, dot);
    final String module = imports.get(root);
    return module != null
        && NEVER_RETURN.contains(dot < 0 ? module : module + callee.substring(dot));
  }
}
