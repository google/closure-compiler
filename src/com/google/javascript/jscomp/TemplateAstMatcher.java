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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.TypeMatchingStrategy.MatchResult;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A matcher that can take an arbitrary AST and use it as a template to find
 * matches in another. As this matcher potentially matches against every node
 * in the AST it is tuned to avoid generating GC garbage. It first checks the
 * AST shape without types and then successful checks the associated types.
 */
public final class TemplateAstMatcher {
  // Custom Token types for to use as placeholders in the template AST.
  private static final int TEMPLATE_TYPE_PARAM = Token.PLACEHOLDER1;
  private static final int TEMPLATE_LOCAL_NAME = Token.PLACEHOLDER2;

  private final AbstractCompiler compiler;

  /**
   * The head of the Node list that should be used to start the matching
   * process.
   */
  private final Node templateStart;

  /** The params declared in the template (in order) */
  private final List<String> templateParams = new ArrayList<>();

  /**
   * Record the first Node to match a template parameter, only valid for
   * the last match if it was successful.
   */
  private final ArrayList<Node> paramNodeMatches = new ArrayList<>();

  /** The locals declared in the template (in order) */
  private final List<String> templateLocals = new ArrayList<>();

  /**
   * Record the first name to match a template local variable, only valid for
   * the last match if it was successful.
   */
  private final ArrayList<String> localVarMatches = new ArrayList<>();

  /**
   * Record whether the last successful was a loosely matched type, only valid
   * for the last match if it was successful.
   */
  private boolean isLooseMatch = false;

  /**
   * The strategy to use when matching the {@code JSType} of nodes.
   */
  private TypeMatchingStrategy typeMatchingStrategy;

  /**
   * Constructs this matcher with a Function node that serves as the template
   * to match all other nodes against. The body of the function will be used
   * to match against.
   */
  public TemplateAstMatcher(
      AbstractCompiler compiler, Node templateFunctionNode) {
    this(compiler, templateFunctionNode, TypeMatchingStrategy.DEFAULT);
  }

  /**
   * Constructs this matcher with a Function node that serves as the template
   * to match all other nodes against. The body of the function will be used
   * to match against.
   */
  public TemplateAstMatcher(
      AbstractCompiler compiler,
      Node templateFunctionNode,
      TypeMatchingStrategy typeMatchingStrategy) {
    Preconditions.checkNotNull(compiler);
    Preconditions.checkState(
        templateFunctionNode.isFunction(),
        "Template node must be a function node. Received: %s",
        templateFunctionNode);

    this.compiler = compiler;
    this.templateStart = initTemplate(templateFunctionNode);
    this.typeMatchingStrategy = typeMatchingStrategy;
  }

  /**
   * @param n The node to check.
   * @return Whether the node is matches the template.
   */
  public boolean matches(Node n) {
    if (matchesTemplateShape(templateStart, n)) {
      if (paramNodeMatches.isEmpty() && localVarMatches.isEmpty()) {
        // If there are no parameters or locals to match against, this
        // has been a successful match and there is no reason to traverse
        // the AST again.
        return true;
      }
      reset();
      return matchesTemplate(templateStart, n);
    }
    return false;
  }

  /**
   * @return Whether the last match succeeded due to loose type information.
   */
  public boolean isLooseMatch() {
    return isLooseMatch;
  }

  /**
   * Returns a map from named template Nodes (such as parameters
   * or local variables) to Nodes that were matches from the last matched
   * template.
   */
  public Map<String, Node> getTemplateNodeToMatchMap() {
    Map<String, Node> map = new HashMap<>();

    for (int i = 0; i < templateParams.size(); i++) {
      String name = templateParams.get(i);
      map.put(name, paramNodeMatches.get(i));
    }

    for (int i = 0; i < templateLocals.size(); i++) {
      String name = templateLocals.get(i);
      map.put(name, IR.name(localVarMatches.get(i)));
    }

    return map;
  }

