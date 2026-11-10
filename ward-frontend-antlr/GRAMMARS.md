# Grammars

Parsers in this module are generated at build time from the grammar files under
`src/main/antlr4/dev/ward/analysis/frontend/antlr/`. The pinned sources and their licenses:

| Language | Source | Pin | License |
|---|---|---|---|
| Java | `grammars-v4/java/java` (`JavaLexer.g4`, `JavaParser.g4`, `Java/JavaParserBase.java`) | `b96e04db9d54bb313e542cc3e6ee913900a21520` | BSD (Terence Parr, Sam Harwell, Ivan Kochurkin, Michał Lorek) |
| Kotlin | `grammars-v4/kotlin/kotlin` (`KotlinLexer.g4`, `KotlinParser.g4`, `imports/UnicodeClasses.g4`) | `b96e04db9d54bb313e542cc3e6ee913900a21520` | Apache 2.0 |
| JavaScript | `grammars-v4/javascript/javascript` (`JavaScriptLexer.g4`, `JavaScriptParser.g4`, base classes) | `b96e04db9d54bb313e542cc3e6ee913900a21520` | MIT |
| TypeScript | `grammars-v4/javascript/typescript` (`TypeScriptLexer.g4`, `TypeScriptParser.g4`, base classes) | `b96e04db9d54bb313e542cc3e6ee913900a21520` | MIT |
| Python | `grammars-v4/python/python3` (`Python3Lexer.g4`, `Python3Parser.g4`, base classes) | `b96e04db9d54bb313e542cc3e6ee913900a21520` | MIT |
| Swift | Ward-owned `swift/SwiftLexer.g4` and `swift/SwiftParser.g4`, derived from `grammars-v4/swift/swift3` (`Swift3.g4`) and `SwiftSupport.java` | `b96e04db9d54bb313e542cc3e6ee913900a21520` (base) | BSD-3-Clause (Terence Parr); the license header is kept in the derived file |

The `grammars-v4` repository has no single license; each grammar carries its own license in its files.
`swift/swift5` is not used: it states no license.

## Swift changes from `swift/swift3`

- `actor` declarations
- multi-line string literals (`"""`)
- `async` in function signatures
- `any` and `some` in types, as contextual keywords
- `await` expressions and `for await`
- Swift 5 key path literals (`\Type.member`)

The derived files keep the BSD-3-Clause notice and are marked as a Ward modification. The combined swift3 grammar is split into a lexer and a parser grammar so that the lexer can extend `WardLexerBase`; the parser uses implicit literal tokens declared in the lexer.
