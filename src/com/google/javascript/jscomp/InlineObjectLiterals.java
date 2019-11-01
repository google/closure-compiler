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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Using the infrastructure provided by {@link ReferenceCollectingCallback},
 * identify variables that are only ever assigned to object literals
 * and that are never used in entirety, and expand the objects into
 * individual variables.
 *
 * Based on the InlineVariables pass
 */
class InlineObjectLiterals implements CompilerPass {

  public static final String VAR_PREFIX = "JSCompiler_object_inline_";
  public static final String STRING_KEY_IDENTIFIER = "string_key";

  private final AbstractCompiler compiler;

  private final Supplier<String> safeNameIdSupplier;

  InlineObjectLiterals(
      AbstractCompiler compiler,
      Supplier<String> safeNameIdSupplier) {
    this.compiler = compiler;
    this.safeNameIdSupplier = safeNameIdSupplier;
  }

  @Override
  public void process(Node externs, Node root) {
    ReferenceCollectingCallback callback =
        new ReferenceCollectingCallback(
            compiler, new InliningBehavior(), new SyntacticScopeCreator(compiler));
    callback.process(externs, root);
  }

  /**
   * Builds up information about nodes in each scope. When exiting the
   * scope, inspects all variables in that scope, and inlines any
   * that we can.
   */
  private class InliningBehavior implements Behavior {

    /**
     * A list of variables that should not be inlined, because their
     * reference information is out of sync with the state of the AST.
     */
    private final Set<Var> staleVars = new HashSet<>();

    @Override
    public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {
      for (Var v : t.getScope().getVarIterable()) {
        if (isVarInlineForbidden(v)) {
          continue;
        }

        ReferenceCollection referenceInfo = referenceMap.getReferences(v);

        if (isInlinableObject(referenceInfo.references)) {
          // Blacklist the object itself, as well as any other values
          // that it refers to, since they will have been moved around.
          staleVars.add(v);

          Reference init = referenceInfo.getInitializingReference();

          // Split up the object into individual variables if the object
          // is never referenced directly in full.
          splitObject(v, init, referenceInfo);
        }
      }
    }

    /**
     * If there are any variable references in the given node tree,
     * blacklist them to prevent the pass from trying to inline the
     * variable. Any code modifications will have potentially made the
     * ReferenceCollection invalid.
     */
    private void blacklistVarReferencesInTree(Node root, final Scope scope) {
      NodeUtil.visitPreOrder(root, new NodeUtil.Visitor() {
        @Override
        public void visit(Node node) {
          if (node.isName()) {
            staleVars.add(scope.getVar(node.getString()));
          }
        }
      }, NodeUtil.MATCH_NOT_FUNCTION);
    }

    /**
     * Whether the given variable is forbidden from being inlined.
     */
    private boolean isVarInlineForbidden(Var var) {
      // A variable may not be inlined if:
      // 1) The variable is defined in the externs
      // 2) The variable is exported,
      // 3) Don't inline the special RENAME_PROPERTY_FUNCTION_NAME
      // 4) A reference to the variable has been inlined. We're downstream
      //    of the mechanism that creates variable references, so we don't
      //    have a good way to update the reference. Just punt on it.

      // Additionally, exclude global variables for now.

      return var.isGlobal()
          || var.isExtern()
          || compiler.getCodingConvention().isExported(var.name)
          || compiler
              .getCodingConvention()
              .isPropertyRenameFunction(var.nameNode.getQualifiedName())
          || staleVars.contains(var);
    }

