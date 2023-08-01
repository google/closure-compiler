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
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import java.util.ArrayList;
import java.util.List;

/** Converts REST parameters and SPREAD expressions. */
public final class Es6RewriteRestAndSpread extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  private static final String FRESH_SPREAD_VAR = "$jscomp$spread$args";
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.REST_PARAMETERS, Feature.SPREAD_EXPRESSIONS);

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final StaticScope namespace;

  private static final AstFactory.Type arrayType = type(StandardColors.ARRAY_ID);
  private static final AstFactory.Type concatFnType = type(StandardColors.TOP_OBJECT);

  public Es6RewriteRestAndSpread(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.namespace = compiler.getTranspilationNamespace();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, transpiledFeatures);
  }

  @Override
  public void visit(NodeTraversal traversal, Node current, Node parent) {
    switch (current.getToken()) {
      case ITER_REST:
        visitRestParam(traversal, current, parent);
        break;
      case ARRAYLIT:
      case NEW:
      case CALL:
        for (Node child = current.getFirstChild(); child != null; child = child.getNext()) {
          if (child.isSpread()) {
            visitArrayLitOrCallWithSpread(current);
            break;
          }
        }
        break;
      default:
        break;
    }
  }

  /** Processes a rest parameter */
  private void visitRestParam(NodeTraversal t, Node restParam, Node paramList) {
    Node functionBody = paramList.getNext();
    int restIndex = paramList.getIndexOfChild(restParam);
    Node nameNode = restParam.getOnlyChild();
    String paramName = nameNode.getString();

    // Remove the existing param from the list, as it will be replaced with a declaration with the
    // same name.
    restParam.detach();

    if (!functionBody.hasChildren()) {
      // If function has no body, we are done!
      t.reportCodeChange();
      return;
    }

    Node let =
        astFactory
            .createSingleLetNameDeclaration(
                paramName,
                astFactory.createCall(
                    astFactory.createGetPropWithUnknownType(
                        astFactory.createQName(this.namespace, "$jscomp.getRestArguments"),
                        "apply"),
                    type(nameNode),
                    astFactory.createNumber(restIndex),
                    astFactory.createArgumentsReference()))
            .srcrefTreeIfMissing(functionBody);
    functionBody.addChildToFront(let);
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.LET_DECLARATIONS, compiler);
    compiler.ensureLibraryInjected("es6/util/restarguments", /* force= */ false);
    t.reportCodeChange();
  }

  /**
   * Processes array literals or calls to eliminate spreads.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>[1, 2, ...x, 4, 5] => [].concat([1, 2], $jscomp.arrayFromIterable(x), [4, 5])
   *   <li>f(1, ...arr) => f.apply(null, [1].concat($jscomp.arrayFromIterable(arr)))
   *   <li>new F(...args) => new Function.prototype.bind.apply(F,
   *       [null].concat($jscomp.arrayFromIterable(args)))
   * </ul>
   */
  private void visitArrayLitOrCallWithSpread(Node spreadParent) {
    if (spreadParent.isArrayLit()) {
      visitArrayLitContainingSpread(spreadParent);
    } else if (spreadParent.isCall()) {
      visitCallContainingSpread(spreadParent);
    } else {
      checkArgument(spreadParent.isNew(), spreadParent);
      visitNewWithSpread(spreadParent);
    }
  }

  /**
   * Extracts child nodes from an ARRAYLIT, CALL or NEW node that may contain spread operators into
   * a list of nodes that may be concatenated with Array.concat() to get an array.
   *
   * <p>Example: [a, b, ...x, c, ...arguments] returns a list containing [ [a, b],
   * $jscomp.arrayFromIterable(x), [c], $jscomp.arrayFromIterable(arguments) ]
   *
   * <p>IMPORTANT: CALL and NEW nodes must have the first, callee, child removed already.
   *
   * <p>Note that all elements of the returned list will be one of:
   *
   * <ul>
   *   <li>array literal
   *   <li>$jscomp.arrayFromIterable(spreadExpression)
   * </ul>
   *
   * TODO(bradfordcsmith): When this pass moves after type checking, we can use type information to
   * avoid unnecessary calls to $jscomp.arrayFromIterable().
   *
   * <p>TODO(nickreid): Stop mutating `spreadParent`.
   */
  private List<Node> extractSpreadGroups(Node spreadParent) {
    checkArgument(spreadParent.isCall() || spreadParent.isArrayLit() || spreadParent.isNew());

    List<Node> groups = new ArrayList<>();
    Node currGroup = null;

    for (Node currElement = spreadParent.removeFirstChild();
        currElement != null;
        currElement = spreadParent.removeFirstChild()) {
      if (currElement.isSpread()) {
        Node spreadExpression = currElement.removeFirstChild();
        if (spreadExpression.isArrayLit()) {
          // We can expand an array literal spread in place.
          if (currGroup == null) {
            // [...[spread, contents], a, b]
            // we can use this array lit itself as a group and append following elements to it
            currGroup = spreadExpression;
          } else {
            // [ a, b, ...[spread, contents], c]
            // Just add contents of this array lit to the group we were already collecting.
            currGroup.addChildrenToBack(spreadExpression.removeChildren());
          }
        } else {
          // We need to treat the spread expression as a separate group
          if (currGroup != null) {
            // finish off and add the group we were collecting before
            groups.add(currGroup);
            currGroup = null;
          }

          TranspilationUtil.preloadTranspilationRuntimeFunction(compiler, "arrayFromIterable");
          groups.add(
              astFactory.createJscompArrayFromIterableCall(spreadExpression, this.namespace));
        }
      } else {
        if (currGroup == null) {
          currGroup = astFactory.createArraylit();
        }
        currGroup.addChildToBack(currElement);
      }
    }

    if (currGroup != null) {
      groups.add(currGroup);
    }
    return groups;
  }

  /**
   * Processes array literals containing spreads.
   *
   * <p>Example:
   *
   * <pre><code>
   * [1, 2, ...x, 4, 5] => [1, 2].concat($jscomp.arrayFromIterable(x), [4, 5])
   * </code></pre>
   */
  private void visitArrayLitContainingSpread(Node spreadParent) {
    checkArgument(spreadParent.isArrayLit());

    List<Node> groups = extractSpreadGroups(spreadParent);

    final Node baseArrayLit;
    if (groups.get(0).isArrayLit()) {
      // g0.concat(g1, g2, ..., gn)
      baseArrayLit = groups.remove(0);
    } else {
      // [].concat(g0, g1, g2, ..., gn)
      baseArrayLit = astFactory.createArraylit();
    }

    final Node joinedGroups;
    if (groups.isEmpty()) {
      joinedGroups = baseArrayLit;
    } else {
      Node concat = astFactory.createGetProp(baseArrayLit, "concat", concatFnType);
      joinedGroups = astFactory.createCall(concat, type(spreadParent), groups.toArray(new Node[0]));
    }

    joinedGroups.srcrefTreeIfMissing(spreadParent);

    spreadParent.replaceWith(joinedGroups);
    compiler.reportChangeToEnclosingScope(joinedGroups);
  }

  /**
   * Processes calls containing spreads.
   *
   * <p>Examples:
   *
   * <pre><code>
   * f(...arr) => f.apply(null, $jscomp.arrayFromIterable(arr))
   * f(a, ...arr) => f.apply(null, [a].concat($jscomp.arrayFromIterable(arr)))
   * f(...arr, b) => f.apply(null, [].concat($jscomp.arrayFromIterable(arr), [b]))
   * </code></pre>
   */
  private void visitCallContainingSpread(Node spreadParent) {
    checkArgument(spreadParent.isCall());

    Node callee = spreadParent.getFirstChild();
    // ES6 classes must all be transpiled away before this pass runs.
    checkState(!callee.isSuper(), "Cannot spread into super calls");
    // Check if the callee has side effects before removing it from the AST (since some NodeUtil
    // methods assume the node they are passed has a non-null parent).
    boolean calleeMayHaveSideEffects = compiler.getAstAnalyzer().mayHaveSideEffects(callee);
    // Must remove callee before extracting argument groups.
    callee.detach();

    while (callee.isCast()) {
      // Drop any CAST nodes. They're not needed anymore since this pass runs at the end of
      // the checks phase, and they complicate detecting GETPROP/GETELEM callees.
      callee = callee.removeFirstChild();
    }
    final Node joinedGroups;
    if (spreadParent.hasOneChild() && isSpreadOfArguments(spreadParent.getOnlyChild())) {
      // Check for special case of `foo(...arguments)` and pass `arguments` directly to
      // `foo.apply(null, arguments)`. We want to avoid calling $jscomp.arrayFromIterable(arguments)
      // for this case, because it can have side effects, which prevents code removal.
      joinedGroups = spreadParent.removeFirstChild().removeFirstChild();
    } else {
      List<Node> groups = extractSpreadGroups(spreadParent);
      checkState(!groups.isEmpty());

      if (groups.size() == 1) {
        // A single group can just be passed to `apply()` as-is
        // It could be `arguments`, an array literal, or $jscomp.arrayFromIterable(someExpression).
        joinedGroups = groups.remove(0);
      } else {
        // If the first group is an array literal, we can just use that for concatenation,
        // otherwise use an empty array literal.
        //
        // TODO(bradfordcsmith): Now that this pass runs after type checking, it would be nice
        // to skip creating an array literal when when the type of the first element says it is
        // an Array.
        Node baseArrayLit =
            groups.get(0).isArrayLit() ? groups.remove(0) : astFactory.createArraylit();
        Node concat = astFactory.createGetProp(baseArrayLit, "concat", concatFnType);
        joinedGroups = astFactory.createCall(concat, arrayType, groups.toArray(new Node[0]));
      }
    }
    boolean isFreeCall = spreadParent.getBooleanProp(Node.FREE_CALL);

    final Node callToApply;
    if (calleeMayHaveSideEffects && callee.isGetProp() && !isFreeCall) {
      // foo().method(...[a, b, c])
      //   must convert to
      // var freshVar;
      // (freshVar = foo()).method.apply(freshVar, [a, b, c])
      Node freshVar =
          astFactory.createName(
              FRESH_SPREAD_VAR + compiler.getUniqueNameIdSupplier().get(),
              // Type of `foo()`.
              type(callee.getFirstChild()));
      Node freshVarDeclaration = IR.var(freshVar.cloneTree());

      Node statementContainingSpread = NodeUtil.getEnclosingStatement(spreadParent);
      freshVarDeclaration.srcrefTreeIfMissing(statementContainingSpread);

      freshVarDeclaration.insertBefore(statementContainingSpread);
      callee.addChildToFront(
          astFactory.createAssign(freshVar.cloneTree(), callee.removeFirstChild()));

      callToApply =
          astFactory.createCallWithUnknownType(
              astFactory.createGetPropWithUnknownType(callee, "apply"),
              freshVar.cloneTree(),
              joinedGroups);
    } else {
      // foo.method(...[a, b, c]) -> foo.method.apply(foo, [a, b, c])
      // foo['method'](...[a, b, c]) -> foo['method'].apply(foo, [a, b, c])
      // or
      // foo(...[a, b, c]) -> foo.apply(null, [a, b, c])
      Node context =
          (callee.isGetProp() || callee.isGetElem()) && !isFreeCall
              ? callee.getFirstChild().cloneTree()
              : astFactory.createNull();
      callToApply =
          astFactory.createCall(
              astFactory.createGetPropWithUnknownType(callee, "apply"),
              type(spreadParent),
              context,
              joinedGroups);
    }

    callToApply.setColor(spreadParent.getColor());
    callToApply.srcrefTreeIfMissing(spreadParent);
    spreadParent.replaceWith(callToApply);
    compiler.reportChangeToEnclosingScope(callToApply);
  }

  private boolean isSpreadOfArguments(Node n) {
    return n.isSpread() && n.getOnlyChild().matchesName("arguments");
  }

  /**
   * Processes new calls containing spreads.
   *
   * <p>Example:
   *
   * <pre><code>
   * new F(...args) =>
   *     new Function.prototype.bind.apply(F, [].concat($jscomp.arrayFromIterable(args)))
   * </code></pre>
   */
  private void visitNewWithSpread(Node spreadParent) {
    checkArgument(spreadParent.isNew());

    // Must remove callee before extracting argument groups.
    Node callee = spreadParent.removeFirstChild();
    List<Node> groups = extractSpreadGroups(spreadParent);

    // We need to generate
    // `new (Function.prototype.bind.apply(callee, [null].concat(other, args))();`.
    // `null` stands in for the 'this' arg to the contructor.
    final Node baseArrayLit;
    if (groups.get(0).isArrayLit()) {
      baseArrayLit = groups.remove(0);
    } else {
      baseArrayLit = astFactory.createArraylit();
    }
    baseArrayLit.addChildToFront(astFactory.createNull());
    Node joinedGroups =
        groups.isEmpty()
            ? baseArrayLit
            : astFactory.createCall(
                astFactory.createGetProp(baseArrayLit, "concat", concatFnType),
                arrayType,
                groups.toArray(new Node[0]));

    if (FeatureSet.ES3.contains(compiler.getOptions().getOutputFeatureSet())) {
      // TODO(tbreisacher): Support this in ES3 too by not relying on Function.bind.
      TranspilationUtil.cannotConvert(
          compiler,
          spreadParent,
          "\"...\" passed to a constructor (consider using --language_out=ES5)");
    }

    // Function.prototype.bind =>
    //      function(this:function(new:[spreadParent], ...?), ...?):function(new:[spreadParent])
    // Function.prototype.bind.apply =>
    //      function(function(new:[spreadParent], ...?), !Array<?>):function(new:[spreadParent])
    Node bindApply =
        astFactory.createGetPropWithUnknownType(
            astFactory.createGetPropWithUnknownType(
                astFactory.createPrototypeAccess(astFactory.createName(this.namespace, "Function")),
                "bind"),
            "apply");
    Node result =
        IR.newNode(
                astFactory.createCallWithUnknownType(
                    bindApply, callee, joinedGroups /* function(new:[spreadParent]) */))
            .setColor(spreadParent.getColor());

    result.srcrefTreeIfMissing(spreadParent);
    spreadParent.replaceWith(result);
    compiler.reportChangeToEnclosingScope(result);
  }

}
