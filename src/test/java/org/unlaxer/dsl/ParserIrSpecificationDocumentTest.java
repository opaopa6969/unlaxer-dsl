package org.unlaxer.dsl;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class ParserIrSpecificationDocumentTest {

    @Test
    public void testNormativeParserIrSpecificationContainsCoreSections() throws Exception {
        String specification = Files.readString(Path.of("specs/parser-ir.md"));
        String legacyPointer = Files.readString(Path.of("docs/PARSER-IR-DRAFT.md"));

        assertTrue(specification.contains("唯一の規範文書"));
        assertTrue(specification.contains("## 配置ルール"));
        assertTrue(specification.contains("## v1 ドキュメント構造"));
        assertTrue(specification.contains("## ノードモデル"));
        assertTrue(specification.contains("## スコープイベント"));
        assertTrue(specification.contains("## アノテーション"));
        assertTrue(specification.contains("## 診断"));
        assertTrue(specification.contains("## CLI との連携"));
        assertTrue(specification.contains("## 外部パーサー統合"));
        assertTrue(specification.contains("ParserIrAdapter"));
        assertTrue(specification.contains("## バージョニング"));
        assertTrue(legacyPointer.contains("non-normative pointer"));
        assertTrue(legacyPointer.contains("../specs/parser-ir.md"));
    }
}
