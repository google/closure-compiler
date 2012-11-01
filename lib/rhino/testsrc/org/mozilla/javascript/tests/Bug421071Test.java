/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * @(#)Bug421071Test.java
 *
 */

package org.mozilla.javascript.tests;

import junit.framework.TestCase;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

public class Bug421071Test extends TestCase {
    private ContextFactory factory;
    private TopLevelScope globalScope;
    private Script testScript;

    public void testProblemReplicator() throws Exception {
        // before debugging please put the breakpoint in the
        // NativeJavaPackage.getPkgProperty()
        // and observe names passed in there
        testScript = compileScript();
        runTestScript(); // this one does not get to the
                            // NativeJavaPackage.getPkgProperty() on my
                            // variables
        runTestScript(); // however this one does
    }

    private Script compileScript() {
        String scriptSource = "importPackage(java.util);\n"
                + "var searchmon = 3;\n"
                + "var searchday = 10;\n"
                + "var searchyear = 2008;\n"
                + "var searchwkday = 0;\n"
                + "\n"
                + "var myDate = Calendar.getInstance();\n // this is a java.util.Calendar"
                + "myDate.set(Calendar.MONTH, searchmon);\n"
                + "myDate.set(Calendar.DATE, searchday);\n"
                + "myDate.set(Calendar.YEAR, searchyear);\n"
                + "searchwkday.value = myDate.get(Calendar.DAY_OF_WEEK);";
        Script script;
        Context context = factory.enterContext();
        try {
            script = context.compileString(scriptSource, "testScript", 1, null);
            return script;
        } finally {
            Context.exit();
        }
    }

    private void runTestScript() throws InterruptedException {
        // will start new thread to get as close as possible to original
        // environment, however the same behavior is exposed using new
        // ScriptRunner(script).run();
        Thread thread = new Thread(new ScriptRunner(testScript));
        thread.start();
        thread.join();
    }

    static class DynamicScopeContextFactory extends ContextFactory {
        @Override
        public boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_DYNAMIC_SCOPE)
                return true;
            return super.hasFeature(cx, featureIndex);
        }
    }

    private TopLevelScope createGlobalScope() {
        factory = new DynamicScopeContextFactory();
        Context context = factory.enterContext();
        // noinspection deprecation
        TopLevelScope globalScope = new TopLevelScope(context);
        Context.exit();
        return globalScope;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        globalScope = createGlobalScope();
    }

    private class TopLevelScope extends ImporterTopLevel {
        private static final long serialVersionUID = 7831526694313927899L;

        public TopLevelScope(Context context) {
            super(context);
        }
    }

    private class ScriptRunner implements Runnable {
        private Script script;

        public ScriptRunner(Script script) {
            this.script = script;
        }

        public void run() {
            Context context = factory.enterContext();
            try {
                // Run each script in its own scope, to keep global variables
                // defined in each script separate
                Scriptable threadScope = context.newObject(globalScope);
                threadScope.setPrototype(globalScope);
                threadScope.setParentScope(null);
                script.exec(context, threadScope);
            } catch (Exception ee) {
                ee.printStackTrace();
            } finally {
                Context.exit();
            }
        }
    }
}
