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
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * RenamePrototypes renames custom properties (including methods) of custom
 * prototypes and object literals. Externed property names are never renamed.
 *
 * To ensure that a prototype property or object literal property gets renamed,
 * end it with an underscore.
 *
 * To ensure that a prototype property is not renamed, give it a leading
 * underscore.
 *
 * For custom prototype property names that lack leading and trailing
 * underscores:
 * - To always rename these, use aggressive renaming.
 * - If aggressive renaming is off, we use a heuristic to decide whether to
 *   rename (to avoid most built-in JS methods). We rename if the original name
 *   contains at least one character that is not a lowercase letter.
 *
 * When a property name is used both in a prototype definition and as an object
 * literal key, we rename it only if it satisfies both renaming policies.
 *
 */
class RenamePrototypes implements CompilerPass {

  private final AbstractCompiler compiler;
  private final boolean aggressiveRenaming;
  private final char[] reservedCharacters;

  /** Previously used prototype renaming map. */
  private final VariableMap prevUsedRenameMap;

  /**
   * The Property class encapsulates the information needed for renaming
   * a method or member.
   */
  private class Property {
    String oldName;
    String newName;
    int prototypeCount;
    int objLitCount;
    int refCount;

    Property(String name) {
      this.oldName = name;
      this.newName = null;
      this.prototypeCount = 0;
      this.objLitCount = 0;
      this.refCount = 0;
    }

    int count() {
      return prototypeCount + objLitCount + refCount;
    }

    boolean canRename() {
      if (this.prototypeCount > 0 && this.objLitCount == 0) {
        return canRenamePrototypeProperty();
      }
      if (this.objLitCount > 0 && this.prototypeCount == 0) {
        return canRenameObjLitProperty();
      }
      // We're not sure what kind of property this is, so we're conservative.
      // Note that we still want to try renaming the property even when both
      // counts are zero. It may be a property added to an object at runtime,
      // like: o.newProp = x;
      return canRenamePrototypeProperty() && canRenameObjLitProperty();
    }

    private boolean canRenamePrototypeProperty() {
      if (compiler.getCodingConvention().isExported(oldName)) {
        // an externally visible name should not be renamed.
        return false;
      }

      if (compiler.getCodingConvention().isPrivate(oldName)) {
        // private names can be safely renamed. Rename!
        return true;
      }

      if (aggressiveRenaming) {
        return true;
      }

      for (int i = 0, n = oldName.length(); i < n; i++) {
        char ch = oldName.charAt(i);

        if (Character.isUpperCase(ch) || !Character.isLetter(ch)) {
          return true;
        }
      }
      return false;
    }

    private boolean canRenameObjLitProperty() {
      if (compiler.getCodingConvention().isExported(oldName)) {
        // an externally visible name should not be renamed.
        return false;
      }

      if (compiler.getCodingConvention().isPrivate(oldName)) {
        // private names can be safely renamed. Rename!
        return true;
      }

      // NOTE(user): We should probably have more aggressive options, like
      // renaming all obj lit properties that are not quoted.
      return false;
    }
  }

  /**
   * Sorts Property objects by their count, breaking ties alphabetically to
   * ensure a deterministic total ordering.
   */
  private static final Comparator<Property> FREQUENCY_COMPARATOR =
    new Comparator<Property>() {
      @Override
      public int compare(Property a1, Property a2) {
        int n1 = a1.count();
        int n2 = a2.count();
        if (n1 != n2) {
          return n2 - n1;
        }
        return a1.oldName.compareTo(a2.oldName);
      }
    };


  // Set of String nodes to rename
  private final Set<Node> stringNodes = new HashSet<>();

  // Mapping of property names to Property objects
  private final Map<String, Property> properties =
      new HashMap<>();

  // Set of names not to rename. Externed properties/methods are added later.
  private final Set<String> reservedNames =
      new HashSet<>(Arrays.asList(
          "indexOf", "lastIndexOf", "toString", "valueOf"));

  // Set of OBJLIT nodes that are assigned to prototypes
  private final Set<Node> prototypeObjLits = new HashSet<>();

  /**
   * Creates an instance.
   *
   * @param compiler The JSCompiler
   * @param aggressiveRenaming Whether to rename aggressively
   * @param reservedCharacters If specified these characters won't be used in
   *   generated names
   * @param prevUsedRenameMap The rename map used in the previous compilation
   */
  RenamePrototypes(AbstractCompiler compiler, boolean aggressiveRenaming,
                   @Nullable char[] reservedCharacters,
                   @Nullable VariableMap prevUsedRenameMap) {
    this.compiler = compiler;
    this.aggressiveRenaming = aggressiveRenaming;
    this.reservedCharacters = reservedCharacters;
    this.prevUsedRenameMap = prevUsedRenameMap;
  }

