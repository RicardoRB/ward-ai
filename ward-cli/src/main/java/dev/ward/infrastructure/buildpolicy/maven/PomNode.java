package dev.ward.infrastructure.buildpolicy.maven;

import java.util.List;

/** An element of a POM with its text and the line it starts on. */
record PomNode(String name, String text, int line, List<PomNode> children) {
  PomNode {
    children = List.copyOf(children);
  }

  PomNode child(final String childName) {
    return children.stream().filter(node -> node.name.equals(childName)).findFirst().orElse(null);
  }

  List<PomNode> all(final String childName) {
    return children.stream().filter(node -> node.name.equals(childName)).toList();
  }

  String text(final String childName) {
    final PomNode node = child(childName);
    return node == null ? "" : node.text.strip();
  }

  /** Returns the child at the end of {@code path}, or {@code null}. */
  PomNode at(final String... path) {
    PomNode node = this;
    for (final String step : path) {
      node = node.child(step);
      if (node == null) {
        return null;
      }
    }
    return node;
  }

  List<PomNode> list(final String... path) {
    final PomNode node = at(path);
    return node == null ? List.of() : node.children;
  }
}
