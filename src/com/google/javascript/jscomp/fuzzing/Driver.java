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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSModule;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SyntheticAst;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.rhino.Node;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
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
          + "If this option is missing, the driver will run forever")
  private int numberOfRuns = -1;

  @Option(name = "--max_ast_size",
      usage = "The max number of nodes in the generated ASTs. Default: 100")
  private int maxASTSize;

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
      required = true,
      usage = "Specifies the configuration file")
  private String configFileName;

  @Option(name = "--execute",
      usage = "Whether to execute the generated JavaScript")
  private boolean execute = false;

  @Option(name = "--stop_on_error",
      usage = "Whether to stop fuzzing once an error is found")
  private boolean stopOnError = false;

  private Logger logger;
  private JsonObject config;

  public Result compile(Node script) throws IOException {
    CompilerInput input = new CompilerInput(new SyntheticAst(script));
    JSModule jsModule = new JSModule("fuzzedModule");
    jsModule.add(input);

    Compiler.setLoggingLevel(level.getLevel());
    Compiler compiler = new Compiler();
    compiler.setTimeout(30);
    compiler.disableThreads();
    return compiler.compileModules(
        CommandLineRunner.getDefaultExterns(),
        Arrays.asList(jsModule), getOptions());
  }

  private CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    compilationLevel.setOptionsForCompilationLevel(options);
    options.variableRenaming = VariableRenamingPolicy.OFF;
    return options;
  }

  private JsonObject getConfig() {
    if (config == null) {
      File file = new File(configFileName);
      try {
        config = new Gson().fromJson(Files.toString(
            file, StandardCharsets.UTF_8), JsonObject.class);
      } catch (JsonParseException | IOException e) {
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

  private Node fuzz(FuzzingContext context) {
    ScriptFuzzer fuzzer = new ScriptFuzzer(context);
    return fuzzer.generate(maxASTSize);
  }

  private boolean executeJS(String js1, String js2) {
    ExecutorService executor = Executors.newCachedThreadPool();
    NodeRunner node1 = new NodeRunner(js1);
    NodeRunner node2 = new NodeRunner(js2);
    String[] output1 = null, output2 = null;
    try {
      // set the timeout to maxASTSize milliseconds
      List<Future<String[]>>  futures = executor.invokeAll(
          Lists.newArrayList(node1, node2), maxASTSize, TimeUnit.MILLISECONDS);

      Future<String[]> future1 = futures.get(0);
      if (!future1.isCancelled()) {
        output1 = future1.get();
      }
      Future<String[]> future2 = futures.get(1);
      if (!future2.isCancelled()) {
        output2 = future2.get();
      }
    } catch (InterruptedException e) {
      getLogger().log(Level.INFO, "Timeout in executing JavaScript", e);
    } catch (ExecutionException e) {
      getLogger().log(Level.SEVERE, "Error in executing JavaScript", e);
    } finally {
      node1.process.destroy();
      node2.process.destroy();
    }
    if (output1 == null && output2 == null) {
      getLogger().info("Infinite loop!");
      return true;
    } else if (NodeRunner.isSame(output1, output2)) {
      boolean hasError = false;
      if (output1 != null && output1[1].length() > 0) {
        getLogger().warning("First JavaScript has a runtime error: " +
            output1[1]);
        hasError = true;
      }
      if (output2 != null && output2[1].length() > 0) {
        getLogger().warning("Second JavaScript has a runtime error: " +
            output2[1]);
        hasError = true;
      }
      return !(hasError && getLogger().getLevel().intValue() < Level.WARNING.intValue());
    } else {
      StringBuilder sb =
          new StringBuilder("Different outputs!");
      sb.append("\nOutput 1:");
      if (output1 != null) {
        sb.append(output1[0]).append(output1[1]);
      } else {
        sb.append("null");
      }
      sb.append("\nOutput 2:");
      if (output2 != null) {
        sb.append(output2[0]).append(output2[1]);
      } else {
        sb.append("null");
      }
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
    for (int i = 0; numberOfRuns == -1 || i < numberOfRuns; i++) {
      currentSeed = seed == -1 ? System.currentTimeMillis() : seed;
      getLogger().info("Running fuzzer [" + i + " of " +
          numberOfRuns + "]");
      Random random = currentSeed == -1 ? new Random(currentSeed) :
        new Random(currentSeed);
      FuzzingContext context = new FuzzingContext(random, getConfig(), execute);
      Node script = null;
      try {
        script = fuzz(context);
      } catch (RuntimeException e) {
        getLogger().log(Level.SEVERE, "Fuzzer error: ", e);
        if (stopOnError) {
          break;
        } else {
          continue;
        }
      }
      String code1 = ScriptFuzzer.getPrettyCode(script);
      StringBuilder debugInfo = new StringBuilder("Seed: ").append(currentSeed);
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
          if (stopOnError) {
            break;
          }
        }
      } catch (Exception e) {
        getLogger().log(Level.SEVERE, "Compiler Crashed!", e);
        getLogger().severe(debugInfo.toString());
        if (stopOnError) {
          break;
        }
      }
      String code2 = ScriptFuzzer.getPrettyCode(script);
      debugInfo.append("\nCompiled Code: " + code2);
      String setUpCode = getSetupCode(context.scopeManager);
//      System.out.print(setUpCode);
      if (execute) {
        if (!executeJS(setUpCode + code1, setUpCode + code2)) {
          getLogger().severe(debugInfo.toString());
          if (stopOnError) {
            break;
          }
        }
      }
      getLogger().info(debugInfo.toString());
    }
  }

  private static String getSetupCode(ScopeManager scopeManager) {
    Collection<String> vars = Collections2.transform(
        Lists.newArrayList(scopeManager.localScope().symbols),
        new Function<Symbol, String>() {
          @Override
          public String apply(Symbol s) {
            return "'" + s.name + "'=" + s.name;
          }
        });
    String setUpCode = "function toString(value) {\n" +
        "    if (value instanceof Array) {\n" +
        "        var string = \"[\";\n" +
        "        for (var i in value) {\n" +
        "            string += toString(value[i]) + \",\";\n" +
        "        }\n" +
        "        string += ']';\n" +
        "        return string;\n" +
        "    } else if (value instanceof Function) {\n" +
        "        return value.length;\n" +
        "    } else {\n" +
        "        return value;\n" +
        "    }\n" +
        "}\n" +
        "\n" +
        "process.on('uncaughtException', function(e) {\n" +
        "    console.log(\"Errors: \");\n" +
        "    if (e instanceof Error) {\n" +
        "        console.log(e.name);\n" +
        "    } else {\n" +
        "        console.log(typeof(e));\n" +
        "    }\n" +
        "});\n" +
        "\n" +
        "process.on(\"exit\", function(e) {\n" +
        "    console.log(\"Variables:\");\n" +
        "    var allvars = " + vars + ";\n" +
        "    console.log(toString(allvars));\n" +
        "});\n" +
        "";
    return setUpCode;
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

  static class NodeRunner implements Callable<String[]> {
    private String js;
    private Process process;
    NodeRunner(String js) {
      this.js = js;
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.Callable#call()
     */
    @Override
    public String[] call() throws IOException {
      String[] command = {"node", "-e", js};
      Runtime runtime = Runtime.getRuntime();
      process = runtime.exec(command);
      String[] results = new String[2];
      results[0] = CharStreams.toString(new InputStreamReader(process.getInputStream(), UTF_8));
      results[1] = CharStreams.toString(new InputStreamReader(process.getErrorStream(), UTF_8));
      return results;
    }

    public static boolean isSame(String[] output1, String[] output2) {
      if (output1 == null && output2 == null) {
        return true;
      } else if (output1 == null || output2 == null) {
        return false;
      } else {
        return output1[0].equals(output2[0]);
      }
    }
  }
}
