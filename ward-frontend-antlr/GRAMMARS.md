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
| Rust | `grammars-v4/rust` (`RustLexer.g4`, `RustParser.g4`, `Java/RustLexerBase.java`, `Java/RustParserBase.java`), Ward-owned copies under `rust/` | `b96e04db9d54bb313e542cc3e6ee913900a21520` | MIT (The Rust Project Developers, Student Main); the license header is kept in every derived file |

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

## Rust changes from `rust`

The grammar covers editions 2021 and 2024. The files are split under `dev.ward`, the lexer extends `WardLexerBase`, and the predicates are renamed to Ward's style. Constructs the pinned grammar lacked were added from the Rust reference:

- `let`-`else` (`let PAT = EXPR else { ... };`)
- `let` conditions as expressions, so `if let` and `while let` are one `ifExpression` and one `predicateLoopExpression`, and a `let` chain (`if let A = a && b && let B = c`) parses; the old `ifLetExpression` and `predicatePatternLoopExpression` rules are gone
- `async` closures (`async |x| ...`, `async move |x| ...`)
- C string literals (`c"..."`, `cr#"..."#`)
- raw borrows (`&raw const place`, `&raw mut place`; `raw` is a contextual word)
- `const { ... }` blocks and labeled blocks (`'a: { ... break 'a v; }`)
- `unsafe extern` blocks with `safe` items (contextual) and `#[unsafe(attr)]` attributes
- associated types with bounds (`type Item: Bound;`) and where clauses after the default
- const generic parameter defaults (`const N: usize = 4`)
- `use<..>` precise-capturing bounds
- `union` as an identifier (it is a weak keyword)
