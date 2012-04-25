/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Set;

/**
 * Finds all method declarations and pulls them into data structures
 * for use during cleanups such as arity checks or inlining.
 *
 */
abstract class MethodCompilerPass implements CompilerPass {
  /** List of methods defined in externs */
  final Set<String> externMethods = Sets.newHashSet();

  /** List of extern methods without signatures that we can't warn about */
  final Set<String> externMethodsWithoutSignatures = Sets.newHashSet();

  /** List of property names that may not be methods */
  final Set<String> nonMethodProperties = Sets.newHashSet();

  // Use a linked map here to keep the output deterministic.  Otherwise,
  // the choice of method bodies is random when multiple identical definitions
  // are found which causes problems in the source maps.
  final Multimap<String, Node> methodDefinitions =
      LinkedHashMultimap.create();

  final AbstractCompiler compiler;

  /**
   * The signature storage is provided by the implementing class.
   */
  interface SignatureStore {
    public void reset();
    public void addSignature(
        String functionName, Node functionNode, String sourceFile);
    public void removeSignature(String functionName);
  }

  MethodCompilerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    externMethods.clear();
    externMethodsWithoutSignatures.clear();
    getSignatureStore().reset();
    methodDefinitions.clear();

    if (externs != null) {
      NodeTraversal.traverse(compiler, externs, new GetExternMethods());
    }


    List<Node> externsAndJs = Lists.newArrayList(externs, root);
    NodeTraversal.traverseRoots(
        compiler, Lists.newArrayList(externs, root), new GatherSignatures());
    NodeTraversal.traverseRoots(
        compiler, externsAndJs, getActingCallback());
  }

  /**
   * Subclasses should return a callback that does the actual work they
   * want to perform given the computed list of method signatures
   */
  abstract Callback getActingCallback();

  /**
   * Subclasses should return a SignatureStore for storing discovered
   * signatures.
   */
  abstract SignatureStore getSignatureStore();

  /**
   * Adds a node that may represent a function signature (if it's a function
   * itself or the name of a function).
   */
  private void addPossibleSignature(String name, Node node, NodeTraversal t) {
    if (node.isFunction()) {
      // The node we're looking at is a function, so we can add it directly
      addSignature(name, node, t.getSourceName());
    } else {
      nonMethodProperties.add(name);
    }
  }

  private void addSignature(String name, Node function, String fnSourceName) {
    if (externMethodsWithoutSignatures.contains(name)) {
      return;
    }

    getSignatureStore().addSignature(name, function, fnSourceName);
    methodDefinitions.put(name, function);
  }

  /**
   * Gathers methods from the externs file. Methods that are listed there but
   * do not have a signature are flagged to be ignored when doing arity checks.
   * Methods that do include signatures will be checked.
   */
  private class GetExternMethods extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
        case Token.GETELEM: {
          Node dest = n.getFirstChild().getNext();

          if (!dest.isString()) {
            return;
          }

          String name = dest.getString();

          // We have a signature. Parse tree of the form:
          // assign                       <- parent
          //      getprop                 <- n
          //          name methods
          //          string setTimeout
          //      function
          if (parent.isAssign() &&
              parent.getFirstChild() == n &&
              n.getNext().isFunction()) {
            addSignature(name, n.getNext(), t.getSourceName());
          } else {
            getSignatureStore().removeSignature(name);
            externMethodsWithoutSignatures.add(name);
          }

          externMethods.add(name);
        } break;

        case Token.OBJECTLIT: {
          for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
            Node value = key.getFirstChild();
            String name = key.getString();
            if (key.isStringKey()
                && value.isFunction()) {
              addSignature(name, value, t.getSourceName());
            } else {
              getSignatureStore().removeSignature(name);
              externMethodsWithoutSignatures.add(name);
            }
            externMethods.add(name);
          }
        } break;
      }
    }
  }

  /**
   * Gather signatures from the source to be compiled.
   */
  private class GatherSignatures extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
        case Token.GETELEM:
          Node dest = n.getFirstChild().getNext();

          if (dest.isString()) {
            if (dest.getString().equals("prototype")) {
              processPrototypeParent(t, parent);
            } else {
              // Static methods of the form Foo.bar = function() {} or
              // Static methods of the form Foo.bar = baz (where baz is a
              // function name). Parse tree looks like:
              // assign                 <- parent
              //      getprop           <- n
              //          name Foo
              //          string bar
              //      function or name  <- n.getNext()
              if (parent.isAssign() &&
                  parent.getFirstChild() == n) {
                addPossibleSignature(dest.getString(), n.getNext(), t);
              }
            }
          }
          break;

        case Token.OBJECTLIT:
          for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
            switch(key.getType()) {
              case Token.STRING_KEY:
                addPossibleSignature(key.getString(), key.getFirstChild(), t);
                break;
              case Token.SETTER_DEF:
              case Token.GETTER_DEF:
                nonMethodProperties.add(key.getString());
                break;
              default:
                throw new IllegalStateException(
                    "unexpect OBJECTLIT key: " + key);
            }
          }
          break;
      }
    }

    /**
     * Processes the parent of a GETPROP prototype, which can either be
     * another GETPROP (in the case of Foo.prototype.bar), or can be
     * an assignment (in the case of Foo.prototype = ...).
     */
    private void processPrototypeParent(NodeTraversal t, Node n) {
      switch (n.getType()) {
        // Foo.prototype.getBar = function() { ... } or
        // Foo.prototype.getBar = getBaz (where getBaz is a function)
        // parse tree looks like:
        // assign                          <- parent
        //     getprop                     <- n
        //         getprop
        //             name Foo
        //             string prototype
        //         string getBar
        //     function or name            <- assignee
        case Token.GETPROP:
        case Token.GETELEM:
          Node dest = n.getFirstChild().getNext();
          Node parent = n.getParent().getParent();

          if (dest.isString() &&
              parent.isAssign()) {
            Node assignee = parent.getFirstChild().getNext();

            addPossibleSignature(dest.getString(), assignee, t);
          }
          break;
      }
    }
  }
}
