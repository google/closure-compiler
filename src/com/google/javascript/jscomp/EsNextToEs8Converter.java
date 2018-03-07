/*
 * Copyright 2018 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.Es6ToEs3Util.createType;
import static com.google.javascript.jscomp.Es6ToEs3Util.withType;

import com.google.auto.value.AutoValue;
import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts ESNext code to valid ES8 code.
 *
 * <p>Currently this class converts Object Rest/Spread properties as documented in tc39.
 * https://github.com/tc39/proposal-object-rest-spread
 */
public final class EsNextToEs8Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM
          .with(Feature.OBJECT_LITERALS_WITH_SPREAD)
          .with(Feature.OBJECT_PATTERN_REST);
  private final boolean addTypes;

  private static final String PATTERN_TEMP_VAR = "$jscomp$objpattern$var";

  private int patternVarCounter = 0;

  public EsNextToEs8Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.addTypes = MostRecentTypechecker.NTI.equals(compiler.getMostRecentTypechecker());
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.markFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case OBJECTLIT:
        visitObject(n);
        break;
      case OBJECT_PATTERN:
        if (n.hasChildren() && n.getLastChild().isRest()) {
          visitObjectPatternWithRest(n, parent);
        }
        break;
      default:
        break;
    }
  }

  private void visitObject(Node obj) {
    for (Node child : obj.children()) {
      if (child.isSpread()) {
        visitObjectWithSpread(obj);
        return;
      }
    }
  }

  @AutoValue
  abstract static class ComputedPropertyName {
    static ComputedPropertyName create(String varName, Node computation) {
      return new AutoValue_EsNextToEs8Converter_ComputedPropertyName(varName, computation);
    }

    abstract String varName();

    abstract Node computation();
  }

  /*
   * A Builder object that analyzes an object pattern and modifies the syntax trees accordingly.
   *
   * The constructor performs the analysis and stores any necessary information, but makes no change
   * to the syntax trees.
   *
   * The insertBindings and prependDeclStatements methods effect the necessary modifications to the
   * syntax tree.
   */
  private class ObjectPatternConverter {
    // A trivial class mapping variable names to computations.

    private final Node pattern;
    private final String varName;

    // Collect DELPROP nodes to delete the appropriate properties for the assignment to the rest
    // variable.
    private final List<Node> deletions = new ArrayList<>();
    // Collect pairs of computed property calls and values.  In general, computed property
    // computations may have side effects so we need to make sure they are called only once.  This
    // object accomplishes this via an auxiliary temporary variable for each computed property.
    private final List<ComputedPropertyName> computedProperties = new ArrayList<>();

    /*
     * Constructs a right-hand side for the rest variable, using the deletions computed in the
     * constructor.
     */
    private Node getRestRhs() {
      Node restRhs = newName();

      if (!this.deletions.isEmpty()) {
        Node comma = this.deletions.remove(0);
        for (Node deletion : this.deletions) {
          comma = IR.comma(comma, deletion);
        }
        restRhs = IR.comma(comma, restRhs);
      }

      restRhs.useSourceInfoIfMissingFromForTree(this.pattern);
      return restRhs;
    }

    ObjectPatternConverter(Node pattern) {
      this.pattern = pattern;
      this.varName = PATTERN_TEMP_VAR + (patternVarCounter++);
      for (Node child : pattern.children()) {
        if (child.isStringKey()) {
          // Add a deletion with the name of the child.
          deletions.add(
              new Node(
                  Token.DELPROP,
                  new Node(
                      child.isQuotedString() ? Token.GETELEM : Token.GETPROP,
                      newName(),
                      IR.string(child.getString()))));
        } else if (child.isComputedProp()) {
          // Create an auxiliary temp variable name.
          String auxTempVarName = PATTERN_TEMP_VAR + (patternVarCounter++);
          // Add a deletion with computed property using the auxiliary temp variable.
          deletions.add(
              new Node(Token.DELPROP, IR.getelem(newName(), IR.name(auxTempVarName))));

          // Add a pair mapping the auxiliary temp variable to the property name computation.
          ComputedPropertyName pair =
              ComputedPropertyName.create(
                  /* varName= */ auxTempVarName, /* computation= */ child.getFirstChild());

          computedProperties.add(pair);
        }
      }
    }

    /*
     * Wraps a call to IR.name with the temp var name and the appropriate source info.
     */
    Node newName() {
      Node name = IR.name(this.varName);
      name.useSourceInfoIfMissingFrom(this.pattern);
      return name;
    }

    /*
     * Inserts nodes into the grandparent of the pattern introducing the following series of
     * bindings:
     * (1) the temporary variable for this pattern to the thing the pattern was bound to
     * (2) (if any) the auxiliary variables of the computed properties bound to their calls
     * (3) the DESTRUCTURING_LHS containing the pattern itself, with the rest variable removed,
     *     bound to the temporary variable
     * (4) the rest variable bound to the temporary variable after deletion.
     */
    void insertBindings() {
      Node parent = this.pattern.getParent();
      checkState(parent.isDestructuringLhs(), parent);
      Node grandparent = parent.getParent();
      checkState(NodeUtil.isNameDeclaration(grandparent), grandparent);

      // Add a binding for the temporary variable.
      Node varName = this.newName();
      // The temp variable is bound to whatever the pattern was bound to.
      varName.addChildToBack(this.pattern.getNext().detach());
      // The temp variable binding goes before the DESTRUCTURING_LHS.
      grandparent.addChildBefore(varName, parent);

      for (ComputedPropertyName pair : this.computedProperties) {
        // Replace the computation with the auxiliary temp variable name.
        pair.computation().replaceWith(IR.name(pair.varName()));

        Node compPropLhs = IR.name(pair.varName());
        compPropLhs.addChildToBack(pair.computation());
        compPropLhs.useSourceInfoIfMissingFromForTree(this.pattern);

        // The auxiliary temp variable binding goes before the DESTRUCTURING_LHS.
        grandparent.addChildBefore(compPropLhs, parent);
      }

      // Remove the rest variable from the pattern.
      Node restNode = this.pattern.getLastChild().detach();

      // The DESTRUCTURING_LHS is now bound to the temporary variable.
      parent.addChildToBack(this.newName());

      Node restLhs = restNode.removeFirstChild();
      restLhs.addChildToBack(this.getRestRhs()); // get the temp variable after deletions.

      // The rest binding goes after the DESTRUCTURING_LHS.
      grandparent.addChildAfter(restLhs, parent);
    }

    /*
     * Prepends to the block introducing the following pair of statements:
     * (1) (if any) let statements for auxiliary temp variables for computed properties in the
     * pattern
     * (2) A declaration (or assignment) for
     *     (a) the head: the pattern without the rest, whose value is the temporary variable.
     *     (b) the rest variable, whose value is the temporary variable after deletions.
     */
    void prependDeclStatements(Token declType, Node block) {
      List<Node> statements = new ArrayList<>();

      for (ComputedPropertyName pair : this.computedProperties) {
        // Replace the computation with the auxiliary temp variable name.
        pair.computation().replaceWith(IR.name(pair.varName()));

        Node let = IR.let(IR.name(pair.varName()), pair.computation());
        let.useSourceInfoIfMissingFromForTree(this.pattern);

        statements.add(let);
      }

      // Remove the pattern from its parent.
      Node headLhs = this.pattern.detach();
      Node headRhs = this.newName();

      // Remove the rest variable from the pattern.
      Node restNode = this.pattern.getLastChild().detach();

      Node restLhs = restNode.removeFirstChild();
      Node restRhs = this.getRestRhs(); // get the temp variable after deletions.

      if (declType == Token.ASSIGN) {
        Node assign =
            IR.exprResult(
                IR.comma(
                    // An assignment for the head.
                    IR.assign(headLhs, headRhs),
                    // An assignment for the rest.
                    IR.assign(restLhs, restRhs)));
        assign.useSourceInfoIfMissingFromForTree(this.pattern);
        statements.add(assign);
      } else {
        // Create a declaration with the head.
        Node decl = IR.declaration(headLhs, headRhs, declType);

        // Add a second declaration for the rest.
        restLhs.addChildToBack(restRhs);
        decl.addChildToBack(restLhs);

        decl.useSourceInfoIfMissingFromForTree(this.pattern);
        statements.add(decl);
      }

      // Prepend the statements to the block.
      Node next = block.getFirstChild();
      for (Node statement : statements) {
        if (next == null) {
          block.addChildToBack(statement);
        } else {
          block.addChildBefore(statement, next);
        }
      }
    }
  }

  /*
   * Figure out whether the result of the node can be omitted.
   */
  private boolean canOmitResult(Node n) {
    if (n.getParent().isExprResult()) {
      // If the parent is an expression result the returned value is ignored.
      return true;
    }
    if (n.getParent().isComma()) {
      if (n.getNext() != null) {
        // If the node is on the left side of a comma, its returned value is ignored.
        return true;
      } else {
        // On the right side, it depends on the parent.
        return canOmitResult(n.getParent());
      }
    }
    // Err on the side of using an explicit return.
    return false;
  }

  /*
   * Handle object patterns with rest.
   */
  private void visitObjectPatternWithRest(Node pattern, Node parent) {
    checkArgument(pattern.isObjectPattern(), pattern);

    // A Builder object that will effect necessary changes to the syntax tree.  The constructor
    // makes no changes to the syntax tree, those will take place in subsequent calls to the
    // ObjectPatternConverter object.
    ObjectPatternConverter converter = new ObjectPatternConverter(pattern);

    /*
     * Convert 'try { x; } catch ({y, ...rest}) { z; }' to:
     * 'try { x; } catch ($tmp) {
     *    let {y} = $tmp,
     *        rest = (delete $tmp.y, $tmp);
     *    z;
     *  }'
     */
    if (parent.isCatch()) {
      // The handling block is the second child, after the catch.
      Node block = parent.getSecondChild();

      // Use let so that the variables have block scope.
      converter.prependDeclStatements(Token.LET, block); // Detaches the pattern from its parent.

      // Put the temp var in the catch, which was left empty by the removal of the pattern.
      parent.addChildToFront(converter.newName());
      compiler.reportChangeToEnclosingScope(parent);
      return;
    }

    Node grandparent = parent.getParent();

    /*
     * Convert 'function f({x,...rest}) { z; }' to:
     * 'function f($tmp) {
     *    let {x} = $tmp,
     *        rest = (delete $tmp.x, $tmp);
     *    z;
     *  }'
     */
    if (parent.isParamList() || (parent.isDefaultValue() && grandparent.isParamList())) {
      // The function body is the Node after the param list.
      Node body = parent.isParamList() ? parent.getNext() : grandparent.getNext();

      // Use let so that the variables have function scope.
      converter.prependDeclStatements(Token.LET, body); // Detaches the pattern from its parent.

      // Put the temp var in the param list (or default), which was left empty by the removal of the
      // pattern.
      parent.addChildToFront(converter.newName());
      compiler.reportChangeToEnclosingScope(parent);
      return;
    }

    /*
     * Convert 'for ({x, ...rest} of foo()) { z; }' to:
     * 'for (let $tmp of foo()) {
     *   ({x} = $tmp,
     *    rest = (delete $tmp.x, $tmp));
     *   z;
     * }'
     */
    if (NodeUtil.isEnhancedFor(parent)) {
      Node enhancedFor = parent;

      Node block = enhancedFor.getLastChild();
      converter.prependDeclStatements(Token.ASSIGN, block);

      // Replace the pattern with a let for the temp variable.
      Node let = new Node(Token.LET, converter.newName());
      let.useSourceInfoIfMissingFrom(pattern);
      enhancedFor.addChildToFront(let);

      compiler.reportChangeToEnclosingScope(enhancedFor);
    }

    if (parent.isDestructuringLhs()) {
      if (NodeUtil.isNameDeclaration(grandparent)) {
        if (NodeUtil.isEnhancedFor(grandparent.getParent())) {
          /*
           * Convert 'for (var {x, ...rest} of foo()) { z; }' to:
           * 'for (let $tmp of foo()) {
           *   var {x} = $tmp,
           *       rest = (delete $tmp.x, $tmp);
           *   z;
           * }'
           * (also handles const and let).
           */
          Node enhancedFor = grandparent.getParent();

          Node block = enhancedFor.getLastChild();
          converter.prependDeclStatements(grandparent.getToken(), block);

          // Replace the name declaration with a let for the temp variable.
          Node let = new Node(Token.LET, converter.newName());
          let.useSourceInfoIfMissingFrom(pattern);
          enhancedFor.replaceChild(grandparent, let);

          compiler.reportChangeToEnclosingScope(enhancedFor);
          return;
        } else {
          /*
           * Convert 'var ..., {x,...rest} = foo(), ...;' to
           * 'var ..., $tmp=foo(), {x}=$tmp, rest=(delete $tmp.x, $tmp), ...;'
           * (also handles const and let).
           */
          converter.insertBindings();
          compiler.reportChangeToEnclosingScope(grandparent);
        }
      }
    }

    /*
     * Convert '..., ... = {x,...rest} = foo(), ...' to
     * '..., ... = (() => {
     *   let $tmp = foo();
     *   let $copy = $tmp; // copy is saved to be returned unmodified
     *   {x} = $tmp, rest = (delete $tmp.x, $tmp);
     *   return $copy;
     * }(), ...'
     * $copy is omitted if the return value is not needed.
     */
    if (parent.isAssign()) {
      Node rhs = pattern.getNext();

      Node body = IR.block();
      converter.prependDeclStatements(Token.ASSIGN, body);
      if (!canOmitResult(parent)) {
        // If the result is needed then we have to store and return a pristine copy whose
        // properties are not deleted.
        String copyName = PATTERN_TEMP_VAR + (patternVarCounter++);

        // The copy goes at the front of the body, before the deletions.
        body.addChildToFront(IR.let(IR.name(copyName), converter.newName()));
        // The return must be last.
        body.addChildToBack(IR.returnNode(IR.name(copyName)));
      }
      // Add the new let for the temp variable at the beginning of the body.
      body.addChildToFront(IR.let(converter.newName(), rhs.detach()));

      Node call = IR.call(IR.arrowFunction(IR.name(""), IR.paramList(), body));
      call.putBooleanProp(Node.FREE_CALL, true);
      call.useSourceInfoIfMissingFromForTree(pattern);
      NodeUtil.markNewScopesChanged(call, compiler);

      grandparent.replaceChild(parent, call);
      compiler.reportChangeToEnclosingScope(grandparent);
      return;
    }
  }

  /*
   * Convert '{first: b, c, ...spread, d: e, last}' to:
   *
   * Object.assign({}, {first:b, c}, spread, {d:e, last});
   */
  private void visitObjectWithSpread(Node obj) {
    checkArgument(obj.isObjectLit());

    TypeI simpleObjectType =
        createType(addTypes, compiler.getTypeIRegistry(), JSTypeNative.EMPTY_OBJECT_LITERAL_TYPE);

    TypeI resultType = simpleObjectType;
    Node result = withType(IR.call(NodeUtil.newQName(compiler, "Object.assign")), resultType);

    // Add an empty target object literal so changes made by Object.assign will not affect any other
    // variables.
    result.addChildToBack(withType(IR.objectlit(), simpleObjectType));

    // An indicator whether the current last thing in the param list is an object literal to which
    // properties may be added.  Initialized to null since nothing should be added to the empty
    // object literal in first position of the param list.
    Node trailingObjectLiteral = null;

    for (Node child : obj.children()) {
      if (child.isSpread()) {
        // Add the object directly to the param list.
        Node spreaded = child.removeFirstChild();
        result.addChildToBack(spreaded);

        // Properties should not be added to the trailing object.
        trailingObjectLiteral = null;
      } else {
        if (trailingObjectLiteral == null) {
          // Add a new object to which properties may be added.
          trailingObjectLiteral = withType(IR.objectlit(), simpleObjectType);
          result.addChildToBack(trailingObjectLiteral);
        }
        // Add the property to the object literal.
        trailingObjectLiteral.addChildToBack(child.detach());
      }
    }

    result.useSourceInfoIfMissingFromForTree(obj);
    obj.replaceWith(result);
    compiler.reportChangeToEnclosingScope(result);
  }
}
