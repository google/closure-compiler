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

import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
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
    this.stripTypes = new HashSet<>(stripTypes);
    this.stripNameSuffixes = new HashSet<>(stripNameSuffixes);
    this.stripTypePrefixes = new HashSet<>(stripTypePrefixes);
    this.stripNamePrefixes = new HashSet<>(stripNamePrefixes);
    this.varsToRemove = new HashSet<>();
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
    // This pass may remove definitions of getter or setter properties
    GatherGettersAndSetterProperties.update(compiler, externs, root);
  }

  // -------------------------------------------------------------------------

  /**
   * A callback that strips debug code from a JavaScript parse tree.
   */
  private class Strip extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case VAR:
        case CONST:
        case LET:
          removeVarDeclarationsByNameOrRvalue(t, n, parent);
          break;

        case NAME:
          maybeRemoveReferenceToRemovedVariable(t, n, parent);
          break;

        case ASSIGN:
        case ASSIGN_BITOR:
        case ASSIGN_BITXOR:
        case ASSIGN_BITAND:
        case ASSIGN_LSH:
        case ASSIGN_RSH:
        case ASSIGN_URSH:
        case ASSIGN_ADD:
        case ASSIGN_SUB:
        case ASSIGN_MUL:
        case ASSIGN_DIV:
        case ASSIGN_MOD:
          maybeEliminateAssignmentByLvalueName(t, n, parent);
          break;

        case CALL:
        case NEW:
          maybeRemoveCall(t, n, parent);
          break;

        case OBJECTLIT:
          eliminateKeysWithStripNamesFromObjLit(t, n);
          break;

        case EXPR_RESULT:
          maybeEliminateExpressionByName(t, n, parent);
          break;

        case CLASS:
          maybeEliminateClassByNameOrExtends(t, n, parent);
          break;

        default:
          break;
      }
    }

    /**
     * Removes declarations of any variables whose names are strip names or whose whose r-values are
     * static method calls on strip types. Builds a set of removed variables so that all references
     * to them can be removed.
     *
     * @param t The traversal
     * @param n A VAR, CONST, or LET node
     * @param parent {@code n}'s parent
     */
    void removeVarDeclarationsByNameOrRvalue(NodeTraversal t, Node n, Node parent) {
      Node next = null;
      for (Node nameNode = n.getFirstChild(); nameNode != null; nameNode = next) {
        next = nameNode.getNext();
        if (nameNode.isDestructuringLhs()) {
          continue;
        }
        String name = nameNode.getString();
        if (isStripName(name)
            || isCallWhoseReturnValueShouldBeStripped(nameNode.getFirstChild())) {
          // Remove the NAME.
          Scope scope = t.getScope();
          varsToRemove.add(scope.getVar(name));
          n.removeChild(nameNode);
          NodeUtil.markFunctionsDeleted(nameNode, compiler);
        }
      }
      if (!n.hasChildren()) {
        // Must also remove the VAR.
        replaceWithEmpty(n, parent);
        t.reportCodeChange();
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
      switch (parent.getToken()) {
        case VAR:
        case CONST:
        case LET:
          // This is a variable declaration, not a reference.
          break;

        case GETPROP:
          // GETPROP
          //   NAME
          //   STRING (property name)
        case GETELEM:
          // GETELEM
          //   NAME
          //   NUMBER|STRING|NAME|...
          if (parent.getFirstChild() == n && isReferenceToRemovedVar(t, n)) {
            replaceHighestNestedCallWithNull(t, parent, parent.getParent());
          }
          break;

        case ASSIGN:
        case ASSIGN_BITOR:
        case ASSIGN_BITXOR:
        case ASSIGN_BITAND:
        case ASSIGN_LSH:
        case ASSIGN_RSH:
        case ASSIGN_URSH:
        case ASSIGN_ADD:
        case ASSIGN_SUB:
        case ASSIGN_MUL:
        case ASSIGN_DIV:
        case ASSIGN_MOD:
          if (isReferenceToRemovedVar(t, n)) {
            if (parent.getFirstChild() == n) {
              Node grandparent = parent.getParent();
              if (grandparent.isExprResult()) {
                // Remove the assignment.
                Node greatGrandparent = grandparent.getParent();
                replaceWithEmpty(grandparent, greatGrandparent);
                t.reportCodeChange();
              } else {
                // Substitute the r-value for the assignment.
                Node rvalue = n.getNext();
                parent.removeChild(rvalue);
                grandparent.replaceChild(parent, rvalue);
                t.reportCodeChange();
              }
            } else {
              // The var reference is the r-value. Replace it with null.
              replaceWithNull(n, parent);
              t.reportCodeChange();
            }
          }
          break;

        default:
          if (isReferenceToRemovedVar(t, n)) {
            replaceWithNull(n, parent);
            t.reportCodeChange();
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
    void replaceHighestNestedCallWithNull(NodeTraversal t, Node node, Node parent) {
      Node ancestor = parent;
      Node ancestorChild = node;
      Node ancestorParent;
      while (true) {
        ancestorParent = ancestor.getParent();

        if (ancestor.getFirstChild() != ancestorChild) {
          replaceWithNull(ancestorChild, ancestor);
          break;
        }
        if (ancestor.isExprResult()) {
          // Remove the entire expression statement.
          replaceWithEmpty(ancestor, ancestorParent);
          break;
        }
        if (ancestor.isAssign()) {
          ancestorParent.replaceChild(ancestor, ancestor.getLastChild().detach());
          break;
        }
        if (!NodeUtil.isGet(ancestor)
            && !ancestor.isCall()) {
          replaceWithNull(ancestorChild, ancestor);
          break;
        }

        // Is not executed on the last iteration so can't be used for change reporting.
        ancestorChild = ancestor;
        ancestor = ancestorParent;
      }
      t.reportCodeChange();
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
          Node grandparent = parent.getParent();
          replaceWithEmpty(parent, grandparent);
          compiler.reportChangeToEnclosingScope(grandparent);
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
          Node grandparent = parent.getParent();
          replaceWithEmpty(parent, grandparent);
          compiler.reportChangeToEnclosingScope(grandparent);
        } else {
          replaceWithEmpty(n, parent);
          compiler.reportChangeToEnclosingScope(parent);
        }
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
        replaceHighestNestedCallWithNull(t, n, parent);
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
        switch (key.getToken()) {
          case GETTER_DEF:
          case SETTER_DEF:
          case STRING_KEY:
          case MEMBER_FUNCTION_DEF:
            if (isStripName(key.getString())) {
              Node next = key.getNext();
              n.removeChild(key);
              NodeUtil.markFunctionsDeleted(key, compiler);
              key = next;
              compiler.reportChangeToEnclosingScope(n);
              break;
            }
            // fall through
          default:
            key = key.getNext();
        }
      }
    }

    /**
     * Removes a class definition if the name is a strip type. Warns if a non-strippable class
     * is extending a strippable type.
     */
    void maybeEliminateClassByNameOrExtends(NodeTraversal t, Node classNode, Node parent) {
      Node nameNode = NodeUtil.getNameNode(classNode);
      String className = "<anonymous>";
      // Replace class with null if it is a strip type
      if (nameNode != null && nameNode.isQualifiedName()) {
        className = nameNode.getQualifiedName();
        if (qualifiedNameBeginsWithStripType(className)) {
          if (NodeUtil.isStatementParent(parent)) {
            replaceWithEmpty(classNode, parent);
          } else {
            replaceWithNull(classNode, parent);
          }
          t.reportCodeChange();
          return;
        }
      }

      // If the class is not a strip type, the superclass also cannot be a strip type
      Node superclassNode = classNode.getSecondChild();
      if (superclassNode != null && superclassNode.isQualifiedName()) {
        String superclassName = superclassNode.getQualifiedName();
        if (qualifiedNameBeginsWithStripType(superclassName)) {
          t.report(classNode, STRIP_TYPE_INHERIT_ERROR, className, superclassName);
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
        Node grandparent = parent.getParent();
        if (grandparent != null && NodeUtil.isNameDeclaration(grandparent)) {
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
      NodeUtil.markFunctionsDeleted(n, compiler);
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
      NodeUtil.markFunctionsDeleted(n, compiler);
    }
  }
}
