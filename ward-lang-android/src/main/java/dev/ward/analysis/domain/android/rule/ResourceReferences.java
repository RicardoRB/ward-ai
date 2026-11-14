package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.android.AndroidSyntax;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects the resources a file refers to: {@code @type/name} in the attribute values and the text
 * of an XML file, the parent of a style, and {@code R.type.name} in Java and Kotlin sources. A
 * layout also counts as used when a source names its view binding class, {@code
 * ActivityMainBinding} for {@code activity_main}.
 */
final class ResourceReferences {

  private static final Pattern XML_REFERENCE =
      Pattern.compile("@\\+?(?:([\\w.]+):)?(\\w+)/([\\w.]+)");
  private static final String ANDROID = "android";
  private static final String STYLE = "style";
  private static final String BINDING = "Binding";
  private static final String DOT = ".";
  private static final int NAME_GROUP = 3;
  private static final int SECOND_DOT_OFFSET = 3;
  private static final int R_ACCESS_TOKENS = 5;
  private static final int TYPE_OFFSET = 2;
  private static final int NAME_OFFSET = 4;

  private final Set<ResourceKey> used = new HashSet<>();
  private final Set<String> bindings = new HashSet<>();

  /** Adds the references of an XML file. */
  void addXml(final SyntaxNode root) {
    final Deque<SyntaxNode> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      final SyntaxNode node = pending.pop();
      node.children().forEach(pending::push);
      final String value = node.fields().getOrDefault(AndroidSyntax.VALUE, "");
      final Matcher matcher = XML_REFERENCE.matcher(value);
      while (matcher.find()) {
        addReference(matcher.group(1), matcher.group(2), matcher.group(NAME_GROUP));
      }
      if (AndroidSyntax.isElement(node.kind()) && STYLE.equals(AndroidNodes.name(node))) {
        addStyleParents(node);
      }
    }
  }

  private void addReference(final String pkg, final String type, final String name) {
    if (!ANDROID.equals(pkg)) {
      used.add(ResourceKey.of(type, name));
    }
  }

  /** A style refers to its {@code parent}, and a dotted name implies the parent before the dot. */
  private void addStyleParents(final SyntaxNode style) {
    AndroidNodes.attribute(style, "", "parent")
        .map(AndroidNodes::value)
        .filter(parent -> !parent.isEmpty() && !parent.startsWith("@"))
        .ifPresent(parent -> used.add(ResourceKey.of(STYLE, parent)));
    AndroidNodes.attribute(style, "", "name")
        .map(AndroidNodes::value)
        .filter(name -> name.contains(DOT))
        .ifPresent(
            name -> used.add(ResourceKey.of(STYLE, name.substring(0, name.lastIndexOf('.')))));
  }

  /** Adds the references of a Java or Kotlin file, read from its tokens in order. */
  void addCode(final SyntaxNode root) {
    final List<String> tokens = new ArrayList<>();
    final Deque<SyntaxNode> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      final SyntaxNode node = pending.pop();
      if (node.children().isEmpty()) {
        tokens.add(node.fields().getOrDefault("text", ""));
      } else {
        for (int index = node.children().size() - 1; index >= 0; index--) {
          pending.push(node.children().get(index));
        }
      }
    }
    for (int index = 0; index < tokens.size(); index++) {
      addAccess(tokens, index);
    }
  }

  private void addAccess(final List<String> tokens, final int index) {
    final String token = tokens.get(index);
    if (token.length() > BINDING.length() && token.endsWith(BINDING)) {
      bindings.add(token);
    }
    final boolean access =
        "R".equals(token)
            && index + R_ACCESS_TOKENS <= tokens.size()
            && DOT.equals(tokens.get(index + 1))
            && DOT.equals(tokens.get(index + SECOND_DOT_OFFSET));
    final boolean framework =
        index >= 2 && DOT.equals(tokens.get(index - 1)) && ANDROID.equals(tokens.get(index - 2));
    if (access && !framework) {
      used.add(ResourceKey.of(tokens.get(index + TYPE_OFFSET), tokens.get(index + NAME_OFFSET)));
    }
  }

  /** Tells whether something refers to the resource. */
  boolean uses(final ResourceKey key) {
    return used.contains(key) || "layout".equals(key.type()) && bindings.contains(binding(key));
  }

  /**
   * The view binding class of a layout: {@code activity_main} gives {@code ActivityMainBinding}.
   */
  private static String binding(final ResourceKey layout) {
    final StringBuilder name = new StringBuilder();
    for (final String part : layout.name().split("_")) {
      if (!part.isEmpty()) {
        name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
    }
    return name + BINDING;
  }
}
