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

import com.google.javascript.jscomp.AnalyzePrototypeProperties.NameInfo;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.Property;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.Symbol;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;

/**
 * Move prototype methods into later modules.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class CrossModuleMethodMotion implements CompilerPass {

  // Internal errors
  static final DiagnosticType NULL_COMMON_MODULE_ERROR = DiagnosticType.error(
      "JSC_INTERNAL_ERROR_MODULE_DEPEND",
      "null deepest common module");

  private final AbstractCompiler compiler;
  private final IdGenerator idGenerator;
  private final AnalyzePrototypeProperties analyzer;
  private final JSModuleGraph moduleGraph;
  private final boolean noStubFunctions;

  static final String STUB_METHOD_NAME = "JSCompiler_stubMethod";
  static final String UNSTUB_METHOD_NAME = "JSCompiler_unstubMethod";

  // Visible for testing
  static final String STUB_DECLARATIONS =
      "var JSCompiler_stubMap = [];" +
      "function JSCompiler_stubMethod(JSCompiler_stubMethod_id) {" +
      "  return function() {" +
      "    return JSCompiler_stubMap[JSCompiler_stubMethod_id].apply(" +
      "        this, arguments);" +
      "  };" +
      "}" +
      "function JSCompiler_unstubMethod(" +
      "    JSCompiler_unstubMethod_id, JSCompiler_unstubMethod_body) {" +
      "  return JSCompiler_stubMap[JSCompiler_unstubMethod_id] = " +
      "      JSCompiler_unstubMethod_body;" +
      "}";

  /**
   * Creates a new pass for moving prototype properties.
   * @param compiler The compiler.
   * @param idGenerator An id generator for method stubs.
   * @param canModifyExterns If true, then we can move prototype
   *     properties that are declared in the externs file.
   * @param noStubFunctions if true, we can move methods without
   *     stub functions in the parent module.
   */
  CrossModuleMethodMotion(AbstractCompiler compiler, IdGenerator idGenerator,
      boolean canModifyExterns, boolean noStubFunctions) {
    this.compiler = compiler;
    this.idGenerator = idGenerator;
    this.moduleGraph = compiler.getModuleGraph();
    this.analyzer = new AnalyzePrototypeProperties(compiler, moduleGraph,
        canModifyExterns, false);
    this.noStubFunctions = noStubFunctions;
  }

  @Override
  public void process(Node externRoot, Node root) {
    // If there are < 2 modules, then we will never move anything,
    // so we're done.
    if (moduleGraph != null && moduleGraph.getModuleCount() > 1) {
      analyzer.process(externRoot, root);
      moveMethods(analyzer.getAllNameInfo());
    }
  }

  /**
   * Move methods deeper in the module graph when possible.
   */
  private void moveMethods(Collection<NameInfo> allNameInfo) {
    boolean hasStubDeclaration = idGenerator.hasGeneratedAnyIds();
    for (NameInfo nameInfo : allNameInfo) {
      if (!nameInfo.isReferenced()) {
        // The code below can't do anything with unreferenced name
        // infos.  They should be skipped to avoid NPE since their
        // deepestCommonModuleRef is null.
        continue;
      }

      if (nameInfo.readsClosureVariables()) {
        continue;
      }

      JSModule deepestCommonModuleRef = nameInfo.getDeepestCommonModuleRef();
      if (deepestCommonModuleRef == null) {
        compiler.report(JSError.make(NULL_COMMON_MODULE_ERROR));
        continue;
      }

      Iterator<Symbol> declarations =
          nameInfo.getDeclarations().descendingIterator();
      while (declarations.hasNext()) {
        Symbol symbol = declarations.next();
        if (!(symbol instanceof Property)) {
          continue;
        }
        Property prop = (Property) symbol;

        // We should only move a property across modules if:
        // 1) We can move it deeper in the module graph, and
        // 2) it's a function, and
        // 3) it is not a GETTER_DEF or a SETTER_DEF, and
        // 4) the class is available in the global scope.
        //
        // #1 should be obvious. #2 is more subtle. It's possible
        // to copy off of a prototype, as in the code:
        // for (var k in Foo.prototype) {
        //   doSomethingWith(Foo.prototype[k]);
        // }
        // This is a common way to implement pseudo-multiple inheritance in JS.
        //
        // So if we move a prototype method into a deeper module, we must
        // replace it with a stub function so that it preserves its original
        // behavior.
        if (prop.getRootVar() == null || !prop.getRootVar().isGlobal()) {
          continue;
        }

        Node value = prop.getValue();
        // Only attempt to move normal functions.
        if (!value.isFunction()
            // A GET or SET can't be deferred like a normal
            // FUNCTION property definition as a mix-in would get the result
            // of a GET instead of the function itself.
            || value.getParent().isGetterDef()
            || value.getParent().isSetterDef()) {
          continue;
        }

        if (moduleGraph.dependsOn(deepestCommonModuleRef, prop.getModule())) {
          if (hasUnmovableRedeclaration(nameInfo, prop)) {
            // If it has been redeclared on the same object, skip it.
            continue;
          }

          Node valueParent = value.getParent();
          Node proto = prop.getPrototype();
          int stubId = idGenerator.newId();

          if (!noStubFunctions) {
            // example: JSCompiler_stubMethod(id);
            Node stubCall = IR.call(
                IR.name(STUB_METHOD_NAME),
                IR.number(stubId))
                .copyInformationFromForTree(value);
            stubCall.putBooleanProp(Node.FREE_CALL, true);

            // stub out the method in the original module
            // A.prototype.b = JSCompiler_stubMethod(id);
            valueParent.replaceChild(value, stubCall);

            // unstub the function body in the deeper module
            Node unstubParent = compiler.getNodeForCodeInsertion(
                deepestCommonModuleRef);
            Node unstubCall = IR.call(
                IR.name(UNSTUB_METHOD_NAME),
                IR.number(stubId),
                value);
            unstubCall.putBooleanProp(Node.FREE_CALL, true);
            unstubParent.addChildToFront(
                // A.prototype.b = JSCompiler_unstubMethod(id, body);
                IR.exprResult(
                    IR.assign(
                        IR.getprop(
                            proto.cloneTree(),
                            IR.string(nameInfo.name)),
                        unstubCall))
                    .copyInformationFromForTree(value));

            compiler.reportCodeChange();
          } else {
            Node assignmentParent = valueParent.getParent();
            valueParent.removeChild(value);
            // remove Foo.prototype.bar = value
            assignmentParent.getParent().removeChild(assignmentParent);

            Node destParent = compiler.getNodeForCodeInsertion(
                deepestCommonModuleRef);
            destParent.addChildToFront(
                // A.prototype.b = value;
                IR.exprResult(
                    IR.assign(
                        IR.getprop(
                            proto.cloneTree(),
                            IR.string(nameInfo.name)),
                        value))
                    .copyInformationFromForTree(value));
            compiler.reportCodeChange();
          }
        }
      }
    }

    if (!noStubFunctions && !hasStubDeclaration && idGenerator
        .hasGeneratedAnyIds()) {
      // Declare stub functions in the top-most module.
      Node declarations = compiler.parseSyntheticCode(STUB_DECLARATIONS);
      compiler.getNodeForCodeInsertion(null).addChildrenToFront(
          declarations.removeChildren());
    }
  }

  static boolean hasUnmovableRedeclaration(NameInfo nameInfo, Property prop) {
    for (Symbol symbol : nameInfo.getDeclarations()) {
      if (!(symbol instanceof Property)) {
        continue;
      }
      Property otherProp = (Property) symbol;
      // It is possible to do better here if the dependencies are well defined
      // but redefinitions are usually in optional modules so it isn't likely
      // worth the effort to check.
      if (prop != otherProp
          && prop.getRootVar() == otherProp.getRootVar()
          && prop.getModule() != otherProp.getModule()) {
        return true;
      }
    }
    return false;
  }

  static class IdGenerator implements Serializable {
    private static final long serialVersionUID = 0L;

    /**
     * Ids for cross-module method stubbing, so that each method has
     * a unique id.
     */
    private int currentId = 0;

    /**
     * Returns whether we've generated any new ids.
     */
    boolean hasGeneratedAnyIds() {
      return currentId != 0;
    }

    /**
     * Creates a new id for stubbing a method.
     */
    int newId() {
      return currentId++;
    }
  }
}
