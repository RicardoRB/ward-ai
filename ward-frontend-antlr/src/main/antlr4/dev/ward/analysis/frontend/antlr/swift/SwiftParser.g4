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

parser grammar SwiftParser;

options {
    tokenVocab = SwiftLexer;
}

top_level
    : statements? EOF
    ;

// Statements

// GRAMMAR OF A STATEMENT

statement
    : declaration ';'?
    | loop_statement ';'?
    | branch_statement ';'?
    | labeled_statement ';'?
    | control_transfer_statement ';'?
    | defer_statement ';'?
    | do_statement ';'?
    | compiler_control_statement ';'?
    | {SwiftLookahead.isExpressionStatement(_input)}? expression ';'?
    // proper logic with semicolons is not supported yet. compiler_control_statement should be separated with a newline, but not with a semicolon
    ;

// Quote: https://developer.apple.com/library/content/documentation/Swift/Conceptual/Swift_Programming_Language/Statements.html#//apple_ref/swift/grammar/statements 
// A semicolon (;) can optionally appear after any statement and is used to separate multiple statements if they appear on the same line.
//
// Commentary:
// Swift requires statements to be separated with semicolon or new line.
// But official grammar has only optional semicolon.
// It is improtant to break ambiguities in grammar.    
//
// This logic is implemented in swift in ParseStmt.cpp (Parser::parseBraceItems)
statements
    : statements_impl
    ;

// The predicate reads only the tokens, and no action follows the statement: the parser can then
// evaluate it, and the predicates of the next statement, while it predicts.
statements_impl
    : {SwiftLookahead.isStatementStart(_input)}? statement statements_impl__next?
    ;

// GRAMMAR OF A LOOP STATEMENT

loop_statement
    : for_statement
    | for_in_statement
    | while_statement
    | repeat_while_statement
    ;

// GRAMMAR OF A FOR STATEMENT

for_statement
    : 'for' for_init? ';' expression__condition? ';' expression__condition? code_block
    | 'for' '(' for_init? ';' expression? ';' expression? ')' code_block
    ;

for_init
    : variable_declaration
    | expression_list__condition
    ;

// GRAMMAR OF A FOR_IN STATEMENT

for_in_statement
    : 'for' 'try'? 'await'? ('case' pattern | pattern__binding) 'in' expression__condition where_clause? code_block
    ;

// GRAMMAR OF A WHILE STATEMENT

while_statement
    : 'while' condition_list code_block
    ;

condition_list
    : condition (',' condition)*
    ;

condition
    : expression__condition
    | availability_condition
    | case_condition
    | optional_binding_condition
    ;

case_condition
    : 'case' pattern initializer__condition where_clause?
    ;

optional_binding_condition
    : 'let' pattern__binding initializer__condition?
    | 'var' pattern__binding initializer__condition?
    ;

// GRAMMAR OF A REPEAT-WHILE STATEMENT

repeat_while_statement
    : 'repeat' code_block 'while' expression
    ;

// GRAMMAR OF A BRANCH STATEMENT

branch_statement
    : if_statement
    | guard_statement
    | switch_statement
    ;

// GRAMMAR OF AN IF STATEMENT

if_statement
    : 'if' condition_list code_block else_clause?
    ;

else_clause
    : 'else' code_block
    | 'else' if_statement
    ;

// GRAMMAR OF A GUARD STATEMENT

guard_statement
    : 'guard' condition_list 'else' code_block
    ;

// GRAMMAR OF A SWITCH STATEMENT

switch_statement
    : 'switch' expression__condition '{' switch_cases? '}'
    ;

switch_cases
    : switch_case switch_cases?
    ;

switch_case
    : case_label statements
    | default_label statements
    | conditional_compilation_block
    ;

case_label
    : attributes? 'case' case_item_list ':'
    ;

case_item_list
    : pattern where_clause?
    | pattern where_clause? ',' case_item_list
    ;

default_label
    : attributes? 'default' ':'
    ;

where_clause
    : 'where' where_expression
    ;

where_expression
    : expression__condition
    ;

// GRAMMAR OF A LABELED STATEMENT

labeled_statement
    : statement_label loop_statement
    | statement_label if_statement
    | statement_label switch_statement
    | statement_label do_statement
    ;

statement_label
    : label_name ':'
    ;

label_name
    : declaration_identifier
    ;

// GRAMMAR OF A CONTROL TRANSFER STATEMENT

control_transfer_statement
    : break_statement
    | continue_statement
    | fallthrough_statement
    | return_statement
    | throw_statement
    ;

// GRAMMAR OF A BREAK STATEMENT

break_statement
    : 'break' label_name?
    ;

// GRAMMAR OF A CONTINUE STATEMENT

continue_statement
    : 'continue' label_name?
    ;

// GRAMMAR OF A FALLTHROUGH STATEMENT

fallthrough_statement
    : 'fallthrough'
    ;

// GRAMMAR OF A RETURN STATEMENT

return_statement
    : 'return' expression?
    ;

// GRAMMAR OF A THROW STATEMENT
throw_statement
    : 'throw' expression
    ;

// GRAMMAR OF A DEFER STATEMENT

defer_statement
    : 'defer' code_block
    ;

// GRAMMAR OF A DO STATEMENT

do_statement
    : 'do' code_block catch_clauses?
    ;

catch_clauses
    : catch_clause catch_clauses?
    ;

catch_clause
    : 'catch' pattern? where_clause? code_block
    ;

// GRAMMAR OF A COMPILER CONTROL STATEMENT

compiler_control_statement
    : conditional_compilation_block
    | line_control_statement
    ;

// GRAMMAR OF A CONDITIONAL COMPILATION BLOCK

conditional_compilation_block
    : if_directive_clause elseif_directive_clauses? else_directive_clause? endif_directive
    ;

if_directive_clause
    : if_directive compilation_condition (statements? | directive_members)
    ;

// the cases of an enumeration or of a switch that a compilation condition guards
directive_members
    : (union_style_enum_case_clause | raw_value_style_enum_case_clause) (
        union_style_enum_case_clause
        | raw_value_style_enum_case_clause
        | declaration
    )*
    | switch_case+
    ;

elseif_directive_clauses
    : (elseif_directive_clause)+
    ;

elseif_directive_clause
    : elseif_directive compilation_condition (statements? | directive_members)
    ;

else_directive_clause
    : else_directive (statements? | directive_members)
    ;

if_directive
    : '#if'
    ;

elseif_directive
    : '#elseif'
    ;

else_directive
    : '#else'
    ;

endif_directive
    : '#endif'
    ;

compilation_condition
    : platform_condition
    | label_identifier
    | boolean_literal
    | '(' compilation_condition ')'
    | '!' compilation_condition
    | compilation_condition compilation_condition_AND compilation_condition
    | compilation_condition compilation_condition_OR compilation_condition
    ;

platform_condition
    : 'os' '(' operating_system ')'
    | 'arch' '(' architecture ')'
    | 'swift' '(' compilation_condition_GE swift_version ')'
    // canImport(Module), targetEnvironment(simulator), compiler(>=5.9), hasFeature(Name)
    | Identifier '(' balanced_tokens ')'
    ;

swift_version
    : Pure_decimal_digits '.' Pure_decimal_digits
    ;

// Rule from docs:
// operating-system → macOS­ | iOS­ | watchOS­ | tvOS
//
// Code from apple/swift:­ 
// #if (!os(Windows) || CYGWIN) && (arch(i386) || arch(x86_64))
//
// "#if os(Any)" gives error that Any is not an identifier.
// So I decided to use declaration_identifier

operating_system
    : declaration_identifier
    ;

architecture
    : declaration_identifier
    ;

