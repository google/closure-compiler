/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.drivers;

import java.io.*;
import java.util.*;

import org.w3c.dom.*;

import org.mozilla.javascript.tools.shell.*;

/**
 * @version $Id: JsDriver.java,v 1.10 2009/05/15 12:30:45 nboyd%atg.com Exp $
 */
public class JsDriver {
    private JsDriver() {
    }

    private static String join(String[] list) {
        String rv = "";
        for (int i=0; i<list.length; i++) {
            rv += list[i];
            if (i+1 != list.length) {
                rv += ",";
            }
        }
        return rv;
    }

    private static class Tests {
        private File testDirectory;
        private String[] list;
        private String[] skip;

        Tests(File testDirectory, String[] list, String[] skip) throws IOException {
            this.testDirectory = testDirectory;
            this.list = getTestList(list);
            this.skip = getTestList(skip);
        }

        private String[] getTestList(String[] tests) throws IOException {
          ArrayList<String> list = new ArrayList<String>();
          for (int i=0; i < tests.length; i++) {
            if (tests[i].startsWith("@"))
              TestUtils.addTestsFromFile(tests[i].substring(1), list);
            else
              list.add(tests[i]);
          }
          return list.toArray(new String[0]);
        }

        private boolean matches(String path) {
            if (list.length == 0) return true;
            return TestUtils.matches(list, path);
        }

        private boolean excluded(String path) {
            if (skip.length == 0) return false;
            return TestUtils.matches(skip, path);
        }

        private void addFiles(List<Script> rv, String prefix, File directory) {
            File[] files = directory.listFiles();
            if (files == null) throw new RuntimeException("files null for " + directory);
            for (int i=0; i<files.length; i++) {
                String path = prefix + files[i].getName();
                if (ShellTest.DIRECTORY_FILTER.accept(files[i])) {
                    addFiles(rv, path + "/", files[i]);
                } else {
                    boolean isTopLevel = prefix.length() == 0;
                    if (ShellTest.TEST_FILTER.accept(files[i]) && matches(path) && !excluded(path) && !isTopLevel) {
                        rv.add(new Script(path, files[i]));
                    }
                }
            }
        }

        static class Script {
            private String path;
            private File file;

            Script(String path, File file) {
                this.path = path;
                this.file = file;
            }

            String getPath() {
                return path;
            }

            File getFile() {
                return file;
            }
        }

        Script[] getFiles() {
            ArrayList<Script> rv = new ArrayList<Script>();
            addFiles(rv, "", testDirectory);
            return rv.toArray(new Script[0]);
        }
    }

    private static class ConsoleStatus extends ShellTest.Status {
        private File jsFile;

        private Arguments.Console console;
        private boolean trace;

        private boolean failed;

        ConsoleStatus(Arguments.Console console, boolean trace) {
            this.console = console;
            this.trace = trace;
        }

