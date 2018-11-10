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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces JavaScript strings in the list of supplied methods with shortened forms. Useful for
 * replacing debug message such as: throw new Error("Something bad happened"); with generated codes
 * like: throw new Error("a"); This makes the compiled JavaScript smaller and prevents us from
 * leaking details about the source code.
 *
 * <p>Based in concept on the work by Jared Jacobs.
 */
class ReplaceStrings extends AbstractPostOrderCallback implements CompilerPass {

  static final DiagnosticType BAD_REPLACEMENT_CONFIGURATION =
      DiagnosticType.warning("JSC_BAD_REPLACEMENT_CONFIGURATION", "Bad replacement configuration.");

  static final DiagnosticType STRING_REPLACEMENT_TAGGED_TEMPLATE =
      DiagnosticType.warning(
          "JSC_STRING_REPLACEMENT_TAGGED_TEMPLATE",
          "Cannot string-replace arguments of a template literal tag function.");

  private static final String DEFAULT_PLACEHOLDER_TOKEN = "`";
  public static final String EXCLUSION_PREFIX = ":!";
  private final String placeholderToken;
  private static final String REPLACE_ONE_MARKER = "?";
  private static final String REPLACE_ALL_MARKER = "*";

  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;

  //
  private final Map<String, Config> functions = new HashMap<>();
  private final Multimap<String, String> methods = HashMultimap.create();
  private final DefaultNameGenerator nameGenerator;
  private final Map<String, Result> results = new LinkedHashMap<>();

  /** Describes a function to look for a which parameters to replace. */
  private static class Config {
    // TODO(johnlenz): Support name "groups" so that unrelated strings can
    // reuse strings.  For example, event-id can reuse the names used for logger
    // classes.
    final String name;
    final List<Integer> parameters;
    final ImmutableSet<String> excludedFilenameSuffixes;

    static final int REPLACE_ALL_VALUE = 0;

    Config(
        String name,
        List<Integer> replacementParameters,
        ImmutableSet<String> excludedFilenameSuffixes) {
      this.name = name;
      this.parameters = replacementParameters;
      this.excludedFilenameSuffixes = excludedFilenameSuffixes;
    }

    public boolean isReplaceAll() {
      return parameters.size() == 1 && parameters.contains(REPLACE_ALL_VALUE);
    }
  }

  /** Describes a replacement that occurred. */
  static class Result {
    // The original message with non-static content replaced with
    // {@code placeholderToken}.
    public final String original;
    public final String replacement;
    public boolean didReplacement = false;

    Result(String original, String replacement) {
      this.original = original;
      this.replacement = replacement;
    }
  }

  /**
   * @param placeholderToken Separator to use between string parts. Used to replace non-static
   *     string content.
   * @param functionsToInspect A list of function configurations in the form of
   *     function($,,,):exclued_filename_suffix1,excluded_filename_suffix2,... or
   *     class.prototype.method($,,,):exclued_filename_suffix1,excluded_filename_suffix2,...
   * @param blacklisted A set of names that should not be used as replacement strings. Useful to
   *     prevent unwanted strings for appearing in the final output. where '$' is used to indicate
   *     which parameter should be replaced.
   *     <p>excluded_filename_suffix is a list of files whose callsites for a given function pattern
   *     should be ignored.
   */
  ReplaceStrings(
      AbstractCompiler compiler,
      String placeholderToken,
      List<String> functionsToInspect,
      Set<String> blacklisted,
      VariableMap previousMappings) {
    this.compiler = compiler;
    this.placeholderToken =
        placeholderToken.isEmpty() ? DEFAULT_PLACEHOLDER_TOKEN : placeholderToken;
    this.registry = compiler.getTypeRegistry();

    Iterable<String> reservedNames = blacklisted;
    if (previousMappings != null) {
      Set<String> previous = previousMappings.getOriginalNameToNewNameMap().keySet();
      reservedNames = Iterables.concat(blacklisted, previous);
      initMapping(previousMappings, blacklisted);
    }
    this.nameGenerator = createNameGenerator(reservedNames);

    // Initialize the map of functions to inspect for renaming candidates.
    parseConfiguration(functionsToInspect);
  }

  private void initMapping(VariableMap previousVarMap, Set<String> reservedNames) {
    Map<String, String> previous = previousVarMap.getOriginalNameToNewNameMap();
    for (Map.Entry<String, String> entry : previous.entrySet()) {
      String key = entry.getKey();
      if (!reservedNames.contains(key)) {
        String value = entry.getValue();
        results.put(value, new Result(value, key));
      }
    }
  }

  static final Predicate<Result> USED_RESULTS =
      new Predicate<Result>() {
        @Override
        public boolean apply(Result result) {
          // The list of locations may be empty if the map
          // was pre-populated from a previous map.
          return result.didReplacement;
        }
      };