// These rules don't work:
// operating_system : 'macOS' | 'iOS' | 'watchOS' | 'tvOS' ;

// GRAMMAR OF A LINE CONTROL STATEMENT

// NOTE: The rule is changed.
// Original rule:
//
// line_control_statement
//  : '#sourceLocation' '(' 'file:' file_name ',' 'line:' line_number ')'
//  | '#sourceLocation' '(' ')'
//  ;
// architecture : 'i386' | 'x86_64' | 'arm' | 'arm64' ;
// 
// This defines a token "file:", but this is valid for swift compiler:
// '#sourceLocation(file : "", line : 1)' (notice spaces between "file" and ":")
//
// Modified rule:
line_control_statement
    : '#sourceLocation' '(' 'file' ':' file_name ',' 'line' ':' line_number ')'
    | '#sourceLocation' '(' ')'
    ;

line_number
    : integer_literal
    ; // TODO: A decimal integer greater than zero

file_name
    : Static_string_literal
    ;

// GRAMMAR OF AN AVAILABILITY CONDITION

availability_condition
    : '#available' '(' availability_arguments ')'
    ;

availability_arguments
    : availability_argument (',' availability_argument)*
    ;

availability_argument
    : Platform_name_platform_version
    | '*'
    ;

generic_parameter_clause
    : '<' generic_parameter_list '>'
    ;

generic_parameter_list
    : generic_parameter (',' generic_parameter)*
    ;

generic_parameter
    : type_name
    | contextual_keyword type_name
    | type_name ':' type_identifier
    | type_name ':' '~' type_identifier
    | type_name ':' protocol_composition_type
    ;

generic_where_clause
    : 'where' requirement_list
    ;

requirement_list
    : requirement (',' requirement)*
    ;

requirement
    : conformance_requirement
    | same_type_requirement
    ;

// the composition comes first: `where T: A & B {` is never `where T: A` before a statement `& B`
conformance_requirement
    : type_identifier ':' protocol_composition_type
    | type_identifier ':' type_identifier
    | self_requirement_type ':' protocol_composition_type
    | self_requirement_type ':' type_identifier
    ;

same_type_requirement
    : type_identifier same_type_equals type_
    | self_requirement_type same_type_equals type_
    ;

// `where Self == Style` and `where Self.Element: Equatable`
self_requirement_type
    : 'Self' ('.' type_identifier)?
    ;

// GRAMMAR OF A GENERIC ARGUMENT CLAUSE

generic_argument_clause
    : '<' generic_argument_list '>'
    ;

generic_argument_list
    : generic_argument (',' generic_argument)*
    ;

generic_argument
    : type_
    ;

// context-sensitive. Allow < as pre, post, or binary op
//lt : {_input.LT(1).getText().equals("<")}? operator_ ;

// GRAMMAR OF A DECLARATION

//gt : {_input.LT(1).getText().equals(">")}? operator_ ;
// Declarations
declaration
    : import_declaration
    | constant_declaration
    | variable_declaration
    | typealias_declaration
    | function_declaration
    | enum_declaration
    | struct_declaration
    | class_declaration
    | macro_declaration
    | protocol_declaration
    | initializer_declaration
    | deinitializer_declaration
    | extension_declaration
    | subscript_declaration
    | operator_declaration
    | operator_declaration
    | precedence_group_declaration
    ;

declarations
    : declaration+
    ;

// GRAMMAR OF A TOP-LEVEL DECLARATION

top_level_declaration
    : statements?
    ;

// GRAMMAR OF A CODE BLOCK

code_block
    : '{' statements? '}'
    ;

// GRAMMAR OF AN IMPORT DECLARATION

import_declaration
    : attributes? 'import' import_kind? import_path
    ;

import_kind
    : 'typealias'
    | 'struct'
    | 'class'
    | 'enum'
    | 'protocol'
    | 'var'
    | 'func'
    ;

import_path
    : import_path_identifier ('.' import_path_identifier)*
    ;

import_path_identifier
    : declaration_identifier
    | operator_
    ;

// GRAMMAR OF A CONSTANT DECLARATION

constant_declaration
    : attributes? declaration_modifiers? 'async'? 'let' pattern_initializer_list
    ;

pattern_initializer_list
    : pattern_initializer (',' pattern_initializer)*
    ;

/** rule is ambiguous. can match "var x = 1" with x as pattern
 *  OR with x as expression_pattern.
 *  ANTLR resolves in favor or first choice: pattern is x, 1 is initializer.
 */
pattern_initializer
    : pattern__binding initializer?
    ;

initializer
    : assignment_operator expression
    ;

// GRAMMAR OF A VARIABLE DECLARATION

variable_declaration
// the accessor blocks come before the code block: `{ get { 1 } set { } }` also reads as two statements
    : variable_declaration_head variable_name type_annotation getter_setter_block
    | variable_declaration_head variable_name type_annotation getter_setter_keyword_block
    | variable_declaration_head variable_name type_annotation code_block
    | variable_declaration_head variable_name type_annotation initializer? willSet_didSet_block
    | variable_declaration_head variable_name type_annotation type_annotation initializer? willSet_didSet_block
    | variable_declaration_head pattern_initializer_list
    ;

variable_declaration_head
    : attributes? declaration_modifiers? 'var'
    ;

variable_name
    : declaration_identifier
    ;

getter_setter_block
    : '{' getter_clause setter_clause? '}'
    | '{' setter_clause getter_clause '}'
    ;

getter_clause
    : attributes? mutation_modifier? 'get' 'async'? throws_clause? code_block
    ;

setter_clause
    : attributes? mutation_modifier? 'set' setter_name? code_block
    ;

setter_name
    : '(' declaration_identifier ')'
    ;

getter_setter_keyword_block
    : '{' getter_keyword_clause setter_keyword_clause? '}'
    | '{' setter_keyword_clause getter_keyword_clause '}'
    ;

getter_keyword_clause
    : attributes? mutation_modifier? 'get' 'async'? throws_clause?
    ;

setter_keyword_clause
    : attributes? mutation_modifier? 'set'
    ;

willSet_didSet_block
    : '{' willSet_clause didSet_clause? '}'
    | '{' didSet_clause willSet_clause '}'
    ;

willSet_clause
    : attributes? 'willSet' setter_name? code_block
    ;

didSet_clause
    : attributes? 'didSet' setter_name? code_block
    ;

// GRAMMAR OF A TYPE ALIAS DECLARATION

typealias_declaration
    : attributes? access_level_modifier? 'typealias' typealias_name generic_parameter_clause? typealias_assignment
    ;

typealias_name
    : declaration_identifier
    ;

typealias_assignment
    : assignment_operator type_
    ;

// GRAMMAR OF A FUNCTION DECLARATION
function_declaration
    : function_head function_name generic_parameter_clause? function_signature generic_where_clause? function_body?
    ;

function_head
    : attributes? declaration_modifiers? 'func'
    ;

function_name
    : declaration_identifier
    | operator_
    ;

function_signature
    : parameter_clause 'async'? throws_clause? function_result?
    | parameter_clause 'async'? 'rethrows' function_result?
    ;

function_result
    : arrow_operator attributes? type_
    ;

function_body
    : code_block
    ;

parameter_clause
    : '(' ')'
    | '(' parameter_list ')'
    ;

parameter_list
    : parameter (',' parameter)*
    ;

parameter
    : attributes? external_parameter_name? local_parameter_name type_annotation (default_argument_clause | range_operator)?
    ;

external_parameter_name
    : label_identifier
    ; // TODO: Check that deleting " | '_'" doesn't break anything

local_parameter_name
    : label_identifier
    ; // TODO: Check that deleting " | '_'" doesn't break anything

default_argument_clause
    : assignment_operator expression
    ;

