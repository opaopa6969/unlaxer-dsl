package org.unlaxer.dsl.codegen;

import org.unlaxer.dsl.bootstrap.UBNFAST.AnnotatedElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.Annotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.AtomicElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.ChoiceBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.GroupElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.LeftAssocAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.MappingAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.OptionalElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.PrecedenceAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RepeatElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.RightAssocAnnotation;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleRefElement;
import org.unlaxer.dsl.bootstrap.UBNFAST.SequenceBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.StringSettingValue;
import org.unlaxer.dsl.bootstrap.UBNFAST.WhitespaceAnnotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates grammar-level semantic constraints that generators rely on.
 */
public final class GrammarValidator {

    private GrammarValidator() {}

    public static void validateOrThrow(GrammarDecl grammar) {
        List<String> errors = new ArrayList<>();

        validateGlobalWhitespace(grammar, errors);

        for (RuleDecl rule : grammar.rules()) {
            MappingAnnotation mapping = null;
            boolean hasLeftAssoc = false;
            boolean hasRightAssoc = false;
            List<PrecedenceAnnotation> precedenceAnnotations = new ArrayList<>();

            for (Annotation annotation : rule.annotations()) {
                if (annotation instanceof MappingAnnotation m) {
                    mapping = m;
                } else if (annotation instanceof LeftAssocAnnotation) {
                    hasLeftAssoc = true;
                } else if (annotation instanceof RightAssocAnnotation) {
                    hasRightAssoc = true;
                } else if (annotation instanceof PrecedenceAnnotation p) {
                    precedenceAnnotations.add(p);
                } else if (annotation instanceof WhitespaceAnnotation w) {
                    validateRuleWhitespace(rule, w, errors);
                }
            }

            if (mapping != null) {
                validateMapping(rule, mapping, errors);
            }
            if (hasLeftAssoc || hasRightAssoc) {
                validateAssoc(rule, mapping, hasLeftAssoc, hasRightAssoc, errors);
            }
            validatePrecedence(rule, hasLeftAssoc, hasRightAssoc, precedenceAnnotations, errors);
        }
        validatePrecedenceTopology(grammar, errors);
        validateAssociativityConsistency(grammar, errors);

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "Grammar validation failed for " + grammar.name() + ":\n - "
                    + String.join("\n - ", errors)
            );
        }
    }

    private static void validateMapping(RuleDecl rule, MappingAnnotation mapping, List<String> errors) {
        List<String> params = mapping.paramNames();
        Set<String> paramSet = new LinkedHashSet<>();
        Set<String> duplicateParams = new LinkedHashSet<>();
        for (String param : params) {
            if (!paramSet.add(param)) {
                duplicateParams.add(param);
            }
        }

        if (!duplicateParams.isEmpty()) {
            addError(errors,
                "rule " + rule.name() + " @mapping(" + mapping.className()
                    + ") has duplicate params: " + duplicateParams,
                "Remove duplicate parameter names in @mapping params.",
                "E-MAPPING-DUPLICATE-PARAM");
        }

        Set<String> captures = collectCaptureNames(rule.body());

        for (String param : paramSet) {
            if (!captures.contains(param)) {
                addError(errors,
                    "rule " + rule.name() + " @mapping(" + mapping.className()
                        + ") param '" + param + "' has no matching capture",
                    "Add @" + param + " capture in the rule body or remove it from params.",
                    "E-MAPPING-MISSING-CAPTURE");
            }
        }

        for (String capture : captures) {
            if (!paramSet.contains(capture)) {
                addError(errors,
                    "rule " + rule.name() + " has capture @" + capture
                        + " not listed in @mapping(" + mapping.className() + ") params",
                    "Add '" + capture + "' to @mapping params.",
                    "E-MAPPING-UNLISTED-CAPTURE");
            }
        }
    }

    private static void validateAssoc(
        RuleDecl rule,
        MappingAnnotation mapping,
        boolean hasLeftAssoc,
        boolean hasRightAssoc,
        List<String> errors
    ) {
        String assocName = hasRightAssoc ? "@rightAssoc" : "@leftAssoc";
        if (hasLeftAssoc && hasRightAssoc) {
            addError(errors,
                "rule " + rule.name() + " cannot use both @leftAssoc and @rightAssoc",
                "Keep exactly one associativity annotation per rule.",
                "E-ASSOC-BOTH");
            return;
        }

        Set<String> captures = collectCaptureNames(rule.body());

        if (mapping == null) {
            addError(errors,
                "rule " + rule.name() + " uses " + assocName + " but has no @mapping",
                "Add @mapping(ClassName, params=[left, op, right]) to this rule.",
                "E-ASSOC-NO-MAPPING");
        } else {
            Set<String> params = new LinkedHashSet<>(mapping.paramNames());
            for (String required : List.of("left", "op", "right")) {
                if (!params.contains(required)) {
                    addError(errors,
                        "rule " + rule.name() + " uses " + assocName + " but @mapping("
                            + mapping.className() + ") params does not contain '" + required + "'",
                        "Include left/op/right in @mapping params.",
                        "E-ASSOC-MAPPING-PARAM");
                }
            }
        }

        for (String required : List.of("left", "op", "right")) {
            if (!captures.contains(required)) {
                addError(errors,
                    "rule " + rule.name() + " uses " + assocName + " but capture @"
                        + required + " is missing",
                    "Add @" + required + " capture in the rule body.",
                    "E-ASSOC-MISSING-CAPTURE");
            }
        }

        if (!containsRepeat(rule.body())) {
            addError(errors,
                "rule " + rule.name() + " uses " + assocName + " but has no repeat segment",
                "Use canonical operator pattern: Base { Op Right }.",
                "E-ASSOC-NO-REPEAT");
        }

        if (hasRightAssoc && !isCanonicalRightAssocShape(rule)) {
            addError(errors,
                "rule " + rule.name()
                    + " uses @rightAssoc but body is not canonical: expected Base { Op "
                    + rule.name() + " }",
                "Rewrite right-assoc rule as Base { op " + rule.name() + " }.",
                "E-RIGHTASSOC-NONCANONICAL");
        }
    }

    private static void validateGlobalWhitespace(GrammarDecl grammar, List<String> errors) {
        grammar.settings().stream()
            .filter(s -> "whitespace".equals(s.key()))
            .forEach(s -> {
                if (s.value() instanceof StringSettingValue sv) {
                    String style = sv.value().trim();
                    if (!style.equalsIgnoreCase("javaStyle")) {
                        addError(errors,
                            "global @whitespace style must be javaStyle: " + style,
                            "Use '@whitespace: javaStyle'.",
                            "E-WHITESPACE-GLOBAL-STYLE");
                    }
                }
            });
    }

    private static void validateRuleWhitespace(RuleDecl rule, WhitespaceAnnotation w, List<String> errors) {
        String style = w.style().orElse("javaStyle").trim();
        if (!style.equalsIgnoreCase("javaStyle") && !style.equalsIgnoreCase("none")) {
            addError(errors,
                "rule " + rule.name() + " uses unsupported @whitespace style: " + style
                    + " (allowed: javaStyle, none)",
                "Use @whitespace or @whitespace(none).",
                "E-WHITESPACE-RULE-STYLE");
        }
    }

    private static void validatePrecedence(
        RuleDecl rule,
        boolean hasLeftAssoc,
        boolean hasRightAssoc,
        List<PrecedenceAnnotation> precedenceAnnotations,
        List<String> errors
    ) {
        if (precedenceAnnotations.size() > 1) {
            addError(errors,
                "rule " + rule.name() + " has duplicate @precedence annotations",
                "Keep a single @precedence(level=...) annotation.",
                "E-PRECEDENCE-DUPLICATE");
        }
        for (PrecedenceAnnotation p : precedenceAnnotations) {
            if (p.level() < 0) {
                addError(errors,
                    "rule " + rule.name() + " has invalid @precedence level: " + p.level(),
                    "Use a non-negative integer (e.g. @precedence(level=10)).",
                    "E-PRECEDENCE-NEGATIVE");
            }
        }
        if (hasLeftAssoc && hasRightAssoc) {
            // already reported by validateAssoc, but keep precedence checks deterministic.
            return;
        }
        if (!precedenceAnnotations.isEmpty() && !hasLeftAssoc && !hasRightAssoc) {
            addError(errors,
                "rule " + rule.name() + " uses @precedence but has no @leftAssoc/@rightAssoc",
                "Add one associativity annotation alongside @precedence.",
                "E-PRECEDENCE-NO-ASSOC");
        }
    }

    private static void validatePrecedenceTopology(GrammarDecl grammar, List<String> errors) {
        var ruleMap = grammar.rules().stream()
            .collect(java.util.stream.Collectors.toMap(RuleDecl::name, r -> r, (a, b) -> a));

        for (RuleDecl rule : grammar.rules()) {
            Integer precedence = findPrecedenceLevel(rule);
            if (precedence == null || !hasAssoc(rule)) {
                continue;
            }
            Set<String> refs = collectReferencedRuleNames(rule.body());
            for (String refName : refs) {
                if (rule.name().equals(refName)) {
                    continue;
                }
                RuleDecl refRule = ruleMap.get(refName);
                if (refRule == null || !hasAssoc(refRule)) {
                    continue;
                }
                Integer refPrecedence = findPrecedenceLevel(refRule);
                if (refPrecedence == null) {
                    continue;
                }
                if (refPrecedence <= precedence) {
                    addError(errors,
                        "rule " + rule.name() + " precedence " + precedence
                            + " must be lower than referenced operator rule "
                            + refName + " precedence " + refPrecedence,
                        "Decrease " + rule.name() + " level or increase " + refName + " level.",
                        "E-PRECEDENCE-ORDER");
                }
            }
        }
    }

    private static void validateAssociativityConsistency(GrammarDecl grammar, List<String> errors) {
        Map<Integer, String> assocByLevel = new LinkedHashMap<>();
        for (RuleDecl rule : grammar.rules()) {
            String assoc = getAssocKind(rule);
            if ("NONE".equals(assoc) || "BOTH".equals(assoc)) {
                continue;
            }
            Integer level = findPrecedenceLevel(rule);
            if (level == null) {
                addError(errors,
                    "rule " + rule.name() + " uses @" + assoc.toLowerCase()
                        + "Assoc but has no @precedence",
                    "Add @precedence(level=...) to this operator rule.",
                    "E-ASSOC-NO-PRECEDENCE");
                continue;
            }
            String existing = assocByLevel.get(level);
            if (existing == null) {
                assocByLevel.put(level, assoc);
                continue;
            }
            if (!existing.equals(assoc)) {
                addError(errors,
                    "precedence level " + level
                        + " mixes associativity: " + existing + " and " + assoc,
                    "Use one associativity per precedence level.",
                    "E-PRECEDENCE-MIXED-ASSOC");
            }
        }
    }

    private static void addError(List<String> errors, String message, String hint, String code) {
        errors.add(message + " [code: " + code + "] [hint: " + hint + "]");
    }

    private static boolean hasAssoc(RuleDecl rule) {
        boolean left = rule.annotations().stream().anyMatch(a -> a instanceof LeftAssocAnnotation);
        boolean right = rule.annotations().stream().anyMatch(a -> a instanceof RightAssocAnnotation);
        return left || right;
    }

    private static String getAssocKind(RuleDecl rule) {
        boolean left = rule.annotations().stream().anyMatch(a -> a instanceof LeftAssocAnnotation);
        boolean right = rule.annotations().stream().anyMatch(a -> a instanceof RightAssocAnnotation);
        if (left && right) {
            return "BOTH";
        }
        if (left) {
            return "LEFT";
        }
        if (right) {
            return "RIGHT";
        }
        return "NONE";
    }

    private static Integer findPrecedenceLevel(RuleDecl rule) {
        return rule.annotations().stream()
            .filter(a -> a instanceof PrecedenceAnnotation)
            .map(a -> (PrecedenceAnnotation) a)
            .reduce((first, second) -> second)
            .map(PrecedenceAnnotation::level)
            .orElse(null);
    }

    private static Set<String> collectCaptureNames(RuleBody body) {
        Set<String> captures = new LinkedHashSet<>();
        collectCaptureNamesFromBody(body, captures);
        return captures;
    }

    private static void collectCaptureNamesFromBody(RuleBody body, Set<String> captures) {
        switch (body) {
            case ChoiceBody choice -> {
                for (SequenceBody seq : choice.alternatives()) {
                    collectCaptureNamesFromSequence(seq, captures);
                }
            }
            case SequenceBody seq -> collectCaptureNamesFromSequence(seq, captures);
        }
    }

    private static void collectCaptureNamesFromSequence(SequenceBody seq, Set<String> captures) {
        for (AnnotatedElement ae : seq.elements()) {
            ae.captureName().ifPresent(captures::add);
            collectCaptureNamesFromAtomic(ae.element(), captures);
        }
    }

    private static void collectCaptureNamesFromAtomic(AtomicElement element, Set<String> captures) {
        switch (element) {
            case GroupElement group -> collectCaptureNamesFromBody(group.body(), captures);
            case OptionalElement opt -> collectCaptureNamesFromBody(opt.body(), captures);
            case RepeatElement rep -> collectCaptureNamesFromBody(rep.body(), captures);
            default -> {
                // TerminalElement / RuleRefElement have no nested bodies.
            }
        }
    }

    private static Set<String> collectReferencedRuleNames(RuleBody body) {
        Set<String> refs = new LinkedHashSet<>();
        collectReferencedRuleNamesFromBody(body, refs);
        return refs;
    }

    private static void collectReferencedRuleNamesFromBody(RuleBody body, Set<String> refs) {
        switch (body) {
            case ChoiceBody choice -> {
                for (SequenceBody seq : choice.alternatives()) {
                    collectReferencedRuleNamesFromSequence(seq, refs);
                }
            }
            case SequenceBody seq -> collectReferencedRuleNamesFromSequence(seq, refs);
        }
    }

    private static void collectReferencedRuleNamesFromSequence(SequenceBody seq, Set<String> refs) {
        for (AnnotatedElement ae : seq.elements()) {
            collectReferencedRuleNamesFromAtomic(ae.element(), refs);
        }
    }

    private static void collectReferencedRuleNamesFromAtomic(AtomicElement element, Set<String> refs) {
        switch (element) {
            case RuleRefElement ref -> refs.add(ref.name());
            case GroupElement group -> collectReferencedRuleNamesFromBody(group.body(), refs);
            case OptionalElement opt -> collectReferencedRuleNamesFromBody(opt.body(), refs);
            case RepeatElement rep -> collectReferencedRuleNamesFromBody(rep.body(), refs);
            default -> {
                // TerminalElement has no nested refs.
            }
        }
    }

    private static boolean containsRepeat(RuleBody body) {
        return switch (body) {
            case ChoiceBody choice -> choice.alternatives().stream()
                .anyMatch(GrammarValidator::containsRepeatInSequence);
            case SequenceBody seq -> containsRepeatInSequence(seq);
        };
    }

    private static boolean containsRepeatInSequence(SequenceBody seq) {
        for (AnnotatedElement ae : seq.elements()) {
            if (containsRepeatInAtomic(ae.element())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRepeatInAtomic(AtomicElement element) {
        return switch (element) {
            case RepeatElement rep -> true;
            case GroupElement group -> containsRepeat(group.body());
            case OptionalElement opt -> containsRepeat(opt.body());
            default -> false;
        };
    }

    private static boolean isCanonicalRightAssocShape(RuleDecl rule) {
        SequenceBody top = getSingleSequence(rule.body());
        if (top == null || top.elements().size() != 2) {
            return false;
        }
        AtomicElement second = top.elements().get(1).element();
        if (!(second instanceof RepeatElement repeat)) {
            return false;
        }
        SequenceBody repSeq = getSingleSequence(repeat.body());
        if (repSeq == null || repSeq.elements().size() != 2) {
            return false;
        }
        AtomicElement repRight = repSeq.elements().get(1).element();
        return repRight instanceof RuleRefElement ref && rule.name().equals(ref.name());
    }

    private static SequenceBody getSingleSequence(RuleBody body) {
        return switch (body) {
            case SequenceBody seq -> seq;
            case ChoiceBody choice when choice.alternatives().size() == 1 -> choice.alternatives().get(0);
            default -> null;
        };
    }
}