    /**
     * Counts the number of direct (full) references to an object.
     * Specifically, we check for references of the following type:
     * <pre>
     *   x;
     *   x.fn();
     * </pre>
     */
    private boolean isInlinableObject(List<Reference> refs) {
      boolean ret = false;
      Set<String> validProperties = new HashSet<>();
      for (Reference ref : refs) {
        Node name = ref.getNode();
        Node parent = ref.getParent();
        Node grandparent = ref.getGrandparent();

        // Ignore most indirect references, like x.y (but not x.y(),
        // since the function referenced by y might reference 'this').
        //
        if (parent.isGetProp()) {
          checkState(parent.getFirstChild() == name);
          // A call target may be using the object as a 'this' value.
          if (grandparent.isCall()
              && grandparent.getFirstChild() == parent) {
            return false;
          }

          // Deleting a property has different semantics from deleting
          // a variable, so deleted properties should not be inlined.
          if (grandparent.isDelProp()) {
            return false;
          }

          // NOTE(nicksantos): This pass's object-splitting algorithm has
          // a blind spot. It assumes that if a property isn't defined on an
          // object, then the value is undefined. This is not true, because
          // Object.prototype can have arbitrary properties on it.
          //
          // We short-circuit this problem by bailing out if we see a reference
          // to a property that isn't defined on the object literal. This
          // isn't a perfect algorithm, but it should catch most cases.
          String propName = parent.getLastChild().getString();
          if (!validProperties.contains(propName)) {
            if (NodeUtil.isNameDeclOrSimpleAssignLhs(parent, grandparent)) {
              validProperties.add(propName);
            } else {
              return false;
            }
          }
          continue;
        }

        // Only rewrite VAR declarations or simple assignment statements
        if (!isVarOrAssignExprLhs(name)) {
           return false;
        }

        // Don't try to handle rewriting VAR/CONST/LET declarations inside for loops.
        // Currently, normalization moves var declarations out of for loop initializers anyway.
        // let/const are more difficult. Declaring each property outside the for loop puts them
        // in an incorrect scope. Declaring them in the loop would initialize them multiple times.
        if (NodeUtil.isNameDeclaration(parent) && NodeUtil.isAnyFor(grandparent)) {
          return false;
        }

        Node val = ref.getAssignedValue();
        if (val == null) {
          // A var with no assignment.
          continue;
        }

        // We're looking for object literal assignments only.
        if (!val.isObjectLit()) {
          return false;
        }

        // Make sure that the value is not self-referential. IOW,
        // disallow things like x = {b: x.a}.
        //
        // TODO(dimvar): Only exclude unorderable self-referential
        // assignments. i.e. x = {a: x.b, b: x.a} is not orderable,
        // but x = {a: 1, b: x.a} is.
        //
        // Also, ES5 getters/setters aren't handled by this pass.
        for (Node child = val.getFirstChild(); child != null;
             child = child.getNext()) {
          switch (child.getToken()) {
              // ES5 get/set not supported.
            case GETTER_DEF:
            case SETTER_DEF:
              // Don't inline computed property names
            case COMPUTED_PROP:
              // Spread can overwrite any preceding prop if there are matching keys.
              // TODO(b/126567617): Allow inlining props declared after the SPREAD.
            case OBJECT_SPREAD:
              return false;

            case MEMBER_FUNCTION_DEF:
            case STRING_KEY:
              break;

            default:
              throw new IllegalStateException(
                  "Unexpected child of OBJECTLIT: " + child.toStringTree());
          }

          validProperties.add(child.getString());

          Node childVal = child.getFirstChild();
          // Check if childVal is the parent of any of the passed in
          // references, as that is how self-referential assignments
          // will happen.
          for (Reference t : refs) {
            Node refNode = t.getParent();
            while (!NodeUtil.isStatementBlock(refNode)) {
              if (refNode == childVal) {
                // There's a self-referential assignment
                return false;
              }
              refNode = refNode.getParent();
            }
          }
        }


        // We have found an acceptable object literal assignment. As
        // long as there are no other assignments that mess things up,
        // we can inline.
        ret = true;
      }
      return ret;
    }

    private boolean isVarOrAssignExprLhs(Node n) {
      Node parent = n.getParent();
      return NodeUtil.isNameDeclaration(parent)
          || (parent.isAssign()
              && parent.getFirstChild() == n
              && parent.getParent().isExprResult());
    }

    /**
     * Computes a list of ever-referenced keys in the object being
     * inlined, and returns a mapping of key name -> generated
     * variable name.
     */
    private Map<String, String> computeVarList(
        ReferenceCollection referenceInfo) {
      Map<String, String> varmap = new LinkedHashMap<>();

      for (Reference ref : referenceInfo.references) {
        if (ref.isLvalue() || ref.isInitializingDeclaration()) {
          Node val = ref.getAssignedValue();
          if (val != null) {
            checkState(val.isObjectLit(), val);
            for (Node child = val.getFirstChild(); child != null;
                 child = child.getNext()) {
              String varname = child.getString();
              if (varmap.containsKey(varname)) {
                continue;
              }
              String var = varname;
              if (!TokenStream.isJSIdentifier(varname)) {
                var = STRING_KEY_IDENTIFIER;
              }
              var = VAR_PREFIX + var + "_" + safeNameIdSupplier.get();
              varmap.put(varname, var);
            }
          }
        } else if (NodeUtil.isNameDeclaration(ref.getParent())) {
          // This is the var. There is no value.
        } else {
          Node getprop = ref.getParent();
          checkState(getprop.isGetProp(), getprop);

          // The key being looked up in the original map.
          String varname = getprop.getLastChild().getString();
          if (varmap.containsKey(varname)) {
            continue;
          }

          String var = VAR_PREFIX + varname + "_" + safeNameIdSupplier.get();
          varmap.put(varname, var);
        }
      }

      return varmap;
    }

    /**
     * Populates a map of key names -> initial assigned values. The
     * object literal these are being pulled from is invalidated as
     * a result.
     */
    private void fillInitialValues(Reference init, Map<String, Node> initvals) {
      Node object = init.getAssignedValue();
      checkState(object.isObjectLit(), object);
      for (Node key = object.getFirstChild(); key != null;
           key = key.getNext()) {
        initvals.put(key.getString(), key.removeFirstChild());
      }
    }

