package org.mozilla.javascript.tests.commonjs.module;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.provider.StrongCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;

/**
 * @version $Id: ComplianceTest.java,v 1.1 2011/04/07 22:24:37 hannes%helma.at Exp $
 */
public class ComplianceTest extends TestCase
{
    public static TestSuite suite() throws Exception {
        final TestSuite suite = new TestSuite("InteroperableJS tests");
        final URL url = ComplianceTest.class.getResource("1.0");
        final String path = URLDecoder.decode(url.getFile(), System.getProperty("file.encoding"));
        final File testsDir = new File(path);
        addTests(suite, testsDir, "");
        return suite;
    }

    private static void addTests(TestSuite suite, File testDir, String name) {
        final File programFile = new File(testDir, "program.js");
        if(programFile.isFile()) {
            suite.addTest(createTest(testDir, name));
        }
        else {
            final File[] files = testDir.listFiles();
            for (File file : files) {
                if(file.isDirectory()) {
                    addTests(suite, file, name + "/" + file.getName());
                }
            }
        }
    }

    private static Test createTest(final File testDir, final String name) {
        return new TestCase(name) {
            @Override
            public int countTestCases() {
                return 1;
            }
            @Override
            public void runBare() throws Throwable {
                final Context cx = Context.enter();
                try {
                    cx.setOptimizationLevel(-1);
                    final Scriptable scope = cx.initStandardObjects();
                    ScriptableObject.putProperty(scope, "print", new Print(scope));
                    createRequire(testDir, cx, scope).requireMain(cx, "program");
                }
                finally {
                    Context.exit();
                }
            }
        };
    }

    private static Require createRequire(File dir, Context cx, Scriptable scope)
    throws URISyntaxException
    {
        return new Require(cx, scope, new StrongCachingModuleScriptProvider(
                new UrlModuleSourceProvider(Collections.singleton(new URI(
                        "file:" + dir.getAbsolutePath().replace(File.separatorChar,'/') + "/")),
                        Collections.singleton(new URI(ComplianceTest.class.getResource(".").toExternalForm() + "/")))),
                        null, null, false);
    }

    private static class Print extends ScriptableObject implements Function
    {
        Print(Scriptable scope) {
            setPrototype(ScriptableObject.getFunctionPrototype(scope));
        }

        public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                Object[] args)
        {
            if(args.length > 1 && "fail".equals(args[1])) {
                throw new AssertionFailedError(String.valueOf(args[0]));
            }
            return null;
        }

        public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
            throw new AssertionFailedError("Shouldn't be invoked as constructor");
        }

        @Override
        public String getClassName() {
            return "Function";
        }

    }
}