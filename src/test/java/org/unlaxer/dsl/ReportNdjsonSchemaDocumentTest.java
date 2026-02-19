package org.unlaxer.dsl;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class ReportNdjsonSchemaDocumentTest {

    @Test
    public void testNdjsonSchemaDefinesAllExpectedEventVariants() throws Exception {
        String json = Files.readString(Path.of("docs/schema/report-v1.ndjson.json"));
        Map<String, Object> schema = JsonTestUtil.parseObject(json);
        List<Object> oneOf = JsonTestUtil.getArray(schema, "oneOf");

        Set<String> titles = new HashSet<>();
        for (Object o : oneOf) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) o;
            titles.add(JsonTestUtil.getString(item, "title"));
        }

        assertTrue(titles.contains("file-event"));
        assertTrue(titles.contains("validate-success"));
        assertTrue(titles.contains("validate-failure"));
        assertTrue(titles.contains("strict-failure"));
        assertTrue(titles.contains("warnings"));
        assertTrue(titles.contains("generate-summary"));
    }
}