    /**
     * Replaces an assignment like x = {...} with t1=a,t2=b,t3=c,true.
     * Note that the resulting expression will always evaluate to
     * true, as would the x = {...} expression.
     */
    private void replaceAssignmentExpression(Var v, Reference ref,
                                             Map<String, String> varmap) {
      // Compute all of the assignments necessary
      List<Node> nodes = new ArrayList<>();
      Node val = ref.getAssignedValue();
      blacklistVarReferencesInTree(val, v.scope);
      checkState(val.isObjectLit(), val);
      Set<String> all = new LinkedHashSet<>(varmap.keySet());
      for (Node key = val.getFirstChild(); key != null;
           key = key.getNext()) {
        String var = key.getString();
        Node value = key.removeFirstChild();
        // TODO(user): Copy type information.
        nodes.add(
            IR.assign(
                IR.name(varmap.get(var)),
                value));
        all.remove(var);
      }

      // TODO(user): Better source information.
      for (String var : all) {
        nodes.add(
            IR.assign(
                IR.name(varmap.get(var)),
                NodeUtil.newUndefinedNode(null)));
      }

      Node replacement;
      if (nodes.isEmpty()) {
        replacement = IR.trueNode();
      } else {
        // All assignments evaluate to true, so make sure that the
        // expr statement evaluates to true in case it matters.
        nodes.add(IR.trueNode());

        // Join these using COMMA.  A COMMA node must have 2 children, so we
        // create a tree. In the tree the first child be the COMMA to match
        // the parser, otherwise tree equality tests fail.
        nodes = Lists.reverse(nodes);
        replacement = new Node(Token.COMMA);
        Node cur = replacement;
        int i;
        for (i = 0; i < nodes.size() - 2; i++) {
          cur.addChildToFront(nodes.get(i));
          Node t = new Node(Token.COMMA);
          cur.addChildToFront(t);
          cur = t;
        }
        cur.addChildToFront(nodes.get(i));
        cur.addChildToFront(nodes.get(i + 1));
      }

      Node replace = ref.getParent();
      replacement.useSourceInfoIfMissingFromForTree(replace);

      if (NodeUtil.isNameDeclaration(replace)) {
        replace.replaceWith(NodeUtil.newExpr(replacement));
      } else {
        replace.replaceWith(replacement);
      }
    }

    /**
     * Splits up the object literal into individual variables, and
     * updates all uses.
     */
    private void splitObject(Var v, Reference init,
                             ReferenceCollection referenceInfo) {
      // First figure out the FULL set of possible keys, so that they
      // can all be properly set as necessary.
      Map<String, String> varmap = computeVarList(referenceInfo);

      Map<String, Node> initvals = new HashMap<>();
      // Figure out the top-level of the var assign node. If it's a plain
      // ASSIGN, then there's an EXPR_STATEMENT above it, if it's a
      // VAR then it should be directly replaced.
      Node vnode;
      boolean defined =
          referenceInfo.isWellDefined() && NodeUtil.isNameDeclaration(init.getParent());
      if (defined) {
        vnode = init.getParent();
        fillInitialValues(init, initvals);
      } else {
        // Find the beginning of the function body / script.
        vnode = v.getScope().getClosestHoistScope().getRootNode().getFirstChild();
      }
      checkState(NodeUtil.isStatement(vnode), vnode);

      for (Map.Entry<String, String> entry : varmap.entrySet()) {
        Node val = initvals.get(entry.getKey());
        Node newVarNode = NodeUtil.newVarNode(entry.getValue(), val);
        if (val == null) {
          // is this right?
          newVarNode.useSourceInfoIfMissingFromForTree(vnode);
        } else {
          blacklistVarReferencesInTree(val, v.scope);
        }
        vnode.getParent().addChildBefore(newVarNode, vnode);
        compiler.reportChangeToEnclosingScope(vnode);
      }

      if (defined) {
        compiler.reportChangeToEnclosingScope(vnode.getParent());
        vnode.detach();
      }

      for (Reference ref : referenceInfo.references) {

        // The init/decl have already been converted.
        if (defined && ref == init) {
          continue;
        }
        compiler.reportChangeToEnclosingScope(ref.getNode());

        if (ref.isLvalue()) {
          // Assignments have to be handled specially, since they
          // expand out into multiple assignments.
          replaceAssignmentExpression(v, ref, varmap);
        } else if (NodeUtil.isNameDeclaration(ref.getParent())) {
          // The old variable declaration. It didn't have a
          // value. Remove it entirely as it should now be unused.
          ref.getGrandparent().removeChild(ref.getParent());
        } else {
          // Make sure that the reference is a GETPROP as we expect it to be.
          Node getprop = ref.getParent();
          checkState(getprop.isGetProp(), getprop);

          // The key being looked up in the original map.
          String var = getprop.getSecondChild().getString();

          // If the variable hasn't already been declared, add an empty
          // declaration near all the other declarations.
          checkState(varmap.containsKey(var));

          // Replace the GETPROP node with a NAME.
          Node replacement = IR.name(varmap.get(var));
          replacement.useSourceInfoIfMissingFrom(getprop);
          ref.getGrandparent().replaceChild(ref.getParent(), replacement);
        }
      }
    }
  }
}
