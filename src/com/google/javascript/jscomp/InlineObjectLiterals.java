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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Iterator;
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
 *
 */
class InlineObjectLiterals implements CompilerPass {

  public static final String VAR_PREFIX = "JSCompiler_object_inline_";

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
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(
        compiler, new InliningBehavior(), Predicates.<Var>alwaysTrue());
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
    private final Set<Var> staleVars = Sets.newHashSet();

    @Override
    public void afterExitScope(NodeTraversal t,
        Map<Var, ReferenceCollection> referenceMap) {
      for (Iterator<Var> it = t.getScope().getVars(); it.hasNext();) {
        Var v = it.next();

        if (isVarInlineForbidden(v)) {
            continue;
        }

        ReferenceCollection referenceInfo = referenceMap.get(v);

        if (isInlinableObject(referenceInfo.references)) {
            // Blacklist the object itself, as well as any other values
            // that it refers to, since they will have been moved around.
            staleVars.add(v);

            Reference declaration = referenceInfo.references.get(0);
            Reference init = referenceInfo.getInitializingReference();

            // Split up the object into individual variables if the object
            // is never referenced directly in full.
            splitObject(v, declaration, init, referenceInfo);
        }
      }
    }

    /**
     * If there are any variable references in the given node tree,
     * blacklist them to prevent the pass from trying to inline the
     * variable. Any code modifications will have potentially made the
     * ReferenceCollection invalid.
     */
    private void blacklistVarReferencesInTree(Node root, Scope scope) {
      for (Node c = root.getFirstChild(); c != null; c = c.getNext()) {
        blacklistVarReferencesInTree(c, scope);
      }

      if (root.getType() == Token.NAME) {
        staleVars.add(scope.getVar(root.getString()));
      }
    }

    /**
     * Whether the given variable is forbidden from being inlined.
     */
    private boolean isVarInlineForbidden(Var var) {
      // A variable may not be inlined if:
      // 1) The variable is exported,
      // 2) Don't inline the special RENAME_PROPERTY_FUNCTION_NAME
      // 3) A reference to the variable has been inlined. We're downstream
      //    of the mechanism that creates variable references, so we don't
      //    have a good way to update the reference. Just punt on it.
      return compiler.getCodingConvention().isExported(var.name)
          || RenameProperties.RENAME_PROPERTY_FUNCTION_NAME.equals(var.name)
          || staleVars.contains(var);
    }

