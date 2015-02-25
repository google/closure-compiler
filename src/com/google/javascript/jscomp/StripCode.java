/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

import javax.annotation.Nullable;

/**
 * A pass for stripping a list of provided JavaScript object types.
 *
 * The stripping strategy is as follows:
 *   - Provide: 1) a list of types that should be stripped, and 2) a list of
 *     suffixes of field/variable names that should be stripped.
 *   - Remove declarations of variables that are initialized using static
 *     methods of strip types (e.g. var x = goog.debug.Logger.getLogger(...);).
 *   - Remove all references to variables that are stripped.
 *   - Remove all object literal keys with strip names.
 *   - Remove all assignments to 1) field names that are strip names and
 *     2) qualified names that begin with strip types.
 *   - Remove all statements containing calls to static methods of strip types.
 *
 */
class StripCode implements CompilerPass {

  // TODO(user): Try eliminating the need for a list of strip names by instead
  // recording which field names are assigned to debug types in each JS input.
  private final AbstractCompiler compiler;
  private final Set<String> stripTypes;
  private final Set<String> stripNameSuffixes;
  private final Set<String> stripTypePrefixes;
  private final Set<String> stripNamePrefixes;
  private final Set<Var> varsToRemove;

  static final DiagnosticType STRIP_TYPE_INHERIT_ERROR = DiagnosticType.error(
      "JSC_STRIP_TYPE_INHERIT_ERROR",
      "Non-strip type {0} cannot inherit from strip type {1}");

  static final DiagnosticType STRIP_ASSIGNMENT_ERROR = DiagnosticType.error(
      "JSC_STRIP_ASSIGNMENT_ERROR",
      "Unable to strip assignment to {0}");

  /**
   * Creates an instance.
   *
   * @param compiler The compiler
   */
  StripCode(AbstractCompiler compiler,
            Set<String> stripTypes,
            Set<String> stripNameSuffixes,
            Set<String> stripTypePrefixes,
            Set<String> stripNamePrefixes) {

    this.compiler = compiler;
    this.stripTypes = Sets.newHashSet(stripTypes);
    this.stripNameSuffixes = Sets.newHashSet(stripNameSuffixes);
    this.stripTypePrefixes = Sets.newHashSet(stripTypePrefixes);
    this.stripNamePrefixes = Sets.newHashSet(stripNamePrefixes);
    this.varsToRemove = Sets.newHashSet();
  }

  /**
   * Enables stripping of goog.tweak functions.
   */
  public void enableTweakStripping() {
    stripTypes.add("goog.tweak");
  }

  @Override
  public void process(Node externs, Node root) {
    // Always strip types that defined on a type that is being stripped, otherwise the
    // resulting code will be invalid, so add "prefix" stripping that isn't a partial name.
    // TODO(johnlenz): I'm not sure what the original intent of "type prefix" stripping was.
    // Verify that we can always assume a complete namespace and simplify this logic.
    for (String type : stripTypes) {
      stripTypePrefixes.add(type + ".");
    }

    NodeTraversal.traverse(compiler, root, new Strip());
  }

  // -------------------------------------------------------------------------

  /**
   * A callback that strips debug code from a JavaScript parse tree.
   */
  private class Strip extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.VAR:
          removeVarDeclarationsByNameOrRvalue(t, n, parent);
          break;

        case Token.NAME:
          maybeRemoveReferenceToRemovedVariable(t, n, parent);
          break;

        case Token.ASSIGN:
        case Token.ASSIGN_BITOR:
        case Token.ASSIGN_BITXOR:
        case Token.ASSIGN_BITAND:
        case Token.ASSIGN_LSH:
        case Token.ASSIGN_RSH:
        case Token.ASSIGN_URSH:
        case Token.ASSIGN_ADD:
        case Token.ASSIGN_SUB:
        case Token.ASSIGN_MUL:
        case Token.ASSIGN_DIV:
        case Token.ASSIGN_MOD:
          maybeEliminateAssignmentByLvalueName(t, n, parent);
          break;

        case Token.CALL:
        case Token.NEW:
          maybeRemoveCall(t, n, parent);
          break;

        case Token.OBJECTLIT:
          eliminateKeysWithStripNamesFromObjLit(t, n);
          break;

