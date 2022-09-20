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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.nullness.Nullable;

/**
 * A pass for stripping a list of provided JavaScript object types.
 *
 * <p>The stripping strategy is as follows:
 *
 * <ul>
 *   <li>Provide: 1) a list of types that should be stripped, and 2) a list of suffixes of
 *       field/variable names that should be stripped.
 *   <li>Remove declarations of variables that are initialized using static methods of strip types
 *       (e.g. var x = goog.debug.Logger.getLogger(...);).
 *   <li>Remove all references to variables that are stripped.
 *   <li>Remove all object literal keys with strip names.
 *   <li>Remove all assignments to 1) field names that are strip names and 2) qualified names that
 *       begin with strip types.
 *   <li>Remove all statements containing calls to static methods of strip types.
 * </ul>
 */
class StripCode implements CompilerPass {

  private final AbstractCompiler compiler;
  private final ImmutableSet<String> stripNameSuffixes;
  private final ImmutableSet<String> stripNamePrefixes;
  private final IdentityHashMap<String, String> varsToRemove = new IdentityHashMap<>();

  private final String[] stripTypesList;
  private final String[] stripTypePrefixesList;
  private final String[] stripNamePrefixesLowerCaseList;
  private final String[] stripNameSuffixesLowerCaseList;

  static final DiagnosticType STRIP_TYPE_INHERIT_ERROR =
      DiagnosticType.error(
          "JSC_STRIP_TYPE_INHERIT_ERROR", "Non-strip type {0} cannot inherit from strip type {1}");

  static final DiagnosticType STRIP_ASSIGNMENT_ERROR =
      DiagnosticType.error("JSC_STRIP_ASSIGNMENT_ERROR", "Unable to strip assignment to {0}");

  /**
   * Returns a stream containing `s` and, if `s` contains a ".", also all the possible collapsed
   * forms of `s`. (e.g. for `'a.b.c'` we generate `'a$b.c'` and `'a$b$c'`)
   *
   * <p>StripCode now runs after `CollapseProperties`, so it needs to look for both the original and
   * collapsed versions of qualified names.
   */
  private static Stream<String> toStreamWithCollapsedVersions(String s) {
    final ArrayList<String> possibleForms = new ArrayList<>();
    possibleForms.add(s);
    final char[] chars = s.toCharArray();
    for (int i = 0; i < chars.length; ++i) {
      if (chars[i] == '.') {
        chars[i] = '$';
        possibleForms.add(new String(chars));
      }
    }
    return possibleForms.stream();
  }

  /**
   * Creates an instance.
   *
   * @param compiler The compiler
   */
  StripCode(
      AbstractCompiler compiler,
      ImmutableSet<String> stripTypes,
      ImmutableSet<String> stripNameSuffixes,
      ImmutableSet<String> stripNamePrefixes,
      boolean enableTweakStripping) {

    this.compiler = compiler;

    this.stripNameSuffixes =
        stripNameSuffixes.stream()
            .flatMap(StripCode::toStreamWithCollapsedVersions)
            .collect(toImmutableSet());
    this.stripNamePrefixes =
        stripNamePrefixes.stream()
            .flatMap(StripCode::toStreamWithCollapsedVersions)
            .collect(toImmutableSet());

    Stream<String> stripTypesStream = stripTypes.stream();
    // Add "tweak" class stripping if requested
    if (enableTweakStripping) {
      stripTypesStream = Stream.concat(stripTypesStream, Stream.of("goog.tweak"));
    }
    ImmutableSet<String> stripTypesAdjusted =
        stripTypesStream
            .flatMap(StripCode::toStreamWithCollapsedVersions)
            .collect(toImmutableSet());

    // Iteration overhead was a high cost in this pass. Using a native array
    // is trivial and avoid those costs.
    this.stripTypesList = stripTypesAdjusted.toArray(new String[0]);

    // We want to strip types that are defined on a type that is being stripped, otherwise the
    // resulting code will be invalid. So, we'll also check for prefixes that indicate such child
    // names.
    // TODO(johnlenz): I'm not sure what the original intent of "type prefix" stripping was.
    // Verify that we can always assume a complete namespace and simplify this logic.
    this.stripTypePrefixesList =
        // look for both non-collapsed and collapsed child names
        stripTypesAdjusted.stream()
            .flatMap(s -> Stream.of(s + ".", s + "$"))
            .toArray(String[]::new);

    // Precalculate the lowercase versions of the string to avoid repeated
    // lowercase conversions.
    this.stripNamePrefixesLowerCaseList =
        this.stripNamePrefixes.stream().map(s -> s.toLowerCase(Locale.ROOT)).toArray(String[]::new);

    this.stripNameSuffixesLowerCaseList =
        this.stripNameSuffixes.stream().map(s -> s.toLowerCase(Locale.ROOT)).toArray(String[]::new);
    ;
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    try (LogFile decisionsLog =
        compiler.createOrReopenIndexedLog(this.getClass(), "decisions.log")) {
      decisionsLog.log(new StripCodeConfigRecord());
      decisionsLog.log("\n=== decisions ===\n");
      NodeTraversal.traverse(compiler, root, new Strip(decisionsLog));
    }
  }