  /**
   * Does property/method renaming.
   *
   * @param externs The root of the externs parse tree
   * @param root The root of the main code parse tree
   */
  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());

    NodeTraversal.traverse(compiler, externs,
                           new ProcessExternedProperties());
    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    // Gather the properties to rename, sorted by count.
    SortedSet<Property> propsByFrequency =
        new TreeSet<>(FREQUENCY_COMPARATOR);

    for (Iterator<Map.Entry<String, Property>> it =
           properties.entrySet().iterator(); it.hasNext(); ) {
      Property a = it.next().getValue();
      if (a.canRename() && !reservedNames.contains(a.oldName)) {
        propsByFrequency.add(a);
      } else {
        it.remove();

        // If we're not renaming this, make sure we don't name something
        // else to this name.
        reservedNames.add(a.oldName);
      }
    }

    // Try and reuse as many names from the previous compilation as possible.
    if (prevUsedRenameMap != null) {
      reusePrototypeNames(propsByFrequency);
    }

    // Generate new names.
    NameGenerator nameGen = new NameGenerator(reservedNames, "",
                                              reservedCharacters);
    StringBuilder debug = new StringBuilder();
    for (Property a : propsByFrequency) {
      if (a.newName == null) {
        a.newName = nameGen.generateNextName();
        reservedNames.add(a.newName);
      }

      debug.append(a.oldName).append(" => ").append(a.newName).append('\n');
    }

    compiler.addToDebugLog("JS property assignments:\n" + debug);

    // Update the string nodes.
    boolean changed = false;
    for (Node n : stringNodes) {
      String oldName = n.getString();
      Property a = properties.get(oldName);
      if (a != null && a.newName != null) {
        n.setString(a.newName);
        changed = changed || !a.newName.equals(oldName);
      }
    }

    if (changed) {
      compiler.reportCodeChange();
    }

    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED_OBFUSCATED);
  }

  /**
   * Runs through the list of properties and tries to rename as many as possible
   * with names that were used for them in the previous compilation.
   * {@code reservedNames} is updated with the set of reused names.
   * @param properties The set of properties to attempt to rename.
   */
  private void reusePrototypeNames(Set<Property> properties) {
    for (Property prop : properties) {
      String prevName = prevUsedRenameMap.lookupNewName(prop.oldName);
      if (prevName != null) {
        if (reservedNames.contains(prevName)) {
          continue;
        }

        prop.newName = prevName;
        reservedNames.add(prevName);
      }
    }
  }

  /**
   * Iterate through the nodes, collect all of the STRING nodes that are
   * children of GETPROP or GETELEM and mark them as externs.
   */
  private class ProcessExternedProperties extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
        case Token.GETELEM:
          Node dest = n.getFirstChild().getNext();
          if (dest.isString()) {
            reservedNames.add(dest.getString());
          }
      }
    }
  }

  /**
   * Iterate through the nodes, collect all of the STRING nodes that are
   * children of GETPROP, GETELEM, or OBJLIT, and also count the number of
   * times each STRING is referenced.
   *
   * Also collects OBJLIT assignments of prototypes as candidates for renaming.
   */
  private class ProcessProperties extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
        case Token.GETELEM:
          Node dest = n.getFirstChild().getNext();
          if (dest.isString()) {
            String s = dest.getString();
            if (s.equals("prototype")) {
              processPrototypeParent(parent, t.getInput());
            } else {
              markPropertyAccessCandidate(dest, t.getInput());
            }
          }
          break;
        case Token.OBJECTLIT:
          if (!prototypeObjLits.contains(n)) {
            // Object literals have their property name/value pairs as a flat
            // list as their children. We want every other node in order to get
            // only the property names.
            for (Node child = n.getFirstChild();
                 child != null;
                 child = child.getNext()) {

              if (TokenStream.isJSIdentifier(child.getString())) {
                markObjLitPropertyCandidate(child, t.getInput());
              }
            }
          }
          break;
      }
    }

    /**
     * Processes the parent of a GETPROP prototype, which can either be
     * another GETPROP (in the case of Foo.prototype.bar), or can be
     * an assignment (in the case of Foo.prototype = ...).
     */
    private void processPrototypeParent(Node n, CompilerInput input) {
      switch (n.getType()) {
        // Foo.prototype.getBar = function() { ... }
        case Token.GETPROP:
        case Token.GETELEM:
          Node dest = n.getFirstChild().getNext();
          if (dest.isString()) {
            markPrototypePropertyCandidate(dest, input);
          }
          break;

        // Foo.prototype = { "getBar" : function() { ... } }
        case Token.ASSIGN:
        case Token.CALL:
          Node map;
          if (n.isAssign()) {
            map = n.getFirstChild().getNext();
          } else {
            map = n.getLastChild();
          }
          if (map.isObjectLit()) {
            // Remember this node so that we can avoid processing it again when
            // the traversal reaches it.
            prototypeObjLits.add(map);

            for (Node key = map.getFirstChild();
                 key != null; key = key.getNext()) {
              if (TokenStream.isJSIdentifier(key.getString())) {
               // May be STRING, GET, or SET
                markPrototypePropertyCandidate(key, input);
              }
            }
          }
          break;
      }
    }

    /**
     * Remembers the given String node and increments the property name's
     * access count.
     *
     * @param n A STRING node
     * @param input The Input that the node came from
     */
    private void markPrototypePropertyCandidate(Node n, CompilerInput input) {
      stringNodes.add(n);
      getProperty(n.getString()).prototypeCount++;
    }

    /**
     * Remembers the given String node and increments the property name's
     * access count.
     *
     * @param n A STRING node
     * @param input The Input that the node came from
     */
    private void markObjLitPropertyCandidate(Node n, CompilerInput input) {
      stringNodes.add(n);
      getProperty(n.getString()).objLitCount++;
    }

    /**
     * Remembers the given String node and increments the property name's
     * access count.
     *
     * @param n A STRING node
     * @param input The Input that the node came from
     */
    private void markPropertyAccessCandidate(Node n, CompilerInput input) {
      stringNodes.add(n);
      getProperty(n.getString()).refCount++;
    }

    /**
     * Gets the current property for the given name, creating a new one if
     * none exists.
     */
    private Property getProperty(String name) {
      Property prop = properties.get(name);
      if (prop == null) {
        prop = new Property(name);
        properties.put(name, prop);
      }
      return prop;
    }
  }

  /**
   * Gets the property renaming map.
   *
   * @return A mapping from original names to new names
   */
  VariableMap getPropertyMap() {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    for (Property p : properties.values()) {
      if (p.newName != null) {
        map.put(p.oldName, p.newName);
      }
    }
    return new VariableMap(map.build());
  }
}
