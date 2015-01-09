/*
 * Copyright 2004 The Closure Compiler Authors.
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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * RenameProperties renames properties (including methods) of all JavaScript
 * objects. This includes prototypes, functions, object literals, etc.
 *
 * <p> If provided a VariableMap of previously used names, it tries to reuse
 * those names.
 *
 * <p> To prevent a property from getting renamed you may extern it (add it to
 * your externs file) or put it in quotes.
 *
 * <p> To avoid run-time JavaScript errors, use quotes when accessing properties
 * that are defined using quotes.
 *
 * <pre>
 *   var a = {'myprop': 0}, b = a['myprop'];  // correct
 *   var x = {'myprop': 0}, y = x.myprop;     // incorrect
 * </pre>
 *
 */
class RenameProperties implements CompilerPass {

  private final AbstractCompiler compiler;
  private final boolean generatePseudoNames;

  /** Property renaming map from a previous compilation. */
  private final VariableMap prevUsedPropertyMap;

  private final List<Node> stringNodesToRename = new ArrayList<>();
  private final Map<Node, Node> callNodeToParentMap =
      new HashMap<>();
  private final char[] reservedCharacters;

  // Map from property name to Property object
  private final Map<String, Property> propertyMap = new HashMap<>();

  // Property names that don't get renamed
  private final Set<String> externedNames = new HashSet<>(
      Arrays.asList("prototype"));

  // Names to which properties shouldn't be renamed, to avoid name conflicts
  private final Set<String> quotedNames = new HashSet<>();

  private static final Comparator<Property> FREQUENCY_COMPARATOR =
    new Comparator<Property>() {
      @Override
      public int compare(Property p1, Property p2) {

        /**
         * First a frequently used names would always be picked first.
         */
        if (p1.numOccurrences != p2.numOccurrences) {
          return p2.numOccurrences - p1.numOccurrences;
        }

        /**
         * Finally, for determinism, we compare them based on the old name.
         */
        return p1.oldName.compareTo(p2.oldName);
       }
    };

  /**
   * The name of a special function that this pass replaces. It takes one
   * argument: a string literal containing one or more dot-separated JS
   * identifiers. This pass will replace them as though they were JS property
   * references. Here are two examples:
   *    JSCompiler_renameProperty('propertyName') -> 'jYq'
   *    JSCompiler_renameProperty('myProp.nestedProp.innerProp') -> 'e4.sW.C$'
   */
  static final String RENAME_PROPERTY_FUNCTION_NAME =
      "JSCompiler_renameProperty";

  static final DiagnosticType BAD_CALL = DiagnosticType.error(
      "JSC_BAD_RENAME_PROPERTY_FUNCTION_NAME_CALL",
      "Bad " + RENAME_PROPERTY_FUNCTION_NAME + " call - " +
      "argument must be a string literal");

  static final DiagnosticType BAD_ARG = DiagnosticType.error(
      "JSC_BAD_RENAME_PROPERTY_FUNCTION_NAME_ARG",
      "Bad " + RENAME_PROPERTY_FUNCTION_NAME + " argument - " +
      "'{0}' is not a valid JavaScript identifier");

  /**
   * Creates an instance.
   *
   * @param compiler The JSCompiler
   * @param generatePseudoNames Generate pseudo names. e.g foo -> $foo$ instead
   *        of compact obfuscated names. This is used for debugging.
   */
  RenameProperties(AbstractCompiler compiler, boolean generatePseudoNames) {
    this(compiler, generatePseudoNames, null, null);
  }

  /**
   * Creates an instance.
   *
   * @param compiler The JSCompiler.
   * @param generatePseudoNames Generate pseudo names. e.g foo -> $foo$ instead
   *        of compact obfuscated names. This is used for debugging.
   * @param prevUsedPropertyMap The property renaming map used in a previous
   *        compilation.
   */
  RenameProperties(AbstractCompiler compiler,
      boolean generatePseudoNames, VariableMap prevUsedPropertyMap) {
    this(compiler, generatePseudoNames, prevUsedPropertyMap, null);
  }

