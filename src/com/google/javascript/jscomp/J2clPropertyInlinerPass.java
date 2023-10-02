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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This pass targets J2CL output. It looks for static get and set methods defined within a class
 * that match the signature of J2CL static fields and inlines them at their call sites. This is done
 * for performance reasons since getter and setter accesses are slower than regular field accesses.
 *
 * <p>This will be done by looking at all property accesses and determining if they have a
 * corresponding get or set method on the property qualifiers definition. Some caveats:
 *
 * <ul>
 *   <li>Avoid inlining if the property is set using compound assignments.
 *   <li>Avoid inlining if the property is incremented using ++ or --
 * </ul>
 *
 * Since this pass only really works after the AggressiveInlineAliases pass has run, we have to look
 * for Object.defineProperties instead of es6 get and set nodes since es6 transpilation has already
 * occurred if the language out is ES5.
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
    GatherGetterAndSetterProperties.update(compiler, externs, root);
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
        checkArgument(
            setKey == null || (setKey.isStringKey() && setKey.getString().equals("set")), setKey);
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
          objectLit.getGrandparent().detach();
        }
      }
    }

    /** A J2CL property created with a ES6-style static getter and setter */
    private class J2clPropertyEs6 extends J2clProperty {
      public J2clPropertyEs6(Node getKey, Node setKey) {
        super(getKey, setKey);
        checkArgument(getKey.isGetterDef(), getKey);
        checkArgument(setKey == null || setKey.isSetterDef(), setKey);
      }

      @Override
      void remove() {
        Node classMembers = getKey.getParent();
        checkState(classMembers.isClassMembers(), classMembers);
        getKey.detach();
        NodeUtil.markFunctionsDeleted(getKey, compiler);
        if (setKey != null) {
          setKey.detach();
          NodeUtil.markFunctionsDeleted(setKey, compiler);
        }
        compiler.reportChangeToEnclosingScope(classMembers);
      }
    }

    /**
     * <li> We match J2CL property getters by looking for the following signature:
     * <pre>{@code
     * get: function() { return (ClassName$$0clinit(), ClassName$$0fieldName)};
     * // OR this if CollapseProperties decided not to collapse these names
     * get: function() { return (ClassName.$clinit(), ClassName.$fieldName)};
     * </pre>
     */
    private boolean matchesJ2clGetKeySignature(String className, Node getKey) {
      if (!getKey.getFirstChild().isFunction()) {
        return false;
      }
      Node getFunction = getKey.getFirstChild();
      if (!getFunction.hasChildren() || !getFunction.getLastChild().isBlock()) {
        return false;
      }
      // `{ return ClassName$$0clinit(), ClassName$prop; }`
      Node getBlock = getFunction.getLastChild();
      if (!getBlock.hasOneChild()) {
        return false;
      }
      // `return ClassName$$0clinit(), ClassName$prop;`
      Node returnStatement = getBlock.getFirstChild();
      if (!returnStatement.isReturn()) {
        return false;
      }
      // `ClassName$$0clinit(), ClassName$prop;`
      Node commaExpr = returnStatement.getOnlyChild();
      if (!commaExpr.isComma()) {
        return false;
      }
      // `ClassName$$0clinit()`
      Node callNode = commaExpr.getFirstChild();
      if (!(callNode.isCall() && callNode.hasOneChild())) {
        return false;
      }
      // `ClassName$$0clinit`
      Node clinitQname = callNode.getOnlyChild();
      if (!clinitQname.isQualifiedName()) {
        return false;
      }
      String clinitQnameString = clinitQname.getQualifiedName();
      if (!looksLikeAJ2clGeneratedClassClinitName(className, clinitQnameString)) {
        return false;
      }
      // `ClassName$prop`
      Node propReference = commaExpr.getSecondChild();
      if (!propReference.isQualifiedName()) {
        return false;
      }
      String propReferenceString = propReference.getQualifiedName();

      if (!looksLikeAJ2clGeneratedStaticPropertyName(className, propReferenceString)) {
        return false;
      }
      return true;
    }

    /**
     * <li> We match J2CL property getters  by looking for the following signature:
     * <pre>{@code
     * set: function(value) { (ClassName$$0clinit(), ClassName$$0fieldName = value)};
     * // OR this if CollapseProperties decided not to collapse the names.
     * set: function(value) { (ClassName.$clinit(), ClassName.$fieldNaNme = value)};
     * </pre>
     */
    private boolean matchesJ2clSetKeySignature(String className, Node setKey) {
      if (!setKey.getFirstChild().isFunction()) {
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
      // `{ ClassName$$0clinit(), ClassName$$0staticProp = initialValue; }`
      Node setBlock = setFunction.getLastChild();
      if (!setBlock.hasOneChild()) {
        return false;
      }

      // `ClassName$$0clinit(), ClassName$$0staticProp = initialValue;`
      Node exprResult = setBlock.getOnlyChild();
      if (!exprResult.isExprResult()) {
        return false;
      }
      // `ClassName$$0clinit(), ClassName$$0staticProp = initialValue`
      Node commaExpr = exprResult.getOnlyChild();
      if (!commaExpr.isComma()) {
        return false;
      }
      // `SomeClassName$$0clinit()`
      Node clinitCall = commaExpr.getFirstChild();
      if (!(clinitCall.isCall() && clinitCall.hasOneChild())) {
        return false;
      }
      // `SomeClassName$$0clinit`
      Node clinitQname = clinitCall.getOnlyChild();
      if (!clinitQname.isQualifiedName()) {
        return false;
      }
      String clinitQnameString = clinitQname.getQualifiedName();
      if (!looksLikeAJ2clGeneratedClassClinitName(className, clinitQnameString)) {
        return false;
      }
      // `ClassName$$0staticProp = initialValue`
      // Rewriting by CollapseProperties may have changed the LHS
      // from a GETPROP to a NAME.
      Node assign = commaExpr.getSecondChild();
      if (!assign.isAssign()) {
        return false;
      }
      Node lhsQname = assign.getFirstChild();
      if (!lhsQname.isQualifiedName()) {
        return false;
      }
      String lhsQnameString = lhsQname.getQualifiedName();
      if (!looksLikeAJ2clGeneratedStaticPropertyName(className, lhsQnameString)) {
        return false;
      }
      return true;
    }

    boolean looksLikeAJ2clGeneratedClassClinitName(String className, String candidate) {
      if (!candidate.startsWith(className)) {
        return false;
      }
      final String candidateAfterClassName = candidate.substring(className.length());
      return candidateAfterClassName.equals("$$0clinit") // collapsed by CollapseProperties
          || candidateAfterClassName.equals(".$clinit"); // not collapsed
    }

    boolean looksLikeAJ2clGeneratedStaticPropertyName(String className, String candidate) {
      if (!candidate.startsWith(className)) {
        return false;
      }
      final String candidateAfterClassName = candidate.substring(className.length());
      return candidateAfterClassName.startsWith("$$0") // collapsed by CollapseProperties
          || candidateAfterClassName.startsWith(".$"); // not collapsed
    }

    /**
     * This class traverses the ast and gathers get and set methods contained in
     * Object.defineProperties nodes.
     */
    private class GatherJ2CLClassGetterSetters extends AbstractPostOrderCallback {
      private final Map<String, J2clProperty> j2clPropertiesByName = new LinkedHashMap<>();

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
          Node propertyLiteral = p.getFirstChild();
          Node getKey = null;
          Node setKey = null;
          boolean hasSetter = false;
          for (Node innerKey = propertyLiteral.getFirstChild();
              innerKey != null;
              innerKey = innerKey.getNext()) {
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
                hasSetter = true;
                if (matchesJ2clSetKeySignature(classNameString, innerKey)) {
                  setKey = innerKey;
                }
                break;
              default: // fall out
            }
          }
          if (getKey != null && (!hasSetter || setKey != null)) {
            j2clPropertiesByName.put(
                classNameString + "." + name, new J2clPropertyEs5(getKey, setKey));
          }
        }
      }

      void visitClass(Node classNode) {
        String className = NodeUtil.getName(classNode);
        Node classMembers = NodeUtil.getClassMembers(classNode);

        Map<String, Node> setterDefByName = new LinkedHashMap<>();
        Map<String, Node> getterDefByName = new LinkedHashMap<>();

        // Collect static setters and getters.
        for (Node memberFunction = classMembers.getFirstChild();
            memberFunction != null;
            memberFunction = memberFunction.getNext()) {
          if (!memberFunction.isStaticMember()) {
            // The only getters and setters we care about are static.
            continue;
          }
          switch (memberFunction.getToken()) {
            case GETTER_DEF:
              getterDefByName.put(memberFunction.getString(), memberFunction);
              break;
            case SETTER_DEF:
              setterDefByName.put(memberFunction.getString(), memberFunction);
              break;
            default: // fall out
          }
        }

        for (String propertyName : getterDefByName.keySet()) {
          Node getterDef = getterDefByName.get(propertyName);
          Node setterDef = setterDefByName.get(propertyName);
          if (matchesJ2clGetKeySignature(className, getterDef)
              && (setterDef == null || matchesJ2clSetKeySignature(className, setterDef))) {
            // This is a J2cl static property.
            j2clPropertiesByName.put(
                className + "." + propertyName, new J2clPropertyEs6(getterDef, setterDef));
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

    /** Look for accesses of j2cl properties and assignments to j2cl properties. */
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
            // This is a stub declaration for the type checker. See:
            // ConcretizeStaticInheritanceForInlining
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
                new FunctionInjector.Builder(compiler)
                    .assumeStrictThis(true)
                    .assumeMinimumCapture(true)
                    .build();
            Node functionCall = IR.call(IR.name("inlined_j2cl_getter"));
            n.replaceWith(functionCall);
            Reference reference =
                new Reference(functionCall, t.getScope(), t.getChunk(), InliningMode.DIRECT);
            Node inlinedCall = injector.inline(reference, null, prop.getKey.getFirstChild());
            t.getCompiler().reportChangeToEnclosingScope(inlinedCall);
          }
        }

        if (n.isAssign()) {
          Node assignmentTarget = n.getFirstChild();
          Node assignmentValue = n.getLastChild();
          if (assignmentTarget.isGetProp()) {
            String accessName = assignmentTarget.getQualifiedName();
            J2clProperty prop = propertiesByName.get(accessName);
            if (prop != null && prop.setKey != null && prop.isSafeToInline) {
              FunctionInjector injector =
                  new FunctionInjector.Builder(compiler)
                      .assumeStrictThis(true)
                      .assumeMinimumCapture(true)
                      .build();
              assignmentValue.detach();
              Node functionCall = IR.call(IR.name("inlined_j2cl_setter"), assignmentValue);
              n.replaceWith(functionCall);
              Reference reference =
                  new Reference(functionCall, t.getScope(), t.getChunk(), InliningMode.BLOCK);
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