// GRAMMAR OF AN ENUMERATION DECLARATION

enum_declaration
    : attributes? access_level_modifier? union_style_enum
    | attributes? access_level_modifier? raw_value_style_enum
    ;

union_style_enum
    : 'indirect'? 'enum' enum_name generic_parameter_clause? type_inheritance_clause? generic_where_clause? '{' union_style_enum_members? '}'
    ;

union_style_enum_members
    : union_style_enum_member union_style_enum_members?
    ;

union_style_enum_member
    : declaration
    | union_style_enum_case_clause
    | compiler_control_statement
    ;

union_style_enum_case_clause
    : attributes? 'indirect'? 'case' union_style_enum_case_list
    ;

union_style_enum_case_list
    : union_style_enum_case
    | union_style_enum_case ',' union_style_enum_case_list
    ;

union_style_enum_case
    : enum_case_name tuple_type?
    ;

enum_name
    : declaration_identifier
    ;

enum_case_name
    : declaration_identifier
    ;

raw_value_style_enum
    : 'enum' enum_name generic_parameter_clause? type_inheritance_clause generic_where_clause? '{' raw_value_style_enum_members '}'
    ;

raw_value_style_enum_members
    : raw_value_style_enum_member raw_value_style_enum_members?
    ;

raw_value_style_enum_member
    : declaration
    | raw_value_style_enum_case_clause
    | compiler_control_statement
    ;

raw_value_style_enum_case_clause
    : attributes? 'case' raw_value_style_enum_case_list
    ;

raw_value_style_enum_case_list
    : raw_value_style_enum_case
    | raw_value_style_enum_case ',' raw_value_style_enum_case_list
    ;

raw_value_style_enum_case
    : enum_case_name raw_value_assignment?
    ;

raw_value_assignment
    : assignment_operator raw_value_literal
    ;

raw_value_literal
    : numeric_literal
    | Static_string_literal
    | boolean_literal
    ;

// GRAMMAR OF A STRUCTURE DECLARATION TODO did not update

struct_declaration
    : attributes? access_level_modifier? 'struct' struct_name generic_parameter_clause? type_inheritance_clause? generic_where_clause? struct_body
    ;

struct_name
    : declaration_identifier
    ;

struct_body
    : '{' struct_member* '}'
    ;

struct_member
    : declaration ';'?
    | compiler_control_statement
    ;

// GRAMMAR OF A MACRO DECLARATION

macro_declaration
    : attributes? access_level_modifier? contextual_macro declaration_identifier generic_parameter_clause? function_signature? generic_where_clause? ('=' expression)?
    ;

contextual_macro
    : {_input.LT(1).getText().equals("macro")}? Contextual_keyword
    ;

// GRAMMAR OF A CLASS DECLARATION

class_declaration
    : attributes? access_level_modifier? ('final' access_level_modifier?)? ('class' | 'actor') class_name generic_parameter_clause?
        type_inheritance_clause? generic_where_clause? class_body
    ;

class_name
    : declaration_identifier
    ;

class_body
    : '{' class_member* '}'
    ;

class_member
    : declaration ';'?
    | compiler_control_statement
    ;

// GRAMMAR OF A PROTOCOL DECLARATION

protocol_declaration
    : attributes? access_level_modifier? 'protocol' protocol_name generic_parameter_clause? type_inheritance_clause? protocol_body
    ;

protocol_name
    : declaration_identifier
    ;

protocol_body
    : '{' protocol_member* '}'
    ;

protocol_member
    : protocol_member_declaration
    | compiler_control_statement
    ;

protocol_member_declaration
    : protocol_property_declaration
    | protocol_method_declaration
    | protocol_initializer_declaration
    | protocol_subscript_declaration
    | protocol_associated_type_declaration
    | typealias_declaration
    ;

// GRAMMAR OF A PROTOCOL PROPERTY DECLARATION

protocol_property_declaration
    : variable_declaration_head variable_name type_annotation getter_setter_keyword_block
    ;

// GRAMMAR OF A PROTOCOL METHOD DECLARATION

protocol_method_declaration
    : function_head function_name generic_parameter_clause? function_signature generic_where_clause?
    ;

// GRAMMAR OF A PROTOCOL INITIALIZER DECLARATION

protocol_initializer_declaration
    : initializer_head generic_parameter_clause? parameter_clause throws_clause? generic_where_clause?
    | initializer_head generic_parameter_clause? parameter_clause 'rethrows' generic_where_clause?
    ;

// GRAMMAR OF A PROTOCOL SUBSCRIPT DECLARATION

protocol_subscript_declaration
    : subscript_head subscript_result getter_setter_keyword_block
    ;

// GRAMMAR OF A PROTOCOL ASSOCIATED TYPE DECLARATION

protocol_associated_type_declaration
    : attributes? access_level_modifier? 'associatedtype' typealias_name type_inheritance_clause? typealias_assignment?
    ;

// GRAMMAR OF AN INITIALIZER DECLARATION

initializer_declaration
    : initializer_head generic_parameter_clause? parameter_clause throws_clause? generic_where_clause? initializer_body
    | initializer_head generic_parameter_clause? parameter_clause 'rethrows' generic_where_clause? initializer_body
    ;

initializer_head
    : attributes? declaration_modifiers? 'init'
    | attributes? declaration_modifiers? 'init' '?'
    | attributes? declaration_modifiers? 'init' '!'
    ;

initializer_body
    : code_block
    ;

// GRAMMAR OF A DEINITIALIZER DECLARATION

deinitializer_declaration
    : attributes? 'deinit' code_block
    ;

// GRAMMAR OF AN EXTENSION DECLARATION

extension_declaration
    : attributes? access_level_modifier? 'extension' (type_identifier | array_type | dictionary_type) type_inheritance_clause? generic_where_clause?
        extension_body
    ;

extension_body
    : '{' extension_member* '}'
    ;

extension_member
    : declaration ';'?
    | compiler_control_statement
    ;

// GRAMMAR OF A SUBSCRIPT DECLARATION

subscript_declaration
    : subscript_head subscript_result generic_where_clause? getter_setter_block
    | subscript_head subscript_result generic_where_clause? getter_setter_keyword_block
    | subscript_head subscript_result generic_where_clause? code_block
    ;

subscript_head
    : attributes? declaration_modifiers? 'subscript' generic_parameter_clause? parameter_clause
    ;

subscript_result
    : arrow_operator attributes? type_
    ;

// GRAMMAR OF AN OPERATOR DECLARATION

operator_declaration
    : prefix_operator_declaration
    | postfix_operator_declaration
    | infix_operator_declaration
    ;

prefix_operator_declaration
    : 'prefix' 'operator' operator_
    ;

postfix_operator_declaration
    : 'postfix' 'operator' operator_
    ;

infix_operator_declaration
    : 'infix' 'operator' operator_ infix_operator_group?
    ;

infix_operator_group
    : ':' precedence_group_name
    ;

// GRAMMAR OF A PRECEDENCE GROUP DECLARATION

precedence_group_declaration
    : 'precedencegroup' precedence_group_name '{' precedence_group_attribute* '}'
    ;

precedence_group_attribute
    : precedence_group_relation
    | precedence_group_assignment
    | precedence_group_associativity
    ;

precedence_group_relation
    : 'higherThan' ':' precedence_group_names
    | 'lowerThan' ':' precedence_group_names
    ;

precedence_group_assignment
    : 'assignment' ':' boolean_literal
    ;

precedence_group_associativity
    : 'associativity' ':' associativity_
    ;

associativity_
    : 'left'
    | 'right'
    | 'none'
    ;

precedence_group_names
    : precedence_group_name (',' precedence_group_name)*
    ;

precedence_group_name
    : declaration_identifier
    ;

