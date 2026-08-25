# 文法バリデーション仕様

> ステータス: draft
> 最終更新: 2026-08-01

## スコープ

このドキュメントは UBNF 文法のバリデーション仕様を定義する。すべてのエラーコード、重大度、カテゴリ、ValidationIssue の形式を含む。

このドキュメントは `GrammarValidator.java`（`org.unlaxer.dsl.codegen.GrammarValidator`）の実装を単一真実の参照元とする。コード値・メッセージ・ヒント・カテゴリ分類は実装に一致しなければならない（MUST）。実装と乖離した場合は実装が優先される。

このドキュメントが **扱わない** 範囲:
- アノテーションのセマンティクス詳細（→ [annotations.md](annotations.md)）
- CLI のバリデーションモード（→ [cli.md](cli.md)）

## 関連ドキュメント

- [annotations.md](annotations.md) — 各アノテーションの契約
- [cli.md](cli.md) — `--validate-only`, `--strict` 等の CLI オプション
- `src/main/java/org/unlaxer/dsl/codegen/GrammarValidator.java` — コード値・メッセージ・ヒントの参照元

---

## GrammarValidator

**クラス**: `org.unlaxer.dsl.codegen.GrammarValidator`

文法レベルのセマンティック制約を検証する。ジェネレータが依存する制約を事前に検証し、明確なエラーを報告する。

### API

| メソッド | 動作 |
|---------|------|
| `validate(GrammarDecl)` | `List<ValidationIssue>` を返す（スローしない） |
| `validateOrThrow(GrammarDecl)` | バリデーションエラーがある場合、例外をスローする |

### 検証フェーズ

`validate(GrammarDecl)` は以下の順序で実行される:

1. `validateGlobalWhitespace` — grammar 設定の `@whitespace` 検証
2. `validateRootPresence` — `@root` ルールの存在検証
3. 各ルールごと:
   - `validateMapping` — `@mapping` の制約
   - `validateAssoc` — `@leftAssoc` / `@rightAssoc` の制約
   - `validatePrecedence` — `@precedence` の制約
   - `validateAdvancedAnnotations` — `@interleave` / `@backref` / `@scopeTree` の制約
   - `validateRuleWhitespace` — ルールレベル `@whitespace` の制約
4. `validatePrecedenceTopology` — 演算子ルール間の優先度順序検証
5. `validateAssociativityConsistency` — 優先度レベルごとの結合性一致性検証

---

## ValidationIssue

**record**: `GrammarValidator.ValidationIssue`

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `code` | `String` | エラーコード（例: `E-MAPPING-MISSING-CAPTURE`） |
| `message` | `String` | 人間可読なエラーメッセージ |
| `hint` | `String` | 修正ヒント |
| `rule` | `String` | 対象ルール名（nullable。grammar 全体エラーは null） |

### 導出プロパティ

| メソッド | ロジック |
|---------|--------|
| `severity()` | コードが `W-` で始まる場合 `"WARNING"`、それ以外 `"ERROR"` |
| `category()` | コードのプレフィックスに基づくカテゴリ判定（下表参照） |
| `format()` | `message [code: ...] [hint: ...]` 形式の文字列 |

---

## 重大度（Severity）

| 値 | 条件 | 意味 |
|----|------|------|
| `ERROR` | コードが `E-` で始まる | コード生成を中断すべきエラー |
| `WARNING` | コードが `W-` で始まる | コード生成は可能だが注意が必要 |

---

## カテゴリ（Category）

`category()` は以下のプレフィックス順で判定される（実装の `ValidationIssue.category()` に一致）:

| カテゴリ | コードプレフィックス | 対象 |
|---------|-------------------|------|
| `MAPPING` | `E-MAPPING-` | @mapping アノテーションの制約 |
| `ASSOCIATIVITY` | `E-ASSOC-`, `E-RIGHTASSOC-` | @leftAssoc / @rightAssoc の制約 |
| `WHITESPACE` | `E-WHITESPACE-` | @whitespace の制約 |
| `PRECEDENCE` | `E-PRECEDENCE-` | @precedence の制約 |
| `ANNOTATION` | `E-ANNOTATION-` | アノテーション全般の制約 |
| `GENERAL` | その他（`W-` を含む） | 一般的な制約 |

> 注: `W-GENERAL-NO-ROOT` は `GENERAL` カテゴリに分類される。

---

## エラーコード一覧

実装（`GrammarValidator.java`）に存在する全 26 のエラーコード（`E-`）と 1 の警告コード（`W-`）を以下に列挙する。コード値・条件・メッセージ・ヒントは実装に一致する。

