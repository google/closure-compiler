/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.ijs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class to keep track of what has been seen so far in a given file.
 */
final class FileInfo {
  private final Set<String> providedNamespaces = new HashSet<>();
  private final Set<String> requiredLocalNames = new HashSet<>();
  @Deprecated
  final List<Node> constructorsToProcess = new ArrayList<>();
  private final ListMultimap<String, PotentialDeclaration> declarations =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();

  void recordDeclaration(Node qnameNode, Scope scope) {
    checkArgument(qnameNode.isQualifiedName(), qnameNode);
    String name =
        ConvertToTypedInterface.isThisProp(qnameNode)
            ? ConvertToTypedInterface.getPrototypeNameOfThisProp(qnameNode)
            : qnameNode.getQualifiedName();
    declarations.put(name, PotentialDeclaration.from(qnameNode, scope));
  }

  ListMultimap<String, PotentialDeclaration> getDeclarations() {
    return declarations;
  }

  void recordDefine(Node callNode, Scope scope) {
    checkArgument(NodeUtil.isCallTo(callNode, "goog.define"));
    String name = callNode.getSecondChild().getString();
    declarations.put(name, PotentialDeclaration.from(callNode, scope));
  }

  void recordDeclaration(String name, PotentialDeclaration decl) {
    declarations.put(name, decl);
  }

  void recordImport(String localName) {
    requiredLocalNames.add(localName);
  }

  boolean isNameDeclared(String fullyQualifiedName) {
    return declarations.containsKey(fullyQualifiedName);
  }

  static boolean containsPrefix(String fullyQualifiedName, Iterable<String> prefixNamespaces) {
    for (String prefix : prefixNamespaces) {
      if (fullyQualifiedName.equals(prefix) || fullyQualifiedName.startsWith(prefix + ".")) {
        return true;
      }
    }
    return false;
  }

  boolean isPrefixProvided(String fullyQualifiedName) {
    return containsPrefix(fullyQualifiedName, providedNamespaces);
  }

  boolean isPrefixRequired(String fullyQualifiedName) {
    return containsPrefix(fullyQualifiedName, requiredLocalNames);
  }

  boolean isStrictPrefixDeclared(String fullyQualifiedName) {
    for (String prefix : declarations.keySet()) {
      if (fullyQualifiedName.startsWith(prefix + ".")) {
        return true;
      }
    }
    return false;
  }

  void markConstructorToProcess(Node ctorNode) {
    checkArgument(ctorNode.isFunction(), ctorNode);
    constructorsToProcess.add(ctorNode);
  }

  void markProvided(String providedName) {
    checkNotNull(providedName);
    providedNamespaces.add(providedName);
  }
}
