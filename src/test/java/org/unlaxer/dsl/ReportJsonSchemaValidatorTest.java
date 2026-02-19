package org.unlaxer.dsl;

import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

public class ReportJsonSchemaValidatorTest {

    @Test
    public void testAcceptsValidateSuccessV1() {
        String json = ReportJsonWriter.validationSuccess(1, "dev", "2026-01-01T00:00:00Z", 1);
        ReportJsonSchemaValidator.validate(1, json);
    }

    @Test
    public void testAcceptsValidateFailureV1() {
        var row = new ReportJsonWriter.ValidationIssueRow(
            "G", "Start", "E-X", "ERROR", "GENERAL", "m", "h"
        );
        String json = ReportJsonWriter.validationFailure(1, "dev", "2026-01-01T00:00:00Z", List.of(row));
        ReportJsonSchemaValidator.validate(1, json);
    }

    @Test
    public void testAcceptsGenerateSuccessV1() {
        String json = ReportJsonWriter.generationSuccess(
            1,
            "dev",
            "2026-01-01T00:00:00Z",
            1,
            List.of("org/example/ValidAST.java")
        );
        ReportJsonSchemaValidator.validate(1, json);
    }

    @Test
    public void testRejectsUnsupportedVersion() {
        String json = ReportJsonWriter.validationSuccess(1, "dev", "2026-01-01T00:00:00Z", 1);
        try {
            ReportJsonSchemaValidator.validate(2, json);
            fail("expected unsupported version error");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testRejectsMissingRequiredKey() {
        String broken = "{\"reportVersion\":1,\"toolVersion\":\"dev\",\"generatedAt\":\"x\",\"mode\":\"validate\",\"ok\":true,\"issues\":[]}";
        try {
            ReportJsonSchemaValidator.validate(1, broken);
            fail("expected schema validation error");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testRejectsWrongKeyOrderForV1() {
        String broken = "{\"toolVersion\":\"dev\",\"reportVersion\":1,\"generatedAt\":\"x\",\"mode\":\"validate\",\"ok\":true,\"grammarCount\":1,\"issues\":[]}";
        try {
            ReportJsonSchemaValidator.validate(1, broken);
            fail("expected schema validation error");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
