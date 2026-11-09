package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The differences between the parameters of an overridden method and its override, as Pylint's
 * {@code _different_parameters} finds them. Positional parameters are compared by position, then by
 * name; keyword-only parameters by name; and {@code *args} or {@code **kwargs} by presence.
 */
final class ParameterComparison {

  /** The messages Pylint's comparison produces. */
  enum Kind {
    NUMBER,
    RENAMED,
    VARIADICS
  }

  private static final String STATIC = "staticmethod";
  private static final Pattern IGNORED_NAME =
      Pattern.compile("_+$|(_[a-zA-Z0-9_]*[a-zA-Z0-9]+?$)|dummy|^ignored_|^unused_");
  private static final Set<String> CONSTRUCTORS =
      Set.of("__new__", "__init__", "__init_subclass__", "__post_init__");
  private static final Set<String> SPECIAL_METHODS =
      Set.of(
          "__abs__",
          "__add__",
          "__aenter__",
          "__aexit__",
          "__aiter__",
          "__and__",
          "__anext__",
          "__await__",
          "__bool__",
          "__bytes__",
          "__call__",
          "__ceil__",
          "__cmp__",
          "__complex__",
          "__contains__",
          "__copy__",
          "__deepcopy__",
          "__del__",
          "__delattr__",
          "__delete__",
          "__delitem__",
          "__dir__",
          "__div__",
          "__divmod__",
          "__enter__",
          "__eq__",
          "__exit__",
          "__float__",
          "__floor__",
          "__floordiv__",
          "__format__",
          "__fspath__",
          "__ge__",
          "__get__",
          "__getattr__",
          "__getattribute__",
          "__getitem__",
          "__getnewargs__",
          "__getnewargs_ex__",
          "__getstate__",
          "__gt__",
          "__hash__",
          "__iadd__",
          "__iand__",
          "__ifloordiv__",
          "__ilshift__",
          "__imatmul__",
          "__imod__",
          "__imul__",
          "__index__",
          "__init__",
          "__init_subclass__",
          "__instancecheck__",
          "__int__",
          "__invert__",
          "__ior__",
          "__ipow__",
          "__irshift__",
          "__isub__",
          "__iter__",
          "__itruediv__",
          "__ixor__",
          "__le__",
          "__len__",
          "__length_hint__",
          "__lshift__",
          "__lt__",
          "__matmul__",
          "__missing__",
          "__mod__",
          "__mul__",
          "__ne__",
          "__neg__",
          "__new__",
          "__nonzero__",
          "__or__",
          "__pos__",
          "__pow__",
          "__radd__",
          "__rand__",
          "__rdivmod__",
          "__reduce__",
          "__reduce_ex__",
          "__repr__",
          "__reversed__",
          "__rfloordiv__",
          "__rlshift__",
          "__rmatmul__",
          "__rmod__",
          "__rmul__",
          "__ror__",
          "__round__",
          "__rpow__",
          "__rrshift__",
          "__rshift__",
          "__rsub__",
          "__rtruediv__",
          "__rxor__",
          "__set__",
          "__set_name__",
          "__setattr__",
          "__setitem__",
          "__setstate__",
          "__str__",
          "__sub__",
          "__subclasscheck__",
          "__subclasses__",
          "__truediv__",
          "__trunc__",
          "__unicode__",
          "__xor__");

  private ParameterComparison() {}

  /** The messages for the override of {@code parent} by {@code child}, in Pylint's order. */
  static List<Kind> differences(
      final PythonClassModel.Method parent, final PythonClassModel.Method child) {
    final MethodSignature original = signature(parent);
    final MethodSignature overriding = signature(child);
    final List<MethodSignature.Param> originalPositional = bound(original, parent);
    final List<MethodSignature.Param> overridingPositional = bound(overriding, child);
    final List<MethodSignature.Param> originalKeywords =
        overriding.kwargs()
            ? keepNamed(original.keywordOnly(), overriding.keywordOnly())
            : original.keywordOnly();
    final List<MethodSignature.Param> keptPositional =
        overriding.varargs()
            ? keepNamed(originalPositional, overridingPositional)
            : originalPositional;
    final List<Kind> out =
        combine(
            differentPositional(keptPositional, overridingPositional),
            differentKeywords(originalKeywords, overriding.keywordOnly()));
    if (lostVariadics(original, overriding)) {
      out.add(Kind.VARIADICS);
    }
    return filtered(child.name(), out);
  }

