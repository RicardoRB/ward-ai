/*
 * [The "BSD license"]
 *  Copyright (c) 2014 Terence Parr
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 *
 * Converted from Apple's doc, http://tinyurl.com/n8rkoue, to ANTLR's
 * meta-language.
 */

// $antlr-format alignTrailingComments true, columnLimit 150, minEmptyLines 1, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine false, allowShortBlocksOnASingleLine true, alignSemicolons hanging, alignColons hanging

lexer grammar SwiftLexer;

options {
    superClass = dev.ward.analysis.frontend.antlr.WardLexerBase;
}

// Literal tokens that the parser uses. Declared first so that keywords win over Identifier.
T__0 : '#' ;
T__1 : '#available' ;
T__2 : '#colorLiteral' ;
T__3 : '#column' ;
T__4 : '#dsohandle' ;
T__5 : '#else' ;
T__6 : '#elseif' ;
T__7 : '#endif' ;
T__8 : '#file' ;
T__9 : '#fileLiteral' ;
T__10 : '#function' ;
T__11 : '#if' ;
T__12 : '#imageLiteral' ;
T__13 : '#keyPath' ;
T__14 : '#line' ;
T__15 : '#selector' ;
T__16 : '#sourceLocation' ;
T__17 : 'Any' ;
T__18 : 'Protocol' ;
T__19 : 'Self' ;
T__20 : 'Type' ;
T__21 : '\\' ;
T__22 : '`' ;
T__23 : 'actor' ;
T__24 : 'alpha' ;
T__25 : 'arch' ;
T__26 : 'arm' ;
T__27 : 'arm64' ;
T__28 : 'as' ;
T__29 : 'assignment' ;
T__30 : 'associatedtype' ;
T__31 : 'associativity' ;
T__32 : 'async' ;
T__33 : 'await' ;
T__34 : 'blue' ;
T__35 : 'break' ;
T__36 : 'case' ;
T__37 : 'catch' ;
T__38 : 'class' ;
T__39 : 'continue' ;
T__40 : 'convenience' ;
T__41 : 'default' ;
T__42 : 'defer' ;
T__43 : 'deinit' ;
T__44 : 'didSet' ;
T__45 : 'do' ;
T__46 : 'dynamic' ;
T__47 : 'else' ;
T__48 : 'enum' ;
T__49 : 'extension' ;
T__50 : 'fallthrough' ;
T__51 : 'false' ;
T__52 : 'file' ;
T__53 : 'fileprivate' ;
T__54 : 'final' ;
T__55 : 'for' ;
T__56 : 'func' ;
T__57 : 'get' ;
T__58 : 'getter:' ;
T__59 : 'green' ;
T__60 : 'guard' ;
T__61 : 'higherThan' ;
T__62 : 'i386' ;
T__63 : 'iOS' ;
T__64 : 'iOSApplicationExtension' ;
T__65 : 'if' ;
T__66 : 'import' ;
T__67 : 'in' ;
T__68 : 'indirect' ;
T__69 : 'infix' ;
T__70 : 'init' ;
T__71 : 'inout' ;
T__72 : 'internal' ;
T__73 : 'is' ;
T__74 : 'lazy' ;
T__75 : 'left' ;
T__76 : 'let' ;
T__77 : 'line' ;
T__78 : 'lowerThan' ;
T__79 : 'macOS' ;
T__80 : 'macOSApplicationExtension' ;
T__81 : 'mutating' ;
T__82 : 'nil' ;
T__83 : 'none' ;
T__84 : 'nonmutating' ;
T__85 : 'of' ;
T__86 : 'open' ;
T__87 : 'operator' ;
T__88 : 'optional' ;
T__89 : 'os' ;
T__90 : 'override' ;
T__91 : 'postfix' ;
T__92 : 'precedence' ;
T__93 : 'precedencegroup' ;
T__94 : 'prefix' ;
T__95 : 'private' ;
T__96 : 'protocol' ;
T__97 : 'public' ;
T__98 : 'red' ;
T__99 : 'repeat' ;
T__100 : 'required' ;
T__101 : 'resourceName' ;
T__102 : 'rethrows' ;
T__103 : 'return' ;
T__104 : 'right' ;
T__105 : 'safe' ;
T__106 : 'self' ;
T__107 : 'set' ;
T__108 : 'setter:' ;
T__109 : 'static' ;
T__110 : 'struct' ;
T__111 : 'subscript' ;
T__112 : 'super' ;
T__113 : 'swift' ;
T__114 : 'switch' ;
T__115 : 'throw' ;
T__116 : 'throws' ;
T__117 : 'true' ;
T__118 : 'try' ;
T__119 : 'tvOS' ;
T__120 : 'type' ;
T__121 : 'typealias' ;
T__122 : 'unowned' ;
T__123 : 'unowned(safe)' ;
T__124 : 'unowned(unsafe)' ;
T__125 : 'unsafe' ;
T__126 : 'var' ;
T__127 : 'watchOS' ;
T__128 : 'weak' ;
T__129 : 'where' ;
T__130 : 'while' ;
T__131 : 'willSet' ;
T__132 : 'x86_64' ;