### GENERAL（警告）

| コード | 重大度 | トリガー条件 | メッセージ（実装準拠） | ヒント（実装準拠） |
|--------|--------|------------|----------------------|-------------------|
| `W-GENERAL-NO-ROOT` | `WARNING` | grammar に `@root` ルールが1つも存在しない | `grammar <name> has no @root rule` | `Add @root to at least one entry rule.` |

> 注: `rule` フィールドは null（grammar 全体エラー）。

### MAPPING エラー

`validateMapping` により報告される。

| コード | トリガー条件 | メッセージ（実装準拠） | ヒント（実装準拠） |
|--------|------------|----------------------|-------------------|
| `E-MAPPING-DUPLICATE-PARAM` | `@mapping` の `params` に重複するパラメータ名がある | `rule <rule> @mapping(<class>) has duplicate params: <duplicates>` | `Remove duplicate parameter names in @mapping params.` |
| `E-MAPPING-MISSING-CAPTURE` | `@mapping` の `params` に記載されたキャプチャ名がルール本体に存在しない | `rule <rule> @mapping(<class>) param '<param>' has no matching capture` | `Add @<param> capture in the rule body or remove it from params.` |
| `E-MAPPING-UNLISTED-CAPTURE` | ルール本体のキャプチャ名が `@mapping` の `params` に含まれていない | `rule <rule> has capture @<capture> not listed in @mapping(<class>) params` | `Add '<capture>' to @mapping params.` |

### ASSOCIATIVITY エラー

`validateAssoc` により報告される。`<assoc>` は `@rightAssoc` 使用時は `@rightAssoc`、それ以外は `@leftAssoc`。

| コード | トリガー条件 | メッセージ（実装準拠） | ヒント（実装準拠） |
|--------|------------|----------------------|-------------------|
| `E-ASSOC-BOTH` | `@leftAssoc` と `@rightAssoc` が同一ルールに使用されている | `rule <rule> cannot use both @leftAssoc and @rightAssoc` | `Keep exactly one associativity annotation per rule.` |
| `E-ASSOC-NO-MAPPING` | `@leftAssoc` / `@rightAssoc` 使用時に `@mapping` がない | `rule <rule> uses <assoc> but has no @mapping` | `Add @mapping(ClassName, params=[left, op, right]) to this rule.` |
| `E-ASSOC-MAPPING-PARAM` | `@leftAssoc` / `@rightAssoc` 使用時に `@mapping` の `params` が `left` / `op` / `right` を含まない | `rule <rule> uses <assoc> but @mapping(<class>) params does not contain '<required>'` | `Include left/op/right in @mapping params.` |
| `E-ASSOC-MISSING-CAPTURE` | `@leftAssoc` / `@rightAssoc` 使用時に `@left` / `@op` / `@right` キャプチャが不足 | `rule <rule> uses <assoc> but capture @<required> is missing` | `Add @<required> capture in the rule body.` |
| `E-ASSOC-NO-REPEAT` | `@leftAssoc` / `@rightAssoc` 使用時に repeat セグメントがない | `rule <rule> uses <assoc> but has no repeat segment` | `Use canonical operator pattern: Base { Op Right }.` |
| `E-RIGHTASSOC-NONCANONICAL` | `@rightAssoc` 使用時にルール本体が正規形（`Base { Op Self }`）でない | `rule <rule> uses @rightAssoc but body is not canonical: expected Base { Op <rule> }` | `Rewrite right-assoc rule as Base { op <rule> }.` |
| `E-ASSOC-NO-PRECEDENCE` | `@leftAssoc` / `@rightAssoc` 使用時に `@precedence` がない（`validateAssociativityConsistency`） | `rule <rule> uses @<assoc>Assoc but has no @precedence` | `Add @precedence(level=...) to this operator rule.` |

### WHITESPACE エラー

`validateGlobalWhitespace`（grammar 設定）と `validateRuleWhitespace`（ルールレベル）により報告される。

| コード | トリガー条件 | メッセージ（実装準拠） | ヒント（実装準拠） |
|--------|------------|----------------------|-------------------|
| `E-WHITESPACE-GLOBAL-STYLE` | grammar 設定の `@whitespace` が `javaStyle` 以外 | `global @whitespace style must be javaStyle: <style>` | `Use '@whitespace: javaStyle'.` |
| `E-WHITESPACE-RULE-STYLE` | ルールレベル `@whitespace` が `javaStyle` / `none` 以外 | `rule <rule> uses unsupported @whitespace style: <style> (allowed: javaStyle, none)` | `Use @whitespace or @whitespace(none).` |

