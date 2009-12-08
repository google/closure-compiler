/*
 * Copyright 2006 Google Inc.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.FunctionCheck.FunctionInfo;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;

import java.util.Collection;

/**
 * Checks method arity. Looks for the following ways of defining methods:
 *
 * <pre>
 * Foo.prototype.bar = function() {};
 * Foo.prototype = {
 *  bar: function() {}
 * };
 * Foo.bar = function() {};
 * </pre>
 *
 * <p>Methods with the same name with different signatures are handled, it is
 * assumed that callers have to match at least one of the signatures.</p>
 *
*
 */
class MethodCheck extends MethodCompilerPass {

  private final CheckLevel level;

  /** Map from method names to possible signatures */
  final Multimap<String,FunctionInfo> methodSignatures =
      HashMultimap.create();

  final MethodCompilerPass.SignatureStore signatureCallback = new Store();

  MethodCheck(AbstractCompiler compiler, CheckLevel level) {
    super(compiler);
    this.level = level;
  }

  /**
   * Checks method calls based on signatures that have been gathered. Only
   * calls of the form foo.bar() will be checked (as opposed to foo["bar"]).
   */
  private class CheckUsage extends InvocationsCallback {

    @Override
    void visit(NodeTraversal t, Node callNode, Node parent, String callName) {
      if (externMethodsWithoutSignatures.contains(callName)) {
        return;
      }

      Collection<FunctionInfo> signatures = methodSignatures.get(callName);

      if (signatures.isEmpty()) {
        // Unfortunately we can't warn directly here since we still can't catch
        // all of the places where object methods are defined, like in arbitrary
        // object literals and as inline properties
        return;
      }

      FunctionCheck.checkCall(callNode, callName, signatures, t, level);
    }
  }

  @Override
  protected Callback getActingCallback() {
    return new CheckUsage();
  }

  @Override
  SignatureStore getSignatureStore() {
    return this.signatureCallback;
  }

  /**
   * Maintains the methodSignatures map.
   */
  private class Store implements MethodCompilerPass.SignatureStore {
    @Override
    public void addSignature(
        String functionName, Node functionNode, String sourceFile) {
      methodSignatures.put(functionName,
          FunctionCheck.createFunctionInfo(compiler, functionNode, sourceFile));
    }

    @Override
    public void removeSignature(String functionName) {
      // No signature, we remove any ones we've already found and add
      // it to the list of methods to skip checks for
      if (methodSignatures.containsKey(functionName)) {
        methodSignatures.removeAll(functionName);
      }
    }

    public void reset() {
      methodSignatures.clear();
    }
  }
}