Platform_name_platform_version
    : Platform_name WS Platform_version
    ;

fragment Platform_name
    : 'iOS'
    | 'iOSApplicationExtension'
    | 'macOS'
    | 'macOSApplicationExtension'
    | 'watchOS'
    | 'tvOS'
    ;

fragment Platform_version
    : Pure_decimal_digits
    | Pure_decimal_digits '.' Pure_decimal_digits
    | Pure_decimal_digits '.' Pure_decimal_digits '.' Pure_decimal_digits
    ;

// Generic Parameters and Arguments

// GRAMMAR OF A GENERIC PARAMETER CLAUSE

Identifier
    : Identifier_head Identifier_characters?
    | '`' Identifier_head Identifier_characters? '`'
    | Implicit_parameter_name
    ;

// identifier_list : identifier (',' identifier)* ;

// 
// identifier is context sensitive
// See: closure_parameter_clause_identifier_list
fragment Identifier_head
    : [a-zA-Z]
    | '_'
    | '\u00A8'
    | '\u00AA'
    | '\u00AD'
    | '\u00AF'
    | [\u00B2-\u00B5]
    | [\u00B7-\u00BA]
    | [\u00BC-\u00BE]
    | [\u00C0-\u00D6]
    | [\u00D8-\u00F6]
    | [\u00F8-\u00FF]
    | [\u0100-\u02FF]
    | [\u0370-\u167F]
    | [\u1681-\u180D]
    | [\u180F-\u1DBF]
    | [\u1E00-\u1FFF]
    | [\u200B-\u200D]
    | [\u202A-\u202E]
    | [\u203F-\u2040]
    | '\u2054'
    | [\u2060-\u206F]
    | [\u2070-\u20CF]
    | [\u2100-\u218F]
    | [\u2460-\u24FF]
    | [\u2776-\u2793]
    | [\u2C00-\u2DFF]
    | [\u2E80-\u2FFF]
    | [\u3004-\u3007]
    | [\u3021-\u302F]
    | [\u3031-\u303F]
    | [\u3040-\uD7FF]
    | [\uF900-\uFD3D]
    | [\uFD40-\uFDCF]
    | [\uFDF0-\uFE1F]
    | [\uFE30-\uFE44]
    | [\uFE47-\uFFFD]
    /*
 | U+10000-U+1FFFD | U+20000-U+2FFFD | U+30000-U+3FFFD | U+40000-U+4FFFD
 | U+50000-U+5FFFD | U+60000-U+6FFFD | U+70000-U+7FFFD | U+80000-U+8FFFD
 | U+90000-U+9FFFD | U+A0000-U+AFFFD | U+B0000-U+BFFFD | U+C0000-U+CFFFD
 | U+D0000-U+DFFFD or U+E0000-U+EFFFD
*/
    ;

fragment Identifier_character
    : [0-9]
    | [\u0300-\u036F]
    | [\u1DC0-\u1DFF]
    | [\u20D0-\u20FF]
    | [\uFE20-\uFE2F]
    | Identifier_head
    ;

fragment Identifier_characters
    : Identifier_character+
    ;

// Keywords reserved in particular contexts: associativity, convenience, dynamic, didSet, final, get, infix, indirect, lazy, left, mutating, none, nonmutating, optional, override, postfix, precedence, prefix, Protocol, required, right, set, Type, unowned, weak, and willSet. Outside the context in which they appear in the grammar, they can be used as identifiers.
// context_sensitive_keyword :
//  'associativity' | 'convenience' | 'dynamic' | 'didSet'
//  | 'final' | 'get' | 'infix' | 'indirect'
//  | 'lazy' | 'left' | 'mutating' | 'none'
//  | 'nonmutating' | 'optional' | 'override' | 'postfix'
//  | 'precedence' | 'prefix' | 'Protocol' | 'required'
//  | 'right' | 'set' | 'Type' | 'unowned'
//  | 'weak' | 'willSet'
//
// ^- this does not work. "[10].index(of: 10)". "of" is a keyword. "type(of: self)"

// Added by myself.
// Tested all alphanumeric tokens in playground.
// E.g. "let mutating = 1".
// E.g. "func mutating() {}".
//
// In source code of swift there are multiple cases of error diag::keyword_cant_be_identifier.
// Maybe it is not even a single error when keyword can't be identifier.
//
DOT
    : '.'
    ;

LCURLY
    : '{'
    ;

LPAREN
    : '('
    ;

LBRACK
    : '['
    ;

RCURLY
    : '}'
    ;

RPAREN
    : ')'
    ;

RBRACK
    : ']'
    ;

COMMA
    : ','
    ;

COLON
    : ':'
    ;

SEMI
    : ';'
    ;

LT
    : '<'
    ;

GT
    : '>'
    ;

UNDERSCORE
    : '_'
    ;

BANG
    : '!'
    ;

QUESTION
    : '?'
    ;

AT
    : '@'
    ;

AND
    : '&'
    ;

SUB
    : '-'
    ;

