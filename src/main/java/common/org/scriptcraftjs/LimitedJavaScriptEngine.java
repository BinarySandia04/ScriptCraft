package org.scriptcraftjs;

import java.io.IOException;
import java.io.Reader;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;

/** A private Rhino context factory; no JVM-wide engine configuration is changed. */
public final class LimitedJavaScriptEngine {
    public static final long DEFAULT_MAX_EXECUTION_MILLIS = 2000L;
    private final ContextFactory factory;
    private final ScriptableObject scope;
    private final long maxExecutionMillis;
    private volatile boolean executionLimitEnabled = true;

    /** Temporarily disable the wall-clock guard for trusted plugin initialization. */
    public void setExecutionLimitEnabled(boolean enabled) {
        executionLimitEnabled = enabled;
    }

    /** Error deliberately bypasses JavaScript catch/finally blocks. */
    public static final class InstructionLimitExceeded extends Error {
        public InstructionLimitExceeded(long limit) {
            super("Script stopped: maximum execution time exceeded (" + limit + " ms).");
        }
    }

    private static final class CountingContext extends Context {
        long deadlineNanos;
        CountingContext(ContextFactory factory) {
            super(factory);
        }

        void beginExecution(long maxExecutionMillis, boolean executionLimitEnabled) {
            if (!executionLimitEnabled) {
                deadlineNanos = Long.MAX_VALUE;
                return;
            }
            long now = System.nanoTime();
            if (deadlineNanos != 0 && now - deadlineNanos < 0) return;
            long durationNanos = maxExecutionMillis > Long.MAX_VALUE / 1000000L
                ? Long.MAX_VALUE : maxExecutionMillis * 1000000L;
            deadlineNanos = now > Long.MAX_VALUE - durationNanos
                ? Long.MAX_VALUE : now + durationNanos;
        }
    }

    public LimitedJavaScriptEngine(final long maxExecutionMillis, final ClassLoader loader) {
        if (maxExecutionMillis <= 0) {
            throw new IllegalArgumentException("max-execution-ms must be greater than zero");
        }
        this.maxExecutionMillis = maxExecutionMillis;
        factory = new ContextFactory() {
            @Override protected Context makeContext() {
                Context cx = new CountingContext(this);
                cx.setApplicationClassLoader(loader);
                cx.setLanguageVersion(Context.VERSION_ES6);
                cx.setOptimizationLevel(-1);
                cx.setInstructionObserverThreshold(1000);
                // ScriptCraft uses Nashorn's SomeJavaType.class reflection idiom.
                cx.setWrapFactory(new WrapFactory() {
                    @Override public Scriptable wrapJavaClass(Context context,
                            Scriptable scope, Class<?> javaClass) {
                        return new NativeJavaClass(scope, javaClass) {
                            @Override public Object get(String name, Scriptable start) {
                                if ("class".equals(name)) {
                                    return Context.getCurrentContext().getWrapFactory()
                                        .wrapAsJavaObject(Context.getCurrentContext(),
                                            getParentScope(), getClassObject(), Class.class);
                                }
                                return super.get(name, start);
                            }
                        };
                    }
                });
                return cx;
            }

            @Override protected Object doTopCall(Callable callable, Context cx,
                    Scriptable scope, Scriptable thisObj, Object[] args) {
                // Nested eval and Java -> JS re-entry share this budget. Java
                // adapters (events/timers) also enter through this factory.
                ((CountingContext) cx).beginExecution(maxExecutionMillis, executionLimitEnabled);
                return super.doTopCall(callable, cx, scope, thisObj, args);
            }

            @Override protected void observeInstructionCount(Context cx, int count) {
                if (!executionLimitEnabled) return;
                CountingContext counting = (CountingContext) cx;
                if (System.nanoTime() - counting.deadlineNanos >= 0) {
                    throw new InstructionLimitExceeded(maxExecutionMillis);
                }
            }
        };
        Context cx = factory.enterContext();
        try {
            scope = cx.initStandardObjects();
        } finally {
            Context.exit();
        }
    }

    public Object eval(String source) {
        Context cx = factory.enterContext();
        try {
            ((CountingContext) cx).beginExecution(maxExecutionMillis, executionLimitEnabled);
            return cx.evaluateString(scope, source, "<script>", 1, null);
        } finally {
            Context.exit();
        }
    }

    public Object eval(Reader source) throws IOException {
        Context cx = factory.enterContext();
        try {
            ((CountingContext) cx).beginExecution(maxExecutionMillis, executionLimitEnabled);
            return cx.evaluateReader(scope, source, "<script>", 1, null);
        } finally {
            Context.exit();
        }
    }

    public Object invokeFunction(String name, Object... args) throws NoSuchMethodException {
        Context cx = factory.enterContext();
        try {
            ((CountingContext) cx).beginExecution(maxExecutionMillis, executionLimitEnabled);
            Object function = ScriptableObject.getProperty(scope, name);
            if (!(function instanceof Function)) {
                throw new NoSuchMethodException(name);
            }
            Object[] jsArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                jsArgs[i] = Context.javaToJS(args[i], scope);
            }
            return ((Function) function).call(cx, scope, scope, jsArgs);
        } finally {
            // A quota abort intentionally skips JS finally, including the REPL's
            // cleanup. Never leave the previous player's identity in the scope.
            if ("__onCommand".equals(name)) {
                ScriptableObject.deleteProperty(scope, "self");
                ScriptableObject.deleteProperty(scope, "__engine");
            }
            Context.exit();
        }
    }
}
