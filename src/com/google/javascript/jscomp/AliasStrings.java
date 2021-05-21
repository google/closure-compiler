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

import static java.lang.Math.min;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * A compiler pass for aliasing strings. String declarations
 * contribute to garbage collection, which becomes a problem in large
 * applications. Strings that should be aliased occur many times in the code,
 * or occur on codepaths that get executed frequently.
 *
 * 2017/09/17 Notes:
 *     - Turning on this pass usually hurts code size after gzip.
 *     - It was originally written to deal with performance problems on some
 *       older browser VMs.
 *     - However, projects that make heavy use of jslayout may need to enable
 *       this pass even for modern browsers, because jslayout generates so many
 *       duplicate strings.
 */
class AliasStrings implements CompilerPass, NodeTraversal.Callback {

  private static final Logger logger =
      Logger.getLogger(AliasStrings.class.getName());

  /** Prefix for variable names for the aliased strings */
  private static final String STRING_ALIAS_PREFIX = "$$S_";

  private final AbstractCompiler compiler;

  private final JSChunkGraph moduleGraph;

  private final boolean outputStringUsage;

  private final SortedMap<String, StringInfo> stringInfoMap = new TreeMap<>();

  private final Set<String> usedHashedAliases = new LinkedHashSet<>();

  /**
   * Map from module to the node in that module that should parent any string variable declarations
   * that have to be moved into that module
   */
  private final Map<JSChunk, Node> moduleVarParentMap = new HashMap<>();

  /** package private.  This value is AND-ed with the hash function to allow
   * unit tests to reduce the range of hash values to test collision cases */
  int unitTestHashReductionMask = ~0;

  /**
   * Creates an instance.
   *
   * @param compiler The compiler
   * @param moduleGraph The module graph, or null if there are no modules
   * @param outputStringUsage Outputs all strings and the number of times they were used in the
   *     application to the server log.
   */
  AliasStrings(AbstractCompiler compiler, JSChunkGraph moduleGraph, boolean outputStringUsage) {
    this.compiler = compiler;
    this.moduleGraph = moduleGraph;
    this.outputStringUsage = outputStringUsage;
  }

  @Override
  public void process(Node externs, Node root) {
    logger.fine("Aliasing common strings");

    // Traverse the tree and collect strings
    NodeTraversal.traverse(compiler, root, this);

    // 1st edit pass: replace some strings with aliases
    replaceStringsWithAliases();

    // 2nd edit pass: add variable declarations for aliased strings.
    addAliasDeclarationNodes();

    if (outputStringUsage) {
      outputStringUsage();
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    switch (n.getToken()) {
      case TEMPLATELIT:
      case TAGGED_TEMPLATELIT:
      case TEMPLATELIT_SUB: // technically redundant, since it must be a child of the others
        // TODO(bradfordcsmith): Consider replacing long and/or frequently occurring substrings
        // within template literals with template substitutions.
        return false;
      default:
        return true;
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isStringLit() && !parent.isRegExp()) {
      String str = n.getString();

      // "undefined" is special-cased, since it needs to be used when JS code
      // is unloading and therefore variable references aren't available.
      // This is because of a bug in Firefox.
      if ("undefined".equals(str)) {
        return;
      }

      Node occurrence = n;
      StringInfo info = getOrCreateStringInfo(str);

      info.occurrences.add(occurrence);

      // The current module.
      JSChunk module = t.getChunk();
      if (info.occurrences.size() != 1) {
        // Check whether the current module depends on the module containing
        // the declaration.
        if (module != null
            && info.moduleToContainDecl != null
            && module != info.moduleToContainDecl) {
          // We need to declare this string in the deepest module in the
          // module dependency graph that both of these modules depend on.
          module =
              moduleGraph.getDeepestCommonDependencyInclusive(module, info.moduleToContainDecl);
        } else {
          // use the previously saved insertion location.
          return;
        }
      }
      Node varParent =
          moduleVarParentMap.computeIfAbsent(module, compiler::getNodeForCodeInsertion);
      info.moduleToContainDecl = module;
      info.parentForNewVarDecl = varParent;
      info.siblingToInsertVarDeclBefore = varParent.getFirstChild();
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
   * Replace strings with references to alias variables.
   */
  private void replaceStringsWithAliases() {
    for (Entry<String, StringInfo> entry : stringInfoMap.entrySet()) {
      String literal = entry.getKey();
      StringInfo info = entry.getValue();
      if (shouldReplaceWithAlias(literal, info)) {
        for (Node node : info.occurrences) {
          replaceStringWithAliasName(node, info.getVariableName(literal), info);
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
      Node firstUse = info.occurrences.get(0);
      var.srcrefTree(firstUse);
      if (info.siblingToInsertVarDeclBefore == null) {
        info.parentForNewVarDecl.addChildToFront(var);
      } else {
        var.insertBefore(info.siblingToInsertVarDeclBefore);
      }
      compiler.reportChangeToEnclosingScope(var);
    }
  }

  /**
   *  Dictates the policy for replacing a string with an alias.
   *
   *  @param str The string literal
   *  @param info Accumulated information about a string
   */
  private static boolean shouldReplaceWithAlias(String str, StringInfo info) {
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
    int count = info.occurrences.size();
    int sizeOfStrings = count * sizeOfLiteral;
    int sizeOfVariable = 3;
    //  '6' comes from: 'var =;' in var XXX="...";
    int sizeOfAliases =
        6
            + sizeOfVariable
            + sizeOfLiteral // declaration
            + count * sizeOfVariable; // + uses

    return sizeOfAliases < sizeOfStrings;
  }

  /** Replaces a string literal with a reference to the string's alias variable. */
  private void replaceStringWithAliasName(Node n, String name, StringInfo info) {
    Node nameNode = IR.name(name);
    n.replaceWith(nameNode);
    info.isAliased = true;
    compiler.reportChangeToEnclosingScope(nameNode);
  }

  /**
   * Outputs a log of all strings used more than once in the code.
   */
  private void outputStringUsage() {
    StringBuilder sb = new StringBuilder("Strings used more than once:\n");
    for (Entry<String, StringInfo> stringInfoEntry : stringInfoMap.entrySet()) {
      StringInfo info = stringInfoEntry.getValue();
      int count = info.occurrences.size();
      if (count > 1) {
        sb.append(count);
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
   * A class that holds information about a JavaScript string that might become
   * aliased.
   */
  private final class StringInfo {
    final int id;

    boolean isAliased;      // set to 'true' when reference to alias created

    final ArrayList<Node> occurrences = new ArrayList<>();

    JSChunk moduleToContainDecl;
    Node parentForNewVarDecl;
    Node siblingToInsertVarDeclBefore;

    String aliasName;

    StringInfo(int id) {
      this.id = id;
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
      final int limit = min(length, maxLimit);

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
      int hash = s.hashCode() & unitTestHashReductionMask;
      sb.append('_');
      sb.append(Integer.toHexString(hash));
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
