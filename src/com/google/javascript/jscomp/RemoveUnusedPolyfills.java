/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Removes any unused polyfill instance methods, using type information to
 * disambiguate calls.  This is a separate pass from {@link RewritePolyfills}
 * because once optimization has started it's not feasible to inject any
 * further runtime libraries, since they're all inter-related.  Thus, the
 * initial polyfill pass is very liberal in the polyfills it adds.  This
 * pass prunes the cases where the type checker can verify that the polyfill
 * was not actually needed.
 *
 * It would be great if we didn't need a special-case optimization for this,
 * i.e. if polyfill injection could be delayed until after the first pass of
 * {@link SmartNameRemoval}, but this causes problems with earlier-injected
 * runtime libraries having already had their properties collapsed, so that
 * later-injected polyfills can no longer reference these names correctly.
 */
class RemoveUnusedPolyfills implements CompilerPass {

  private final AbstractCompiler compiler;

  RemoveUnusedPolyfills(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    CollectUnusedPolyfills collector = new CollectUnusedPolyfills();
    NodeTraversal.traverse(compiler, root, collector);
    for (Node node : collector.removableNodes()) {
      Node parent = node.getParent();
      NodeUtil.removeChild(parent, node);
      NodeUtil.markFunctionsDeleted(node, compiler);
      compiler.reportChangeToEnclosingScope(parent);
    }
  }

  // Main traversal logic.
  private class CollectUnusedPolyfills extends GuardedCallback<String> {

    final SetMultimap<String, PrototypeMethod> methodsByName = HashMultimap.create();
    // These maps map polyfill names to their definitions in the AST.
    // Each polyfill is considered unused by default, and if we find uses of it we
    // remove it from these maps.
    final Map<PrototypeMethod, Node> unusedMethodPolyfills = new HashMap<>();
    final Map<String, Node> unusedStaticPolyfills = new HashMap<>();
    // Set of all qualified name suffixes for installed polyfills, so
    // that we do not need to construct qualified names for everything.
    final Set<String> suffixes = new HashSet<>();

    CollectUnusedPolyfills() {
      super(compiler);
    }

    Iterable<Node> removableNodes() {
      return Iterables.concat(unusedMethodPolyfills.values(), unusedStaticPolyfills.values());
    }

    @Override
    public void visitGuarded(NodeTraversal traversal, Node n, Node parent) {
      if (NodeUtil.isExprCall(n)) {
        Node call = n.getFirstChild();
        Node callee = call.getFirstChild();
        if (isPolyfillDefinition(callee)) {
          // A polyfill definition looks like this:
          // $jscomp.polyfill('Array.prototype.includes', ...);
          String polyfillName = call.getSecondChild().getString();
          visitPolyfillDefinition(n, polyfillName);
        }
      }
      if (n.isQualifiedName() && suffixes.contains(getLastPartOfQualifiedName(n))) {
        visitPossibleStaticPolyfillUse(n);
      }
      if (n.isGetProp()) {
        visitPossibleMethodPolyfillUse(n);
      }
    }

    // Determine if the definition is for a static or a method, and add it to the
    // appropriate "unused polyfills" map, to be removed later when a use is found.
    void visitPolyfillDefinition(Node n, String polyfillName) {
      // Find the $jscomp.polyfill calls and add them to the table.
      PrototypeMethod method = PrototypeMethod.split(polyfillName);
      if (method != null) {
        if (unusedMethodPolyfills.put(method, n) != null) {
          throw new RuntimeException(method + " polyfilled multiple times.");
        }
        methodsByName.put(method.method(), method);
        suffixes.add(method.method());
      } else {
        if (unusedStaticPolyfills.put(polyfillName, n) != null) {
          throw new RuntimeException(polyfillName + " polyfilled multiple times.");
        }
        suffixes.add(polyfillName.substring(polyfillName.lastIndexOf('.') + 1));
      }
    }

    // Determine if a static polyfill is being used (or if a method polyfill is being
    // used statically).  If so, remove it from the respective "unused polyfills" map.
    void visitPossibleStaticPolyfillUse(Node n) {
      String qname = removeExplicitGlobalPrefix(n.getQualifiedName());
      if (!isGuarded(qname)) {
        unusedStaticPolyfills.remove(qname);
        unusedMethodPolyfills.remove(PrototypeMethod.split(qname));
      }
    }

    // Determine if a GETPROP node could reference any polyfilled methods, now that
    // we have type information.  If so, remove any possibile matches from the
    // unusedMethodPolyfills map.
    void visitPossibleMethodPolyfillUse(Node n) {
      // Now look at the method name and possible target types.
      String methodName = n.getLastChild().getString();
      Set<PrototypeMethod> methods = methodsByName.get(methodName);
      if (methods.isEmpty() || isGuarded("." + methodName)) {
        return;
      }

      // Check all the methods to see if the types could possibly be compatible.
      // If so, remove from the unused methods map.
      JSType receiverType = determineReceiverType(n);
      for (PrototypeMethod method : ImmutableSet.copyOf(methods)) {
        if (isTypeCompatible(receiverType, method.type())) {
          unusedMethodPolyfills.remove(method);
        }
      }
    }

