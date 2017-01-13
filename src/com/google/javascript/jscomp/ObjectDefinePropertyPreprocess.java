/*
 * Copyright 2014 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Rewrites <code>Object.defineProperty(foo, 'bar', {});</code> to
 * <code>/** @expose * /foo.bar; Object.defineProperty...</code>
 *
 * These phase is for Object.defineProperty and will also annotate the functions
 * presented into the ObjectLit for "this" object when the defineProperty object
 * is prototype.
 */
public class ObjectDefinePropertyPreprocess implements CompilerPass {
  static final String OBJECT_DEFINE_PROPERTY =
      "Object.defineProperty";

  public static final DiagnosticType DYNAMIC_DEFINE_PROPERTY_NAME_WARNING =
    DiagnosticType.warning(
        "DYNAMIC_DEFINE_PROPERTY_NAME_WARNING",
        "The use of dynamic name for Object.defineProperty is not supported");

  private final AbstractCompiler compiler;

  ObjectDefinePropertyPreprocess(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Callback());
  }

  private class Callback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall() &&
          n.getFirstChild().matchesQualifiedName(OBJECT_DEFINE_PROPERTY)) {
        annotateSetterGetters(n, parent);
      }
    }

    /**
     * Mark creation of setter/getter functions added to object using
     * Object.defineProperty(object, 'name', setter/getter);
     */
    private void annotateSetterGetters(Node n, Node parent) {
      Preconditions.checkState(
          n.getFirstChild().matchesQualifiedName("Object.defineProperty"));

      Node propertyName = n.getChildAtIndex(2);
      Node obj = n.getChildAtIndex(1);

      if (!propertyName.isString()) {
        /* Cannot handle non literal strings as properties */
        compiler.report(JSError.make(propertyName,
            DYNAMIC_DEFINE_PROPERTY_NAME_WARNING));
        return;
      }

      String objName = obj.getQualifiedName();

      /* create a JSDocInfo for the property */
      JSDocInfoBuilder infoBuilder;
      JSDocInfo propInfo = NodeUtil.getBestJSDocInfo(n);
      if (propInfo == null) {
        infoBuilder = new JSDocInfoBuilder(false);
      } else {
        infoBuilder = JSDocInfoBuilder.copyFrom(propInfo);
      }
      infoBuilder.recordExpose();
      propInfo = infoBuilder.build(n);

      /* add the property to the AST, so the JSDoc info can be attached */
      String propName = objName + "." + propertyName.getString();
      Node namedProperty = NodeUtil.newQualifiedNameNodeDeclaration(
          compiler.getCodingConvention(), propName, null, propInfo);

      NodeUtil.setDebugInformation(namedProperty, n, propName);
      namedProperty.copyInformationFrom(n);
      parent.getParent().addChildBefore(namedProperty, parent);
      compiler.reportCodeChange();

      if (objName.endsWith(".prototype")) {
        /* this is a prototype - annotate "this" */
        Node descriptor = n.getChildAtIndex(3);
        Node classNode = NodeUtil.getPrototypeClassName(obj);
        JSTypeExpression type = new JSTypeExpression(
            new Node(Token.BANG, IR.string(classNode.getQualifiedName())),
            classNode.getSourceFileName());
        for (int i = 0; i < descriptor.getChildCount(); i++) {
          Node child = descriptor.getChildAtIndex(i);
          /* if the child is [sg]et: function() mark @this */
          if (child != null && child.getFirstChild() != null &&
              child.getFirstChild().isFunction()) {
            markThisFunction(child.getFirstChild(), type);
          }
        }
      }
    }

    /**
     * Mark the "@this" in functions in defineProperty
     *
     * @param func - the node of the function
     * @param type - the type of "this"
     */
    private void markThisFunction(Node func, JSTypeExpression type) {
      Preconditions.checkState(func.isFunction());

      JSDocInfoBuilder infoBuilder;
      JSDocInfo funcInfo;
      funcInfo = NodeUtil.getBestJSDocInfo(func);
      if (funcInfo == null) {
        infoBuilder = new JSDocInfoBuilder(false);
      } else {
        infoBuilder = JSDocInfoBuilder.copyFrom(funcInfo);
      }
      infoBuilder.recordThisType(type);

      funcInfo = infoBuilder.build(func);
      func.setJSDocInfo(funcInfo);
    }
  }
}
