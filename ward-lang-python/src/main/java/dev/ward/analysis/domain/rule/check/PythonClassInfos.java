package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the classes a module declares at its top level: header, metaclass and members. */
final class PythonClassInfos {

  private static final Pattern METACLASS_KEYWORD = Pattern.compile("metaclass=(.+)$");
  private static final Pattern ABC_METACLASS = Pattern.compile("^(?:abc\\.)?ABCMeta$");
  private static final Pattern HEADER = Pattern.compile("^(?:async)?class\\w+\\(([^)]*)\\)");
  private static final String STAR = "*";
  private static final String NO_DECORATORS = "";

  private PythonClassInfos() {}

  /** The classes the module declares at its top level, by name. */
  static Map<String, PythonClassModel.ClassInfo> of(final SyntaxNode root) {
    final Map<String, PythonClassModel.ClassInfo> found = new TreeMap<>();
    for (final Map.Entry<String, SyntaxNode> entry : ModuleDefinitions.of(root).entrySet()) {
      if (SyntaxKinds.CLASSDEF.equals(entry.getValue().kind())) {
        found.put(entry.getKey(), info(entry.getValue()));
      }
    }
    return found;
  }

  private static PythonClassModel.ClassInfo info(final SyntaxNode definition) {
    final String name = ModuleDefinitions.definedName(definition).orElse("");
    final List<String> bases = new ArrayList<>();
    final boolean abcMetaclass = readHeader(SyntaxWalk.text(definition), bases);
    final Map<String, PythonClassModel.Method> methods = new TreeMap<>();
    collectMethods(definition, methods);
    final Set<String> attributes = new HashSet<>(FunctionBodies.assignedNames(definition));
    attributes.addAll(nestedClassNames(definition));
    return new PythonClassModel.ClassInfo(
        name, definition, bases, abcMetaclass, methods, attributes);
  }

  /** Adds the positional bases, and reports whether the metaclass keyword is ABCMeta. */
  private static boolean readHeader(final String text, final List<String> bases) {
    boolean abcMetaclass = false;
    for (final String argument : headerArguments(text)) {
      final Matcher metaclass = METACLASS_KEYWORD.matcher(argument);
      if (metaclass.find()) {
        abcMetaclass = ABC_METACLASS.matcher(metaclass.group(1)).matches();
      } else if (!argument.contains("=") && !argument.startsWith(STAR)) {
        bases.add(argument);
      }
    }
    return abcMetaclass;
  }

  private static List<String> headerArguments(final String header) {
    final Matcher matcher = HEADER.matcher(header);
    return matcher.find() ? ArgumentList.split(matcher.group(1)) : List.of();
  }

  private static Set<String> nestedClassNames(final SyntaxNode definition) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode nested :
        FunctionBodies.ownScope(definition, Set.of(SyntaxKinds.CLASSDEF))) {
      ModuleDefinitions.definedName(nested).ifPresent(names::add);
    }
    return names;
  }

  /** Collects the functions defined directly in a class body, not in nested scopes. */
  private static void collectMethods(
      final SyntaxNode scope, final Map<String, PythonClassModel.Method> methods) {
    for (final SyntaxNode child : scope.children()) {
      if (SyntaxKinds.DECORATED.equals(child.kind())) {
        addDecorated(child, methods);
      } else if (SyntaxKinds.FUNCDEF.equals(child.kind())) {
        addMethod(child, NO_DECORATORS, methods);
      } else if (!FunctionBodies.SCOPES.contains(child.kind())) {
        collectMethods(child, methods);
      }
    }
  }

  private static void addDecorated(
      final SyntaxNode decorated, final Map<String, PythonClassModel.Method> methods) {
    decoratedDefinition(decorated)
        .ifPresent(inner -> addMethod(inner, decoratorText(decorated, inner), methods));
  }

  /** The decorator lines of a decorated definition, which precede the definition's own text. */
  private static String decoratorText(final SyntaxNode decorated, final SyntaxNode inner) {
    final String text = SyntaxWalk.text(decorated);
    final String innerText = SyntaxWalk.text(inner);
    final String prefix =
        text.endsWith(innerText) ? text.substring(0, text.length() - innerText.length()) : "";
    return prefix.strip();
  }

  private static Optional<SyntaxNode> decoratedDefinition(final SyntaxNode decorated) {
    return decorated.children().stream()
        .filter(child -> SyntaxKinds.FUNCDEF.equals(child.kind()))
        .findFirst();
  }

  private static void addMethod(
      final SyntaxNode function,
      final String decorators,
      final Map<String, PythonClassModel.Method> methods) {
    ModuleDefinitions.definedName(function)
        .ifPresent(
            name -> methods.put(name, new PythonClassModel.Method(name, function, decorators)));
  }
}
