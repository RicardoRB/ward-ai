package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The types of the parameters and locals of one Rust function, as written type text: stated, made
 * by a creating expression, returned by a function of the file (through {@code ?}, {@code
 * unwrap} and {@code expect}), or the item of a known collection that a {@code for} loop or an
 * iterator closure binds.
 */
final class RustLocalTypes {
  private static final String LET = "letStatement";
  private static final String PARAM = "functionParamPattern";
  private static final String FOR = "iteratorLoopExpression";
  private static final Pattern SUFFIX =
      Pattern.compile("(\\?|\\.unwrap\\(\\)|\\.unwrap_or_default\\(\\)|\\.expect\\([^()]*\\))$");
  private static final Pattern CALL = Pattern.compile("^(?:(\\w+)\\.)?(\\w+)\\([^()]*\\)$");
  private static final Pattern ITEMS =
      Pattern.compile("^&?(?:mut)?(\\w+)(?:\\.(?:iter|iter_mut|into_iter)\\(\\))?$");
  private static final Pattern CLOSURE =
      Pattern.compile("^(\\w+)\\.(?:iter|iter_mut|into_iter)\\(\\)\\.\\w+\\(\\|&?(\\w+)\\|");

  private RustLocalTypes() {}

  /**
   * The type of each parameter and local of {@code function} whose type is stated, by name. A name
   * that is bound twice with different types is left out.
   */
  static Map<String, String> of(
      final SyntaxNode function,
      final String owner,
      final RustReturnTypes returns
  ) {
    final Map<String, String> found = new HashMap<>();
    final Set<String> clashing = new HashSet<>();
    new Binder(owner, returns, found, clashing).bind(function);
    clashing.forEach(found::remove);
    return found;
  }

  /** Walks a function in source order and records what each binding is. */
  private static final class Binder {
    private final String owner;
    private final RustReturnTypes returns;
    private final Map<String, String> found;
    private final Set<String> clashing;

    Binder(
        final String owner,
        final RustReturnTypes returns,
        final Map<String,
        String> found,
        final Set<String> clashing
    ) {
      this.owner = owner;
      this.returns = returns;
      this.found = found;
      this.clashing = clashing;
    }

    void bind(final SyntaxNode node) {
      if (LET.equals(node.kind())) {
        declare(RustTrees.child(node, RustKinds.PATTERN_NO_ALT), letType(node));
      } else if (PARAM.equals(node.kind())) {
        declare(RustTrees.child(node, RustKinds.PATTERN), RustTypeNames.declaredText(node));
      } else if (FOR.equals(node.kind())) {
        loop(node);
      } else if (RustKinds.EXPRESSION.equals(node.kind())) {
        closure(RustTrees.text(node));
      }
      node.children().forEach(this::bind);
    }

    private void loop(final SyntaxNode loop) {
      final SyntaxNode pattern = RustTrees.child(loop, RustKinds.PATTERN);
      final SyntaxNode source = RustTrees.child(loop, RustKinds.EXPRESSION);
      if (pattern != null && source != null) {
        declare(pattern, items(RustTrees.text(source)));
      }
    }

    private void closure(final String text) {
      final Matcher match = CLOSURE.matcher(text);
      if (match.find()) {
        record(match.group(2), RustTypeText.element(found.getOrDefault(match.group(1), "")));
      }
    }

    private String items(final String source) {
      final Matcher match = ITEMS.matcher(source);
      return match.matches()
          ? RustTypeText.element(found.getOrDefault(match.group(1), ""))
          : RustTypeNames.UNKNOWN;
    }

    private String letType(final SyntaxNode let) {
      final String declared = RustTypeNames.declaredText(let);
      if (!RustTypeNames.UNKNOWN.equals(declared)) {
        return declared;
      }
      final List<SyntaxNode> parts = let.children();
      for (int index = 0; index + 1 < parts.size(); index++) {
        if (RustTrees.isToken(parts.get(index), RustKinds.EQUALS)) {
          return initializer(parts.get(index + 1));
        }
      }
      return RustTypeNames.UNKNOWN;
    }

    private String initializer(final SyntaxNode value) {
      final String made = RustTypeNames.ofCreation(value);
      if (!RustTypeNames.UNKNOWN.equals(made)) {
        return made;
      }
      String text = RustTrees.text(value);
      for (Matcher end = SUFFIX.matcher(text); end.find(); end = SUFFIX.matcher(text)) {
        text = text.substring(0, end.start());
      }
      final Matcher call = CALL.matcher(text);
      if (!call.matches()) {
        return RustTypeNames.UNKNOWN;
      }
      return returns.returned(ownerOf(call.group(1)), call.group(2));
    }

    private String ownerOf(final String receiver) {
      if (receiver == null) {
        return "";
      }
      return "self".equals(receiver)
          ? owner
          : RustTypeNames.classify(found.getOrDefault(receiver, ""));
    }

    private void declare(final SyntaxNode pattern, final String type) {
      final SyntaxNode name =
          pattern == null ? null : RustTrees.find(pattern, RustKinds.IDENTIFIER);
      if (name == null) {
        return;
      }
      record(RustTrees.name(name), type);
    }

    private void record(final String key, final String type) {
      final String before = found.putIfAbsent(key, type);
      if (before != null && !Objects.equals(before, type)) {
        clashing.add(key);
      }
    }
  }
}