  /**
   * Prepare an template AST to use when performing matches.
   *
   * @param templateFunctionNode The template declaration function to extract
   *     the template AST from.
   * @return The first node of the template AST sequence to use when matching.
   */
  private Node initTemplate(Node templateFunctionNode) {
    Node prepped = templateFunctionNode.cloneTree();
    prepTemplatePlaceholders(prepped);

    Node body = prepped.getLastChild();
    Node startNode;
    if (body.hasOneChild() && body.getFirstChild().isExprResult()) {
      // When matching an expression, don't require it to be a complete
      // statement.
      startNode = body.getFirstChild().getFirstChild();
    } else {
      startNode = body.getFirstChild();
    }

    for (int i = 0; i < templateLocals.size(); i++) {
      // reserve space in the locals array.
      this.localVarMatches.add(null);
    }
    for (int i = 0; i < templateParams.size(); i++) {
      // reserve space in the params array.
      this.paramNodeMatches.add(null);
    }

    return startNode;
  }

  /**
   * Build parameter and local information for the template and replace
   * the references in the template 'fn' with placeholder nodes use to
   * facility matching.
   */
  private void prepTemplatePlaceholders(Node fn) {
    final List<String> locals = templateLocals;
    final List<String> params = templateParams;
    final Map<String, JSType> paramTypes = new HashMap<>();

    // drop the function name so it isn't include in the name maps
    String fnName = fn.getFirstChild().getString();
    fn.getFirstChild().setString("");

    // Build a list of parameter names and types.
    Node templateParametersNode = fn.getFirstChild().getNext();
    JSDocInfo info = NodeUtil.getBestJSDocInfo(fn);
    if (templateParametersNode.hasChildren()) {
      Preconditions.checkNotNull(info, 
          "Missing JSDoc declaration for template function %s", fnName);
    }
    for (Node paramNode : templateParametersNode.children()) {
      String name = paramNode.getString();
      JSTypeExpression expression = info.getParameterType(name);
      Preconditions.checkNotNull(expression, 
          "Missing JSDoc for parameter %s of template function %s", 
          name, fnName);
      JSType type = expression.evaluate(null, compiler.getTypeRegistry());
      Preconditions.checkNotNull(type);
      params.add(name);
      paramTypes.put(name, type);
    }

    // Find references to local variables and parameters and replace them.
    traverse(fn, new Visitor() {
      @Override
      public void visit(Node n) {
        if (n.isName()) {
          Node parent = n.getParent();
          String name = n.getString();
          if (!name.isEmpty() && parent.isVar() && !locals.contains(name)) {
            locals.add(n.getString());
          }

          if (params.contains(name)) {
            JSType type = paramTypes.get(name);
            replaceNodeInPlace(n,
                createTemplateParameterNode(params.indexOf(name), type));
          } else if (locals.contains(name)) {
            replaceNodeInPlace(n,
                createTemplateLocalNameNode(locals.indexOf(name)));
          }
        }
      }
    });
  }

  void replaceNodeInPlace(Node n, Node replacement) {
    Node parent = n.getParent();
    if (n.hasChildren()) {
      Node children = n.removeChildren();
      replacement.addChildrenToFront(children);
    }
    parent.replaceChild(n, replacement);
  }

  private static interface Visitor {
    void visit(Node n);
  }

  private void traverse(Node n, Visitor callback) {
    Node next = null;
    for (Node c = n.getFirstChild(); c != null; c = next) {
      next = c.getNext(); // in case the child is remove, grab the next node now
      traverse(c, callback);
    }
    callback.visit(n);
  }

  private void reset() {
    isLooseMatch = false;
    Collections.fill(localVarMatches, null);
    for (int i = 0; i < paramNodeMatches.size(); i++) {
      this.paramNodeMatches.set(i, null);
    }
  }

  private boolean isTemplateParameterNode(Node n) {
    return (n.getType() == TEMPLATE_TYPE_PARAM);
  }

  private Node createTemplateParameterNode(int index, JSType type) {
    Preconditions.checkState(index >= 0);
    Preconditions.checkNotNull(type);
    Node n = Node.newNumber(index);
    n.setType(TEMPLATE_TYPE_PARAM);
    n.setJSType(type);
    return n;
  }

  private boolean isTemplateLocalNameNode(Node n) {
    return (n.getType() == TEMPLATE_LOCAL_NAME);
  }

  private Node createTemplateLocalNameNode(int index) {
    Preconditions.checkState(index >= 0);
    Node n = Node.newNumber(index);
    n.setType(TEMPLATE_LOCAL_NAME);
    return n;
  }




