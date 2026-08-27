---
name: 文書の修正・追加
about: 文書の誤り、不足、改善案を報告する
title: "[Docs] "
labels: documentation, loop:docs
assignees: ""
---

## 概要

修正または追加したい文書内容を簡潔に記載してください。

## 対象

対象ファイル、見出し、URL を記載してください。

## 現在の記述・問題点

不正確、不足、または分かりにくい点を記載してください。

## 期待する記述

完了時に文書が満たす条件を記載してください。

## 機械可読ブロック

```yaml
kind: docs
loop: docs
priority: normal
depends_on: []
acceptance:
  - "対象文書が期待する記述を満たすこと"
```