  /**
   * Creates an instance.
   *
   * @param compiler The JSCompiler.
   * @param generatePseudoNames Generate pseudo names. e.g foo -> $foo$ instead
   *        of compact obfuscated names. This is used for debugging.
   * @param prevUsedPropertyMap The property renaming map used in a previous
   *        compilation.
   * @param reservedCharacters If specified these characters won't be used in
   *   generated names
   */
  RenameProperties(AbstractCompiler compiler,
      boolean generatePseudoNames,
      VariableMap prevUsedPropertyMap,
      @Nullable char[] reservedCharacters) {
    this.compiler = compiler;
    this.generatePseudoNames = generatePseudoNames;
    this.prevUsedPropertyMap = prevUsedPropertyMap;
    this.reservedCharacters = reservedCharacters;
    externedNames.addAll(compiler.getExternProperties());
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());

    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    Set<String> reservedNames =
        new HashSet<>(externedNames.size() + quotedNames.size());
    reservedNames.addAll(externedNames);
    reservedNames.addAll(quotedNames);

    // First, try and reuse as many property names from the previous compilation
    // as possible.
    if (prevUsedPropertyMap != null) {
      reusePropertyNames(reservedNames, propertyMap.values());
    }

    compiler.addToDebugLog("JS property assignments:");

    // Assign names, sorted by descending frequency to minimize code size.
    Set<Property> propsByFreq = new TreeSet<>(FREQUENCY_COMPARATOR);
    propsByFreq.addAll(propertyMap.values());
    generateNames(propsByFreq, reservedNames);

    // Update the string nodes.
    boolean changed = false;
    for (Node n : stringNodesToRename) {
      String oldName = n.getString();
      Property p = propertyMap.get(oldName);
      if (p != null && p.newName != null) {
        Preconditions.checkState(oldName.equals(p.oldName));
        n.setString(p.newName);
        changed = changed || !p.newName.equals(oldName);
      }
    }

    // Update the call nodes.
    for (Map.Entry<Node, Node> nodeEntry : callNodeToParentMap.entrySet()) {
      Node parent = nodeEntry.getValue();
      Node firstArg = nodeEntry.getKey().getFirstChild().getNext();
      StringBuilder sb = new StringBuilder();
      for (String oldName : Splitter.on('.').split(firstArg.getString())) {
        Property p = propertyMap.get(oldName);
        String replacement;
        if (p != null && p.newName != null) {
          Preconditions.checkState(oldName.equals(p.oldName));
          replacement = p.newName;
        } else {
          replacement = oldName;
        }
        if (sb.length() > 0) {
          sb.append('.');
        }
        sb.append(replacement);
      }
      parent.replaceChild(nodeEntry.getKey(), IR.string(sb.toString()));
      changed = true;
    }

