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
            errors.add("rule " + rule.name() + " @mapping(" + mapping.className()
                + ") has duplicate params: " + duplicateParams);
        }

        Set<String> captures = collectCaptureNames(rule.body());

        for (String param : paramSet) {
            if (!captures.contains(param)) {
                errors.add("rule " + rule.name() + " @mapping(" + mapping.className()
                    + ") param '" + param + "' has no matching capture");
            }
        }

        for (String capture : captures) {
            if (!paramSet.contains(capture)) {
                errors.add("rule " + rule.name() + " has capture @" + capture
                    + " not listed in @mapping(" + mapping.className() + ") params");
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
            errors.add("rule " + rule.name() + " cannot use both @leftAssoc and @rightAssoc");
            return;
        }

        Set<String> captures = collectCaptureNames(rule.body());

        if (mapping == null) {
            errors.add("rule " + rule.name() + " uses " + assocName + " but has no @mapping");
        } else {
            Set<String> params = new LinkedHashSet<>(mapping.paramNames());
            for (String required : List.of("left", "op", "right")) {
                if (!params.contains(required)) {
                    errors.add("rule " + rule.name() + " uses " + assocName + " but @mapping("
                        + mapping.className() + ") params does not contain '" + required + "'");
                }
            }
        }

        for (String required : List.of("left", "op", "right")) {
            if (!captures.contains(required)) {
                errors.add("rule " + rule.name() + " uses " + assocName + " but capture @"
                    + required + " is missing");
            }
        }

        if (!containsRepeat(rule.body())) {
            errors.add("rule " + rule.name() + " uses " + assocName + " but has no repeat segment");
        }
    }

    private static void validateGlobalWhitespace(GrammarDecl grammar, List<String> errors) {
        grammar.settings().stream()
            .filter(s -> "whitespace".equals(s.key()))
            .forEach(s -> {
                if (s.value() instanceof StringSettingValue sv) {
                    String style = sv.value().trim();
                    if (!style.equalsIgnoreCase("javaStyle")) {
                        errors.add("global @whitespace style must be javaStyle: " + style);
                    }
                }
            });
    }

    private static void validateRuleWhitespace(RuleDecl rule, WhitespaceAnnotation w, List<String> errors) {
        String style = w.style().orElse("javaStyle").trim();
        if (!style.equalsIgnoreCase("javaStyle") && !style.equalsIgnoreCase("none")) {
            errors.add("rule " + rule.name() + " uses unsupported @whitespace style: " + style
                + " (allowed: javaStyle, none)");
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
            errors.add("rule " + rule.name() + " has duplicate @precedence annotations");
        }
        for (PrecedenceAnnotation p : precedenceAnnotations) {
            if (p.level() < 0) {
                errors.add("rule " + rule.name() + " has invalid @precedence level: " + p.level());
            }
        }
        if (hasLeftAssoc && hasRightAssoc) {
            // already reported by validateAssoc, but keep precedence checks deterministic.
            return;
        }
        if (!precedenceAnnotations.isEmpty() && !hasLeftAssoc && !hasRightAssoc) {
            errors.add("rule " + rule.name() + " uses @precedence but has no @leftAssoc/@rightAssoc");
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
                    errors.add("rule " + rule.name() + " precedence " + precedence
                        + " must be lower than referenced operator rule "
                        + refName + " precedence " + refPrecedence);
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
                errors.add("rule " + rule.name() + " uses @" + assoc.toLowerCase()
                    + "Assoc but has no @precedence");
                continue;
            }
            String existing = assocByLevel.get(level);
            if (existing == null) {
                assocByLevel.put(level, assoc);
                continue;
            }
            if (!existing.equals(assoc)) {
                errors.add("precedence level " + level
                    + " mixes associativity: " + existing + " and " + assoc);
            }
        }
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
}
