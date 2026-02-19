#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPTS_DIR="$ROOT_DIR/scripts"

mapfile -t files < <(find "$SCRIPTS_DIR" -maxdepth 1 -type f -name '*.sh' | sort)
if [[ ${#files[@]} -eq 0 ]]; then
  echo "[check-scripts] No scripts found under scripts/."
  exit 0
fi

for file in "${files[@]}"; do
  first_line="$(head -n1 "$file" || true)"
  if [[ "$first_line" != "#!/usr/bin/env bash" ]]; then
    echo "[check-scripts] ERROR: invalid shebang in $file" >&2
    echo "  expected: #!/usr/bin/env bash" >&2
    echo "  actual:   $first_line" >&2
    exit 1
  fi

  if ! bash -n "$file"; then
    echo "[check-scripts] ERROR: syntax check failed for $file" >&2
    exit 1
  fi

done

echo "[check-scripts] OK: ${#files[@]} script(s) passed shebang and syntax checks."
