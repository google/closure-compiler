package com.google.javascript.jscomp;

import java.util.Collections;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

public class WhitespaceWrapGoogModules implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;

  WhitespaceWrapGoogModules(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node c = root.getFirstChild(); c != null; c = c.getNext()) {
      Preconditions.checkState(c.isScript());
      hotSwapScript(c, null);
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    if (!isModuleFile(scriptRoot)) return;

    // As per ClosureBundler:
    /*
	      // add the prefix on the first line so the line numbers aren't affected.
	      out.append(
	          "goog.loadModule(function(exports) {"
	          + "'use strict';");
	      append(out, Mode.NORMAL, contents);
	      out.append(
	          "\n" // terminate any trailing single line comment.
	          + ";" // terminate any trailing expression.
	          + "return exports;});\n");
	      appendSourceUrl(out, Mode.NORMAL);
     */

    Node block = IR.block();
    for (Node c = scriptRoot.getFirstChild(); c != null; c = c.getNext()) {
      block.addChildToBack(c.cloneTree());
    }
    block.addChildToBack(IR.returnNode(IR.name("exports")));
    block.getFirstChild().setDirectives(Collections.singleton("use strict"));

    Node loadMod = IR.exprResult(IR.call(
        IR.getprop(IR.name("goog"), IR.string("loadModule")),
        IR.function(IR.name(""), IR.paramList(IR.name("exports")), block)));
    // ? .srcrefTree(srcref);

    scriptRoot.removeChildren();
    scriptRoot.addChildToBack(loadMod);
    compiler.reportCodeChange();
  }

  // from ClosureRewriteModule, TODO move to common area

  private static boolean isModuleFile(Node n) {
    return n.isScript() && n.hasChildren()
        && isGoogModuleCall(n.getFirstChild());
  }

  private static boolean isGoogModuleCall(Node n) {
    if (NodeUtil.isExprCall(n)) {
      Node target = n.getFirstChild().getFirstChild();
      return (target.matchesQualifiedName("goog.module"));
    }
    return false;
  }

}
