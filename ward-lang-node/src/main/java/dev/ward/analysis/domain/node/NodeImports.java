package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The imports of one TypeScript or JavaScript file: named, default and namespace imports, and
 * {@code require()} bindings. A name imported from a module maps to {@code module.name}, which is
 * the receiver type of a type annotation or {@code new} initializer that names it. Every bound name
 * maps to its module, which is the receiver type of a call through it, and a called name keeps the
 * imported name, so {@code import { exec as run }} calls {@code exec}.
 *
 * <p>shortcut: a binding is read from the text of its statement or declaration, so a binding inside
 * a nested scope is read like a top-level one. A {@code node:} prefix is removed from every module
 * name.
 */
final class NodeImports {
  private static final String IMPORT_STATEMENT = "importStatement";
  private static final String VARIABLE_DECLARATION = "variableDeclaration";
  private static final String NODE_PREFIX = "node:";
  private static final int NODE_PREFIX_LENGTH = NODE_PREFIX.length();
  private static final String TYPE_ONLY = "^type\\s+";
  private static final Pattern NAMED =
      Pattern.compile("import\\s*(?:type\\s*)?\\{([^}]*)}\\s*from\\s*['\"]([^'\"]+)['\"]");
  private static final Pattern DEFAULT =
      Pattern.compile(
          "import\\s*(?:type\\s*)?(\\w+)\\s*(?:,\\s*\\{[^}]*}\\s*)?from\\s*['\"]([^'\"]+)['\"]"
      );
  private static final Pattern NAMESPACE =
      Pattern.compile("import\\s*(?:type\\s*)?\\*\\s*as\\s*(\\w+)\\s*from\\s*['\"]([^'\"]+)['\"]");
  private static final Pattern REQUIRE =
      Pattern.compile("^(\\w+)\\s*=\\s*require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
  private static final Pattern REQUIRE_DESTRUCTURED =
      Pattern.compile("^\\{([^}]*)}\\s*=\\s*require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

  /** Module-level names as bound: the module of each local name, and the name it imports. */
  private final Map<String, String> named = new HashMap<>();

  private final Map<String, String> modules = new HashMap<>();
  private final Map<String, String> imported = new HashMap<>();
  private final Set<String> projectLocals = new HashSet<>();
  private final String directory;

  private NodeImports(final String directory) {
    this.directory = directory;
  }

  /**
   * Reads the imports of a file.
   *
   * @param root the root of the syntax tree of the file
   * @param path the path of the file, which a relative module specifier is resolved against
   * @return the imports of the file
   */
  static NodeImports of(final SyntaxNode root, final String path) {
    final NodeImports imports = new NodeImports(NodeModuleNames.directoryOf(path));
    imports.collect(root);
    return imports;
  }

  /**
   * Returns the receiver type of a name imported from a module.
   *
   * @param local the name as the file uses it
   * @return the {@code module.name} of the import, or null when the name is not imported
   */
  String typeOf(final String local) {
    return named.get(local);
  }

  /**
   * Returns the module that a name is bound to by an import or a {@code require()}.
   *
   * @param local the name as the file uses it
   * @return the module specifier without a {@code node:} prefix, or empty when the name is not
   *     bound
   */
  Optional<String> moduleOf(final String local) {
    return Optional.ofNullable(modules.get(local));
  }

  /**
   * Returns the name that a call through a local name calls: the imported name for an aliased or
   * destructured binding, and the local name otherwise.
   *
   * @param local the name as the file uses it
   * @return the called name
   */
  String calledName(final String local) {
    return imported.getOrDefault(local, local);
  }

  private void collect(final SyntaxNode node) {
    if (IMPORT_STATEMENT.equals(node.kind())) {
      addImport(NodeFlowSyntax.spaced(node));
    } else if (VARIABLE_DECLARATION.equals(node.kind())) {
      addRequire(NodeFlowSyntax.spaced(node));
    }
    node.children().forEach(this::collect);
  }

  private void addImport(final String text) {
    final Matcher braces = NAMED.matcher(text);
    if (braces.find()) {
      bindNamed(braces.group(1), module(braces.group(2)), ",", " as ");
    }
    final Matcher defaults = DEFAULT.matcher(text);
    if (defaults.find()) {
      bindWhole(defaults.group(1), defaults.group(2));
    }
    final Matcher namespace = NAMESPACE.matcher(text);
    if (namespace.find()) {
      bindWhole(namespace.group(1), namespace.group(2));
    }
  }

  private void addRequire(final String text) {
    final Matcher single = REQUIRE.matcher(text);
    if (single.find()) {
      bindWhole(single.group(1), single.group(2));
      return;
    }
    final Matcher destructured = REQUIRE_DESTRUCTURED.matcher(text);
    if (destructured.find()) {
      bindNamed(destructured.group(1), module(destructured.group(2)), ",", ":");
    }
  }

  /**
   * Binds each specifier of a brace list: {@code exec}, {@code exec as run} or {@code exec: run}.
   */
  private void bindNamed(
      final String specifiers,
      final String module,
      final String separator,
      final String alias
  ) {
    for (final String specifier : specifiers.split(separator)) {
      final String[] parts = specifier.trim().replaceFirst(TYPE_ONLY, "").split(alias);
      final String name = parts[0].trim();
      final String local = parts[parts.length - 1].trim();
      if (!name.isEmpty() && !local.isEmpty()) {
        named.put(local, module + "." + name);
        modules.put(local, module);
        imported.put(local, name);
      }
    }
  }

  private void bindWhole(final String local, final String specifier) {
    modules.put(local, module(specifier));
    if (specifier.startsWith(".")) {
      projectLocals.add(local);
    }
  }

  /**
   * The owner of the methods of an object that another file of the project exports: {@code
   * module#object} for {@code ns.object}, where {@code ns} is bound to a relative module as a whole.
   *
   * @param written a receiver as written, such as {@code security.authenticatedUsers}
   * @return the owner, or empty when the receiver is not a member of such a module
   */
  Optional<String> objectOwner(final String written) {
    final int dot = written.indexOf('.');
    if (dot < 0 || written.indexOf('.', dot + 1) >= 0) {
      return Optional.empty();
    }
    final String local = written.substring(0, dot);
    return projectLocals.contains(local)
        ? Optional.of(modules.get(local) + "#" + written.substring(dot + 1))
        : Optional.empty();
  }

  private String module(final String specifier) {
    if (specifier.startsWith(NODE_PREFIX)) {
      return specifier.substring(NODE_PREFIX_LENGTH);
    }
    return NodeModuleNames.resolve(directory, specifier);
  }
}