> 注: `E-WHITESPACE-GLOBAL-STYLE` は `rule` フィールドが null（grammar 全体エラー）。

### PRECEDENCE エラー

`validatePrecedence`、`validatePrecedenceTopology`、`validateAssociativityConsistency` により報告される。

| コード | トリガー条件 | メッセージ（実装準拠） | ヒント（実装準拠） |
|--------|------------|----------------------|-------------------|
| `E-PRECEDENCE-DUPLICATE` | 同一ルールに `@precedence` が複数回宣言されている | `rule <rule> has duplicate @precedence annotations` | `Keep a single @precedence(level=...) annotation.` |
| `E-PRECEDENCE-NEGATIVE` | `@precedence(level=...)` が負の値 | `rule <rule> has invalid @precedence level: <level>` | `Use a non-negative integer (e.g. @precedence(level=10)).` |
| `E-PRECEDENCE-NO-ASSOC` | `@precedence` が `@leftAssoc` / `@rightAssoc` なしで使用されている | `rule <rule> uses @precedence but has no @leftAssoc/@rightAssoc` | `Add one associativity annotation alongside @precedence.` |
| `E-PRECEDENCE-ORDER` | 演算子ルールが参照先ルールより優先度が低くない（参照先 `<=` 自身） | `rule <rule> precedence <level> must be lower than referenced operator rule <ref> precedence <refLevel>` | `Decrease <rule> level or increase <ref> level.` |
| `E-PRECEDENCE-MIXED-ASSOC` | 同一優先度レベルで左結合と右結合が混在している | `precedence level <level> mixes associativity: <existing> and <assoc>` | `Use one associativity per precedence level.` |

### ANNOTATION エラー

`validateAdvancedAnnotations` により報告される。

| コード | トリガー条件 | メッセージ（実装準拠） | ヒント（実装準拠） |
|--------|------------|----------------------|-------------------|
| `E-ANNOTATION-DUPLICATE-INTERLEAVE` | 同一ルールに `@interleave` が複数回宣言されている | `rule <rule> has duplicate @interleave annotations` | `Keep a single @interleave(profile=...) annotation.` |
| `E-ANNOTATION-INTERLEAVE-PROFILE` | `@interleave` の `profile` が `javaStyle` / `commentsAndSpaces` 以外 | `rule <rule> uses unsupported @interleave profile: <profile>` | `Use @interleave(profile=javaStyle) or @interleave(profile=commentsAndSpaces).` |
| `E-ANNOTATION-DUPLICATE-BACKREF` | 同一ルールに `@backref` が複数回宣言されている | `rule <rule> has duplicate @backref annotations` | `Keep a single @backref(name=...) annotation.` |
| `E-ANNOTATION-DUPLICATE-SCOPETREE` | 同一ルールに `@scopeTree` が複数回宣言されている | `rule <rule> has duplicate @scopeTree annotations` | `Keep a single @scopeTree(mode=...) annotation.` |
| `E-ANNOTATION-SCOPETREE-MODE` | `@scopeTree` の `mode` が `lexical` / `dynamic` 以外 | `rule <rule> uses unsupported @scopeTree mode: <mode>` | `Use @scopeTree(mode=lexical) or @scopeTree(mode=dynamic).` |

---

## バリデーション結果の集約

- バリデーションは grammar ブロック単位で実行される
- 複数の grammar ブロックのバリデーションエラーは集約されて1つのエラーとして報告される
- JSON レポートでは `issues[]` エントリが構造化メタデータを含む:
  - `rule`, `code`, `severity`, `category`, `message`, `hint`, `grammar`
- `issues[]` の順序は決定的（`grammar`, `rule`, `code`, `message` でソート）（MUST）
- レポートには集約サマリーが含まれる: `severityCounts`, `categoryCounts`

---

## 現在の制限事項

- 警告コードは `W-GENERAL-NO-ROOT` の1件のみ（それ以外はエラーコード）
- トークン解決のバリデーション（パーサークラスの存在確認）は未実装

## 変更履歴

- 2026-03-01: 初版作成
- 2026-08-01: 実装（`GrammarValidator.java`）の26エラーコード+1警告コードに一致するよう全面書き直し。実装を単一真実の参照元として明記。欠落していた5カテゴリ16コードを追加し、仕様のみに存在した8コード（`E-MAPPING-EXTRA-CAPTURE` 等）を実装のコード値にリネーム。（issue #3）