    // Returns the type of the first child of the given node, if it's specific
    // enough to be useful for polyfill removal.  Unknown types, top, bottom,
    // and equivalent-to-object all return null, since they don't allow backing
    // off at all.
    JSType determineReceiverType(Node n) {
      JSType receiverType = n.getFirstChild().getJSType();
      if (NodeUtil.isPrototypeProperty(n)) {
        JSType maybeCtor = n.getFirstFirstChild().getJSType();
        if (maybeCtor != null && maybeCtor.isConstructor()) {
          receiverType = maybeCtor.toMaybeFunctionType().getInstanceType();
        }
      }

      // No type information at all, return null.
      if (receiverType == null) {
        return null;
      }

      // If the known type is too generic to be useful, also return null.
      receiverType = receiverType.restrictByNotNullOrUndefined();
      if (receiverType.isUnknownType()
          || receiverType.isEmptyType()
          || receiverType.isAllType()
          || receiverType.isEquivalentTo(
              compiler.getTypeRegistry().getNativeType(JSTypeNative.OBJECT_TYPE))) {
        return null;
      }

      return receiverType;
    }

    // Checks whether a receiver type determined by the type checker could
    // possibly be a match for the given typename,
    boolean isTypeCompatible(JSType receiverType, String typeName) {
      // Unknown/general types are compatible with everything.
      if (receiverType == null) {
        return true;
      }

      // Look up the typename in the registry.  All the polyfilled method
      // receiver types are built-in JS types, so they had better not be
      // missing from the registry.
      JSType type = compiler.getTypeRegistry().getGlobalType(typeName);
      if (type == null) {
        throw new RuntimeException("Missing built-in type: " + typeName);
      }

      // If there is any non-bottom type in common, then the types are compatible.
      if (!receiverType.meetWith(type).isEmptyType()) {
        return true;
      }

      // One last check - if this is a wrapped primitive type, then check the unwrapped version too.
      String primitiveType = unwrapPrimitiveWrapperTypename(typeName);
      return primitiveType != null && isTypeCompatible(receiverType, primitiveType);
    }
  }

  // Returns the final part of a qualified name, e.g. "of" from 'Array.of' and "Map" from 'Map',
  // or null for 'this' and 'super'.
  private static String getLastPartOfQualifiedName(Node n) {
    if (n.isName()) {
      return n.getString();
    } else if (n.isGetProp()) {
      return n.getLastChild().getString();
    }
    return null;
  }

  // Removes any "goog.global" (or similar) prefix from a qualified name.
  private static String removeExplicitGlobalPrefix(String qname) {
    for (String global : GLOBAL_NAMES) {
      if (qname.startsWith(global)) {
        return qname.substring(global.length());
      }
    }
    return qname;
  }

  private static final ImmutableSet<String> GLOBAL_NAMES =
      ImmutableSet.of("goog.global.", "goog$global.", "window.");

  // Checks whether the node is (or was) a call to $jscomp.polyfill.
  private static boolean isPolyfillDefinition(Node callee) {
    // If the callee is just $jscomp.polyfill then it's easy.
    if (callee.matchesQualifiedName("$jscomp.polyfill")
        || callee.matchesQualifiedName("$jscomp$polyfill")) {
      return true;
    }
    // It's possible that the function has been inlined, so look for
    // a four-parameter function with parameters who have the correct
    // prefix (since a disambiguate suffix may have been added).
    if (callee.isFunction()) {
      Node paramList = callee.getSecondChild();
      Node param = paramList.getFirstChild();
      if (paramList.hasXChildren(4)) {
        for (String name : POLYFILL_PARAMETERS) {
          if (!param.isName() || !param.getString().startsWith(name)) {
            return false;
          }
          param = param.getNext();
        }
        return true;
      }
    }
    return false;
  }

  private static final ImmutableList<String> POLYFILL_PARAMETERS =
      ImmutableList.of("target", "polyfill", "fromLang", "toLang");

  // Converts a wrapper type name to its primitive type, or returns null otherwise.
  private static String unwrapPrimitiveWrapperTypename(String type) {
    return PRIMITIVE_WRAPPERS.get(type);
  }

  private static final ImmutableMap<String, String> PRIMITIVE_WRAPPERS = ImmutableMap.of(
      "Boolean", "boolean",
      "Number", "number",
      "String", "string");

  // Package-private for AutoValue. Otherwise would be private.
  @AutoValue
  @Immutable
  abstract static class PrototypeMethod {
    abstract String type();
    abstract String method();

    /**
     * Builds a new PrototypeMethod from the qualified name <TYPE>.prototype.<METHOD>, or returns
     * null if the qualified name does not match that pattern.
     */
    @Nullable
    static PrototypeMethod split(String name) {
      int index = name.indexOf(PROTOTYPE);
      return index < 0
          ? null
          : new AutoValue_RemoveUnusedPolyfills_PrototypeMethod(
              name.substring(0, index), name.substring(index + PROTOTYPE.length()));
    }

    @Override
    public String toString() {
      return type() + PROTOTYPE + method();
    }

    private static final String PROTOTYPE = ".prototype.";
  }
}
