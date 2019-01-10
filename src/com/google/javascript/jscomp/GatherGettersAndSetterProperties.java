/*
 * Copyright 2018 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AbstractCompiler.PropertyAccessKind;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;

/**
 * Finds getter and setter properties in the AST.
 *
 * <p>Used to back off certain optimizations, e.g. code removal.
 */
final class GatherGettersAndSetterProperties implements CompilerPass {

  private final AbstractCompiler compiler;

  GatherGettersAndSetterProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    update(compiler, externs, root);
  }

  /** Gathers all getters and setters in the AST. */
  static void update(AbstractCompiler compiler, Node externs, Node root) {
    compiler.setExternGetterAndSetterProperties(gather(compiler, externs));
    compiler.setSourceGetterAndSetterProperties(gather(compiler, root));
  }

  static ImmutableMap<String, PropertyAccessKind> gather(AbstractCompiler compiler, Node root) {
    GatherCallback gatherCallback = new GatherCallback();
    NodeTraversal.traverse(compiler, root, gatherCallback);
    return ImmutableMap.copyOf(gatherCallback.properties);
  }

  private static final class GatherCallback extends AbstractPostOrderCallback {
    private final LinkedHashMap<String, PropertyAccessKind> properties = new LinkedHashMap<>();

    private void record(String property, PropertyAccessKind kind) {
      properties.merge(property, kind, PropertyAccessKind::unionWith);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case GETTER_DEF:
          recordGetterDef(n);
          break;
        case SETTER_DEF:
          recordSetterDef(n);
          break;
        case CALL:
          if (NodeUtil.isObjectDefinePropertyDefinition(n)) {
            visitDefineProperty(n);
          } else if (NodeUtil.isObjectDefinePropertiesDefinition(n)) {
            visitDefineProperties(n);
          }
          break;
        default:
          break;
      }
    }

    private void recordGetterDef(Node getterDef) {
      checkState(getterDef.isGetterDef());

      String name = getterDef.getString();
      record(name, PropertyAccessKind.GETTER_ONLY);
    }

    private void recordSetterDef(Node setterDef) {
      checkState(setterDef.isSetterDef());

      String name = setterDef.getString();
      record(name, PropertyAccessKind.SETTER_ONLY);
    }

    /**
     * Looks for getters and setters passed to Object.defineProperty or Object.defineProperties.
     *
     * <pre>{@code
     * Object.defineProperty(obj, 'propertyName', { /* descriptor *\/ });
     * Object.defineProperties(obj, { 'propertyName': { /* descriptor *\/ } });
     * }</pre>
     */
    private void visitDescriptor(String propertyName, Node descriptor) {
      for (Node key : descriptor.children()) {
        if (key.isStringKey() || key.isMemberFunctionDef()) {
          if ("get".equals(key.getString())) {
            record(propertyName, PropertyAccessKind.GETTER_ONLY);
          } else if ("set".equals(key.getString())) {
            record(propertyName, PropertyAccessKind.SETTER_ONLY);
          }
        }
      }
    }

    private void visitDefineProperty(Node definePropertyCall) {
      Node propertyNameNode = definePropertyCall.getChildAtIndex(2);
      Node descriptor = definePropertyCall.getChildAtIndex(3);

      if (!propertyNameNode.isString() || !descriptor.isObjectLit()) {
        return;
      }

      String propertyName = propertyNameNode.getString();
      visitDescriptor(propertyName, descriptor);
    }

    private void visitDefineProperties(Node definePropertiesCall) {
      Node props = definePropertiesCall.getChildAtIndex(2);

      if (!props.isObjectLit()) {
        return;
      }

      for (Node prop : props.children()) {
        if (prop.isStringKey() && prop.hasOneChild() && prop.getFirstChild().isObjectLit()) {
          String propertyName = prop.getString();
          Node descriptor = prop.getFirstChild();

          visitDescriptor(propertyName, descriptor);
        }
      }
    }
  }
}
