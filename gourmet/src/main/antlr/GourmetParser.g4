parser grammar GourmetParser;

options { tokenVocab = GourmetLexer; }

@header {
    package io.github.seggan.gourmet.antlr;
}

file
    : function* EOF
    ;

function
    : attribute* FUN Identifier LPAREN (parameter (COMMA parameter)*)? RPAREN (COLON type)? block
    ;

attribute
    : AT Identifier
    ;

parameter
    : Identifier COLON type
    ;

block
    : LBRACE statement* RBRACE
    ;

statement
    : ((expression | declaration | assignment | return) SEMICOLON)
    | block | if | doWhile | while | for
    ;

declaration
    : LET Identifier (((COLON type)? ASSIGN expression) | COLON type)
    ;

assignment
    : Identifier assignType=(ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | MULT_ASSIGN | DIV_ASSIGN | MOD_ASSIGN)
     expression
    ;

if
    : IF LPAREN ifCond=expression RPAREN ifBlock=statement (ELSE elseBlock=statement)?
    ;

while
    : WHILE LPAREN expression RPAREN statement
    ;

doWhile
    : DO statement WHILE LPAREN expression RPAREN SEMICOLON
    ;

for
    : FOR LPAREN (declaration | init=assignment)? SEMICOLON cond=expression? SEMICOLON update=assignment? RPAREN body=statement
    ;

return
    : RETURN expression?
    ;

expression
    : primary
    | expression DOT Identifier
    | prefixOp=(NOT | MINUS | STAR | ASM | SIZEOF) expression
    | expression op=(STAR | SLASH | PERCENT) expression
    | expression op=(PLUS | MINUS) expression
    | expression op=(EQ | NE | LT | LE | GT | GE) expression
    | expression op=(AND | OR) expression
    ;

primary
    : LPAREN paren=expression RPAREN
    | variable=Identifier
    | Number
    | string
    | Char
    | Boolean
    | fn=Identifier generic? LPAREN (expression (COMMA expression)*)? RPAREN
    ;

string
    : AT? (String | MultilineString)
    ;

type
    : Identifier generic? | type STAR
    ;

generic
    : LBRACKET type (COMMA type)* RBRACKET
    ;