package dev.ward.analysis.domain.openapi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * A loaded OpenAPI 3.0 or 3.1 document: the root of the entry file, every file its {@code $ref}s
 * reach, and the resolved target of each reference. Two views read the same nodes: the {@link
 * View#RAW} view, where a {@code $ref} object is a node of its own, and the {@link View#RESOLVED}
 * view, where a reference reads as its target, which keeps its own file and position.
 *
 * <p>The accessors return the nodes themselves, never copies, so a rule or a diff always has the
 * position of what it read, and an accessor on a part the document lacks returns empty, never null.
 * {@link #parts()} reads the standard places of the document. Create documents with {@link
 * OpenApiDocuments}.
 */
public final class OpenApiDocument {

  /** The key of a reference object. */
  public static final String REF = "$ref";

  private final DocNode root;
  private final String version;
  private final SortedMap<String, DocNode> files;
  private final Map<DocNode, DocNode> targets;

  OpenApiDocument(
      final DocNode root,
      final String version,
      final Map<String, DocNode> files,
      final Map<DocNode, DocNode> targets) {
    this.root = root;
    this.version = version;
    this.files = Collections.unmodifiableSortedMap(new TreeMap<>(files));
    this.targets = Collections.unmodifiableMap(new IdentityHashMap<>(targets));
  }

  /** Returns the root of the entry file. */
  public DocNode root() {
    return root;
  }

  /** Returns the project-relative path of the entry file. */
  public String file() {
    return root.file();
  }

  /** Returns the {@code openapi} version text, such as {@code 3.0.3}. */
  public String version() {
    return version;
  }

  /** Tells whether the document is OpenAPI 3.1 or later, so a schema is a JSON Schema 2020-12. */
  public boolean isVersion31() {
    return !version.startsWith("3.0");
  }

  /** Returns the root of every loaded file by project-relative path, entry file included. */
  public SortedMap<String, DocNode> files() {
    return files;
  }

  /** Returns the standard places of the document: info, servers, paths, operations, components. */
  public OpenApiParts parts() {
    return new OpenApiParts(this);
  }

  /** Returns the node a {@code $ref} object points at directly, or empty for any other node. */
  public Optional<DocNode> target(final DocNode node) {
    return Optional.ofNullable(targets.get(node));
  }

  /**
   * Returns what a node reads as in the resolved view: the node itself, or the end of its chain of
   * references.
   */
  public DocNode resolve(final DocNode node) {
    DocNode current = node;
    final Set<DocNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    while (targets.containsKey(current) && seen.add(current)) {
      current = targets.get(current);
    }
    return current;
  }

  /** Returns {@code node} as the given view reads it. */
  public DocNode resolve(final DocNode node, final View view) {
    return view == View.RESOLVED ? resolve(node) : node;
  }

  /** Returns the child of {@code node} at {@code key} as the given view reads it. */
  public Optional<DocNode> get(final DocNode node, final String key, final View view) {
    return resolve(node, view).get(key).map(child -> resolve(child, view));
  }

  /**
   * Returns every node of the document, each once, in depth-first file order. The raw view walks
   * the entry file; the resolved view follows each reference to its target, which may be in another
   * file, and never visits a node twice, so a cyclic schema is finite.
   */
  public Stream<DocNode> walk(final View view) {
    final List<DocNode> visited = new ArrayList<>();
    final Set<DocNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    final Deque<DocNode> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
      final DocNode node = resolve(stack.pop(), view);
      if (seen.add(node)) {
        visited.add(node);
        final List<DocNode> children = node.children();
        for (int index = children.size() - 1; index >= 0; index--) {
          stack.push(children.get(index));
        }
      }
    }
    return visited.stream();
  }

  /** Which nodes a walk and a read see. */
  public enum View {
    /** A reference object is a node; nothing is followed. */
    RAW,
    /** A reference reads as its target; each node is visited once, so cycles end. */
    RESOLVED
  }
}
