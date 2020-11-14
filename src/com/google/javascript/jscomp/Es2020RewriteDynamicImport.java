package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;

public class Es2020RewriteDynamicImport implements CompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;
  private boolean hasNotified = false;

  static final DiagnosticType DYNAMIC_IMPORT_EXPERIMENTAL =
      DiagnosticType.warning(
          "JSC_DYNAMIC_IMPORT_EXPERIMENTAL",
          "Dynamic import support is experimental. No module rewriting is performed.");

  public Es2020RewriteDynamicImport(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case DYNAMIC_IMPORT:
        if (!hasNotified) {
          hasNotified = true;
          t.report(n, DYNAMIC_IMPORT_EXPERIMENTAL);
        }
        break;
      default:
        break;
    }
  }
}
