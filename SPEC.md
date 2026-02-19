# UBNF Spec Notes (Working Draft)

This document defines behavior that users should be able to rely on when writing grammars for `unlaxer-dsl`.

## Scope

- UBNF syntax and annotation semantics.
- Generator-facing constraints that are validated before code generation.
- LSP/DAP behavior guarantees and current limitations.

## Annotation Semantics

### `@mapping(ClassName, params=[...])`

Contract:

1. Every param in `params` must match an existing capture name (`@name`) in the same rule body.
2. Every capture name used in that rule body must be listed in `params`.
3. Duplicate param names are invalid.

Current behavior:

- Violations fail fast during code generation (`GrammarValidator.validateOrThrow`) with a clear error message, stable error code, and short fix hint.
- `GrammarValidator.validate(grammar)` returns structured issues (`code`, `message`, `hint`) without throwing.

### `@leftAssoc`

- Current status: contract-validated metadata.
- Parser generation remains grammar-driven.

### `@rightAssoc`

- Current status: partially consumed by parser generation.
- It uses the same capture/mapping contract as `@leftAssoc` (`left`, `op`, `right`).
- It is mutually exclusive with `@leftAssoc` on the same rule.
- Canonical shape `Base { Op Self }` is generated as right-recursive choice (`Base Op Self | Base`).
- Non-canonical shapes are rejected by validator.
- Mapper generator emits `foldRightAssoc<Class>` helper skeletons for right-associative mappings.

### `@precedence(level=...)`

Contract:

1. `level` is an integer (`0` or greater).
2. A rule may declare `@precedence` at most once.
3. `@precedence` currently requires either `@leftAssoc` or `@rightAssoc` on the same rule.
4. `@leftAssoc` and `@rightAssoc` cannot be used together on one rule.
5. If an operator rule references another operator rule and both have `@precedence`, the referenced rule must have a higher precedence level.
6. Every `@leftAssoc` / `@rightAssoc` rule must declare `@precedence`.
7. A single precedence level cannot mix left/right associativity.

Current behavior:

- Violations fail fast during code generation (`GrammarValidator.validateOrThrow`) with a clear error message.
- Parser generator emits `PRECEDENCE_<RULE_NAME>` constants for annotated rules.
- Parser generator emits operator metadata APIs: `getPrecedence(ruleName)` and `getAssociativity(ruleName)`.
- Parser generator emits sorted operator specs: `getOperatorSpecs()`.
- Parser generator emits `OperatorSpec` lookup helpers: `getOperatorSpec(ruleName)` and `isOperatorRule(ruleName)`.
- Parser generator emits precedence-step helper: `getNextHigherPrecedence(ruleName)`.
- Parser generator emits parser-resolution helpers: `getOperatorParser(ruleName)`, `getLowestPrecedenceOperator()`, and `getLowestPrecedenceParser()`.
- Parser generator emits precedence-level helpers: `getPrecedenceLevels()`, `getOperatorsAtPrecedence(level)`, and `getOperatorParsersAtPrecedence(level)`.

### `@whitespace`

- Global `@whitespace` in grammar settings controls delimiter insertion in generated parsers.
- Rule-level `@whitespace` overrides global behavior for that rule. `@whitespace(none)` disables auto delimiters, while `@whitespace` or `@whitespace(javaStyle)` enables auto delimiters.

## Token Resolution

`token NAME = ParserClass` maps as follows:

- In generated parser code, references to `NAME` become `Parser.get(ParserClass.class)`.
- Import resolution currently checks known `unlaxer` parser packages.
- If a parser class cannot be resolved/imported, compilation fails in generated consumer projects.

## CLI (`CodegenMain`) Behavior

