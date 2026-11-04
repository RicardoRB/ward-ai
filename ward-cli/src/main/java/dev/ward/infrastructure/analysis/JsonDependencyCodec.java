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
              all(dto.declared, SymbolDto::toSymbol)));
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
  public static final class SummaryDto {
    public int formatVersion;
    public String key;
    public String path;
    public List<SymbolDto> exports;
    public List<ReferenceDto> imports;
    public List<SymbolDto> declared;

    static SummaryDto from(final String key, final FileSummary summary) {
      final SummaryDto dto = new SummaryDto();
      dto.formatVersion = JsonAnalysisResultCodec.FORMAT_VERSION;
      dto.key = key;
      dto.path = summary.path();
      dto.exports = summary.exports().stream().map(SymbolDto::from).toList();
      dto.imports = summary.imports().stream().map(ReferenceDto::from).toList();
      dto.declared = summary.declared().stream().map(SymbolDto::from).toList();
      return dto;
    }
  }

  /** One stored symbol. */
  @JsonType
  public static final class SymbolDto {
    public String name;
    public SpanDto span;

    static SymbolDto from(final Symbol symbol) {
      final SymbolDto dto = new SymbolDto();
      dto.name = symbol.name();
      dto.span = SpanDto.from(symbol.span());
      return dto;
    }

    Symbol toSymbol() {
      if (name == null) {
        throw new IllegalArgumentException("Cached symbol is incomplete");
      }
      return new Symbol(name, span(span));
    }
  }

  /** One stored reference. */
  @JsonType
  public static final class ReferenceDto {
    public String specifier;
    public SpanDto span;

    static ReferenceDto from(final Reference reference) {
      final ReferenceDto dto = new ReferenceDto();
      dto.specifier = reference.specifier();
      dto.span = SpanDto.from(reference.span());
      return dto;
    }

    Reference toReference() {
      if (specifier == null) {
        throw new IllegalArgumentException("Cached reference is incomplete");
      }
      return new Reference(specifier, span(span));
    }
  }

  /** One stored region of a source unit. */
  @JsonType
  public static final class SpanDto {
    public int startLine;
    public int startColumn;
    public int endLine;
    public int endColumn;
    public int startOffset;
    public int endOffset;

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
  public static final class IndexDto {
    public int formatVersion;
    public List<IndexEntryDto> entries;

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
  public static final class IndexEntryDto {
    public String path;
    public List<String> dependents;

    static IndexEntryDto of(final String path, final List<String> dependents) {
      final IndexEntryDto dto = new IndexEntryDto();
      dto.path = path;
      dto.dependents = dependents;
      return dto;
    }
  }
}
