parser grammar GourmetParser;

options { tokenVocab = GourmetLexer; }

file
    : function* EOF
    ;

function
    : FUN Identifier LPAREN (parameter (COMMA parameter)*)? RPAREN (COLON type)? block
    ;

parameter
    : Identifier COLON type
    ;

block
    : LBRACE statements? RBRACE
    ;

statements
    : statement+
    ;

statement
    : (expression SEMICOLON) | declaration | assignment | block | if | doWhile | while | for | return
    ;

declaration
    : VAR Identifier (((COLON type)? ASSIGN expression) | COLON type) SEMICOLON
    ;

assignment
    : Identifier ASSIGN expression SEMICOLON
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
    : FOR LPAREN (declaration | init=assignment)? SEMICOLON cond=expression? SEMICOLON (update=assignment)? RPAREN statement
    ;

return
    : RETURN expression SEMICOLON
    ;

expression
    : primary
    | expression DOT Identifier
    | expression AS type
    | prefixOp=(NOT | MINUS | STAR) expression
    | expression op=(STAR | SLASH | PERCENT) expression
    | expression op=(PLUS | MINUS) expression
    | expression op=(EQ | NE | LT | LE | GT | GE) expression
    | expression op=(AND | OR) expression
    ;

primary
    : LPAREN paren=expression RPAREN
    | variable=Identifier
    | Number
    | String
    | MultilineString
    | Char
    | Boolean
    | fn=Identifier LPAREN (expression (COMMA expression)*)? RPAREN
    ;

type
    : Identifier (LBRACKET generic+=type (COMMA generic+=type)* RBRACKET)?
    | type STAR
    ;