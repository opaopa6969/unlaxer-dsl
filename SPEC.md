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

- Violations fail fast during code generation (`GrammarValidator.validateOrThrow`) with a clear error message.

### `@leftAssoc`

- Current status: **declarative only**.
- It records intent but does not currently change parser generation behavior.
- Future work: introduce operator-precedence/associativity aware generation.

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

## LSP / DAP Expectations

### LSP

- Diagnostics, hover, and keyword completion are supported.
- Semantic tokens currently return an empty token list to avoid invalid token encoding.

### DAP

- Breakpoints and stepping are based on parsed token stream.
- Step points are collected depth-first from parse tree leaves.

## Future Tightening Candidates

1. Rule-level whitespace override semantics.
2. Formal precedence/associativity model for `@leftAssoc`.
3. Explicit token import namespace configuration.
4. Golden test fixtures for generated source snapshots.