    if (changed) {
      compiler.reportCodeChange();
    }

    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED_OBFUSCATED);
  }

  /**
   * Runs through the list of properties and renames as many as possible with
   * names from the previous compilation. Also, updates reservedNames with the
   * set of reused names.
   * @param reservedNames Reserved names to use during renaming.
   * @param allProps Properties to rename.
   */
  private void reusePropertyNames(Set<String> reservedNames,
                                  Collection<Property> allProps) {
    for (Property prop : allProps) {
      // Check if this node can reuse a name from a previous compilation - if
      // it can set the newName for the property too.
      String prevName = prevUsedPropertyMap.lookupNewName(prop.oldName);
      if (!generatePseudoNames && prevName != null) {
        // We can reuse prevName if it's not reserved.
        if (reservedNames.contains(prevName)) {
          continue;
        }

        prop.newName = prevName;
        reservedNames.add(prevName);
      }
    }
  }

  /**
   * Generates new names for properties.
   *
   * @param props Properties to generate new names for
   * @param reservedNames A set of names to which properties should not be
   *     renamed
   */
  private void generateNames(Set<Property> props, Set<String> reservedNames) {
    NameGenerator nameGen = new NameGenerator(
        reservedNames, "", reservedCharacters);
    for (Property p : props) {
      if (generatePseudoNames) {
        p.newName = "$" + p.oldName + "$";
      } else {
        // If we haven't already given this property a reusable name.
        if (p.newName == null) {
          p.newName = nameGen.generateNextName();
        }
      }
      reservedNames.add(p.newName);
      compiler.addToDebugLog(p.oldName + " => " + p.newName);
    }
  }

  /**
   * Gets the property renaming map (the "answer key").
   *
   * @return A mapping from original names to new names
   */
  VariableMap getPropertyMap() {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    for (Property p : propertyMap.values()) {
      if (p.newName != null) {
        map.put(p.oldName, p.newName);
      }
    }
    return new VariableMap(map.build());
  }


  // -------------------------------------------------------------------------

  /**
   * A traversal callback that collects property names and counts how
   * frequently each property name occurs.
   */
  private class ProcessProperties extends AbstractPostOrderCallback implements
      ScopedCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
          Node propNode = n.getFirstChild().getNext();
          if (propNode.isString()) {
            maybeMarkCandidate(propNode);
          }
          break;
        case Token.OBJECTLIT:
          for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
            if (!key.isQuotedString()) {
              maybeMarkCandidate(key);
            } else {
              // Ensure that we never rename some other property in a way
              // that could conflict with this quoted key.
              quotedNames.add(key.getString());
            }
          }
          break;
        case Token.GETELEM:
          // If this is a quoted property access (e.g. x['myprop']), we need to
          // ensure that we never rename some other property in a way that
          // could conflict with this quoted name.
          Node child = n.getLastChild();
          if (child != null && child.isString()) {
            quotedNames.add(child.getString());
          }
          break;
        case Token.CALL:
          // We replace a JSCompiler_renameProperty function call with a string
          // containing the renamed property.
          Node fnName = n.getFirstChild();
          if (fnName.isName() &&
              RENAME_PROPERTY_FUNCTION_NAME.equals(fnName.getString())) {
            callNodeToParentMap.put(n, parent);
            countCallCandidates(t, n);
          }
          break;
        case Token.FUNCTION:
          // We eliminate any stub implementations of JSCompiler_renameProperty
          // that we encounter.
          if (NodeUtil.isFunctionDeclaration(n)) {
            String name = n.getFirstChild().getString();
            if (RENAME_PROPERTY_FUNCTION_NAME.equals(name)) {
              if (parent.isExprResult()) {
                parent.detachFromParent();
              } else {
                parent.removeChild(n);
              }
              compiler.reportCodeChange();
            }
          } else if (parent.isName() &&
                     RENAME_PROPERTY_FUNCTION_NAME.equals(parent.getString())) {
            Node varNode = parent.getParent();
            if (varNode.isVar()) {
              varNode.removeChild(parent);
              if (!varNode.hasChildren()) {
                varNode.detachFromParent();
              }
              compiler.reportCodeChange();
            }
          }
          break;
      }
    }

    /**
     * If a property node is eligible for renaming, stashes a reference to it
     * and increments the property name's access count.
     *
     * @param n The STRING node for a property
     */
    private void maybeMarkCandidate(Node n) {
      String name = n.getString();
      if (!externedNames.contains(name)) {
        stringNodesToRename.add(n);
        countPropertyOccurrence(name);
      }
    }

    /**
     * Counts references to property names that occur in a special function
     * call.
     *
     * @param callNode The CALL node for a property
     * @param t The traversal
     */
    private void countCallCandidates(NodeTraversal t, Node callNode) {
      Node firstArg = callNode.getFirstChild().getNext();
      if (!firstArg.isString()) {
        t.report(callNode, BAD_CALL);
        return;
      }

      for (String name : firstArg.getString().split("[.]")) {
        if (!TokenStream.isJSIdentifier(name)) {
          t.report(callNode, BAD_ARG, name);
          continue;
        }
        if (!externedNames.contains(name)) {
          countPropertyOccurrence(name);
        }
      }
    }

    /**
     * Increments the occurrence count for a property name.
     *
     * @param name The property name
     */
    private void countPropertyOccurrence(String name) {
      Property prop = propertyMap.get(name);
      if (prop == null) {
        prop = new Property(name);
        propertyMap.put(name, prop);
      }
      prop.numOccurrences++;
    }

    @Override
    public void enterScope(NodeTraversal t) {
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }
  }

  // -------------------------------------------------------------------------

  /**
   * Encapsulates the information needed for renaming a property.
   */
  private static class Property {
    final String oldName;
    String newName;
    int numOccurrences;

    Property(String name) {
      this.oldName = name;
    }
  }
}