        case Token.EXPR_RESULT:
          maybeEliminateExpressionByName(t, n, parent);
          break;
      }
    }

    /**
     * Removes declarations of any variables whose names are strip names or
     * whose whose r-values are static method calls on strip types. Builds a set
     * of removed variables so that all references to them can be removed.
     *
     * @param t The traversal
     * @param n A VAR node
     * @param parent {@code n}'s parent
     */
    void removeVarDeclarationsByNameOrRvalue(NodeTraversal t, Node n,
        Node parent) {
      for (Node nameNode = n.getFirstChild(); nameNode != null;
          nameNode = nameNode.getNext()) {
        String name = nameNode.getString();
        if (isStripName(name) ||
            isCallWhoseReturnValueShouldBeStripped(nameNode.getFirstChild())) {
          // Remove the NAME.
          Scope scope = t.getScope();
          varsToRemove.add(scope.getVar(name));
          n.removeChild(nameNode);
          compiler.reportCodeChange();
        }
      }
      if (!n.hasChildren()) {
        // Must also remove the VAR.
        replaceWithEmpty(n, parent);
        compiler.reportCodeChange();
      }
    }

    /**
     * Removes a reference if it is a reference to a removed variable.
     *
     * @param t The traversal
     * @param n A NAME node
     * @param parent {@code n}'s parent
     */
    void maybeRemoveReferenceToRemovedVariable(NodeTraversal t, Node n,
                                               Node parent) {
      switch (parent.getType()) {
        case Token.VAR:
          // This is a variable declaration, not a reference.
          break;

        case Token.GETPROP:
          // GETPROP
          //   NAME
          //   STRING (property name)
        case Token.GETELEM:
          // GETELEM
          //   NAME
          //   NUMBER|STRING|NAME|...
          if (parent.getFirstChild() == n && isReferenceToRemovedVar(t, n)) {
            replaceHighestNestedCallWithNull(parent, parent.getParent());
          }
          break;

        case Token.ASSIGN:
        case Token.ASSIGN_BITOR:
        case Token.ASSIGN_BITXOR:
        case Token.ASSIGN_BITAND:
        case Token.ASSIGN_LSH:
        case Token.ASSIGN_RSH:
        case Token.ASSIGN_URSH:
        case Token.ASSIGN_ADD:
        case Token.ASSIGN_SUB:
        case Token.ASSIGN_MUL:
        case Token.ASSIGN_DIV:
        case Token.ASSIGN_MOD:
          if (isReferenceToRemovedVar(t, n)) {
            if (parent.getFirstChild() == n) {
              Node gramps = parent.getParent();
              if (gramps.isExprResult()) {
                // Remove the assignment.
                Node greatGramps = gramps.getParent();
                replaceWithEmpty(gramps, greatGramps);
                compiler.reportCodeChange();
              } else {
                // Substitute the r-value for the assignment.
                Node rvalue = n.getNext();
                parent.removeChild(rvalue);
                gramps.replaceChild(parent, rvalue);
                compiler.reportCodeChange();
              }
            } else {
              // The var reference is the r-value. Replace it with null.
              replaceWithNull(n, parent);
              compiler.reportCodeChange();
            }
          }
          break;

        default:
          if (isReferenceToRemovedVar(t, n)) {
            replaceWithNull(n, parent);
            compiler.reportCodeChange();
          }
          break;
      }
    }

    /**
     * Use a while loop to get up out of any nested calls. For example,
     * if we have just detected that we need to remove the a.b() call
     * in a.b().c().d(), we'll have to remove all of the calls, and it
     * will take a few iterations through this loop to get up to d().
     */
    void replaceHighestNestedCallWithNull(Node node, Node parent) {
      Node ancestor = parent;
      Node ancestorChild = node;
      while (true) {
        if (ancestor.getFirstChild() != ancestorChild) {
          replaceWithNull(ancestorChild, ancestor);
          break;
        }
        if (ancestor.isExprResult()) {
          // Remove the entire expression statement.
          Node ancParent = ancestor.getParent();
          replaceWithEmpty(ancestor, ancParent);
          break;
        }
        if (ancestor.isAssign()) {
          Node ancParent = ancestor.getParent();
          ancParent.replaceChild(
              ancestor, ancestor.getLastChild().detachFromParent());
          break;
        }
        if (!NodeUtil.isGet(ancestor)
            && !ancestor.isCall()) {
          replaceWithNull(ancestorChild, ancestor);
          break;
        }
        ancestorChild = ancestor;
        ancestor = ancestor.getParent();
      }
      compiler.reportCodeChange();
    }

    /**
     * Eliminates an assignment if the l-value is:
     *  - A field name that's a strip name
     *  - A qualified name that begins with a strip type
     *
     * @param t The traversal
     * @param n An ASSIGN node
     * @param parent {@code n}'s parent
     */
    void maybeEliminateAssignmentByLvalueName(NodeTraversal t, Node n,
                                              Node parent) {
      // ASSIGN
      //   l-value
      //   r-value
      Node lvalue = n.getFirstChild();
      if (nameIncludesFieldNameToStrip(lvalue) ||
          qualifiedNameBeginsWithStripType(lvalue)) {

        // Limit to EXPR_RESULT because it is not
        // safe to eliminate assignment in complex expressions,
        // e.g. in ((x = 7) + 8)
        if (parent.isExprResult()) {
          Node gramps = parent.getParent();
          replaceWithEmpty(parent, gramps);
          compiler.reportCodeChange();
        } else {
          t.report(n, STRIP_ASSIGNMENT_ERROR, lvalue.getQualifiedName());
        }
      }
    }

    /**
     * Eliminates an expression if it refers to:
     *  - A field name that's a strip name
     *  - A qualified name that begins with a strip type
     * This gets rid of construct like:
     *  a.prototype.logger; (used instead of a.prototype.logger = null;)
     * This expression is not an assignment and so will not be caught by
     * maybeEliminateAssignmentByLvalueName.
     * @param t The traversal
     * @param n An EXPR_RESULT node
     * @param parent {@code n}'s parent
     */
    void maybeEliminateExpressionByName(NodeTraversal t, Node n,
                                        Node parent) {
      // EXPR_RESULT
      //   expression
      Node expression = n.getFirstChild();
      if (nameIncludesFieldNameToStrip(expression) ||
          qualifiedNameBeginsWithStripType(expression)) {
        if (parent.isExprResult()) {
          Node gramps = parent.getParent();
          replaceWithEmpty(parent, gramps);
        } else {
          replaceWithEmpty(n, parent);
        }
        compiler.reportCodeChange();
      }
    }

    /**
     * Removes a method call if {@link #isMethodOrCtorCallThatTriggersRemoval}
     * indicates that it should be removed.
     *
     * @param t The traversal
     * @param n A CALL node
     * @param parent {@code n}'s parent
     */
    void maybeRemoveCall(NodeTraversal t, Node n, Node parent) {
      // CALL/NEW
      //   function
      //   arguments
      if (isMethodOrCtorCallThatTriggersRemoval(t, n, parent)) {
        replaceHighestNestedCallWithNull(n, parent);
      }
    }

    /**
     * Eliminates any object literal keys in an object literal declaration that
     * have strip names.
     *
     * @param t The traversal
     * @param n An OBJLIT node
     */
    void eliminateKeysWithStripNamesFromObjLit(NodeTraversal t, Node n) {
      // OBJLIT
      //   key1
      //     value1
      //   key2
      //   ...
      Node key = n.getFirstChild();
      while (key != null) {
        if (isStripName(key.getString())) {
          Node next = key.getNext();
          n.removeChild(key);
          key = next;
          compiler.reportCodeChange();
        } else {
          key = key.getNext();
        }
      }
    }

    /**
     * Gets whether a node is a CALL node whose return value should be
     * stripped. A call's return value should be stripped if the function
     * getting called is a static method in a class that gets stripped. For
     * example, if "goog.debug.Logger" is a strip name, then this function
     * returns true for a call such as "goog.debug.Logger.getLogger(...)".  It
     * may also simply be a function that is getting stripped.  For example,
     * if "getLogger" is a strip name, but not "goog.debug.Logger", this will
     * still return true.
     *
     * @param n A node (typically a CALL node)
     * @return Whether the call's return value should be stripped
     */
    boolean isCallWhoseReturnValueShouldBeStripped(@Nullable Node n) {
      return n != null &&
          (n.isCall() ||
           n.isNew()) &&
          n.hasChildren() &&
          (qualifiedNameBeginsWithStripType(n.getFirstChild()) ||
              nameIncludesFieldNameToStrip(n.getFirstChild()));
    }

    /**
     * Gets whether a qualified name begins with a strip name. The names
     * "goog.debug", "goog.debug.Logger", and "goog.debug.Logger.Level" are
     * examples of strip names that would result in this function returning
     * true for a node representing the name "goog.debug.Logger.Level".
     *
     * @param n A node (typically a NAME or GETPROP node)
     * @return Whether the name begins with a strip name
     */
    boolean qualifiedNameBeginsWithStripType(Node n) {
      String name = n.getQualifiedName();
      return qualifiedNameBeginsWithStripType(name);
    }

    /**
     * Gets whether a qualified name begins with a strip name. The names
     * "goog.debug", "goog.debug.Logger", and "goog.debug.Logger.Level" are
     * examples of strip names that would result in this function returning
     * true for a node representing the name "goog.debug.Logger.Level".
     *
     * @param name A qualified class name
     * @return Whether the name begins with a strip name
     */
    boolean qualifiedNameBeginsWithStripType(String name) {
      if (name != null) {
        for (String type : stripTypes) {
          if (name.equals(type)) {
            return true;
          }
        }
        for (String type : stripTypePrefixes) {
          if (name.startsWith(type)) {
            return true;
          }
        }
      }
      return false;
    }

    /**
     * Determines whether a NAME node represents a reference to a variable that
     * has been removed.
     *
     * @param t The traversal
     * @param n A NAME node
     * @return Whether the variable was removed
     */
    boolean isReferenceToRemovedVar(NodeTraversal t, Node n) {
      String name = n.getString();
      Scope scope = t.getScope();
      Var var = scope.getVar(name);
      return varsToRemove.contains(var);
    }

    /**
     * Gets whether a CALL node triggers statement removal, based on the name
     * of the object whose method is being called, or the name of the method.
     * Checks whether the name begins with a strip type, includes a field name
     * that's a strip name, or belongs to the set of global class-defining
     * functions (e.g. goog.inherits).
     *
     * @param t The traversal
     * @param n A CALL node
     * @return Whether the node triggers statement removal
     */
    boolean isMethodOrCtorCallThatTriggersRemoval(
        NodeTraversal t, Node n, Node parent) {
      // CALL/NEW
      //   GETPROP (function)         <-- we're interested in this, the function
      //     GETPROP (callee object)  <-- or the object on which it is called
      //       ...
      //       STRING (field name)
      //     STRING (method name)
      //   ... (arguments)

      Node function = n.getFirstChild();
      if (function == null || !function.isGetProp()) {
        // We are only interested in calls on object references that are
        // properties. We don't need to eliminate method calls on variables
        // that are getting removed, since that's already done by the code
        // that removes all references to those variables.
        return false;
      }

      if (parent != null && parent.isName()) {
        Node gramps = parent.getParent();
        if (gramps != null && gramps.isVar()) {
          // The call's return value is being used to initialize a newly
          // declared variable. We should leave the call intact for now.
          // That way, when the traversal reaches the variable declaration,
          // we'll recognize that the variable and all references to it need
          // to be eliminated.
          return false;
        }
      }

      Node callee = function.getFirstChild();
      return nameIncludesFieldNameToStrip(callee) ||
          nameIncludesFieldNameToStrip(function) ||
          qualifiedNameBeginsWithStripType(function) ||
          actsOnStripType(t, n);
    }

    /**
     * @return Whether a name includes a field name that should be stripped.
     * E.g., "foo.stripMe.bar", "(foo.bar).stripMe", etc.
     */
    boolean nameIncludesFieldNameToStrip(@Nullable Node n) {
      if (n != null && n.isGetProp()) {
        Node propNode = n.getLastChild();
        return isStripName(propNode.getString())
            || nameIncludesFieldNameToStrip(n.getFirstChild());
      }
      return false;
    }

    /**
     * Determines whether the given node helps to define a
     * strip type. For example, goog.inherits(stripType, Object)
     * would be such a call.
     *
     * Also reports an error if a non-strip type inherits from a strip type.
     *
     * @param t The current traversal
     * @param callNode The CALL node
     */
    private boolean actsOnStripType(NodeTraversal t, Node callNode) {
      SubclassRelationship classes =
          compiler.getCodingConvention().getClassesDefinedByCall(callNode);
      if (classes != null) {
        // It's okay to strip a type that inherits from a non-stripped type
        // e.g. goog.inherits(goog.debug.Logger, Object)
        if (qualifiedNameBeginsWithStripType(classes.subclassName)) {
          return true;
        }

        // report an error if a non-strip type inherits from a
        // strip type.
        if (qualifiedNameBeginsWithStripType(classes.superclassName)) {
          t.report(callNode, STRIP_TYPE_INHERIT_ERROR,
                   classes.subclassName, classes.superclassName);
        }
      }

      return false;
    }

    /**
     * Gets whether a JavaScript identifier is the name of a variable or
     * property that should be stripped.
     *
     * @param name A JavaScript identifier
     * @return Whether {@code name} is a name that triggers removal
     */
    boolean isStripName(String name) {
      if (stripNameSuffixes.contains(name) ||
          stripNamePrefixes.contains(name)) {
        return true;
      }

      if (name.isEmpty() || Character.isUpperCase(name.charAt(0))) {
        return false;
      }

      String lcName = name.toLowerCase();
      for (String stripName : stripNamePrefixes) {
        if (lcName.startsWith(stripName.toLowerCase())) {
          return true;
        }
      }

      for (String stripName : stripNameSuffixes) {
        if (lcName.endsWith(stripName.toLowerCase())) {
          return true;
        }
      }

      return false;
    }

    /**
     * Replaces a node with a NULL node. This is useful where a value is
     * expected.
     *
     * @param n A node
     * @param parent {@code n}'s parent
     */
    void replaceWithNull(Node n, Node parent) {
      parent.replaceChild(n, IR.nullNode());
    }

    /**
     * Replaces a node with an EMPTY node. This is useful where a statement is
     * expected.
     *
     * @param n A node
     * @param parent {@code n}'s parent
     */
    void replaceWithEmpty(Node n, Node parent) {
      NodeUtil.removeChild(parent, n);
    }
  }
}
