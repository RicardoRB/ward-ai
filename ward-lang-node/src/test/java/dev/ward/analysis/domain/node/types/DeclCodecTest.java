package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Checks that a declaration index survives being written and read back. */
class DeclCodecTest {
  private static final byte UNKNOWN_VERSION = 99;
  private static final int TRUNCATED = 6;
  private static final String SOURCE =
      """
      import { a as b } from "./a";
      export interface Pair<T> extends Base<T> { first: T; readonly second?: T[]; run<U>(cb: (v: T) => U): U[]; }
      export declare class Box<T = string> extends Pair<T> implements Named {
        static make(): Box<string>;
        constructor(private n: number);
        value: { x: number } | null;
      }
      export declare function load(id: number, ...rest: string[]): Promise<string>;
      export declare const flag: true;
      export type Mode = "fast" | "slow";
      export declare enum Level { Low, High }
      declare namespace NS { const size: number; }
      declare module "ambient" { export function f(): void; }
      export * from "./z";
      export { load as fetchIt };
      """;

  @Test
  void writesAndReadsBackTheSameIndex() {
    final FileDecls decls =
        DeclReader.read(
            new AntlrLanguageFrontend()
                .parse(
                    new dev.ward.analysis.domain.ir.SourceUnit(
                        "x.d.ts", dev.ward.analysis.domain.ir.AnalysisLanguage.TYPESCRIPT, SOURCE))
                .tree(),
            SOURCE);

    final FileDecls back = DeclCodec.decode(DeclCodec.encode(decls)).orElseThrow();

    assertThat(back.isModule()).isTrue();
    assertThat(back.imports()).isEqualTo(decls.imports());
    assertThat(back.exports()).isEqualTo(decls.exports()).hasSize(2);
    assertThat(back.modules().keySet()).containsExactly("ambient");
    assertThat(back.decls()).hasSameSizeAs(decls.decls());
    for (int index = 0; index < decls.decls().size(); index++) {
      assertThat(printed(back.decls().get(index))).isEqualTo(printed(decls.decls().get(index)));
    }
    assertThat(decls.decls())
        .extracting(Decl::name)
        .containsExactly("Pair", "Box", "load", "flag", "Mode", "Level", "NS");
  }

  private static String printed(final Decl decl) {
    final StringBuilder out = new StringBuilder(64);
    out.append(decl.kind()).append(' ').append(decl.name()).append(' ').append(decl.flags());
    decl.members()
        .forEach(
            member ->
                out.append(' ').append(member.name()).append(':').append(member.type().print()));
    decl.header().calls().forEach(sig -> out.append(' ').append(TsType.printSig(sig, "=>")));
    decl.header()
        .constructs()
        .forEach(sig -> out.append(" new").append(TsType.printSig(sig, "=>")));
    decl.header().extendsTypes().forEach(type -> out.append(" extends ").append(type.print()));
    out.append(' ').append(decl.type().print());
    decl.body().forEach(inner -> out.append(" {").append(printed(inner)).append('}'));
    return out.toString();
  }

  @Test
  void rejectsBytesOfAnotherVersionOrACorruptIndex() {
    assertThat(DeclCodec.decode(new byte[] {0, 0, 0, UNKNOWN_VERSION})).isEmpty();
    assertThat(DeclCodec.decode(new byte[] {1})).isEmpty();
    assertThat(DeclCodec.decode(Arrays.copyOf(DeclCodec.encode(FileDecls.EMPTY), TRUNCATED)))
        .isEmpty();
  }

  @Test
  void readerKeepsReferenceDirectives() {
    final String text =
        "/// <reference lib=\"es2015\" />\n/// <reference types=\"node\" />\ndeclare var x: number;\n";
    final FileDecls decls =
        DeclReader.read(
            new AntlrLanguageFrontend()
                .parse(
                    new dev.ward.analysis.domain.ir.SourceUnit(
                        "g.d.ts", dev.ward.analysis.domain.ir.AnalysisLanguage.TYPESCRIPT, text))
                .tree(),
            text);

    assertThat(decls.references()).containsExactly("lib:es2015", "types:node");
    assertThat(decls.isModule()).isFalse();
    assertThat(new NodeScopeAnalysis(environments -> java.util.Set.of())).isNotNull();
  }
}
