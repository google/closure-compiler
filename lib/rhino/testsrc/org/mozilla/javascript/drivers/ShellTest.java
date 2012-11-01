/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.drivers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;

import org.mozilla.javascript.*;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.Main;
import org.mozilla.javascript.tools.shell.ShellContextFactory;

/**
 * @version $Id: ShellTest.java,v 1.14 2011/03/29 15:17:49 hannes%helma.at Exp $
 */
public class ShellTest {
    public static final FileFilter DIRECTORY_FILTER = new FileFilter() {
        public boolean accept(File pathname)
        {
            return pathname.isDirectory() && !pathname.getName().equals("CVS");
        }
    };

    public static final FileFilter TEST_FILTER = new FileFilter() {
        public boolean accept(File pathname)
        {
            return pathname.getName().endsWith(".js")
                    && !pathname.getName().equals("shell.js")
                    && !pathname.getName().equals("browser.js")
                    && !pathname.getName().equals("template.js");
        }
    };

    public static String getStackTrace(Throwable t) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        t.printStackTrace(new PrintStream(bytes));
        return new String(bytes.toByteArray());
    }

    private static void runFileIfExists(Context cx, Scriptable global, File f) {
        if(f.isFile()) {
            Main.processFileNoThrow(cx, global, f.getPath());
        }
    }

    private static class TestState {
        boolean finished;
        ErrorReporterWrapper errors;
        int exitCode = 0;
    }

    public static abstract class Status {
        private boolean negative;

        public final void setNegative() {
            this.negative = true;
        }

        public final boolean isNegative() {
            return this.negative;
        }

        public final void hadErrors(JsError[] errors) {
            if (!negative && errors.length > 0) {
                failed("JavaScript errors:\n" + JsError.toString(errors));
            } else if (negative && errors.length == 0) {
                failed("Should have produced runtime error.");
            }
        }

        public final void hadErrors(File jsFile, JsError[] errors) {
            if (!negative && errors.length > 0) {
                failed("JavaScript errors in " + jsFile + ":\n" + JsError.toString(errors));
            } else if (negative && errors.length == 0) {
                failed("Should have produced runtime error in " + jsFile + ".");
            }
        }

        public abstract void running(File jsFile);

        public abstract void failed(String s);
        public abstract void threw(Throwable t);
        public abstract void timedOut();
        public abstract void exitCodesWere(int expected, int actual);
        public abstract void outputWas(String s);

        static Status compose(final Status[] array) {
            return new Status() {
                @Override
                public void running(File file) {
					for (int i=0; i<array.length; i++) {
						array[i].running(file);
					}
                }
                @Override
                public void threw(Throwable t) {
					for (int i=0; i<array.length; i++) {
						array[i].threw(t);
					}
				}
                @Override
                public void failed(String s) {
					for (int i=0; i<array.length; i++) {
						array[i].failed(s);
					}
                }
                @Override
                public void exitCodesWere(int expected, int actual) {
					for (int i=0; i<array.length; i++) {
						array[i].exitCodesWere(expected, actual);
					}
                }
                @Override
                public void outputWas(String s) {
					for (int i=0; i<array.length; i++) {
						array[i].outputWas(s);
					}
                }
                @Override
                public void timedOut() {
					for (int i=0; i<array.length; i++) {
						array[i].timedOut();
					}
                }
            };
        }

        static class JsError {
            static String toString(JsError[] e) {
                String rv = "";
                for (int i=0; i<e.length; i++) {
                    rv += e[i].toString();
                    if (i+1 != e.length) {
                        rv += "\n";
                    }
                }
                return rv;
            }

            private String message;
            private String sourceName;
            private int line;
            private String lineSource;
            private int lineOffset;

            JsError(String message, String sourceName, int line, String lineSource, int lineOffset) {
                this.message = message;
                this.sourceName = sourceName;
                this.line = line;
                this.lineSource = lineSource;
                this.lineOffset = lineOffset;
            }

            @Override
            public String toString() {
                String locationLine = "";
                if (sourceName != null)
                    locationLine += sourceName + ":";
                if (line != 0)
                    locationLine += line + ": ";
                locationLine += message;
                String sourceLine = this.lineSource;
                String errCaret = null;
                if (lineSource != null) {
                    errCaret = "";
                    for (int i=0; i<lineSource.length(); i++) {
                        char c = lineSource.charAt(i);
                        if (i < lineOffset-1) {
                            if (c == '\t') {
                                errCaret += "\t";
                            } else {
                                errCaret += " ";
                            }
                        } else if (i == lineOffset-1) {
                            errCaret += "^";
                        }
                    }
                }
                String rv = locationLine;
                if (sourceLine != null) {
                    rv += "\n" + sourceLine;
                }
                if (errCaret != null) {
                    rv += "\n" + errCaret;
                }
                return rv;
            }

            String getMessage() {
                return message;
            }

            String getSourceName() {
                return sourceName;
            }

            int getLine() {
                return line;
            }

            String getLineSource() {
                return lineSource;
            }

            int getLineOffset() {
                return lineOffset;
            }
        }
    }

    private static class ErrorReporterWrapper implements ErrorReporter {
        private ErrorReporter original;
        private ArrayList<Status.JsError> errors = new ArrayList<Status.JsError>();

        ErrorReporterWrapper(ErrorReporter original) {
            this.original = original;
        }

        private void addError(String string, String string0, int i, String string1, int i0) {
            errors.add( new Status.JsError(string, string0, i, string1, i0) );
        }

        public void warning(String string, String string0, int i, String string1, int i0) {
            original.warning(string, string0, i, string1, i0);
        }

        public EvaluatorException runtimeError(String string, String string0, int i, String string1, int i0) {
            return original.runtimeError(string, string0, i, string1, i0);
        }

        public void error(String string, String string0, int i, String string1, int i0) {
            addError(string, string0, i, string1, i0);
        }
    }

    public static abstract class Parameters {
        public abstract int getTimeoutMilliseconds();
    }

    @SuppressWarnings(value={"deprecation"})
    private static void callStop(Thread t) {
        t.stop();
    }

    public static void run(final ShellContextFactory shellContextFactory,
            final File jsFile, final Parameters parameters,
            final Status status) throws Exception {
        final Global global = new Global();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream p = new PrintStream(out);
        global.setOut(p);
        global.setErr(p);
        global.defineFunctionProperties(
                new String[] { "options" }, ShellTest.class,
                ScriptableObject.DONTENUM | ScriptableObject.PERMANENT |
                  ScriptableObject.READONLY);
        // test suite expects keywords to be disallowed as identifiers
        shellContextFactory.setAllowReservedKeywords(false);
        final TestState testState = new TestState();
        if (jsFile.getName().endsWith("-n.js")) {
            status.setNegative();
        }
        final Throwable thrown[] = {null};

        Thread t = new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    shellContextFactory.call(new ContextAction()
                    {
                        public Object run(Context cx)
                        {
                            status.running(jsFile);
                            testState.errors = new ErrorReporterWrapper(cx.getErrorReporter());
                            cx.setErrorReporter( testState.errors );
                            global.init(cx);
                            try {
                                runFileIfExists(cx, global, new File(jsFile.getParentFile().getParentFile().getParentFile(), "shell.js"));
                                runFileIfExists(cx, global, new File(jsFile.getParentFile().getParentFile(), "shell.js"));
                                runFileIfExists(cx, global, new File(jsFile.getParentFile(), "shell.js"));
                                runFileIfExists(cx, global, jsFile);
                                status.hadErrors(jsFile, testState.errors.errors.toArray(new Status.JsError[0]));
                            } catch (ThreadDeath e) {
                            } catch (Throwable t) {
                                status.threw(t);
                            }
                            return null;
                        }
                    });
                }
                catch (Error t)
                {
                    thrown[0] = t;
                }
                catch (RuntimeException t)
                {
                    thrown[0] = t;
                }
                finally {
                    synchronized(testState)
                    {
                        testState.finished = true;
                    }
                }
            }
        }, jsFile.getPath());
        t.setDaemon(true);
        t.start();
        t.join(parameters.getTimeoutMilliseconds());
        synchronized(testState)
        {
            if(!testState.finished)
            {
                callStop(t);
                status.timedOut();
            }
        }
        int expectedExitCode = 0;
        p.flush();
        status.outputWas(new String(out.toByteArray()));
        BufferedReader r = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(out.toByteArray())));
        String failures = "";
        for(;;)
        {
            String s = r.readLine();
            if(s == null)
            {
                break;
            }
            if(s.indexOf("FAILED!") != -1)
            {
                failures += s + '\n';
            }
            int expex = s.indexOf("EXPECT EXIT CODE ");
            if(expex != -1)
            {
                expectedExitCode = s.charAt(expex + "EXPECT EXIT CODE ".length()) - '0';
            }
        }
        if (thrown[0] != null)
        {
        	status.threw(thrown[0]);
        }
        status.exitCodesWere(expectedExitCode, testState.exitCode);
        if(failures != "")
        {
            status.failed(failures);
        }
    }

    // Global function to mimic options() function in spidermonkey.
    // It looks like this toggles jit compiler mode in spidermonkey
    // when called with "jit" as argument. Our version is a no-op
    // and returns an empty string.
    public static String options() {
        return "";
    }
}
