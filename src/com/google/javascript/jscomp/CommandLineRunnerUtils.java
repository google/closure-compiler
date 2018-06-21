/*
 * Copyright 2018 The Closure Compiler Authors.
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
package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utility methods used by the CommandLineRunner classes that are also GWT compatible */
public class CommandLineRunnerUtils {
  /**
   * Creates module objects from a list of js module specifications.
   *
   * @param specs A list of js module specifications, not null or empty.
   * @param inputs A list of JS file paths, not null
   * @return An array of module objects
   */
  public static List<JSModule> createJsModules(List<JsModuleSpec> specs, List<SourceFile> inputs)
      throws IOException {
    checkState(specs != null);
    checkState(!specs.isEmpty());
    checkState(inputs != null);

    List<String> moduleNames = new ArrayList<>(specs.size());
    Map<String, JSModule> modulesByName = new LinkedHashMap<>();
    Map<String, Integer> modulesFileCountMap = new LinkedHashMap<>();
    int numJsFilesExpected = 0;
    int minJsFilesRequired = 0;
    for (JsModuleSpec spec : specs) {
      if (modulesByName.containsKey(spec.name)) {
        throw new FlagUsageException("Duplicate module name: " + spec.name);
      }
      JSModule module = new JSModule(spec.name);

      for (String dep : spec.deps) {
        JSModule other = modulesByName.get(dep);
        if (other == null) {
          throw new FlagUsageException(
              "Module '"
                  + spec.name
                  + "' depends on unknown module '"
                  + dep
                  + "'. Be sure to list modules in dependency order.");
        }
        module.addDependency(other);
      }

      // We will allow modules of zero input.
      if (spec.numJsFiles < 0) {
        numJsFilesExpected = -1;
      } else {
        minJsFilesRequired += spec.numJsFiles;
      }

      if (numJsFilesExpected >= 0) {
        numJsFilesExpected += spec.numJsFiles;
      }

      // Add modules in reverse order so that source files are allocated to
      // modules in reverse order. This allows the first module
      // (presumably the base module) to have a size of 'auto'
      moduleNames.add(0, spec.name);
      modulesFileCountMap.put(spec.name, spec.numJsFiles);
      modulesByName.put(spec.name, module);
    }

    final int totalNumJsFiles = inputs.size();

    if (numJsFilesExpected >= 0 || minJsFilesRequired > totalNumJsFiles) {
      if (minJsFilesRequired > totalNumJsFiles) {
        numJsFilesExpected = minJsFilesRequired;
      }

      if (numJsFilesExpected > totalNumJsFiles) {
        throw new FlagUsageException(
            "Not enough JS files specified. Expected "
                + numJsFilesExpected
                + " but found "
                + totalNumJsFiles);
      } else if (numJsFilesExpected < totalNumJsFiles) {
        throw new FlagUsageException(
            "Too many JS files specified. Expected "
                + numJsFilesExpected
                + " but found "
                + totalNumJsFiles);
      }
    }

    int numJsFilesLeft = totalNumJsFiles;
    int moduleIndex = 0;
    for (String moduleName : moduleNames) {
      // Parse module inputs.
      int numJsFiles = modulesFileCountMap.get(moduleName);
      JSModule module = modulesByName.get(moduleName);

      // Check if the first js module specified 'auto' for the number of files
      if (moduleIndex == moduleNames.size() - 1 && numJsFiles == -1) {
        numJsFiles = numJsFilesLeft;
      }

      List<SourceFile> moduleFiles = inputs.subList(numJsFilesLeft - numJsFiles, numJsFilesLeft);
      for (SourceFile input : moduleFiles) {
        module.add(input);
      }
      numJsFilesLeft -= numJsFiles;
      moduleIndex++;
    }

    return new ArrayList<>(modulesByName.values());
  }

  /**
   * Parses module wrapper specifications.
   *
   * @param specs A list of module wrapper specifications, not null. The spec format is: <code>
   *     name:wrapper</code>. Wrappers.
   * @param chunks The JS chunks whose wrappers are specified
   * @return A map from module name to module wrapper. Modules with no wrapper will have the empty
   *     string as their value in this map.
   */
  public static Map<String, String> parseChunkWrappers(List<String> specs, List<JSModule> chunks) {
    checkState(specs != null);

    Map<String, String> wrappers = Maps.newHashMapWithExpectedSize(chunks.size());

    // Prepopulate the map with module names.
    for (JSModule c : chunks) {
      wrappers.put(c.getName(), "");
    }

    for (String spec : specs) {
      // Format is "<name>:<wrapper>".
      int pos = spec.indexOf(':');
      if (pos == -1) {
        throw new FlagUsageException(
            "Expected module wrapper to have " + "<name>:<wrapper> format: " + spec);
      }

      // Parse module name.
      String name = spec.substring(0, pos);
      if (!wrappers.containsKey(name)) {
        throw new FlagUsageException("Unknown chunk: '" + name + "'");
      }
      String wrapper = spec.substring(pos + 1);
      // Support for %n% and %output%
      wrapper = wrapper.replace("%output%", "%s").replace("%n%", "\n");
      if (!wrapper.contains("%s")) {
        throw new FlagUsageException("No %s placeholder in chunk wrapper: '" + wrapper + "'");
      }

      wrappers.put(name, wrapper);
    }
    return wrappers;
  }

