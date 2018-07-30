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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.ArrayList;
import java.util.List;

/** Converts REST parameters and SPREAD expressions. */
public final class Es6RewriteRestAndSpread extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  static final DiagnosticType BAD_REST_PARAMETER_ANNOTATION =
      DiagnosticType.warning(
          "BAD_REST_PARAMETER_ANNOTATION",
          "Missing \"...\" in type annotation for rest parameter.");

  // The name of the index variable for populating the rest parameter array.
  private static final String REST_INDEX = "$jscomp$restIndex";

  // The name of the placeholder for the rest parameters.
  private static final String REST_PARAMS = "$jscomp$restParams";

  private static final String FRESH_SPREAD_VAR = "$jscomp$spread$args";
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.REST_PARAMETERS, Feature.SPREAD_EXPRESSIONS);

  private final AbstractCompiler compiler;

  private final JSType arrayType;
  private final JSType boolType;
  private final JSType concatFnType;
  private final JSType nullType;
  private final JSType numberType;
  private final JSType u2uFunctionType;
  private final JSType functionFunctionType;

  public Es6RewriteRestAndSpread(AbstractCompiler compiler) {
    this.compiler = compiler;

    if (compiler.hasTypeCheckingRun()) {
      JSTypeRegistry registry = compiler.getTypeRegistry();
      this.arrayType = registry.getNativeType(JSTypeNative.ARRAY_TYPE);
      this.boolType = registry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
      this.concatFnType = arrayType.findPropertyType("concat");
      this.nullType = registry.getNativeType(JSTypeNative.NULL_TYPE);
      this.numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
      this.u2uFunctionType = registry.getNativeType(JSTypeNative.U2U_FUNCTION_TYPE);
      this.functionFunctionType = registry.getNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE);
    } else {
      this.arrayType = null;
      this.boolType = null;
      this.concatFnType = null;
      this.nullType = null;
      this.numberType = null;
      this.u2uFunctionType = null;
      this.functionFunctionType = null;
    }
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void visit(NodeTraversal traversal, Node current, Node parent) {
    switch (current.getToken()) {
      case REST:
        visitRestParam(traversal, current, parent);
        break;
      case ARRAYLIT:
      case NEW:
      case CALL:
        for (Node child : current.children()) {
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
    String paramName = restParam.getFirstChild().getString();

    // Swap a vararg param into the parameter list.
    Node nameNode = IR.name(paramName);
    nameNode.setVarArgs(true);
    nameNode.setJSDocInfo(restParam.getJSDocInfo());
    paramList.replaceChild(restParam, nameNode);

    // Make sure rest parameters are typechecked.
    JSDocInfo inlineInfo = restParam.getJSDocInfo();
    JSDocInfo functionInfo = NodeUtil.getBestJSDocInfo(paramList.getParent());
    final JSTypeExpression paramTypeAnnotation;
    if (inlineInfo != null) {
      paramTypeAnnotation = inlineInfo.getType();
    } else if (functionInfo != null) {
      paramTypeAnnotation = functionInfo.getParameterType(paramName);
    } else {
      paramTypeAnnotation = null;
    }

    // TODO(lharker): we should report this error in typechecking, not during transpilation, so
    // that it also occurs when natively typechecking ES6.
    if (paramTypeAnnotation != null && paramTypeAnnotation.getRoot().getToken() != Token.ELLIPSIS) {
      compiler.report(JSError.make(restParam, BAD_REST_PARAMETER_ANNOTATION));
    }

    if (!functionBody.hasChildren()) {
      // If function has no body, we are done!
      t.reportCodeChange();
      return;
    }

    // Don't insert these directly, just clone them.
    Node newArrayName = IR.name(REST_PARAMS).setJSType(arrayType);
    Node cursorName = IR.name(REST_INDEX).setJSType(numberType);

    Node newBlock = IR.block().useSourceInfoFrom(functionBody);
    Node name = IR.name(paramName);
    Node let = IR.let(name, newArrayName).useSourceInfoIfMissingFromForTree(functionBody);
    newBlock.addChildToFront(let);
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.LET_DECLARATIONS);

    for (Node child : functionBody.children()) {
      newBlock.addChildToBack(child.detach());
    }

    Node newArrayDeclaration = IR.var(newArrayName.cloneTree(), arrayLitWithJSType());
    functionBody.addChildToFront(newArrayDeclaration.useSourceInfoIfMissingFromForTree(restParam));

    // TODO(b/74074478): Use a general utility method instead of an inlined loop.
    Node copyLoop =
        IR.forNode(
                IR.var(cursorName.cloneTree(), IR.number(restIndex).setJSType(numberType)),
                IR.lt(
                        cursorName.cloneTree(),
                        IR.getprop(IR.name("arguments"), IR.string("length")).setJSType(numberType))
                    .setJSType(boolType),
                IR.inc(cursorName.cloneTree(), false).setJSType(numberType),
                IR.block(
                    IR.exprResult(
                        IR.assign(
                                IR.getelem(
                                    newArrayName.cloneTree(),
                                    IR.sub(
                                            cursorName.cloneTree(),
                                            IR.number(restIndex).setJSType(numberType))
                                        .setJSType(numberType)),
                                IR.getelem(IR.name("arguments"), cursorName.cloneTree())
                                    .setJSType(numberType))
                            .setJSType(numberType))))
            .useSourceInfoIfMissingFromForTree(restParam);
    functionBody.addChildAfter(copyLoop, newArrayDeclaration);

    functionBody.addChildToBack(newBlock);
    compiler.reportChangeToEnclosingScope(newBlock);

    // For now, we are running transpilation before type-checking, so we'll
    // need to make sure changes don't invalidate the JSDoc annotations.
    // Therefore we keep the parameter list the same length and only initialize
    // the values if they are set to undefined.
    // TODO(lharker): the above comment is out of date since we move transpilation after
    // typechecking. see if we can improve transpilation and not keep the parameter list the
    // same length?
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

          groups.add(Es6ToEs3Util.arrayFromIterable(compiler, spreadExpression));
        }
      } else {
        if (currGroup == null) {
          currGroup = arrayLitWithJSType();
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
      baseArrayLit = arrayLitWithJSType();
    }

    final Node joinedGroups;
    if (groups.isEmpty()) {
      joinedGroups = baseArrayLit;
    } else {
      Node concat = IR.getprop(baseArrayLit, IR.string("concat")).setJSType(concatFnType);
      joinedGroups = IR.call(concat, groups.toArray(new Node[0]));
    }

    joinedGroups.useSourceInfoIfMissingFromForTree(spreadParent);
    joinedGroups.setJSType(arrayType);

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
    // Check if the callee has side effects before removing it from the AST (since some NodeUtil
    // methods assume the node they are passed has a non-null parent).
    boolean calleeMayHaveSideEffects = NodeUtil.mayHaveSideEffects(callee);
    // Must remove callee before extracting argument groups.
    spreadParent.removeChild(callee);

    final Node joinedGroups;
    if (spreadParent.hasOneChild() && isSpreadOfArguments(spreadParent.getOnlyChild())) {
      // Check for special case of `foo(...arguments)` and pass `arguments` directly to
      // `foo.apply(null, arguments)`. We want to avoid calling $jscomp.arrayFromIterable(arguments)
      // for this case, because it can have side effects, which prevents code removal.
      //
      // TODO(b/74074478): Generalize this to avoid ever calling $jscomp.arrayFromIterable() for
      // `arguments`.
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
        // TODO(nickreid): Stop distringuishing between array literals and variables when this pass
        // is moved after type-checking.
        Node baseArrayLit = groups.get(0).isArrayLit() ? groups.remove(0) : arrayLitWithJSType();
        Node concat = IR.getprop(baseArrayLit, IR.string("concat")).setJSType(concatFnType);
        joinedGroups = IR.call(concat, groups.toArray(new Node[0])).setJSType(arrayType);
      }
      joinedGroups.setJSType(arrayType);
    }

    final Node callToApply;
    if (calleeMayHaveSideEffects && callee.isGetProp()) {
      JSType receiverType = callee.getFirstChild().getJSType(); // Type of `foo()`.

      // foo().method(...[a, b, c])
      //   must convert to
      // var freshVar;
      // (freshVar = foo()).method.apply(freshVar, [a, b, c])
      Node freshVar =
          IR.name(FRESH_SPREAD_VAR + compiler.getUniqueNameIdSupplier().get())
              .setJSType(receiverType);
      Node freshVarDeclaration = IR.var(freshVar.cloneTree());

      Node statementContainingSpread = NodeUtil.getEnclosingStatement(spreadParent);
      freshVarDeclaration.useSourceInfoIfMissingFromForTree(statementContainingSpread);

      statementContainingSpread
          .getParent()
          .addChildBefore(freshVarDeclaration, statementContainingSpread);
      callee.addChildToFront(
          IR.assign(freshVar.cloneTree(), callee.removeFirstChild()).setJSType(receiverType));

      callToApply =
          IR.call(getpropInferringJSType(callee, "apply"), freshVar.cloneTree(), joinedGroups);
    } else {
      // foo.method(...[a, b, c]) -> foo.method.apply(foo, [a, b, c]
      // or
      // foo(...[a, b, c]) -> foo.apply(null, [a, b, c])
      Node context = callee.isGetProp() ? callee.getFirstChild().cloneTree() : nullWithJSType();
      callToApply = IR.call(getpropInferringJSType(callee, "apply"), context, joinedGroups);
    }

    callToApply.setJSType(spreadParent.getJSType());
    callToApply.useSourceInfoIfMissingFromForTree(spreadParent);
    spreadParent.replaceWith(callToApply);
    compiler.reportChangeToEnclosingScope(callToApply);
  }

  private boolean isSpreadOfArguments(Node n) {
    return n.isSpread() && n.getOnlyChild().matchesQualifiedName("arguments");
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
      baseArrayLit = arrayLitWithJSType();
    }
    baseArrayLit.addChildToFront(nullWithJSType());
    Node joinedGroups =
        groups.isEmpty()
            ? baseArrayLit
            : IR.call(
                    IR.getprop(baseArrayLit, IR.string("concat")).setJSType(concatFnType),
                    groups.toArray(new Node[0]))
                .setJSType(arrayType);

    if (FeatureSet.ES3.contains(compiler.getOptions().getOutputFeatureSet())) {
      // TODO(tbreisacher): Support this in ES3 too by not relying on Function.bind.
      Es6ToEs3Util.cannotConvert(
          compiler,
          spreadParent,
          "\"...\" passed to a constructor (consider using --language_out=ES5)");
    }

    // Function.prototype.bind =>
    //      function(this:function(new:[spreadParent], ...?), ...?):function(new:[spreadParent])
    // Function.prototype.bind.apply =>
    //      function(function(new:[spreadParent], ...?), !Array<?>):function(new:[spreadParent])
    Node bindApply =
        getpropInferringJSType(
            IR.getprop(
                    getpropInferringJSType(
                        IR.name("Function").setJSType(functionFunctionType), "prototype"),
                    "bind")
                .setJSType(u2uFunctionType),
            "apply");
    Node result =
        IR.newNode(
                callInferringJSType(
                    bindApply, callee, joinedGroups /* function(new:[spreadParent]) */))
            .setJSType(spreadParent.getJSType());

    result.useSourceInfoIfMissingFromForTree(spreadParent);
    spreadParent.replaceWith(result);
    compiler.reportChangeToEnclosingScope(result);
  }

  private Node arrayLitWithJSType() {
    return IR.arraylit().setJSType(arrayType);
  }

  private Node nullWithJSType() {
    return IR.nullNode().setJSType(nullType);
  }

  private Node getpropInferringJSType(Node receiver, String propName) {
    Node getprop = IR.getprop(receiver, propName);

    JSType receiverType = receiver.getJSType();
    if (receiverType == null) {
      return getprop;
    }

    JSType getpropType = receiverType.findPropertyType(propName);
    if (getpropType == null && receiverType instanceof FunctionType) {
      getpropType = ((FunctionType) receiverType).getPropertyType(propName);
    }

    return getprop.setJSType(getpropType);
  }

  private Node callInferringJSType(Node callee, Node... args) {
    Node call = IR.call(callee, args);

    JSType calleeType = callee.getJSType();
    if (calleeType == null || !(calleeType instanceof FunctionType)) {
      return call;
    }

    JSType returnType = ((FunctionType) calleeType).getReturnType();
    return call.setJSType(returnType);
  }
}
