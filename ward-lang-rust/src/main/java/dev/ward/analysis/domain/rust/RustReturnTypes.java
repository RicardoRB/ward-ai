package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The declared return types of the functions and methods of one Rust file, and the associated
 * types its impls define, so that {@code let a = owner.make()?;} can be typed. Only what the file
 * states is used: a name declared twice with different types, or a trait's associated type that
 * no impl of the file fixes, is unknown.
 * shortcut: a function of another file is unknown; upgrade with project-wide type facts.
 */
final class RustReturnTypes {
  private static final String RETURN = "functionReturnType";
  private static final String TYPE = "type_";
  private static final String ALIAS = "typeAlias";
  private static final String TRAIT = "trait_";
  private static final String SEPARATOR = "::";
  private static final Pattern UNWRAP = Pattern.compile("^(?:Result|Option|io::Result)<(.*)>$");
  private static final Pattern ASSOCIATED = Pattern.compile("^Self::(\\w+)$");

  private final Map<String, String> returns = new HashMap<>();
  private final Map<String, String> aliases = new HashMap<>();
  private final Set<String> clashing = new HashSet<>();

  /** Reads the functions and associated types of a file. */
  RustReturnTypes(final SyntaxNode root) {
    collect(root, "");
  }

  /**
   * The written type that {@code owner::name} returns with {@code Result} and {@code Option}
   * removed, or unknown. An empty owner names a free function.
   */
  String returned(final String owner, final String name) {
    final String text = returns.get(owner + SEPARATOR + name);
    if (text == null || clashing.contains(owner + SEPARATOR + name)) {
      return RustTypeNames.UNKNOWN;
    }
    return resolve(owner, unwrap(text));
  }

  /**
   * The class of the type alias {@code name} of the file, or of the {@code Target} that an impl of
   * the file gives {@code name} (a {@code Deref}), or {@code name} itself.
   */
  String aliased(final String name) {
    for (final String key : new String[] {SEPARATOR + name, name + SEPARATOR + "Target"}) {
      if (aliases.containsKey(key) && !clashing.contains(key)) {
        return RustTypeNames.classify(aliases.get(key));
      }
    }
    return name;
  }

  private String resolve(final String owner, final String text) {
    final Matcher associated = ASSOCIATED.matcher(text);
    if (!associated.matches()) {
      return text;
    }
    final String key = owner + SEPARATOR + associated.group(1);
    final String target = aliases.get(key);
    return target == null || clashing.contains(key) ? RustTypeNames.UNKNOWN : target;
  }

  private static String unwrap(final String text) {
    final Matcher wrapped = UNWRAP.matcher(text);
    return wrapped.matches() ? RustTypeText.untilTopLevel(wrapped.group(1), ',') : text;
  }

  private void collect(final SyntaxNode node, final String outer) {
    String inner = outer;
    if (RustTypeNames.isImpl(node)) {
      inner = RustTypeNames.implType(node);
    } else if (TRAIT.equals(node.kind())) {
      inner = RustTypeNames.traitName(node, outer);
    } else if (RustKinds.FUNCTION.equals(node.kind())) {
      declare(returns, node, inner, returnText(node));
    } else if (ALIAS.equals(node.kind())) {
      declare(aliases, node, inner, RustTypeNames.declaredText(node));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, inner);
    }
  }

  private static String returnText(final SyntaxNode function) {
    final SyntaxNode result = RustTrees.child(function, RETURN);
    return result == null ? RustTypeNames.UNKNOWN : RustTypeNames.declaredText(result);
  }

  private void declare(
      final Map<String,
      String> into,
      final SyntaxNode node,
      final String owner,
      final String text
  ) {
    final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
    if (name == null) {
      return;
    }
    final String key = owner + SEPARATOR + RustTrees.name(name);
    final String before = into.putIfAbsent(key, text);
    if (before != null && !before.equals(text)) {
      clashing.add(key);
    }
  }
}