  // -------------------------------------------------------------------------
  private final class StripCodeConfigRecord implements Supplier<String> {

    @Override
    public String get() {
      StringBuilder builder = new StringBuilder();
      builder.append("=== stripNameSuffixes ===\n");
      for (String stripNameSuffix : stripNameSuffixes) {
        builder.append(stripNameSuffix).append("\n");
      }
      builder.append("\n");
      builder.append("=== stripNamePrefixes ===\n");
      for (String stripNamePrefix : stripNamePrefixes) {
        builder.append(stripNamePrefix).append("\n");
      }
      builder.append("\n");
      builder.append("=== stripTypesList ===\n");
      for (String stripType : stripTypesList) {
        builder.append(stripType).append("\n");
      }
      builder.append("\n");
      builder.append("=== stripTypePrefixesList ===\n");
      for (String stripNamePrefix : stripTypePrefixesList) {
        builder.append(stripNamePrefix).append("\n");
      }
      builder.append("\n");
      return builder.toString();
    }
  }

  /** A callback that strips debug code from a JavaScript parse tree. */
  private class Strip implements NodeTraversal.Callback {

    private final LogFile decisionsLog;

    private Strip(LogFile decisionsLog) {
      this.decisionsLog = decisionsLog;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      // Here we check for cases where we're going to remove a large chunk of code,
      // and so should not traverse into it to avoid both wasting time and causing
      // problems with logic duplication.
      switch (n.getToken()) {
        case CALL:
        case NEW:
          // If we're removing the whole call / new
          if (isMethodOrCtorCallThatTriggersRemoval(t, n, parent)) {
            decisionsLog.log(() -> "removing function call");
            replaceHighestNestedCallWithNull(t, n, parent);
            return false;
          } else {
            return true;
          }
        default:
          return true;
      }
    }

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

        case OBJECTLIT:
          eliminateKeysWithStripNamesFromObjLit(t, n);
          break;

        case EXPR_RESULT:
          maybeEliminateExpressionByName(n);
          break;

        case CLASS:
          maybeEliminateClassByNameOrExtends(t, n, parent);
          break;