- All `grammar` blocks in a single `.ubnf` file are processed (not only the first one).
- Missing values for `--grammar`, `--output`, or `--generators` are treated as CLI errors.
- Grammar validation runs before generation for each grammar block.
- Validation failures are aggregated across grammar blocks and reported in one error.
- `--validate-only` runs grammar validation without writing generated sources.
- `--report-format json` emits machine-readable validation output (especially useful with `--validate-only`).
- `--report-file <path>` writes the final report payload (text/json) to a file.
- `--report-version 1` selects JSON schema version (currently only version `1` is supported).
- `--report-schema-check` validates JSON payload schema before emitting/writing reports.
- In normal generation mode with `--report-format json`, CLI emits generation summary (`generatedCount`, `generatedFiles`).
- JSON report schema includes stable top-level fields:
  `reportVersion`, `toolVersion`, `generatedAt` (UTC ISO-8601), and `mode` (`validate` or `generate`).
- `toolVersion` is sourced from artifact `Implementation-Version`; fallback is `dev`.
- Validation failure `issues[]` entries include structured metadata:
  `rule`, `code`, `severity`, `category`, `message`, and `hint` (plus `grammar`).
- Validation failure reports include aggregate summaries:
  `severityCounts` and `categoryCounts`.
- Validation `issues[]` order is deterministic (sorted by `grammar`, `rule`, `code`, `message`).
- Process exit codes are explicit:
  `0` success, `2` CLI usage error, `3` validation error, `4` generation/runtime error.
- JSON payload creation is centralized in `ReportJsonWriter` and versioned via `ReportJsonWriterV1`.
- `ReportJsonSchemaCompatibilityTest` pins top-level JSON schema order/keys for report version 1.
- `CodegenMain.runWithClock(...)` exists for deterministic timestamp testing.

### JSON Report Examples
Regenerate from live CLI output:

```bash
./scripts/spec/refresh-json-examples.sh
```

<!-- JSON_REPORT_EXAMPLES_START -->
Validate success:

```json
{"reportVersion":1,"toolVersion":"<toolVersion>","generatedAt":"<generatedAt>","mode":"validate","ok":true,"grammarCount":1,"issues":[]}
```

Validate failure:

```json
{"reportVersion":1,"toolVersion":"<toolVersion>","generatedAt":"<generatedAt>","mode":"validate","ok":false,"issueCount":1,"severityCounts":{"ERROR":1},"categoryCounts":{"MAPPING":1},"issues":[{"grammar":"Invalid","rule":"Invalid","code":"E-MAPPING-MISSING-CAPTURE","severity":"ERROR","category":"MAPPING","message":"rule Invalid @mapping(RootNode) param 'missing' has no matching capture","hint":"Add @missing capture in the rule body or remove it from params."}]}
```

Generate success:

```json
{"reportVersion":1,"toolVersion":"<toolVersion>","generatedAt":"<generatedAt>","mode":"generate","ok":true,"grammarCount":1,"generatedCount":1,"generatedFiles":["/path/to/out/org/example/valid/ValidAST.java"]}
```
<!-- JSON_REPORT_EXAMPLES_END -->

## LSP / DAP Expectations

### LSP

- Diagnostics, hover, and keyword completion are supported.
- Completion includes DSL core keywords and annotation keywords (`@root`, `@mapping`, `@whitespace`, `@leftAssoc`, `@rightAssoc`, `@precedence`) plus grammar terminals.
- Semantic tokens currently return an empty token list to avoid invalid token encoding.

### DAP

- Breakpoints and stepping are based on parsed token stream.
- Step points are collected depth-first from parse tree leaves.

## Future Tightening Candidates

1. Rule-level whitespace override semantics.
2. Parser generation that actually consumes `@precedence` + associativity metadata.
3. Explicit token import namespace configuration.

## Golden Fixture Maintenance

- Snapshot fixtures are stored under `src/test/resources/golden/`.
- `org.unlaxer.dsl.codegen.SnapshotFixtureWriter` rewrites all fixtures from the current generators.
- `SnapshotFixtureWriter` supports `--output-dir <path>` for safe dry-runs from tests or local verification.
- `SnapshotFixtureData` keeps snapshot grammars and fixture file lists in one place.
- `SnapshotFixtureGoldenConsistencyTest` verifies writer output matches committed fixtures.
- Fixtures currently cover AST/Parser/Mapper/Evaluator/LSP/LSPLauncher/DAP/DAPLauncher (plus right-assoc parser/mapper variants).