EQUAL
    : '='
    ;

OR
    : '|'
    ;

DIV
    : '/'
    ;

ADD
    : '+'
    ;

MUL
    : '*'
    ;

MOD
    : '%'
    ;

CARET
    : '^'
    ;

TILDE
    : '~'
    ;

/** Need to separate this out from Prefix_operator as it's referenced in numeric_literal
 *  as specifically a negation prefix op.
 */
Operator_head_other // valid operator chars not used by Swift itself
    : [\u00A1-\u00A7]
    | [\u00A9\u00AB]
    | [\u00AC\u00AE]
    | [\u00B0-\u00B1\u00B6\u00BB\u00BF\u00D7\u00F7]
    | [\u2016-\u2017\u2020-\u2027]
    | [\u2030-\u203E]
    | [\u2041-\u2053]
    | [\u2055-\u205E]
    | [\u2190-\u23FF]
    | [\u2500-\u2775]
    | [\u2794-\u2BFF]
    | [\u2E00-\u2E7F]
    | [\u3001-\u3003]
    | [\u3008-\u3030]
    ;

Operator_following_character
    : [\u0300-\u036F]
    | [\u1DC0-\u1DFF]
    | [\u20D0-\u20FF]
    | [\uFE00-\uFE0F]
    | [\uFE20-\uFE2F]
    //| [\uE0100-\uE01EF]  ANTLR can't do >16bit char
    ;

Implicit_parameter_name
    : '$' Pure_decimal_digits
    ;

// GRAMMAR OF A LITERAL

Binary_literal
    : '0b' Binary_digit Binary_literal_characters?
    ;

fragment Binary_digit
    : [01]
    ;

fragment Binary_literal_character
    : Binary_digit
    | '_'
    ;

fragment Binary_literal_characters
    : Binary_literal_character+
    ;

Octal_literal
    : '0o' Octal_digit Octal_literal_characters?
    ;

fragment Octal_digit
    : [0-7]
    ;

fragment Octal_literal_character
    : Octal_digit
    | '_'
    ;

fragment Octal_literal_characters
    : Octal_literal_character+
    ;

Decimal_literal
    : [0-9] [0-9_]*
    ;

Pure_decimal_digits
    : [0-9]+
    ;

fragment Decimal_digit
    : [0-9]
    ;

fragment Decimal_literal_character
    : Decimal_digit
    | '_'
    ;

fragment Decimal_literal_characters
    : Decimal_literal_character+
    ;

Hexadecimal_literal
    : '0x' Hexadecimal_digit Hexadecimal_literal_characters?
    ;

fragment Hexadecimal_digit
    : [0-9a-fA-F]
    ;

fragment Hexadecimal_literal_character
    : Hexadecimal_digit
    | '_'
    ;

fragment Hexadecimal_literal_characters
    : Hexadecimal_literal_character+
    ;

// GRAMMAR OF A FLOATING_POINT LITERAL

Floating_point_literal
    : Decimal_literal Decimal_fraction? Decimal_exponent?
    | Hexadecimal_literal Hexadecimal_fraction? Hexadecimal_exponent
    ;

fragment Decimal_fraction
    : '.' Decimal_literal
    ;

fragment Decimal_exponent
    : Floating_point_e Sign? Decimal_literal
    ;

fragment Hexadecimal_fraction
    : '.' Hexadecimal_digit Hexadecimal_literal_characters?
    ;

fragment Hexadecimal_exponent
    : Floating_point_p Sign? Decimal_literal
    ;

fragment Floating_point_e
    : [eE]
    ;

fragment Floating_point_p
    : [pP]
    ;

fragment Sign
    : [+\-]
    ;

// GRAMMAR OF A STRING LITERAL

Multiline_string_literal
    : '"""' .*? '"""'
    ;

Static_string_literal
    : '"' Quoted_text? '"'
    ;

fragment Quoted_text
    : Quoted_text_item+
    ;

fragment Quoted_text_item
    : Escaped_character
    | ~["\n\r\\]
    ;

fragment Escaped_character
    : '\\' [0\\tnr"']
    | '\\x' Hexadecimal_digit Hexadecimal_digit
    | '\\u' '{' Hexadecimal_digit Hexadecimal_digit Hexadecimal_digit Hexadecimal_digit '}'
    | '\\u' '{' Hexadecimal_digit Hexadecimal_digit Hexadecimal_digit Hexadecimal_digit Hexadecimal_digit Hexadecimal_digit Hexadecimal_digit
        Hexadecimal_digit '}'
    ;

Interpolated_string_literal
    : '"' Interpolated_text_item* '"'
    ;

fragment Interpolated_text_item
    : '\\(' (Interpolated_string_literal | Interpolated_text_item)+ ')' // nested strings allowed
    | Quoted_text_item
    ;

WS
    : [ \n\r\t\u000B\u000C\u0000]+ -> channel(HIDDEN)
    ;

Block_comment
    : '/*' (Block_comment | .)*? '*/' -> channel(HIDDEN)
    ; // nesting comments allowed

Line_comment
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;
