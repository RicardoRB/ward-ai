package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the declaration readers collect from one file or namespace body. */
final class DeclSink {
  private final List<Decl> decls = new ArrayList<>();
  private final List<FileDecls.Export> exports = new ArrayList<>();
  private final Map<String, List<Decl>> modules = new LinkedHashMap<>();

  void add(final Decl decl) {
    decls.add(decl);
  }

  void addAll(final List<Decl> more) {
    decls.addAll(more);
  }

  void add(final FileDecls.Export export) {
    exports.add(export);
  }

  void addModule(final String specifier, final List<Decl> body) {
    modules.computeIfAbsent(specifier, key -> new ArrayList<>()).addAll(body);
  }

  List<Decl> decls() {
    return List.copyOf(decls);
  }

  List<FileDecls.Export> exports() {
    return List.copyOf(exports);
  }

  Map<String, List<Decl>> modules() {
    return Map.copyOf(modules);
  }
}
