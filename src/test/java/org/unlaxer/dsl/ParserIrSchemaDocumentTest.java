package org.unlaxer.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ParserIrSchemaDocumentTest {

    @Test
    public void testParserIrSchemaIncludesCoreTopLevelContract() throws Exception {
        String json = Files.readString(Path.of("docs/schema/parser-ir-v1.draft.json"));
        Map<String, Object> schema = JsonTestUtil.parseObject(json);

        assertEquals("https://unlaxer.dev/schema/parser-ir-v1.draft.json", JsonTestUtil.getString(schema, "$id"));
        assertEquals("object", JsonTestUtil.getString(schema, "type"));

        List<Object> required = JsonTestUtil.getArray(schema, "required");
        assertTrue(required.contains("irVersion"));
        assertTrue(required.contains("source"));
        assertTrue(required.contains("nodes"));
        assertTrue(required.contains("diagnostics"));
    }

    @Test
    public void testParserIrSchemaDefinesKeyDefs() throws Exception {
        String json = Files.readString(Path.of("docs/schema/parser-ir-v1.draft.json"));
        Map<String, Object> schema = JsonTestUtil.parseObject(json);
        Map<String, Object> defs = JsonTestUtil.getObject(schema, "$defs");

        assertTrue(defs.containsKey("node"));
        assertTrue(defs.containsKey("span"));
        assertTrue(defs.containsKey("scopeEvent"));
        assertTrue(defs.containsKey("annotation"));
        assertTrue(defs.containsKey("diagnostic"));
    }

    @Test
    public void testParserIrSchemaDefinesAnnotationNamePattern() throws Exception {
        String json = Files.readString(Path.of("docs/schema/parser-ir-v1.draft.json"));
        Map<String, Object> schema = JsonTestUtil.parseObject(json);
        Map<String, Object> defs = JsonTestUtil.getObject(schema, "$defs");
        Map<String, Object> annotation = JsonTestUtil.getObject(defs, "annotation");
        Map<String, Object> props = JsonTestUtil.getObject(annotation, "properties");
        Map<String, Object> name = JsonTestUtil.getObject(props, "name");
        assertEquals("^[a-z][a-zA-Z0-9-]*$", JsonTestUtil.getString(name, "pattern"));
    }
}