  /**
   * Create a map of constant names to constant values from a textual
   * description of the map.
   *
   * @param definitions A list of overriding definitions for defines in
   *     the form {@code <name>[=<val>]}, where {@code <val>} is a number, boolean, or
   *     single-quoted string without single quotes.
   */
  public static void createDefineOrTweakReplacements(List<String> definitions,
      CompilerOptions options, boolean tweaks) {
    // Parse the definitions
    for (String override : definitions) {
      String[] assignment = override.split("=", 2);
      String defName = assignment[0];

      if (defName.length() > 0) {
        String defValue = assignment.length == 1 ? "true" : assignment[1];

        boolean isTrue = defValue.equals("true");
        boolean isFalse = defValue.equals("false");
        if (isTrue || isFalse) {
          if (tweaks) {
            options.setTweakToBooleanLiteral(defName, isTrue);
          } else {
            options.setDefineToBooleanLiteral(defName, isTrue);
          }
          continue;
        } else if (defValue.length() > 1
            && ((defValue.charAt(0) == '\'' &&
            defValue.charAt(defValue.length() - 1) == '\'')
            || (defValue.charAt(0) == '\"' &&
            defValue.charAt(defValue.length() - 1) == '\"'))) {
          // If the value starts and ends with a single quote,
          // we assume that it's a string.
          String maybeStringVal =
              defValue.substring(1, defValue.length() - 1);
          if (maybeStringVal.indexOf(defValue.charAt(0)) == -1) {
            if (tweaks) {
              options.setTweakToStringLiteral(defName, maybeStringVal);
            } else {
              options.setDefineToStringLiteral(defName, maybeStringVal);
            }
            continue;
          }
        } else {
          try {
            double value = Double.parseDouble(defValue);
            if (tweaks) {
              options.setTweakToDoubleLiteral(defName, value);
            } else {
              options.setDefineToDoubleLiteral(defName, value);
            }
            continue;
          } catch (NumberFormatException e) {
            // do nothing, it will be caught at the end
          }

          if (defValue.length() > 0) {
            if (tweaks) {
              options.setTweakToStringLiteral(defName, defValue);
            } else {
              options.setDefineToStringLiteral(defName, defValue);
            }
            continue;
          }
        }
      }

      if (tweaks) {
        throw new RuntimeException(
            "--tweak flag syntax invalid: " + override);
      }
      throw new RuntimeException(
          "--define flag syntax invalid: " + override);
    }
  }

  /**
   * A helper function for creating the dependency options object.
   */
  public static DependencyOptions createDependencyOptions(
      CompilerOptions.DependencyMode dependencyMode,
      List<ModuleIdentifier> entryPoints) {
    if (dependencyMode == CompilerOptions.DependencyMode.STRICT) {
      if (entryPoints.isEmpty()) {
        throw new FlagUsageException(
            "When dependency_mode=STRICT, you must " + "specify at least one entry_point");
      }

      return new DependencyOptions()
          .setDependencyPruning(true)
          .setDependencySorting(true)
          .setMoocherDropping(true)
          .setEntryPoints(entryPoints);
    } else if (dependencyMode == CompilerOptions.DependencyMode.LOOSE || !entryPoints.isEmpty()) {
      return new DependencyOptions()
          .setDependencyPruning(true)
          .setDependencySorting(true)
          .setMoocherDropping(false)
          .setEntryPoints(entryPoints);
    }
    return null;
  }

  /** Represents a specification for a js module. */
  public static class JsModuleSpec {
    private final String name;
    // Number of input files, including js and zip files.
    private final int numInputs;
    private final ImmutableList<String> deps;
    // Number of input js files. All zip files should be expanded.
    int numJsFiles;

    private JsModuleSpec(String name, int numInputs, ImmutableList<String> deps) {
      this.name = name;
      this.numInputs = numInputs;
      this.deps = deps;
      this.numJsFiles = numInputs;
    }

    /**
     * @param specString The spec format is: <code>name:num-js-files[:[dep,...][:]]</code>. Module
     *     names must not contain the ':' character.
     * @param isFirstModule Whether the spec is for the first module.
     * @return A parsed js module spec.
     */
    public static JsModuleSpec create(String specString, boolean isFirstModule) {
      // Format is "<name>:<num-js-files>[:[<dep>,...][:]]".
      String[] parts = specString.split(":");
      if (parts.length < 2 || parts.length > 4) {
        throw new FlagUsageException(
            "Expected 2-4 colon-delimited parts in " + "js module spec: " + specString);
      }

      // Parse module name.
      String name = parts[0];

      // Parse module dependencies.
      String[] deps =
          parts.length > 2 && parts[2].length() > 0 ? parts[2].split(",") : new String[0];

      // Parse module inputs.
      int numInputs = -1;
      try {
        numInputs = Integer.parseInt(parts[1]);
      } catch (NumberFormatException ignored) {
        numInputs = -1;
      }

      // We will allow modules of zero input.
      if (numInputs < 0) {
        // A size of 'auto' is only allowed on the base module if
        // and it must also be the first module
        if (parts.length == 2 && "auto".equals(parts[1])) {
          if (!isFirstModule) {
            throw new FlagUsageException(
                "Invalid JS file count '"
                    + parts[1]
                    + "' for module: "
                    + name
                    + ". Only the first module may specify "
                    + "a size of 'auto' and it must have no dependencies.");
          }
        } else {
          throw new FlagUsageException(
              "Invalid JS file count '" + parts[1] + "' for module: " + name);
        }
      }

      return new JsModuleSpec(name, numInputs, ImmutableList.copyOf(deps));
    }

    public String getName() {
      return name;
    }

    public int getNumInputs() {
      return numInputs;
    }

    public ImmutableList<String> getDeps() {
      return deps;
    }

    public int getNumJsFiles() {
      return numJsFiles;
    }
  }

  /** An exception thrown when command-line flags are used incorrectly. */
  public static class FlagUsageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FlagUsageException(String message) {
      super(message);
    }
  }
}