        @Override
        public void running(File jsFile) {
            try {
                console.println("Running: " + jsFile.getCanonicalPath());
                this.jsFile = jsFile;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void failed(String s) {
            console.println("Failed: " + jsFile + ": " + s);
            failed = true;
        }

        @Override
        public void threw(Throwable t) {
            console.println("Failed: " + jsFile + " with exception.");
            console.println(ShellTest.getStackTrace(t));
            failed = true;
        }

        @Override
        public void timedOut() {
            console.println("Failed: " + jsFile + ": timed out.");
            failed = true;
        }

        @Override
        public void exitCodesWere(int expected, int actual) {
            if (expected != actual) {
                console.println("Failed: " + jsFile + " expected " + expected + " actual " + actual);
                failed = true;
            }
        }

        @Override
        public void outputWas(String s) {
            if (!failed) {
                console.println("Passed: " + jsFile);
                if (trace) {
                    console.println(s);
                }
            }
        }
    }

    //    returns true if node was found, false otherwise
    private static boolean setContent(Element node, String id, String content) {
        if (node.getAttribute("id").equals(id)) {
            node.setTextContent(node.getTextContent() + "\n" + content);
            return true;
        } else {
            NodeList children = node.getChildNodes();
            for (int i=0; i<children.getLength(); i++) {
                if (children.item(i) instanceof Element) {
                    Element e = (Element)children.item(i);
                    boolean rv = setContent( e, id, content );
                    if (rv) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Element getElementById(Element node, String id) {
        if (node.getAttribute("id").equals(id)) {
            return node;
        } else {
            NodeList children = node.getChildNodes();
            for (int i=0; i<children.getLength(); i++) {
                if (children.item(i) instanceof Element) {
                    Element rv = getElementById( (Element)children.item(i), id );
                    if (rv != null) {
                        return rv;
                    }
                }
            }
        }
        return null;
    }

	private static String newlineLineEndings(String s) {
		StringBuffer rv = new StringBuffer();
		for (int i=0; i<s.length(); i++) {
			if (s.charAt(i) == '\r') {
				if (i+1<s.length() && s.charAt(i+1) == '\n') {
					//    just skip \r
				} else {
					//    Macintosh, substitute \n
					rv.append('\n');
				}
			} else {
				rv.append(s.charAt(i));
			}
		}
		return rv.toString();
	}

    private static class HtmlStatus extends ShellTest.Status {
        private String testPath;
        private String bugUrl;
        private String lxrUrl;
        private Document html;
        private Element failureHtml;
        private boolean failed;

        private String output;

        HtmlStatus(String lxrUrl, String bugUrl, String testPath, Document html, Element failureHtml) {
            this.testPath = testPath;
            this.bugUrl = bugUrl;
            this.lxrUrl = lxrUrl;
            this.html = html;
            this.failureHtml = failureHtml;
        }

        @Override
        public void running(File file) {
        }

        @Override
        public void failed(String s) {
            failed = true;
            setContent(failureHtml, "failureDetails.reason", "Failure reason: \n" + s);
        }

        @Override
        public void exitCodesWere(int expected, int actual) {
            if (expected != actual) {
                failed = true;
                setContent(failureHtml, "failureDetails.reason", "expected exit code " + expected + " but got " + actual);
            }
        }

        @Override
        public void threw(Throwable e) {
            failed = true;
            setContent(failureHtml, "failureDetails.reason", "Threw Java exception:\n" + newlineLineEndings(ShellTest.getStackTrace(e)));
        }

        @Override
        public void timedOut() {
            failed = true;
            setContent(failureHtml, "failureDetails.reason", "Timed out.");
        }

        @Override
        public void outputWas(String s) {
            this.output = s;
        }

        private String getLinesStartingWith(String prefix) {
            BufferedReader r = new BufferedReader(new StringReader(output));
            String line = null;
            String rv = "";
            try {
                while( (line = r.readLine()) != null ) {
                    if (line.startsWith(prefix)) {
                        if (rv.length() > 0) {
                            rv += "\n";
                        }
                        rv += line;
                    }
                }
                return rv;
            } catch (IOException e) {
                throw new RuntimeException("Can't happen.");
            }
        }

        boolean failed() {
            return failed;
        }

        void finish() {
            if (failed) {
                getElementById(failureHtml, "failureDetails.status").setTextContent(getLinesStartingWith("STATUS:"));

                String bn = getLinesStartingWith("BUGNUMBER:");
                Element bnlink = getElementById(failureHtml, "failureDetails.bug.href");
                if (bn.length() > 0) {
                    String number = bn.substring("BUGNUMBER: ".length());
                    if (!number.equals("none")) {
                        bnlink.setAttribute("href", bugUrl + number);
                        getElementById(bnlink, "failureDetails.bug.number").setTextContent(number);
                    } else {
                        bnlink.getParentNode().removeChild(bnlink);
                    }
                } else {
                    bnlink.getParentNode().removeChild(bnlink);
                }

                getElementById(failureHtml, "failureDetails.lxr").setAttribute("href", lxrUrl + testPath);
                getElementById(failureHtml, "failureDetails.lxr.text").setTextContent(testPath);

                getElementById(html.getDocumentElement(), "retestList.text").setTextContent(
                    getElementById(html.getDocumentElement(), "retestList.text").getTextContent()
                    + testPath
                    + "\n"
                );

                getElementById(html.getDocumentElement(), "failureDetails").appendChild(failureHtml);
            }
        }
    }

	private static class XmlStatus extends ShellTest.Status {
		private Element target;
		private Date start;

		XmlStatus(String path, Element root) {
			this.target = root.getOwnerDocument().createElement("test");
			this.target.setAttribute("path", path);
			root.appendChild(target);
		}

		@Override
		public void running(File file) {
			this.start = new Date();
		}

		private Element createElement(Element parent, String name) {
			Element rv = parent.getOwnerDocument().createElement(name);
			parent.appendChild(rv);
			return rv;
		}

		private void finish() {
			Date end = new Date();
			long elapsed = end.getTime() - start.getTime();
			this.target.setAttribute("elapsed", String.valueOf(elapsed));
		}

		private void setTextContent(Element e, String content) {
			e.setTextContent( newlineLineEndings(content) );
		}

        @Override
        public void exitCodesWere(int expected, int actual) {
			finish();
			Element exit = createElement(target, "exit");
			exit.setAttribute("expected", String.valueOf(expected));
			exit.setAttribute("actual", String.valueOf(actual));
		}

        @Override
        public void timedOut() {
			finish();
			createElement(target, "timedOut");
		}

        @Override
        public void failed(String s) {
			finish();
			Element failed = createElement(target, "failed");
			setTextContent(failed, s);
		}

        @Override
        public void outputWas(String message) {
			finish();
			Element output = createElement(target, "output");
			setTextContent(output, message);
		}

        @Override
        public void threw(Throwable t) {
			finish();
			Element threw = createElement(target, "threw");
			setTextContent(threw, ShellTest.getStackTrace(t));
		}
	}

    private static class Results {
        private ShellContextFactory factory;
        private Arguments arguments;
        private File output;
        private boolean trace;

        private Document html;
        private Element failureHtml;

		private Document xml;

		private Date start;
        private int tests;
        private int failures;

        Results(ShellContextFactory factory, Arguments arguments, boolean trace) {
            this.factory = factory;
            this.arguments = arguments;

			File output = arguments.getOutputFile();
			if (output == null) {
				output = new File("rhino-test-results." + new java.text.SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date()) + ".html");
			}
			this.output = output;

            this.trace = trace;
        }

        private Document parse(InputStream in) {
            try {
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setValidating(false);
                javax.xml.parsers.DocumentBuilder dom = factory.newDocumentBuilder();
                return dom.parse(in);
            } catch (Throwable t) {
                throw new RuntimeException("Parser failure", t);
            }
        }

        private Document getTemplate() {
            return parse(getClass().getResourceAsStream("results.html"));
        }

        private void write(Document template, boolean xml) {
            try {
				File output = this.output;
				javax.xml.transform.TransformerFactory factory = javax.xml.transform.TransformerFactory.newInstance();
				javax.xml.transform.Transformer xform = factory.newTransformer();
				if (xml) {
					xform.setOutputProperty(javax.xml.transform.OutputKeys.METHOD, "xml");
					xform.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
					output = new File(output.getCanonicalPath() + ".xml");
				}
                xform.transform(
                    new javax.xml.transform.dom.DOMSource(template),
                    new javax.xml.transform.stream.StreamResult( new FileOutputStream(output) )
                );
            } catch (IOException e) {
                arguments.getConsole().println("Could not write results file to " + output + ": ");
                e.printStackTrace(System.err);
            } catch (javax.xml.transform.TransformerConfigurationException e) {
                throw new RuntimeException("Parser failure", e);
            } catch (javax.xml.transform.TransformerException e) {
                throw new RuntimeException("Parser failure", e);
            }
        }

		void start() {
            this.html = getTemplate();
            this.failureHtml = getElementById(html.getDocumentElement(), "failureDetails.prototype");
            if (this.failureHtml == null) {
                try {
                    javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
                        new javax.xml.transform.dom.DOMSource(html),
                        new javax.xml.transform.stream.StreamResult(System.err)
                    );
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
                throw new RuntimeException("No");
            }
            this.failureHtml.getParentNode().removeChild(this.failureHtml);

			try {
				this.xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.getDOMImplementation().createDocument(null, "results", null)
				;
				xml.getDocumentElement().setAttribute("timestamp", String.valueOf(new Date().getTime()));
				xml.getDocumentElement().setAttribute("optimization", String.valueOf(arguments.getOptimizationLevel()));
                xml.getDocumentElement().setAttribute("strict", String.valueOf(arguments.isStrict()));
                xml.getDocumentElement().setAttribute("timeout", String.valueOf(arguments.getTimeout()));
			} catch (javax.xml.parsers.ParserConfigurationException e) {
				throw new RuntimeException(e);
			}

			this.start = new Date();
		}

        void run(Tests.Script script, ShellTest.Parameters parameters) {
			String path = script.getPath();
			File test = script.getFile();
            ConsoleStatus cStatus = new ConsoleStatus(arguments.getConsole(), trace);
            HtmlStatus hStatus = new HtmlStatus(arguments.getLxrUrl(), arguments.getBugUrl(), path, html, (Element)failureHtml.cloneNode(true));
			XmlStatus xStatus = new XmlStatus(path, this.xml.getDocumentElement());
            ShellTest.Status status = ShellTest.Status.compose(new ShellTest.Status[] { cStatus, hStatus, xStatus });
            try {
                ShellTest.run(factory, test, parameters, status);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            tests++;
            if (hStatus.failed()) {
                failures++;
            }
            hStatus.finish();
        }

        private void set(Document document, String id, String value) {
            getElementById(document.getDocumentElement(), id).setTextContent(value);
        }

        void finish() {
			Date end = new Date();
            long elapsedMs = end.getTime() - start.getTime();
            set(html, "results.testlist", join(arguments.getTestList()));
            set(html, "results.skiplist", join(arguments.getSkipList()));
            String pct = new java.text.DecimalFormat("##0.00").format( (double)failures / (double)tests * 100.0 );
            set(html, "results.results", "Tests attempted: " + tests + " Failures: " + failures + " (" + pct + "%)");
            set(html, "results.platform", "java.home=" + System.getProperty("java.home")
                + "\n" + "java.version=" + System.getProperty("java.version")
                + "\n" + "os.name=" + System.getProperty("os.name")
            );
            set(html, "results.classpath", System.getProperty("java.class.path").replace(File.pathSeparatorChar, ' '));
            int elapsedSeconds = (int)(elapsedMs / 1000);
            int elapsedMinutes = elapsedSeconds / 60;
            elapsedSeconds = elapsedSeconds % 60;
            String elapsed = "" + elapsedMinutes + " minutes, " + elapsedSeconds + " seconds";
            set(html, "results.elapsed", elapsed);
            set(html, "results.time", new java.text.SimpleDateFormat("MMMM d yyyy h:mm:ss aa").format(new java.util.Date()));
            write(html, false);
			write(xml, true);
        }
    }

    private static class ShellTestParameters extends ShellTest.Parameters {
        private int timeout;

        ShellTestParameters(int timeout) {
            this.timeout = timeout;
        }

        @Override
        public int getTimeoutMilliseconds() {
            return timeout;
        }
    }

    void run(Arguments arguments) throws Throwable {
        if (arguments.help()) {
            System.out.println("See mozilla/js/tests/README-jsDriver.html; note that some options are not supported.");
            System.out.println("Consult the Java source code at testsrc/org/mozilla/javascript/JsDriver.java for details.");
            System.exit(0);
        }

        ShellContextFactory factory = new ShellContextFactory();
        factory.setOptimizationLevel(arguments.getOptimizationLevel());
        factory.setStrictMode(arguments.isStrict());

        File path = arguments.getTestsPath();
        if (path == null) {
            path = new File("../tests");
        }
        if (!path.exists()) {
            throw new RuntimeException("JavaScript tests not found at " + path.getCanonicalPath());
        }
        Tests tests = new Tests(path, arguments.getTestList(), arguments.getSkipList());
        Tests.Script[] all = tests.getFiles();
        arguments.getConsole().println("Running " + all.length + " tests.");

        Results results = new Results(factory, arguments, arguments.trace());

		results.start();
        for (int i=0; i<all.length; i++) {
            results.run(all[i], new ShellTestParameters(arguments.getTimeout()));
        }
		results.finish();
    }

    public static void main(Arguments arguments) throws Throwable {
        JsDriver driver = new JsDriver();
        driver.run(arguments);
    }

    private static class Arguments {
        private ArrayList<Option> options = new ArrayList<Option>();

        private Option bugUrl = new Option("b", "bugurl", false, false, "http://bugzilla.mozilla.org/show_bug.cgi?id=");
        private Option optimizationLevel = new Option("o", "optimization", false, false, "-1");
        private Option strict = new Option(null, "strict", false, true, null);
        private Option outputFile = new Option("f", "file", false, false, null);
        private Option help = new Option("h", "help", false, true, null);
        private Option logFailuresToConsole = new Option("k", "confail", false, true, null);
        private Option testList = new Option("l", "list", true, false, null);
        private Option skipList = new Option("L", "neglist", true, false, null);
        private Option testsPath = new Option("p", "testpath", false, false, null);
        private Option trace = new Option("t", "trace", false, true, null);
        private Option lxrUrl = new Option("u", "lxrurl", false, false, "http://lxr.mozilla.org/mozilla/source/js/tests/");
        private Option timeout = new Option(null, "timeout", false, false, "60000");

        public static class Console {
          public void print(String message) {
            System.out.print(message);
          }
          public void println(String message) {
            System.out.println(message);
          }
        }
        private Console console = new Console();

        private class Option {
            private String letterOption;
            private String wordOption;
            private boolean array;
            private boolean flag;
            private boolean ignored;

            private ArrayList<String> values = new ArrayList<String>();

            //    array: can this option have multiple values?
            //    flag: is this option a simple true/false switch?
            Option(String letterOption, String wordOption, boolean array,
                   boolean flag, String unspecified)
            {
                this.letterOption = letterOption;
                this.wordOption = wordOption;
                this.flag = flag;
                this.array = array;
                if (!flag && !array) {
                    this.values.add(unspecified);
                }
                options.add(this);
            }

            Option ignored() {
                this.ignored = true;
                return this;
            }

            int getInt() {
                return Integer.parseInt( getValue() );
            }

            String getValue() {
                return values.get(0);
            }

            boolean getSwitch() {
                return values.size() > 0;
            }

            File getFile() {
                if (getValue() == null) return null;
                return new File(getValue());
            }

            String[] getValues() {
                return values.toArray(new String[0]);
            }

            void process(List<String> arguments) {
                String option = arguments.get(0);
                String dashLetter = (letterOption == null) ? (String)null : "-" + letterOption;
                if (option.equals(dashLetter) || option.equals("--" + wordOption)) {
                    arguments.remove(0);
                    if (flag) {
                        values.add(0, (String)null );
                    } else if (array) {
                        while (arguments.size() > 0 &&
                               !arguments.get(0).startsWith("-"))
                        {
                            values.add(arguments.remove(0));
                        }
                    } else {
                        values.set(0, arguments.remove(0));
                    }
                    if (ignored) {
                        System.err.println("WARNING: " + option + " is ignored in the Java version of the test driver.");
                    }
                }
            }
        }

        //    -b URL, --bugurl=URL
        public String getBugUrl() {
            return bugUrl.getValue();
        }

        //    -c PATH, --classpath=PATH
        //    Does not apply; we will use the VM's classpath

        //    -e TYPE ..., --engine=TYPE ...
        //    Does not apply; was used to select between SpiderMonkey and Rhino

        //    Not in jsDriver.pl
        public int getOptimizationLevel() {
            return optimizationLevel.getInt();
        }

        //    --strict
        public boolean isStrict() {
          return strict.getSwitch();
        }

        //    -f FILE, --file=FILE
        public File getOutputFile() {
            return outputFile.getFile();
        }

        //    -h, --help
        public boolean help() {
            return help.getSwitch();
        }

        //    -j PATH, --javapath=PATH
        //    Does not apply; we will use this JVM

        //    -k, --confail
        //    TODO    Currently this is ignored; not clear precisely what it means (perhaps we should not be logging ordinary
        //            pass/fail to the console currently?)
        public boolean logFailuresToConsole() {
            return logFailuresToConsole.getSwitch();
        }

        //    -l FILE,... or --list=FILE,...
        public String[] getTestList() {
            return testList.getValues();
        }

        //    -L FILE,... or --neglist=FILE,...
        public String[] getSkipList() {
            return skipList.getValues();
        }

        //    -p PATH, --testpath=PATH
        public File getTestsPath() {
            return testsPath.getFile();
        }

        //    -s PATH, --shellpath=PATH
        //    Does not apply; we will use the Rhino shell with any classes given on the classpath

        //    -t, --trace
        public boolean trace() {
            return trace.getSwitch();
        }

        //    -u URL, --lxrurl=URL
        public String getLxrUrl() {
            return lxrUrl.getValue();
        }

        //
        //    New arguments
        //

        //    --timeout
        //    Milliseconds to wait for each test
        public int getTimeout() {
            return timeout.getInt();
        }

        public Console getConsole() {
            return console;
        }

        void process(List<String> arguments) {
            while(arguments.size() > 0) {
                String option = arguments.get(0);
                if (option.startsWith("--")) {
                    //    preprocess --name=value options into --name value
                    if (option.indexOf("=") != -1) {
                        arguments.set(0, option.substring(option.indexOf("=")));
                        arguments.add(1, option.substring(option.indexOf("=") + 1));
                    }
                } else if (option.startsWith("-")) {
                    //    could be multiple single-letter options, e.g. -kht, so preprocess them into -k -h -t
                    if (option.length() > 2) {
                        for (int i=2; i<option.length(); i++) {
                            arguments.add(1, "-" + option.substring(i,i+1));
                        }
                        arguments.set(0, option.substring(0,2));
                    }
                }
                int lengthBefore = arguments.size();
                for (int i=0; i<options.size(); i++) {
                    if (arguments.size() > 0) {
                        options.get(i).process(arguments);
                    }
                }

                if (arguments.size() == lengthBefore) {
					System.err.println("WARNING: ignoring unrecognized option " + arguments.remove(0));
                }
            }
        }
    }

    public static void main(String[] args) throws Throwable {
        ArrayList<String> arguments = new ArrayList<String>();
        arguments.addAll(Arrays.asList(args));
        Arguments clArguments = new Arguments();
        clArguments.process(arguments);
        main(clArguments);
    }
}
