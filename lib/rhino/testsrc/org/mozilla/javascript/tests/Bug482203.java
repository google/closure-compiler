package org.mozilla.javascript.tests;

import java.io.InputStreamReader;

import junit.framework.TestCase;

import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class Bug482203 extends TestCase {
    public void testJsApi() throws Exception {
        Context cx = Context.enter();
        cx.setOptimizationLevel(-1);
        Script script = cx.compileReader(new InputStreamReader(
                Bug482203.class.getResourceAsStream("conttest.js")),
                "", 1, null);
        Scriptable scope = cx.initStandardObjects();
        script.exec(cx, scope);
        for(;;)
        {
            Object cont = ScriptableObject.getProperty(scope, "c");
            if(cont == null)
            {
                break;
            }
            ((Callable)cont).call(cx, scope, scope, new Object[] { null });
        }
    }
    public void testJavaApi() throws Exception {
        Context cx = Context.enter();
        try {
	        cx.setOptimizationLevel(-1);
	        Script script = cx.compileReader(new InputStreamReader(
	                Bug482203.class.getResourceAsStream("conttest.js")),
	                "", 1, null);
	        Scriptable scope = cx.initStandardObjects();
	        cx.executeScriptWithContinuations(script, scope);
	        for(;;)
	        {
	            Object cont = ScriptableObject.getProperty(scope, "c");
	            if(cont == null)
	            {
	                break;
	            }
	            cx.resumeContinuation(cont, scope, null);
	        }
        } finally {
        	Context.exit();
        }
    }
}
