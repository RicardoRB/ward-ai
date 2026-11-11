package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.fory.json.annotation.JsonType;

/**
 * Encodes the file summaries and the reverse-dependency index of the analysis cache as Fory JSON. A
 * value that does not read back is a miss.
 */
final class JsonDependencyCodec {
  private final JsonCodec codec;

  /** Creates the codec over {@code codec}. */
  JsonDependencyCodec(final JsonCodec codec) {
    this.codec = codec;
  }

  String encodeSummary(final String key, final FileSummary summary) {
    return codec.toJson(SummaryDto.from(key, summary));
  }

  Optional<FileSummary> decodeSummary(final String key, final String value) {
    try {
      final SummaryDto dto = codec.fromJson(value, SummaryDto.class);
      if (dto == null
          || dto.formatVersion != JsonAnalysisResultCodec.FORMAT_VERSION
          || !key.equals(dto.key)
          || dto.path == null) {
        return Optional.empty();
      }
      return Optional.of(
          new FileSummary(
              dto.path,
              all(dto.exports, SymbolDto::toSymbol),
              all(dto.imports, ReferenceDto::toReference),
              all(dto.declared, SymbolDto::toSymbol),
              Set.copyOf(names(dto.usedNames)),
              Set.copyOf(names(dto.assignedNames))));
    } catch (final JsonCodecException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  String encodeIndex(final DependencyGraph graph) {
    return codec.toJson(IndexDto.from(graph));
  }

  /**
   * Converts every element, and fails if one is missing, so a corrupt list is never partly read.
   */
  private static <D, T> List<T> all(final List<D> dtos, final Function<D, T> convert) {
    if (dtos == null) {
      throw new IllegalArgumentException("Cached list is missing");
    }
    return dtos.stream().map(dto -> convertOrFail(dto, convert)).toList();
  }

  private static List<String> names(final List<String> names) {
    if (names == null || names.contains(null)) {
      throw new IllegalArgumentException("Cached names are missing");
    }
    return names;
  }

  private static <D, T> T convertOrFail(final D dto, final Function<D, T> convert) {
    if (dto == null) {
      throw new IllegalArgumentException("Cached element is missing");
    }
    return convert.apply(dto);
  }

  private static Span span(final SpanDto dto) {
    if (dto == null) {
      throw new IllegalArgumentException("Cached span is missing");
    }
    return new Span(
        dto.startLine, dto.startColumn, dto.endLine, dto.endColumn, dto.startOffset, dto.endOffset);
  }

  /** One stored file summary; its public fields are serialized. */
  @JsonType
  static final class SummaryDto {
    int formatVersion;
    String key;
    String path;
    List<SymbolDto> exports;
    List<ReferenceDto> imports;
    List<SymbolDto> declared;
    List<String> usedNames;
    List<String> assignedNames;

    static SummaryDto from(final String key, final FileSummary summary) {
      final SummaryDto dto = new SummaryDto();
      dto.formatVersion = JsonAnalysisResultCodec.FORMAT_VERSION;
      dto.key = key;
      dto.path = summary.path();
      dto.exports = summary.exports().stream().map(SymbolDto::from).toList();
      dto.imports = summary.imports().stream().map(ReferenceDto::from).toList();
      dto.declared = summary.declared().stream().map(SymbolDto::from).toList();
      dto.usedNames = summary.usedNames().stream().sorted().toList();
      dto.assignedNames = summary.assignedNames().stream().sorted().toList();
      return dto;
    }
  }

  /** One stored symbol. */
  @JsonType
  static final class SymbolDto {
    String name;
    String kind;
    SpanDto span;

    static SymbolDto from(final Symbol symbol) {
      final SymbolDto dto = new SymbolDto();
      dto.name = symbol.name();
      dto.kind = symbol.kind();
      dto.span = SpanDto.from(symbol.span());
      return dto;
    }

    Symbol toSymbol() {
      if (name == null || kind == null) {
        throw new IllegalArgumentException("Cached symbol is incomplete");
      }
      return new Symbol(name, span(span), kind);
    }
  }

  /** One stored reference. */
  @JsonType
  static final class ReferenceDto {
    String specifier;
    SpanDto span;
    boolean typeChecking;

    static ReferenceDto from(final Reference reference) {
      final ReferenceDto dto = new ReferenceDto();
      dto.specifier = reference.specifier();
      dto.span = SpanDto.from(reference.span());
      dto.typeChecking = reference.typeChecking();
      return dto;
    }

    Reference toReference() {
      if (specifier == null) {
        throw new IllegalArgumentException("Cached reference is incomplete");
      }
      return new Reference(specifier, span(span), typeChecking);
    }
  }

  /** One stored region of a source unit. */
  @JsonType
  static final class SpanDto {
    int startLine;
    int startColumn;
    int endLine;
    int endColumn;
    int startOffset;
    int endOffset;

    static SpanDto from(final Span span) {
      final SpanDto dto = new SpanDto();
      dto.startLine = span.startLine();
      dto.startColumn = span.startColumn();
      dto.endLine = span.endLine();
      dto.endColumn = span.endColumn();
      dto.startOffset = span.startOffset();
      dto.endOffset = span.endOffset();
      return dto;
    }
  }

  /** The stored reverse-dependency index: the dependents of each depended-on file. */
  @JsonType
  static final class IndexDto {
    int formatVersion;
    List<IndexEntryDto> entries;

    static IndexDto from(final DependencyGraph graph) {
      final IndexDto dto = new IndexDto();
      dto.formatVersion = JsonAnalysisResultCodec.FORMAT_VERSION;
      dto.entries =
          graph.edges().stream()
              .map(DependencyEdge::to)
              .distinct()
              .sorted()
              .map(path -> IndexEntryDto.of(path, graph.dependentsOf(path)))
              .toList();
      return dto;
    }
  }

  /** One file of the index and the files that depend on it. */
  @JsonType
  static final class IndexEntryDto {
    String path;
    List<String> dependents;

    static IndexEntryDto of(final String path, final List<String> dependents) {
      final IndexEntryDto dto = new IndexEntryDto();
      dto.path = path;
      dto.dependents = dependents;
      return dto;
    }
  }
}
