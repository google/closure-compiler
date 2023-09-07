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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites static inheritance to explicitly copy inherited properties from superclass to subclass
 * so that the optimizer knows the subclass has those properties.
 *
 * <p>For example, the main transpilation passes will convert this ES6 code:
 *
 * <pre>
 *   class Foo { static f() {} }
 *   class Bar extends Foo {}
 * </pre>
 *
 * to this ES3 code:
 *
 * <pre>
 *   function Foo() {}
 *   Foo.f = function() {};
 *   function Bar() {}
 *   $jscomp.inherits(Bar, Foo);
 * </pre>
 *
 * and then this class will convert that to
 *
 * <pre>
 *   function Foo() {}
 *   Foo.f = function() {};
 *   function Bar() {}
 *   $jscomp.inherits(Bar, Foo);
 *   Bar.f = Foo.f;
 * </pre>
 *
 * Additionally, there are getter and setter fields which are transpiled from:
 *
 * <pre>
 *   class Foo { static get prop() { return 1; } }
 *   class Bar extends Foo {}
 * </pre>
 *
 * to:
 *
 * <pre>
 *   var Foo = function() {};
 *   Foo.prop; // stub declaration so that the optimizer knows about prop
 *   Object.defineProperties(Foo, {prop:{get:function() { return 1; }}});
 *
 *   var Bar = function() {};
 *   $jscomp.inherits(Bar, Foo);
 * </pre>
 *
 * The stub declaration of Foo.prop needs to be duplicated for Bar so that the optimizer knows that
 * Bar also has this property. (ES5 classes don't have class-side inheritance).
 *
 * <pre>
 *   var Bar = function() {};
 *   Bar.prop;
 *   $jscomp.inherits(Bar, Foo);
 * </pre>
 *
 * <p>In order to gather the stub declarations, this pass gathers all GETPROPs on a class. In order
 * to determine which of these are the stub declarations it filters them based on names discovered
 * in Object.defineProperties. Unfortunately, we cannot simply gather the defined properties because
 * they don't have the JSDoc, which may include optimization-relevant annotations like @nocollapse.
 *
 * <p>TODO(tdeegan): In the future the JSDoc for getter/setter properties could be stored in the
 * defineProperties functions. It would reduce the complexity of this pass significantly.
 *
 * <p>NOTE: currently this pass only exists to prevent property collapsing from breaking some simple
 * class-side inheritance cases when transpiling.
 */
public final class ConcretizeStaticInheritanceForInlining implements CompilerPass {

  static final DiagnosticType DUPLICATE_CLASS =
      DiagnosticType.error("DUPLICATE_CLASS", "Multiple classes cannot share the same name: {0}");

  private final Set<String> duplicateClassNames = new LinkedHashSet<>();

  // Property names that may cause issues if they are concretized.
  private static final ImmutableSet<String> BANNED_PROP_NAMES =
      ImmutableSet.of("prototype", "getInstance");

  private static class JavascriptClass {
    // All static members to the class including get set properties.
    private final Set<Node> staticMembers = new LinkedHashSet<>();
    // Keep updated the set of static member names to avoid O(n^2) searches.
    private final Set<String> staticMemberNames = new LinkedHashSet<>();
    // Collect all the static field accesses to the class.
    private final Set<Node> staticFieldAccess = new LinkedHashSet<>();
    // Collect all get set properties as defined by Object.defineProperties(...)
    private final Set<String> definedProperties = new LinkedHashSet<>();

    void addStaticMember(Node node) {
      staticMembers.add(node);
      staticMemberNames.add(node.getFirstChild().getString());
    }
  }

  private final AbstractCompiler compiler;

  private final LinkedHashMap<String, JavascriptClass> classByAlias = new LinkedHashMap<>();

  public ConcretizeStaticInheritanceForInlining(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    FindStaticMembers findStaticMembers = new FindStaticMembers();
    // Since this is an optimization pass, running on externs would be unnecessary
    NodeTraversal.traverse(compiler, root, findStaticMembers);
    processInherits(findStaticMembers);
  }

  private void processInherits(FindStaticMembers findStaticMembers) {
    for (Node inheritsCall : findStaticMembers.inheritsCalls) {
      Node superclassNameNode = inheritsCall.getLastChild();
      String superclassQname = superclassNameNode.getQualifiedName();
      Node subclassNameNode = superclassNameNode.getPrevious();
      String subclassQname = subclassNameNode.getQualifiedName();
      JavascriptClass superClass = classByAlias.get(superclassQname);
      JavascriptClass subClass = classByAlias.get(subclassQname);
      if (duplicateClassNames.contains(superclassQname)) {
        compiler.report(JSError.make(inheritsCall, DUPLICATE_CLASS, superclassQname));
        return;
      }
      if (superClass == null || subClass == null) {
        continue;
      }
      copyStaticMembers(superClass, subClass, inheritsCall, findStaticMembers);
      copyDeclarations(superClass, subClass, inheritsCall);
    }
  }

  /**
   * When static get/set properties are transpiled, in addition to the Object.defineProperties, they
   * are declared with stub GETPROP declarations so that the optimizer understands that these
   * properties exist on the class. When subclassing, we also need to declare these properties on
   * the subclass so that the optimizer knows they exist.
   */
  private void copyDeclarations(
      JavascriptClass superClass, JavascriptClass subClass, Node inheritsCall) {
    for (Node staticGetProp : superClass.staticFieldAccess) {
      checkState(staticGetProp.isGetProp());
      String memberName = staticGetProp.getString();
      // We only copy declarations that have corresponding Object.defineProperties
      if (!superClass.definedProperties.contains(memberName)) {
        continue;
      }
      // If the subclass already declares the property no need to redeclare it.
      if (isOverriden(subClass, memberName)) {
        continue;
      }
      Node subclassNameNode = inheritsCall.getSecondChild();
      Node getprop = IR.getprop(subclassNameNode.cloneTree(), memberName);

      getprop.setJSDocInfo(null);
      Node declaration = IR.exprResult(getprop);
      declaration.srcrefTreeIfMissing(inheritsCall);
      Node parent = inheritsCall.getParent();
      declaration.insertBefore(parent);
      compiler.reportChangeToEnclosingScope(parent);

      // Copy over field access so that subclasses of this subclass can also make the declarations
      if (!subClass.definedProperties.contains(memberName)) {
        subClass.staticFieldAccess.add(getprop);
        subClass.definedProperties.add(memberName);
      }
    }
  }

  private void copyStaticMembers(
      JavascriptClass superClass,
      JavascriptClass subClass,
      Node inheritsCall,
      FindStaticMembers findStaticMembers) {
    for (Node staticMember : superClass.staticMembers) {
      checkState(staticMember.isAssign(), staticMember);
      String memberName = staticMember.getFirstChild().getString();
      if (superClass.definedProperties.contains(memberName)) {
        continue;
      }
      if (isOverriden(subClass, memberName)) {
        continue;
      }
      if (findStaticMembers.isBefore(inheritsCall, staticMember)) {
        // Don't copy members that are defined after the $jscomp.inherits call,
        // since they will not work correctly in IE<11, where static inheritance
        // is done by copying, rather than prototype manipulation.
        continue;
      }

      JSDocInfo.Builder info = JSDocInfo.Builder.maybeCopyFrom(staticMember.getJSDocInfo());
      Node function = staticMember.getLastChild();
      Node sourceInfoNode = function;
      if (function.isFunction()) {
        sourceInfoNode = function.getFirstChild();
      }

      Node subclassNameNode = inheritsCall.getSecondChild();
      Node superclassNameNode = subclassNameNode.getNext();
      Node assign =
          IR.assign(
              IR.getprop(subclassNameNode.cloneTree(), memberName),
              IR.getprop(superclassNameNode.cloneTree(), memberName));
      assign.setJSDocInfo(info.build());
      Node exprResult = IR.exprResult(assign);
      exprResult.srcrefTreeIfMissing(sourceInfoNode);
      Node inheritsExpressionResult = inheritsCall.getParent();
      exprResult.insertAfter(inheritsExpressionResult);
      compiler.reportChangeToEnclosingScope(inheritsExpressionResult);

      // Add the static member to the subclass so that subclasses also copy this member.
      subClass.addStaticMember(assign);
    }
  }

  private boolean isOverriden(JavascriptClass subClass, String memberName) {
    if (subClass.staticMemberNames.contains(memberName)) {
      // This subclass overrides the static method, so there is no need to copy the
      // method from the base class.
      return true;
    }
    if (subClass.definedProperties.contains(memberName)) {
      return true;
    }
    return false;
  }

  private boolean isReferenceToClass(NodeTraversal t, Node n) {
    String className = n.getQualifiedName();
    if (!classByAlias.containsKey(className)) {
      return false;
    }

    if (!n.isName()) {
      return true;
    }

    Var var = t.getScope().getVar(className);
    return var == null || !var.isLocal();
  }

  private class FindStaticMembers extends AbstractPostOrderCallback {
    final List<Node> inheritsCalls = new ArrayList<>();
    // Store the order we find class definitions and static fields.  Copied statics must occur
    // after both the namespace and the copied property are defined.
    final Map<Node, Integer> nodeOrder = new LinkedHashMap<>();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case CALL:
          if (n.getFirstChild().matchesQualifiedName(Es6RewriteClass.INHERITS)) {
            inheritsCalls.add(n);
            nodeOrder.put(n, nodeOrder.size());
          }
          if (NodeUtil.isObjectDefinePropertiesDefinition(n)) {
            visitDefinedPropertiesCall(t, n);
          }
          break;
        case CONST:
        case LET:
        case VAR:
          visitVariableDeclaration(n);
          break;
        case ASSIGN:
          visitAssign(t, n);
          break;
        case GETPROP:
          if (parent.isExprResult()) {
            visitGetProp(t, n);
          }
          break;
        case FUNCTION:
          visitFunctionClassDef(n);
          break;
        default:
          break;
      }
    }

    private void visitDefinedPropertiesCall(NodeTraversal t, Node definePropertiesCall) {
      Node object = definePropertiesCall.getSecondChild();
      if (isReferenceToClass(t, object)) {
        String className = object.getQualifiedName();
        JavascriptClass c = classByAlias.get(className);
        for (Node prop : NodeUtil.getObjectDefinedPropertiesKeys(definePropertiesCall)) {
          c.definedProperties.add(prop.getString());
        }
      }
    }

    private void visitFunctionClassDef(Node n) {
      JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(n);
      if (classInfo != null && classInfo.isConstructor()) {
        String name = NodeUtil.getName(n);
        if (name == null) {
          return;
        }
        if (classByAlias.containsKey(name)) {
          duplicateClassNames.add(name);
        } else {
          classByAlias.put(name, new JavascriptClass());
        }
      }
    }

    private void setAlias(String original, String alias) {
      Preconditions.checkNotNull(original, "original is null");
      Preconditions.checkNotNull(alias, "alias is null");
      checkArgument(classByAlias.containsKey(original));
      classByAlias.put(alias, classByAlias.get(original));
    }

    private void visitGetProp(NodeTraversal t, Node n) {
      Node classNode = n.getFirstChild();
      if (isReferenceToClass(t, classNode)) {
        classByAlias.get(classNode.getQualifiedName()).staticFieldAccess.add(n);
      }
    }

    private void visitAssign(NodeTraversal t, Node n) {
      // Alias for classes. We assume that the alias appears after the class declaration.
      String existingClassQname = n.getLastChild().getQualifiedName();
      if (existingClassQname != null && classByAlias.containsKey(existingClassQname)) {
        String alias = n.getFirstChild().getQualifiedName();
        if (alias != null) {
          setAlias(existingClassQname, alias);
        }
      } else if (n.getFirstChild().isGetProp()) {
        Node getProp = n.getFirstChild();
        Node classNode = getProp.getFirstChild();
        if (isReferenceToClass(t, classNode) && !BANNED_PROP_NAMES.contains(getProp.getString())) {
          classByAlias.get(classNode.getQualifiedName()).addStaticMember(n);
          nodeOrder.put(n, nodeOrder.size());
        }
      }
    }

    private void visitVariableDeclaration(Node n) {
      Node child = n.getFirstChild();
      if (!child.hasChildren()) {
        return;
      }
      String maybeOriginalName = child.getFirstChild().getQualifiedName();
      if (maybeOriginalName != null && classByAlias.containsKey(maybeOriginalName)) {
        String maybeAlias = child.getQualifiedName();
        if (maybeAlias != null) {
          setAlias(maybeOriginalName, maybeAlias);
        }
      }
    }

    boolean isBefore(Node earlier, Node later) {
      Integer earlierPosition = nodeOrder.get(earlier);
      Integer laterPosition = nodeOrder.get(later);
      return earlierPosition != null && laterPosition != null && earlierPosition < laterPosition;
    }
  }
}
