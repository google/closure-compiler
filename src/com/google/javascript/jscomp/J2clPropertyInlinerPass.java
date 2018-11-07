/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;

/**
 * This pass targets J2CL output. It looks for static get and set methods defined within a class
 * that match the signature of J2CL static fields and inlines them at their
 * call sites.  This is done for performance reasons since getter and setter accesses are slower
 * than regular field accesses.
 *
 * <p>This will be done by looking at all property accesses and determining if they have a
 * corresponding get or set method on the property qualifiers definition.  Some caveats:
 * <ul>
 * <li> Avoid inlining if the property is set using compound assignments.</li>
 * <li> Avoid inlining if the property is incremented using ++ or --</li>
 * </ul>
 *
 * Since this pass only really works after the AggressiveInlineAliases pass has run, we
 * have to look for Object.defineProperties instead of es6 get and set nodes since es6
 * transpilation has already occurred if the language out is ES5.
 *
 */
public class J2clPropertyInlinerPass implements CompilerPass {
  final AbstractCompiler compiler;

  public J2clPropertyInlinerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    if (!J2clSourceFileChecker.shouldRunJ2clPasses(compiler)) {
      return;
    }

    new StaticFieldGetterSetterInliner(root).run();
    // This pass may remove getters and setters.
    GatherGettersAndSetterProperties.update(compiler, externs, root);
  }

  class StaticFieldGetterSetterInliner {
    Node root;

    StaticFieldGetterSetterInliner(Node root) {
      this.root = root;
    }

    private void run() {
      GatherJ2CLClassGetterSetters gatherer = new GatherJ2CLClassGetterSetters();
      NodeTraversal.traverse(compiler, root, gatherer);
      Map<String, J2clProperty> result = gatherer.getResults();
      NodeTraversal.traverse(compiler, root, new DetermineInlinableProperties(result));
      new InlinePropertiesPass(result).run();
    }

    private abstract class J2clProperty {
      final Node getKey;
      final Node setKey;
      boolean isSafeToInline;

      public J2clProperty(Node getKey, Node setKey) {
        this.getKey = getKey;
        this.setKey = setKey;
        this.isSafeToInline = true;
      }

      abstract void remove();
    }

    /** A J2CL property with a getter and setter from an Object.defineProperties call */
    private class J2clPropertyEs5 extends J2clProperty {
      public J2clPropertyEs5(Node getKey, Node setKey) {
        super(getKey, setKey);
        checkArgument(getKey.isStringKey() && getKey.getString().equals("get"), getKey);
        checkArgument(setKey.isStringKey() && setKey.getString().equals("set"), setKey);
      }

      @Override
      void remove() {
        Node nodeToDetach = getKey.getGrandparent();
        Node objectLit = nodeToDetach.getParent();
        checkState(objectLit.isObjectLit(), objectLit);
        nodeToDetach.detach();
        NodeUtil.markFunctionsDeleted(nodeToDetach, compiler);
        compiler.reportChangeToEnclosingScope(objectLit);
        if (!objectLit.hasChildren()) {
          // Remove the whole Object.defineProperties call if there are no properties left.
          objectLit.getParent().getParent().detach();
        }
      }
    }

    /** A J2CL property created with a ES6-style static getter and setter */
    private class J2clPropertyEs6 extends J2clProperty {
      public J2clPropertyEs6(Node getKey, Node setKey) {
        super(getKey, setKey);
        checkArgument(getKey.isGetterDef(), getKey);
        checkArgument(setKey.isSetterDef(), setKey);
      }

      @Override
      void remove() {
        Node classMembers = getKey.getParent();
        checkState(classMembers.isClassMembers(), classMembers);
        classMembers.removeChild(getKey);
        classMembers.removeChild(setKey);
        NodeUtil.markFunctionsDeleted(getKey, compiler);
        NodeUtil.markFunctionsDeleted(setKey, compiler);
        compiler.reportChangeToEnclosingScope(classMembers);
      }
    }

    /**
     * <li> We match J2CL property getters  by looking for the following signature:
     * <pre>{@code
     * get: function() { return (ClassName.$clinit(), ClassName.$fieldName)};
     * </pre>
     */
    private boolean matchesJ2clGetKeySignature(String className, Node getKey) {
      if (!getKey.hasChildren() || !getKey.getFirstChild().isFunction()) {
        return false;
      }
      Node getFunction = getKey.getFirstChild();
      if (!getFunction.hasChildren() || !getFunction.getLastChild().isBlock()) {
        return false;
      }
      Node getBlock = getFunction.getLastChild();
      if (!getBlock.hasChildren()
          || !getBlock.hasOneChild()
          || !getBlock.getFirstChild().isReturn()) {
        return false;
      }
      Node returnStatement = getBlock.getFirstChild();
      if (!returnStatement.getFirstChild().isComma()) {
        return false;
      }
      Node multiExpression = returnStatement.getFirstChild();
      if (!multiExpression.getFirstChild().isCall()
          || !multiExpression.getSecondChild().isGetProp()) {
        return false;
      }
      Node clinitFunction = multiExpression.getFirstFirstChild();
      Node internalProp = multiExpression.getSecondChild();
      if (!clinitFunction.matchesQualifiedName(className + ".$clinit")) {
        return false;
      }
      if (!internalProp.getQualifiedName().startsWith(className + ".$")) {
        return false;
      }
      return true;
    }

    /**
     * <li> We match J2CL property getters  by looking for the following signature:
     * <pre>{@code
     * set: function(value) { (ClassName.$clinit(), ClassName.$fieldName = value)};
     * </pre>
     */
    private boolean matchesJ2clSetKeySignature(String className, Node setKey) {
      if (!setKey.hasChildren() || !setKey.getFirstChild().isFunction()) {
        return false;
      }
      Node setFunction = setKey.getFirstChild();
      if (!setFunction.hasChildren()
          || !setFunction.getLastChild().isBlock()
          || !setFunction.getSecondChild().isParamList()) {
        return false;
      }
      if (!setFunction.getSecondChild().hasOneChild()) {
        // There is a single parameter "value".
        return false;
      }
      Node setBlock = setFunction.getLastChild();
      if (!setBlock.hasChildren()
          || !setBlock.getFirstChild().isExprResult()
          || !setBlock.getFirstFirstChild().isComma()) {
        return false;
      }
      Node multiExpression = setBlock.getFirstFirstChild();
      if (multiExpression.getChildCount() != 2 || !multiExpression.getSecondChild().isAssign()) {
        return false;
      }
      Node clinitFunction = multiExpression.getFirstFirstChild();
      if (!clinitFunction.matchesQualifiedName(className + ".$clinit")) {
        return false;
      }
      return true;
    }

    /**
     * This class traverses the ast and gathers get and set methods contained in
     * Object.defineProperties nodes.
     */
    private class GatherJ2CLClassGetterSetters extends AbstractPostOrderCallback {
      private final Map<String, J2clProperty> j2clPropertiesByName = new HashMap<>();

      private Map<String, J2clProperty> getResults() {
        return j2clPropertiesByName;
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n.isClass()) {
          visitClass(n);
        } else if (NodeUtil.isObjectDefinePropertiesDefinition(n)) {
          visitObjectDefineProperties(n);
        }
      }

      void visitObjectDefineProperties(Node n) {
        Node className = n.getSecondChild();
        if (!className.isName()) {
          return;
        }
        String classNameString = className.getString();
        for (Node p : NodeUtil.getObjectDefinedPropertiesKeys(n)) {
          String name = p.getString();
          // J2cl static fields are always synthesized with both a getter and setter.
          Node propertyLiteral = p.getFirstChild();
          Node getKey = null;
          Node setKey = null;
          for (Node innerKey : propertyLiteral.children()) {
            if (!innerKey.isStringKey()) {
              continue;
            }
            switch (innerKey.getString()) {
              case "get":
                if (matchesJ2clGetKeySignature(classNameString, innerKey)) {
                  getKey = innerKey;
                }
                break;
              case "set":
                if (matchesJ2clSetKeySignature(classNameString, innerKey)) {
                  setKey = innerKey;
                }
                break;
              default: // fall out
            }
          }
          if (getKey != null && setKey != null) {
            j2clPropertiesByName.put(
                classNameString + "." + name, new J2clPropertyEs5(getKey, setKey));
          }
        }
      }

      void visitClass(Node classNode) {
        String className = NodeUtil.getName(classNode);
        Node classMembers = NodeUtil.getClassMembers(classNode);

        Node getKey = null;
        Node setterDef = null;
        String name = "";
        Map<String, Node> nameToGetterOrSetterDef = new HashMap<>();

        // These J2CL-created getters and setters always come in pairs. The first time seeing a
        // getter/setter for a given name, add it to the nameToGetterOrSetterDef map.
        // Upon seeing another getter/setter for that name, create a new J2clProperty for the
        // getter/setter pair.
        for (Node memberFunction : classMembers.children()) {
          if (!memberFunction.isStaticMember()) {
            // The only getters and setters we care about are static.
            continue;
          }
          switch (memberFunction.getToken()) {
            case GETTER_DEF:
              if (matchesJ2clGetKeySignature(className, memberFunction)) {
                getKey = memberFunction;
                name = memberFunction.getString();
                setterDef = nameToGetterOrSetterDef.get(name);

                if (setterDef != null) {
                  j2clPropertiesByName.put(
                      className + "." + name, new J2clPropertyEs6(getKey, setterDef));
                  nameToGetterOrSetterDef.remove(name);
                } else {
                  nameToGetterOrSetterDef.put(name, getKey);
                }
              }
              break;
            case SETTER_DEF:
              if (matchesJ2clSetKeySignature(className, memberFunction)) {
                setterDef = memberFunction;
                name = memberFunction.getString();
                getKey = nameToGetterOrSetterDef.get(name);

                if (getKey != null) {
                  j2clPropertiesByName.put(
                      className + "." + name, new J2clPropertyEs6(getKey, setterDef));
                  nameToGetterOrSetterDef.remove(name);
                } else {
                  nameToGetterOrSetterDef.put(name, setterDef);
                }
              }
              break;
            default: // fall out
          }
        }
      }
    }

    private class DetermineInlinableProperties extends AbstractPostOrderCallback {
      private final Map<String, J2clProperty> propertiesByName;

      DetermineInlinableProperties(Map<String, J2clProperty> allGetterSetters) {
        this.propertiesByName = allGetterSetters;
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (NodeUtil.isCompoundAssignmentOp(n) || n.isInc() || n.isDec()) {
          Node assignmentTarget = n.getFirstChild();
          if (assignmentTarget.isGetProp()) {
            String accessName = assignmentTarget.getQualifiedName();
            J2clProperty prop = propertiesByName.get(accessName);
            if (prop != null) {
              prop.isSafeToInline = false;
            }
          }
        }
      }
    }

    /**
     * Look for accesses of j2cl properties and assignments to j2cl properties.
     */
    private class InlinePropertiesPass extends AbstractPostOrderCallback {
      private final Map<String, J2clProperty> propertiesByName;

      InlinePropertiesPass(Map<String, J2clProperty> allGetterSetters) {
        this.propertiesByName = allGetterSetters;
      }

      private void run() {
        NodeTraversal.traverse(compiler, root, this);

        for (J2clProperty prop : propertiesByName.values()) {
          if (prop.isSafeToInline) {
            prop.remove();
          }
        }
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n.isGetProp()) {
          if (parent.isExprResult()) {
            // This is a stub declaration for the type checker. See: Es6ToEs3ClassSideInheritance
            return;
          }
          if (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n) {
            // This case should be handled below.  It needs to be inlined differently.
            return;
          }
          String accessName = n.getQualifiedName();
          J2clProperty prop = propertiesByName.get(accessName);
          if (prop != null && prop.isSafeToInline) {
            FunctionInjector injector =
                new FunctionInjector(
                    compiler, compiler.getUniqueNameIdSupplier(), true, true, true);
            Node inlinedCall =
                injector.inline(
                    new Reference(n, t.getScope(), t.getModule(), InliningMode.DIRECT),
                    null,
                    prop.getKey.getFirstChild());
            t.getCompiler().reportChangeToEnclosingScope(inlinedCall);
          }
        }

        if (n.isAssign()) {
          Node assignmentTarget = n.getFirstChild();
          Node assignmentValue = n.getLastChild();
          if (assignmentTarget.isGetProp()) {
            String accessName = assignmentTarget.getQualifiedName();
            J2clProperty prop = propertiesByName.get(accessName);
            if (prop != null && prop.isSafeToInline) {
              FunctionInjector injector =
                  new FunctionInjector(
                      compiler, compiler.getUniqueNameIdSupplier(), true, true, true);
              assignmentValue.detach();
              Node functionCall = IR.call(IR.empty(), assignmentValue);
              parent.replaceChild(n, functionCall);
              Reference reference =
                  new Reference(functionCall, t.getScope(), t.getModule(), InliningMode.BLOCK);
              injector.maybePrepareCall(reference);
              Node inlinedCall = injector.inline(reference, null, prop.setKey.getFirstChild());
              t.getCompiler().reportChangeToEnclosingScope(inlinedCall);
            }
          }
        }
      }
    }
  }
}
