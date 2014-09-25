/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>Replaces references to aliasable keyword literals (true, false,
 * null) with variables and statements (throw) with functions declared in the
 * global scope. When combined with RenameVars, this pass helps to reduce the
 * number of bytes taken up by references to these keywords by replacing them
 * with references to variables and functions with shorter names.</p>
 *
 */
class AliasKeywords implements CompilerPass {
  /** Callback that finds the nodes that we will alias. */
  private class FindAliasableNodes extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      final int type = n.getType();
      if (isAliasableType(type)) {
        visitAliasableNode(n, parent);
      } else if (type == Token.NAME) {
        visitNameNode(n);
      }
    }

    /**
     * Find the AliasSpecification associated with the node, and tell
     * that AliasSpecification about the new node.
     */
    private void visitAliasableNode(Node n, Node parent) {
      AliasSpecification aliasableNodes = aliasTypes.get(n.getType());
      aliasableNodes.visit(n, parent);
    }

    /**
     * Sanity check that our aliases are not already defined by
     * someone else.
     */
    private void visitNameNode(Node n) {
      if (isAliasDefinition(n)) {
        throw new IllegalStateException(
            "Existing alias definition for " + Token.name(n.getType()));
      }
    }
  }

  /**
   * An AliasSpecification encapsulates all of the logic to find
   * aliasable nodes and alias those nodes, for a given alias name. Subclasses
   * fill in template methods, allowing for various kinds of aliasing.
   */
  private abstract static class AliasSpecification {

    /** List of nodes to alias (e.g. all 'null' nodes). */
    private final Map<Node, Node> nodes = Maps.newHashMap();

    /**
     * Have we declared the alias (e.g. did we inject var
     * $$ALIAS_NULL=null; into the parse tree)?
     */
    private boolean isAliased = false;

    private String aliasName;

    private int tokenId;

    /**
     * @param aliasName name being used as alias
     * @param tokenId type of node being replaced with alias
     */
    public AliasSpecification(String aliasName, int tokenId) {
      this.aliasName = aliasName;
      this.tokenId = tokenId;
    }

    public void visit(Node n, Node parent) {
      nodes.put(n, parent);
    }

    /**
     * Insert a node that declares our alias into the parse tree, as a
     * child of the specified var node. Only do so if we haven't
     * already and there are enough references to the aliased node to
     * save bytes.
     * @return Whether the alias has been inserted.
     */
    boolean maybeInsertAliasDeclarationIntoParseTree(Node codeRoot) {
      if (nodes.size() >= minOccurrencesRequiredToAlias()) {
        insertAliasDeclaration(codeRoot);
        isAliased = true;
        return true;
      }
      return false;
    }

    /**
     * Update all of the nodes with a reference to the corresponding
     * alias node.
     */
    public void doAlias(AbstractCompiler compiler) {
      if (isAliased) {
        for (Map.Entry<Node, Node> entry : nodes.entrySet()) {
          Node n = entry.getKey();
          Node parent = entry.getValue();
          aliasNode(n, parent);
          compiler.reportCodeChange();
        }
      }
    }

    public int getTokenId() {
      return tokenId;
    }

    public String getAliasName() {
      return aliasName;
    }

    /**
     * Returns the minimum number of nodes that should be present for aliasing
     * to take place.
     */
    protected abstract int minOccurrencesRequiredToAlias();

    /**
     * Creates a node that defines the alias and attaches it to the parse tree.
     *
     * @param codeRoot The root of the script. Functions can be attached here,
     *     e.g., <code>function alias(p){throw p;}</code>.
     */
    protected abstract void insertAliasDeclaration(Node codeRoot);

    /** Replaces the node n with its alias. */
    protected abstract void aliasNode(Node n, Node parent);
  }

  /** Aliases throw statements with a function call. */
  // TODO(user): Generalize this to work with typeof expressions.
  private class ThrowAliasSpecification extends AliasSpecification {
    ThrowAliasSpecification(String aliasName) {
      super(aliasName, Token.THROW);
    }

    @Override
    protected void aliasNode(Node throwNode, Node parent) {
      Node name = NodeUtil.newName(
          compiler, getAliasName(), throwNode, getAliasName());
      Node aliasCall = IR.call(name, throwNode.removeFirstChild());
      aliasCall.putBooleanProp(Node.FREE_CALL, true);
      Node exprResult = IR.exprResult(aliasCall);
      parent.replaceChild(throwNode, exprResult);
    }

    @Override
    /**
     * Adds alias function to codeRoot. See {@link #createAliasFunctionNode}).
     */
    protected void insertAliasDeclaration(Node codeRoot) {
      codeRoot.addChildToFront(createAliasFunctionNode(getAliasName()));
    }

    @Override
    protected int minOccurrencesRequiredToAlias() {
      return MIN_OCCURRENCES_REQUIRED_TO_ALIAS_THROW;
    }
  }

  /**
   * Calculates the minimum number of occurrences of throw needed to alias
   * throw.
   */
  static int estimateMinOccurrencesRequriedToAlias() {
    // Assuming that the alias function name is two bytes in length, two bytes
    // will be saved per occurrence of throw:
    //   <code>throw e;</code>, compared to
    //   <code>TT(e);</code>.
    // However, the alias definition is some length, N, e.g.,
    //   <code>function TT(t){throw t;}</code>
    // Hence there must be more than N/2 occurrences of throw to reduce
    // the code size.
    Node alias = createAliasFunctionNode("TT");
    return InlineCostEstimator.getCost(alias) / 2 + 1;
  }

  /**
   * Creates a function node that takes a single argument, the object to
   * throw. The function throws the object.
   */
  private static Node createAliasFunctionNode(String aliasName) {
    final String paramName = "jscomp_throw_param";
    return IR.function(
        IR.name(aliasName),
        IR.paramList(IR.name(paramName)),
        IR.block(
            IR.throwNode(IR.name(paramName))));
  }

  /** Aliases literal keywords (e.g., null) with variable names. */
  private class KeywordAliasSpecification extends AliasSpecification {
    KeywordAliasSpecification(String aliasName, int tokenId) {
      super(aliasName, tokenId);
    }

    @Override
    protected int minOccurrencesRequiredToAlias() {
      return MIN_OCCURRENCES_REQUIRED_TO_ALIAS_LITERAL;
    }

    @Override
    protected void aliasNode(Node n, Node parent) {
      Node aliasNode = NodeUtil.newName(
          compiler, getAliasName(), n, getAliasName());
      parent.replaceChild(n, aliasNode);
    }

    @Override
    /**
     * Create the alias declaration (e.g. var $$ALIAS_NULL=null;).
     */
    protected void insertAliasDeclaration(Node codeRoot) {
      Node varNode = new Node(Token.VAR);
      Node value = new Node(getTokenId());
      Node name = NodeUtil.newName(
          compiler, getAliasName(), varNode, getAliasName());
      name.addChildToBack(value);
      varNode.addChildToBack(name);
      codeRoot.addChildrenToFront(varNode);
    }
  }

  /** Aliases literal keywords (e.g., null) with variable names. */
  private class VoidKeywordAliasSpecification extends AliasSpecification {
    VoidKeywordAliasSpecification(String aliasName, int tokenId) {
      super(aliasName, tokenId);
    }

    @Override
    public void visit(Node n, Node parent) {
      Node value = n.getFirstChild();
      if (value.isNumber() && value.getDouble() == 0) {
        super.visit(n, parent);
      }
    }

    @Override
    protected int minOccurrencesRequiredToAlias() {
      return MIN_OCCURRENCES_REQUIRED_TO_ALIAS_LITERAL;
    }

    @Override
    protected void aliasNode(Node n, Node parent) {
      Node aliasNode = NodeUtil.newName(
          compiler, getAliasName(), n, getAliasName());
      parent.replaceChild(n, aliasNode);
    }

    @Override
    /**
     * Create the alias declaration (e.g. var $$ALIAS_VOID=void 0;).
     */
    protected void insertAliasDeclaration(Node codeRoot) {
      Node varNode = new Node(Token.VAR);
      Node value = IR.voidNode(IR.number(0));
      Node name = NodeUtil.newName(
          compiler, getAliasName(), varNode, getAliasName());
      name.addChildToBack(value);
      varNode.addChildToBack(name);
      codeRoot.addChildrenToFront(varNode);
    }
  }


  static final String ALIAS_NULL = "JSCompiler_alias_NULL";
  static final String ALIAS_TRUE = "JSCompiler_alias_TRUE";
  static final String ALIAS_FALSE = "JSCompiler_alias_FALSE";
  static final String ALIAS_THROW = "JSCompiler_alias_THROW";
  static final String ALIAS_VOID = "JSCompiler_alias_VOID";

  /**
   * Don't alias a keyword unless it's referenced at least
   * MIN_OCCURRENCES_REQUIRED_TO_ALIAS_LITERAL times. Aliasing a keyword has a
   * cost (e.g. 'var XX=true;' costs 12 bytes). We make up for this
   * cost by replacing references to the keyword with variables that
   * have shorter names. If there are only a few references to a
   * keyword, the cost outweighs the benefit. It is not possible to
   * determine the exact break-even point without compiling twice
   * (once with aliasing, another without) and comparing the
   * post-gzipped size, so we define a minimum number of references
   * required in order to alias. We choose 6 because the alias cost is
   * ~7-12 bytes (12 bytes for 'var XX=true;', 7 bytes for a
   * subsequent declaration that does not require its own 'var ' or
   * semicolon, e.g. var XX=true,XY=null;), but each reference saves
   * 2-3 bytes (2 for true and null, 3 for false). Thus, the break
   * even point is 3 at best, and 6 at worst. We could use a
   * CostEstimator to be precise, but requiring a constant number of
   * occurrences is much simpler, and the added precision of a
   * CostEstimator would save us <10 bytes for some unlikely edge
   * cases (e.g. where false is referenced exactly 5 times, but does
   * not get aliased).
   */
  static final int MIN_OCCURRENCES_REQUIRED_TO_ALIAS_LITERAL = 6;

  /**
   * Don't alias throw statements unless throw is used at least
   * MIN_OCCURRENCES_REQUIRED_TO_ALIAS_THROW times.
   */
  static final int MIN_OCCURRENCES_REQUIRED_TO_ALIAS_THROW =
      estimateMinOccurrencesRequriedToAlias();

  /** Reference to JS Compiler */
  private final AbstractCompiler compiler;

  /** List of alias specifications, stored in order which transformations
   * should be applied. See {@link #createAliasSpecifications}.
   */
  private final List<AliasSpecification> aliasSpecifications;

  /** Map from rhino nodes to the corresponding AliasSpecification */
  private final Map<Integer, AliasSpecification> aliasTypes;

  /** Set of alias names. */
  private final Set<String> aliasNames;

  AliasKeywords(AbstractCompiler compiler) {
    this.compiler = compiler;
    aliasSpecifications = createAliasSpecifications();
    aliasTypes = Maps.newLinkedHashMap();
    aliasNames = Sets.newLinkedHashSet();
    for (AliasSpecification specification : aliasSpecifications) {
      aliasTypes.put(specification.getTokenId(), specification);
      aliasNames.add(specification.getAliasName());
    }
  }

  /**
   * Do all processing on the root node.
   */
  @Override
  public void process(Node externs, Node root) {
    // Find candidates to alias.
    NodeTraversal.traverse(compiler, root, new FindAliasableNodes());

    if (needsAliases()) {
      // Inject alias nodes for null, true, and false into the global scope.
      addAliasNodes(compiler.getNodeForCodeInsertion(null));

      // Update references to null/true/false with references to the aliases.
      for (AliasSpecification spec : aliasSpecifications) {
        spec.doAlias(compiler);
      }
    }
  }

  private boolean needsAliases() {
    for (AliasSpecification spec : aliasSpecifications) {
      if (!spec.nodes.isEmpty()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Inject alias nodes into the global scope. e.g.
   * var $$ALIAS_NULL=null,$$ALIAS_TRUE=true,$$ALIAS_FALSE=false;.
   */
  private void addAliasNodes(Node codeRoot) {
    boolean codeChanged = false;

    for (AliasSpecification spec : aliasSpecifications) {
      if (spec.maybeInsertAliasDeclarationIntoParseTree(codeRoot)) {
        codeChanged = true;
      }
    }

    if (codeChanged) {
      compiler.reportCodeChange();
    }
  }

  /**
   * Does the given node define one of our aliases?
   */
  private boolean isAliasDefinition(Node n) {
    if (!n.isName()) {
      return false;
    }

    if (!isAliasName(n.getString())) {
      // The given Node's string contents is not an alias. Skip it.
      return false;
    }

    /*
     * A definition must have a child node (otherwise it's just a
     * reference to the alias).
     */
    return n.getFirstChild() != null;
  }

  /**
   * Is this one of the Rhino token types that we're aliasing?
   */
  private boolean isAliasableType(int type) {
    return aliasTypes.containsKey(type);
  }

  /**
   * Is this one of our alias names?
   */
  private boolean isAliasName(String name) {
    return aliasNames.contains(name);
  }

  /**
   * Create the AliasSpecifications, one for each type we're aliasing. The
   * order of the elements in the list is significant. Transformations should
   * be applied in the given order.
   */
  private List<AliasSpecification> createAliasSpecifications() {
    List<AliasSpecification> l = Lists.newArrayList();
    l.add(new KeywordAliasSpecification(ALIAS_FALSE, Token.FALSE));
    l.add(new KeywordAliasSpecification(ALIAS_NULL, Token.NULL));
    l.add(new KeywordAliasSpecification(ALIAS_TRUE, Token.TRUE));
    l.add(new VoidKeywordAliasSpecification(ALIAS_VOID, Token.VOID));
    // Process throw nodes after literal keyword nodes. This is important when
    // a literal keyword is thrown (e.g., throw true;).
    // KeywordAliasSpecification needs to know what the parent of the node being
    // replaced with an alias is. Because ThrowAliasSpecification replaces the
    // parent of the node being aliased, ThrowAliasSpecification invalidates the
    // record of the node's parent that KeywordAliasSpecification stores.
    l.add(new ThrowAliasSpecification(ALIAS_THROW));
    return l;
  }
}
