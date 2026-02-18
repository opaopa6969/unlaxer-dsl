# unlaxer-dsl

A tool that automatically generates Java parsers, ASTs, mappers, and evaluators from grammar definitions written in UBNF (Unlaxer BNF) notation.

---

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Build](#build)
- [Quick Start](#quick-start)
- [How to Write UBNF Grammars](#how-to-write-ubnf-grammars)
  - [Overall Structure](#overall-structure)
  - [Global Settings](#global-settings)
  - [Token Declarations](#token-declarations)
  - [Rule Declarations](#rule-declarations)
  - [Element Syntax](#element-syntax)
  - [Annotations](#annotations)
- [How to Use the Code Generators](#how-to-use-the-code-generators)
- [Generated Artifacts in Detail (TinyCalc Example)](#generated-artifacts-in-detail-tinycalc-example)
  - [ASTGenerator](#astgenerator)
  - [ParserGenerator](#parsergenerator)
  - [MapperGenerator](#mappergenerator)
  - [EvaluatorGenerator](#evaluatorgenerator)
- [TinyCalc Tutorial](#tinycalc-tutorial)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)

---

## Features

- **UBNF notation**: Describe grammars concisely with extended BNF syntax (groups `()`, optional `[]`, repetition `{}`, capture `@name`)
- **Four kinds of code generation**: Automatically generate four Java classes from one grammar definition
  - `XxxParsers.java`: parser classes using unlaxer-common parser combinators
  - `XxxAST.java`: type-safe AST using sealed interfaces + records
  - `XxxMapper.java`: parse-tree -> AST mapping skeleton
  - `XxxEvaluator.java`: abstract evaluator that traverses AST
- **Java 21 support**: Full use of sealed interfaces, records, and switch expressions
- **Self-hosting design**: UBNF grammar itself is written in UBNF, aiming to eventually process itself

---

## Prerequisites

| Software | Version |
|---|---|
| Java | 21+ (with `--enable-preview`) |
| Maven | 3.8+ |

---

## Build

```bash
git clone https://github.com/yourorg/unlaxer-dsl.git
cd unlaxer-dsl
mvn package
```

Run tests:

```bash
mvn test
```

---

## Quick Start

1. Prepare a UBNF file (e.g. `tinycalc.ubnf`)
2. Parse the grammar with `UBNFMapper.parse()`
3. Generate Java sources with each generator
4. Add generated sources to your project

```java
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFMapper;
import org.unlaxer.dsl.codegen.*;

// 1. Read .ubnf file content as a string
String ubnfSource = Files.readString(Path.of("tinycalc.ubnf"));

// 2. Parse grammar
GrammarDecl grammar = UBNFMapper.parse(ubnfSource).grammars().get(0);

// 3. Generate each output
CodeGenerator.GeneratedSource ast       = new ASTGenerator()      .generate(grammar);
CodeGenerator.GeneratedSource parsers   = new ParserGenerator()   .generate(grammar);
CodeGenerator.GeneratedSource mapper    = new MapperGenerator()   .generate(grammar);
CodeGenerator.GeneratedSource evaluator = new EvaluatorGenerator().generate(grammar);

// 4. Extract and save source
System.out.println(parsers.packageName()); // org.unlaxer.tinycalc.generated
System.out.println(parsers.className());   // TinyCalcParsers
System.out.println(parsers.source());      // public class TinyCalcParsers { ... }
```

---

## How to Write UBNF Grammars

### Overall Structure

```ubnf
grammar GrammarName {
    // Global settings
    @package: com.example.generated
    @whitespace: javaStyle

    // Token declarations
    token TOKEN_NAME = ParserClassName

    // Rule declarations
    @root
    RootRule ::= ... ;

    OtherRule ::= ... ;
}
```

- You can write multiple `grammar` blocks in one file
- Comments use `//` line comment syntax

---

### Global Settings

Write global settings in the form `@key: value`.

| Key | Value Example | Description |
|---|---|---|
| `@package` | `org.example.generated` | Package name of generated Java files |
| `@whitespace` | `javaStyle` | Whitespace handling style. With `javaStyle`, spaces between rule elements are skipped automatically |
| `@comment` | `{ line: "//" }` | Comment format. With `line: "//"`, line comments are skipped like whitespace |

```ubnf
grammar MyLang {
    @package: com.example.mylang
    @whitespace: javaStyle
    @comment: { line: "//" }
    ...
}
```

---

### Token Declarations

Declare external unlaxer-common parser classes as tokens.

```ubnf
token TOKEN_NAME = ParserClassName
```

- `TOKEN_NAME`: name referenced in grammar (uppercase snake case by convention)
- `ParserClassName`: unlaxer-common parser class name to use (without package)

**Example:**

```ubnf
token NUMBER     = NumberParser
token IDENTIFIER = IdentifierParser
token STRING     = StringParser
```

If you write `NUMBER` in a rule, generated code converts it to `Parser.get(NumberParser.class)`.

---

### Rule Declarations

```ubnf
[annotations...]
RuleName ::= body ;
```

- PascalCase is recommended for rule names
- The body can combine choice `|`, sequence, group `()`, optional `[]`, and repetition `{}`
- Trailing `;` is required

**Choice:**

```ubnf
Factor ::= '(' Expression ')' | NUMBER | IDENTIFIER ;
```

**Sequence:**

```ubnf
VariableDeclaration ::= 'var' IDENTIFIER '=' Expression ';' ;
```

**Group `()` - grouping choices:**

```ubnf
VariableDeclaration ::= ( 'var' | 'variable' ) IDENTIFIER ';' ;
```

**Optional `[]` - zero or one time:**

```ubnf
VariableDeclaration ::= 'var' IDENTIFIER [ '=' Expression ] ';' ;
```

**Repetition `{}` - zero or more times:**

```ubnf
Program ::= { VariableDeclaration } Expression ;
```

---

### Element Syntax

| Syntax | Meaning | Generated Code |
|---|---|---|
| `'literal'` | literal string | `new WordParser("literal")` |
| `RuleName` | rule reference | `Parser.get(RuleNameParser.class)` |
| `TOKEN` | token reference (declared with `token`) | `Parser.get(TokenParserClass.class)` |
| `( A \| B )` | group (choice) | helper class `extends LazyChoice` |
| `[ A ]` | optional (0 or 1) | `new Optional(...)` |
| `{ A }` | repetition (0 or more) | `new ZeroOrMore(...)` |

**Capture name `@name`:**

Adding `@name` to the end of an element maps it to an AST record field.

```ubnf
Expression ::= Term @left { ( '+' @op | '-' @op ) Term @right } ;
```

In this example:
- `Term @left` -> `left` field
- `'+' @op` -> `op` field
- `Term @right` -> `right` field

List capture names corresponding to `@mapping` annotation `params`.

---

### Annotations

Place annotations immediately before a rule declaration.

| Annotation | Description |
|---|---|
| `@root` | Declares this rule as the parse entry point (root). Its class is returned by `getRootParser()` |
| `@mapping(ClassName)` | Specifies AST class name for mapping parse tree |
| `@mapping(ClassName, params=[a, b, c])` | Specifies AST class name and field names. Field types are inferred from capture names |
| `@leftAssoc` | Declares a left-associative operator (currently records intent; does not affect parser generation) |
| `@whitespace` | Controls whitespace handling for this rule individually (optional) |

---

## How to Use the Code Generators

All generators implement the `CodeGenerator` interface.

```java
public interface CodeGenerator {
    GeneratedSource generate(GrammarDecl grammar);

    record GeneratedSource(
        String packageName,  // package name for generated code
        String className,    // generated class name
        String source        // full Java source code
    ) {}
}
```

**Example:**

```java
GrammarDecl grammar = UBNFMapper.parse(ubnfSource).grammars().get(0);

// Generate AST
var ast = new ASTGenerator().generate(grammar);
// ast.packageName() -> "org.unlaxer.tinycalc.generated"
// ast.className()   -> "TinyCalcAST"
// ast.source()      -> "package org.unlaxer...public sealed interface TinyCalcAST..."

// Generate parser
var parsers = new ParserGenerator().generate(grammar);

// Generate mapper
var mapper = new MapperGenerator().generate(grammar);

// Generate evaluator
var evaluator = new EvaluatorGenerator().generate(grammar);
```

---

## Generated Artifacts in Detail (TinyCalc Example)

The following grammar (`examples/tinycalc.ubnf`) is used to explain outputs from each generator.

```ubnf
grammar TinyCalc {
    @package: org.unlaxer.tinycalc.generated
    @whitespace: javaStyle

    token NUMBER     = NumberParser
    token IDENTIFIER = IdentifierParser

    @root
    @mapping(TinyCalcProgram, params=[declarations, expression])
    TinyCalc ::=
        { VariableDeclaration } @declarations
        Expression              @expression ;

    @mapping(VarDecl, params=[keyword, name, init])
    VariableDeclaration ::=
        ( 'var' | 'variable' ) @keyword
        IDENTIFIER @name
        [ 'set' Expression @init ]
        ';' ;

    @mapping(BinaryExpr, params=[left, op, right])
    @leftAssoc
    Expression ::= Term @left { ( '+' @op | '-' @op ) Term @right } ;

    @mapping(BinaryExpr, params=[left, op, right])
    @leftAssoc
    Term ::= Factor @left { ( '*' @op | '/' @op ) Factor @right } ;

    Factor ::=
          '(' Expression ')'
        | NUMBER
        | IDENTIFIER ;
}
```

---

### ASTGenerator

Generates `TinyCalcAST.java`. It collects rules with `@mapping` and outputs them as sealed interface + records.

**Generated `TinyCalcAST.java`:**

```java
package org.unlaxer.tinycalc.generated;

import java.util.List;
import java.util.Optional;

public sealed interface TinyCalcAST permits
    TinyCalcAST.TinyCalcProgram,
    TinyCalcAST.VarDecl,
    TinyCalcAST.BinaryExpr {

    // { VariableDeclaration } @declarations -> List<TinyCalcAST.VarDecl>
    // Expression @expression -> TinyCalcAST.BinaryExpr (Expression has @mapping(BinaryExpr))
    record TinyCalcProgram(
        List<TinyCalcAST.VarDecl> declarations,
        TinyCalcAST.BinaryExpr expression
    ) implements TinyCalcAST {}

    // ( 'var' | 'variable' ) @keyword -> Object because it is a grouped element
    // IDENTIFIER @name -> String because it is a token reference
    // [ 'set' Expression @init ] @init -> Optional<TinyCalcAST.BinaryExpr>
    record VarDecl(
        Object keyword,
        String name,
        Optional<TinyCalcAST.BinaryExpr> init
    ) implements TinyCalcAST {}

    // Both Expression and Term have @mapping(BinaryExpr), but only one record is generated
    record BinaryExpr(
        TinyCalcAST.BinaryExpr left,
        String op,
        TinyCalcAST.BinaryExpr right
    ) implements TinyCalcAST {}
}
```

**Field type inference rules:**

| Grammar Element | Inferred Type |
|---|---|
| `{ RuleName } @field` (reference to @mapping rule inside repetition) | `List<TinyCalcAST.ClassName>` |
| `[ RuleName ] @field` (reference to @mapping rule inside optional) | `Optional<TinyCalcAST.ClassName>` |
| `RuleName @field` (reference to @mapping rule) | `TinyCalcAST.ClassName` |
| `TOKEN @field` (token reference) | `String` |
| `'literal' @field` (terminal symbol) | `String` |
| `( A \| B ) @field` (group element) | `Object` |

---

### ParserGenerator

Generates `TinyCalcParsers.java`. It emits parser class groups that correspond to the grammar using unlaxer-common parser combinators.

**Structure of generated `TinyCalcParsers.java`:**

```java
package org.unlaxer.tinycalc.generated;

import java.util.Optional;
import java.util.function.Supplier;
import org.unlaxer.RecursiveMode;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.*;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.SpaceParser;
import org.unlaxer.reducer.TagBasedReducer.NodeKind;
import org.unlaxer.util.cache.SupplierBoundCache;

public class TinyCalcParsers {

    // --- Whitespace Delimiter ---
    // Generated from @whitespace: javaStyle setting
    public static class TinyCalcSpaceDelimitor extends LazyZeroOrMore {
        private static final long serialVersionUID = 1L;
        @Override
        public Supplier<Parser> getLazyParser() {
            return new SupplierBoundCache<>(() -> Parser.get(SpaceParser.class));
        }
        @Override
        public java.util.Optional<Parser> getLazyTerminatorParser() {
            return java.util.Optional.empty();
        }
    }

    // --- Base Chain ---
    // Base class for sequence parsers. Automatically skips whitespace between parsers.
    public static abstract class TinyCalcLazyChain extends LazyChain {
        private static final long serialVersionUID = 1L;
        private static final TinyCalcSpaceDelimitor SPACE = createSpace();
        ...
        @Override
        public void prepareChildren(Parsers c) {
            if (!c.isEmpty()) return;
            c.add(SPACE);
            for (Parser p : getLazyParsers()) { c.add(p); c.add(SPACE); }
        }
        public abstract Parsers getLazyParsers();
    }

    // --- Helper classes (expanded composite elements) ---

    // Generated from ( 'var' | 'variable' ) in VariableDeclaration
    public static class VariableDeclarationGroup0Parser extends LazyChoice {
        private static final long serialVersionUID = 1L;
        @Override
        public Parsers getLazyParsers() {
            return new Parsers(
                new WordParser("var"),
                new WordParser("variable")
            );
        }
        @Override
        public java.util.Optional<RecursiveMode> getNotAstNodeSpecifier() {
            return java.util.Optional.empty();
        }
    }

    // Generated from [ 'set' Expression @init ] in VariableDeclaration
    public static class VariableDeclarationOpt0Parser extends TinyCalcLazyChain {
        private static final long serialVersionUID = 1L;
        @Override
        public Parsers getLazyParsers() {
            return new Parsers(
                new WordParser("set"),
                Parser.get(ExpressionParser.class)
            );
        }
    }

    // Generated from { ( '+' @op | '-' @op ) Term @right } in Expression
    public static class ExpressionRepeat0Parser extends TinyCalcLazyChain {
        private static final long serialVersionUID = 1L;
        @Override
        public Parsers getLazyParsers() {
            return new Parsers(
                Parser.get(ExpressionGroup0Parser.class),
                Parser.get(TermParser.class)
            );
        }
    }

    // --- Rule classes ---

    // @root rule
    public static class TinyCalcParser extends TinyCalcLazyChain {
        private static final long serialVersionUID = 1L;
        @Override
        public Parsers getLazyParsers() {
            return new Parsers(
                new ZeroOrMore(VariableDeclarationParser.class),
                Parser.get(ExpressionParser.class)
            );
        }
    }

    public static class VariableDeclarationParser extends TinyCalcLazyChain {
        private static final long serialVersionUID = 1L;
        @Override
        public Parsers getLazyParsers() {
            return new Parsers(
                Parser.get(VariableDeclarationGroup0Parser.class),
                Parser.get(IdentifierParser.class),
                new Optional(VariableDeclarationOpt0Parser.class),
                new WordParser(";")
            );
        }
    }

    public static class ExpressionParser extends TinyCalcLazyChain {
        private static final long serialVersionUID = 1L;
        @Override
        public Parsers getLazyParsers() {
            return new Parsers(
                Parser.get(TermParser.class),
                new ZeroOrMore(ExpressionRepeat0Parser.class)
            );
        }
    }

    // Factor is a 3-way choice body, so it extends LazyChoice
    // Candidate 1 '(' Expression ')' contains multiple elements, so it is wrapped in anonymous TinyCalcLazyChain
    public static class FactorParser extends LazyChoice {
        private static final long serialVersionUID = 1L;
        @Override
        public Parsers getLazyParsers() {
            return new Parsers(
                new TinyCalcLazyChain() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public Parsers getLazyParsers() {
                        return new Parsers(
                            new WordParser("("),
                            Parser.get(ExpressionParser.class),
                            new WordParser(")")
                        );
                    }
                },
                Parser.get(NumberParser.class),
                Parser.get(IdentifierParser.class)
            );
        }
        @Override
        public java.util.Optional<RecursiveMode> getNotAstNodeSpecifier() {
            return java.util.Optional.empty();
        }
    }

    // --- Factory ---
    public static Parser getRootParser() {
        return Parser.get(TinyCalcParser.class);
    }
}
```

**Element conversion rules:**

| Grammar Element | Generated Code |
|---|---|
| `'var'` | `new WordParser("var")` |
| `NUMBER` (token declared) | `Parser.get(NumberParser.class)` |
| `Expression` (rule reference) | `Parser.get(ExpressionParser.class)` |
| `{ VariableDeclaration }` (repetition of a single RuleRef) | `new ZeroOrMore(VariableDeclarationParser.class)` |
| `{ ( '+' \| '-' ) Term }` (repetition of composite body) | helper class + `new ZeroOrMore(ExpressionRepeat0Parser.class)` |
| `[ 'set' Expression ]` (optional composite body) | helper class + `new Optional(VariableDeclarationOpt0Parser.class)` |
| `( 'var' \| 'variable' )` (group) | helper class `extends LazyChoice` + `Parser.get(VariableDeclarationGroup0Parser.class)` |

**Helper class naming rules:**

| Pattern | Class Name |
|---|---|
| `{ compositeBody }` (repetition) | `{RuleName}Repeat{N}Parser` |
| `[ compositeBody ]` (optional) | `{RuleName}Opt{N}Parser` |
| `( body )` (group) | `{RuleName}Group{N}Parser` |

`N` is a zero-based sequence number inside each rule.

---

### MapperGenerator

Generates `TinyCalcMapper.java`. It outputs `to{ClassName}(Token)` method skeletons for rules annotated with `@mapping`.

**Structure of generated `TinyCalcMapper.java`:**

```java
package org.unlaxer.tinycalc.generated;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;

public class TinyCalcMapper {
    private TinyCalcMapper() {}

    /**
     * Parses TinyCalc source text and converts it into AST.
     * NOTE: complete implementation after TinyCalcParsers is generated and placed.
     */
    public static TinyCalcAST.TinyCalcProgram parse(String source) {
        // TODO: implement after TinyCalcParsers is generated
        // StringSource stringSource = StringSource.createRootSource(source);
        // try (ParseContext context = new ParseContext(stringSource)) {
        //     Parser rootParser = TinyCalcParsers.getRootParser();
        //     Parsed parsed = rootParser.parse(context);
        //     if (!parsed.isSucceeded()) {
        //         throw new IllegalArgumentException("Parse failed: " + source);
        //     }
        //     return toTinyCalcProgram(parsed.getRootToken());
        // }
        throw new UnsupportedOperationException("TinyCalcParsers: not implemented");
    }

    // --- Mapping methods (skeleton) ---

    static TinyCalcAST.TinyCalcProgram toTinyCalcProgram(Token token) {
        // TODO: extract declarations
        // TODO: extract expression
        return new TinyCalcAST.TinyCalcProgram(
            null, // declarations
            null  // expression
        );
    }

    static TinyCalcAST.VarDecl toVarDecl(Token token) {
        // TODO: extract keyword, name, init
        return new TinyCalcAST.VarDecl(null, null, null);
    }

    // BinaryExpr appears in both Expression and Term @mapping, but generated once
    static TinyCalcAST.BinaryExpr toBinaryExpr(Token token) {
        // TODO: extract left, op, right
        return new TinyCalcAST.BinaryExpr(null, null, null);
    }

    // --- Utilities ---

    /** Find descendant Tokens for the specified parser class in depth-first order */
    static List<Token> findDescendants(Token token, Class<? extends Parser> parserClass) { ... }

    /** Remove surrounding single quotes from quoted string */
    static String stripQuotes(String quoted) { ... }
}
```

**Post-generation work (manual implementation):**

`to{ClassName}` methods in `TinyCalcMapper` are generated as TODO skeletons. Implement actual field extraction logic using `findDescendants()`.

```java
// Implementation example
static TinyCalcAST.BinaryExpr toBinaryExpr(Token token) {
    // Get left-hand side Factor or recursive BinaryExpr
    List<Token> leftTokens = findDescendants(token, TermParser.class);
    // Extract op ('+'/'-')
    // Extract right
    ...
}
```

---

### EvaluatorGenerator

Generates `TinyCalcEvaluator.java`. It is an abstract class with type parameter `<T>`, where you implement evaluation logic by overriding methods for each AST node type.

**Structure of generated `TinyCalcEvaluator.java`:**

```java
package org.unlaxer.tinycalc.generated;

public abstract class TinyCalcEvaluator<T> {

    private DebugStrategy debugStrategy = DebugStrategy.NOOP;

    public void setDebugStrategy(DebugStrategy strategy) {
        this.debugStrategy = strategy;
    }

    /** Public entry point. Calls evalInternal with debug hooks. */
    public T eval(TinyCalcAST node) {
        debugStrategy.onEnter(node);
        T result = evalInternal(node);
        debugStrategy.onExit(node, result);
        return result;
    }

    /** Dispatch with sealed switch */
    private T evalInternal(TinyCalcAST node) {
        return switch (node) {
            case TinyCalcAST.TinyCalcProgram n -> evalTinyCalcProgram(n);
            case TinyCalcAST.VarDecl n        -> evalVarDecl(n);
            case TinyCalcAST.BinaryExpr n     -> evalBinaryExpr(n);
        };
    }

    // Abstract methods corresponding to each @mapping class
    protected abstract T evalTinyCalcProgram(TinyCalcAST.TinyCalcProgram node);
    protected abstract T evalVarDecl(TinyCalcAST.VarDecl node);
    protected abstract T evalBinaryExpr(TinyCalcAST.BinaryExpr node);    // shared by Expression and Term

    // --- Debug strategies ---

    public interface DebugStrategy {
        void onEnter(TinyCalcAST node);
        void onExit(TinyCalcAST node, Object result);

        DebugStrategy NOOP = new DebugStrategy() {
            public void onEnter(TinyCalcAST node) {}
            public void onExit(TinyCalcAST node, Object result) {}
        };
    }

    /** Debug implementation that counts eval() call steps */
    public static class StepCounterStrategy implements DebugStrategy {
        private int step = 0;
        private final java.util.function.BiConsumer<Integer, TinyCalcAST> onStep;

        public StepCounterStrategy(java.util.function.BiConsumer<Integer, TinyCalcAST> onStep) {
            this.onStep = onStep;
        }

        @Override
        public void onEnter(TinyCalcAST node) { onStep.accept(++step, node); }

        @Override
        public void onExit(TinyCalcAST node, Object result) {}
    }
}
```

**Evaluator implementation example (arithmetic evaluation):**

```java
public class TinyCalcCalculator extends TinyCalcEvaluator<Double> {

    @Override
    protected Double evalTinyCalcProgram(TinyCalcAST.TinyCalcProgram node) {
        // Process variable declarations, then evaluate final expression
        node.declarations().forEach(d -> eval(d));
        return eval(node.expression());
    }

    @Override
    protected Double evalVarDecl(TinyCalcAST.VarDecl node) {
        // Register variable in environment (implementation omitted)
        return 0.0;
    }

    @Override
    protected Double evalBinaryExpr(TinyCalcAST.BinaryExpr node) {
        Double left  = eval(node.left());
        Double right = eval(node.right());
        return switch (node.op()) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> left / right;
            default  -> throw new IllegalArgumentException("Unknown op: " + node.op());
        };
    }
}
```

---

## TinyCalc Tutorial

TinyCalc is a small calculator DSL that supports variable declarations and basic arithmetic. Here is the flow from grammar definition to evaluation.

### Step 1: Define the grammar

See `examples/tinycalc.ubnf` (the TinyCalc grammar shown above).

### Step 2: Generate code

```java
String src = Files.readString(Path.of("examples/tinycalc.ubnf"));
GrammarDecl grammar = UBNFMapper.parse(src).grammars().get(0);

// Generate 4 kinds of code and save to src/main/java/org/unlaxer/tinycalc/generated/
for (CodeGenerator gen : List.of(
        new ASTGenerator(),
        new ParserGenerator(),
        new MapperGenerator(),
        new EvaluatorGenerator())) {
    var result = gen.generate(grammar);
    var path = Path.of("src/main/java",
        result.packageName().replace('.', '/'),
        result.className() + ".java");
    Files.createDirectories(path.getParent());
    Files.writeString(path, result.source());
}
```

### Step 3: Implement `TinyCalcMapper.parse()`

Complete the TODO part in the generated `parse()` method in `TinyCalcMapper.java`.

```java
public static TinyCalcAST.TinyCalcProgram parse(String source) {
    StringSource stringSource = StringSource.createRootSource(source);
    try (ParseContext context = new ParseContext(stringSource)) {
        Parser rootParser = TinyCalcParsers.getRootParser();
        Parsed parsed = rootParser.parse(context);
        if (!parsed.isSucceeded()) {
            throw new IllegalArgumentException("Parse failed: " + source);
        }
        return toTinyCalcProgram(parsed.getRootToken());
    }
}
```

### Step 4: Implement evaluator and run evaluation

```java
TinyCalcAST.TinyCalcProgram ast = TinyCalcMapper.parse("1 + 2 * 3");
TinyCalcCalculator calc = new TinyCalcCalculator();
Double result = calc.eval(ast);
System.out.println(result); // 7.0
```

---

## Project Structure

```
unlaxer-dsl/
├── grammar/
│   └── ubnf.ubnf              Self-hosting grammar: UBNF described in UBNF
├── examples/
│   └── tinycalc.ubnf          TinyCalc sample grammar
├── src/
│   ├── main/java/org/unlaxer/dsl/
│   │   ├── bootstrap/
│   │   │   ├── UBNFAST.java       UBNF AST (sealed interface + record)
│   │   │   ├── UBNFParsers.java   Bootstrap parser (handwritten)
│   │   │   └── UBNFMapper.java    Parse tree -> AST mapper
│   │   └── codegen/
│   │       ├── CodeGenerator.java      Common interface
│   │       ├── ASTGenerator.java       XxxAST.java generator
│   │       ├── ParserGenerator.java    XxxParsers.java generator
│   │       ├── MapperGenerator.java    XxxMapper.java generator
│   │       └── EvaluatorGenerator.java XxxEvaluator.java generator
│   └── test/java/org/unlaxer/dsl/
│       ├── UBNFParsersTest.java
│       ├── UBNFMapperTest.java
│       └── codegen/
│           ├── ASTGeneratorTest.java
│           ├── ParserGeneratorTest.java
│           ├── MapperGeneratorTest.java
│           └── EvaluatorGeneratorTest.java
└── pom.xml
```

---

## Roadmap

| Phase | Description | Status |
|---|---|---|
| Phase 0 | UBNF grammar definition (`grammar/ubnf.ubnf`) | Done |
| Phase 1 | Bootstrap parser (`UBNFParsers.java`) | Done |
| Phase 2 | AST definitions + bootstrap mapper (`UBNFAST.java`, `UBNFMapper.java`) | Done |
| Phase 3 | ASTGenerator / EvaluatorGenerator / MapperGenerator implementation | Done |
| Phase 4 | ParserGenerator implementation | Done |
| Phase 5 | Compile verification for generated code (using `unlaxer-runtime-compiler`) | Not started |
| Phase 6 | Self-hosting (generate itself from `grammar/ubnf.ubnf`) | Not started |
| Phase 7 | Auto-generate LSP / DAP support | Not started |

---

## License

MIT License - Copyright (c) 2026 opaopa6969
