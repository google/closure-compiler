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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
    CollectUnusedPolyfills collector = new CollectUnusedPolyfills();
    NodeTraversal.traverseEs6(compiler, root, collector);
    for (Node node : collector.removableNodes()) {
      NodeUtil.removeChild(node.getParent(), node);
      compiler.reportCodeChange();
    }
  }

  private static final ImmutableMap<String, String> PRIMITIVE_WRAPPERS = ImmutableMap.of(
      "Boolean", "boolean",
      "Number", "number",
      "String", "string");

  private class CollectUnusedPolyfills extends AbstractPostOrderCallback {

    final SetMultimap<String, PrototypeMethod> methodsByName = HashMultimap.create();
    // These maps map polyfill names to their definitions in the AST.
    // Each polyfill is considered unused by default, and if we find uses of it we
    // remove it from these maps.
    final Map<PrototypeMethod, Node> unusedMethodPolyfills = new HashMap<>();
    final Map<String, Node> unusedStaticPolyfills = new HashMap<>();

    Iterable<Node> removableNodes() {
      return Iterables.concat(unusedMethodPolyfills.values(), unusedStaticPolyfills.values());
    }

    @Override
    public void visit(NodeTraversal traversal, Node n, Node parent) {
      if (NodeUtil.isExprCall(n)) {
        Node call = n.getFirstChild();
        Node callee = call.getFirstChild();
        String originalName = callee.getOriginalQualifiedName();
        if ("$jscomp.polyfill".equals(originalName)) {
          // A polyfill definition looks like this:
          // $jscomp.polyfill('Array.prototype.includes', ...);
          String polyfillName = call.getSecondChild().getString();
          visitPolyfillDefinition(n, polyfillName);
        }
      } else if (n.isGetProp() || n.isQualifiedName()) {
        visitPossiblePolyfillUse(n);
      }
    }

    void visitPolyfillDefinition(Node n, String polyfillName) {
      // Find the $jscomp.polyfill calls and add them to the table.
      PrototypeMethod method = PrototypeMethod.split(polyfillName);
      if (method != null) {
        if (unusedMethodPolyfills.put(method, n) != null) {
          throw new RuntimeException(method + " polyfilled multiple times.");
        }
        methodsByName.put(method.method, method);
      } else {
        if (unusedStaticPolyfills.put(polyfillName, n) != null) {
          throw new RuntimeException(polyfillName + " polyfilled multiple times.");
        }
      }
    }

    void visitPossiblePolyfillUse(Node n) {
      // Remove anything from the table that could possibly be needed.
      if (n.isQualifiedName()) {
        // First remove anything with an exact qualified name match.
        String qname = n.getQualifiedName();
        qname = qname.replaceAll("^(goog\\.global\\.|window\\.)", "");
        unusedStaticPolyfills.remove(qname);
        unusedMethodPolyfills.remove(PrototypeMethod.split(qname));
      }
      if (!n.isGetProp()) {
        return;
      }
      // Now look at the method name and possible target types.
      String methodName = n.getLastChild().getString();
      Set<PrototypeMethod> methods = methodsByName.get(methodName);
      if (methods.isEmpty()) {
        return;
      }
      JSType receiverType = n.getFirstChild().getJSType();
      if (receiverType == null) {
        // TODO(sdh): When does this happen?  If it means incomplete type information, then
        // we need to remove all the potential methods.  If not, we can just return.
        unusedMethodPolyfills.keySet().removeAll(methods);
        return;
      }
      receiverType = receiverType.restrictByNotNullOrUndefined();
      TypeIRegistry registry = compiler.getTypeIRegistry();
      if (receiverType.isUnknownType()
          || receiverType.isEmptyType()
          || receiverType.isAllType()
          || receiverType.isEquivalentTo(
              registry.getNativeType(JSTypeNative.OBJECT_TYPE))) {
        unusedMethodPolyfills.keySet().removeAll(methods);
      }
      for (PrototypeMethod method : ImmutableSet.copyOf(methods)) {
        checkType(receiverType, registry, method, method.type);
        String primitiveType = PRIMITIVE_WRAPPERS.get(method.type);
        if (primitiveType != null) {
          checkType(receiverType, registry, method, primitiveType);
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
        unusedMethodPolyfills.remove(method);
      }
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
      return type + PROTOTYPE + method;
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
