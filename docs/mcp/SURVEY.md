# unlaxer-dsl MCP 化調査（Phase 1）

## 概要

unlaxer-dsl は **UBNF（Unlaxer BNF）文法定義から Java の Parser / AST / Mapper / Evaluator / LSP / DAP ソースコードを自動生成する DSL ジェネレータ** である。CLI ツール `CodegenMain` により batch 実行で完結する。Java 21 / Maven / unlaxer-common 依存。Maven Central 公開済み（v3.0.11）。

- リポジトリ種別: `library`（CLI ツールを含むが常駐サーバはない）
- 既存エンドポイント: なし（HTTP API / MCP / healthz / volta manifest すべて不存在）
- セルフホスティング: UBNF 文法自体が UBNF で記述されており、生成パーサーで自身をパースできる（`SelfHostingRoundTripTest` 検証済み）

## 判定と理由

**判定: `defer`（保留）**

理由:
1. **常駐の価値が薄い**: CLI として完成度が高く、起動時間は 1 秒未満、状態を持たない。batch 実行で十分機能するため、MCP サーバを常駐させる意義が低い。
2. **既存サービスとの重複**: volta カタログに `unlaxer-parser`（GitHub: opaopa6969/unlaxer-parser）が存在し、description が "Grammar-to-full-language code generator: UBNF grammar to Lexer, Parser, AST, LSP, DAP" と本リポジトリとほぼ同一。どちらを MCP 化すべきか持ち主の判断が必要。
3. **組み合わせ需要が具体化していない**: Parser IR のエクスポート・バリデーションは他サービスとのパイプライン接続を意図しているが、現時点で具体の消費者がいない。

再検討の条件:
- エージェントが UBNF 文法を動的に書いてコード生成するワークフロー需要が出た場合
- Parser IR を他サービスが消費するパイプラインが具体化した場合
- `unlaxer-parser` との役割分担が明確化された場合

## 公開候補

MCP 化する場合の候補（参照用。実装しない）:

| kind | name | io | 副作用 | 長時間 | maps_to |
|------|------|-----|--------|--------|---------|
| tool | `validate` | `{ grammar_path, strict? } → { ok, issues[], severityCounts }` | read | no | `CodegenMain --validate-only --report-format json` |
| tool | `generate` | `{ grammar_path, generators[], output, overwrite?, dry_run? } → { generatedFiles, writtenCount, ... }` | write | no | `CodegenMain --generators ... --output ...` |
| tool | `export_parser_ir` | `{ grammar_path, output } → { grammarCount, nodeCount, annotationCount }` | write | no | `CodegenMain --export-parser-ir` |
| tool | `validate_parser_ir` | `{ ir_path } → { ok, diagnostics[] }` | read | no | `CodegenMain --validate-parser-ir` |
| resource | `spec` | `ubnf://spec`（能力の機械可読仕様） | — | — | — |
| resource | `guide` | `ubnf://guide`（使い方） | — | — | — |
| skill | `ubnf-grammar-writing` | UBNF 文法記述の手順 | — | — | locality: global |
| skill | `ubnf-vscode-extension-build` | LSP/DAP 拡張のビルド手順 | — | — | locality: repo |

namespace 候補: `ubnf`

## 組み合わせ例

1. `ubnf__validate` → `unlaxer-parser` 側で generate: エージェントが文法を検証してから別サービスでコード生成する流水線
2. `ubnf__export_parser_ir` → 他のパーサーツールが IR を消費: 非 UBNF パーサーも同一 IR パイプラインに接続可能（仕様上の意図だが具体例なし）
3. `ubnf__generate(LSP)` → tinycalc-vscode ビルド流水線: 生成された LSP サーバを VS Code 拡張に組み込む

## 依存と協調

| 相手 repo | 方向 | 能力 | 現存 | 備考 |
|-----------|------|------|------|------|
| `unlaxer-parser` | depends_on | パーサーコンビネータランタイム | yes | カタログ上は unlaxer-parser の backend が null（未 MCP 化）。機能重複の可能性高 |
| `unlaxer-common` | depends_on | Parser, Chain, Choice, WordParser 等のパーサーコンビネータ | yes | Maven 依存 org.unlaxer:unlaxer-common:3.0.10 |

協調が必要な場合は Phase 2 で issue-hub に登録する。このフェーズでは issue を立てない。

## ライブラリのサーバ化

該当しない（`library_serve.needed = false`）。

MCP 化を再検討する場合に必要な新規実装:
- Streamable HTTP `/mcp` エンドポイント
- `/healthz` エンドポイント
- `PORT` 環境変数対応
- `volta.service.json` manifest
- systemd unit または docker 起動設定
- MCP サーバ実装（Java + MCP SDK）
- runtime: Java
- 推定作業量: M

## リスク

- unlaxer-parser との機能重複。どちらを MCP 化すべきか持ち主の判断がないと二重投資になる
- Java 21 + `--enable-preview` が必要。MCP サーバ常駐時の安定性が未検証
- 生成されたコードのコンパイルには unlaxer-common がクラスパスに必要（自己完結しない）
- 破壊的操作: `--clean-output` はターゲットディレクトリを削除する。MCP 化する場合は `confirm` / dry-run 必須
- Maven Central 公開済み（GPG 署名あり）。ビルド・リリース流水線の変更は慎重に

## 持ち主への質問

1. `unlaxer-parser`（GitHub: opaopa6969/unlaxer-parser）と本リポジトリの関係は？ 同一機能の別実装か、役割分担があるか？
2. エージェントが UBNF 文法を書いてコード生成するワークフローは実際に需要があるか？ それとも人間が CLI で使うだけか？
3. Parser IR を他サービスが消費する予定はあるか？（`specs/parser-ir.md` にその意図が書かれているが具体パートナーなし）