  private static MethodSignature signature(final PythonClassModel.Method method) {
    return MethodSignature.of(method.node())
        .orElse(new MethodSignature(List.of(), List.of(), false, false));
  }

  private static boolean lostVariadics(
      final MethodSignature original, final MethodSignature overriding) {
    return (original.kwargs() && !overriding.kwargs())
        || (original.varargs() && !overriding.varargs());
  }

  /** The positional parameters, without the first one when the method is bound. */
  private static List<MethodSignature.Param> bound(
      final MethodSignature signature, final PythonClassModel.Method method) {
    final List<MethodSignature.Param> positional = signature.positional();
    if (method.decoratedWith(STATIC) || positional.isEmpty()) {
      return positional;
    }
    return positional.subList(1, positional.size());
  }

  private static List<MethodSignature.Param> keepNamed(
      final List<MethodSignature.Param> params, final List<MethodSignature.Param> kept) {
    final Set<String> names = new HashSet<>();
    kept.forEach(param -> names.add(param.name()));
    return params.stream().filter(param -> names.contains(param.name())).toList();
  }

  private static List<Kind> differentPositional(
      final List<MethodSignature.Param> original, final List<MethodSignature.Param> overriding) {
    final List<Kind> out = new ArrayList<>();
    final int size = Math.max(original.size(), overriding.size());
    for (int index = 0; index < size; index++) {
      final MethodSignature.Param first = index < original.size() ? original.get(index) : null;
      final MethodSignature.Param second = index < overriding.size() ? overriding.get(index) : null;
      if (second == null) {
        return List.of(Kind.NUMBER);
      }
      if (first == null) {
        if (!second.hasDefault()) {
          return List.of(Kind.NUMBER);
        }
      } else if (!ignored(first.name(), second.name()) && !first.name().equals(second.name())) {
        out.add(Kind.RENAMED);
      }
    }
    return out;
  }

  private static boolean ignored(final String first, final String second) {
    return IGNORED_NAME.matcher(first).lookingAt() || IGNORED_NAME.matcher(second).lookingAt();
  }

  private static List<Kind> differentKeywords(
      final List<MethodSignature.Param> original, final List<MethodSignature.Param> overriding) {
    final Set<String> originalNames = new HashSet<>();
    original.forEach(param -> originalNames.add(param.name()));
    final Set<String> overridingNames = new HashSet<>();
    overriding.forEach(param -> overridingNames.add(param.name()));
    if (!overridingNames.containsAll(originalNames)) {
      return List.of(Kind.NUMBER);
    }
    for (final MethodSignature.Param param : overriding) {
      if (!originalNames.contains(param.name()) && !param.hasDefault()) {
        return List.of(Kind.NUMBER);
      }
    }
    return List.of();
  }

  /** A number mismatch on both sides is reported once, as Pylint does. */
  private static List<Kind> combine(final List<Kind> positional, final List<Kind> keywords) {
    final List<Kind> out = new ArrayList<>();
    if (positional.equals(List.of(Kind.NUMBER)) && keywords.equals(List.of(Kind.NUMBER))) {
      out.add(Kind.NUMBER);
      return out;
    }
    out.addAll(positional);
    out.addAll(keywords);
    return out;
  }

  /** Constructors ignore every difference; special methods keep only the count difference. */
  private static List<Kind> filtered(final String name, final List<Kind> kinds) {
    if (CONSTRUCTORS.contains(name)) {
      return List.of();
    }
    if (SPECIAL_METHODS.contains(name)) {
      return kinds.stream().filter(kind -> kind == Kind.NUMBER).toList();
    }
    return kinds;
  }
}
