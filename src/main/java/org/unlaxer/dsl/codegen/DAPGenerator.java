package org.unlaxer.dsl.codegen;

import org.unlaxer.dsl.bootstrap.UBNFAST.GrammarDecl;
import org.unlaxer.dsl.bootstrap.UBNFAST.StringSettingValue;

/**
 * GrammarDecl から {Name}DebugAdapter.java を生成する。
 *
 * 生成されるデバッグアダプターは DAP (Debug Adapter Protocol) over stdio で動作する。
 *
 * stopOnEntry: false (デフォルト)
 *   launch → configurationDone → parse → 結果を Debug Console に出力 → terminated
 *
 * stopOnEntry: true (ステップ実行)
 *   launch → configurationDone → parse → stopped(entry)
 *   → [F10] next → stopped(step) → ... → terminated
 *   → [F5]  continue → terminated
 *   stackTrace: 現在トークンの行/列をエディタで強調
 *   variables:  現在トークンのテキストと parser 名を表示
 */
public class DAPGenerator implements CodeGenerator {

    @Override
    public GeneratedSource generate(GrammarDecl grammar) {
        String packageName = getPackageName(grammar);
        String grammarName = grammar.name();
        String adapterClass = grammarName + "DebugAdapter";
        String parsersClass = grammarName + "Parsers";
        String mapperClass = grammarName + "Mapper";

        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(packageName).append(";\n\n");

        sb.append("import java.io.IOException;\n");
        sb.append("import java.nio.file.Files;\n");
        sb.append("import java.nio.file.Path;\n");
        sb.append("import java.util.ArrayList;\n");
        sb.append("import java.util.HashSet;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.Set;\n");
        sb.append("import java.util.concurrent.CompletableFuture;\n");
        sb.append("import org.eclipse.lsp4j.debug.*;\n");
        sb.append("import org.eclipse.lsp4j.debug.services.*;\n");
        sb.append("import org.unlaxer.Parsed;\n");
        sb.append("import org.unlaxer.StringSource;\n");
        sb.append("import org.unlaxer.Token;\n");
        sb.append("import org.unlaxer.context.ParseContext;\n");
        sb.append("import org.unlaxer.parser.Parser;\n");
        sb.append("\n");

        sb.append("public class ").append(adapterClass).append(" implements IDebugProtocolServer {\n\n");

        // fields
        sb.append("    private IDebugProtocolClient client;\n");
        sb.append("    private String pendingProgram;\n");
        sb.append("    private String runtimeMode = \"token\";\n");
        sb.append("    private boolean stopOnEntry;\n");
        sb.append("    private String sourceContent = \"\";\n");
        sb.append("    private List<Token> stepPoints = new ArrayList<>();\n");
        sb.append("    private int stepIndex = 0;\n");
        sb.append("    private int astNodeCount = 0;\n");
        sb.append("    private Set<Integer> breakpointLines = new HashSet<>();\n\n");

        // connect()
        sb.append("    public void connect(IDebugProtocolClient client) {\n");
        sb.append("        this.client = client;\n");
        sb.append("    }\n\n");

        // initialize()
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {\n");
        sb.append("        Capabilities cap = new Capabilities();\n");
        sb.append("        cap.setSupportsConfigurationDoneRequest(true);\n");
        sb.append("        return CompletableFuture.completedFuture(cap);\n");
        sb.append("    }\n\n");

        // launch() - store args, fire initialized
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<Void> launch(Map<String, Object> args) {\n");
        sb.append("        pendingProgram = (String) args.getOrDefault(\"program\", \"\");\n");
        sb.append("        runtimeMode = String.valueOf(args.getOrDefault(\"runtimeMode\", \"token\"));\n");
        sb.append("        stopOnEntry = Boolean.TRUE.equals(args.get(\"stopOnEntry\"));\n");
        sb.append("        client.initialized();\n");
        sb.append("        return CompletableFuture.completedFuture(null);\n");
        sb.append("    }\n\n");

        // configurationDone() - parse, then branch on stopOnEntry / breakpoints
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {\n");
        sb.append("        if (!parseAndCollectSteps()) {\n");
        sb.append("            return CompletableFuture.completedFuture(null); // error already sent\n");
        sb.append("        }\n");
        sb.append("        if (stopOnEntry && !stepPoints.isEmpty()) {\n");
        sb.append("            StoppedEventArguments stopped = new StoppedEventArguments();\n");
        sb.append("            stopped.setReason(\"entry\");\n");
        sb.append("            stopped.setThreadId(1);\n");
        sb.append("            stopped.setAllThreadsStopped(true);\n");
        sb.append("            client.stopped(stopped);\n");
        sb.append("        } else if (!breakpointLines.isEmpty()) {\n");
        sb.append("            // run to first breakpoint\n");
        sb.append("            int bp = findBreakpointIndex(-1);\n");
        sb.append("            if (bp >= 0) {\n");
        sb.append("                stepIndex = bp;\n");
        sb.append("                StoppedEventArguments stopped = new StoppedEventArguments();\n");
        sb.append("                stopped.setReason(\"breakpoint\");\n");
        sb.append("                stopped.setThreadId(1);\n");
        sb.append("                stopped.setAllThreadsStopped(true);\n");
        sb.append("                client.stopped(stopped);\n");
        sb.append("            } else {\n");
        sb.append("                sendOutput(\"stdout\", \"Parsed successfully: \" + pendingProgram + \"\\n\");\n");
        sb.append("                sendTerminated();\n");
        sb.append("            }\n");
        sb.append("        } else {\n");
        sb.append("            sendOutput(\"stdout\", \"Parsed successfully: \" + pendingProgram + \"\\n\");\n");
        sb.append("            sendTerminated();\n");
        sb.append("        }\n");
        sb.append("        return CompletableFuture.completedFuture(null);\n");
        sb.append("    }\n\n");

        // setBreakpoints() - store lines, return verified
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {\n");
        sb.append("        breakpointLines.clear();\n");
        sb.append("        SetBreakpointsResponse response = new SetBreakpointsResponse();\n");
        sb.append("        SourceBreakpoint[] requested = args.getBreakpoints();\n");
        sb.append("        int count = requested == null ? 0 : requested.length;\n");
        sb.append("        Breakpoint[] breakpoints = new Breakpoint[count];\n");
        sb.append("        for (int i = 0; i < count; i++) {\n");
        sb.append("            int line = requested[i].getLine();\n");
        sb.append("            breakpointLines.add(line);\n");
        sb.append("            Breakpoint bp = new Breakpoint();\n");
        sb.append("            bp.setVerified(true);\n");
        sb.append("            bp.setLine(line);\n");
        sb.append("            breakpoints[i] = bp;\n");
        sb.append("        }\n");
        sb.append("        response.setBreakpoints(breakpoints);\n");
        sb.append("        return CompletableFuture.completedFuture(response);\n");
        sb.append("    }\n\n");

        // next() - advance one step
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<Void> next(NextArguments args) {\n");
        sb.append("        stepIndex++;\n");
        sb.append("        if (stepIndex >= stepPoints.size()) {\n");
        sb.append("            sendOutput(\"stdout\", \"Completed: \" + pendingProgram + \"\\n\");\n");
        sb.append("            sendTerminated();\n");
        sb.append("        } else {\n");
        sb.append("            StoppedEventArguments stopped = new StoppedEventArguments();\n");
        sb.append("            stopped.setReason(\"step\");\n");
        sb.append("            stopped.setThreadId(1);\n");
        sb.append("            stopped.setAllThreadsStopped(true);\n");
        sb.append("            client.stopped(stopped);\n");
        sb.append("        }\n");
        sb.append("        return CompletableFuture.completedFuture(null);\n");
        sb.append("    }\n\n");

        // continue_() - run to next breakpoint, or terminate
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {\n");
        sb.append("        ContinueResponse response = new ContinueResponse();\n");
        sb.append("        response.setAllThreadsContinued(true);\n");
        sb.append("        int bp = findBreakpointIndex(stepIndex);\n");
        sb.append("        if (bp >= 0) {\n");
        sb.append("            stepIndex = bp;\n");
        sb.append("            StoppedEventArguments stopped = new StoppedEventArguments();\n");
        sb.append("            stopped.setReason(\"breakpoint\");\n");
        sb.append("            stopped.setThreadId(1);\n");
        sb.append("            stopped.setAllThreadsStopped(true);\n");
        sb.append("            client.stopped(stopped);\n");
        sb.append("        } else {\n");
        sb.append("            sendOutput(\"stdout\", \"Completed: \" + pendingProgram + \"\\n\");\n");
        sb.append("            sendTerminated();\n");
        sb.append("        }\n");
        sb.append("        return CompletableFuture.completedFuture(response);\n");
        sb.append("    }\n\n");

        // threads()
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<ThreadsResponse> threads() {\n");
        sb.append("        ThreadsResponse response = new ThreadsResponse();\n");
        sb.append("        org.eclipse.lsp4j.debug.Thread thread = new org.eclipse.lsp4j.debug.Thread();\n");
        sb.append("        thread.setId(1);\n");
        sb.append("        thread.setName(\"main\");\n");
        sb.append("        response.setThreads(new org.eclipse.lsp4j.debug.Thread[]{thread});\n");
        sb.append("        return CompletableFuture.completedFuture(response);\n");
        sb.append("    }\n\n");

        // stackTrace() - current token's line/col
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {\n");
        sb.append("        StackTraceResponse response = new StackTraceResponse();\n");
        sb.append("        if (!stepPoints.isEmpty() && stepIndex < stepPoints.size()) {\n");
        sb.append("            Token current = stepPoints.get(stepIndex);\n");
        sb.append("            int charOffset = current.source.offsetFromRoot().value();\n");
        sb.append("            int line = 0, col = 0;\n");
        sb.append("            for (int i = 0; i < charOffset && i < sourceContent.length(); i++) {\n");
        sb.append("                if (sourceContent.charAt(i) == '\\n') { line++; col = 0; }\n");
        sb.append("                else { col++; }\n");
        sb.append("            }\n");
        sb.append("            StackFrame frame = new StackFrame();\n");
        sb.append("            frame.setId(0);\n");
        sb.append("            frame.setName(\"step \" + (stepIndex + 1) + \"/\" + stepPoints.size());\n");
        sb.append("            frame.setLine(line + 1);   // DAP は 1-based\n");
        sb.append("            frame.setColumn(col + 1);\n");
        sb.append("            Source source = new Source();\n");
        sb.append("            source.setPath(pendingProgram);\n");
        sb.append("            source.setName(Path.of(pendingProgram).getFileName().toString());\n");
        sb.append("            frame.setSource(source);\n");
        sb.append("            response.setStackFrames(new StackFrame[]{frame});\n");
        sb.append("        } else if (pendingProgram != null && !pendingProgram.isEmpty()) {\n");
        sb.append("            StackFrame frame = new StackFrame();\n");
        sb.append("            frame.setId(0);\n");
        sb.append("            frame.setName(\"<program>\");\n");
        sb.append("            frame.setLine(1);\n");
        sb.append("            frame.setColumn(0);\n");
        sb.append("            Source source = new Source();\n");
        sb.append("            source.setPath(pendingProgram);\n");
        sb.append("            frame.setSource(source);\n");
        sb.append("            response.setStackFrames(new StackFrame[]{frame});\n");
        sb.append("        } else {\n");
        sb.append("            response.setStackFrames(new StackFrame[0]);\n");
        sb.append("        }\n");
        sb.append("        return CompletableFuture.completedFuture(response);\n");
        sb.append("    }\n\n");

        // scopes() - "Current Token" scope when stepping
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {\n");
        sb.append("        ScopesResponse response = new ScopesResponse();\n");
        sb.append("        if (!stepPoints.isEmpty() && stepIndex < stepPoints.size()) {\n");
        sb.append("            Scope scope = new Scope();\n");
        sb.append("            scope.setName(\"Current Token\");\n");
        sb.append("            scope.setVariablesReference(1);\n");
        sb.append("            scope.setExpensive(false);\n");
        sb.append("            response.setScopes(new Scope[]{scope});\n");
        sb.append("        } else {\n");
        sb.append("            response.setScopes(new Scope[0]);\n");
        sb.append("        }\n");
        sb.append("        return CompletableFuture.completedFuture(response);\n");
        sb.append("    }\n\n");

        // variables() - current token text + parser name
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {\n");
        sb.append("        VariablesResponse response = new VariablesResponse();\n");
        sb.append("        if (!stepPoints.isEmpty() && stepIndex < stepPoints.size()) {\n");
        sb.append("            Token current = stepPoints.get(stepIndex);\n");
        sb.append("            String text = current.source.sourceAsString().strip();\n");
        sb.append("            String parserName = current.getParser().getClass().getSimpleName();\n");
        sb.append("            Variable var = new Variable();\n");
        sb.append("            var.setName(parserName);\n");
        sb.append("            var.setValue(\"\\\"\" + text.replace(\"\\\"\", \"\\\\\\\"\") + \"\\\"\");\n");
        sb.append("            var.setType(\"Token\");\n");
        sb.append("            var.setVariablesReference(0);\n");
        sb.append("            Variable mode = new Variable();\n");
        sb.append("            mode.setName(\"runtimeMode\");\n");
        sb.append("            mode.setValue(runtimeMode);\n");
        sb.append("            mode.setType(\"String\");\n");
        sb.append("            mode.setVariablesReference(0);\n");
        sb.append("            Variable astNodes = new Variable();\n");
        sb.append("            astNodes.setName(\"astNodeCount\");\n");
        sb.append("            astNodes.setValue(String.valueOf(astNodeCount));\n");
        sb.append("            astNodes.setType(\"int\");\n");
        sb.append("            astNodes.setVariablesReference(0);\n");
        sb.append("            response.setVariables(new Variable[]{var, mode, astNodes});\n");
        sb.append("        } else {\n");
        sb.append("            response.setVariables(new Variable[0]);\n");
        sb.append("        }\n");
        sb.append("        return CompletableFuture.completedFuture(response);\n");
        sb.append("    }\n\n");

        // disconnect()
        sb.append("    @Override\n");
        sb.append("    public CompletableFuture<Void> disconnect(DisconnectArguments args) {\n");
        sb.append("        System.exit(0);\n");
        sb.append("        return CompletableFuture.completedFuture(null);\n");
        sb.append("    }\n\n");

        // parseAndCollectSteps() - read file, parse, collect step points
        sb.append("    private boolean parseAndCollectSteps() {\n");
        sb.append("        if (pendingProgram == null || pendingProgram.isEmpty()) {\n");
        sb.append("            sendOutput(\"stderr\", \"No program specified\\n\");\n");
        sb.append("            sendTerminated();\n");
        sb.append("            return false;\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("            sourceContent = Files.readString(Path.of(pendingProgram));\n");
        sb.append("        } catch (IOException e) {\n");
        sb.append("            sendOutput(\"stderr\", \"Cannot read file: \" + pendingProgram + \"\\n\");\n");
        sb.append("            sendTerminated();\n");
        sb.append("            return false;\n");
        sb.append("        }\n");
        sb.append("        Parser parser = ").append(parsersClass).append(".getRootParser();\n");
        sb.append("        ParseContext context = new ParseContext(createRootSourceCompat(sourceContent));\n");
        sb.append("        Parsed result = parser.parse(context);\n");
        sb.append("        context.close();\n");
        sb.append("        boolean fullParse = result.isSucceeded() &&\n");
        sb.append("            result.getConsumed().source.sourceAsString().length() == sourceContent.length();\n");
        sb.append("        if (!fullParse) {\n");
        sb.append("            int offset = result.isSucceeded()\n");
        sb.append("                ? result.getConsumed().source.sourceAsString().length() : 0;\n");
        sb.append("            sendOutput(\"stderr\", \"Parse error at offset \" + offset + \"\\n\");\n");
        sb.append("            sendTerminated();\n");
        sb.append("            return false;\n");
        sb.append("        }\n");
        sb.append("        stepPoints = new ArrayList<>();\n");
        sb.append("        collectStepPoints(result.getConsumed(), stepPoints);\n");
        sb.append("        if (stepPoints.isEmpty()) {\n");
        sb.append("            stepPoints.add(result.getConsumed()); // fallback: root token\n");
        sb.append("        }\n");
        sb.append("        astNodeCount = isAstRuntimeMode() ? tryCountAstNodes() : 0;\n");
        sb.append("        stepIndex = 0;\n");
        sb.append("        return true;\n");
        sb.append("    }\n\n");

        sb.append("    private static StringSource createRootSourceCompat(String source) {\n");
        sb.append("        try {\n");
        sb.append("            java.lang.reflect.Method m = StringSource.class.getMethod(\"createRootSource\", String.class);\n");
        sb.append("            Object v = m.invoke(null, source);\n");
        sb.append("            if (v instanceof StringSource s) {\n");
        sb.append("                return s;\n");
        sb.append("            }\n");
        sb.append("        } catch (Throwable ignored) {}\n");
        sb.append("        try {\n");
        sb.append("            for (java.lang.reflect.Constructor<?> c : StringSource.class.getDeclaredConstructors()) {\n");
        sb.append("                Class<?>[] types = c.getParameterTypes();\n");
        sb.append("                if (types.length == 0 || types[0] != String.class) {\n");
        sb.append("                    continue;\n");
        sb.append("                }\n");
        sb.append("                Object[] args = new Object[types.length];\n");
        sb.append("                args[0] = source;\n");
        sb.append("                c.setAccessible(true);\n");
        sb.append("                Object v = c.newInstance(args);\n");
        sb.append("                if (v instanceof StringSource s) {\n");
        sb.append("                    return s;\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        } catch (Throwable ignored) {}\n");
        sb.append("        throw new IllegalStateException(\"No compatible StringSource initializer found\");\n");
        sb.append("    }\n\n");

        sb.append("    private int tryCountAstNodes() {\n");
        sb.append("        try {\n");
        sb.append("            Object ast = ").append(mapperClass).append(".parse(sourceContent);\n");
        sb.append("            return countAstNodes(ast);\n");
        sb.append("        } catch (Throwable ignored) {\n");
        sb.append("            return 0;\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    private int countAstNodes(Object node) {\n");
        sb.append("        if (node == null) {\n");
        sb.append("            return 0;\n");
        sb.append("        }\n");
        sb.append("        int count = 1;\n");
        sb.append("        java.lang.reflect.Method[] methods = node.getClass().getMethods();\n");
        sb.append("        for (java.lang.reflect.Method method : methods) {\n");
        sb.append("            if (method.getParameterCount() != 0) {\n");
        sb.append("                continue;\n");
        sb.append("            }\n");
        sb.append("            String name = method.getName();\n");
        sb.append("            if (\"getClass\".equals(name) || \"hashCode\".equals(name) || \"toString\".equals(name)) {\n");
        sb.append("                continue;\n");
        sb.append("            }\n");
        sb.append("            try {\n");
        sb.append("                Object value = method.invoke(node);\n");
        sb.append("                if (value == null) {\n");
        sb.append("                    continue;\n");
        sb.append("                }\n");
        sb.append("                if (value instanceof List<?> list) {\n");
        sb.append("                    for (Object element : list) {\n");
        sb.append("                        if (isAstNodeCandidate(element)) {\n");
        sb.append("                            count += countAstNodes(element);\n");
        sb.append("                        }\n");
        sb.append("                    }\n");
        sb.append("                    continue;\n");
        sb.append("                }\n");
        sb.append("                if (isAstNodeCandidate(value)) {\n");
        sb.append("                    count += countAstNodes(value);\n");
        sb.append("                }\n");
        sb.append("            } catch (Throwable ignored) {}\n");
        sb.append("        }\n");
        sb.append("        return count;\n");
        sb.append("    }\n\n");

        sb.append("    private boolean isAstNodeCandidate(Object value) {\n");
        sb.append("        if (value == null) {\n");
        sb.append("            return false;\n");
        sb.append("        }\n");
        sb.append("        String className = value.getClass().getName();\n");
        sb.append("        return className.startsWith(\"").append(packageName).append(".").append(grammarName).append("AST\");\n");
        sb.append("    }\n\n");

        // collectStepPoints() - dispatch by runtime mode
        sb.append("    private void collectStepPoints(Token token, List<Token> out) {\n");
        sb.append("        if (isAstRuntimeMode()) {\n");
        sb.append("            collectAstStepPoints(token, out);\n");
        sb.append("            return;\n");
        sb.append("        }\n");
        sb.append("        collectTokenStepPoints(token, out);\n");
        sb.append("    }\n\n");

        sb.append("    private boolean isAstRuntimeMode() {\n");
        sb.append("        return \"ast\".equalsIgnoreCase(runtimeMode) || \"ast_evaluator\".equalsIgnoreCase(runtimeMode);\n");
        sb.append("    }\n\n");

        // collectTokenStepPoints() - depth-first token collection
        sb.append("    private void collectTokenStepPoints(Token token, List<Token> out) {\n");
        sb.append("        if (token == null) return;\n");
        sb.append("        if (token.filteredChildren == null || token.filteredChildren.isEmpty()) {\n");
        sb.append("            out.add(token);\n");
        sb.append("            return;\n");
        sb.append("        }\n");
        sb.append("        for (Token child : token.filteredChildren) {\n");
        sb.append("            collectTokenStepPoints(child, out);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // collectAstStepPoints() - placeholder for AST evaluator stepping
        sb.append("    private void collectAstStepPoints(Token token, List<Token> out) {\n");
        sb.append("        // Current fallback keeps token-level stepping; replace with AST-node stepping when mapper/evaluator runtime is wired.\n");
        sb.append("        collectTokenStepPoints(token, out);\n");
        sb.append("    }\n\n");

        // getLineForToken() - 1-based line number from char offset
        sb.append("    private int getLineForToken(Token t) {\n");
        sb.append("        int charOffset = t.source.offsetFromRoot().value();\n");
        sb.append("        int line = 1;\n");
        sb.append("        for (int i = 0; i < charOffset && i < sourceContent.length(); i++) {\n");
        sb.append("            if (sourceContent.charAt(i) == '\\n') { line++; }\n");
        sb.append("        }\n");
        sb.append("        return line;\n");
        sb.append("    }\n\n");

        // findBreakpointIndex() - first token after fromIndex whose line is in breakpointLines
        sb.append("    private int findBreakpointIndex(int fromIndex) {\n");
        sb.append("        for (int i = fromIndex + 1; i < stepPoints.size(); i++) {\n");
        sb.append("            if (breakpointLines.contains(getLineForToken(stepPoints.get(i)))) {\n");
        sb.append("                return i;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return -1;\n");
        sb.append("    }\n\n");

        // sendOutput()
        sb.append("    private void sendOutput(String category, String output) {\n");
        sb.append("        if (client == null) return;\n");
        sb.append("        OutputEventArguments event = new OutputEventArguments();\n");
        sb.append("        event.setCategory(category);\n");
        sb.append("        event.setOutput(output);\n");
        sb.append("        client.output(event);\n");
        sb.append("    }\n\n");

        // sendTerminated()
        sb.append("    private void sendTerminated() {\n");
        sb.append("        if (client == null) return;\n");
        sb.append("        client.terminated(new TerminatedEventArguments());\n");
        sb.append("        ExitedEventArguments exited = new ExitedEventArguments();\n");
        sb.append("        exited.setExitCode(0);\n");
        sb.append("        client.exited(exited);\n");
        sb.append("    }\n");

        sb.append("}\n");

        return new GeneratedSource(packageName, adapterClass, sb.toString());
    }

    private String getPackageName(GrammarDecl grammar) {
        return grammar.settings().stream()
            .filter(s -> "package".equals(s.key()))
            .map(s -> s.value() instanceof StringSettingValue sv ? sv.value() : "")
            .findFirst()
            .orElse("generated");
    }
}
