grammar Ciallo;

// Parser

program
    : (NEWLINE | statement)* EOF
    ;

statement
    : expression
    ;

expression
    : comparison
    ;

comparison
    : additive (comparisonOp additive)*
    ;

comparisonOp
    : EQ | NEQ | LT | LE | GT | GE
    ;

additive
    : multiplicative (addOp multiplicative)*
    ;

addOp
    : PLUS | MINUS
    ;

multiplicative
    : unary (mulOp unary)*
    ;

mulOp
    : STAR | SLASH | PERCENT
    ;

unary
    : MINUS unary   # negateUnary
    | BANG unary    # notUnary
    | primary       # atomUnary
    ;

primary
    : LPAREN expression RPAREN
    | chain
    ;

chain
    : segment+
    ;

segment
    : AT namePath args? block?      # symbolSegment
    | namePath args? block?         # callSegment
    | literal                       # literalSegment
    | block                         # blockSegment
    ;

namePath
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

args
    : LPAREN NEWLINE*
        (expression (NEWLINE* COMMA NEWLINE* expression)* NEWLINE*)?
      RPAREN
    ;

block
    : LBRACE (NEWLINE | statement)* RBRACE
    ;

literal
    : INT
    | NUM_SUFFIX
    | FLOAT
    | STRING
    | LONG_STRING
    | NULL
    | TRUE
    | FALSE
    | array
    ;

array
    : LBRACKET NEWLINE*
        (expression (NEWLINE* COMMA NEWLINE* expression)* NEWLINE*)?
      RBRACKET
    ;

AT          : '@';
DOT         : '.';
COMMA       : ',';
LPAREN      : '(';
RPAREN      : ')';
LBRACE      : '{';
RBRACE      : '}';
LBRACKET    : '[';
RBRACKET    : ']';

LONG_STRING : '"""' .*? '"""';
STRING      : '"' ( '\\' . | ~["\\\r\n] )* '"';

NUM_SUFFIX  : DIGIT+ ('.' DIGIT+)? [a-zA-Z];
FLOAT       : DIGIT+ '.' DIGIT+;

INT         : DIGIT+;

NULL        : 'null';
TRUE        : 'true';
FALSE       : 'false';

IDENTIFIER  : [a-zA-Z_] [a-zA-Z0-9_]*;

// Comments stay in the token stream on the hidden channel so the formatter
// can preserve them; the parser ignores hidden tokens as usual.
LINE_COMMENT  : '//' ~[\r\n]* -> channel(HIDDEN);
BLOCK_COMMENT : '/*' .*? '*/' -> channel(HIDDEN);

PLUS    : '+';
MINUS   : '-';
STAR    : '*';
SLASH   : '/';
PERCENT : '%';
EQ      : '==';
NEQ     : '!=';
LT      : '<';
LE      : '<=';
GT      : '>';
GE      : '>=';
BANG    : '!';

WS      : [ \t\f]+ -> skip;
NEWLINE : ('\r\n' | '\r' | '\n')+;

fragment DIGIT : [0-9];
