# Parser IR Draft (Design Memo)

Status: Draft for discussion.

This memo proposes a hybrid approach for advanced parsing features in unlaxer-dsl:
- Syntax-level capabilities in grammar/BNF extensions
- Post-parse semantics and tooling integration in annotations
- A shared Parser Annotation IR so non-UBNF parsers can use the same downstream pipeline

## 1. Design Goals

- Represent parser behavior that is hard to express in plain CFG:
  - interleave
  - backreference
  - scope-tree-aware resolution
- Keep generated pipeline compatibility for LSP/DAP and later stages.
- Allow non-UBNF parsers to plug into the same annotation-driven post-processing.

## 2. Placement Rule: BNF Extension vs Annotation

- Put it in BNF extension when it changes recognition semantics.
  - examples: interleave, backreference constraints, lexical/syntactic context gates
- Put it in annotation when it adds semantic interpretation or generation policy.
  - examples: symbol definition/use intent, scope policy, diagnostics policy

Practical rule:
- Parse-time truth -> BNF extension
- Post-parse meaning -> annotation

## 3. Feature Direction

### 3.1 Interleave

- Core mechanism belongs to grammar level (BNF extension), not only annotation.
- Annotation can expose named profiles:
  - `@whitespace: javaStyle`
  - `@interleave: commentsAndSpaces`

The profile declaration is annotation-level UX; the behavior model is grammar-level.

### 3.2 Backreference

- Needs grammar-level expression because it constrains matching.
- Annotation can attach validation intent or diagnostic metadata.

### 3.3 Scope Tree

- Scope model should be explicit in annotation IR.
- If scope state affects recognition, use grammar-level hooks plus annotation metadata.

## 4. Parser Annotation IR (Shared Contract)

Define a parser-agnostic IR consumed by downstream phases.

Minimum contract:
- parse result tree identity:
  - node id, kind, span (start/end offsets)
- token/trivia streams (optional but recommended)
- scope events:
  - enter scope, leave scope, define symbol, use symbol
- annotation map:
  - normalized key/value payload per node/rule
- diagnostics stream:
  - code, severity, span, message, hint

## 5. Non-UBNF Parser Integration

Introduce an adapter SPI:
- external parser output -> Parser Annotation IR
- same downstream steps as UBNF-generated parser

Suggested components:
- `ParserIrAdapter` interface
- conformance test kit:
  - fixture input
  - expected IR snapshots
  - required invariants (span order, scope balance, stable ids)

## 6. Proposed Work Items

1. Define IR schema document and JSON examples.
2. Add adapter SPI in codegen/runtime boundary.
3. Implement one reference adapter for a non-UBNF parser.
4. Add conformance tests and golden snapshots.
5. Add docs for migration: UBNF-generated parser vs external parser.

## 7. Open Questions

- How strict should node id stability be across parser versions?
- Should trivia be required in v1 IR or optional?
- Do we allow dynamic scope kinds, or fixed enum first?
- Backreference semantics: exact text equality vs normalized token equality?
