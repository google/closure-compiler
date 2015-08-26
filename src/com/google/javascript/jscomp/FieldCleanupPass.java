/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

/**
 * A CleanupPass implementation that will remove all field declarations on
 * JSTypes contributed by the original file.
 * <p>
 * This pass is expected to clear out declarations contributed to any JSType,
 * even if the constructor declaration is not provided in the file being
 * updated.
 *
 * @author tylerg@google.com (Tyler Goodwin)
 */
public final class FieldCleanupPass implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;

  public FieldCleanupPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    String srcName = originalRoot.getSourceFileName();
    Callback cb =
        new QualifiedNameSearchTraversal(compiler.getTypeRegistry(), srcName);
    NodeTraversal.traverseEs6(compiler, originalRoot, cb);
  }

  @Override
  public void process(Node externs, Node root) {
    // FieldCleanupPass should not do work during process.
  }

  /**
   * Search for fields to cleanup by looking for nodes in the tree which are
   * root nodes of qualified names and getting the final token of the qualified
   * name as a candidate field.
   * <p>
   * Once a candidate field is found, ask the {@code JSTypeRegistry} for all
   * JSTypes that have a field with the same name, and check if the field on
   * that type is defined in the file the compiler is cleaning up. If so, remove
   * the field, and update the {@code JSTypeRegistry} to no longer associate the
   * type with the field.
   * <p>
   * This algorithm was chosen for simplicity and is less than optimally
   * efficient in two ways:
   * <p>
   * 1) All types with a matching field name are iterated over (when only types
   * that extend or implement the JSType indicated by the containing object in
   * the found Qualified Name need to be checked).
   * <p>
   * 2) All Qualified Names are checked, even those which are not L-Values or
   * single declarations of an Type Expression. In general field should only be
   * declared as part of an assignment ('ns.Type.a = 3;') or stand alone name
   * declaration ('ns.Type.a;').
   */
  static class QualifiedNameSearchTraversal extends AbstractShallowCallback {

    private final JSTypeRegistry typeRegistry;
    private final String srcName;

    public QualifiedNameSearchTraversal(
        JSTypeRegistry typeRegistry, String srcName) {
      this.typeRegistry = typeRegistry;
      this.srcName = srcName;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node p) {
      // We are a root GetProp
      if (n.isGetProp() && !p.isGetProp()) {
        String propName = getFieldName(n);
        JSType type = n.getFirstChild().getJSType();
        if (type == null || type.toObjectType() == null) {
          // Note cases like <primitive>.field
          return;
        }
        removeProperty(type.toObjectType(), propName);
      }
    }

    /**
     * Removes a given property from a type and updates type-registry.
     *
     * @param type the object type to be updated, should not be null
     * @param propName the property to remove
     */
    private void removeProperty(ObjectType type, String propName) {
      Node pNode = type.getPropertyNode(propName);
      if (pNode != null && srcName.equals(pNode.getSourceFileName())) {
        typeRegistry.unregisterPropertyOnType(propName, type);
        type.removeProperty(propName);
      }
    }

    private static String getFieldName(Node n) {
      return n.getLastChild().getString();
    }
  }
}
