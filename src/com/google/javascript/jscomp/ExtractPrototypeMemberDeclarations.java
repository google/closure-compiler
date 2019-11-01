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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * When there are multiple prototype member declarations to the same class,
 * use a temp variable to alias the prototype object.
 *
 * Example:
 *
 * <pre>
 * function B() { ... }                 \
 * B.prototype.foo = function() { ... }  \___ {@link ExtractionInstance}
 * ...                                   /
 * B.prototype.bar = function() { ... } /
 *          ^---------------------------------{@link PrototypeMemberDeclaration}
 * </pre>
 * <p>becomes
 * <pre>
 * function B() { ... }
 * x = B.prototype;
 * x.foo = function() { ... }
 * ...
 * x.bar = function() { ... }
 * </pre>
 *
 * <p> Works almost like a redundant load elimination but limited to only
 * recognizing the class prototype declaration idiom. First it only works within
 * a basic block because we avoided {@link DataFlowAnalysis} for compilation
 * performance. Secondly, we can avoid having to compute how long to
 * sub-expressing has to be. Example:
 * <pre>
 * a.b.c.d = ...
 * a.b.c = ...
 * a.b = ...
 * a.b.c = ...
 * </pre>
 * <p> Further more, we only introduce one temp variable to hold a single
 * prototype at a time. So all the {@link PrototypeMemberDeclaration}
 * to be extracted must be in a single line. We call this a single
 * {@link ExtractionInstance}.
 *
 * <p>Alternatively, for users who do not want a global variable to be
 * introduced, we will create an anonymous function instead.
 * <pre>
 * function B() { ... }
 * (function (x) {
 *   x.foo = function() { ... }
 *   ...
 *   x.bar = function() { ... }
 * )(B.prototype)
 * </pre>
 *
 * The RHS of the declarations can have side effects, however, one good way to
 * break this is the following:
 * <pre>
 * function B() { ... }
 * B.prototype.foo = (function() { B.prototype = somethingElse(); return 0 })();
 * ...
 * </pre>
 * Such logic is highly unlikely and we will assume that it never occurs.
 */
class ExtractPrototypeMemberDeclarations implements CompilerPass {

  // The name of variable that will temporary hold the pointer to the prototype
  // object. Of cause, we assume that it'll be renamed by RenameVars.
  private static final String PROTOTYPE_ALIAS = "JSCompiler_prototypeAlias";

  private final AbstractCompiler compiler;

  private final Pattern pattern;

  enum Pattern {
    USE_GLOBAL_TEMP(
        // Global Overhead.
        // We need a temp variable to hold all the prototype.
        "var t;".length(),
        // Per Extract overhead:
        // Every extraction instance must first use the temp variable to point
        // to the prototype object.
        "t=y.prototype;".length(),
        // TODO(user): Check to to see if AliasExterns is on
        // The gain we get per prototype declaration. Assuming it can be
        // aliased.
        "t.y=".length() - "x[p].y=".length()),

    USE_IIFE(
       // Global Overhead:
       0,
       // Per-extraction overhead:
       // This is the cost of a single anoynmous function.
       "(function(t){})(y.prototype);".length(),
       // Per-prototype member declaration overhead:
       // Here we assumes that they don't have AliasExterns on (in SIMPLE mode).
       "t.y=".length() - "x.prototype.y=".length());


    private final int globalOverhead;
    private final int perExtractionOverhead;
    private final int perMemberOverhead;

    Pattern(int globalOverHead, int perExtractionOverhead, int perMemberOverhead) {
      this.globalOverhead = globalOverHead;
      this.perExtractionOverhead = perExtractionOverhead;
      this.perMemberOverhead = perMemberOverhead;
    }
  }

  ExtractPrototypeMemberDeclarations(AbstractCompiler compiler, Pattern pattern) {
    this.compiler = compiler;
    this.pattern = pattern;
  }

  @Override
  public void process(Node externs, Node root) {
    GatherExtractionInfo extractionInfo = new GatherExtractionInfo();
    NodeTraversal.traverse(compiler, root, extractionInfo);
    if (extractionInfo.shouldExtract()) {
      doExtraction(extractionInfo);
    }
  }

  /**
   * Declares the temp variable to point to prototype objects and iterates
   * through all ExtractInstance and performs extraction there.
   */
  private void doExtraction(GatherExtractionInfo info) {
    // Insert a global temp if we are using the USE_GLOBAL_TEMP pattern.
    if (pattern == Pattern.USE_GLOBAL_TEMP) {
      Node injectionPoint = compiler.getNodeForCodeInsertion(null);

      Node var = NodeUtil.newVarNode(PROTOTYPE_ALIAS, null)
          .useSourceInfoIfMissingFromForTree(injectionPoint);

      injectionPoint.addChildToFront(var);
      compiler.reportChangeToEnclosingScope(var);
    }
    // Go through all extraction instances and extract each of them.
    for (ExtractionInstance instance : info.instances) {
      extractInstance(instance);
    }
  }

  /**
   * At a given ExtractionInstance, stores and prototype object in the temp
   * variable and rewrite each member declaration to assign to the temp variable
   * instead.
   */
  private void extractInstance(ExtractionInstance instance) {
    PrototypeMemberDeclaration first = instance.declarations.get(0);
    String className = first.qualifiedClassName;
    if (pattern == Pattern.USE_GLOBAL_TEMP) {
      // Use the temp variable to hold the prototype.
      Node classNameNode = NodeUtil.newQName(compiler, className);
      classNameNode.putBooleanProp(Node.IS_CONSTANT_NAME, first.constant);
      Node stmt =
          IR.exprResult(
              IR.assign(
                  IR.name(PROTOTYPE_ALIAS),
                  IR.getprop(
                      classNameNode, IR.string("prototype"))))
          .useSourceInfoIfMissingFromForTree(first.node);

      instance.parent.addChildBefore(stmt, first.node);
      compiler.reportChangeToEnclosingScope(stmt);
    } else if (pattern == Pattern.USE_IIFE) {
      Node block = IR.block();
      Node func = IR.function(
           IR.name(""),
           IR.paramList(IR.name(PROTOTYPE_ALIAS)),
           block);

      Node call = IR.call(func,
           NodeUtil.newQName(
               compiler, className + ".prototype",
               instance.parent, className + ".prototype"));
      call.putIntProp(Node.FREE_CALL, 1);

      Node stmt = IR.exprResult(call);
      stmt.useSourceInfoIfMissingFromForTree(first.node);
      instance.parent.addChildBefore(stmt, first.node);
      compiler.reportChangeToEnclosingScope(stmt);
      for (PrototypeMemberDeclaration declar : instance.declarations) {
        compiler.reportChangeToEnclosingScope(declar.node);
        block.addChildToBack(declar.node.detach());
      }
    }
    // Go through each member declaration and replace it with an assignment
    // to the prototype variable.
    for (PrototypeMemberDeclaration declar : instance.declarations) {
      replacePrototypeMemberDeclaration(declar);
    }
  }

  /**
   * Replaces a member declaration to an assignment to the temp prototype
   * object.
   */
  private void replacePrototypeMemberDeclaration(PrototypeMemberDeclaration declar) {
    // x.prototype.y = ...  ->  t.y = ...
    Node assignment = declar.node.getFirstChild();
    Node lhs = assignment.getFirstChild();
    Node name = NodeUtil.newQName(
        compiler,
        PROTOTYPE_ALIAS + "." + declar.memberName, declar.node,
        declar.memberName);

    // Save the full prototype path on the left hand side of the assignment for debugging purposes.
    // declar.lhs = x.prototype.y so first child of the first child is 'x'.
    Node accessNode = declar.lhs.getFirstFirstChild();
    String originalName = accessNode.getOriginalName();
    String className = originalName != null ? originalName : "?";
    name.getFirstChild().useSourceInfoFromForTree(lhs);
    name.putBooleanProp(Node.IS_CONSTANT_NAME, lhs.getBooleanProp(Node.IS_CONSTANT_NAME));
    name.getFirstChild().setOriginalName(className + ".prototype");

    assignment.replaceChild(lhs, name);
    compiler.reportChangeToEnclosingScope(name);
  }

  /**
   * Collects all the possible extraction instances in a node traversal.
   */
  private class GatherExtractionInfo extends AbstractShallowCallback {

    private final List<ExtractionInstance> instances = new ArrayList<>();
    private int totalDelta = pattern.globalOverhead;

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {

      if (!n.isScript() && !n.isBlock()) {
        return;
      }

      for (Node cur = n.getFirstChild(); cur != null; cur = cur.getNext()) {
        PrototypeMemberDeclaration prototypeMember =
            PrototypeMemberDeclaration.extractDeclaration(cur);
        if (prototypeMember == null) {
          continue;
        }

        // Found a good site here. The constructor will computes the chain of
        // declarations that is qualified for extraction.
        ExtractionInstance instance = new ExtractionInstance(prototypeMember, n);
        cur = instance.declarations.get(instance.declarations.size() - 1).node;

        // Only add it to our work list if the extraction at this instance makes the code smaller.
        if (instance.isFavorable()) {
          instances.add(instance);
          totalDelta += instance.delta;
        }
      }
    }

    /**
     * @return {@code true} if the sum of all the extraction instance gain
     * outweighs the overhead of the temp variable declaration.
     */
    private boolean shouldExtract() {
      return totalDelta < 0;
    }
  }

  private class ExtractionInstance {
    List<PrototypeMemberDeclaration> declarations = new ArrayList<>();
    private int delta = 0;
    private final Node parent;

    private ExtractionInstance(PrototypeMemberDeclaration head, Node parent) {
      this.parent = parent;
      declarations.add(head);
      delta = pattern.perExtractionOverhead + pattern.perMemberOverhead;

      for (Node cur = head.node.getNext(); cur != null; cur = cur.getNext()) {

        // We can skip over any named functions because they have no effect on
        // the control flow. In fact, they are lifted to the beginning of the
        // block. This happens a lot when devirtualization breaks the whole chain.
        if (cur.isFunction()) {
          continue;
        }

        PrototypeMemberDeclaration prototypeMember =
            PrototypeMemberDeclaration.extractDeclaration(cur);
        if (prototypeMember == null || !head.isSameClass(prototypeMember)) {
          break;
        }
        declarations.add(prototypeMember);
        delta += pattern.perMemberOverhead;
      }
    }

    /**
     * @return {@code true} if extracting all the declarations at this instance
     * will overweight the overhead of aliasing the prototype object.
     */
    boolean isFavorable() {
      return delta <= 0;
    }
  }

  /**
   * Abstraction for a prototype member declaration.
   *
   * <p>{@code a.b.c.prototype.d = ....}
   */
  private static class PrototypeMemberDeclaration {
    final String memberName;
    final Node node;
    final String qualifiedClassName;
    final Node lhs;
    final boolean constant;

    private PrototypeMemberDeclaration(Node lhs, Node node) {
      checkState(NodeUtil.isExprAssign(node), node);
      this.lhs = lhs;
      this.memberName = NodeUtil.getPrototypePropertyName(lhs);
      this.node = node;
      Node classNode = getPrototypeClassName(lhs);
      this.qualifiedClassName = classNode.getQualifiedName();
      this.constant = classNode.getBooleanProp(Node.IS_CONSTANT_NAME);
    }

    private boolean isSameClass(PrototypeMemberDeclaration other) {
      return qualifiedClassName.equals(other.qualifiedClassName);
    }

    private static Node getPrototypeClassName(Node qName) {
      Node cur = qName;
      while (cur.isGetProp()) {
        if (cur.getLastChild().getString().equals("prototype")) {
          return cur.getFirstChild();
        } else {
          cur = cur.getFirstChild();
        }
      }
      return null;
    }

    private static boolean isPrototypePropertyDeclaration(Node n) {
      if (!NodeUtil.isExprAssign(n)) {
        return false;
      }
      Node lvalue = n.getFirstFirstChild();
      if (lvalue.isGetProp()) {
        Node cur = lvalue.getFirstChild();
        while (cur.isGetProp()) {
          if (cur.getLastChild().getString().equals("prototype")) {
            return cur.isQualifiedName();
          }
          cur = cur.getFirstChild();
        }
      }
      return false;
    }

    /**
     * @return A prototype member declaration representation if there is one
     * else it returns {@code null}.
     */
    @Nullable
    private static PrototypeMemberDeclaration extractDeclaration(Node n) {
      if (!isPrototypePropertyDeclaration(n)) {
        return null;
      }
      Node lhs = n.getFirstFirstChild();
      return new PrototypeMemberDeclaration(lhs, n);
    }
  }
}
