package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;

/** The projects and queries that the native TypeScript fact tests share. */
final class TypeScenarios {
  static final String UNIT = "src/app.ts";
  static final String LIB = "node_modules/typescript/lib/";
  static final String ES5 =
      """
      interface Math { max(...values: number[]): number; }
      declare var Math: Math;
      interface Array<T> {
        length: number;
        map<U>(callback: (value: T, index: number) => U): U[];
        [n: number]: T;
      }
      interface Promise<T> { then<R>(onfulfilled: (value: T) => R): Promise<R>; }
      interface String { length: number; toUpperCase(): string; }
      interface Number { toFixed(digits?: number): string; }
      interface Boolean { valueOf(): boolean; }
      interface Function { call(thisArg: any): any; }
      """;

  static TypeProject project(final String unit) {
    return new TypeProject()
        .file(UNIT, unit)
        .file(LIB + "lib.d.ts", "/// <reference lib=\"es5\" />\n")
        .file(LIB + "lib.es5.d.ts", ES5);
  }

  /** The type of the last occurrence of {@code token} in the unit, or {@code -}. */
  static String type(final TypeProject project, final String token) {
    final UnitTypeFacts facts = project.answer(UNIT, token);
    final String text = project.files().read(UNIT).orElseThrow();
    return facts.typeOf(TypeProject.span(text, token)).map(TypeRef::display).orElse("-");
  }

  private TypeScenarios() {}
}
