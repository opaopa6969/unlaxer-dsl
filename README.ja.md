# unlaxer-dsl

UBNF（Unlaxer BNF）記法で書いた文法定義から、Java のパーサー・AST・マッパー・エバリュエーター・LSP サーバーを自動生成し、VS Code 拡張（VSIX）までビルドできるツールです。

---

## 目次

- [特徴](#特徴)
- [前提条件](#前提条件)
- [ビルド](#ビルド)
- [クイックスタート](#クイックスタート)
- [UBNF 文法の書き方](#ubnf-文法の書き方)
  - [全体構造](#全体構造)
  - [グローバル設定](#グローバル設定)
  - [トークン宣言](#トークン宣言)
  - [ルール宣言](#ルール宣言)
  - [要素記法](#要素記法)
  - [アノテーション](#アノテーション)
- [コードジェネレーターの使い方](#コードジェネレーターの使い方)
- [生成物の詳細（TinyCalc 例）](#生成物の詳細tinycalc-例)
  - [ASTGenerator](#astgenerator)
  - [ParserGenerator](#parsergenerator)
  - [MapperGenerator](#mappergenerator)
  - [EvaluatorGenerator](#evaluatorgenerator)
  - [LSPGenerator](#lspgenerator)
  - [LSPLauncherGenerator](#lsplaunchergenerator)
- [CodegenMain — CLI ツール](#codegenmain--cli-ツール)
- [VS Code 拡張（VSIX）のビルド](#vs-code-拡張vsixのビルド)
- [TinyCalc チュートリアル](#tinycalc-チュートリアル)
- [プロジェクト構造](#プロジェクト構造)
- [ロードマップ](#ロードマップ)

---

## 特徴

- **UBNF 記法** — BNF 拡張記法（グループ `()`、Optional `[]`、繰り返し `{}`、キャプチャ `@name`）でシンプルに文法を記述できる
- **6 種類のコード生成** — 1 つの文法定義から 6 つの Java クラスを自動生成
  - `XxxParsers.java` — unlaxer-common のパーサーコンビネータを使ったパーサー
  - `XxxAST.java` — sealed interface + record による型安全な AST
  - `XxxMapper.java` — パースツリー → AST 変換のスケルトン
  - `XxxEvaluator.java` — AST を走査する抽象エバリュエーター
  - `XxxLanguageServer.java` — lsp4j 製 LSP サーバー（補完・ホバー・シンタックスハイライト）
  - `XxxLspLauncher.java` — stdio 経由で起動する LSP サーバーの main クラス
- **CLI ツール `CodegenMain`** — `.ubnf` ファイルを指定してコマンド 1 行でソースを生成
- **VSIX ワンコマンドビルド** — `tinycalc-vscode/` で `mvn verify` を実行するだけで VS Code 拡張（`.vsix`）が `target/` に生成される
- **Java 21 対応** — sealed interface・record・switch 式をフル活用
- **自己ホスティング設計** — UBNF 文法自体も UBNF で記述されており、将来的には自分自身を処理できることを目指している

---

## 前提条件

| ソフトウェア | バージョン | 用途 |
|---|---|---|
| Java | 21 以上（`--enable-preview` 有効） | ライブラリ本体・コード生成 |
| Maven | 3.8 以上 | ビルド管理 |
| Node.js + npm | 18 以上 | VSIX ビルド時のみ必要 |

---

## ビルド

```bash
git clone https://github.com/yourorg/unlaxer-dsl.git
cd unlaxer-dsl
mvn package
```

テスト実行：

```bash
mvn test
```

---

## クイックスタート

1. UBNF ファイルを用意する（例: `tinycalc.ubnf`）
2. `UBNFMapper.parse()` で文法をパース
3. 各ジェネレーターで Java ソースを生成
4. 生成されたソースをプロジェクトに追加

```java
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFMapper;
import org.unlaxer.dsl.codegen.*;

// 1. .ubnf ファイルの内容を文字列として読み込む
String ubnfSource = Files.readString(Path.of("tinycalc.ubnf"));

// 2. 文法をパース
GrammarDecl grammar = UBNFMapper.parse(ubnfSource).grammars().get(0);

// 3. 各コードを生成
CodeGenerator.GeneratedSource ast       = new ASTGenerator()      .generate(grammar);
CodeGenerator.GeneratedSource parsers   = new ParserGenerator()   .generate(grammar);
CodeGenerator.GeneratedSource mapper    = new MapperGenerator()   .generate(grammar);
CodeGenerator.GeneratedSource evaluator = new EvaluatorGenerator().generate(grammar);

// 4. ソースを取り出して保存
System.out.println(parsers.packageName()); // org.unlaxer.tinycalc.generated
System.out.println(parsers.className());   // TinyCalcParsers
System.out.println(parsers.source());      // public class TinyCalcParsers { ... }
```

---

## UBNF 文法の書き方

### 全体構造

```ubnf
grammar GrammarName {
    // グローバル設定
    @package: com.example.generated
    @whitespace: javaStyle

    // トークン宣言
    token TOKEN_NAME = ParserClassName

    // ルール宣言
    @root
    RootRule ::= ... ;

    OtherRule ::= ... ;
}
```

- 1 ファイルに複数の `grammar` ブロックを書ける
- コメントは `//` 行コメント

---

### グローバル設定

`@キー: 値` の形式でグローバル設定を記述する。

| キー | 値の例 | 説明 |
|---|---|---|
| `@package` | `org.example.generated` | 生成される Java ファイルのパッケージ名 |
| `@whitespace` | `javaStyle` | 空白処理スタイル。`javaStyle` の場合、ルール間にスペースが自動的に読み飛ばされる |
| `@comment` | `{ line: "//" }` | コメント形式。`line: "//"` の場合、行コメントを空白と同様に読み飛ばす |

```ubnf
grammar MyLang {
    @package: com.example.mylang
    @whitespace: javaStyle
    @comment: { line: "//" }
    ...
}
```

---

### トークン宣言

外部の unlaxer-common パーサークラスをトークンとして宣言する。

```ubnf
token TOKEN_NAME = ParserClassName
```

- `TOKEN_NAME` — 文法内で参照する名前（慣習として大文字スネークケース）
- `ParserClassName` — 使用する unlaxer-common のパーサークラス名（パッケージなし）

**例：**

```ubnf
token NUMBER     = NumberParser
token IDENTIFIER = IdentifierParser
token STRING     = StringParser
```

ルール内で `NUMBER` と書くと、生成コードでは `Parser.get(NumberParser.class)` に変換される。

---

### ルール宣言

```ubnf
[アノテーション...]
RuleName ::= 本体 ;
```

- ルール名は PascalCase を推奨
- 本体は選択 `|`、順列、グループ `()`、Optional `[]`、繰り返し `{}` を組み合わせて記述
- 末尾に `;` が必要

**選択（Choice）：**

```ubnf
Factor ::= '(' Expression ')' | NUMBER | IDENTIFIER ;
```

**順列（Sequence）：**

```ubnf
VariableDeclaration ::= 'var' IDENTIFIER '=' Expression ';' ;
```

**グループ `()` — 選択のグループ化：**

```ubnf
VariableDeclaration ::= ( 'var' | 'variable' ) IDENTIFIER ';' ;
```

**Optional `[]` — 0 または 1 回：**

```ubnf
VariableDeclaration ::= 'var' IDENTIFIER [ '=' Expression ] ';' ;
```

**繰り返し `{}` — 0 回以上：**

```ubnf
Program ::= { VariableDeclaration } Expression ;
```

---

### 要素記法

| 記法 | 意味 | 生成コード |
|---|---|---|
| `'literal'` | リテラル文字列 | `new WordParser("literal")` |
| `RuleName` | ルール参照 | `Parser.get(RuleNameParser.class)` |
| `TOKEN` | トークン参照（`token` 宣言あり） | `Parser.get(TokenParserClass.class)` |
| `( A \| B )` | グループ（選択） | ヘルパークラス `extends LazyChoice` |
| `[ A ]` | Optional（0 または 1 回） | `new Optional(...)` |
| `{ A }` | 繰り返し（0 回以上） | `new ZeroOrMore(...)` |

**キャプチャ名 `@name`：**

要素の末尾に `@名前` を付けると、AST レコードのフィールドとして対応付けられる。

```ubnf
Expression ::= Term @left { ( '+' @op | '-' @op ) Term @right } ;
```

この例では：
- `Term @left` → `left` フィールド
- `'+' @op` → `op` フィールド
- `Term @right` → `right` フィールド

`@mapping` アノテーションの `params` に対応するキャプチャ名を並べる。

---

### アノテーション

ルール宣言の直前に付ける。

| アノテーション | 説明 |
|---|---|
| `@root` | このルールがパースのエントリーポイント（ルート）であることを宣言。`getRootParser()` が返すクラスになる |
| `@mapping(ClassName)` | パースツリーをマップする AST クラス名を指定 |
| `@mapping(ClassName, params=[a, b, c])` | AST クラス名とフィールド名を指定。フィールド型はキャプチャ名から自動推論される |
| `@leftAssoc` | 左結合演算子であることを宣言（現時点ではパーサー生成に影響しないが意図を記録する） |
| `@whitespace` | このルールの空白処理を個別に制御（オプション） |

---

## コードジェネレーターの使い方

すべてのジェネレーターは `CodeGenerator` インターフェースを実装している。

```java
public interface CodeGenerator {
    GeneratedSource generate(GrammarDecl grammar);

    record GeneratedSource(
        String packageName,  // 生成コードのパッケージ名
        String className,    // 生成クラス名
        String source        // Java ソースコード全文
    ) {}
}
```

**使用例：**

```java
GrammarDecl grammar = UBNFMapper.parse(ubnfSource).grammars().get(0);

// AST 生成
var ast = new ASTGenerator().generate(grammar);
// ast.packageName() → "org.unlaxer.tinycalc.generated"
// ast.className()   → "TinyCalcAST"
// ast.source()      → "package org.unlaxer...public sealed interface TinyCalcAST..."

// パーサー生成
var parsers = new ParserGenerator().generate(grammar);

// マッパー生成
var mapper = new MapperGenerator().generate(grammar);

// エバリュエーター生成
var evaluator = new EvaluatorGenerator().generate(grammar);

// LSP サーバー生成
var lspServer  = new LSPGenerator().generate(grammar);
// LSP ランチャー生成
var lspLauncher = new LSPLauncherGenerator().generate(grammar);
```

---

## 生成物の詳細（TinyCalc 例）

以下の文法（`examples/tinycalc.ubnf`）を使って各ジェネレーターの出力を説明する。

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

`TinyCalcAST.java` を生成する。`@mapping` アノテーション付きルールを収集し、sealed interface + record として出力する。

**生成される `TinyCalcAST.java`：**

```java
package org.unlaxer.tinycalc.generated;

import java.util.List;
import java.util.Optional;

public sealed interface TinyCalcAST permits
    TinyCalcAST.TinyCalcProgram,
    TinyCalcAST.VarDecl,
    TinyCalcAST.BinaryExpr {

    // { VariableDeclaration } @declarations → List<TinyCalcAST.VarDecl>
    // Expression @expression → TinyCalcAST.BinaryExpr（Expression は @mapping(BinaryExpr) なので）
    record TinyCalcProgram(
        List<TinyCalcAST.VarDecl> declarations,
        TinyCalcAST.BinaryExpr expression
    ) implements TinyCalcAST {}

    // ( 'var' | 'variable' ) @keyword → グループ要素のため Object
    // IDENTIFIER @name → トークン参照のため String
    // [ 'set' Expression @init ] @init → Optional<TinyCalcAST.BinaryExpr>
    record VarDecl(
        Object keyword,
        String name,
        Optional<TinyCalcAST.BinaryExpr> init
    ) implements TinyCalcAST {}

    // Expression と Term の両方に @mapping(BinaryExpr) があるが record は1つだけ生成
    record BinaryExpr(
        TinyCalcAST.BinaryExpr left,
        String op,
        TinyCalcAST.BinaryExpr right
    ) implements TinyCalcAST {}
}
```

**フィールド型の推論ルール：**

| 文法要素 | 推論された型 |
|---|---|
| `{ RuleName } @field`（繰り返し内の @mapping 付きルール参照） | `List<TinyCalcAST.ClassName>` |
| `[ RuleName ] @field`（Optional 内の @mapping 付きルール参照） | `Optional<TinyCalcAST.ClassName>` |
| `RuleName @field`（@mapping 付きルール参照） | `TinyCalcAST.ClassName` |
| `TOKEN @field`（トークン参照） | `String` |
| `'literal' @field`（終端記号） | `String` |
| `( A \| B ) @field`（グループ要素） | `Object` |

---

### ParserGenerator

`TinyCalcParsers.java` を生成する。unlaxer-common のパーサーコンビネータを使って、文法に対応するパーサークラス群を出力する。

**生成される `TinyCalcParsers.java` の構造：**

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

    // --- Whitespace Delimitor ---
    // @whitespace: javaStyle の設定から生成
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
    // 各シーケンスパーサーの基底クラス。パーサー間に自動的に空白をスキップする
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

    // --- ヘルパークラス（複合要素の展開）---

    // VariableDeclaration の ( 'var' | 'variable' ) から生成
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

    // VariableDeclaration の [ 'set' Expression @init ] から生成
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

    // Expression の { ( '+' @op | '-' @op ) Term @right } から生成
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

    // --- ルールクラス ---

    // @root ルール
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

    // Factor は3択の ChoiceBody なので LazyChoice を継承
    // 第1候補 '(' Expression ')' は複数要素なので匿名の TinyCalcLazyChain に
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

    // --- ファクトリ ---
    public static Parser getRootParser() {
        return Parser.get(TinyCalcParser.class);
    }
}
```

**要素変換ルール：**

| 文法要素 | 生成コード |
|---|---|
| `'var'` | `new WordParser("var")` |
| `NUMBER`（token 宣言あり） | `Parser.get(NumberParser.class)` |
| `Expression`（ルール参照） | `Parser.get(ExpressionParser.class)` |
| `{ VariableDeclaration }`（単一 RuleRef の繰り返し） | `new ZeroOrMore(VariableDeclarationParser.class)` |
| `{ ( '+' \| '-' ) Term }`（複合 body の繰り返し） | ヘルパークラス + `new ZeroOrMore(ExpressionRepeat0Parser.class)` |
| `[ 'set' Expression ]`（複合 body の Optional） | ヘルパークラス + `new Optional(VariableDeclarationOpt0Parser.class)` |
| `( 'var' \| 'variable' )`（グループ） | ヘルパークラス `extends LazyChoice` + `Parser.get(VariableDeclarationGroup0Parser.class)` |

**ヘルパークラスの命名規則：**

| パターン | クラス名 |
|---|---|
| `{ 複合body }`（繰り返し） | `{RuleName}Repeat{N}Parser` |
| `[ 複合body ]`（Optional） | `{RuleName}Opt{N}Parser` |
| `( body )`（グループ） | `{RuleName}Group{N}Parser` |

N はルール内での 0 始まり連番。

---

### MapperGenerator

`TinyCalcMapper.java` を生成する。`@mapping` 付きルールに対応する `to{ClassName}(Token)` メソッドのスケルトンを出力する。

**生成される `TinyCalcMapper.java` の構造：**

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
     * TinyCalc ソース文字列をパースして AST に変換する。
     * NOTE: TinyCalcParsers が生成・配置されてから実装を完成させる。
     */
    public static TinyCalcAST.TinyCalcProgram parse(String source) {
        // TODO: TinyCalcParsers が生成されたら実装する
        // StringSource stringSource = StringSource.createRootSource(source);
        // try (ParseContext context = new ParseContext(stringSource)) {
        //     Parser rootParser = TinyCalcParsers.getRootParser();
        //     Parsed parsed = rootParser.parse(context);
        //     if (!parsed.isSucceeded()) {
        //         throw new IllegalArgumentException("パース失敗: " + source);
        //     }
        //     return toTinyCalcProgram(parsed.getRootToken());
        // }
        throw new UnsupportedOperationException("TinyCalcParsers: 未実装");
    }

    // --- 変換メソッド（スケルトン） ---

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

    // BinaryExpr は Expression と Term の両方に @mapping があるが、1つだけ生成
    static TinyCalcAST.BinaryExpr toBinaryExpr(Token token) {
        // TODO: extract left, op, right
        return new TinyCalcAST.BinaryExpr(null, null, null);
    }

    // --- ユーティリティ ---

    /** 指定パーサークラスの子孫 Token を深さ優先で探す */
    static List<Token> findDescendants(Token token, Class<? extends Parser> parserClass) { ... }

    /** シングルクォートで囲まれた文字列から引用符を除去する */
    static String stripQuotes(String quoted) { ... }
}
```

**生成後の作業（手動実装箇所）：**

`TinyCalcMapper` の `to{ClassName}` メソッドは TODO コメント付きのスケルトンとして生成される。`findDescendants()` を使って実際のフィールド抽出ロジックを実装する。

```java
// 実装例
static TinyCalcAST.BinaryExpr toBinaryExpr(Token token) {
    // 左辺 Factor または再帰的 BinaryExpr を取得
    List<Token> leftTokens = findDescendants(token, TermParser.class);
    // op（'+'/'-') を取得
    // right を取得
    ...
}
```

---

### EvaluatorGenerator

`TinyCalcEvaluator.java` を生成する。型パラメーター `<T>` を持つ抽象クラスで、AST ノードの型に応じたメソッドをオーバーライドして評価ロジックを実装する。

**生成される `TinyCalcEvaluator.java` の構造：**

```java
package org.unlaxer.tinycalc.generated;

public abstract class TinyCalcEvaluator<T> {

    private DebugStrategy debugStrategy = DebugStrategy.NOOP;

    public void setDebugStrategy(DebugStrategy strategy) {
        this.debugStrategy = strategy;
    }

    /** パブリックエントリーポイント。デバッグフックを挟んで evalInternal を呼ぶ */
    public T eval(TinyCalcAST node) {
        debugStrategy.onEnter(node);
        T result = evalInternal(node);
        debugStrategy.onExit(node, result);
        return result;
    }

    /** sealed switch によるディスパッチ */
    private T evalInternal(TinyCalcAST node) {
        return switch (node) {
            case TinyCalcAST.TinyCalcProgram n -> evalTinyCalcProgram(n);
            case TinyCalcAST.VarDecl n        -> evalVarDecl(n);
            case TinyCalcAST.BinaryExpr n     -> evalBinaryExpr(n);
        };
    }

    // 各 @mapping クラスに対応する抽象メソッド
    protected abstract T evalTinyCalcProgram(TinyCalcAST.TinyCalcProgram node);
    protected abstract T evalVarDecl(TinyCalcAST.VarDecl node);
    protected abstract T evalBinaryExpr(TinyCalcAST.BinaryExpr node);    // Expression と Term で共有

    // --- デバッグ用ストラテジー ---

    public interface DebugStrategy {
        void onEnter(TinyCalcAST node);
        void onExit(TinyCalcAST node, Object result);

        DebugStrategy NOOP = new DebugStrategy() {
            public void onEnter(TinyCalcAST node) {}
            public void onExit(TinyCalcAST node, Object result) {}
        };
    }

    /** eval() の呼び出し回数をカウントするデバッグ実装 */
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

**エバリュエーターの実装例（四則演算の評価）：**

```java
public class TinyCalcCalculator extends TinyCalcEvaluator<Double> {

    @Override
    protected Double evalTinyCalcProgram(TinyCalcAST.TinyCalcProgram node) {
        // 変数宣言を処理してから最終式を評価
        node.declarations().forEach(d -> eval(d));
        return eval(node.expression());
    }

    @Override
    protected Double evalVarDecl(TinyCalcAST.VarDecl node) {
        // 変数を環境に登録（実装省略）
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

### LSPGenerator

`{Name}LanguageServer.java` を生成する。lsp4j を使った LSP サーバーで、以下の機能をスケルトンとして含む。

| 機能 | 実装内容 |
|---|---|
| `initialize` | TextDocumentSync.Full + Completion + Hover + SemanticTokens |
| `completion` | grammar の `TerminalElement` から自動抽出したキーワード一覧を返す |
| `hover` | パース成功時は `"Valid {Name}"`、失敗時は `"Parse error at offset N"` |
| `semanticTokensFull` | 有効範囲（type=0 緑）+ 無効範囲（type=1 赤）の 2 トークン |
| `didOpen / didChange` | `{Name}Parsers.getRootParser()` でパースし診断（Diagnostic）を publish |

**生成される `TinyCalcLanguageServer.java` の主要部分：**

```java
public class TinyCalcLanguageServer implements LanguageServer, LanguageClientAware {

    private static final List<String> KEYWORDS =
        List.of("var", "variable", "set", "(", ")", ";", "+", "-", "*", "/");

    public ParseResult parseDocument(String uri, String content) {
        Parser parser = TinyCalcParsers.getRootParser();
        ParseContext context = new ParseContext(StringSource.createRootSource(content));
        Parsed result = parser.parse(context);
        // ...publishDiagnostics
    }

    static class TinyCalcLanguageServerTextDocumentService implements TextDocumentService {
        // didOpen, didChange, completion, hover, semanticTokensFull ...
    }
    static class TinyCalcLanguageServerWorkspaceService implements WorkspaceService { ... }
}
```

---

### LSPLauncherGenerator

`{Name}LspLauncher.java` を生成する。stdio 経由で LSP サーバーを起動する `main` クラス。

```java
public class TinyCalcLspLauncher {
    public static void main(String[] args) throws IOException {
        TinyCalcLanguageServer server = new TinyCalcLanguageServer();
        Launcher<LanguageClient> launcher =
            LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}
```

---

## CodegenMain — CLI ツール

`CodegenMain` は `.ubnf` ファイルを読み込んで指定したジェネレーターを一括実行し、Java ソースをファイルに書き出す CLI ツール。

```bash
java -cp unlaxer-dsl.jar org.unlaxer.dsl.CodegenMain \
  --grammar path/to/my.ubnf \
  --output  src/main/java \
  --generators Parser,LSP,Launcher
```

| オプション | 説明 | デフォルト |
|---|---|---|
| `--grammar <file>` | `.ubnf` ファイルのパス | （必須） |
| `--output <dir>` | 出力ルートディレクトリ（package 構造で書き出す） | （必須） |
| `--generators <list>` | カンマ区切りの生成器名 | `Parser,LSP,Launcher` |

使用可能な生成器名: `AST`, `Parser`, `Mapper`, `Evaluator`, `LSP`, `Launcher`

---

## VS Code 拡張（VSIX）のビルド

`tinycalc-vscode/` ディレクトリが TinyCalc 言語の VS Code 拡張サンプルを含む。
`mvn verify` 1 コマンドで **文法定義 → コード生成 → fat jar → VSIX** までを自動実行する。

### 前提

```bash
# unlaxer-dsl を Maven ローカルリポジトリにインストール（初回のみ）
cd unlaxer-dsl
mvn install -DskipTests
```

### ビルド

```bash
cd tinycalc-vscode
mvn verify
# → target/tinycalc-lsp-0.1.0.vsix
```

### Maven フェーズ別の処理内容

| フェーズ | 処理 | 出力先 |
|---|---|---|
| `generate-sources` | `CodegenMain` が `grammar/tinycalc.ubnf` を読み込み Java ソースを生成 | `target/generated-sources/tinycalc/` |
| `compile` | 生成された `TinyCalcParsers`, `TinyCalcLanguageServer`, `TinyCalcLspLauncher` をコンパイル | `target/classes/` |
| `package` | `maven-shade-plugin` で fat jar を作成し `server-dist/` にコピー | `target/tinycalc-lsp-server.jar` |
| `verify` | `npm install` → `npx vsce package`（内部で TypeScript コンパイルも実行） | `target/tinycalc-lsp-0.1.0.vsix` |

### インストール

```bash
code --install-extension tinycalc-vscode/target/tinycalc-lsp-0.1.0.vsix
```

`.tcalc` ファイルを開くと LSP サーバーが自動起動する。

---

## TinyCalc チュートリアル

TinyCalc は変数宣言と四則演算をサポートする小さな計算機 DSL である。以下は文法定義から評価までの流れ。

### ステップ 1: 文法を定義する

`examples/tinycalc.ubnf` を参照（前述の TinyCalc 文法）。

### ステップ 2: コードを生成する

```java
String src = Files.readString(Path.of("examples/tinycalc.ubnf"));
GrammarDecl grammar = UBNFMapper.parse(src).grammars().get(0);

// 4 種類のコードを生成して src/main/java/org/unlaxer/tinycalc/generated/ に保存
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

### ステップ 3: TinyCalcMapper の parse() を実装する

生成された `TinyCalcMapper.java` の `parse()` メソッドの TODO 部分を完成させる。

```java
public static TinyCalcAST.TinyCalcProgram parse(String source) {
    StringSource stringSource = StringSource.createRootSource(source);
    try (ParseContext context = new ParseContext(stringSource)) {
        Parser rootParser = TinyCalcParsers.getRootParser();
        Parsed parsed = rootParser.parse(context);
        if (!parsed.isSucceeded()) {
            throw new IllegalArgumentException("パース失敗: " + source);
        }
        return toTinyCalcProgram(parsed.getRootToken());
    }
}
```

### ステップ 4: エバリュエーターを実装して評価する

```java
TinyCalcAST.TinyCalcProgram ast = TinyCalcMapper.parse("1 + 2 * 3");
TinyCalcCalculator calc = new TinyCalcCalculator();
Double result = calc.eval(ast);
System.out.println(result); // 7.0
```

---

## プロジェクト構造

```
unlaxer-dsl/
├── grammar/
│   └── ubnf.ubnf              UBNF 自身を UBNF で記述した自己ホスティング文法
├── examples/
│   └── tinycalc.ubnf          TinyCalc サンプル文法
├── src/
│   ├── main/java/org/unlaxer/dsl/
│   │   ├── CodegenMain.java       CLI ツール（ubnf → Java ソース一括生成）
│   │   ├── bootstrap/
│   │   │   ├── UBNFAST.java       UBNF の AST（sealed interface + record）
│   │   │   ├── UBNFParsers.java   Bootstrap パーサー（手書き）
│   │   │   └── UBNFMapper.java    パースツリー → AST マッパー
│   │   └── codegen/
│   │       ├── CodeGenerator.java         共通インターフェース
│   │       ├── ASTGenerator.java          XxxAST.java 生成器
│   │       ├── ParserGenerator.java       XxxParsers.java 生成器
│   │       ├── MapperGenerator.java       XxxMapper.java 生成器
│   │       ├── EvaluatorGenerator.java    XxxEvaluator.java 生成器
│   │       ├── LSPGenerator.java          XxxLanguageServer.java 生成器
│   │       └── LSPLauncherGenerator.java  XxxLspLauncher.java 生成器
│   └── test/java/org/unlaxer/dsl/
│       ├── UBNFParsersTest.java
│       ├── UBNFMapperTest.java
│       └── codegen/
│           ├── ASTGeneratorTest.java
│           ├── ParserGeneratorTest.java
│           ├── MapperGeneratorTest.java
│           ├── EvaluatorGeneratorTest.java
│           ├── LSPGeneratorTest.java
│           ├── LSPLauncherGeneratorTest.java
│           └── LSPCompileVerificationTest.java
├── tinycalc-vscode/           VS Code 拡張サンプル（TinyCalc）
│   ├── pom.xml                Maven ビルド設定（codegen → compile → jar → VSIX）
│   ├── grammar/
│   │   └── tinycalc.ubnf      拡張のソース文法（CodegenMain への入力）
│   ├── src/
│   │   └── extension.ts       VS Code クライアント（TypeScript）
│   ├── syntaxes/
│   │   └── tinycalc.tmLanguage.json  TextMate 文法（シンタックスハイライト）
│   ├── language-configuration.json
│   ├── package.json
│   └── target/                ← ビルド成果物（gitignore）
│       ├── generated-sources/ ← 生成 Java ソース
│       ├── tinycalc-lsp-server.jar  ← fat jar
│       └── tinycalc-lsp-0.1.0.vsix ← VS Code 拡張パッケージ
└── pom.xml
```

---

## ロードマップ

| フェーズ | 内容 | 状態 |
|---|---|---|
| Phase 0 | UBNF 文法定義（`grammar/ubnf.ubnf`） | 完了 |
| Phase 1 | Bootstrap パーサー（`UBNFParsers.java`） | 完了 |
| Phase 2 | AST 定義・Bootstrap マッパー（`UBNFAST.java`, `UBNFMapper.java`） | 完了 |
| Phase 3 | ASTGenerator / EvaluatorGenerator / MapperGenerator 実装 | 完了 |
| Phase 4 | ParserGenerator 実装 | 完了 |
| Phase 5 | TinyCalc 統合テスト（`TinyCalcIntegrationTest`） | 完了 |
| Phase 6 | 自己ホスティングテスト（`SelfHostingTest`） | 完了 |
| Phase 7 | コンパイル検証テスト（`CompileVerificationTest`） | 完了 |
| Phase 8 | LSP サーバー自動生成（`LSPGenerator`, `LSPLauncherGenerator`, `CodegenMain`） | 完了 |
| Phase 9 | VSIX ワンコマンドビルド（`tinycalc-vscode/pom.xml`） | 完了 |
| Phase 10 | 自己ホスティング（`grammar/ubnf.ubnf` から自分自身を生成） | 未着手 |
| Phase 11 | DAP サポート自動生成 | 未着手 |

---

## ライセンス

MIT License — Copyright (c) 2026 opaopa6969