// GRAMMAR OF A DECLARATION MODIFIER

nonisolated_modifier
    : {_input.LT(1).getText().equals("nonisolated")}? Contextual_keyword ('(' 'unsafe' ')')?
    ;
declaration_modifier
    : nonisolated_modifier
    | 'class'
    | 'convenience'
    | 'dynamic'
    | 'final'
    | 'infix'
    | 'lazy'
    | 'optional'
    | 'override'
    | 'postfix'
    | 'prefix'
    | 'required'
    | 'static'
    | 'unowned'
    | 'unowned' '(' 'safe' ')'
    | 'unowned' '(' 'unsafe' ')'
    | 'weak'
    | access_level_modifier
    | mutation_modifier
    ;

declaration_modifiers
    : declaration_modifier+
    ;

access_level_modifier
    : 'private'
    | 'private' '(' 'set' ')'
    | 'fileprivate'
    | 'fileprivate' '(' 'set' ')'
    | 'internal'
    | 'internal' '(' 'set' ')'
    | 'public'
    | 'public' '(' 'set' ')'
    | 'open'
    | 'open' '(' 'set' ')'
    | {_input.LT(1).getText().equals("package")}? Contextual_keyword
    ;

mutation_modifier
    : 'mutating'
    | 'nonmutating'
    ;

// Patterns

// GRAMMAR OF A PATTERN

pattern
    : wildcard_pattern type_annotation?
    | identifier_pattern type_annotation?
    | value_binding_pattern
    | tuple_pattern type_annotation?
    | enum_case_pattern
    | optional_pattern
    | 'is' type_
    | pattern 'as' type_
    | {SwiftPatternLookahead.isExpressionPattern(_input)}? expression_pattern
    ;

// The pattern of a declaration, of `if let` and of `for ... in` binds names: it is `_`, a name or a
// tuple of them, never an expression or an enumeration case. Without this variant the parser read
// `var body: some View { ... }` to the end of the block as `case body:` and a statement (#567).
pattern__binding
    : wildcard_pattern type_annotation?
    | identifier_pattern type_annotation?
    | tuple_pattern__binding type_annotation?
    | 'self' // `guard let self else { return }`
    ;

tuple_pattern__binding
    : '(' tuple_pattern_element_list__binding? ')'
    ;

tuple_pattern_element_list__binding
    : tuple_pattern_element__binding (',' tuple_pattern_element__binding)*
    ;

tuple_pattern_element__binding
    : pattern__binding
    ;

// GRAMMAR OF A WILDCARD PATTERN

wildcard_pattern
    : '_'
    ;

// GRAMMAR OF AN IDENTIFIER PATTERN

identifier_pattern
    : declaration_identifier
    ;

// GRAMMAR OF A VALUE_BINDING PATTERN

value_binding_pattern
    : 'var' pattern
    | 'let' pattern
    ;

// GRAMMAR OF A TUPLE PATTERN

tuple_pattern
    : '(' tuple_pattern_element_list? ')'
    ;

tuple_pattern_element_list
    : tuple_pattern_element (',' tuple_pattern_element)*
    ;

tuple_pattern_element
    : pattern
    ;

// GRAMMAR OF AN ENUMERATION CASE PATTERN

enum_case_pattern
    : type_identifier? '.' enum_case_name tuple_pattern?
    ;

// GRAMMAR OF AN OPTIONAL PATTERN
optional_pattern
    : identifier_pattern '?'
    ;

// GRAMMAR OF A TYPE CASTING PATTERN

// integrated directly into pattern to avoid indirect left-recursion

// GRAMMAR OF AN EXPRESSION PATTERN

/** Doc says "Expression patterns appear only in switch statement case labels." */
expression_pattern
    : expression__condition
    ;

// Attributes

// GRAMMAR OF AN ATTRIBUTE

attribute
    : '@' attribute_name attribute_argument_clause?
    ;

attribute_name
    : declaration_identifier
    ;

attribute_argument_clause
    : '(' balanced_tokens ')'
    ;

attributes
    : attribute+
    ;

balanced_tokens
    : balanced_token*
    ;

// https://developer.apple.com/library/content/documentation/Swift/Conceptual/Swift_Programming_Language/Attributes.html#//apple_ref/swift/grammar/attributes
//
// Quote:
// balanced-token → (­balanced-tokens­opt­)­
// balanced-token → [­balanced-tokens­opt­]­
// balanced-token → {­balanced-tokens­opt­}­
// balanced-token → Any identifier, keyword, literal, or operator
// balanced-token → Any punctuation except (­, )­, [­, ]­, {­, or }­
//
// Example: @available(*, deprecated, message: "it will be removed in Swift 4.0.  Please use 'Collection' instead")
// Apple doesn't provide proper grammar for attributes. It says "Any punctuation except (­, )­, [­, ]­, {­, or }­".
balanced_token
    : '(' balanced_tokens ')'
    | '[' balanced_tokens ']'
    | '{' balanced_tokens '}'
    | label_identifier
    | literal
    | operator_
    | Platform_name_platform_version // there is a kludge, see Platform_name_platform_version; it is a token
    | any_punctuation_for_balanced_token
    ;

// https://developer.apple.com/library/content/documentation/Swift/Conceptual/Swift_Programming_Language/LexicalStructure.html#//apple_ref/swift/grammar/identifier
// Quote:
// The following tokens are reserved as punctuation and can’t be used as custom operators: (, ), {, }, [, ], ., ,, :, ;, =, @, #, & (as a prefix operator), ->, `, ?, and ! (as a postfix operator).
any_punctuation_for_balanced_token
    : ('.' | ',' | ':' | ';' | '=' | '@' | '#' | '`' | '?' | '\\')
    | arrow_operator
    | {SwiftSupport.isPrefixOp(_input)}? '&'
    | {SwiftSupport.isPostfixOp(_input)}? '!'
    ;

// Expressions

// GRAMMAR OF AN EXPRESSION
expression
    : try_operator? prefix_expression binary_expressions?
    ;

expression_list
    : expression (',' expression)*
    ;

// GRAMMAR OF A PREFIX EXPRESSION

prefix_expression
    : contextual_operator postfix_expression
    | prefix_operator postfix_expression
    | 'await' postfix_expression
    | postfix_expression
    | in_out_expression
    | 'repeat' prefix_expression
    ;

// `consume x` and `each t` are prefix operators only before an identifier
contextual_operator
    : {SwiftContextualKeywords.isContextualOperator(_input)}? Contextual_keyword
    ;

in_out_expression
    : '&' declaration_identifier
    ;

// GRAMMAR OF A TRY EXPRESSION

try_operator
    : 'try' {SwiftLookahead.isAttached(_input)}? '?'
    | 'try' {SwiftLookahead.isAttached(_input)}? '!'
    | {SwiftLookahead.isPlainTry(_input)}? 'try'
    ;

// GRAMMAR OF A BINARY EXPRESSION

binary_expression
    : binary_operator prefix_expression
    // without `try` the first alternative reads the assignment
    | assignment_operator try_operator prefix_expression
    | conditional_operator try_operator? prefix_expression
    | type_casting_operator
    ;

binary_expressions
    : binary_expression+
    ;

// GRAMMAR OF A CONDITIONAL OPERATOR

conditional_operator
    : '?' try_operator? expression ':'
    ;

// GRAMMAR OF A TYPE_CASTING OPERATOR

type_casting_operator
    : 'is' type_
    | 'as' type_
    // `as ? Type` is not a cast: the mark is written right after `as`
    | 'as' {SwiftLookahead.isAttached(_input)}? '?' type_
    | 'as' {SwiftLookahead.isAttached(_input)}? '!' type_
    ;

// GRAMMAR OF A PRIMARY EXPRESSION

