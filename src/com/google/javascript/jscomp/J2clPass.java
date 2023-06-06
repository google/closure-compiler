/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A normalization pass to inline some J2CL calls to enable other optimizations. */
public class J2clPass implements CompilerPass {
  private static final String ALL_CLASS_FILE_NAMES = "*";
  private final AbstractCompiler compiler;
  private final Supplier<String> safeNameIdSupplier;

  public J2clPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.safeNameIdSupplier = compiler.getUniqueNameIdSupplier();
  }

  @Override
  public void process(Node externs, Node root) {
    if (!J2clSourceFileChecker.shouldRunJ2clPasses(compiler)) {
      return;
    }

    /*
     * Inline functions in Arrays that take references to static $isInstance() functions. This
     * ensures that the references will be fully qualified to work with collapse properties.
     */
    inlineFunctionsInFile(
        root,
        "Arrays.impl.java.js",
        ImmutableSet.of(
            "$create",
            "$createWithInitializer",
            "$init",
            "$instanceIsOfType",
            "$castTo",
            "$stampType"),
        InliningMode.DIRECT);
    inlineFunctionsInFile(root, "Casts.impl.java.js", ImmutableSet.of("$to"), InliningMode.DIRECT);

    /*
     * Inlines all Interface.$markImplementor(FooClass) metaclass calls so that FooClass and others
     * like it are not unnecessarily retained and so that static analysis of interface instanceof
     * calls becomes possible.
     *
     * Note that this pass should NOT be restricted to j2cl .java.js files because JavaScript code
     * implementing Java interfaces (not recommended but widely used in xplat) needs calls to
     * $markImplementor.
     */
    inlineFunctionsInFile(
        root, ALL_CLASS_FILE_NAMES, ImmutableSet.of("$markImplementor"), InliningMode.BLOCK);

    /*
     * Inlines class metadata calls so they become optimizable and avoids escaping of constructor.
     */
    inlineFunctionsInFile(
        root,
        "Util.impl.java.js",
        ImmutableSet.of(
            "$setClassMetadata",
            "$setClassMetadataForInterface",
            "$setClassMetadataForEnum",
            "$setClassMetadataForPrimitive"),
        InliningMode.BLOCK);
  }

  private void inlineFunctionsInFile(
      Node root, String classFileName, Set<String> fnNamesToInline, InliningMode inliningMode) {
    new ClassStaticFunctionsInliner(root, classFileName, fnNamesToInline, inliningMode).run();
  }

  /**
   * Collects references to certain function definitions in a certain class and then inlines fully
   * qualified static method calls to those functions anywhere in the program.
   *
   * <p>Assumes that the set of provided short function names will not collide with any of the
   * collected fully qualified function names once the module prefix has been added.
   */
  private class ClassStaticFunctionsInliner {
    private final String classFileName;
    private final Set<String> fnNamesToInline;
    private final InliningMode inliningMode;
    private final Map<String, Node> fnsToInlineByQualifiedName = new HashMap<>();
    private final FunctionInjector injector;
    private final Node root;

    private ClassStaticFunctionsInliner(
        Node root, String classFileName, Set<String> fnNamesToInline, InliningMode inliningMode) {
      this.root = root;
      this.classFileName = classFileName;
      this.fnNamesToInline = fnNamesToInline;
      this.inliningMode = inliningMode;

      this.injector =
          new FunctionInjector.Builder(compiler)
              .safeNameIdSupplier(safeNameIdSupplier)
              .assumeStrictThis(true)
              .assumeMinimumCapture(true)
              .build();
      this.injector.setKnownConstantFunctions(ImmutableSet.copyOf(fnNamesToInline));
    }

    private void run() {
      NodeTraversal.traverse(compiler, root, new FunctionDefsCollector());
      NodeTraversal.traverse(compiler, root, new StaticCallInliner());
    }

    private class FunctionDefsCollector implements NodeTraversal.Callback {
      @Override
      public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
        // Only look inside the referenced class file.
        return !n.isScript()
            || n.getSourceFileName().endsWith(classFileName)
            || classFileName.equals(ALL_CLASS_FILE_NAMES);
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        // If we arrive here then we're already inside the desired script.

        // Only look at named function declarations that are fully qualified.
        final String qualifiedFnName;
        final String fnName;
        switch (n.getToken()) {
          case ASSIGN:
            // TODO(b/69730966): Delete this branch when ES5 syntax support is no longer needed.
            if (!n.getLastChild().isFunction()) {
              return;
            }

            Node qualifiedNameNode = n.getFirstChild();
            if (!qualifiedNameNode.isGetProp() || !qualifiedNameNode.isQualifiedName()) {
              return;
            }

            qualifiedFnName = qualifiedNameNode.getQualifiedName();
            fnName = qualifiedNameNode.getString();
            break;

          case MEMBER_FUNCTION_DEF:
            qualifiedFnName = NodeUtil.getBestLValueName(n);
            fnName = n.getString();
            break;

          default:
            return;
        }

        if (fnNamesToInline.contains(fnName)) {
          // Then store a reference to it.
          fnsToInlineByQualifiedName.put(qualifiedFnName, n.getLastChild());
        }
      }
    }

    private class StaticCallInliner extends AbstractPostOrderCallback {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        // Only look at method calls
        if (!n.isCall()) {
          return;
        }

        // ... that are fully qualified
        Node qualifiedNameNode = n.getFirstChild();
        if (!qualifiedNameNode.isGetProp() || !qualifiedNameNode.isQualifiedName()) {
          return;
        }

        // ... and that reference a function definition we want to inline
        String qualifiedFnName = qualifiedNameNode.getQualifiedName();
        String fnName = qualifiedNameNode.getString();
        Node fnImpl = fnsToInlineByQualifiedName.get(qualifiedFnName);
        if (fnImpl == null) {
          return;
        }

        // Ensure that the function only has a single return statement when direct inlining.
        if (inliningMode == InliningMode.DIRECT
            && !NodeUtil.getFunctionBody(fnImpl).getFirstChild().isReturn()) {
          throw new IllegalStateException(
              "Attempted to direct inline function "
                  + qualifiedFnName
                  + ", but function is not a simple return.");
        }

        // Otherwise inline the call.
        // Note: This pass has to run before normalization, so we must use the unsafeInline method.
        // It is safe because these are strictly controlled trivial bootstrap methods that are
        // written with inlining in mind (e.g. doesn't read/write local/global variables).
        // TODO(goktug): Add a check that will ensure safety of this.
        Node inlinedCall =
            injector.unsafeInline(
                new Reference(n, t.getScope(), t.getChunk(), inliningMode), fnName, fnImpl);
        // Avoid overridding original source information with the helper classes source information.
        // For example; we want a cast to point related Java statement instead of the Casts utility.
        inlinedCall.srcrefTree(n);
        t.getCompiler().reportChangeToEnclosingScope(inlinedCall);
      }
    }
  }
}
