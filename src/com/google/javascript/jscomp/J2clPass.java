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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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

    List<InlineSpec> inlinings = new ArrayList<>();

    /*
     * Inline functions in Arrays that take references to static $isInstance() functions. This
     * ensures that the references will be fully qualified to work with collapse properties.
     */
    inlinings.add(
        new InlineSpec(
            "Arrays.impl.java.js",
            ImmutableSet.of(
                "$create",
                "$createWithInitializer",
                "$init",
                "$instanceIsOfType",
                "$castTo",
                "$stampType"),
            InliningMode.DIRECT));
    inlinings.add(
        new InlineSpec("Casts.impl.java.js", ImmutableSet.of("$to"), InliningMode.DIRECT));

    /*
     * Inlines all Interface.$markImplementor(FooClass) metaclass calls so that FooClass and others
     * like it are not unnecessarily retained and so that static analysis of interface instanceof
     * calls becomes possible.
     *
     * Note that this pass should NOT be restricted to j2cl .java.js files because JavaScript code
     * implementing Java interfaces (not recommended but widely used in xplat) needs calls to
     * $markImplementor.
     */
    inlinings.add(
        new InlineSpec(
            ALL_CLASS_FILE_NAMES, ImmutableSet.of("$markImplementor"), InliningMode.BLOCK));

    /*
     * Inlines class metadata calls so they become optimizable and avoids escaping of constructor.
     */
    inlinings.add(
        new InlineSpec(
            "Util.impl.java.js",
            ImmutableSet.of(
                "$setClassMetadata",
                "$setClassMetadataForInterface",
                "$setClassMetadataForEnum",
                "$setClassMetadataForPrimitive"),
            InliningMode.BLOCK));

    new ClassStaticFunctionsInliner(root, inlinings).run();
  }

  private record InlineSpec(
      String classFileName, Set<String> fnNamesToInline, InliningMode inliningMode) {}

  /**
   * Collects references to certain function definitions in a certain class and then inlines fully
   * qualified static method calls to those functions anywhere in the program.
   *
   * <p>Assumes that the set of provided short function names will not collide with any of the
   * collected fully qualified function names once the module prefix has been added.
   */
  private class ClassStaticFunctionsInliner {
    private final Map<String, InlinableFunction> fnsToInlineByQualifiedName = new LinkedHashMap<>();
    private final FunctionInjector injector;
    private final Node root;
    private final List<InlineSpec> inlineSpecs;

    private record InlinableFunction(Node impl, InliningMode inliningMode) {}

    private ClassStaticFunctionsInliner(Node root, List<InlineSpec> inlineSpecs) {
      this.root = root;
      this.inlineSpecs = inlineSpecs;
      this.injector =
          new FunctionInjector.Builder(compiler)
              .safeNameIdSupplier(safeNameIdSupplier)
              .assumeStrictThis(true)
              .assumeMinimumCapture(true)
              .build();
      ImmutableSet<String> fnNamesToInline =
          inlineSpecs.stream().flatMap(b -> b.fnNamesToInline.stream()).collect(toImmutableSet());
      this.injector.setKnownConstantFunctions(fnNamesToInline);
    }

    private void run() {
      NodeTraversal.traverse(compiler, root, new FunctionDefsCollector());
      NodeTraversal.traverse(compiler, root, new StaticCallInliner());
    }

    private ImmutableList<InlineSpec> findMatchingSpecsForFile(String sourceFileName) {
      ImmutableList.Builder<InlineSpec> matchingSpecs = ImmutableList.builder();
      for (InlineSpec inlineSpec : inlineSpecs) {
        if (inlineSpec.classFileName.equals(ALL_CLASS_FILE_NAMES)
            || sourceFileName.endsWith(inlineSpec.classFileName)) {
          matchingSpecs.add(inlineSpec);
        }
      }
      return matchingSpecs.build();
    }

    private class FunctionDefsCollector implements NodeTraversal.Callback {
      // This list is initialized every time the node traversal enters a new script. It contains
      // all InlineSpecs whose classFileName matches the script's filename.
      private ImmutableList<InlineSpec> currFileInlineSpecs;

      @Override
      public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
        if (n.isScript()) {
          this.currFileInlineSpecs = findMatchingSpecsForFile(n.getSourceFileName());
          return !this.currFileInlineSpecs.isEmpty();
        }
        return true;
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        // Only look at named function declarations that are fully qualified.
        final String fnName;
        switch (n.getToken()) {
          case ASSIGN -> {
            // TODO(b/69730966): Delete this branch when ES5 syntax support is no longer needed.
            if (!n.getLastChild().isFunction()) {
              return;
            }

            Node qualifiedNameNode = n.getFirstChild();
            if (!qualifiedNameNode.isGetProp() || !qualifiedNameNode.isQualifiedName()) {
              return;
            }
            fnName = qualifiedNameNode.getString();
          }
          case MEMBER_FUNCTION_DEF -> fnName = n.getString();
          default -> {
            return;
          }
        }
        for (InlineSpec inlineSpec : currFileInlineSpecs) {
          if (!inlineSpec.fnNamesToInline.contains(fnName)) {
            continue;
          }
          // Then store a reference to it.
          InlinableFunction inlinableFunction =
              new InlinableFunction(n.getLastChild(), inlineSpec.inliningMode);
          String qualifiedFnName =
              switch (n.getToken()) {
                case ASSIGN -> n.getFirstChild().getQualifiedName();
                case MEMBER_FUNCTION_DEF -> NodeUtil.getBestLValueName(n);
                default -> throw new AssertionError();
              };
          var previousValue = fnsToInlineByQualifiedName.put(qualifiedFnName, inlinableFunction);
          checkState(
              previousValue == null,
              "expected each function to match at most one InliningSpec, but found:\n%s\n%s",
              previousValue,
              inlinableFunction);
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
        InlinableFunction fn = fnsToInlineByQualifiedName.get(qualifiedFnName);
        if (fn == null) {
          return;
        }
        String fnName = qualifiedNameNode.getString();
        Node fnImpl = fn.impl();
        InliningMode inliningMode = fn.inliningMode();

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
