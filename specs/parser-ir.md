# Parser IR 仕様

> ステータス: draft
> 最終更新: 2026-08-27
> 規範性: Parser IR の唯一の規範文書

## スコープ

このドキュメントは Parser IR（Intermediate Representation）の規範仕様を定義する。JSON スキーマ、ノードモデル、スコープイベント、バリデーションルールを含む。

このドキュメントが **扱わない** 範囲:
- CLI の `--validate-parser-ir` / `--export-parser-ir`（→ [cli.md](cli.md)）

## 関連ドキュメント

- [cli.md](cli.md) — Parser IR の CLI 操作
- [annotations.md](annotations.md) — @scopeTree とスコープイベントの関係
- [docs/schema/parser-ir-v1.draft.json](../docs/schema/parser-ir-v1.draft.json) — JSON スキーマ

---

## 設計目標

Parser IR は以下を目的とする:

1. パーサーの動作を CFG（文脈自由文法）では表現しにくい高度な機能で拡張する
2. LSP/DAP や後段パイプラインとの互換性を維持する
3. UBNF 以外のパーサーも同じ下流パイプラインに接続可能にする

---

## 配置ルール

| 種別 | 配置先 | 例 |
|------|--------|-----|
| 認識セマンティクスに影響 | BNF 拡張（文法レベル） | interleave, backreference |
| ポストパースの意味解釈 | アノテーション | symbol definition/use, scope policy |

---

## v1 ドキュメント構造

| フィールド | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| `irVersion` | `string` | はい | 現在は `"1.0"` |
| `source` | `string` | はい | 空白以外を含むソースパスまたは論理 ID |
| `nodes` | `object[]` | はい | 1件以上の Parser IR ノード |
| `diagnostics` | `object[]` | はい | 診断。診断がない場合は空配列 |
| `tokens` | `object[]` | いいえ | トークン列 |
| `trivia` | `object[]` | いいえ | trivia 列 |
| `scopeEvents` | `object[]` | いいえ | スコープイベント列 |
| `annotations` | `object[]` | いいえ | ノードに付与されたアノテーション |

未定義のトップレベルフィールドは許可しない（MUST NOT）。

---

## ノードモデル

各ノードは以下のフィールドを持つ:

| フィールド | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| `id` | `string` | はい | ノードの一意識別子（ドキュメント内で重複不可） |
| `kind` | `string` | はい | ノード種別 |
| `span` | `object` | はい | ソース位置（`start`, `end` オフセット） |
| `parentId` | `string` | いいえ | 親ノードの ID |
| `children` | `string[]` | いいえ | 子ノードの ID リスト |

### span

- `start`: 開始オフセット（inclusive）
- `end`: 終了オフセット（exclusive）
- `start` と `end` は 0 以上（MUST）
- `start <= end`（MUST）。空範囲は許可する

### 親子関係の整合性

- `parentId` で参照されるノードは存在する（MUST）
- `children` で参照されるノードは存在する（MUST）
- 自己参照は不可（MUST NOT）
- 親子関係は双方向で整合する（MUST）: 子の `parentId` が親を指し、親の `children` が子を含む

---

## スコープイベント

すべてのイベントで `event`, `scopeId`, `span` が必須である。

| イベント種別 | 追加の必須フィールド | 禁止フィールド |
|------------|--------------------|--------------|
| `enterScope` | — | `symbol`, `kind`, `targetScopeId` |
| `leaveScope` | — | `symbol`, `kind`, `targetScopeId` |
| `define` | `symbol`, `kind` | `scopeMode` |
| `use` | `symbol` | `kind`, `scopeMode` |

### スコープイベントのルール

- `scopeMode` は `enterScope` / `leaveScope` のみに許可（MUST）
- `leaveScope` の順序はネスト構造（LIFO）に従う（MUST）
- スコープイベントは unbalanced であってはならない（MUST NOT）
- 同一ストリーム内で重複する `enterScope` は不可（MUST NOT）
- `targetScopeId` が参照するスコープは存在する（MUST）

---