primary_expression
    : declaration_identifier generic_argument_clause?
    | 'Any'
    | literal_expression
    | self_expression
    | superclass_expression
    | closure_expression
    | parenthesized_expression
    | tuple_expression
    | implicit_member_expression
    | wildcard_expression
    | selector_expression
    | key_path_expression
    | key_path_literal
    | '#' declaration_identifier generic_argument_clause?
    | if_statement
    | switch_statement
    ;

// GRAMMAR OF A LITERAL EXPRESSION

literal_expression
    : literal
    | array_literal
    | dictionary_literal
    | '#file'
    | '#fileID'
    | '#filePath'
    | '#line'
    | '#column'
    | '#function'
    | '#dsohandle' // Private Apple stuff. Not in docs, but in compiler and in sources of swift.
    ;

array_literal
    : '[' array_literal_items? ']'
    ;

array_literal_items
    : array_literal_item ','?
    | array_literal_item ',' array_literal_items
    ;

array_literal_item
    : expression
    ;

dictionary_literal
    : '[' dictionary_literal_items ']'
    | '[' ':' ']'
    ;

dictionary_literal_items
    : dictionary_literal_item ','?
    | dictionary_literal_item ',' dictionary_literal_items
    ;

dictionary_literal_item
    : expression ':' expression
    ;

playground_literal
    : '#colorLiteral' '(' 'red' ':' expression ',' 'green' ':' expression ',' 'blue' ':' expression ',' 'alpha' ':' expression ')'
    | '#fileLiteral' '(' 'resourceName' ':' expression ')'
    | '#imageLiteral' '(' 'resourceName' ':' expression ')'
    ;

// GRAMMAR OF A SELF EXPRESSION

// `self.name`, `self[index]` and `Self.name` are members and subscripts of these, read by
// postfix_expression: alternatives for them here were ambiguous with it and never chosen.
self_expression
    : 'self'
    | 'Self'
    ;

// GRAMMAR OF A SUPERCLASS EXPRESSION

superclass_expression
    : superclass_method_expression
    | superclass_subscript_expression
    | superclass_initializer_expression
    ;

superclass_method_expression
    : 'super' '.' declaration_identifier
    ;

superclass_subscript_expression
    : 'super' '[' expression ']'
    ;

superclass_initializer_expression
    : 'super' '.' 'init'
    ;

// GRAMMAR OF A CLOSURE EXPRESSION

closure_expression
    : '{' closure_signature? statements? '}'
    ;

closure_signature
    : attributes? capture_list? closure_parameter_clause 'async'? throws_clause? function_result? 'in'
    | attributes? capture_list 'in'
    | attributes 'in'
    ;

closure_parameter_clause
    : '(' ')'
    | '(' closure_parameter_list ')'
    | closure_parameter_clause_identifier_list
    ;

// Renamed rule "identifier_list"
closure_parameter_clause_identifier_list
    : (declaration_identifier | '_') (',' (declaration_identifier | '_'))*
    ;

closure_parameter_list
    : closure_parameter (',' closure_parameter)*
    ;

closure_parameter
    : closure_parameter_name type_annotation?
    | closure_parameter_name type_annotation range_operator
    ;

closure_parameter_name
    : label_identifier
    ;

capture_list
    : '[' capture_list_items ']'
    ;

capture_list_items
    : capture_list_item (',' capture_list_item)*
    ;

capture_list_item
    : capture_specifier? expression
    ;

capture_specifier
    : 'weak'
    | 'unowned'
    | 'unowned(safe)'
    | 'unowned(unsafe)'
    ;

// GRAMMAR OF A IMPLICIT MEMBER EXPRESSION

implicit_member_expression
    : '.' label_identifier
    ; // let a: MyType = .default; static let `default` = MyType()

// GRAMMAR OF A PARENTHESIZED EXPRESSION

parenthesized_expression
    : '(' expression ')'
    ;

// GRAMMAR OF A TUPLE EXPRESSION

tuple_expression
    : '(' ')'
    | '(' tuple_element (',' tuple_element)+ ')'
    ;

tuple_element
    : expression
    | label_identifier ':' expression
    ;

// GRAMMAR OF A WILDCARD EXPRESSION

wildcard_expression
    : '_'
    ;

// GRAMMAR OF A SELECTOR EXPRESSION

selector_expression
    : '#selector' '(' expression ')'
    | '#selector' '(' 'getter:' expression ')'
    | '#selector' '(' 'setter:' expression ')'
    ;

// GRAMMAR OF A KEY-PATH EXPRESSION

key_path_expression
    : '#keyPath' '(' expression ')'
    ;

// Swift 5.0 key path literal, for example \Stack<Int>.items
key_path_literal
    : '\\' type_identifier? key_path_component+
    ;

// \.self, \.first?.name and \.items[0]
key_path_component
    : '.' (declaration_identifier | 'self') key_path_postfix*
    ;

key_path_postfix
    : {SwiftSupport.isPostfixOp(_input)}? ('?' | '!')
    | {SwiftLookahead.isOnSameLine(_input)}? '[' expression_list ']'
    ;

// GRAMMAR OF A POSTFIX EXPRESSION (inlined many rules from spec to avoid indirect left-recursion)

postfix_expression
    : primary_expression                                                     # primary
    | postfix_expression postfix_operator                                    # postfix_operation
    | postfix_expression {SwiftLookahead.isOnSameLine(_input)}? function_call_argument_clause            # function_call_expression
    | postfix_expression trailing_closure             # function_call_expression_with_closure
    | postfix_expression '.' 'init'                                          # initializer_expression
    | postfix_expression '.' 'init' '(' argument_names ')'                   # initializer_expression_with_args
    | postfix_expression '.' (Pure_decimal_digits | Decimal_literal)         # explicit_member_expression1
    | postfix_expression '.' declaration_identifier generic_argument_clause? # explicit_member_expression2
    | postfix_expression '.' declaration_identifier '(' argument_names ')'   # explicit_member_expression3
    // This does't exist in the swift grammar, but this valid swift statement fails without it
    // self.addTarget(self, action: #selector(nameOfAction(_:)))
    | postfix_expression {SwiftLookahead.isOnSameLine(_input)}? '(' argument_names ')'  # explicit_member_expression4
    | postfix_expression '.' 'self'              # postfix_self_expression
    | postfix_expression '.' keyword_member_name # explicit_member_expression_keyword
    | postfix_expression {SwiftLookahead.isPostfixDirective(_input)}? postfix_directive # postfix_directive_expression
    | dynamic_type_expression                    # dynamic_type
    | postfix_expression {SwiftLookahead.isOnSameLine(_input)}? '[' expression_list ']' # subscript_expression
    | postfix_expression {SwiftPatternLookahead.isLabeledSubscript(_input)}? '[' function_call_argument_list ']' # labeled_subscript_expression
    | postfix_expression {SwiftLookahead.isOnSameLine(_input)}? '[' operator_ ']' # operator_subscript_expression
    // ! is a postfix operator already
    // | postfix_expression '!'                                            # forced_value_expression
    // ? is a postfix operator already
    // | postfix_expression '?'                                            # optional_chaining_expression
    ;

// VARIANTS OF THE EXPRESSION RULES
//
// A rule named `x__variant` reads a part of what `x` reads, and its node in the syntax tree has the
// kind `x`. Two variants take out the readings that made the parser read on to the end of a
// block before it could choose (#567):
//  - `__condition`: an expression in the condition of `if`, `guard`, `while`, `switch`, `for` and
//    `catch` takes no trailing closure, so the brace after it opens the body of the statement.

//  - `__next`: a statement after the first of its block does not start with a closure, a dot,
//    `&` or `=`. The brace after `var name: Type` or after a call is then never a new statement, a line
//    that starts with a dot continues the expression of the line above, and `=` continues a
//    declaration.

