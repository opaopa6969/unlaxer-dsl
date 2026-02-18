#!/usr/bin/env bash
# TinyCalc VSIX ビルドスクリプト
# 実行場所: tinycalc-vscode/ ディレクトリ or プロジェクトルート
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DSL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VSCODE_DIR="$SCRIPT_DIR"

echo "=== Step 1: unlaxer-dsl を Maven ローカルリポジトリにインストール ==="
(cd "$DSL_ROOT" && mvn -q install -DskipTests)

echo "=== Step 2: npm 依存インストール ==="
(cd "$VSCODE_DIR" && npm install)

echo "=== Step 3: サーバー jar ビルド（コード生成 → コンパイル → fat jar） ==="
(cd "$VSCODE_DIR" && npm run build:server)

echo "=== Step 4: TypeScript コンパイル + VSIX パッケージング ==="
(cd "$VSCODE_DIR" && npm run package)

VSIX_FILE=$(ls "$VSCODE_DIR"/*.vsix 2>/dev/null | head -1)
if [[ -n "$VSIX_FILE" ]]; then
  echo ""
  echo "✓ VSIX ビルド完了: $VSIX_FILE"
  echo "  インストール方法: code --install-extension $VSIX_FILE"
else
  echo "VSIX ファイルが見つかりません"
  exit 1
fi
