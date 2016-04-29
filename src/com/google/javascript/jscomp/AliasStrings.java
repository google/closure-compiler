/*
 * Copyright 2007 The Closure Compiler Authors.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * A {@link Compiler} pass for aliasing strings. String declarations
 * contribute to garbage collection, which becomes a problem in large
 * applications. Strings that should be aliased occur many times in the code,
 * or occur on codepaths that get executed frequently.
 *
 */
@GwtIncompatible("java.util.regex")
class AliasStrings extends AbstractPostOrderCallback
    implements CompilerPass {

  private static final Logger logger =
      Logger.getLogger(AliasStrings.class.getName());

  /** Prefix for variable names for the aliased strings */
  private static final String STRING_ALIAS_PREFIX = "$$S_";

  private final AbstractCompiler compiler;

  private final JSModuleGraph moduleGraph;

  // Regular expression matcher for a blacklisting strings in aliasing.
  private Matcher blacklist = null;

  /**
   * Strings that can be aliased, or null if all strings except 'undefined'
   * should be aliased
   */
  private final Set<String> aliasableStrings;

  private final boolean outputStringUsage;

  private final SortedMap<String, StringInfo> stringInfoMap = new TreeMap<>();

  private final Set<String> usedHashedAliases = new LinkedHashSet<>();

  /**
   * Map from module to the node in that module that should parent any string
   * variable declarations that have to be moved into that module
   */
  private final Map<JSModule, Node> moduleVarParentMap =
      new HashMap<>();

  /** package private.  This value is AND-ed with the hash function to allow
   * unit tests to reduce the range of hash values to test collision cases */
  long unitTestHashReductionMask = ~0L;

  /**
   * Creates an instance.
   *
   * @param compiler The compiler
   * @param moduleGraph The module graph, or null if there are no modules
   * @param strings Set of strings to be aliased. If null, all strings except
   *     'undefined' will be aliased.
   * @param blacklistRegex The regex to blacklist words in aliasing strings.
   * @param outputStringUsage Outputs all strings and the number of times they
   * were used in the application to the server log.
   */
  AliasStrings(AbstractCompiler compiler,
               JSModuleGraph moduleGraph,
               Set<String> strings,
               String blacklistRegex,
               boolean outputStringUsage) {
    this.compiler = compiler;
    this.moduleGraph = moduleGraph;
    this.aliasableStrings = strings;
    if (blacklistRegex.length() != 0) {
      this.blacklist = Pattern.compile(blacklistRegex).matcher("");
    } else {
      this.blacklist = null;
    }
    this.outputStringUsage = outputStringUsage;
  }

  @Override
  public void process(Node externs, Node root) {
    logger.fine("Aliasing common strings");

    // Traverse the tree and collect strings
    NodeTraversal.traverseEs6(compiler, root, this);

    // 1st edit pass: replace some strings with aliases
    replaceStringsWithAliases();

    // 2nd edit pass: add variable declarations for aliased strings.
    addAliasDeclarationNodes();

    if (outputStringUsage) {
      outputStringUsage();
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isString() &&
        !parent.isGetProp() &&
        !parent.isRegExp()) {

      String str = n.getString();

      // "undefined" is special-cased, since it needs to be used when JS code
      // is unloading and therefore variable references aren't available.
      // This is because of a bug in Firefox.
      if ("undefined".equals(str)) {
        return;
      }

      if (blacklist != null && blacklist.reset(str).find()) {
        return;
      }

      if (aliasableStrings == null || aliasableStrings.contains(str)) {
        StringOccurrence occurrence = new StringOccurrence(n, parent);
        StringInfo info = getOrCreateStringInfo(str);

        info.occurrences.add(occurrence);
        info.numOccurrences++;

        if (t.inGlobalHoistScope() || isInThrowExpression(n)) {
          info.numOccurrencesInfrequentlyExecuted++;
        }

        // The current module.
        JSModule module = t.getModule();
        if (info.numOccurrences != 1) {
          // Check whether the current module depends on the module containing
          // the declaration.
          if (module != null &&
              info.moduleToContainDecl != null &&
              module != info.moduleToContainDecl &&
              !moduleGraph.dependsOn(module, info.moduleToContainDecl)) {
            // We need to declare this string in the deepest module in the
            // module dependency graph that both of these modules depend on.
            module = moduleGraph.getDeepestCommonDependency(
                module, info.moduleToContainDecl);
          } else {
            // use the previously saved insertion location.
            return;
          }
        }
        Node varParent = moduleVarParentMap.get(module);
        if (varParent == null) {
          varParent = compiler.getNodeForCodeInsertion(module);
          moduleVarParentMap.put(module, varParent);
        }
        info.moduleToContainDecl = module;
        info.parentForNewVarDecl = varParent;
        info.siblingToInsertVarDeclBefore = varParent.getFirstChild();
      }
    }
  }

  /**
   * Looks up the {@link StringInfo} object for a JavaScript string. Creates
   * it if necessary.
   */
  private StringInfo getOrCreateStringInfo(String string) {
    StringInfo info = stringInfoMap.get(string);
    if (info == null) {
      info = new StringInfo(stringInfoMap.size());
      stringInfoMap.put(string, info);
    }
    return info;
  }

  /**
   * Is the {@link Node} currently within a 'throw' expression?
   */
  private static boolean isInThrowExpression(Node n) {
    // Look up the traversal stack to find a THROW node
    for (Node ancestor : n.getAncestors()) {
      switch (ancestor.getType()) {
        case Token.THROW:
          return true;
        case Token.IF:
        case Token.WHILE:
        case Token.DO:
        case Token.FOR:
        case Token.SWITCH:
        case Token.CASE:
        case Token.DEFAULT_CASE:
        case Token.BLOCK:
        case Token.SCRIPT:
        case Token.FUNCTION:
        case Token.TRY:
        case Token.CATCH:
        case Token.RETURN:
        case Token.EXPR_RESULT:
          // early exit - these nodes types can't be within a THROW
          return false;
      }
    }
    return false;
  }

 /**
   * Replace strings with references to alias variables.
   */
  private void replaceStringsWithAliases() {
    for (Entry<String, StringInfo> entry : stringInfoMap.entrySet()) {
      String literal = entry.getKey();
      StringInfo info = entry.getValue();
      if (shouldReplaceWithAlias(literal, info)) {
        for (StringOccurrence occurrence : info.occurrences) {
          replaceStringWithAliasName(
              occurrence, info.getVariableName(literal), info);
        }
      }
    }
  }

  /**
   * Creates a var declaration for each aliased string. Var declarations are
   * inserted as close to the first use of the string as possible.
   */
  private void addAliasDeclarationNodes() {
    for (Entry<String, StringInfo> entry : stringInfoMap.entrySet()) {
      StringInfo info = entry.getValue();
      if (!info.isAliased) {
        continue;
      }
      String alias = info.getVariableName(entry.getKey());
      Node var = IR.var(IR.name(alias), IR.string(entry.getKey()));
      if (info.siblingToInsertVarDeclBefore == null) {
        info.parentForNewVarDecl.addChildToFront(var);
      } else {
        info.parentForNewVarDecl.addChildBefore(
            var, info.siblingToInsertVarDeclBefore);
      }
      compiler.reportCodeChange();
    }
  }

  /**
   *  Dictates the policy for replacing a string with an alias.
   *
   *  @param str The string literal
   *  @param info Accumulated information about a string
   */
  private static boolean shouldReplaceWithAlias(String str, StringInfo info) {
    // Optimize for application performance.  If there are any uses of the
    // string that are not 'infrequent uses', assume they are frequent and
    // create an alias.
    if (info.numOccurrences > info.numOccurrencesInfrequentlyExecuted) {
      return true;
    }

    // Optimize for code size.  Are aliases smaller than strings?
    //
    // This logic optimizes for the size of uncompressed code, but it tends to
    // get good results for the size of the gzipped code too.
    //
    // gzip actually prefers that strings are not aliased - it compresses N
    // string literals better than 1 string literal and N+1 short variable
    // names, provided each string is within 32k of the previous copy.  We
    // follow the uncompressed logic as insurance against there being multiple
    // strings more than 32k apart.

    int sizeOfLiteral = 2 + str.length();
    int sizeOfStrings = info.numOccurrences * sizeOfLiteral;
    int sizeOfVariable = 3;
    //  '6' comes from: 'var =;' in var XXX="...";
    int sizeOfAliases = 6 + sizeOfVariable + sizeOfLiteral    // declaration
        + info.numOccurrences * sizeOfVariable;               // + uses

    return sizeOfAliases < sizeOfStrings;
  }

  /**
   * Replaces a string literal with a reference to the string's alias variable.
   */
  private void replaceStringWithAliasName(StringOccurrence occurrence,
                                          String name,
                                          StringInfo info) {
    occurrence.parent.replaceChild(occurrence.node,
                                   IR.name(name));
    info.isAliased = true;
    compiler.reportCodeChange();
  }

  /**
   * Outputs a log of all strings used more than once in the code.
   */
  private void outputStringUsage() {
    StringBuilder sb = new StringBuilder("Strings used more than once:\n");
    for (Entry<String, StringInfo> stringInfoEntry : stringInfoMap.entrySet()) {
      StringInfo info = stringInfoEntry.getValue();
      if (info.numOccurrences > 1) {
        sb.append(info.numOccurrences);
        sb.append(": ");
        sb.append(stringInfoEntry.getKey());
        sb.append('\n');
      }
    }
    // TODO(user): Make this save to file OR output to the application
    logger.fine(sb.toString());
  }

  // -------------------------------------------------------------------------

  /**
   * A class that holds the location of a single JavaScript string literal
   */
  private static final class StringOccurrence {
    final Node node;
    final Node parent;

    StringOccurrence(Node node, Node parent) {
      this.node = node;
      this.parent = parent;
    }
  }

  /**
   * A class that holds information about a JavaScript string that might become
   * aliased.
   */
  private final class StringInfo {
    final int id;

    boolean isAliased;      // set to 'true' when reference to alias created

    final List<StringOccurrence> occurrences;
    int numOccurrences;
    int numOccurrencesInfrequentlyExecuted;

    JSModule moduleToContainDecl;
    Node parentForNewVarDecl;
    Node siblingToInsertVarDeclBefore;

    String aliasName;

    StringInfo(int id) {
      this.id = id;
      this.occurrences = new ArrayList<>();
      this.isAliased = false;
    }

    /** Returns the JS variable name to be substituted for this string. */
    String getVariableName(String stringLiteral) {
      if (aliasName == null) {
        aliasName =
            encodeStringAsIdentifier(STRING_ALIAS_PREFIX, stringLiteral);
      }
      return aliasName;
    }

    /**
     * Returns a legal identifier that uniquely characterizes string 's'.
     *
     * We want the identifier to be a function of the string value because that
     * makes the identifiers stable as the program is changed.
     *
     * The digits of a good hash function would be adequate, but for short
     * strings the following algorithm is easier to work with for unit tests.
     *
     * ASCII alphanumerics are mapped to themselves.  Other characters are
     * mapped to $XXX or $XXX_ where XXX is a variable number of hex digits.
     * The underscore is inserted as necessary to avoid ambiguity when the
     * character following is a hex digit. E.g. '\n1' maps to '$a_1',
     * distinguished by the underscore from '\u00A1' which maps to '$a1'.
     *
     * If the string is short enough, this is sufficient.  Longer strings are
     * truncated after encoding an initial prefix and appended with a hash
     * value.
     */
    String encodeStringAsIdentifier(String prefix, String s) {
      // Limit to avoid generating very long identifiers
      final int maxLimit = 20;
      final int length = s.length();
      final int limit = Math.min(length, maxLimit);

      StringBuilder sb = new StringBuilder();
      sb.append(prefix);
      boolean protectHex = false;

      for (int i = 0; i < limit; i++) {
        char ch = s.charAt(i);

        if (protectHex) {
          if ((ch >= '0' && ch <= '9') ||
              (ch >= 'a' && ch <= 'f')) { // toHexString generate lowercase
            sb.append('_');
          }
          protectHex = false;
        }

        if ((ch >= '0' && ch <= '9') ||
            (ch >= 'A' && ch <= 'Z') ||
            (ch >= 'a' && ch <= 'z')) {
          sb.append(ch);
        } else {
          sb.append('$');
          sb.append(Integer.toHexString(ch));
          protectHex = true;
        }
      }

      if (length == limit) {
        return sb.toString();
      }

      // The identifier is not unique because we omitted part, so add a
      // checksum as a hashcode.
      CRC32 crc32 = new CRC32();
      crc32.update(s.getBytes(UTF_8));
      long hash = crc32.getValue() & unitTestHashReductionMask;
      sb.append('_');
      sb.append(Long.toHexString(hash));
      String encoded = sb.toString();
      if (!usedHashedAliases.add(encoded)) {
        // A collision has been detected (which is very rare). Use the sequence
        // id to break the tie. This means that the name is no longer invariant
        // across source code changes and recompilations.
        encoded += "_" + id;
      }
      return encoded;
    }
  }
}