## アノテーション

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `targetId` | `string` | 対象ノードの ID |
| `name` | `string` | アノテーション名 |
| `payload` | `object` | アノテーションデータ（1つ以上のプロパティが必要） |

### ルール

- 命名規約: `^[a-z][a-zA-Z0-9-]*$`（MUST）
- `(targetId, name)` ペアはドキュメント内で一意（MUST）
- `payload` は少なくとも1つのプロパティを持つオブジェクト（MUST）

---

## 診断

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `code` | `string` | はい | 診断コード |
| `severity` | `string` | はい | `ERROR`, `WARNING`, `INFO` のいずれか |
| `span` | `object` | はい | ソース位置 |
| `message` | `string` | はい | 空でないメッセージ |
| `hint` | `string` | いいえ | 空でない修正ヒント |
| `related` | `object[]` | いいえ | 関連情報 |

### ルール

- `(code, span.start, span.end, message)` タプルはドキュメント内で一意（MUST）
- `related` 内の `(span.start, span.end, message)` タプルは各診断内で一意（MUST）
- `span` はソース範囲内（MUST）
- `related` の `span` もソース範囲内（MUST）

---

## CLI との連携

| CLI オプション | 動作 |
|--------------|------|
| `--export-parser-ir <path>` | `.ubnf` から Parser IR JSON をエクスポート |
| `--validate-parser-ir <path>` | Parser IR JSON を直接バリデーション |

NDJSON モードでは `parser-ir-export` イベントが出力される:
- `source`: 入力ファイルパス
- `output`: 出力ファイルパス
- `grammarCount`: grammar ブロック数
- `nodeCount`: ノード数
- `annotationCount`: アノテーション数

---

## 外部パーサー統合

UBNF で生成されていないパーサーは、次の SPI を使って同じ Parser IR
パイプラインへ接続できる。

- `ParseRequest` — ソース ID、内容、アダプター固有オプションを渡す
- `ParserIrAdapter` — `metadata()` と `parseToIr(ParseRequest)` を実装する
- `ParserIrAdapterMetadata` — アダプター ID、対応 IR バージョン、対応機能を宣言する
- `ParserIrDocument` — 生成した IR ペイロードを保持する
- `ParserIrConformanceValidator` — 共通の実行時契約を検査する
- `ParserIrFeature` — tokens、trivia、scope events、annotations、diagnostics などの対応機能を表す

アダプターは空でない ID と1件以上の対応 IR バージョンを宣言し（MUST）、
返したドキュメントが本仕様と JSON スキーマを満たすことを保証する（MUST）。
最小の実行例は `ParserIrAdapterContractTest` の
`ScopeTreeSampleAdapter` を参照する。

`GrammarToParserIrExporter` は UBNF のルールをノードとアノテーションへ
変換する。`@scopeTree(...)` を持つルールについては、
`scope:{GrammarName}::{RuleName}` を ID とする、同じ `scopeMode` の
`enterScope` / `leaveScope` イベントを生成する。

---

## バージョニング

- v1 の `irVersion` は `1.0` とする
- 任意フィールドの追加は後方互換とする
- 必須フィールドの削除または改名はメジャーバージョンを更新する
- オフセット単位や ID 安定性などの意味変更はメジャーバージョンを更新する
- アダプターは `ParserIrAdapterMetadata` で対応バージョンを宣言する

---

## テストフィクスチャ

`src/test/resources/schema/parser-ir/` に配置:

- `valid-minimal.json` — 最小有効ペイロード
- `valid-rich.json` — オプションフィールドを含む有効ペイロード
- `invalid-*.json` — 各種バリデーションエラーの負のフィクスチャ

---

## 現在の制限事項

- Parser IR は Draft ステータス
- UBNF → Parser IR のエクスポートは基本的なノード構造のみ
- 外部パーサー向け SPI と適合性検査は提供するが、本番用途の参照アダプターは未提供

## 変更履歴

- 2026-08-27: Parser IR の唯一の規範文書として位置づけ
- 2026-03-01: 初版作成
