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
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.RuleDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.SequenceBody;
import org.unlaxer.dsl.bootstrap.UBNFAST.StringSettingValue;
import org.unlaxer.dsl.bootstrap.UBNFAST.WhitespaceAnnotation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
            List<PrecedenceAnnotation> precedenceAnnotations = new ArrayList<>();

            for (Annotation annotation : rule.annotations()) {
                if (annotation instanceof MappingAnnotation m) {
                    mapping = m;
                } else if (annotation instanceof LeftAssocAnnotation) {
                    hasLeftAssoc = true;
                } else if (annotation instanceof PrecedenceAnnotation p) {
                    precedenceAnnotations.add(p);
                } else if (annotation instanceof WhitespaceAnnotation w) {
                    validateRuleWhitespace(rule, w, errors);
                }
            }

            if (mapping != null) {
                validateMapping(rule, mapping, errors);
            }
            if (hasLeftAssoc) {
                validateLeftAssoc(rule, mapping, errors);
            }
            validatePrecedence(rule, hasLeftAssoc, precedenceAnnotations, errors);
        }

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

    private static void validateLeftAssoc(RuleDecl rule, MappingAnnotation mapping, List<String> errors) {
        Set<String> captures = collectCaptureNames(rule.body());

        if (mapping == null) {
            errors.add("rule " + rule.name() + " uses @leftAssoc but has no @mapping");
        } else {
            Set<String> params = new LinkedHashSet<>(mapping.paramNames());
            for (String required : List.of("left", "op", "right")) {
                if (!params.contains(required)) {
                    errors.add("rule " + rule.name() + " uses @leftAssoc but @mapping("
                        + mapping.className() + ") params does not contain '" + required + "'");
                }
            }
        }

        for (String required : List.of("left", "op", "right")) {
            if (!captures.contains(required)) {
                errors.add("rule " + rule.name() + " uses @leftAssoc but capture @"
                    + required + " is missing");
            }
        }

        if (!containsRepeat(rule.body())) {
            errors.add("rule " + rule.name() + " uses @leftAssoc but has no repeat segment");
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
        if (!precedenceAnnotations.isEmpty() && !hasLeftAssoc) {
            errors.add("rule " + rule.name() + " uses @precedence but has no @leftAssoc");
        }
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
