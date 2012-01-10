package org.mozilla.javascript.tests;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.drivers.TestUtils;
import org.mozilla.javascript.tools.shell.Global;

import static org.junit.Assert.*;

/**
 * Run doctests in folder testsrc/doctests.
 *
 * A doctest is a test in the form of an interactive shell session; Rhino
 * collects and runs the inputs to the shell prompt and compares them to the
 * expected outputs.
 *
 */
@RunWith(Parameterized.class)
public class DoctestsTest {
    static final String baseDirectory = "testsrc" + File.separator + "doctests";
    static final String doctestsExtension = ".doctest";
    String name;
    String source;
    int optimizationLevel;

    public DoctestsTest(String name, String source, int optimizationLevel) {
        this.name = name;
        this.source = source;
        this.optimizationLevel = optimizationLevel;
    }

    public static File[] getDoctestFiles() {
        return TestUtils.recursiveListFiles(new File(baseDirectory),
                new FileFilter() {
                    public boolean accept(File f) {
                        return f.getName().endsWith(doctestsExtension);
                    }
            });
    }

    public static String loadFile(File f) throws IOException {
        int length = (int) f.length(); // don't worry about very long files
        char[] buf = new char[length];
        new FileReader(f).read(buf, 0, length);
        return new String(buf);
    }

    @Parameters
    public static Collection<Object[]> doctestValues() throws IOException {
        File[] doctests = getDoctestFiles();
        List<Object[]> result = new ArrayList<Object[]>();
        for (File f : doctests) {
            String contents = loadFile(f);
            result.add(new Object[] { f.getName(), contents, -1 });
            result.add(new Object[] { f.getName(), contents, 0 });
            result.add(new Object[] { f.getName(), contents, 9 });
        }
        return result;
    }

    // move "@Parameters" to this method to test a single doctest
    public static Collection<Object[]> singleDoctest() throws IOException {
        List<Object[]> result = new ArrayList<Object[]>();
        File f = new File(baseDirectory, "Counter.doctest");
        String contents = loadFile(f);
        result.add(new Object[] { f.getName(), contents, -1 });
        return result;
    }

    @Test
    public void runDoctest() throws Exception {
        ContextFactory factory = ContextFactory.getGlobal();
        Context cx = factory.enterContext();
        try {
            cx.setOptimizationLevel(optimizationLevel);
            Global global = new Global(cx);
            // global.runDoctest throws an exception on any failure
            int testsPassed = global.runDoctest(cx, global, source, name, 1);
            System.out.println(name + "(" + optimizationLevel + "): " +
                    testsPassed + " passed.");
            assertTrue(testsPassed > 0);
        } catch (Exception ex) {
          System.out.println(name + "(" + optimizationLevel + "): FAILED due to "+ex);
          throw ex;
        } finally {
            Context.exit();
        }
    }
}
