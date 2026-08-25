---
kind: engine-benchmark
repo: opaopa6969/unlaxer-dsl
profile: engine-benchmark
measured_at: 2026-08-16
commit_base: 452dad3 (fix/issue-3-validation-error-codes)
jdk: 21.0.9+7-LTS-338 (Temurin Linux x64)
os: linux
---

# Engine Benchmark 2026-08-16

## 目的

現行の codegen パイプライン性能を再現可能な形で測定し、改善仮説を検証する。

## 測定方法

- 対象 grammar: `SnapshotFixtureData.SNAPSHOT_GRAMMAR`（最小の算術式 DSL、
  `token NUMBER = NumberParser`, Expr/Term/Factor の3ルール）。
- pipeline: `UBNFMapper.parse(grammar)` → 8 generator (`AST`, `Parser`, `Mapper`,
  `Evaluator`, `LSP`, `Launcher`, `DAP`, `DAPLauncher`) で `generate(GrammarDecl)`。
- JVM 21.0.9 LTS, `--enable-preview`。warmup 200 / iters 500 の単純マイクロベンチ。
  JMH ではなく `System.nanoTime` の簡易計測（反復可能、コードは
  `/tmp/benchsrc/org/unlaxer/dsl/codegen/Bench6.java` 相当）。

## Baseline (memoize=false)

| ステップ                 | ns/op      | us/op    | 備考                       |
|--------------------------|------------|----------|----------------------------|
| parse (UBNFMapper.parse) | 16,539,295 | 16,539   | パイプラインの約 89%      |
| ASTGenerator             |     81,933 |     82   |                            |
| ParserGenerator          |    205,777 |    206   | 最大                       |
| MapperGenerator          |    117,883 |    118   |                            |
| DAPGenerator             |    145,458 |    145   |                            |
| LSPGenerator             |     33,034 |     33   |                            |
| EvaluatorGenerator       |     10,771 |     11   |                            |
| LSPLauncherGenerator     |      4,764 |      5   |                            |
| DAPLauncherGenerator     |      3,620 |      4   |                            |
| **TOTAL (parse + 8 gen)**| **17,707,367** | **17,707** | baseline             |

### 内訳の観察

- **パース処理が 89% を占める**。generator 8個の合計は約 2,100 us/op で全体の 11%。
- `parse` の内訳:
  - raw parse (`ParseContext` + `rootParser.parse`): 15,360 us/op
  - mapper (Token木 → UBNFAST): 約 320 us/op（全体の 2%、無視できる）
  - `ParseContext` / `StringSource` 生成: 各 70-80 us/op（無視できる）
- つまり **`unlaxer-common` の `Parser.parse` がパイプライン全体の 82%**。
  DSL 側の generator 改善では頭打ち。

## 反復1の仮説: packrat memoization を有効化

### 仮説

`unlaxer-common` の `ParseContext` は opt-in の packrat memoization
（`enableMemoize()`）を持つ。`UBNFParsers` はパーサーコンビネータで、
`Choice` や `ZeroOrMore` のバックトラックが多く、packrat memoization は
PEG パーサーの標準最適化（Ford 2002）。

### 実装

- `UBNFMapper.parse(String, boolean memoize)` を追加（`context.enableMemoize()` を呼ぶ）。
- `CodegenRunner` から `UBNFMapper.parse(source, true)` で呼び出し、
  CLI の codegen パイプラインで memoize を有効化。
- `UBNFMapper.parse(String)` のデフォルトは `false` のまま（テスト互換性維持）。

### 結果

| 構成                   | ns/op      | us/op    | 改善率  |
|------------------------|------------|----------|--------|
| baseline (memoize=false) | 17,707,367 | 17,707 | -      |
| improved (memoize=true)  | 10,809,738 | 10,810 | **39.0%** |

parse 単体の比較:
- without memo: 16,539 us/op
- with memo:     9,923 us/op （**40.0% 短縮**）

### 安全性の検証

- **出力への影響**: memoize あり・なしで全テストの actual が完全一致することを確認。
  特に `CodegenSnapshotTest`, `MapperGeneratorTest`, `ParserGeneratorTest`,
  `SelfHostingTest`, `SelfHostingRoundTripTest`, `SnapshotFixtureGoldenConsistencyTest`
  の actual を diff で比較し、差分なし。
- **失敗テスト数**: memoize あり・なしで同じ 10 テストが失敗（既存の失敗）。
  新たな破壊なし。
- `CodegenMainTest` (76 テスト) は全て成功。

## 外部比較データ

| 製品       | パース方式           | 公開ベンチデータ | ライセンス    | URL                                                  | 取得日     |
|------------|----------------------|------------------|---------------|------------------------------------------------------|------------|
| ANTLR 4    | ALL(*) 適応的 LL     | repo 公式なし     | BSD-3-Clause  | https://github.com/antlr/antlr4                      | 2026-08-16 |
| Tree-sitter | GLR + インクリメンタル | 公式は C11 で "every keystroke" | MIT | https://tree-sitter.github.io/tree-sitter/ | 2026-08-16 |
| JavaCC     | LL(k)                | repo 公式なし     | BSD-2-Clause  | https://github.com/javacc/javacc                     | 2026-08-16 |

ANTLR/Tree-sitter/JavaCC はいずれも batch codegen のパース性能として直接比較できる
公開ベンチマークを提供していない。packrat memoization は PEG パーサーの標準手法
（Ford, "Packrat Parsing: a Practical Linear-Time Algorithm with Backtracking",
  ICFP 2002）で、unlaxer-common が既に実装を持つ。

出典:
- Tree-sitter Introduction: https://tree-sitter.github.io/tree-sitter/ （取得日 2026-08-16, ライセンス MIT）
- ANTLR4 repo: https://github.com/antlr/antlr4 （取得日 2026-08-16, ライセンス BSD-3-Clause）
- market 調査 doc: `docs/market/2026-07-31.md` (Codex 調査)

## 再現手順

```sh
export PATH="$HOME/.sdkman/candidates/maven/current/bin:$PATH"
mvn -q -DskipTests test-compile
mvn -q -DincludeScope=test -Dmdep.outputFile=/tmp/unlaxer-dsl-test-cp.txt dependency:build-classpath
CP="target/classes:target/test-classes:$(cat /tmp/unlaxer-dsl-test-cp.txt)"
# Bench6.java を /tmp/benchsrc/org/unlaxer/dsl/codegen/ に配置（内容は本文参照）
javac --release 21 --enable-preview -cp "$CP" -d /tmp/benchsrc /tmp/benchsrc/org/unlaxer/dsl/codegen/Bench6.java
java --enable-preview -cp "$CP:/tmp/benchsrc" org.unlaxer.dsl.codegen.Bench6
```

## 反復1のサマリ

- **観測事実**: parse がパイプラインの 89%。generator 合計は 11%。
- **仮説**: packrat memoization を有効化。
- **実施内容**: `UBNFMapper.parse(String, boolean)` 追加、`CodegenRunner` で
  `parse(source, true)` を呼び出し。
- **検証結果**: 17,707 → 10,810 us/op（**39.0% 改善**）。出力への影響なし。
- **次の判断**: 反復2 では generator 側（ParserGenerator 206us が最大）の
  最適化余地を調査。ただしパイプライン全体から見ると限界は薄い。