statements_impl__next
    : {SwiftLookahead.isStatementStart(_input)}? statement__next statements_impl__next?
    ;

statement__next
    : declaration ';'?
    | loop_statement ';'?
    | branch_statement ';'?
    | labeled_statement ';'?
    | control_transfer_statement ';'?
    | defer_statement ';'?
    | do_statement ';'?
    | {!SwiftLookahead.isPostfixDirective(_input)}? compiler_control_statement ';'?
    | {SwiftLookahead.isExpressionStatement(_input)}? expression__next ';'?
    // proper logic with semicolons is not supported yet. compiler_control_statement should be separated with a newline, but not with a semicolon
    ;

expression__next
    : try_operator? prefix_expression__next binary_expressions?
    ;

prefix_expression__next
    : contextual_operator postfix_expression
    | prefix_operator__next postfix_expression
    | 'await' postfix_expression
    | postfix_expression__next
    | 'repeat' prefix_expression
    ;

prefix_operator__next
    : {SwiftSupport.isPrefixOp(_input)}? operator_head__next ({_input.get(_input.index()-1).getType()!=WS}? operator_character)*
    ;

operator_head__next
    : ('/' | '-' | '+' | '!' | '*' | '%' | '|' | '<' | '>' | '^' | '~' | '?')
    | Operator_head_other
    ;

postfix_expression__next
    : primary_expression__next                                                     # primary_next
    | postfix_expression__next postfix_operator                                    # postfix_operation_next
    | postfix_expression__next {SwiftLookahead.isOnSameLine(_input)}? function_call_argument_clause            # function_call_expression_next
    | postfix_expression__next trailing_closure             # function_call_expression_with_closure_next
    | postfix_expression__next '.' 'init'                                          # initializer_expression_next
    | postfix_expression__next '.' 'init' '(' argument_names ')'                   # initializer_expression_with_args_next
    | postfix_expression__next '.' (Pure_decimal_digits | Decimal_literal)         # explicit_member_expression1_next
    | postfix_expression__next '.' declaration_identifier generic_argument_clause? # explicit_member_expression2_next
    | postfix_expression__next '.' declaration_identifier '(' argument_names ')'   # explicit_member_expression3_next
    // This does't exist in the swift grammar, but this valid swift statement fails without it
    // self.addTarget(self, action: #selector(nameOfAction(_:)))
    | postfix_expression__next {SwiftLookahead.isOnSameLine(_input)}? '(' argument_names ')'  # explicit_member_expression4_next
    | postfix_expression__next '.' 'self'              # postfix_self_expression_next
    | postfix_expression__next '.' keyword_member_name # explicit_member_expression_keyword_next
    | postfix_expression__next {SwiftLookahead.isPostfixDirective(_input)}? postfix_directive # postfix_directive_expression_next
    | dynamic_type_expression                    # dynamic_type_next
    | postfix_expression__next {SwiftLookahead.isOnSameLine(_input)}? '[' expression_list ']' # subscript_expression_next
    | postfix_expression__next {SwiftPatternLookahead.isLabeledSubscript(_input)}? '[' function_call_argument_list ']' # labeled_subscript_expression_next
    | postfix_expression__next {SwiftLookahead.isOnSameLine(_input)}? '[' operator_ ']' # operator_subscript_expression_next
    // ! is a postfix operator already
    // | postfix_expression__next '!'                                            # forced_value_expression_next
    // ? is a postfix operator already
    // | postfix_expression__next '?'                                            # optional_chaining_expression_next
    ;

primary_expression__next
    : declaration_identifier generic_argument_clause?
    | 'Any'
    | literal_expression
    | self_expression
    | superclass_expression
    | parenthesized_expression
    | tuple_expression
    | wildcard_expression
    | selector_expression
    | key_path_expression
    | key_path_literal
    | '#' declaration_identifier generic_argument_clause?
    | if_statement
    | switch_statement
    ;

expression__condition
    : try_operator? prefix_expression__condition binary_expressions__condition?
    ;

expression_list__condition
    : expression__condition (',' expression__condition)*
    ;

binary_expressions__condition
    : binary_expression__condition+
    ;

binary_expression__condition
    : binary_operator prefix_expression__condition
    | assignment_operator try_operator prefix_expression__condition
    | conditional_operator try_operator? prefix_expression__condition
    | type_casting_operator
    ;

prefix_expression__condition
    : contextual_operator postfix_expression__condition
    | prefix_operator postfix_expression__condition
    | 'await' postfix_expression__condition
    | postfix_expression__condition
    | in_out_expression
    | 'repeat' prefix_expression__condition
    ;

initializer__condition
    : assignment_operator expression__condition
    ;

postfix_expression__condition
    : primary_expression                                                     # primary_condition
    | postfix_expression__condition postfix_operator                                    # postfix_operation_condition
    | postfix_expression__condition {SwiftLookahead.isOnSameLine(_input)}? function_call_argument_clause            # function_call_expression_condition
    | postfix_expression__condition '.' 'init'                                          # initializer_expression_condition
    | postfix_expression__condition '.' 'init' '(' argument_names ')'                   # initializer_expression_with_args_condition
    | postfix_expression__condition '.' (Pure_decimal_digits | Decimal_literal)         # explicit_member_expression1_condition
    | postfix_expression__condition '.' declaration_identifier generic_argument_clause? # explicit_member_expression2_condition
    | postfix_expression__condition '.' declaration_identifier '(' argument_names ')'   # explicit_member_expression3_condition
    // This does't exist in the swift grammar, but this valid swift statement fails without it
    // self.addTarget(self, action: #selector(nameOfAction(_:)))
    | postfix_expression__condition {SwiftLookahead.isOnSameLine(_input)}? '(' argument_names ')'  # explicit_member_expression4_condition
    | postfix_expression__condition '.' 'self'              # postfix_self_expression_condition
    | postfix_expression__condition '.' keyword_member_name # explicit_member_expression_keyword_condition
    | postfix_expression__condition {SwiftLookahead.isPostfixDirective(_input)}? postfix_directive # postfix_directive_expression_condition
    | dynamic_type_expression                    # dynamic_type_condition
    | postfix_expression__condition {SwiftLookahead.isOnSameLine(_input)}? '[' expression_list ']' # subscript_expression_condition
    | postfix_expression__condition {SwiftPatternLookahead.isLabeledSubscript(_input)}? '[' function_call_argument_list ']' # labeled_subscript_expression_condition
    // ! is a postfix operator already
    // | postfix_expression__condition '!'                                            # forced_value_expression_condition
    // ? is a postfix operator already
    // | postfix_expression__condition '?'                                            # optional_chaining_expression_condition
    ;

// members that a compilation condition guards, as in
//     view
//         #if os(iOS)
//         .frame(width: 40)
//         #endif
postfix_directive
    : if_directive compilation_condition postfix_expression? (elseif_directive compilation_condition postfix_expression?)* (
        else_directive postfix_expression?
    )? endif_directive
    ;

// GRAMMAR OF A FUNCTION CALL EXPRESSION

// See the optimization in postfix_expression. It should be doing exactly this:
//
// function-call-expression → postfix-expression­ function-call-argument-clause­
// function-call-expression → postfix-expression­ function-call-argument-clause­?­ trailing-closure

function_call_argument_clause
    : '(' ')'
    | '(' function_call_argument_list ')'
    ;

function_call_argument_list
    : function_call_argument (',' function_call_argument)*
    ;

function_call_argument
    : expression
    | label_identifier ':' expression
    | operator_
    | label_identifier ':' operator_
    ;

trailing_closure
    : closure_expression labeled_trailing_closure*
    ;

