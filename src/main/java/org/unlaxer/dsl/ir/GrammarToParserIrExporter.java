package org.unlaxer.dsl.ir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.unlaxer.dsl.bootstrap.UBNFAST.Annotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.BackrefAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.InterleaveAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.LeftAssocAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.MappingAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.PrecedenceAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RightAssocAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RootAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.ScopeTreeAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.SimpleAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.WhitespaceAnnotation;

/**
 * Exports UBNF grammar metadata into Parser IR draft shape.
 */
public final class GrammarToParserIrExporter {
    private GrammarToParserIrExporter() {}

    public static ParserIrDocument export(GrammarDecl grammar, String sourceId) {
        if (grammar == null) {
            throw new IllegalArgumentException("grammar must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }

        List<Object> nodes = new ArrayList<>();
        List<Object> annotations = new ArrayList<>();
        for (RuleDecl rule : grammar.rules()) {
            nodes.add(buildRuleNode(rule));
            for (Annotation annotation : rule.annotations()) {
                annotations.add(buildAnnotation(rule.name(), annotation));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("irVersion", "1.0");
        payload.put("source", sourceId);
        payload.put("nodes", nodes);
        payload.put("diagnostics", List.of());
        if (!annotations.isEmpty()) {
            payload.put("annotations", annotations);
        }

        return new ParserIrDocument(payload);
    }

    private static Map<String, Object> buildRuleNode(RuleDecl rule) {
        Map<String, Object> span = new LinkedHashMap<>();
        span.put("start", 0);
        span.put("end", 0);

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", rule.name());
        node.put("kind", "RuleDecl");
        node.put("span", span);
        return node;
    }

    private static Map<String, Object> buildAnnotation(String ruleName, Annotation annotation) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("targetId", ruleName);

        Map<String, Object> payload = new LinkedHashMap<>();
        String name;
        if (annotation instanceof RootAnnotation) {
            name = "root";
            payload.put("enabled", true);
        } else if (annotation instanceof MappingAnnotation mapping) {
            name = "mapping";
            payload.put("className", mapping.className());
            payload.put("params", mapping.paramNames());
        } else if (annotation instanceof WhitespaceAnnotation whitespace) {
            name = "whitespace";
            payload.put("style", whitespace.style().orElse("javaStyle"));
        } else if (annotation instanceof InterleaveAnnotation interleave) {
            name = "interleave";
            payload.put("profile", interleave.profile());
        } else if (annotation instanceof BackrefAnnotation backref) {
            name = "backref";
            payload.put("name", backref.name());
        } else if (annotation instanceof ScopeTreeAnnotation scopeTree) {
            name = "scope-tree";
            payload.put("mode", scopeTree.mode());
        } else if (annotation instanceof LeftAssocAnnotation) {
            name = "left-assoc";
            payload.put("assoc", "left");
        } else if (annotation instanceof RightAssocAnnotation) {
            name = "right-assoc";
            payload.put("assoc", "right");
        } else if (annotation instanceof PrecedenceAnnotation precedence) {
            name = "precedence";
            payload.put("level", precedence.level());
        } else if (annotation instanceof SimpleAnnotation simple) {
            name = "simple";
            payload.put("name", simple.name());
        } else {
            throw new IllegalArgumentException("unsupported annotation type: " + annotation.getClass().getName());
        }

        out.put("name", name);
        out.put("payload", payload);
        return out;
    }
}
