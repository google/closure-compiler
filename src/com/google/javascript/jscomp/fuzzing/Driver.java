/*
 * Copyright 2013 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp.fuzzing;

import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSModule;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SyntheticAst;
import com.google.javascript.rhino.Node;

import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UNDER DEVELOPMENT. DO NOT USE!
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class Driver {
  @Option(name = "--number_of_runs",
      usage = "The number of runs of the fuzzer. "
          + "If this option is missing, the driver will run until "
          + "first error (either in Fuzzer or Compiler) is found. ")
  private int numberOfRuns = -1;

  @Option(name = "--max_ast_size",
      usage = "The max number of nodes in the generated ASTs. Default: 100")
  private int maxASTSize = 100;

  @Option(name = "--compilation_level",
      usage = "Specifies the compilation level to use. " +
      "Default: SIMPLE_OPTIMIZATIONS")
  private CompilationLevel compilationLevel =
      CompilationLevel.SIMPLE_OPTIMIZATIONS;

  @Option(name = "--seed",
      usage = "Specifies the seed for the fuzzer. "
          + "It will override --number_of_runs to 1. "
          + "If not given, System.currentTimeMillis() will be used")
  private long seed = -1;

  @Option(name = "--logging_level",
      usage = "Specifies the logging level for the driver. "
          + "Default: INFO")
  private LoggingLevel level = LoggingLevel.INFO;

  @Option(name = "--config",
      usage = "Specifies the configuration file")
  private String configFileName;

  @Option(name = "--execute",
      usage = "Whether to execute the generated JavaScript")
  private boolean execute = false;

  private Logger logger;
  private JSONObject config;

  public Result compile(String code) throws IOException {
    Compiler.setLoggingLevel(level.getLevel());
    Compiler compiler = new Compiler();
    return compiler.compile(CommandLineRunner.getDefaultExterns(),
        Arrays.asList(SourceFile.fromCode("[fuzzedCode]", code)), getOptions());
  }

  public Result compile(Node script) throws IOException {
    CompilerInput input = new CompilerInput(new SyntheticAst(script));
    JSModule jsModule = new JSModule("fuzzedModule");
    jsModule.add(input);

    Compiler.setLoggingLevel(level.getLevel());
    Compiler compiler = new Compiler();
    compiler.setTimeout(30);
    return compiler.compileModules(
        CommandLineRunner.getDefaultExterns(),
        Arrays.asList(jsModule), getOptions());
  }

  private CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    compilationLevel.setOptionsForCompilationLevel(options);
    return options;
  }

  private JSONObject getConfig() {
    if (config == null) {
      File file = new File(configFileName);
      try {
        config = new JSONObject(Files.toString(
            file, StandardCharsets.UTF_8));
      } catch (JSONException | IOException e) {
        e.printStackTrace();
      }
    }
    return config;
  }

  private Logger getLogger() {
    if (logger == null) {
      logger = Logger.getLogger(Driver.class.getName());
      logger.setLevel(level.getLevel());
      for (Handler handler : logger.getHandlers()) {
        handler.setLevel(Level.ALL);
      }
    }
    return logger;
  }

  private boolean forever() {
    return numberOfRuns == -1;
  }

  private Node fuzz(Random random) {
    FuzzingContext context = new FuzzingContext(random, getConfig(), execute);
    ScriptFuzzer fuzzer = new ScriptFuzzer(context);
    Node script = null;
    try {
      script = fuzzer.generate(maxASTSize);
    } catch (Exception e) {
      getLogger().log(Level.SEVERE, "Fuzzer error!\nSeed: " + seed, e);
    }
    return script;
  }

  private boolean executeJS(String js1, String js2) {
    ExecutorService executor = Executors.newCachedThreadPool();
    NodeRunner node1 = new NodeRunner(js1);
    NodeRunner node2 = new NodeRunner(js2);
    String error1 = null, error2 = null;
    try {
      // set the timeout to maxASTSize milliseconds
      List<Future<String>>  futures = executor.invokeAll(
          Lists.newArrayList(node1, node2), maxASTSize, TimeUnit.MILLISECONDS);

      Future<String> future1 = futures.get(0);
      if (!future1.isCancelled()) {
        error1 = future1.get();
      }
      Future<String> future2 = futures.get(1);
      if (!future2.isCancelled()) {
        error2 = future2.get();
      }
    } catch (InterruptedException e) {
      getLogger().log(Level.INFO, "Timeout in executing JavaScript", e);
    } catch (ExecutionException e) {
      getLogger().log(Level.SEVERE, "Error in executing JavaScript", e);
    } finally {
      node1.process.destroy();
      node2.process.destroy();
    }
    if (error1 == null && error2 == null) {
      getLogger().info("Infinite loop!");
      return true;
    } else if (NodeRunner.isSame(error1, error2)) {
      if (error1.length() > 0) {
        getLogger().warning("JavaScript runtime error: " + error1);
      }
      if (getLogger().getLevel().intValue() > Level.WARNING.intValue()) {
        return true;
      } else {
        return false;
      }
    } else {
      StringBuilder sb =
          new StringBuilder("Different runtime errors!");
      sb.append("\nError1:").append(error1);
      sb.append("\nError2:").append(error2);
      getLogger().severe(sb.toString());
      return false;
    }
  }

  private void run() {
    if (seed != -1) {
      // When user specifies seed, only run once
      numberOfRuns = 1;
    }
    long currentSeed;
    for (int i = 0; forever() || i < numberOfRuns; i++) {
      currentSeed = seed == -1 ? System.currentTimeMillis() : seed;
      getLogger().info("Running fuzzer [" + i + " of " +
          numberOfRuns + "]");
      Random random = currentSeed == -1 ? new Random(currentSeed) :
        new Random(currentSeed);
      Node script = fuzz(random);
      if (script == null) {
        if (forever()) {
          break;
        }
      }
      String code1 = ScriptFuzzer.getPrettyCode(script);
      StringBuffer debugInfo = new StringBuffer("Seed: ").append(currentSeed);
      debugInfo.append("\nJavaScript: ").append(code1);
      try {
        Result result = compile(script);
        if (result.success) {
          if (result.warnings.length == 0) {
            getLogger().info(debugInfo.toString());
          } else {
            getLogger().warning(debugInfo.toString());
          }
        } else {
          getLogger().severe(debugInfo.toString());
          if (forever()) {
            break;
          }
        }
      } catch (Exception e) {
        getLogger().log(Level.SEVERE, "Compiler Crashed!", e);
        getLogger().severe(debugInfo.toString());
        if (forever()) {
          break;
        }
      }
      String code2 = ScriptFuzzer.getPrettyCode(script);
      debugInfo.append("\nCompiled Code: " + code2);
      if (execute) {
        if (!executeJS(code1, code2)) {
          getLogger().severe(debugInfo.toString());
          if (forever()) {
            break;
          }
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    Driver driver = new Driver();
    CmdLineParser parser = new CmdLineParser(driver);
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      // handling of wrong arguments
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.exit(1);
    }
    driver.run();
    System.exit(0);
  }

  enum LoggingLevel {
    OFF(Level.OFF),
    SEVERE(Level.SEVERE),
    WARNING(Level.WARNING),
    INFO(Level.INFO),
    CONFIG(Level.CONFIG),
    FINE(Level.FINE),
    FINER(Level.FINER),
    FINEST(Level.FINEST),
    ALL(Level.ALL);

    private Level level;

    private LoggingLevel(Level l) {
      level = l;
    }
    /**
     * @return the level
     */
    public Level getLevel() {
      return level;
    }
  }

  static class NodeRunner implements Callable<String> {
    private String js;
    private Process process;
    NodeRunner(String js) {
      this.js = "try {" + js +
          "} catch (e) {"
          // catching all errors
          + "if (e instanceof SyntaxError) {"
          // want to know more information about SyntaxError
          + "console.error(e.message);"
          + "} else if (e instanceof Error) {"
          + "console.error(e.name);"
          + "} else if (typeof(e) == \"object\" || typeof(e) == \"function\") {"
          + "console.error(typeof(e));"
          + "} else {"
          + "console.error(e);"
          + "}"
          + "}";
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.Callable#call()
     */
    @Override
    public String call() throws IOException {
      String[] command = {"node", "-e", js};
      Runtime runtime = Runtime.getRuntime();
      process = runtime.exec(command);
      return CharStreams.toString(
          new InputStreamReader(process.getErrorStream()));
    }

    public static boolean isSame(String error1, String error2) {
      if (error1 == null && error2 == null) {
        return true;
      } else if (error1 == null || error2 == null) {
        return false;
      } else {
        // exact match
        if (error1.equals(error2)) {
          return true;
        }
      }
      return false;
    }
  }
}
