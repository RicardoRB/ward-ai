package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.jvm.JavaScopeAnalysis;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Runs the native facts over source text, reading JDK classes from the test JVM. */
final class Probe {
  private final Map<String, String> others = new HashMap<>();
  private final String text;
  private final NativeJvmTypeFacts nativeFacts;
  private final SourceUnit unit;

  Probe(final String text, final Map<String, String> others) {
    this(text, others, AnalysisLanguage.JAVA);
  }

  Probe(final String text, final Map<String, String> others, final AnalysisLanguage language) {
    this.text = text;
    this.others.putAll(others);
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final JvmLibrary library =
        name -> {
          try (InputStream in =
              ClassLoader.getSystemResourceAsStream(name.replace('.', '/') + ".class")) {
            return in == null ? Optional.empty() : Optional.of(ClassFileReader.read(in.readAllBytes()));
          } catch (IOException | ClassFileException unreadable) {
            return Optional.empty();
          }
        };
    final JvmSources sources =
        name ->
            Optional.ofNullable(this.others.get(name))
                .map(
                    source ->
                        new SourceUnit(name.replace('.', '/') + ".java", AnalysisLanguage.JAVA, source));
    final NativeJvmTypeFacts native1 =
        new NativeJvmTypeFacts(
            new JvmClasses(library, sources, frontend),
            frontend,
            Map.of(
                AnalysisLanguage.JAVA, new JavaScopeAnalysis(environments -> Set.of()),
                AnalysisLanguage.KOTLIN, new dev.ward.analysis.domain.jvm.KotlinScopeAnalysis(environments -> Set.of())));
    this.nativeFacts = native1;
    this.unit = new SourceUnit("src/App." + (language == AnalysisLanguage.KOTLIN ? "kt" : "java"), language, text);
  }

  UnitTypeFacts at(final Span span) {
    return nativeFacts.factsOf(unit, List.of(span), EnumSet.allOf(FactKind.class));
  }

  static Probe kotlin(final String text) {
    return new Probe(text, Map.of(), AnalysisLanguage.KOTLIN);
  }

  static Probe of(final String text) {
    return new Probe(text, Map.of());
  }

  Span span(final String token) {
    return Spans.of(text, token, true);
  }

  Span first(final String token) {
    return Spans.of(text, token, false);
  }

  /** The type display of the last occurrence of {@code token}. */
  String type(final String token) {
    return at(span(token)).typeOf(span(token)).map(ref -> ref.display() + args(ref)).orElse("?");
  }

  private static String args(final dev.ward.analysis.domain.types.TypeRef ref) {
    return ref.arguments().isEmpty()
        ? ""
        : ref.arguments().stream().map(a -> a.display() + args(a)).toList().toString().replace('[', '<').replace(']', '>');
  }

  Optional<CallTarget> call(final String token) {
    return at(span(token)).callTarget(span(token));
  }

}
