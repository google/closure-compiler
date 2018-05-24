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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;

/** Converts REST parameters and SPREAD expressions. */
public final class Es6RewriteRestAndSpread extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;

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

  public Es6RewriteRestAndSpread(AbstractCompiler compiler) {
    this.compiler = compiler;
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
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case REST:
        visitRestParam(t, n, parent);
        break;
      case ARRAYLIT:
      case NEW:
      case CALL:
        for (Node child : n.children()) {
          if (child.isSpread()) {
            visitArrayLitOrCallWithSpread(n, parent);
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

    Node nameNode = IR.name(paramName);
    nameNode.setVarArgs(true);
    nameNode.setJSDocInfo(restParam.getJSDocInfo());
    paramList.replaceChild(restParam, nameNode);

    // Make sure rest parameters are typechecked
    JSTypeExpression type = null;
    JSDocInfo info = restParam.getJSDocInfo();
    JSDocInfo functionInfo = NodeUtil.getBestJSDocInfo(paramList.getParent());
    if (info != null) {
      type = info.getType();
    } else {
      if (functionInfo != null) {
        type = functionInfo.getParameterType(paramName);
      }
    }
    if (type != null && type.getRoot().getToken() != Token.ELLIPSIS) {
      compiler.report(JSError.make(restParam, BAD_REST_PARAMETER_ANNOTATION));
    }

    if (!functionBody.hasChildren()) {
      // If function has no body, we are done!
      t.reportCodeChange();
      return;
    }

    Node newBlock = IR.block().useSourceInfoFrom(functionBody);
    Node name = IR.name(paramName);
    Node let = IR.let(name, IR.name(REST_PARAMS)).useSourceInfoIfMissingFromForTree(functionBody);
    newBlock.addChildToFront(let);

    for (Node child : functionBody.children()) {
      newBlock.addChildToBack(child.detach());
    }

    if (type != null) {
      Node arrayType = IR.string("Array");
      Node typeNode = type.getRoot();
      Node memberType =
          typeNode.getToken() == Token.ELLIPSIS
              ? typeNode.getFirstChild().cloneTree()
              : typeNode.cloneTree();
      if (functionInfo != null) {
        memberType = replaceTypeVariablesWithUnknown(functionInfo, memberType);
      }
      arrayType.addChildToFront(
          new Node(Token.BLOCK, memberType).useSourceInfoIfMissingFrom(typeNode));
      JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
      builder.recordType(
          new JSTypeExpression(new Node(Token.BANG, arrayType), restParam.getSourceFileName()));
      name.setJSDocInfo(builder.build());
    }

    // TODO(b/74074478): Use a general utility method instead of an inlined loop.
    Node newArr = IR.var(IR.name(REST_PARAMS), IR.arraylit());
    functionBody.addChildToFront(newArr.useSourceInfoIfMissingFromForTree(restParam));
    Node init = IR.var(IR.name(REST_INDEX), IR.number(restIndex));
    Node cond = IR.lt(IR.name(REST_INDEX), IR.getprop(IR.name("arguments"), IR.string("length")));
    Node incr = IR.inc(IR.name(REST_INDEX), false);
    Node body =
        IR.block(
            IR.exprResult(
                IR.assign(
                    IR.getelem(
                        IR.name(REST_PARAMS), IR.sub(IR.name(REST_INDEX), IR.number(restIndex))),
                    IR.getelem(IR.name("arguments"), IR.name(REST_INDEX)))));
    functionBody.addChildAfter(
        IR.forNode(init, cond, incr, body).useSourceInfoIfMissingFromForTree(restParam), newArr);
    functionBody.addChildToBack(newBlock);
    compiler.reportChangeToEnclosingScope(newBlock);

    // For now, we are running transpilation before type-checking, so we'll
    // need to make sure changes don't invalidate the JSDoc annotations.
    // Therefore we keep the parameter list the same length and only initialize
    // the values if they are set to undefined.
  }

  private Node replaceTypeVariablesWithUnknown(JSDocInfo functionJsdoc, Node typeAst) {
    final List<String> typeVars = functionJsdoc.getTemplateTypeNames();
    if (typeVars.isEmpty()) {
      return typeAst;
    }
    NodeUtil.visitPreOrder(
        typeAst,
        new Visitor() {
          @Override
          public void visit(Node n) {
            if (n.isString() && n.getParent() != null && typeVars.contains(n.getString())) {
              n.replaceWith(new Node(Token.QMARK));
            }
          }
        });
    return typeAst;
  }

  /**
   * Processes array literals or calls containing spreads. Examples: [1, 2, ...x, 4, 5] =>
   * [].concat([1, 2], $jscomp.arrayFromIterable(x), [4, 5])
   *
   * <p>f(...arr) => f.apply(null, [].concat($jscomp.arrayFromIterable(arr)))
   *
   * <p>new F(...args) => new Function.prototype.bind.apply(F,
   * [].concat($jscomp.arrayFromIterable(args)))
   */
  private void visitArrayLitOrCallWithSpread(Node node, Node parent) {
    if (node.isArrayLit()) {
      visitArrayLitWithSpread(node, parent);
    } else if (node.isCall()) {
      visitCallWithSpread(node, parent);
    } else {
      checkArgument(node.isNew(), node);
      visitNewWithSpread(node, parent);
    }
  }

  /**
   * Extracts child nodes from an array literal, call or new node that may contain spread operators
   * into a list of nodes that may be concatenated with Array.concat() to get an array.
   *
   * <p>Example: [a, b, ...x, c, ...arguments] returns a list containing [ [a, b],
   * $jscomp.arrayFromIterable(x), [c], $jscomp.arrayFromIterable(arguments) ]
   *
   * <p>IMPORTANT: Call and New nodes must have the first, callee, child removed already.
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
   */
  private List<Node> extractSpreadGroups(Node parentNode) {
    checkArgument(parentNode.isCall() || parentNode.isArrayLit() || parentNode.isNew());
    List<Node> groups = new ArrayList<>();
    Node currGroup = null;
    Node currElement = parentNode.removeFirstChild();
    while (currElement != null) {
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
          currGroup = IR.arraylit();
        }
        currGroup.addChildToBack(currElement);
      }
      currElement = parentNode.removeFirstChild();
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
  private void visitArrayLitWithSpread(Node node, Node parent) {
    checkArgument(node.isArrayLit());
    List<Node> groups = extractSpreadGroups(node);
    Node baseArrayLit;
    if (groups.get(0).isArrayLit()) {
      baseArrayLit = groups.remove(0);
    } else {
      baseArrayLit = IR.arraylit();
      // [].concat(g0, g1, g2, ..., gn)
    }
    Node joinedGroups =
        groups.isEmpty()
            ? baseArrayLit
            : IR.call(IR.getprop(baseArrayLit, IR.string("concat")), groups.toArray(new Node[0]));
    joinedGroups.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, joinedGroups);
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
  private void visitCallWithSpread(Node node, Node parent) {
    checkArgument(node.isCall());
    Node callee = node.getFirstChild();
    // Check if the callee has side effects before removing it from the AST (since some NodeUtil
    // methods assume the node they are passed has a non-null parent).
    boolean calleeMayHaveSideEffects = NodeUtil.mayHaveSideEffects(callee);
    // must remove callee before extracting argument groups
    node.removeChild(callee);
    Node joinedGroups;
    if (node.hasOneChild() && isSpreadOfArguments(node.getOnlyChild())) {
      // Check for special case of
      // `foo(...arguments)` and pass `arguments` directly to `foo.apply(null, arguments)`.
      // We want to avoid calling $jscomp.arrayFromIterable(arguments) for this case,
      // because it can have side effects, which prevents code removal.
      // TODO(b/74074478): generalize this to avoid ever calling $jscomp.arrayFromIterable() for
      // `arguments`.
      joinedGroups = node.removeFirstChild().removeFirstChild();
    } else {
      List<Node> groups = extractSpreadGroups(node);
      checkState(!groups.isEmpty());
      if (groups.size() == 1) {
        // single group can just be passed to apply() as-is
        // It could be `arguments`, an array literal, or $jscomp.arrayFromIterable(someExpression).
        joinedGroups = groups.remove(0);
      } else {
        // If the first group is an array literal, we can just use that for concatenation,
        // otherwise use an empty array literal.
        Node baseArrayLit = groups.get(0).isArrayLit() ? groups.remove(0) : IR.arraylit();
        joinedGroups =
            groups.isEmpty()
                ? baseArrayLit
                : IR.call(
                    IR.getprop(baseArrayLit, IR.string("concat")), groups.toArray(new Node[0]));
      }
    }

    Node result = null;
    if (calleeMayHaveSideEffects && callee.isGetProp()) {
      // foo().method(...[a, b, c])
      //   must convert to
      // var freshVar;
      // (freshVar = foo()).method.apply(freshVar, [a, b, c])
      Node statement = node;
      while (!NodeUtil.isStatement(statement)) {
        statement = statement.getParent();
      }
      Node freshVar = IR.name(FRESH_SPREAD_VAR + compiler.getUniqueNameIdSupplier().get());
      Node n = IR.var(freshVar.cloneTree());
      n.useSourceInfoIfMissingFromForTree(statement);
      statement.getParent().addChildBefore(n, statement);
      callee.addChildToFront(IR.assign(freshVar.cloneTree(), callee.removeFirstChild()));
      result = IR.call(IR.getprop(callee, IR.string("apply")), freshVar, joinedGroups);
    } else {
      // foo.method(...[a, b, c]) -> foo.method.apply(foo, [a, b, c]
      // or
      // foo(...[a, b, c]) -> foo.apply(null, [a, b, c])
      Node context = callee.isGetProp() ? callee.getFirstChild().cloneTree() : IR.nullNode();
      result = IR.call(IR.getprop(callee, IR.string("apply")), context, joinedGroups);
    }
    result.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, result);
    compiler.reportChangeToEnclosingScope(result);
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
  private void visitNewWithSpread(Node node, Node parent) {
    checkArgument(node.isNew());
    // must remove callee before extracting argument groups
    Node callee = node.removeFirstChild();
    List<Node> groups = extractSpreadGroups(node);
    // We need to generate
    // new (Function.prototype.bind.apply(callee, [null].concat(other, args))();
    // null stands in for the 'this' arg to bind
    Node baseArrayLit;
    if (groups.get(0).isArrayLit()) {
      baseArrayLit = groups.remove(0);
    } else {
      baseArrayLit = IR.arraylit();
    }
    baseArrayLit.addChildToFront(IR.nullNode());
    Node joinedGroups =
        groups.isEmpty()
            ? baseArrayLit
            : IR.call(IR.getprop(baseArrayLit, IR.string("concat")), groups.toArray(new Node[0]));
    if (compiler.getOptions().getLanguageOut() == LanguageMode.ECMASCRIPT3) {
      // TODO(tbreisacher): Support this in ES3 too by not relying on Function.bind.
      Es6ToEs3Util.cannotConvert(
          compiler, node, "\"...\" passed to a constructor (consider using --language_out=ES5)");
    }
    Node bindApply = NodeUtil.newQName(compiler, "Function.prototype.bind.apply");
    Node result = IR.newNode(IR.call(bindApply, callee, joinedGroups));
    result.useSourceInfoIfMissingFromForTree(node);
    parent.replaceChild(node, result);
    compiler.reportChangeToEnclosingScope(result);
  }
}
