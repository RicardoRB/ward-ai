package dev.ward.analysis.domain.openapi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

/** One load of a document: the files read so far, the reference targets and the errors. */
final class OpenApiLoader {

  private static final Logger LOG = Logger.getLogger(OpenApiLoader.class.getName());

  private final Function<String, Optional<Parsed>> source;
  private final Map<String, DocNode> files = new LinkedHashMap<>();
  private final Map<DocNode, DocNode> targets = new IdentityHashMap<>();
  private final List<LoadError> errors = new ArrayList<>();
  private final Set<String> failed = new HashSet<>();

  OpenApiLoader(final Function<String, Optional<Parsed>> source) {
    this.source = source;
  }

  LoadResult load(final String entry) {
    final Optional<DocNode> root = file(entry, null);
    if (root.isEmpty()) {
      return new LoadResult(Optional.empty(), errors);
    }
    final Optional<String> version = version(root.get());
    if (version.isPresent()) {
      resolveAll();
      rejectReferenceCycles();
    }
    if (version.isEmpty() || !errors.isEmpty()) {
      LOG.fine(() -> "openapi document not loaded: " + errors.size() + " problems");
      return new LoadResult(Optional.empty(), errors);
    }
    LOG.fine(() -> "openapi document loaded from " + files.size() + " files");
    return new LoadResult(
        Optional.of(new OpenApiDocument(root.get(), version.get(), files, targets)), errors);
  }

  /** Reads a file once; a problem becomes an error at the reference that named it, if any. */
  private Optional<DocNode> file(final String path, final DocNode from) {
    if (files.containsKey(path)) {
      return Optional.of(files.get(path));
    }
    if (failed.contains(path)) {
      return Optional.empty();
    }
    final Optional<Parsed> read = source.apply(path);
    if (read.isEmpty()) {
      failed.add(path);
      errors.add(LoadError.at(from, path, "the file cannot be read: " + path));
      return Optional.empty();
    }
    final Parsed parsed = read.get();
    if (parsed.problem() != null) {
      failed.add(path);
      errors.add(
          new LoadError(
              path,
              parsed.problem().line(),
              parsed.problem().column(),
              parsed.problem().message()));
      return Optional.empty();
    }
    files.put(path, parsed.root());
    return Optional.of(parsed.root());
  }

  private Optional<String> version(final DocNode root) {
    final String file = root.file();
    final Optional<DocNode> swagger = member(root, "swagger");
    final Optional<DocNode> openapi = member(root, "openapi");
    if (swagger.isPresent()) {
      errors.add(
          LoadError.at(
              swagger.get(),
              file,
              "Swagger " + swagger.get().text() + " is not supported; use OpenAPI 3.0 or 3.1"));
      return Optional.empty();
    }
    if (openapi.isEmpty()) {
      errors.add(
          new LoadError(
              file, 1, 1, "the file is not an OpenAPI document: it has no 'openapi' key"));
      return Optional.empty();
    }
    final String version = openapi.get().text();
    if (!ReferenceTargets.supportedVersion(version)) {
      errors.add(
          LoadError.at(
              openapi.get(), file, "OpenAPI " + version + " is not supported; use 3.0 or 3.1"));
      return Optional.empty();
    }
    return Optional.of(version);
  }

  private static Optional<DocNode> member(final DocNode root, final String key) {
    return root.kind() == DocNode.Kind.OBJECT ? root.get(key) : Optional.empty();
  }

  /** Resolves the references of every loaded file, and of the files they load, to any depth. */
  private void resolveAll() {
    final Deque<String> pending = new ArrayDeque<>(files.keySet());
    final Set<String> scanned = new HashSet<>();
    while (!pending.isEmpty()) {
      final String path = pending.poll();
      if (scanned.add(path)) {
        for (final DocNode reference : ReferenceTargets.referencesIn(files.get(path))) {
          resolve(reference);
        }
        files.keySet().stream().filter(name -> !scanned.contains(name)).forEach(pending::add);
      }
    }
  }

  private void resolve(final DocNode reference) {
    final DocNode value = reference.get(OpenApiDocument.REF).orElseThrow();
    final String target = reference.reference().orElseThrow();
    final Optional<String> problem = ReferenceTargets.problem(target);
    if (problem.isPresent()) {
      errors.add(LoadError.at(value, reference.file(), problem.get()));
      return;
    }
    final int hash = target.indexOf('#');
    final String filePart = hash < 0 ? target : target.substring(0, hash);
    final String fragment = hash < 0 ? "" : target.substring(hash + 1);
    final Optional<String> path =
        ReferenceTargets.path(reference.file(), ReferenceTargets.decode(filePart));
    if (path.isEmpty()) {
      errors.add(
          LoadError.at(
              value, reference.file(), "the reference '" + target + "' leaves the project"));
      return;
    }
    file(path.get(), value).ifPresent(root -> bind(reference, value, target, root, fragment));
  }

  private void bind(
      final DocNode reference,
      final DocNode value,
      final String target,
      final DocNode root,
      final String fragment) {
    final Optional<DocNode> node =
        ReferenceTargets.pointer(root, ReferenceTargets.decode(fragment));
    if (node.isPresent()) {
      targets.put(reference, node.get());
    } else {
      errors.add(
          LoadError.at(
              value, reference.file(), "the reference '" + target + "' points at nothing"));
    }
  }

  /** A chain of references that never reaches a node can be followed forever. */
  private void rejectReferenceCycles() {
    for (final DocNode start : targets.keySet()) {
      if (loops(start)) {
        final DocNode value = start.get(OpenApiDocument.REF).orElseThrow();
        errors.add(LoadError.at(value, start.file(), "the references form a loop with no target"));
        return;
      }
    }
  }

  private boolean loops(final DocNode start) {
    final Set<DocNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    DocNode current = start;
    while (targets.containsKey(current) && seen.add(current)) {
      current = targets.get(current);
    }
    return targets.containsKey(current);
  }

  /** A file read: its nodes, or the first syntax problem. */
  record Parsed(DocNode root, YamlDiagnostic problem) {}
}
