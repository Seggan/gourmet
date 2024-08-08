lexer grammar GourmetLexer;

LineComment
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;

BlockComment
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

WS
    : [\u0020\u0009\u000C\r\n] -> channel(HIDDEN)
    ;

USE: 'use';
FUN: 'fun';
LET: 'let';
IF: 'if';
ELSE: 'else';
DO: 'do';
WHILE: 'while';
FOR: 'for';
IN: 'in';
BREAK: 'break';
CONTINUE: 'continue';
RETURN: 'return';

LPAREN: '(';
RPAREN: ')';
LBRACE: '{';
RBRACE: '}';
LBRACKET: '[';
RBRACKET: ']';

COMMA: ',';
SEMICOLON: ';';
COLON: ':';
DOT: '.';
QUESTION: '?';

ASSIGN: '=';
PLUS_ASSIGN: '+=';
MINUS_ASSIGN: '-=';
MULT_ASSIGN: '*=';
DIV_ASSIGN: '/=';
MOD_ASSIGN: '%=';

PLUS: '+';
MINUS: '-';
STAR: '*';
SLASH: '/';
PERCENT: '%';

LT: '<';
GT: '>';
EQ: '==';
NE: '!=';
LE: '<=';
GE: '>=';
AND: '&&';
OR: '||';
NOT: '!';

Number
    : [0-9_]+ ('.' [0-9_]+)?
    ;

String
    : '"' (~[\r\n])* '"'
    ;

MultilineString
    : '"""' .*? '"""'
    ;

Char
    : '\'' (~[\r\n])+ '\''
    ;

Boolean
    : 'true' | 'false'
    ;

Identifier
    : [a-zA-Z_] [a-zA-Z_0-9]*
    ;