/*
 * Derived from the grammars-v4 "csharp/v8-spec" grammar
 * (https://github.com/antlr/grammars-v4/tree/master/csharp/v8-spec, commit 77e908c3e76b0f4a352b0c5fe392606bfed1a4f1),
 * which is derived from ECMA-334 and is distributed under the MIT License.
 * Ward changed it: the symbol-table predicates and reflection-based tree edits are removed,
 * comments go to the hidden channel, the declaration rules are rewritten, and the constructs
 * of C# 9 to 14 are added from the C# language documentation, in rules written for Ward.
 * The grammars-v4 "csharp/v7" grammar is not used.
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
 * to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

parser grammar CSharpParser;

options { tokenVocab=CSharpLexer; }

// Insert here @header for parser.

// Source: §0.0.0 Top Level Rule
// [ADDED] Rule added as the start point
// ║ Added ║
prog: compilation_unit EOF;

// Source: §6.3.1 General




// Source: §6.4.1 General
// ║ Previous:
// ║
// ║ token
// ║     : identifier
// ║     | keyword
// ║     | Integer_Literal
// ║     | Real_Literal
// ║     | Character_Literal
// ║     | String_Literal
// ║     | operator_or_punctuator
// ║     ;
// ║

// Source: §6.4.3 Identifiers
identifier
    : Simple_Identifier
    | contextual_keyword
    | discard_token
    | '__arglist'                                                          // MS extension
    ;

discard_token
    : '_'
    ;

// Source: §6.4.4 Keywords
keyword
    : 'abstract' | 'as'       | 'base'       | 'bool'      | 'break'
    | 'byte'     | 'case'     | 'catch'      | 'char'      | 'checked'
    | 'class'    | 'const'    | 'continue'   | 'decimal'   | DEFAULT
    | 'delegate' | 'do'       | 'double'     | 'else'      | 'enum'
    | 'event'    | 'explicit' | 'extern'     | FALSE       | 'finally'
    | 'fixed'    | 'float'    | 'for'        | 'foreach'   | 'goto'
    | 'if'       | 'implicit' | 'in'         | 'int'       | 'interface'
    | 'internal' | 'is'       | 'lock'       | 'long'      | 'namespace'
    | 'new'      | NULL       | 'object'     | 'operator'  | 'out'
    | 'override' | 'params'   | 'private'    | 'protected' | 'public'
    | 'readonly' | 'ref'      | 'return'     | 'sbyte'     | 'sealed'
    | 'short'    | 'sizeof'   | 'stackalloc' | 'static'    | 'string'
    | 'struct'   | 'switch'   | 'this'       | 'throw'     | TRUE
    | 'try'      | 'typeof'   | 'uint'       | 'ulong'     | 'unchecked'
    | 'unsafe'   | 'ushort'   | 'using'      | 'virtual'   | 'void'
    | 'volatile' | 'while'
    ;

contextual_keyword
    : 'add'     | 'alias'      | 'ascending' | 'async'     | 'await'
    | 'by'      | 'descending' | 'dynamic'   | 'equals'    | 'from'
    | 'get'     | 'global'     | 'group'     | 'into'      | 'join'
    | 'let'     | 'nameof'     | 'notnull'   | 'on'        | 'orderby'
    | 'partial' | 'remove'     | 'select'    | 'set'       | 'unmanaged'
    | 'value'   | 'var'        | 'when'      | 'where'     | 'yield'
    | 'record'  | 'init'       | 'with'      | 'and'       | 'or'
    | 'not'     | 'required'   | 'file'      | 'field'     | 'scoped'
    | 'extension' | 'allows'
    ;

// Source: §6.4.5.1 General
literal
    : boolean_literal
    | Integer_Literal
    | Real_Literal
    | Character_Literal
    | String_Literal
    | Raw_String_Literal
    | Interpolated_Raw_String
    | null_literal
    ;

// Source: §6.4.5.2 Boolean literals
boolean_literal
    : TRUE
    | FALSE
    ;

// Source: §6.4.5.7 The null literal
null_literal
    : NULL
    ;

// Source: §6.4.6 Operators and punctuators

right_shift
    : '>'  '>'
    ;

right_shift_assignment
    : '>' '>='
    ;

right_shift_unsigned
    : '>' '>' '>'
    ;

right_shift_unsigned_assignment
    : '>' '>' '>='
    ;

// Source: §7.8.1 General
namespace_name
    : namespace_or_type_name
    ;

type_name
    : namespace_or_type_name
    ;

namespace_or_type_name
    : identifier type_argument_list? ('.' identifier type_argument_list?)*
    | qualified_alias_member ('.' identifier type_argument_list?)*
    ;

// Source: §8.1 General

// Source: §8.2.1 General






rank_specifier
    : '[' ','* ']'
    ;




// Source: §8.3.1 General



simple_type
    : numeric_type
    | 'bool'
    ;

numeric_type
    : integral_type
    | floating_point_type
    | 'decimal'
    ;

integral_type
    : 'sbyte'
    | 'byte'
    | 'short'
    | 'ushort'
    | 'int'
    | 'uint'
    | 'long'
    | 'ulong'
    | 'char'
    ;

floating_point_type
    : 'float'
    | 'double'
    ;

tuple_type
    : '(' tuple_type_element (',' tuple_type_element)+ ')'
    ;

tuple_type_element
    : type_ identifier?
    ;



// Source: §8.4.2 Type arguments
type_argument_list
    : '<' type_argument (',' type_argument)* '>'
    ;


// Source: §8.5 Type parameters

// Source: §8.8 Unmanaged types

// Source: §9.5 Variable references
variable_reference
    : expression
    ;

// Source: §11.2.1 General

// Source: §11.2.2 Declaration pattern

simple_designation
    : discard_designation
    | single_variable_designation
    ;

discard_designation
    : '_'
    ;

single_variable_designation
    : identifier
    ;

// Source: §11.2.3 Constant pattern

// Source: §11.2.4 Var pattern
var_pattern
    : 'var' designation
    ;

designation
    : simple_designation
    | tuple_designation
    ;

tuple_designation
    : '(' designations? ')'
    ;

designations
    : designation (',' designation)*
    ;

// Source: §11.2.5 Positional pattern
positional_pattern
    : type_? '(' subpatterns? ')' property_subpattern? simple_designation?
    ;

subpatterns
    : subpattern (',' subpattern)*
    ;


// Source: §11.2.6 Property pattern
property_pattern
    : type_? property_subpattern simple_designation?
    ;

property_subpattern
    : '{' '}'
    | '{' subpatterns ','? '}'
    ;

// Source: §11.2.7 Discard pattern
discard_pattern
    : '_'
    ;

// Source: §12.6.2.1 General
argument_list
    : argument (',' argument)*
    ;

argument
    : argument_name? argument_value
    ;

argument_name
    : identifier ':'
    ;

argument_value
    : expression
    | 'in' variable_reference
    | 'ref' variable_reference
    | 'out' variable_reference
    ;

// Source: §12.8.1 General
// ║ Previous:
// ║
// ║ primary_expression
// ║     : literal
// ║     | interpolated_string_expression
// ║     | simple_name
// ║     | parenthesized_expression
// ║     | tuple_expression
// ║     | member_access
// ║     | null_conditional_member_access
// ║     | invocation_expression
// ║     | element_access
// ║     | null_conditional_element_access
// ║     | this_access
// ║     | base_access
// ║     | post_increment_expression
// ║     | post_decrement_expression
// ║     | null_forgiving_expression
// ║     | array_creation_expression
// ║     | object_creation_expression
// ║     | delegate_creation_expression
// ║     | anonymous_object_creation_expression
// ║     | typeof_expression
// ║     | sizeof_expression
// ║     | checked_expression
// ║     | unchecked_expression
// ║     | default_value_expression
// ║     | nameof_expression
// ║     | anonymous_method_expression
// ║     | pointer_member_access     // unsafe code support
// ║     | pointer_element_access    // unsafe code support
// ║     | stackalloc_expression
// ║     ;
// ║
// [CHANGE] This removes a mutual left-recursion group which has been left in the Standard
// [CHANGE] (other uses of MLR have been removed). Without this change the grammar will fail
// [CHANGE] to verify and no sample testing can be done.
primary_expression
    : literal
    | interpolated_string_expression
    | simple_name
    | tuple_expression
    | parenthesized_expression
    // From: member_access
        | primary_expression '.' identifier type_argument_list? 
        | predefined_type '.' identifier type_argument_list? 
        | qualified_alias_member '.' identifier type_argument_list? 
    // From: null_conditional_member_access
    	| primary_expression '?' '.' identifier type_argument_list? (null_forgiving_operator? dependent_access)* 
    // From: invocation_expression
        | primary_expression '(' argument_list? ')' 
    // From: element_access and pointer_element_access (unsafe code support)
        | primary_expression '[' argument_list ']'  
    // From: null_conditional_element_access
        | primary_expression '?' '[' argument_list ']' (null_forgiving_operator? dependent_access)*  
    | this_access
    | base_access
    // From: post_increment_expression
        | primary_expression '++' 
    // From: post_decrement_expression
        | primary_expression '--' 
    // | null_forgiving_expression
        | primary_expression null_forgiving_operator 
    | array_creation_expression
    | object_creation_expression
    | delegate_creation_expression
    | anonymous_object_creation_expression
    | typeof_expression
    | sizeof_expression
    | checked_expression
    | unchecked_expression
    | default_value_expression
    | nameof_expression
    | anonymous_method_expression
    | makeref_expression    // MS extension
    | reftype_expression    // MS extension
    | refvalue_expression   // MS extension
    // From: pointer_member_access     // unsafe code support
        | primary_expression '->' identifier type_argument_list? 
    // From: pointer_element_access    // unsafe code support
        // covered by element_access replacement above
    | stackalloc_expression
    | collection_expression
    ;

// Source: §12.8.3 Interpolated string expressions
interpolated_string_expression
    : interpolated_regular_string_expression
    | interpolated_verbatim_string_expression
    ;

// interpolated regular string expressions

interpolated_regular_string_expression
    : Interpolated_Regular_String_Start Interpolated_Regular_String_Mid?
      ('{' regular_interpolation '}' Interpolated_Regular_String_Mid?)*
      Interpolated_Regular_String_End
    ;

regular_interpolation
    : expression (',' interpolation_minimum_width)?
      Regular_Interpolation_Format?
    ;

interpolation_minimum_width
    : constant_expression
    ;

// interpolated verbatim string expressions

interpolated_verbatim_string_expression
    : Interpolated_Verbatim_String_Start Interpolated_Verbatim_String_Mid?
      ('{' verbatim_interpolation '}' Interpolated_Verbatim_String_Mid?)*
      Interpolated_Verbatim_String_End
    ;

verbatim_interpolation
    : expression (',' interpolation_minimum_width)?
      Verbatim_Interpolation_Format?
    ;

// Source: §12.8.4 Simple names
simple_name
    : identifier type_argument_list?
    ;

// Source: §12.8.5 Parenthesized expressions
parenthesized_expression
    : '(' expression ')'
    ;

// Source: §12.8.6 Tuple expressions
tuple_expression
    : '(' tuple_element (',' tuple_element)+ ')'
    | deconstruction_expression
    ;

tuple_element
    : (identifier ':')? expression
    ;

deconstruction_expression
    : 'var' deconstruction_tuple
    ;

deconstruction_tuple
    : '(' deconstruction_element (',' deconstruction_element)+ ')'
    ;

deconstruction_element
    : deconstruction_tuple
    | identifier
    ;

// Source: §12.8.7.1 General
member_access
    : primary_expression '.' identifier type_argument_list?
    | predefined_type '.' identifier type_argument_list?
    | qualified_alias_member '.' identifier type_argument_list?
    ;

predefined_type
    : 'bool' | 'byte' | 'char' | 'decimal' | 'double' | 'float' | 'int'
    | 'long' | 'object' | 'sbyte' | 'short' | 'string' | 'uint' | 'ulong'
    | 'ushort'
    ;

// Source: §12.8.8 Null Conditional Member Access
null_conditional_member_access
    : primary_expression '?' '.' identifier type_argument_list?
      (null_forgiving_operator? dependent_access)*
    ;

dependent_access
    : '.' identifier type_argument_list?    // member access
    | '[' argument_list ']'                 // element access
    | '(' argument_list? ')'                // invocation
    ;

null_conditional_projection_initializer
    : primary_expression '?' '.' identifier type_argument_list?
    ;

// Source: §12.8.9.1 General
//null_forgiving_expression
//    : primary_expression null_forgiving_operator
//    ;

null_forgiving_operator
    : '!'
    ;

// Source: §12.8.10.1 General
//invocation_expression
//    : primary_expression '(' argument_list? ')'
//    ;

// Source: §12.8.11 Null Conditional Invocation Expression
null_conditional_invocation_expression
    : null_conditional_member_access null_forgiving_operator? '(' argument_list? ')'
    | null_conditional_element_access null_forgiving_operator? '(' argument_list? ')'
    ;

// Source: §12.8.12.1 General
//element_access
//    : primary_expression '[' argument_list ']'
//    ;

// Source: §12.8.13 Null Conditional Element Access
null_conditional_element_access
    : primary_expression '?' '[' argument_list ']'
      (null_forgiving_operator? dependent_access)*
    ;

// Source: §12.8.14 This access
this_access
    : 'this'
    ;

// Source: §12.8.15 Base access
base_access
    : 'base' '.' identifier type_argument_list?
    | 'base' '[' argument_list ']'
    ;

// Source: §12.8.16 Postfix increment and decrement operators
post_increment_expression
    : primary_expression '++'
    ;

post_decrement_expression
    : primary_expression '--'
    ;

// Source: §12.8.17.2.1 General
object_creation_expression
    : 'new' type_? '(' argument_list? ')' object_or_collection_initializer?
    | 'new' type_ object_or_collection_initializer
    ;

object_or_collection_initializer
    : object_initializer
    | collection_initializer
    ;

// Source: §12.8.17.2.2 Object initializers
object_initializer
    : '{' member_initializer_list? '}'
    | '{' member_initializer_list ',' '}'
    ;

member_initializer_list
    : member_initializer (',' member_initializer)*
    ;

member_initializer
    : initializer_target '=' initializer_value
    ;

initializer_target
    : identifier
    | '[' argument_list ']'
    ;

initializer_value
    : expression
    | object_or_collection_initializer
    ;

// Source: §12.8.17.2.3 Collection initializers
collection_initializer
    : '{' element_initializer_list '}'
    | '{' element_initializer_list ',' '}'
    ;

element_initializer_list
    : element_initializer (',' element_initializer)*
    ;

element_initializer
    : non_assignment_expression
    | '{' expression_list '}'
    ;

expression_list
    : expression (',' expression)*
    ;

// Source: §12.8.17.3 Anonymous object creation expressions
anonymous_object_creation_expression
    : 'new' anonymous_object_initializer
    ;

anonymous_object_initializer
    : '{' member_declarator_list? '}'
    | '{' member_declarator_list ',' '}'
    ;

member_declarator_list
    : member_declarator (',' member_declarator)*
    ;

member_declarator
    : simple_name
    | member_access
    | null_conditional_projection_initializer
    | base_access
    | identifier '=' expression
    ;

// Source: §12.8.17.4 Array creation expressions
array_creation_expression
    : 'new' non_array_type '[' expression_list ']' rank_specifier*
      array_initializer?
    | 'new' type_ array_initializer
    | 'new' rank_specifier array_initializer
    ;

// Source: §12.8.17.5 Delegate creation expressions
delegate_creation_expression
    : 'new' delegate_type '(' expression ')'
    ;

// Source: §12.8.18 The typeof operator
typeof_expression
    : 'typeof' '(' type_ ')'
    | 'typeof' '(' unbound_type_name ')'
    | 'typeof' '(' 'void' ')'
    ;

unbound_type_name
    : identifier generic_dimension_specifier?
      ('.' identifier generic_dimension_specifier?)*
    | unbound_qualified_alias_member
      ('.' identifier generic_dimension_specifier?)*
    ;

unbound_qualified_alias_member
    : identifier '::' identifier generic_dimension_specifier?
    ;

generic_dimension_specifier
    : '<' comma* '>'
    ;

comma
    : ','
    ;

// Source: §12.8.19 The sizeof operator
sizeof_expression
    : 'sizeof' '(' unmanaged_type ')'
    ;

// MS extension: typed reference operators (__makeref, __reftype, __refvalue)
makeref_expression
    : '__makeref' '(' expression ')'
    ;

// MS extension: typed reference operators (__makeref, __reftype, __refvalue)
reftype_expression
    : '__reftype' '(' expression ')'
    ;

// MS extension: typed reference operators (__makeref, __reftype, __refvalue)
refvalue_expression
    : '__refvalue' '(' expression ',' type_ ')'
    ;

// Source: §12.8.20 The checked and unchecked operators
checked_expression
    : 'checked' '(' expression ')'
    ;

unchecked_expression
    : 'unchecked' '(' expression ')'
    ;

// Source: §12.8.21 Default value expressions
default_value_expression
    : explicitly_typed_default
    | default_literal
    ;

explicitly_typed_default
    : 'default' '(' type_ ')'
    ;

default_literal
    : 'default'
    ;

// Source: §12.8.22 Stack allocation
stackalloc_expression
    : 'stackalloc' unmanaged_type '[' expression ']'
    | 'stackalloc' unmanaged_type? '[' constant_expression? ']' stackalloc_initializer
    ;

stackalloc_initializer
     : '{' stackalloc_initializer_element_list? '}'
     ;

stackalloc_initializer_element_list
     : stackalloc_element_initializer (',' stackalloc_element_initializer)* ','?
     ;

stackalloc_element_initializer
    : expression
    ;

// Source: §12.8.23 The nameof operator
nameof_expression
    : 'nameof' '(' named_entity ')'
    ;

named_entity
    : named_entity_target ('.' identifier type_argument_list?)*
    ;

named_entity_target
    : simple_name
    | 'this'
    | 'base'
    | predefined_type
    | qualified_alias_member
    ;

// Source: §12.9.1 General
unary_expression
    : primary_expression
    | '+' unary_expression
    | '-' unary_expression
    | logical_negation_operator unary_expression
    | '~' unary_expression
    | '^' unary_expression
    | pre_increment_expression
    | pre_decrement_expression
    | cast_expression
    | await_expression
    | pointer_indirection_expression    // unsafe code support
    | addressof_expression              // unsafe code support
    ;

// Source: §12.9.7 Prefix increment and decrement operators
pre_increment_expression
    : '++' unary_expression
    ;

pre_decrement_expression
    : '--' unary_expression
    ;

// Source: §12.9.8 Cast expressions
// The semantic predicate checks the first token inside '(' against the symbol
// table.  If it is a known type name we prefer cast_expression over the
// parenthesised_expression alternative of primary_expression.

// Source: §12.9.9.1 General
await_expression
    : 'await' unary_expression
    ;

// Source: §12.10 Range operator
// Original:
//range_expression
//    : unary_expression
//    | unary_expression? '..' unary_expression?
//    ;
range_expression
    : unary_expression ('..' unary_expression?)?
    | '..' unary_expression?
    ;

// Source: §12.11 Switch expression

switch_expression_arms
    : switch_expression_arm (',' switch_expression_arm)* ','?
    ;

switch_expression_arm
    : pattern case_guard? '=>' switch_expression_arm_expression
    ;

switch_expression_arm_expression
    : expression
    ;

// Source: §12.12.1 General


// Source: §12.13 Shift operators

// Source: §12.14.1 General


// Source: §12.15.1 General



// Source: §12.16.1 General


// Source: §12.17 The null coalescing operator
// Original:
//null_coalescing_expression
//    : conditional_or_expression
//    | conditional_or_expression '??' null_coalescing_expression
//    | throw_expression
//    ;

// Source: §12.18 The throw expression operator

// Source: §12.19 Declaration expressions
declaration_expression
    : local_variable_type identifier
    ;

// MOVED: local_variable_type relocated to §13.6.2 block and reordered ('var' first).
// See definition below near local_variable_declaration.
// Original:
//   local_variable_type
//       : type_
//       | 'var'
//       ;

// Source: §12.20 Conditional operator
// Original rule before left-factoring:
//conditional_expression
//    : null_coalescing_expression
//    | null_coalescing_expression '?' expression ':' expression
//    | null_coalescing_expression '?' 'ref' variable_reference ':' 'ref' variable_reference
//    ;

// Source: §12.21.1 General

anonymous_method_expression
    : 'async'? 'delegate' explicit_anonymous_function_signature? block
    ;

anonymous_function_signature
    : explicit_anonymous_function_signature
    | implicit_anonymous_function_signature
    ;

explicit_anonymous_function_signature
    : '(' explicit_anonymous_function_parameter_list? ')'
    ;

explicit_anonymous_function_parameter_list
    : explicit_anonymous_function_parameter
      (',' explicit_anonymous_function_parameter)*
    ;


anonymous_function_parameter_modifier
    : 'ref'
    | 'out'
    | 'in'
    ;

implicit_anonymous_function_signature
    : '(' implicit_anonymous_function_parameter_list? ')'
    | implicit_anonymous_function_parameter
    ;

implicit_anonymous_function_parameter_list
    : implicit_anonymous_function_parameter
      (',' implicit_anonymous_function_parameter)*
    ;



// Source: §12.22.1 General
query_expression
    : from_clause query_body
    ;

from_clause
    : 'from' type_? identifier 'in' expression
    ;

query_body
    : query_body_clause* select_or_group_clause query_continuation?
    ;

query_body_clause
    : from_clause
    | let_clause
    | where_clause
    | join_clause
    | join_into_clause
    | orderby_clause
    ;

let_clause
    : 'let' identifier '=' expression
    ;

where_clause
    : 'where' boolean_expression
    ;

join_clause
    : 'join' type_? identifier 'in' expression 'on' expression
      'equals' expression
    ;

join_into_clause
    : 'join' type_? identifier 'in' expression 'on' expression
      'equals' expression 'into' identifier
    ;

orderby_clause
    : 'orderby' orderings
    ;

orderings
    : ordering (',' ordering)*
    ;

ordering
    : expression ordering_direction?
    ;

ordering_direction
    : 'ascending'
    | 'descending'
    ;

select_or_group_clause
    : select_clause
    | group_clause
    ;

select_clause
    : 'select' expression
    ;

group_clause
    : 'group' expression 'by' expression
    ;

query_continuation
    : 'into' identifier query_body
    ;

// Source: §12.23.1 General
assignment
    : unary_expression assignment_operator expression
    ;

assignment_operator
    : '=' 'ref'? | '+=' | '-=' | '*=' | '/=' | '%=' | '&=' | '|=' | '^=' |
      '<<=' | '??='
    | right_shift_assignment
    | right_shift_unsigned_assignment
    ;

// Source: §12.24 Expression
// Original:
//expression
//    : non_assignment_expression
//    | assignment
//    ;
expression
    : non_assignment_expression (assignment_operator expression)?
    ;

non_assignment_expression
    : declaration_expression
    | conditional_expression
    | lambda_expression
    | query_expression
    ;

// Source: §12.25 Constant expressions
constant_expression
    : expression
    ;

// Source: §12.26 Boolean expressions
boolean_expression
    : expression
    ;

// Source: §13.1 General
statement
    : labeled_statement
    | declaration_statement
    | embedded_statement
    ;

embedded_statement
    : block
    | empty_statement
    | expression_statement
    | selection_statement
    | iteration_statement
    | jump_statement
    | try_statement
    | checked_statement
    | unchecked_statement
    | lock_statement
    | using_statement
    | yield_statement
    | unsafe_statement   // unsafe code support
    | fixed_statement    // unsafe code support
    ;

// Source: §13.3.1 General
block
    : '{' 
        statement_list?
      '}' 
    ;

// Source: §13.3.2 Statement lists
statement_list
    : statement+
    ;

// Source: §13.4 The empty statement
empty_statement
    : ';'
    ;

// Source: §13.5 Labeled statements
labeled_statement
    : identifier ':' statement
    ;

// Source: §13.6.1 General
declaration_statement
    : local_variable_declaration ';'
    | local_constant_declaration ';'
    | local_function_declaration
    | using_declaration
    ;

// Source: §13.6.2.1 General
// CHANGED: replaced three-way predicated decision with unified v7-style form.
// 'var' as a terminal resolves the implicit/explicit decision at LT(1);
// ref_kind? prefix is token-distinguishable at LT(1)=ref.
// Drops the implicit/explicit distinction from the parse tree (deferred to semantics).
// Original:
//   local_variable_declaration
//       :  implicitly_typed_local_variable_declaration
//       |  explicitly_typed_local_variable_declaration
//       |  explicitly_typed_ref_local_variable_declaration
//       ;

// CHANGED: unified declarator covering implicit, explicit, and ref initializer cases.
// Original implicit:   identifier '=' expression
// Original explicit:   identifier ('=' local_variable_initializer)?
// Original ref:        identifier '=' 'ref' variable_reference
// Original:
//   local_variable_type
//       : type_
//       | 'var'
//       ;
// Note: 'var' is listed first so SLL resolves the choice at LT(1).
local_variable_type
    : 'var'
    | type_
    ;

// CHANGED: merged implicitly_typed_local_variable_declarator,
// explicitly_typed_local_variable_declarator, and ref_local_variable_declarator
// into one rule. '=' 'ref'? covers plain and ref initializers; initializer
// is optional (semantic analysis enforces mandatory = for implicit var).
// Original (explicit): identifier  ('=' local_variable_initializer)?
local_variable_declarator
    : identifier 
      ('=' 'ref'? local_variable_initializer)?
    ;

local_variable_initializer
    : expression
    | array_initializer
    ;

// DELETED: implicitly_typed_local_variable_declaration
// Merged into local_variable_declaration above.
// Original:
//   // Source: §13.6.2.2 Implicitly typed local variable declarations
//   implicitly_typed_local_variable_declaration
//       : 'var' implicitly_typed_local_variable_declarator
//       | ref_kind 'var' ref_local_variable_declarator
//       ;

// DELETED: implicitly_typed_local_variable_declarator
// Merged into local_variable_declarator above.
// Original:
//   implicitly_typed_local_variable_declarator
//       : identifier '=' expression
//       ;

// DELETED: explicitly_typed_local_variable_declaration
// Merged into local_variable_declaration above.
// Original:
//   // Source: §13.6.2.3 Explicitly typed local variable declarations
//   explicitly_typed_local_variable_declaration
//       : type_ 
//         explicitly_typed_local_variable_declarators
//       ;

// DELETED: explicitly_typed_local_variable_declarators
// Merged into local_variable_declaration above.
// Original:
//   explicitly_typed_local_variable_declarators
//       : explicitly_typed_local_variable_declarator
//         (',' explicitly_typed_local_variable_declarator)*
//       ;

// DELETED: explicitly_typed_local_variable_declarator
// Merged into local_variable_declarator above.
// Original:
//   explicitly_typed_local_variable_declarator
//       : identifier 
//         ('=' local_variable_initializer)?
//       ;

// DELETED: explicitly_typed_ref_local_variable_declaration
// Merged into local_variable_declaration above (ref_kind? prefix).
// Original:
//   // Source: §13.6.2.4 Explicitly typed ref local variable declarations
//   explicitly_typed_ref_local_variable_declaration
//       : ref_kind type_ ref_local_variable_declarators
//       ;

// DELETED: ref_local_variable_declarators
// Merged into local_variable_declaration above.
// Original:
//   ref_local_variable_declarators
//       : ref_local_variable_declarator (',' ref_local_variable_declarator)*
//       ;

// DELETED: ref_local_variable_declarator
// Merged into local_variable_declarator above ('=' 'ref'? local_variable_initializer).
// Original:
//   ref_local_variable_declarator
//       : identifier '=' 'ref' variable_reference
//       ;

// Source: §13.6.3 Local constant declarations
local_constant_declaration
    : 'const' type_ constant_declarators
    ;

constant_declarators
    : constant_declarator (',' constant_declarator)*
    ;

constant_declarator
    : identifier '=' constant_expression
    ;

// Source: §13.6.4 Local function declarations






// Source: §13.7 Expression statements
expression_statement
    : statement_expression ';'
    ;


// Source: §13.8.1 General
selection_statement
    : if_statement
    | switch_statement
    ;

// Source: §13.8.2 The if statement
if_statement
    : 'if' '(' boolean_expression ')' embedded_statement
    | 'if' '(' boolean_expression ')' embedded_statement
      'else' embedded_statement
    ;

// Source: §13.8.3 The switch statement
switch_statement
    : 'switch' selector_expression switch_block
    ;

selector_expression
    : '(' expression ')'
    | tuple_expression
    ;

switch_block
    : '{' switch_section* '}'
    ;

switch_section
    : switch_label+ statement_list
    ;

switch_label
    : 'case' pattern case_guard?  ':'
    | 'default' ':'
    ;


// Source: §13.9.1 General
iteration_statement
    : while_statement
    | do_statement
    | for_statement
    | foreach_statement
    ;

// Source: §13.9.2 The while statement
while_statement
    : 'while' '(' boolean_expression ')' embedded_statement
    ;

// Source: §13.9.3 The do statement
do_statement
    : 'do' embedded_statement 'while' '(' boolean_expression ')' ';'
    ;

// Source: §13.9.4 The for statement
for_statement
    : 'for' '(' for_initializer? ';' for_condition? ';' for_iterator? ')'
      embedded_statement
    ;

for_initializer
    : local_variable_declaration
    | statement_expression_list
    ;

for_condition
    : boolean_expression
    ;

for_iterator
    : statement_expression_list
    ;

statement_expression_list
    : statement_expression (',' statement_expression)*
    ;

// Source: §13.9.5.1 General
foreach_statement
    : 'await'? 'foreach' '(' ref_kind? local_variable_type identifier
      'in' expression ')' embedded_statement
    | 'await'? 'foreach' '(' deconstruction_expression
      'in' expression ')' embedded_statement           // Missing from spec.
    ;

// Source: §13.10.1 General
jump_statement
    : break_statement
    | continue_statement
    | goto_statement
    | return_statement
    | throw_statement
    ;

// Source: §13.10.2 The break statement
break_statement
    : 'break' ';'
    ;

// Source: §13.10.3 The continue statement
continue_statement
    : 'continue' ';'
    ;

// Source: §13.10.4 The goto statement
goto_statement
    : 'goto' identifier ';'
    | 'goto' 'case' constant_expression ';'
    | 'goto' 'default' ';'
    ;

// Source: §13.10.5 The return statement
return_statement
    : 'return' ';'
    | 'return' expression ';'
    | 'return' 'ref' variable_reference ';'
    ;

// Source: §13.10.6 The throw statement
throw_statement
    : 'throw' expression? ';'
    ;

// Source: §13.11 The try statement
try_statement
    : 'try' block catch_clauses
    | 'try' block catch_clauses? finally_clause
    ;

catch_clauses
    : specific_catch_clause+
    | specific_catch_clause* general_catch_clause
    ;

specific_catch_clause
    : 'catch' exception_specifier exception_filter? block
    | 'catch' exception_filter block
    ;

exception_specifier
    : '(' type_ identifier? ')'
    ;

exception_filter
    : 'when' '(' boolean_expression ')'
    ;

general_catch_clause
    : 'catch' block
    ;

finally_clause
    : 'finally' block
    ;

// Source: §13.12 The checked and unchecked statements
checked_statement
    : 'checked' block
    ;

unchecked_statement
    : 'unchecked' block
    ;

// Source: §13.13 The lock statement
lock_statement
    : 'lock' '(' expression ')' embedded_statement
    ;

// Source: §13.14.1 General
using_statement
    : 'await'? 'using' '(' resource_acquisition ')' embedded_statement
    ;

resource_acquisition
    : non_ref_local_variable_declaration
    | expression
    ;

// CHANGED: rewritten to use the new unified rules instead of the deleted
// implicitly_typed_local_variable_declaration / explicitly_typed_local_variable_declaration.
// Excludes ref_kind prefix (same semantic constraint as before).
// Original:
//   non_ref_local_variable_declaration
//       : implicitly_typed_local_variable_declaration
//       | explicitly_typed_local_variable_declaration
//       ;

// Source: §13.14.2 Using declaration

// Source: §13.15 The yield statement
yield_statement
    : 'yield' 'return' expression ';'
    | 'yield' 'break' ';'
    ;

// Source: §14.2 Compilation units

// Source: §14.3 Namespace declarations

qualified_identifier
    : identifier ('.' identifier)*
    ;


// Source: §14.4 Extern alias directives
extern_alias_directive
    : 'extern' 'alias' identifier ';'
    ;

// Source: §14.5.1 General

// Source: §14.5.2 Using alias directives

// Source: §14.5.3 Using namespace directives

// Source: §14.5.4 Using static directives

// Source: §14.6 Namespace member declarations

// Source: §14.7 Type declarations

// Source: §14.8.1 General
qualified_alias_member
    : identifier '::' identifier type_argument_list?
    ;

// Source: §15.2.1 General

// Source: §15.2.2.1 General

// Source: §15.2.3 Type parameters


// Source: §15.2.4.1 General

interface_type_list
    : interface_type (',' interface_type)*
    ;

// Source: §15.2.5 Type parameter constraints






// Source: §15.2.6 Class body

// Source: §15.3.1 General

// Source: §15.4 Constants


// Source: §15.5.1 General




// Source: §15.6.1 General


ref_kind
    : 'ref'
    | 'ref' 'readonly'
    ;










// Source: §15.6.2.1 General



default_argument
    : '=' expression
    ;




// Source: §15.7.1 General





// Source: §15.7.3 Accessors







// Source: §15.8.1 General





// Source: §15.9.1 General





// Source: §15.10.1 General




logical_negation_operator
    : '!'
    ;






// Source: §15.11.1 General





// Source: §15.12 Static constructors



// Source: §15.13 Finalizers


// Source: §16.2.1 General

// Source: §16.2.2 Struct modifiers

// Source: §16.2.5 Struct interfaces

// Source: §16.2.6 Struct body

// Source: §16.3.1 General

// Source: §17.7 Array initializers
array_initializer
    : '{' variable_initializer_list? '}'
    | '{' variable_initializer_list ',' '}'
    ;

variable_initializer_list
    : variable_initializer (',' variable_initializer)*
    ;

variable_initializer
    : expression
    | array_initializer
    ;

// Source: §19.2.1 General

// Source: §19.2.2 Interface modifiers

// Source: §19.2.3.1 General


variance_annotation
    : 'in'
    | 'out'
    ;

// Source: §19.2.4 Base interfaces

// Source: §19.3 Interface body

// Source: §19.4.1 General

// Source: §20.2 Enum declarations




// Source: §20.3 Enum modifiers

// Source: §20.4 Enum members

enum_member_declaration
    : attributes? identifier ('=' constant_expression)?
    ;

// Source: §21.2 Delegate declarations



// Source: §23.3 Attribute specification
global_attributes
    : global_attribute_section+
    ;

global_attribute_section
    : '[' global_attribute_target_specifier attribute_list ']'
    ;

global_attribute_target_specifier
    : global_attribute_target ':'
    ;

global_attribute_target
    : identifier
    ;

attributes
    : attribute_section+
    ;

attribute_section
    : '[' attribute_target_specifier? attribute_list ']'
    ;

attribute_target_specifier
    : attribute_target ':'
    ;

attribute_target
    : identifier
    | keyword
    ;

attribute_list
    : attribute (',' attribute)* ','?
    ;

attribute
    : attribute_name attribute_arguments?
    ;

attribute_name
    : type_name
    ;







// Source: §24.2 Unsafe contexts
unsafe_modifier
    : 'unsafe'
    ;

unsafe_statement
    : 'unsafe' block
    ;

// Source: §24.3 Pointer types

// Source: §24.6.2 Pointer indirection
pointer_indirection_expression
    : '*' unary_expression
    ;

// Source: §24.6.3 Pointer member access
//pointer_member_access
//    : primary_expression '->' identifier type_argument_list?
//    ;

// Source: §24.6.4 Pointer element access
//pointer_element_access
//    : primary_expression '[' expression ']'
//    ;

// Source: §24.6.5 The address-of operator
addressof_expression
    : '&' unary_expression
    ;

// Source: §24.7 The fixed statement
fixed_statement
    : 'fixed' '(' pointer_type fixed_pointer_declarators ')' embedded_statement
    ;

fixed_pointer_declarators
    : fixed_pointer_declarator (','  fixed_pointer_declarator)*
    ;

fixed_pointer_declarator
    : identifier '=' fixed_pointer_initializer
    ;

fixed_pointer_initializer
    : '&' variable_reference
    | expression
    ;

// Source: §24.8.2 Fixed-size buffer declarations


buffer_element_type
    : type_
    ;

fixed_size_buffer_declarators
    : fixed_size_buffer_declarator (',' fixed_size_buffer_declarator)*
    ;

fixed_size_buffer_declarator
    : identifier '[' constant_expression ']'
    ;


// ---------------------------------------------------------------------------------------------
// Rules written for Ward (from the C# language documentation and ECMA-334).
// ---------------------------------------------------------------------------------------------

type_
    : non_array_type nullable_type_annotation? (rank_specifier nullable_type_annotation?)*
    | pointer_type
    ;

non_array_type
    : simple_type
    | tuple_type
    | type_name
    | 'object'
    | 'string'
    | 'dynamic'
    ;

nullable_type_annotation
    : '?'
    ;

class_type
    : type_name
    | 'object'
    | 'string'
    ;

interface_type
    : type_name
    ;

delegate_type
    : type_name
    ;

unmanaged_type
    : type_
    ;

type_argument
    : type_
    ;

type_parameter
    : identifier
    ;

pointer_type
    : (simple_type | type_name) ('*')+
    | 'void' ('*')+
    ;

// Patterns: C# 7 to 11 (relational, logical, list and slice patterns).
pattern
    : disjunctive_pattern
    ;

disjunctive_pattern
    : conjunctive_pattern ('or' conjunctive_pattern)*
    ;

conjunctive_pattern
    : negated_pattern ('and' negated_pattern)*
    ;

negated_pattern
    : 'not' negated_pattern
    | primary_pattern
    ;

primary_pattern
    : relational_pattern
    | var_pattern
    | discard_pattern
    | list_pattern
    | positional_pattern
    | property_pattern
    | declaration_pattern
    | constant_pattern
    | type_pattern
    ;

relational_pattern
    : ('<' | '<=' | '>' | '>=') shift_expression
    ;

declaration_pattern
    : type_ simple_designation
    ;

constant_pattern
    : shift_expression
    ;

type_pattern
    : type_
    ;

list_pattern
    : '[' (list_pattern_element (',' list_pattern_element)* ','?)? ']' simple_designation?
    ;

list_pattern_element
    : slice_pattern
    | pattern
    ;

slice_pattern
    : '..' pattern?
    ;

subpattern
    : (identifier ('.' identifier)* ':')? pattern
    ;

// C# 12 collection expressions.
collection_expression
    : '[' (collection_element (',' collection_element)* ','?)? ']'
    ;

collection_element
    : spread_element
    | expression
    ;

spread_element
    : '..' expression
    ;

with_initializer
    : '{' (member_initializer (',' member_initializer)* ','?)? '}'
    ;

cast_expression
    : '(' simple_type nullable_type_annotation? ')' unary_expression
    | '(' type_ ')' non_sign_unary_expression
    ;

non_sign_unary_expression
    : primary_expression
    | '~' unary_expression
    | logical_negation_operator unary_expression
    | pre_increment_expression
    | pre_decrement_expression
    | cast_expression
    | await_expression
    ;

lambda_expression
    : attributes? lambda_modifier* anonymous_function_signature '=>' anonymous_function_body
    ;

lambda_modifier
    : 'async'
    | 'static'
    ;

explicit_anonymous_function_parameter
    : attributes? parameter_modifier* type_ identifier default_argument?
    ;

implicit_anonymous_function_parameter
    : identifier default_argument?
    ;

anonymous_function_body
    : expression
    | 'ref' variable_reference
    | block
    ;

statement_expression
    : expression
    ;

local_function_declaration
    : attributes? local_function_modifier* return_type identifier type_parameter_list?
      '(' parameter_list? ')' type_parameter_constraints_clause* method_body
    ;

local_function_modifier
    : 'static'
    | 'async'
    | 'unsafe'
    | 'extern'
    | 'ref'
    | 'readonly'
    ;

local_variable_declaration
    : 'scoped'? ref_kind? local_variable_type local_variable_declarator
      (',' local_variable_declarator)*
    ;

non_ref_local_variable_declaration
    : local_variable_type local_variable_declarator (',' local_variable_declarator)*
    ;

using_declaration
    : 'await'? 'using' non_ref_local_variable_declaration ';'
    ;

// Compilation unit, namespaces and usings: file-scoped namespaces, global usings, top-level statements.
compilation_unit
    : extern_alias_directive* namespace_member_declaration*
    ;

namespace_declaration
    : 'namespace' qualified_identifier namespace_body ';'?
    ;

// The members of a file-scoped namespace are the siblings that follow it: nesting them would make
// the loop of members ambiguous with the loop of the enclosing unit, and prediction would read to
// the end of the file at every member.
file_scoped_namespace_declaration
    : 'namespace' qualified_identifier ';'
    ;

namespace_body
    : '{' extern_alias_directive* namespace_member_declaration* '}'
    ;

namespace_member_declaration
    : using_directive
    | global_attribute_section
    | namespace_declaration
    | file_scoped_namespace_declaration
    | type_declaration
    | global_statement
    ;

global_statement
    : statement
    ;

using_directive
    : 'global'? 'using' 'static'? 'unsafe'? (identifier '=' type_ | namespace_or_type_name) ';'
    ;

type_declaration
    : attributes? modifier* type_declaration_body
    ;

type_declaration_body
    : class_declaration
    | struct_declaration
    | record_declaration
    | interface_declaration
    | enum_declaration
    | delegate_declaration
    ;

modifier
    : 'new' | 'public' | 'protected' | 'internal' | 'private' | 'static' | 'readonly'
    | 'volatile' | 'virtual' | 'sealed' | 'override' | 'abstract' | 'extern' | 'unsafe'
    | 'partial' | 'async' | 'required' | 'file' | 'ref'
    ;

class_declaration
    : 'class' identifier type_parameter_list? primary_parameters? class_base?
      type_parameter_constraints_clause* class_body ';'?
    ;

struct_declaration
    : 'struct' identifier type_parameter_list? primary_parameters? class_base?
      type_parameter_constraints_clause* class_body ';'?
    ;

record_declaration
    : 'record' ('class' | 'struct')? identifier type_parameter_list? primary_parameters?
      class_base? type_parameter_constraints_clause* (class_body ';'? | ';')
    ;

interface_declaration
    : 'interface' identifier type_parameter_list? class_base?
      type_parameter_constraints_clause* class_body ';'?
    ;

enum_declaration
    : 'enum' identifier (':' type_)? enum_body ';'?
    ;

enum_body
    : '{' (enum_member_declaration (',' enum_member_declaration)* ','?)? '}'
    ;

delegate_declaration
    : 'delegate' return_type identifier type_parameter_list? '(' parameter_list? ')'
      type_parameter_constraints_clause* ';'
    ;

primary_parameters
    : '(' parameter_list? ')'
    ;

class_base
    : ':' base_type (',' base_type)*
    ;

base_type
    : type_name ('(' argument_list? ')')?
    ;

type_parameter_list
    : '<' type_parameter_declaration (',' type_parameter_declaration)* '>'
    ;

type_parameter_declaration
    : attributes? variance_annotation? type_parameter
    ;

type_parameter_constraints_clause
    : 'where' type_parameter ':' type_parameter_constraint (',' type_parameter_constraint)*
    ;

type_parameter_constraint
    : 'class' nullable_type_annotation?
    | 'struct'
    | 'notnull'
    | 'unmanaged'
    | 'default'
    | 'new' '(' ')'
    | 'allows' 'ref' 'struct'
    | type_
    ;

class_body
    : '{' member_declaration* '}'
    ;

member_declaration
    : attributes? modifier* member_body
    ;

member_body
    : constant_declaration
    | event_declaration
    | type_declaration_body
    | extension_declaration
    | operator_declaration
    | indexer_declaration
    | finalizer_declaration
    | constructor_declaration
    | method_declaration
    | property_declaration
    | field_declaration
    | fixed_size_buffer_declaration
    ;

constant_declaration
    : 'const' type_ constant_declarators ';'
    ;

field_declaration
    : type_ variable_declarators ';'
    ;

variable_declarators
    : variable_declarator (',' variable_declarator)*
    ;

variable_declarator
    : identifier ('=' variable_initializer)?
    ;

method_declaration
    : return_type member_name type_parameter_list? '(' parameter_list? ')'
      type_parameter_constraints_clause* method_body
    ;

return_type
    : type_
    | 'void'
    ;

member_name
    : identifier type_argument_list? ('.' identifier type_argument_list?)*
    ;

method_body
    : block
    | '=>' 'ref'? expression ';'
    | ';'
    ;

parameter_list
    : parameter (',' parameter)*
    ;

parameter
    : attributes? parameter_modifier* type_ identifier default_argument?
    | '__arglist'
    ;

parameter_modifier
    : 'ref' | 'out' | 'in' | 'this' | 'params' | 'scoped' | 'readonly'
    ;

property_declaration
    : type_ member_name property_body
    ;

property_body
    : '{' accessor_declaration+ '}' ('=' variable_initializer ';')?
    | '=>' 'ref'? expression ';'
    ;

accessor_declaration
    : attributes? accessor_modifier* accessor_keyword accessor_body
    ;

accessor_keyword
    : 'get' | 'set' | 'init' | 'add' | 'remove'
    ;

accessor_modifier
    : 'protected' | 'internal' | 'private' | 'readonly' | 'async'
    ;

accessor_body
    : block
    | '=>' 'ref'? expression ';'
    | ';'
    ;

event_declaration
    : 'event' type_ variable_declarators ';'
    | 'event' type_ member_name '{' accessor_declaration+ '}'
    ;

indexer_declaration
    : type_ (identifier type_argument_list? '.')* 'this' '[' parameter_list ']' property_body
    ;

operator_declaration
    : type_ 'operator' 'checked'? overloadable_operator '(' parameter_list ')' method_body
    | ('implicit' | 'explicit') 'operator' 'checked'? type_ '(' parameter_list ')' method_body
    ;

overloadable_operator
    : '+' | '-' | logical_negation_operator | '~' | '++' | '--' | 'true' | 'false'
    | '*' | '/' | '%' | '&' | '|' | '^' | '<<' | right_shift_unsigned | right_shift | '==' | '!=' | '>' | '<'
    | '>=' | '<=' | '+=' | '-=' | '*=' | '/=' | '%=' | '&=' | '|=' | '^=' | '<<='
    | right_shift_assignment | right_shift_unsigned_assignment
    ;

constructor_declaration
    : identifier '(' parameter_list? ')' constructor_initializer? method_body
    ;

constructor_initializer
    : ':' ('base' | 'this') '(' argument_list? ')'
    ;

finalizer_declaration
    : '~' identifier '(' ')' method_body
    ;

// C# 14 extension members.
extension_declaration
    : 'extension' type_parameter_list? '(' parameter ')' type_parameter_constraints_clause*
      class_body
    ;

fixed_size_buffer_declaration
    : 'fixed' type_ fixed_size_buffer_declarator (',' fixed_size_buffer_declarator)* ';'
    ;

attribute_arguments
    : '(' (attribute_argument (',' attribute_argument)*)? ')'
    ;

attribute_argument
    : (identifier ('=' | ':'))? non_assignment_expression
    ;

// One left-recursive rule per precedence band: ANTLR climbs precedence inside a rule, so an
// expression does not pass through one rule and one decision per operator level.
shift_expression
    : range_expression
    | shift_expression 'switch' '{' switch_expression_arms? '}'
    | shift_expression 'with' with_initializer
    | shift_expression ('*' | '/' | '%') shift_expression
    | shift_expression ('+' | '-') shift_expression
    | shift_expression ('<<' | right_shift_unsigned | right_shift) shift_expression
    ;

operator_expression
    : shift_expression
    | operator_expression ('<' | '>' | '<=' | '>=') operator_expression
    | operator_expression 'is' pattern
    | operator_expression 'as' type_
    | operator_expression ('==' | '!=') operator_expression
    | operator_expression '&' operator_expression
    | operator_expression '^' operator_expression
    | operator_expression '|' operator_expression
    | operator_expression '&&' operator_expression
    | operator_expression '||' operator_expression
    | <assoc=right> operator_expression '??' operator_expression
    | 'throw' operator_expression
    ;

conditional_expression
    : operator_expression (
        '?' (
            expression ':' expression
            | 'ref' variable_reference ':' 'ref' variable_reference
        )
    )?
    ;

case_guard
    : 'when' operator_expression
    ;
