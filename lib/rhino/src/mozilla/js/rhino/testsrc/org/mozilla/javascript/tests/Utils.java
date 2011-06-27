package org.mozilla.javascript.tests;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;

/**
 * Misc utilities to make test code easier.
 */
public class Utils
{
	/**
	 * Runs the action successively with all available optimization levels
	 */
	public static void runWithAllOptimizationLevels(final ContextAction action)
	{
		runWithOptimizationLevel(action, -1);
		runWithOptimizationLevel(action, 0);
		runWithOptimizationLevel(action, 1);
	}

	/**
	 * Runs the provided action at the given optimization level
	 */
	public static void runWithOptimizationLevel(final ContextAction action, final int optimizationLevel)
	{
    	final Context cx = new ContextFactory().enterContext();
    	try
    	{
    		cx.setOptimizationLevel(optimizationLevel);
    		action.run(cx);
    	}
    	finally
    	{
    		Context.exit();
    	}
	}
}
