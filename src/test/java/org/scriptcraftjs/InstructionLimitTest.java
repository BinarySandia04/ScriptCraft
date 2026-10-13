package org.scriptcraftjs;

import java.io.StringReader;
import org.scriptcraftjs.LimitedJavaScriptEngine.InstructionLimitExceeded;

/** Run with a process timeout: a regression must not hang the test runner. */
public final class InstructionLimitTest {
    private static int passed;

    public static Runnable asRunnable(Runnable task) {
        return task;
    }

    private static LimitedJavaScriptEngine engine(long limit) {
        return new LimitedJavaScriptEngine(limit, InstructionLimitTest.class.getClassLoader());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }

    private static void stops(LimitedJavaScriptEngine engine, String source) {
        try {
            engine.eval(source);
            throw new AssertionError("Script did not exceed its budget: " + source);
        } catch (InstructionLimitExceeded expected) {
            check(expected.getMessage().contains("execution time"), "Useful error message");
        }
        check(((Number) engine.eval("6 * 7")).intValue() == 42, "Recovery after abort");
    }

    public static void main(String[] args) throws Exception {
        LimitedJavaScriptEngine engine = engine(10000);
        check(Boolean.TRUE.equals(engine.eval(
            "new JavaAdapter(org.bukkit.event.Listener, {}) instanceof org.bukkit.event.Listener")),
            "Rhino Bukkit listener adapter");
        check(Boolean.TRUE.equals(engine.eval(
            "Array.prototype.some.call(org.bukkit.block.Block.class.methods, function(m) {"
            + " return m.name == 'getType'; })")), "Drone reflection compatibility");
        check(((Number) engine.eval(new StringReader(
            "var sum = 0; for (var i = 0; i < 10; i++) sum += i; sum;"))).intValue() == 45,
            "Finite code and Reader evaluation");
        stops(engine, "while (true) {}");
        stops(engine, "for (;;) {}");
        stops(engine, "do {} while (true);");
        stops(engine, "try { while (true) {} } catch (e) { while (true) {} }");
        stops(engine, "try { while (true) {} } finally { while (true) {} }");
        stops(engine, "eval('while (true) {}')");
        stops(engine, "new Function('while (true) {}')()");
        stops(engine, "function f() { for (var j=0;j<100;j++) {} f(); } f();");
        stops(engine, "while (true) { eval('1 + 1'); }");

        // Function adapters used by Bukkit's scheduler must retain the factory too.
        engine.eval("function getFunctionTask() { return Packages.org.scriptcraftjs.InstructionLimitTest"
            + ".asRunnable(function() { while (true) {} }); }");
        Runnable functionTask = (Runnable) org.mozilla.javascript.Context.jsToJava(
            engine.invokeFunction("getFunctionTask"), Runnable.class);
        try {
            functionTask.run();
            throw new AssertionError("Function callback did not stop");
        } catch (InstructionLimitExceeded expected) { passed++; }

        engine.eval("function nested(e) { while (true) { e.eval('1 + 1'); } }");
        try {
            engine.invokeFunction("nested", engine);
            throw new AssertionError("Java re-entry reset the budget");
        } catch (InstructionLimitExceeded expected) { passed++; }

        engine.eval("function __onCommand() { self = 'player'; __engine = {}; while (true) {} }");
        try {
            engine.invokeFunction("__onCommand");
            throw new AssertionError("Command did not stop");
        } catch (InstructionLimitExceeded expected) { passed++; }
        check(Boolean.TRUE.equals(engine.eval(
            "typeof self === 'undefined' && typeof __engine === 'undefined'")), "Player cleanup");

        engine.eval("function callback() { while (true) {} }\n"
            + "var task = new java.lang.Runnable({run: callback});\n"
            + "function getTask() { return task; }");
        Runnable task = (Runnable) org.mozilla.javascript.Context.jsToJava(
            engine.invokeFunction("getTask"), Runnable.class);
        for (int i = 0; i < 2; i++) {
            try {
                task.run();
                throw new AssertionError("Java callback did not stop");
            } catch (InstructionLimitExceeded expected) { passed++; }
        }

        // A long-running script is stopped by elapsed time, regardless of its
        // instruction count.
        stops(engine(1), "var n = 0; for (;;) n++;");
        for (int i = 0; i < 100; i++) engine.eval("for (var k=0;k<10;k++) {}");
        passed++;
        try {
            engine(0);
            throw new AssertionError("Zero limit accepted");
        } catch (IllegalArgumentException expected) { passed++; }
        System.out.println("Passed " + passed + " execution-time checks.");
    }
}
