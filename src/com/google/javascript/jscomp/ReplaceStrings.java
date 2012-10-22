/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces JavaScript strings in the list of supplied methods with shortened
 * forms. Useful for replacing debug message such as: throw new
 * Error("Something bad happened"); with generated codes like: throw new
 * Error("a"); This makes the compiled JavaScript smaller and prevents us from
 * leaking details about the source code.
 *
 * Based in concept on the work by Jared Jacobs.
 */
class ReplaceStrings extends AbstractPostOrderCallback
    implements CompilerPass {
  static final DiagnosticType BAD_REPLACEMENT_CONFIGURATION =
      DiagnosticType.warning(
          "JSC_BAD_REPLACEMENT_CONFIGURATION",
          "Bad replacement configuration.");

  private final String DEFAULT_PLACEHOLDER_TOKEN = "`";
  private final String placeholderToken;
  private static final String REPLACE_ONE_MARKER = "?";
  private static final String REPLACE_ALL_MARKER = "*";

  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;

  //
  private final Map<String, Config> functions = Maps.newHashMap();
  private final Multimap<String, String> methods = HashMultimap.create();
  private final NameGenerator nameGenerator;
  private final Map<String, Result> results = Maps.newLinkedHashMap();

  /**
   * Describes a function to look for a which parameters to replace.
   */
  private class Config {
    // TODO(johnlenz): Support name "groups" so that unrelated strings can
    // reuse strings.  For example, event-id can reuse the names used for logger
    // classes.
    final String name;
    final int parameter;
    static final int REPLACE_ALL_VALUE = 0;

    Config(String name, int parameter) {
      this.name = name;
      this.parameter = parameter;
    }
  }

  /**
   * Describes a replacement that occurred.
   */
  class Result {
    // The original message with non-static content replaced with
    // {@code placeholderToken}.
    public final String original;
    public final String replacement;
    public final List<Location> replacementLocations = Lists.newLinkedList();

    Result(String original, String replacement) {
      this.original = original;
      this.replacement = replacement;
    }

    void addLocation(Node n) {
      replacementLocations.add(new Location(
          n.getSourceFileName(),
          n.getLineno(), n.getCharno()));
    }
  }

  /** Represent a source location where a replacement occurred. */
  class Location {
    public final String sourceFile;
    public final int line;
    public final int column;
    Location(String sourceFile, int line, int column) {
      this.sourceFile = sourceFile;
      this.line = line;
      this.column = column;
    }
  }

  /**
   * @param placeholderToken Separator to use between string parts. Used to replace
   *     non-static string content.
   * @param functionsToInspect A list of function configurations in the form of
   *     function($,,,)
   *   or
   *     class.prototype.method($,,,)
   * @param blacklisted A set of names that should not be used as replacement
   *     strings.  Useful to prevent unwanted strings for appearing in the
   *     final output.
   * where '$' is used to indicate which parameter should be replaced.
   */
  ReplaceStrings(
      AbstractCompiler compiler, String placeholderToken,
      List<String> functionsToInspect,
      Set<String> blacklisted,
      VariableMap previousMappings) {
    this.compiler = compiler;
    this.placeholderToken = placeholderToken.isEmpty()
        ? DEFAULT_PLACEHOLDER_TOKEN : placeholderToken;
    this.registry = compiler.getTypeRegistry();

    Iterable<String> reservedNames = blacklisted;
    if (previousMappings != null) {
      Set<String> previous =
          previousMappings.getOriginalNameToNewNameMap().keySet();
      reservedNames = Iterables.concat(blacklisted, previous);
      initMapping(previousMappings, blacklisted);
    }
    this.nameGenerator = createNameGenerator(reservedNames);

    // Initialize the map of functions to inspect for renaming candidates.
    parseConfiguration(functionsToInspect);
  }

  private void initMapping(
      VariableMap previousVarMap, Set<String> reservedNames) {
    Map<String,String> previous = previousVarMap.getOriginalNameToNewNameMap();
    for (Map.Entry<String,String> entry : previous.entrySet()) {
      String key = entry.getKey();
      if (!reservedNames.contains(key)) {
        String value = entry.getValue();
        results.put(value, new Result(value, key));
      }
    }
  }

  static final Predicate<Result> USED_RESULTS = new Predicate<Result>() {
    @Override
    public boolean apply(Result result) {
      // The list of locations may be empty if the map
      // was pre-populated from a previous map.
      return !result.replacementLocations.isEmpty();
    }
  };

  // Get the list of all replacements performed.
  List<Result> getResult() {
    return ImmutableList.copyOf(
        Iterables.filter(results.values(), USED_RESULTS));
  }

  // Get the list of replaces as a VariableMap
  VariableMap getStringMap() {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    for (Result result : Iterables.filter(results.values(), USED_RESULTS)) {
      map.put(result.replacement, result.original);
    }

    VariableMap stringMap = new VariableMap(map.build());
    return stringMap;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // TODO(johnlenz): Determine if it is necessary to support ".call" or
    // ".apply".
    switch (n.getType()) {
      case Token.NEW: // e.g. new Error('msg');
      case Token.CALL: // e.g. Error('msg');
        Node calledFn = n.getFirstChild();

        // Look for calls to static functions.
        String name = calledFn.getQualifiedName();
        if (name != null) {
          Config config = findMatching(name);
          if (config != null) {
            doSubstitutions(t, config, n);
            return;
          }
        }

        // Look for calls to class methods.
        if (NodeUtil.isGet(calledFn)) {
          Node rhs = calledFn.getLastChild();
          if (rhs.isName() || rhs.isString()) {
            String methodName = rhs.getString();
            Collection<String> classes = methods.get(methodName);
            if (classes != null) {
              Node lhs = calledFn.getFirstChild();
              if (lhs.getJSType() != null) {
                JSType type = lhs.getJSType().restrictByNotNullOrUndefined();
                Config config = findMatchingClass(type, classes);
                if (config != null) {
                  doSubstitutions(t, config, n);
                  return;
                }
              }
            }
          }
        }
        break;
    }
  }

  /**
   * @param name The function name to find.
   * @return The Config object for the name or null if no match was found.
   */
  private Config findMatching(String name) {
    Config config = functions.get(name);
    if (config == null) {
      name = name.replace('$', '.');
      config = functions.get(name);
    }
    return config;
  }

  /**
   * @return The Config object for the class match the specified type or null
   * if no match was found.
   */
  private Config findMatchingClass(
      JSType callClassType, Collection<String> declarationNames) {
    if (!callClassType.isNoObjectType() && !callClassType.isUnknownType()) {
      for (String declarationName : declarationNames) {
        String className = getClassFromDeclarationName(declarationName);
        JSType methodClassType = registry.getType(className);
        if (methodClassType != null
            && callClassType.isSubtype(methodClassType)) {
          return functions.get(declarationName);
        }
      }
    }
    return null;
  }

  /**
   * Replace the parameters specified in the config, if possible.
   */
  private void doSubstitutions(NodeTraversal t, Config config, Node n) {
    Preconditions.checkState(
        n.isNew() || n.isCall());

    if (config.parameter != Config.REPLACE_ALL_VALUE) {
      // Note: the first child is the function, but the parameter id is 1 based.
      Node arg = n.getChildAtIndex(config.parameter);
      if (arg != null) {
        replaceExpression(t, arg, n);
      }
    } else {
      // Replace all parameters.
      Node firstParam = n.getFirstChild().getNext();
      for (Node arg = firstParam; arg != null; arg = arg.getNext()) {
        arg = replaceExpression(t, arg, n);
      }
    }
  }

  /**
   * Replaces a string expression with a short encoded string expression.
   *
   * @param t The traversal
   * @param expr The expression node
   * @param parent The expression node's parent
   * @return The replacement node (or the original expression if no replacement
   *         is made)
   */
  private Node replaceExpression(NodeTraversal t, Node expr, Node parent) {
    Node replacement;
    String key = null;
    String replacementString;
    switch (expr.getType()) {
      case Token.STRING:
        key = expr.getString();
        replacementString = getReplacement(key);
        replacement = IR.string(replacementString);
        break;
      case Token.ADD:
        StringBuilder keyBuilder = new StringBuilder();
        Node keyNode = IR.string("");
        replacement = buildReplacement(expr, keyNode, keyBuilder);
        key = keyBuilder.toString();
        replacementString = getReplacement(key);
        keyNode.setString(replacementString);
        break;
      case Token.NAME:
        // If the referenced variable is a constant, use its value.
        Scope.Var var = t.getScope().getVar(expr.getString());
        if (var != null && var.isConst()) {
          Node value = var.getInitialValue();
          if (value != null && value.isString()) {
            key = value.getString();
            replacementString = getReplacement(key);
            replacement = IR.string(replacementString);
            break;
          }
        }
        return expr;
      default:
        // This may be a function call or a variable reference. We don't
        // replace these.
        return expr;
    }

    Preconditions.checkNotNull(key);
    Preconditions.checkNotNull(replacementString);
    recordReplacement(expr, key, replacementString);

    parent.replaceChild(expr, replacement);
    compiler.reportCodeChange();
    return replacement;
  }

  /**
   * Get a replacement string for the provide key text.
   */
  private String getReplacement(String key) {
    Result result = results.get(key);
    if (result != null) {
      return result.replacement;
    }

    String replacement = nameGenerator.generateNextName();
    result = new Result(key, replacement);
    results.put(key, result);
    return replacement;
  }

  /**
   * Record the location the replacement was made.
   */
  private void recordReplacement(Node n, String key, String replacement) {
    Result result = results.get(key);
    Preconditions.checkState(result != null);

    result.addLocation(n);
  }

  /**
   * Builds a replacement abstract syntax tree for the string expression {@code
   * expr}. Appends any string literal values that are encountered to
   * {@code keyBuilder}, to build the expression's replacement key.
   *
   * @param expr A JS expression that evaluates to a string value
   * @param prefix The JS expression to which {@code expr}'s replacement is
   *        logically being concatenated. It is a partial solution to the
   *        problem at hand and will either be this method's return value or a
   *        descendant of it.
   * @param keyBuilder A builder of the string expression's replacement key
   * @return The abstract syntax tree that should replace {@code expr}
   */
  private Node buildReplacement(
      Node expr, Node prefix, StringBuilder keyBuilder) {
    switch (expr.getType()) {
      case Token.ADD:
        Node left = expr.getFirstChild();
        Node right = left.getNext();
        prefix = buildReplacement(left, prefix, keyBuilder);
        return buildReplacement(right, prefix, keyBuilder);
      case Token.STRING:
        keyBuilder.append(expr.getString());
        return prefix;
      default:
        keyBuilder.append(placeholderToken);
        prefix = IR.add(prefix, IR.string(placeholderToken));
        return IR.add(prefix, expr.cloneTree());
    }
  }

  /**
   * From a provide name extract the method name.
   */
  private String getMethodFromDeclarationName(String fullDeclarationName) {
    String[] parts = fullDeclarationName.split("\\.prototype\\.");
    Preconditions.checkState(parts.length == 1 || parts.length == 2);
    if (parts.length == 2) {
      return parts[1];
    }
    return null;
  }

  /**
   * From a provide name extract the class name.
   */
  private String getClassFromDeclarationName(String fullDeclarationName) {
    String[] parts = fullDeclarationName.split("\\.prototype\\.");
    Preconditions.checkState(parts.length == 1 || parts.length == 2);
    if (parts.length == 2) {
      return parts[0];
    }
    return null;
  }

  /**
   * Build the data structures need by this pass from the provided
   * list of functions and methods.
   */
  private void parseConfiguration(List<String> functionsToInspect) {
    for (String function : functionsToInspect) {
      Config config = parseConfiguration(function);
      functions.put(config.name, config);

      String method = getMethodFromDeclarationName(config.name);
      if (method != null) {
        methods.put(method, config.name);
      }
    }
  }

  /**
   * Convert the provide string into a Config.  The string can be a static function:
   *    foo(,,?)
   *    foo.bar(?)
   * or a class method:
   *    foo.prototype.bar(?)
   * And is allowed to either replace all parameters using "*" or one parameter "?".
   * "," is used as a placeholder for ignored parameters.
   */
  private Config parseConfiguration(String function) {
    // Looks like this function_name(,$,)
    int first = function.indexOf('(');
    int last = function.indexOf(')');

    // TODO(johnlenz): Make parsing precondition checks JSErrors reports.
    Preconditions.checkState(first != -1 && last != -1);

    String name = function.substring(0, first);
    String params = function.substring(first+1, last);

    int paramCount = 0;
    int replacementParameter = -1;
    String[] parts = params.split(",");
    for (String param : parts) {
      paramCount++;
      if (param.equals(REPLACE_ALL_MARKER)) {
        Preconditions.checkState(paramCount == 1 && parts.length == 1);
        replacementParameter = Config.REPLACE_ALL_VALUE;
      } else if (param.equals(REPLACE_ONE_MARKER)) {
        // TODO(johnlenz): Support multiple.
        Preconditions.checkState(replacementParameter == -1);
        replacementParameter = paramCount;
      } else {
        // TODO(johnlenz): report an error.
        Preconditions.checkState(param.isEmpty(), "Unknown marker", param);
      }
    }

    Preconditions.checkState(replacementParameter != -1);
    return new Config(name, replacementParameter);
  }

  /**
   * Use a name generate to create names so the names overlap with the names
   * used for variable and properties.
   */
  private static NameGenerator createNameGenerator(Iterable<String> reserved) {
    final String namePrefix = "";
    final char[] reservedChars = new char[0];
    return new NameGenerator(
        ImmutableSet.copyOf(reserved), namePrefix, reservedChars);
  }
}