    /**
     * Counts the number of direct (full) references to an object.
     * Specifically we check for references of the following type:
     * <pre>
     *   x;
     *   x.fn();
     * </pre>
     */
    private boolean isInlinableObject(List<Reference> refs) {
      boolean ret = false;
      for (Reference ref : refs) {
        Node name = ref.getNameNode();
        Node parent = ref.getParent();
        Node gramps = ref.getGrandparent();

        // Ignore indirect references, like x.y (except x.y(), since
        // the function referenced by y might reference 'this').
        //
        // TODO: If a function is called, figure out if it references
        // 'this', and if not, then inlining the object should be OK.
        if (parent.getType() == Token.GETPROP &&
            (gramps.getType() != Token.CALL ||
             gramps.getFirstChild() != parent) &&
            parent.getFirstChild().isEquivalentTo(name)) {
          continue;
        }

        // Full references mean that we can't inline the object.
        if (!ref.isLvalue() && !ref.isInitializingDeclaration()) {
          if (parent.getType() != Token.VAR) {
            // This is a full reference to the object, we can't inline.
            return false;
          }

          // var x; We can ignore safely.
          continue;
        }

        Node val = ref.getAssignedValue();
        if (val == null) {
          // Var with no assignment. Keep going.
          continue;
        }

        // We're looking for object literal assignments only.
        if (val.getType() != Token.OBJECTLIT) {
          return false;
        }

        // Make sure that the value is not self-refential. IOW,
        // disallow things like x = {b: x.a}.
        //
        // TODO: Only exclude unorderable self-referential
        // assignments. i.e. x = {a: x.b, b: x.a} is not orderable,
        // but x = {a: 1, b: x.a} is.
        //
        // Also, ES5 getters/setters aren't handled by this pass.
        for (Node child = val.getFirstChild(); child != null;
             child = child.getNext()) {
          if (child.getType() == Token.GET ||
              child.getType() == Token.SET) {
            // ES5 get/set not supported.
            return false;
          }
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

        // This is an assignment to an object literal. Make sure that
        // this isn't inside some giant GETPROP thing, e.g.
        // (x = {}).c = 5 (even though technically we could work out the
        // (x = {}).c case without the assignment, that's a sufficiently odd
        // case to not worry about it.
        Node p = parent;
        while ((p = p.getParent()) != null) {
          if (p.getType() == Token.GETPROP) {
            return false;
          }
        }

        // We have found an acceptable object literal assignment. As
        // long as there are no other assignments that mess things up,
        // we can inline.
        ret = true;
      }
      return ret;
    }

    /**
     * Computes a list of ever-referenced keys in the object being
     * inlined, and returns a mapping of key name -> generated
     * variable name.
     */
    private Map<String, String> computeVarList(
        Var v, ReferenceCollection referenceInfo) {
      Map<String, String> varmap = Maps.newHashMap();

      for (Reference ref : referenceInfo.references) {
        if (ref.isLvalue() || ref.isInitializingDeclaration()) {
          Node val = ref.getAssignedValue();
          if (val != null) {
            for (Node child = val.getFirstChild(); child != null;
                 child = child.getNext()) {
              String varname = child.getString();
              if (varmap.containsKey(varname)) {
                continue;
              }

              String var = VAR_PREFIX + varname + "_" +
                safeNameIdSupplier.get();
              varmap.put(varname, var);
            }
          }
        } else if (ref.getParent().getType() == Token.VAR) {
          // This is the var. There is no value.
        } else {
          Node getprop = ref.getParent();
          Preconditions.checkState(
            getprop.getType() == Token.GETPROP,
            "Unexpected reference type: " + Token.name(getprop.getType()));
          Preconditions.checkState(
            getprop.getFirstChild().getString().equals(v.getName()),
            "Unexpected variable name: " + getprop.getFirstChild().getString() +
            ", expecting: " + v.getName());

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
      Preconditions.checkState(object.getType() == Token.OBJECTLIT);
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
      List<Node> nodes = Lists.newArrayList();
      Node val = ref.getAssignedValue();
      blacklistVarReferencesInTree(val, v.scope);
      Preconditions.checkState(val.getType() == Token.OBJECTLIT);
      Set<String> all = Sets.newHashSet(varmap.keySet());
      for (Node key = val.getFirstChild(); key != null;
           key = key.getNext()) {
        String var = key.getString();
        Node value = key.removeFirstChild();
        // TODO(user): Copy type information.
        nodes.add(
          new Node(Token.ASSIGN,
                   Node.newString(Token.NAME, varmap.get(var)), value));
        all.remove(var);
      }

      // TODO(user): Better source information.
      for (String var : all) {
        nodes.add(
          new Node(Token.ASSIGN,
                   Node.newString(Token.NAME, varmap.get(var)),
                   NodeUtil.newUndefinedNode(null)));
      }

      // All assignments evaluate to true, so make sure that the
      // expr statement evaluates to true in case it matters.
      nodes.add(new Node(Token.TRUE));

      // Join these using COMMA.  A COMMA node must have 2 children, so we
      // create a tree. In the tree the first child be the COMMA to match
      // the parser, otherwise tree equality tests fail.
      nodes = Lists.reverse(nodes);
      Node replacement = new Node(Token.COMMA);
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

      Node replace = ref.getParent();
      replacement.copyInformationFromForTree(replace);

      if (replace.getType() == Token.VAR) {
        replace.getParent().replaceChild(
            replace, NodeUtil.newExpr(replacement));
      } else {
        replace.getParent().replaceChild(replace, replacement);
      }
    }

    /**
     * Splits up the object literal into individual variables, and
     * updates all uses.
     */
    private void splitObject(Var v, Reference declaration,
                             Reference init,
                             ReferenceCollection referenceInfo) {
      // First figure out the FULL set of possible keys, so that they
      // can all be properly set as necessary.
      Map<String, String> varmap = computeVarList(v, referenceInfo);

      Map<String, Node> initvals = Maps.newHashMap();
      // Figure out the top-level of the var assign node. If it's a plain
      // ASSIGN, then there's an EXPR_STATEMENT above it, if it's a
      // VAR then it should be directly replaced.
      Node vnode;
      boolean defined = referenceInfo.isWellDefined() &&
          init.getParent().getType() == Token.VAR;
      if (defined) {
        vnode = init.getParent();
        fillInitialValues(init, initvals);
      } else {
        // TODO(user): More test / rewrite this part.
        // Find the beginning of the function / script.
        vnode = v.getScope().getRootNode().getLastChild().getFirstChild();
      }

      for (Map.Entry<String, String> entry : varmap.entrySet()) {
        Node val = initvals.get(entry.getKey());
        Node varnode = NodeUtil.newVarNode(entry.getValue(), val);
        if (val == null) {
          // is this right?
          varnode.copyInformationFromForTree(vnode);
        }
        vnode.getParent().addChildBefore(varnode, vnode);
      }

      if (defined) {
        vnode.getParent().removeChild(vnode);
      }

      for (Reference ref : referenceInfo.references) {
        // The init/decl have already been converted.
        if (defined && ref == init) continue;

        if (ref.isLvalue()) {
          // Assignments have to be handled specially, since they
          // expand out into multiple assignments.
          replaceAssignmentExpression(v, ref, varmap);
        } else if (ref.getParent().getType() == Token.VAR) {
          // The old variable declaration. It didn't have a
          // value. Remove it entirely as it should now be unused.
          ref.getGrandparent().removeChild(ref.getParent());
        } else {
          // Make sure that the reference is a GETPROP as we expect it to be.
          Node getprop = ref.getParent();
          Preconditions.checkState(
            getprop.getType() == Token.GETPROP,
            "Unexpected reference type: " + Token.name(getprop.getType()));
          Preconditions.checkState(
            getprop.getFirstChild().getString().equals(v.getName()),
            "Unexpected variable name: " + getprop.getFirstChild().getString() +
            ", expecting: " + v.getName());

          // The key being looked up in the original map.
          String var = getprop.getChildAtIndex(1).getString();

          // If the variable hasn't already been declared, add an empty
          // declaration near all the other declarations.
          Preconditions.checkState(varmap.containsKey(var));

          // Replace the GETPROP node with a NAME.
          Node replacement = Node.newString(Token.NAME, varmap.get(var));
          replacement.copyInformationFrom(getprop);
          ref.getGrandparent().replaceChild(ref.getParent(), replacement);
        }
      }

      compiler.reportCodeChange();
    }
  }
}