        default:
          break;
      }
    }

    /**
     * Removes declarations of any variables whose names are strip names or whose r-values are
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
        checkState(nameNode.isName(), nameNode);
        String name = nameNode.getString();
        // If this variable represents a collapsed property, it's the original property name we're
        // supposed to be matching against.
        int lastDollarSign = name.lastIndexOf('$');
        String possibleStripName = lastDollarSign != -1 ? name.substring(lastDollarSign + 1) : name;
        if (isStripName(possibleStripName)
            || qualifiedNameBeginsWithStripType(nameNode)
            || isCallWhoseReturnValueShouldBeStripped(nameNode.getFirstChild())) {
          // Remove the NAME.
          varsToRemove.put(name, name);
          if (name.contains("$")) {
            // We need to be careful with this code pattern that appears after
            // collapsing properties.
            // ```javascript
            // /** @constructor */
            // var a$b$C = function() {
            //   this.nonStrippedName = a$b$C$strippedByNameOrValue;
            // };
            // var a$b$C$strippedByNameOrValue = strippedFunction();
            // ```
            // Note that the declaration of `a$b$C$nonStrippedByNameOrValue` will be visited
            // **after** its first use, so it's too late to go back and remove the
            // reference (without restructuring this class quite a bit). Instead,
            // we'll preserve here the behavior you would get before collapsing and
            // just replace the rhs with `null`.
            decisionsLog.log(() -> name + ": initialize with null (" + possibleStripName + ")");
            if (nameNode.hasChildren()) {
              replaceWithNull(nameNode.getOnlyChild());
            } else {
              // `var my$name = null;` is a bit easier to optimize away than
              // `var my$name;`, because we can clearly see that it is initialized, so we have
              // a value to inline in later passes.
              nameNode.addChildToFront(IR.nullNode().srcref(nameNode));
            }
            t.reportCodeChange();
          } else {
            // Assume that the declaration comes before any reference.
            // We will remove the references when we see them later.
            decisionsLog.log(() -> name + ": removing declaration");
            nameNode.detach();
            NodeUtil.markFunctionsDeleted(nameNode, compiler);
          }
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
    void maybeRemoveReferenceToRemovedVariable(NodeTraversal t, Node n, Node parent) {
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
            decisionsLog.log(() -> n.getString() + ": removing getelem/getprop/call chain");
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
              decisionsLog.log(
                  () -> n.getQualifiedName() + ": removing assignment to stripped var");
              if (grandparent.isExprResult()) {
                // Remove the assignment.
                Node greatGrandparent = grandparent.getParent();
                replaceWithEmpty(grandparent, greatGrandparent);
                t.reportCodeChange();
              } else {
                // Substitute the r-value for the assignment.
                Node rvalue = n.getNext();
                rvalue.detach();
                parent.replaceWith(rvalue);
                t.reportCodeChange();
              }
            } else {
              // The var reference is the r-value. Replace it with null.
              decisionsLog.log(() -> n.getQualifiedName() + ": replacing rhs reference with null");
              replaceWithNull(n);
              t.reportCodeChange();
            }
          }
          break;

        case NEW:
        case CALL:
          if (!n.isFirstChildOf(parent) && isReferenceToRemovedVar(t, n)) {
            // NOTE: the callee is handled when we visit the CALL or NEW node
            decisionsLog.log(
                () -> n.getQualifiedName() + ": replacing parameter reference with null");
            replaceWithNull(n);
            t.reportCodeChange();
          }
          break;

        case COMMA:
          Node grandparent = parent.getParent();
          // The last child in a comma expression is its result, so we need to be careful replacing
          // it with null. We don't want to replace the entire comma expression with null because
          // there could be other elements in it with side-effects.
          boolean isLastChild = parent.getLastChild() == n;
          // The only parent where replacing with null is an issue is likely where the comma
          // expression is the first child (callee) of a CALL or NEW node.
          boolean parentIsCallee =
              (grandparent.isCall() || grandparent.isNew()) && parent.isFirstChildOf(grandparent);
          boolean isSafeToRemove = !isLastChild || !parentIsCallee;
          if (isSafeToRemove && isReferenceToRemovedVar(t, n)) {
            decisionsLog.log(
                () -> n.getQualifiedName() + ": replacing reference in comma expr with null");
            replaceWithNull(n);
            t.reportCodeChange();
          }
          break;

        default:
          if (isReferenceToRemovedVar(t, n)) {
            decisionsLog.log(() -> n.getQualifiedName() + ": replacing reference with null");
            replaceWithNull(n);
            t.reportCodeChange();
          }
          break;
      }
    }

    /**
     * Use a while loop to get up out of any nested calls. For example, if we have just detected
     * that we need to remove the a.b() call in a.b().c().d(), we'll have to remove all of the
     * calls, and it will take a few iterations through this loop to get up to d().
     */
    void replaceHighestNestedCallWithNull(NodeTraversal t, Node node, Node parent) {
      Node ancestor = parent;
      Node ancestorChild = node;
      Node ancestorParent;
      while (true) {
        ancestorParent = ancestor.getParent();
        if (ancestorParent == null) {
          return; // the call was already removed from the AST
        }

        if (ancestor.getFirstChild() != ancestorChild) {
          replaceWithNull(ancestorChild);
          break;
        }
        if (ancestor.isExprResult()) {
          // Remove the entire expression statement.
          replaceWithEmpty(ancestor, ancestorParent);
          break;
        }
        if (ancestor.isAssign()) {
          ancestor.replaceWith(ancestor.getLastChild().detach());
          break;
        }
        if (!NodeUtil.isNormalGet(ancestor) && !ancestor.isCall()) {
          replaceWithNull(ancestorChild);
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
     *
     * <ul>
     *   <li>A field name that's a strip name
     *   <li>A qualified name that begins with a strip type
     * </ul>
     *
     * @param t The traversal
     * @param n An ASSIGN node
     * @param parent {@code n}'s parent
     */
    void maybeEliminateAssignmentByLvalueName(NodeTraversal t, Node n, Node parent) {
      // ASSIGN
      //   l-value
      //   r-value
      Node lvalue = n.getFirstChild();
      if (nameIncludesFieldNameToStrip(lvalue) || qualifiedNameBeginsWithStripType(lvalue)) {

        // Limit to EXPR_RESULT because it is not
        // safe to eliminate assignment in complex expressions,
        // e.g. in ((x = 7) + 8)
        if (parent.isExprResult()) {
          decisionsLog.log(() -> lvalue.getString() + ": removing assignment statement");
          Node grandparent = parent.getParent();
          // the assignment may already have been removed when visiting either the lhs
          // or the rhs.
          if (grandparent != null) {
            replaceWithEmpty(parent, grandparent);
            compiler.reportChangeToEnclosingScope(grandparent);
          }
        } else {
          t.report(n, STRIP_ASSIGNMENT_ERROR, lvalue.getQualifiedName());
        }
      }
    }

    /**
     * Eliminates an expression if it refers to:
     *
     * <ul>
     *   <li>A field name that's a strip name
     *   <li>A qualified name that begins with a strip type
     * </ul>
     *
     * <p>This gets rid of construct like: a.prototype.logger; (used instead of a.prototype.logger =
     * null;) This expression is not an assignment and so will not be caught by
     * maybeEliminateAssignmentByLvalueName.
     *
     * @param n An EXPR_RESULT node
     */
    void maybeEliminateExpressionByName(Node n) {
      // EXPR_RESULT
      //   expression
      checkArgument(n.isExprResult(), n);
      final Node parent = n.getParent();
      if (parent == null) {
        // This EXPR_RESULT was already removed when one of its child nodes was visited.
        return;
      }
      Node expression = n.getFirstChild();
      if (nameIncludesFieldNameToStrip(expression)
          || qualifiedNameBeginsWithStripType(expression)) {
        decisionsLog.log(
            () -> expression.getString() + ": removing property declaration statement");
        replaceWithEmpty(n, parent);
        compiler.reportChangeToEnclosingScope(parent);
      }
    }

    /**
     * Eliminates any object literal keys in an object literal declaration that have strip names.
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
              key.detach();
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
     * Removes a class definition if the name is a strip type. Warns if a non-strippable class is
     * extending a strippable type.
     */
    void maybeEliminateClassByNameOrExtends(NodeTraversal t, Node classNode, Node parent) {
      Node nameNode = NodeUtil.getNameNode(classNode);
      final String className;
      // Replace class with null if it is a strip type
      if (nameNode != null && nameNode.isQualifiedName()) {
        className = nameNode.getQualifiedName();
        if (qualifiedNameBeginsWithStripType(className)) {
          decisionsLog.log(() -> className + ": removing class");
          if (NodeUtil.isStatementParent(parent)) {
            replaceWithEmpty(classNode, parent);
          } else {
            replaceWithNull(classNode);
          }
          t.reportCodeChange();
          return;
        }
      } else {
        className = "<anonymous>";
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
     * Gets whether a node is a CALL node whose return value should be stripped. A call's return
     * value should be stripped if the function getting called is a static method in a class that
     * gets stripped. For example, if "goog.debug.Logger" is a strip name, then this function
     * returns true for a call such as "goog.debug.Logger.getLogger(...)". It may also simply be a
     * function that is getting stripped. For example, if "getLogger" is a strip name, but not
     * "goog.debug.Logger", this will still return true.
     *
     * @param n A node (typically a CALL node)
     * @return Whether the call's return value should be stripped
     */
    boolean isCallWhoseReturnValueShouldBeStripped(@Nullable Node n) {
      if (n == null || (!n.isCall() && !n.isNew()) || !n.hasChildren()) {
        return false;
      }

      Node function = NodeUtil.getCallTargetResolvingIndirectCalls(n);
      return qualifiedNameBeginsWithStripType(function) || nameIncludesFieldNameToStrip(function);
    }

    /**
     * Gets whether a qualified name begins with a strip name. The names "goog.debug",
     * "goog.debug.Logger", and "goog.debug.Logger.Level" are examples of strip names that would
     * result in this function returning true for a node representing the name
     * "goog.debug.Logger.Level".
     *
     * @param n A node (typically a NAME or GETPROP node)
     * @return Whether the name begins with a strip name
     */
    boolean qualifiedNameBeginsWithStripType(Node n) {
      String name = n.getQualifiedName();
      return qualifiedNameBeginsWithStripType(name);
    }

    /**
     * Gets whether a qualified name begins with a strip name. The names "goog.debug",
     * "goog.debug.Logger", and "goog.debug.Logger.Level" are examples of strip names that would
     * result in this function returning true for a node representing the name
     * "goog.debug.Logger.Level".
     *
     * @param name A qualified class name
     * @return Whether the name begins with a strip name
     */
    boolean qualifiedNameBeginsWithStripType(String name) {
      if (name != null) {
        for (String type : stripTypesList) {
          if (name.equals(type)) {
            logStripName(name, "equals strip type");
            return true;
          }
        }
        for (String type : stripTypePrefixesList) {
          if (name.startsWith(type)) {
            logStripName(name, "starts with strip type prefix");
            return true;
          }
        }
      }
      logNotAStripName(name, "does not begin with a strip type");
      return false;
    }

    /**
     * Determines whether a NAME node represents a reference to a variable that has been removed.
     *
     * @param t The traversal
     * @param n A NAME node
     * @return Whether the variable was removed
     */
    boolean isReferenceToRemovedVar(NodeTraversal t, Node n) {
      return varsToRemove.containsKey(n.getString());
    }

    /**
     * Gets whether a CALL node triggers statement removal, based on the name of the object whose
     * method is being called, or the name of the method. Checks whether the name begins with a
     * strip type, includes a field name that's a strip name, or belongs to the set of global
     * class-defining functions (e.g. goog.inherits).
     *
     * @param t The traversal
     * @param n A CALL node
     * @return Whether the node triggers statement removal
     */
    boolean isMethodOrCtorCallThatTriggersRemoval(NodeTraversal t, Node n, Node parent) {
      // CALL/NEW
      //   GETPROP (function)         <-- we're interested in this, the function
      //     GETPROP (callee object)  <-- or the object on which it is called
      //       ...
      //       STRING (field name)
      //     STRING (method name)
      //   ... (arguments)

      Node function = NodeUtil.getCallTargetResolvingIndirectCalls(n);
      if (function == null || !function.isQualifiedName()) {
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

      if (function.isName() && isStripName(function.getString())) {
        return true;
      }
      Node callee = function.getFirstChild();
      return nameIncludesFieldNameToStrip(callee)
          || nameIncludesFieldNameToStrip(function)
          || qualifiedNameBeginsWithStripType(function)
          || actsOnStripType(t, n);
    }

    /**
     * @return Whether a name includes a field name that should be stripped. E.g.,
     *     "foo.stripMe.bar", "(foo.bar).stripMe", etc.
     */
    boolean nameIncludesFieldNameToStrip(@Nullable Node n) {
      if (n == null) {
        return false;
      } else if (n.isGetProp()) {
        return isStripName(n.getString()) || nameIncludesFieldNameToStrip(n.getFirstChild());
      } else if (n.isName()) {
        String nameString = n.getString();
        // CollapseProperties may have turned "a.b.c" into "a$b$c",
        // so split that up and match its parts.
        if (nameString.indexOf('$') != -1) {
          for (String part : nameString.split("\\$")) {
            if (isStripName(part)) {
              return true;
            }
          }
        }
        return false;
      } else {
        return false;
      }
    }

    /**
     * Determines whether the given node helps to define a strip type. For example,
     * goog.inherits(stripType, Object) would be such a call.
     *
     * <p>Also reports an error if a non-strip type inherits from a strip type.
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
          logStripName(classes.subclassName, "class defining call");
          return true;
        }

        // report an error if a non-strip type inherits from a
        // strip type.
        if (qualifiedNameBeginsWithStripType(classes.superclassName)) {
          t.report(
              callNode, STRIP_TYPE_INHERIT_ERROR, classes.subclassName, classes.superclassName);
        }
      }

      return false;
    }

    /**
     * Gets whether a JavaScript identifier is the name of a variable or property that should be
     * stripped.
     *
     * @param name A JavaScript identifier
     * @return Whether {@code name} is a name that triggers removal
     */
    boolean isStripName(String name) {
      if (stripNameSuffixes.contains(name)) {
        logStripName(name, "matches a suffix");
        return true;
      }

      if (stripNamePrefixes.contains(name)) {
        logStripName(name, "matches a prefix");
        return true;
      }

      if (name.isEmpty() || Character.isUpperCase(name.charAt(0))) {
        logNotAStripName(name, "empty or starts with uppercase");
        return false;
      }

      String lcName = name.toLowerCase(Locale.ROOT);
      for (String stripName : stripNamePrefixesLowerCaseList) {
        if (lcName.startsWith(stripName)) {
          logStripName(name, () -> "matches lc prefix: " + stripName);
          return true;
        }
      }

      for (String stripName : stripNameSuffixesLowerCaseList) {
        if (lcName.endsWith(stripName)) {
          logStripName(name, () -> "matches lc suffix: " + stripName);
          return true;
        }
      }

      logNotAStripName(name, "no matches");
      return false;
    }

    private void logNotAStripName(String name, String reason) {
      if (decisionsLog.isLogging()) {
        decisionsLog.log(() -> name + "\tnot a strip name: " + reason);
      }
    }

    private void logStripName(String name, String reason) {
      if (decisionsLog.isLogging()) {
        decisionsLog.log(() -> name + "\tstrip name: " + reason);
      }
    }

    private void logStripName(String name, Supplier<String> reasonSupplier) {
      if (decisionsLog.isLogging()) {
        decisionsLog.log(() -> name + "\tstrip name: " + reasonSupplier.get());
      }
    }

    /**
     * Replaces a node with a NULL node. This is useful where a value is expected.
     *
     * @param n A node
     */
    void replaceWithNull(Node n) {
      decisionsLog.log(() -> "replace with null: " + n.getLocation());
      n.replaceWith(IR.nullNode());
      NodeUtil.markFunctionsDeleted(n, compiler);
    }
    /**
     * Replaces a node with an EMPTY node. This is useful where a statement is expected.
     *
     * @param n A node
     * @param parent {@code n}'s parent
     */
    void replaceWithEmpty(Node n, Node parent) {
      decisionsLog.log(() -> "replace with empty: " + n.getLocation());
      NodeUtil.removeChild(parent, n);
      NodeUtil.markFunctionsDeleted(n, compiler);
    }
  }
}
