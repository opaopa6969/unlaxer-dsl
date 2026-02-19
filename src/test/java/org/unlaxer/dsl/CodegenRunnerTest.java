package org.unlaxer.dsl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class CodegenRunnerTest {

    @Test
    public void testHasErrorRowsDetectsNonWarningSeverity() {
        var rows = List.of(
            new ReportJsonWriter.ValidationIssueRow("G", "R", "E-X", "ERROR", "GENERAL", "m", "h")
        );
        assertTrue(CodegenRunner.hasErrorRows(rows));
        assertFalse(CodegenRunner.hasWarningRows(rows));
    }

    @Test
    public void testHasWarningRowsDetectsWarningSeverity() {
        var rows = List.of(
            new ReportJsonWriter.ValidationIssueRow("G", "R", "W-X", "WARNING", "GENERAL", "m", "h")
        );
        assertFalse(CodegenRunner.hasErrorRows(rows));
        assertTrue(CodegenRunner.hasWarningRows(rows));
    }
}