  /**
   * Returns whether the template matches an AST structure node starting with
   * node, taking into account the template parameters that were provided to
   * this matcher.
   * Here only the template shape is checked, template local declarations and
   * parameters are checked later.
   */
  private boolean matchesTemplateShape(Node template, Node ast) {
    while (template != null) {
      if (ast == null || !matchesNodeShape(template, ast)) {
        return false;
      }
      template = template.getNext();
      ast = ast.getNext();
    }
    return true;
  }

  private boolean matchesNodeShape(Node template, Node ast) {
    if (isTemplateParameterNode(template)) {
      // Match the entire expression but only if it is an expression.
      return !NodeUtil.isStatement(ast);
    } else  if (isTemplateLocalNameNode(template)) {
      // Match any name. Maybe match locals here.
      if (!ast.isName()) {
        return false;
      }
      // But check any children.
    } else if (!template.isEquivalentToShallow(ast)) {
      return false;
    }

    // isEquivalentToShallow guarantees the child counts match
    Node templateChild = template.getFirstChild();
    Node astChild = ast.getFirstChild();
    while (templateChild != null) {
      if (!matchesNodeShape(templateChild, astChild)) {
        return false;
      }
      templateChild = templateChild.getNext();
      astChild = astChild.getNext();
    }
    return true;
  }


  private boolean matchesTemplate(Node template, Node ast) {
    while (template != null) {
      if (ast == null || !matchesNode(template, ast)) {
        return false;
      }
      template = template.getNext();
      ast = ast.getNext();
    }
    return true;
  }

  /**
   * Returns whether two nodes are equivalent, taking into account the template
   * parameters that were provided to this matcher. If the template comparison
   * node is a parameter node, then only the types of the node must match.
   * Otherwise, the node must be equal and the child nodes must be equivalent
   * according to the same function. This differs from the built in
   * Node equivalence function with the special comparison.
   */
  private boolean matchesNode(Node template, Node ast) {
    if (isTemplateParameterNode(template)) {
      int paramIndex = (int) (template.getDouble());
      Node previousMatch = paramNodeMatches.get(paramIndex);
      if (previousMatch != null) {
        // If this named node has already been matched against, make sure all
        // subsequent usages of the same named node are equivalent.
        return ast.isEquivalentTo(previousMatch);
      }

      // Only the types need to match for the template parameters, which allows
      // the template function to express arbitrary expressions.
      JSType templateType = template.getJSType();

      Preconditions.checkNotNull(templateType, "null template parameter type.");

      // TODO(johnlenz): We shouldn't spend time checking template whose
      // types whose definitions aren't included (NoResolvedType). Alternately
      // we should treat them as "unknown" and perform loose matches.
      if (templateType.isNoResolvedType()) {
        return false;
      }

      MatchResult matchResult = typeMatchingStrategy.match(templateType, ast.getJSType());
      isLooseMatch = matchResult.isLooseMatch();
      boolean isMatch = matchResult.isMatch();
      if (isMatch && previousMatch == null) {
        paramNodeMatches.set(paramIndex, ast);
      }
      return isMatch;
    } else if (isTemplateLocalNameNode(template)) {
      // If this template name node was already matched against, then make sure
      // all subsequent usages of the same template name node are equivalent in
      // the matched code.
      // For example, this code will handle the case:
      // function template() {
      //   var a = 'str';
      //   fn(a);
      // }
      //
      // will only match test code:
      //   var b = 'str';
      //   fn(b);
      //
      // but it will not match:
      //   var b = 'str';
      //   fn('str');
      int paramIndex = (int) (template.getDouble());
      boolean previouslyMatched = this.localVarMatches.get(paramIndex) != null;
      if (previouslyMatched) {
        // If this named node has already been matched against, make sure all
        // subsequent usages of the same named node are equivalent.
        return ast.getString().equals(this.localVarMatches.get(paramIndex));
      } else {
        this.localVarMatches.set(paramIndex, ast.getString());
      }
    }

    // Template and AST shape has already been checked, but continue look for
    // other template variables (parameters and locals) that must be checked.
    Node templateChild = template.getFirstChild();
    Node astChild = ast.getFirstChild();
    while (templateChild != null) {
      if (!matchesNode(templateChild, astChild)) {
        return false;
      }
      templateChild = templateChild.getNext();
      astChild = astChild.getNext();
    }

    return true;
  }
}