// the closures after the first: `observe { ... } onStop: { ... }`
labeled_trailing_closure
    : (declaration_identifier | '_') ':' closure_expression
    ;

// GRAMMAR OF AN EXPLICIT MEMBER EXPRESSION

argument_names
    : argument_name (argument_name)*
    ;

argument_name
    : label_identifier ':'
    ;

// GRAMMAR OF A DYNAMIC TYPE EXPRESSION

dynamic_type_expression
    : 'type' '(' 'of' ':' expression ')'
    ;

// GRAMMAR OF A TYPE

// `contextual_keyword type_` is not the first alternative: ANTLR puts an empty action before the
// first alternative of a left-recursive rule, and the parser does not evaluate a predicate behind
// an action while it predicts, so `name: Type` before a line that starts with a name failed (#567).
type_
    : 'repeat' type_
    | contextual_keyword type_
    | array_type
    | dictionary_type
    | function_type
    | type_identifier
    | tuple_type
    | type_ {SwiftLookahead.isAttached(_input)}? '?'
    | type_ {SwiftLookahead.isAttached(_input)}? '!'
    | protocol_composition_type
    | type_ '.' 'Type'
    | type_ '.' 'Protocol'
    | 'Any'
    | 'Self' '.' type_identifier
    | 'Self'
    ;

// GRAMMAR OF A TYPE ANNOTATION

type_annotation
    : ':' attributes? 'inout'? type_
    ;

// GRAMMAR OF A TYPE IDENTIFIER

// `any` and `some` are contextual keywords, so they stay identifiers everywhere else
contextual_keyword
    : {SwiftContextualKeywords.isContextualTypeKeyword(_input)}? Contextual_keyword
    ;

type_identifier
    : type_name generic_argument_clause? ('.' type_identifier)?
    ;

// not `Contextual_keyword`: `some View` is never the type `some` before a statement `View`
type_name
    : declaration_identifier__type
    ;

declaration_identifier__type
    : Identifier
    | keyword_as_identifier_in_declarations
    ;

// GRAMMAR OF A TUPLE TYPE

tuple_type
    : '(' tuple_type_element_list? ')'
    ;

tuple_type_element_list
    : tuple_type_element
    | tuple_type_element ',' tuple_type_element_list
    ;

tuple_type_element
    : element_name element_name type_annotation default_argument_clause?
    | element_name type_annotation default_argument_clause?
    | type_
    ;

element_name
    : label_identifier
    ;

// GRAMMAR OF A THROWS CLAUSE
throws_clause
    : 'throws' ('(' type_ ')')?
    ;

// GRAMMAR OF A FUNCTION TYPE

function_type
    : attributes? function_type_argument_clause 'async'? throws_clause? arrow_operator type_
    | attributes? function_type_argument_clause 'rethrows' arrow_operator type_
    ;

function_type_argument_clause
    : '(' ')'
    | '(' function_type_argument_list range_operator? ')'
    ;

function_type_argument_list
    : function_type_argument
    | function_type_argument ',' function_type_argument_list
    ;

function_type_argument
    : attributes? 'inout'? type_
    | argument_label argument_label? type_annotation
    ;

argument_label
    : label_identifier
    | '_'
    ;

// GRAMMAR OF AN ARRAY TYPE

array_type
    : '[' type_ ']'
    ;

// GRAMMAR OF A DICTIONARY TYPE

dictionary_type
    : '[' type_ ':' type_ ']'
    ;

// GRAMMAR OF AN OPTIONAL TYPE

// The following sets of rules are mutually left-recursive [type, optional_type, implicitly_unwrapped_optional_type, metatype_type]
// optional_type : type '?' ;

// GRAMMAR OF AN IMPLICITLY UNWRAPPED OPTIONAL TYPE

// The following sets of rules are mutually left-recursive [type, optional_type, implicitly_unwrapped_optional_type, metatype_type]
// implicitly_unwrapped_optional_type : type '!' ;

// GRAMMAR OF A PROTOCOL COMPOSITION TYPE

protocol_composition_type
    : protocol_identifier ('&' protocol_identifier)+
    ;

protocol_identifier
    : type_identifier
    | 'Any'
    ;

// GRAMMAR OF A METATYPE TYPE

// The following sets of rules are mutually left-recursive [type, optional_type, implicitly_unwrapped_optional_type, metatype_type]
// metatype_type
//  : type '.' 'Type'
//  | type '.' 'Protocol'
//  ;

// GRAMMAR OF A TYPE INHERITANCE CLAUSE

type_inheritance_clause
    : ':' class_requirement ',' type_inheritance_list
    | ':' class_requirement
    | ':' type_inheritance_list
    ;

type_inheritance_list
    : type_inheritance_item
    | type_inheritance_item ',' type_inheritance_list
    ;

type_inheritance_item
    : attributes? type_identifier ('&' type_identifier)*
    | '~' type_identifier
    ;

class_requirement
    : 'class'
    ;

// ---------- Lexical Structure -----------

// GRAMMAR OF AN IDENTIFIER

// identifier : Identifier | context_sensitive_keyword ;

// var x = 1; funx x() {}; class x {}
//
// identifier is context sensitive
declaration_identifier
    : Identifier
    | Contextual_keyword
    | keyword_as_identifier_in_declarations
    ;

// external, internal argument name
label_identifier
    : Identifier
    | Contextual_keyword
    | keyword_as_identifier_in_labels
    ;

keyword_as_identifier_in_declarations
    : 'Protocol'
    | 'async'
    | 'Type'
    | 'alpha'
    | 'arch'
    | 'arm'
    | 'arm64'
    | 'assignment'
    | 'associativity'
    | 'blue'
    | 'convenience'
    | 'didSet'
    | 'dynamic'
    | 'file'
    | 'final'
    | 'get'
    | 'green'
    | 'higherThan'
    | 'i386'
    | 'iOS'
    | 'iOSApplicationExtension'
    | 'indirect'
    | 'infix'
    | 'lazy'
    | 'left'
    | 'line'
    | 'lowerThan'
    | 'macOS'
    | 'macOSApplicationExtension'
    | 'mutating'
    | 'none'
    | 'nonmutating'
    | 'of'
    | 'open'
    | 'optional'
    | 'os'
    | 'override'
    | 'postfix'
    | 'precedence'
    | 'prefix'
    | 'red'
    | 'required'
    | 'resourceName'
    | 'right'
    | 'safe'
    | 'set'
    | 'swift'
    | 'tvOS'
    | 'type'
    | 'unowned'
    | 'unsafe'
    | 'watchOS'
    | 'weak'
    | 'willSet'
    | 'x86_64'
    ;

