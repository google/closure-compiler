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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This pass targets j2cl output. It looks for static get and set methods defined within a class
 * that follow the naming convention of j2cl static fields and inlines them at their
 * call sites.  This is done for performance reasons since getter and setter accesses are slower
 * than regular field accesses.
 *
 * <p>This will be done by looking at all property accesses and determining if they have a
 * corresponding get or set method on the property qualifiers definition.  Some caveats:
 * <ul>
 * <li>- We make the assumption that all names that match the j2cl static field naming convention:
 *     they are unique to their declared class.</li>
 * <li>- Avoid inlining if the property is set using compound assignments.</li>
 * <li>- Avoid inlining if the property is incremented using ++ or --</li>
 * </ul>
 *
 * Since the FunctionInliner class really only works after the CollapseProperties pass has run, we
 * have to look for Object.defineProperties instead of es6 get and set nodes since es6
 * transpilation has already occured.
 *
 */
public class J2clPropertyInlinerPass implements CompilerPass {
  final AbstractCompiler compiler;

  public J2clPropertyInlinerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    new StaticFieldGetterSetterInliner(root).run();
  }

  class StaticFieldGetterSetterInliner {
    Node root;
    private Pattern matchJ2CLStaticFieldName;

    StaticFieldGetterSetterInliner(Node root) {
      this.root = root;
      // \\A Marks start of input
      // \\z Marks end of input
      // [A-Za-z0-9$_]+ matches a sequence of upper or lower case character, number, $, or
      // underscore. This is essentially any valid java class or field name.
      String pattern = "\\Af_[A-Za-z0-9$_]+__([A-Za-z0-9$]+_)*[A-Za-z0-9$]+\\z";
      matchJ2CLStaticFieldName = Pattern.compile(pattern);
    }

    /**
     * Determines if the field name is a j2cl pattern which is:
     * f_<original field name>__<underscore delimited qualified class name>
     */
    @VisibleForTesting
    boolean matchesJ2clStaticFieldName(String fieldName) {
      Matcher m = matchJ2CLStaticFieldName.matcher(fieldName);
      while (m.find()) {
        return true;
      }
      return false;
    }

    private void run() {
      GatherJ2CLClassGetterSetters gatherer = new GatherJ2CLClassGetterSetters();
      NodeTraversal.traverseEs6(compiler, root, gatherer);
      Map<String, J2clProperty> result = gatherer.getResults();
      NodeTraversal.traverseEs6(compiler, root, new DetermineInlinableProperties(result));
      new InlinePropertiesPass(result).run();
    }

    private class J2clProperty {
      private Node getKey;
      private Node setKey;
      private boolean isSafeToInline;

      public J2clProperty(Node getKey, Node setKey) {
        this.getKey = getKey;
        this.setKey = setKey;
        this.isSafeToInline = true;
      }

      void remove() {
        Node objectLit = getKey.getParent().getParent().getParent();
        Preconditions.checkArgument(objectLit.isObjectLit());
        getKey.getParent().getParent().detachFromParent();
        compiler.reportCodeChange();
        if (objectLit.getChildCount() == 0) {
          // Remove the whole Object.defineProperties call if there are no properties left.
          objectLit.getParent().getParent().detachFromParent();
        }
      }
    }

    /**
     * This class traverses the ast and gathers get and set methods contained in
     * Object.defineProperties nodes.
     */
    private class GatherJ2CLClassGetterSetters extends AbstractPostOrderCallback {
      private Map<String, J2clProperty> propertiesByName = new HashMap<>();

      private Map<String, J2clProperty> getResults() {
        return propertiesByName;
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (!NodeUtil.isObjectDefinePropertiesDefinition(n)) {
          return;
        }
        for (Node p : NodeUtil.getObjectDefinedPropertiesKeys(n)) {
          String name = p.getString();
          if (!matchesJ2clStaticFieldName(name)) {
            continue;
          }
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
                getKey = innerKey;
                break;
              case "set":
                setKey = innerKey;
                break;
            }
          }
          Preconditions.checkArgument(
              getKey != null && setKey != null,
              "J2cl Properties should have both a getter and setter");
          propertiesByName.put(name, new J2clProperty(getKey, setKey));
        }
      }
    }

    private class DetermineInlinableProperties extends AbstractPostOrderCallback {
      private Map<String, J2clProperty> propertiesByName;

      DetermineInlinableProperties(Map<String, J2clProperty> allGetterSetters) {
        this.propertiesByName = allGetterSetters;
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (NodeUtil.isCompoundAssignementOp(n) || n.isInc() || n.isDec()) {
          Node assignmentTarget = n.getFirstChild();
          if (assignmentTarget.isGetProp()) {
            String accessName = assignmentTarget.getLastChild().getString();
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
      private Map<String, J2clProperty> propertiesByName;

      InlinePropertiesPass(Map<String, J2clProperty> allGetterSetters) {
        this.propertiesByName = allGetterSetters;
      }

      private void run() {
        NodeTraversal.traverseEs6(compiler, root, this);

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
          String accessName = n.getLastChild().getString();
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
            String accessName = assignmentTarget.getLastChild().getString();
            J2clProperty prop = propertiesByName.get(accessName);
            if (prop != null && prop.isSafeToInline) {
              FunctionInjector injector =
                  new FunctionInjector(
                      compiler, compiler.getUniqueNameIdSupplier(), true, true, true);
              assignmentValue.detachFromParent();
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