  // Get the list of all replacements performed.
  List<Result> getResult() {
    return ImmutableList.copyOf(Iterables.filter(results.values(), USED_RESULTS));
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
    // TODO(johnlenz): Determine if it is necessary to support ".call" or ".apply".
    switch (n.getToken()) {
      case NEW: // e.g. new Error('msg');
      case CALL: // e.g. Error('msg');
      case TAGGED_TEMPLATELIT: // e.g. Error`msg` - not supported!
        Node calledFn = n.getFirstChild();

        // Look for calls to static functions.
        String name = calledFn.getOriginalQualifiedName();
        if (name != null) {
          Config config = findMatching(name, n.getSourceFileName());
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
            String originalMethodName = rhs.getParent().getOriginalName();
            Collection<String> classes;
            if (originalMethodName != null) {
              classes = methods.get(originalMethodName);
            } else {
              classes = methods.get(methodName);
            }
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
      default:
        break;
    }
  }

  /**
   * @param name The function name to find.
   * @param callsiteSourceFileName the filename containing the callsite
   * @return The Config object for the name or null if no match was found.
   */
  private Config findMatching(String name, String callsiteSourceFileName) {
    Config config = functions.get(name);
    if (config == null) {
      name = name.replace('$', '.');
      config = functions.get(name);
    }
    if (config != null) {
      for (String excludedSuffix : config.excludedFilenameSuffixes) {
        if (callsiteSourceFileName.endsWith(excludedSuffix)) {
          return null;
        }
      }
    }
    return config;
  }

  /**
   * @return The Config object for the class match the specified type or null if no match was found.
   */
  private Config findMatchingClass(JSType callClassType, Collection<String> declarationNames) {
    if (!callClassType.isEmptyType() && !callClassType.isUnknownType()) {
      for (String declarationName : declarationNames) {
        String className = getClassFromDeclarationName(declarationName);
        JSType methodClassType = registry.getGlobalType(className);
        if (methodClassType != null && callClassType.isSubtypeOf(methodClassType)) {
          return functions.get(declarationName);
        }
      }
    }
    return null;
  }

  /** Replace the parameters specified in the config, if possible. */
  private void doSubstitutions(NodeTraversal t, Config config, Node n) {
    if (n.isTaggedTemplateLit()) {
      // This is currently not supported, since tagged template literals have a different calling
      // convention than ordinary functions, so it's unclear which arguments are expected to be
      // replaced. Specifically, there are no direct string arguments, and for arbitrary tag
      // functions it's not clear that it's safe to inline any constant placeholders.
      compiler.report(JSError.make(n, STRING_REPLACEMENT_TAGGED_TEMPLATE));
      return;
    }
    checkState(n.isNew() || n.isCall());

    if (!config.isReplaceAll()) {
      // Note: the first child is the function, but the parameter id is 1 based.
      for (int parameter : config.parameters) {
        Node arg = n.getChildAtIndex(parameter);
        if (arg != null) {
          replaceExpression(t, arg, n);
        }
      }
    } else {
      // Replace all parameters.
      Node firstParam = n.getSecondChild();
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
   * @return The replacement node (or the original expression if no replacement is made)
   */
  private Node replaceExpression(NodeTraversal t, Node expr, Node parent) {
    Node replacement;
    String key = null;
    String replacementString;
    switch (expr.getToken()) {
      case STRING:
        key = expr.getString();
        replacementString = getReplacement(key);
        replacement = IR.string(replacementString);
        break;
      case TEMPLATELIT:
      case ADD:
      case NAME:
        StringBuilder keyBuilder = new StringBuilder();
        Node keyNode = IR.string("");
        replacement = buildReplacement(t, expr, keyNode, keyBuilder);
        key = keyBuilder.toString();
        if (key.equals(placeholderToken)) {
          // There is no static text in expr - only a placeholder - so just return expr directly.
          // In this case, replacement is just the string join ('`' + expr), which is not useful.
          return expr;
        }
        replacementString = getReplacement(key);
        keyNode.setString(replacementString);
        break;
      default:
        // This may be a function call or a variable reference. We don't
        // replace these.
        return expr;
    }

    checkNotNull(key);
    checkNotNull(replacementString);
    recordReplacement(key);

    replacement.useSourceInfoIfMissingFromForTree(expr);
    parent.replaceChild(expr, replacement);
    t.reportCodeChange();
    return replacement;
  }

  /** Get a replacement string for the provide key text. */
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

  /** Record the location the replacement was made. */
  private void recordReplacement(String key) {
    Result result = results.get(key);
    checkState(result != null);

    result.didReplacement = true;
  }

  /**
   * Builds a replacement abstract syntax tree for the string expression {@code expr}. Appends any
   * string literal values that are encountered to {@code keyBuilder}, to build the expression's
   * replacement key.
   *
   * @param expr A JS expression that evaluates to a string value
   * @param prefix The JS expression to which {@code expr}'s replacement is logically being
   *     concatenated. It is a partial solution to the problem at hand and will either be this
   *     method's return value or a descendant of it.
   * @param keyBuilder A builder of the string expression's replacement key
   * @return The abstract syntax tree that should replace {@code expr}
   */
  private Node buildReplacement(NodeTraversal t, Node expr, Node prefix, StringBuilder keyBuilder) {
    switch (expr.getToken()) {
      case ADD:
        Node left = expr.getFirstChild();
        Node right = left.getNext();
        prefix = buildReplacement(t, left, prefix, keyBuilder);
        return buildReplacement(t, right, prefix, keyBuilder);
      case TEMPLATELIT:
        for (Node child = expr.getFirstChild(); child != null; child = child.getNext()) {
          switch (child.getToken()) {
            case TEMPLATELIT_STRING:
              keyBuilder.append(child.getCookedString());
              break;
            case TEMPLATELIT_SUB:
              prefix = buildReplacement(t, child.getFirstChild(), prefix, keyBuilder);
              break;
            default:
              throw new IllegalStateException("Unexpected TEMPLATELIT child: " + child);
          }
        }
        return prefix;
      case STRING:
        keyBuilder.append(expr.getString());
        return prefix;
      case NAME:
        // If the referenced variable is a constant, use its value.
        Var var = t.getScope().getVar(expr.getString());
        if (var != null && (var.isInferredConst() || var.isConst())) {
          Node initialValue = var.getInitialValue();
          if (initialValue != null) {
            Node newKeyNode = IR.string("");
            StringBuilder newKeyBuilder = new StringBuilder();
            Node replacement = buildReplacement(t, initialValue, newKeyNode, newKeyBuilder);
            if (replacement == newKeyNode) {
              keyBuilder.append(newKeyBuilder);
              return prefix;
            }
          }
          // Not a simple string constant.
        }
        // fall-through
      default:
        keyBuilder.append(placeholderToken);
        prefix = IR.add(prefix, IR.string(placeholderToken));
        return IR.add(prefix, expr.cloneTree());
    }
  }

  /** From a provide name extract the method name. */
  private static String getMethodFromDeclarationName(String fullDeclarationName) {
    String[] parts = fullDeclarationName.split("\\.prototype\\.");
    checkState(parts.length == 1 || parts.length == 2);
    if (parts.length == 2) {
      return parts[1];
    }
    return null;
  }

  /** From a provide name extract the class name. */
  private static String getClassFromDeclarationName(String fullDeclarationName) {
    String[] parts = fullDeclarationName.split("\\.prototype\\.");
    checkState(parts.length == 1 || parts.length == 2);
    if (parts.length == 2) {
      return parts[0];
    }
    return null;
  }

  /**
   * Build the data structures need by this pass from the provided list of functions and methods.
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
   * Convert the provide string into a Config. The string can be a static function: foo(,,?)
   * foo.bar(?) or a class method: foo.prototype.bar(?) And is allowed to either replace all
   * parameters using "*" or one parameter "?". "," is used as a placeholder for ignored parameters.
   */
  private Config parseConfiguration(String function) {
    // Looks like this function_name(,$,)
    int first = function.indexOf('(');
    int last = function.indexOf(')');
    int colon = function.indexOf(EXCLUSION_PREFIX);

    // TODO(johnlenz): Make parsing precondition checks JSErrors reports.
    checkState(first != -1 && last != -1);

    String name = function.substring(0, first);
    String params = function.substring(first + 1, last);

    int paramCount = 0;
    List<Integer> replacementParameters = new ArrayList<>();
    String[] parts = params.split(",");
    for (String param : parts) {
      paramCount++;
      if (param.equals(REPLACE_ALL_MARKER)) {
        checkState(paramCount == 1 && parts.length == 1);
        replacementParameters.add(Config.REPLACE_ALL_VALUE);
      } else if (param.equals(REPLACE_ONE_MARKER)) {
        // TODO(johnlenz): Support multiple.
        checkState(!replacementParameters.contains(Config.REPLACE_ALL_VALUE));
        replacementParameters.add(paramCount);
      } else {
        // TODO(johnlenz): report an error.
        Preconditions.checkState(param.isEmpty(), "Unknown marker", param);
      }
    }

    checkState(!replacementParameters.isEmpty());

    return new Config(
        name,
        replacementParameters,
        colon == -1
            ? ImmutableSet.of()
            : ImmutableSet.copyOf(
                function.substring(colon + EXCLUSION_PREFIX.length()).split(",")));
  }

  /**
   * Use a name generate to create names so the names overlap with the names used for variable and
   * properties.
   */
  private static DefaultNameGenerator createNameGenerator(Iterable<String> reserved) {
    final String namePrefix = "";
    final char[] reservedChars = new char[0];
    return new DefaultNameGenerator(ImmutableSet.copyOf(reserved), namePrefix, reservedChars);
  }
}