// func x(Any: Any)
keyword_as_identifier_in_labels
    : 'Any'
    | 'Protocol'
    | 'Self'
    | 'Type'
    | 'alpha'
    | 'arch'
    | 'arm'
    | 'arm64'
    | 'actor'
    | 'as'
    | 'assignment'
    | 'associatedtype'
    | 'async'
    | 'await'
    | 'associativity'
    | 'blue'
    | 'break'
    | 'case'
    | 'catch'
    | 'class'
    | 'continue'
    | 'convenience'
    | 'default'
    | 'defer'
    | 'deinit'
    | 'didSet'
    | 'do'
    | 'dynamic'
    | 'else'
    | 'enum'
    | 'extension'
    | 'fallthrough'
    | 'false'
    | 'file'
    | 'fileprivate'
    | 'final'
    | 'for'
    | 'func'
    | 'get'
    | 'green'
    | 'guard'
    | 'higherThan'
    | 'i386'
    | 'iOS'
    | 'iOSApplicationExtension'
    | 'if'
    | 'import'
    | 'in'
    | 'indirect'
    | 'infix'
    | 'init'
    | 'internal'
    | 'is'
    | 'lazy'
    | 'left'
    | 'line'
    | 'lowerThan'
    | 'macOS'
    | 'macOSApplicationExtension'
    | 'mutating'
    | 'nil'
    | 'none'
    | 'nonmutating'
    | 'of'
    | 'open'
    | 'operator'
    | 'optional'
    | 'os'
    | 'override'
    | 'postfix'
    | 'precedence'
    | 'precedencegroup'
    | 'prefix'
    | 'private'
    | 'protocol'
    | 'public'
    | 'red'
    | 'repeat'
    | 'required'
    | 'resourceName'
    | 'rethrows'
    | 'return'
    | 'right'
    | 'safe'
    | 'self'
    | 'set'
    | 'static'
    | 'struct'
    | 'subscript'
    | 'super'
    | 'swift'
    | 'switch'
    | 'throw'
    | 'throws'
    | 'true'
    | 'try'
    | 'tvOS'
    | 'type'
    | 'typealias'
    | 'unowned'
    | 'unsafe'
    | 'watchOS'
    | 'weak'
    | 'where'
    | 'while'
    | 'willSet'
    | 'x86_64'
    ;

// a keyword is a member name after a dot: Configuration.default, queue.async, value.as(Int.self)
keyword_member_name
    : 'Any'
    | 'Self'
    | 'actor'
    | 'as'
    | 'associatedtype'
    | 'async'
    | 'await'
    | 'break'
    | 'case'
    | 'catch'
    | 'class'
    | 'continue'
    | 'default'
    | 'defer'
    | 'deinit'
    | 'do'
    | 'else'
    | 'enum'
    | 'extension'
    | 'fallthrough'
    | 'false'
    | 'fileprivate'
    | 'for'
    | 'func'
    | 'guard'
    | 'if'
    | 'import'
    | 'in'
    | 'internal'
    | 'is'
    | 'nil'
    | 'operator'
    | 'precedencegroup'
    | 'private'
    | 'protocol'
    | 'public'
    | 'repeat'
    | 'rethrows'
    | 'return'
    | 'static'
    | 'struct'
    | 'subscript'
    | 'super'
    | 'switch'
    | 'throw'
    | 'throws'
    | 'true'
    | 'try'
    | 'typealias'
    | 'where'
    | 'while'
    ;

// GRAMMAR OF OPERATORS

/*
From doc on operators:
 The tokens =, ->, //, /*, *\/ [without the escape on \/], .,
 the prefix operators <, &, and ?, the infix
 operator ?, and the postfix operators >, !, and ? are reserved. These tokens
 can’t be overloaded, nor can they be used as custom operators.

 The whitespace around an operator is used to determine whether an operator
 is used as a prefix operator, a postfix operator, or a binary operator.

	* If an operator has whitespace around both sides or around neither
	  side, it is treated as a binary operator. As an example, the +
	  operator in a+b and a + b is treated as a binary operator.

	* If an operator has whitespace on the left side only, it is treated
	  as a prefix unary operator. As an example, the ++ operator in a ++b
	  is treated as a prefix unary operator.

	* If an operator has whitespace on the right side only, it is treated
	  as a postfix unary operator. As an example, the ++ operator in a++ b
	  is treated as a postfix unary operator.

	* If an operator has no whitespace on the left but is followed
	  immediately by a dot (.), it is treated as a postfix unary
	  operator. As an example, the ++ operator in a++.b is treated as a
	  postfix unary operator (a++ .b rather than a ++ .b).

 For the purposes of these rules, the characters (, [, and { before an operator,
 the characters ), ], and } after an operator, and the characters ,, ;, and :
 are also considered whitespace.

 There is one caveat to the rules above. If the ! or ? predefined operator has
 no whitespace on the left, it is treated as a postfix operator, regardless of
 whether it has whitespace on the right. To use the ? as the optional-chaining
 operator, it must not have whitespace on the left. To use it in the ternary
 conditional (? :) operator, it must have whitespace around both sides.

 In certain constructs, operators with a leading < or > may be split
 into two or more tokens. The remainder is treated the same way and may
 be split again. As a result, there is no need to use whitespace to
 disambiguate between the closing > characters in constructs like
 Dictionary<String, Array<Int>>. In this example, the closing >
 characters are not treated as a single token that may then be
 misinterpreted as a bit shift >> operator.
*/

//operator : Binary_operator | Prefix_operator | Postfix_operator ;

/* these following tokens are also a Binary_operator so much come first as special case */

assignment_operator
    : {SwiftSupport.isBinaryOp(_input)}? '='
    ;

negate_prefix_operator
    : {SwiftSupport.isPrefixOp(_input)}? '-'
    ;

compilation_condition_AND
    : {SwiftSupport.isOperator(_input,"&&")}? '&' '&'
    ;

compilation_condition_OR
    : {SwiftSupport.isOperator(_input,"||")}? '|' '|'
    ;

compilation_condition_GE
    : {SwiftSupport.isOperator(_input,">=")}? '>' '='
    ;

arrow_operator
    : {SwiftSupport.isOperator(_input,"->")}? '-' '>'
    ;

range_operator
    : {SwiftSupport.isOperator(_input,"...")}? '.' '.' '.'
    ;

same_type_equals
    : {SwiftSupport.isOperator(_input,"==")}? '=' '='
    ;

/**
 "If an operator has whitespace around both sides or around neither side,
 it is treated as a binary operator. As an example, the + operator in a+b
  and a + b is treated as a binary operator."
*/
binary_operator
    : {SwiftSupport.isBinaryOp(_input)}? operator_
    ;

/**
 "If an operator has whitespace on the left side only, it is treated as a
 prefix unary operator. As an example, the ++ operator in a ++b is treated
 as a prefix unary operator."
*/
prefix_operator
    : {SwiftSupport.isPrefixOp(_input)}? operator_
    ;

/**
 "If an operator has whitespace on the right side only, it is treated as a
 postfix unary operator. As an example, the ++ operator in a++ b is treated
 as a postfix unary operator."

 "If an operator has no whitespace on the left but is followed immediately
 by a dot (.), it is treated as a postfix unary operator. As an example,
 the ++ operator in a++.b is treated as a postfix unary operator (a++ .b
 rather than a ++ .b)."
 */
postfix_operator
    : {SwiftSupport.isPostfixOp(_input)}? operator_
    ;

operator_
    : operator_head ({_input.get(_input.index()-1).getType()!=WS}? operator_character)*
    | dot_operator_head ({_input.get(_input.index()-1).getType()!=WS}? dot_operator_character)*
    ;

operator_character
    : operator_head
    | Operator_following_character
    ;

operator_head
    : (
        '/'
        | '='
        | '-'
        | '+'
        | '!'
        | '*'
        | '%'
        | '&'
        | '|'
        | '<'
        | '>'
        | '^'
        | '~'
        | '?'
    ) // wrapping in (..) makes it a fast set comparison
    | Operator_head_other
    ;

dot_operator_head
    : '.'
    ;

dot_operator_character
    : '.'
    | operator_character
    ;

literal
    : numeric_literal
    | string_literal
    | boolean_literal
    | nil_literal
    ;

numeric_literal
    : negate_prefix_operator? integer_literal
    | negate_prefix_operator? Floating_point_literal
    ;

boolean_literal
    : 'true'
    | 'false'
    ;

nil_literal
    : 'nil'
    ;

// GRAMMAR OF AN INTEGER LITERAL

integer_literal
    : Binary_literal
    | Octal_literal
    | Decimal_literal
    | Pure_decimal_digits
    | Hexadecimal_literal
    ;

string_literal
    : Static_string_literal
    | Multiline_string_literal
    | Interpolated_string_literal
    ;
