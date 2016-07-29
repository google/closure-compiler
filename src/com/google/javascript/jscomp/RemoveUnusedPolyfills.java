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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    Traverser traverser = new Traverser();
    NodeTraversal.traverseEs6(compiler, root, traverser);
    boolean changed = false;
    for (Node node : traverser.removableNodes()) {
      NodeUtil.removeChild(node.getParent(), node);
      changed = true;
    }
    if (changed) {
      compiler.reportCodeChange();
    }
  }

  private static final ImmutableMap<String, String> PRIMITIVE_WRAPPERS = ImmutableMap.of(
      "Boolean", "boolean",
      "Number", "number",
      "String", "string");

  private class Traverser extends AbstractPostOrderCallback {

    final SetMultimap<String, PrototypeMethod> methodsByName = HashMultimap.create();
    final Map<PrototypeMethod, Node> methodPolyfills = new HashMap<>();
    final Map<String, Node> staticPolyfills = new HashMap<>();

    Iterable<Node> removableNodes() {
      return Iterables.concat(methodPolyfills.values(), staticPolyfills.values());
    }

    void visitPolyfillDefinition(Node node, String polyfill) {
      // Find the $jscomp.polyfill calls and add them to the table.
      PrototypeMethod method = PrototypeMethod.split(polyfill);
      if (method != null) {
        if (methodPolyfills.put(method, node) != null) {
          throw new RuntimeException(method + " polyfilled multiple times.");
        }
        methodsByName.put(method.method, method);
      } else {
        if (staticPolyfills.put(polyfill, node) != null) {
          throw new RuntimeException(polyfill + " polyfilled multiple times.");
        }
      }
    }

    void visitPossiblePolyfillUse(Node node) {
      // Remove anything from the table that could possibly be needed.

      if (node.isQualifiedName()) {
        // First remove anything with an exact qualified name match.
        String qname = node.getQualifiedName();
        qname = qname.replaceAll("^(goog\\.global\\.|window\\.)", "");
        staticPolyfills.remove(qname);
        methodPolyfills.remove(PrototypeMethod.split(qname));
      }

      if (node.isGetProp()) {
        // Now look at the method name and possible target types.
        String methodName = node.getLastChild().getString();
        Node target = node.getFirstChild();

        Set<PrototypeMethod> methods = methodsByName.get(methodName);

        if (methods.isEmpty()) {
          return;
        }

        JSType targetType = target.getJSType();
        if (targetType == null) {
          // TODO(sdh): When does this happen?  If it means incomplete type information, then
          // we need to remove all the potential methods.  If not, we can just return.
          methodPolyfills.keySet().removeAll(methods);
          return;
        }
        targetType = targetType.restrictByNotNullOrUndefined();

        TypeIRegistry registry = compiler.getTypeIRegistry();
        if (targetType.isUnknownType()
            || targetType.isEmptyType()
            || targetType.isAllType()
            || targetType.isEquivalentTo(
                registry.getNativeType(JSTypeNative.OBJECT_TYPE))) {
          methodPolyfills.keySet().removeAll(methods);
        }

        for (PrototypeMethod method : ImmutableList.copyOf(methods)) {
          checkType(targetType, registry, method, method.type);
          String primitiveType = PRIMITIVE_WRAPPERS.get(method.type);
          if (primitiveType != null) {
            checkType(targetType, registry, method, primitiveType);
          }
        }
      }
    }

    private void checkType(
        JSType targetType, TypeIRegistry registry, PrototypeMethod method, String typeName) {
      JSType type = registry.getType(typeName);
      if (type == null) {
        throw new RuntimeException("Missing built-in type: " + typeName);
      }
      if (!targetType.getGreatestSubtype(type).isBottom()) {
        methodPolyfills.remove(method);
      }
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      if (NodeUtil.isExprCall(node)) {
        Node call = node.getFirstChild();
        Node name = call.getFirstChild();
        String originalName = name.getOriginalQualifiedName();
        if ("$jscomp.polyfill".equals(originalName)) {
          visitPolyfillDefinition(node, name.getNext().getString());
        }
      }
      visitPossiblePolyfillUse(node);
    }
  }

  // Simple value type for a (type,method) pair.
  private static class PrototypeMethod {
    final String type;
    final String method;
    PrototypeMethod(String type, String method) {
      this.type = type;
      this.method = method;
    }
    @Override public boolean equals(Object other) {
      return other instanceof PrototypeMethod
          && ((PrototypeMethod) other).type.equals(type)
          && ((PrototypeMethod) other).method.equals(method);
    }
    @Override public int hashCode() {
      return Objects.hash(type, method);
    }
    @Override public String toString() {
      return type + ".prototype." + method;
    }
    static PrototypeMethod split(String name) {
      int index = name.indexOf(PROTOTYPE);
      return index < 0
          ? null
          : new PrototypeMethod(
                name.substring(0, index), name.substring(index + PROTOTYPE.length()));
    }
  }
  private static final String PROTOTYPE = ".prototype.";
}
