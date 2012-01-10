/**
 *
 */
package org.mozilla.javascript.tests;

import junit.framework.TestCase;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.drivers.TestUtils;

/**
 */
public class ObserveInstructionCountTest extends TestCase {
    // Custom Context to store execution time.
    static class MyContext extends Context {
        MyContext(ContextFactory factory) {
            super(factory);
        }
        int quota;
    }

    static class QuotaExceeded extends RuntimeException {
        private static final long serialVersionUID = -8018441873635071899L;
    }

    @Override
    protected void setUp() {
        TestUtils.setGlobalContextFactory(new MyFactory());
    }

    @Override
    protected void tearDown() {
        TestUtils.setGlobalContextFactory(null);
    }

    static class MyFactory extends ContextFactory {

        @Override
        protected Context makeContext()
        {
            MyContext cx = new MyContext(this);
            // Make Rhino runtime call observeInstructionCount
            // each 500 bytecode instructions (if we're really enforcing
            // a quota of 2000, we could set this closer to 2000)
            cx.setInstructionObserverThreshold(500);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount)
        {
            MyContext mcx = (MyContext)cx;
            mcx.quota -= instructionCount;
            if (mcx.quota <= 0) {
                throw new QuotaExceeded();
            }
        }

        @Override
        protected Object doTopCall(Callable callable,
                                   Context cx, Scriptable scope,
                                   Scriptable thisObj, Object[] args)
        {
            MyContext mcx = (MyContext)cx;
            mcx.quota = 2000;
            return super.doTopCall(callable, cx, scope, thisObj, args);
        }
    }

    private void baseCase(int optimizationLevel, String source) {
        ContextFactory factory = new MyFactory();
        Context cx = factory.enterContext();
        cx.setOptimizationLevel(optimizationLevel);
        assertTrue(cx instanceof MyContext);
        try {
            Scriptable globalScope = cx.initStandardObjects();
            cx.evaluateString(globalScope,
                    source,
                    "test source", 1, null);
            fail();
        } catch (QuotaExceeded e) {
            // expected
        } catch (RuntimeException e) {
            fail(e.toString());
        } finally {
            Context.exit();
        }
    }

    public void testWhileTrueInGlobal() {
        String source = "var i=0; while (true) i++;";
        baseCase(-1, source); // interpreted mode
        baseCase(1, source); // compiled mode
    }

    public void testWhileTrueNoCounterInGlobal() {
        String source = "while (true);";
        baseCase(-1, source); // interpreted mode
        baseCase(1, source); // compiled mode
    }

    public void testWhileTrueInFunction() {
        String source = "var i=0; function f() { while (true) i++; } f();";
        baseCase(-1, source); // interpreted mode
        baseCase(1, source); // compiled mode
    }

    public void testForever() {
        String source = "for(;;);";
        baseCase(-1, source); // interpreted mode
        baseCase(1, source); // compiled mode
    }

 }
