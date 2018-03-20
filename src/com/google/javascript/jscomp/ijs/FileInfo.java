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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Set;

/**
 * Class to keep track of what has been seen so far in a given file.
 */
final class FileInfo {
  private final Set<String> providedNamespaces = new HashSet<>();
  private final Set<String> requiredLocalNames = new HashSet<>();
  private final ListMultimap<String, PotentialDeclaration> declarations =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();

  void recordNameDeclaration(Node qualifiedNameNode) {
    recordDeclaration(PotentialDeclaration.fromName(qualifiedNameNode));
  }

  void recordMethod(Node functionNode) {
    recordDeclaration(PotentialDeclaration.fromMethod(functionNode));
  }

  void recordStringKeyDeclaration(Node stringKeyNode) {
    recordDeclaration(PotentialDeclaration.fromStringKey(stringKeyNode));
  }

  void recordDefine(Node callNode) {
    recordDeclaration(PotentialDeclaration.fromDefine(callNode));
  }

  ListMultimap<String, PotentialDeclaration> getDeclarations() {
    return declarations;
  }

  void recordDeclaration(PotentialDeclaration decl) {
    declarations.put(decl.getFullyQualifiedName(), decl);
  }

  void recordImport(String localName) {
    requiredLocalNames.add(localName);
  }

  boolean isNameDeclared(String fullyQualifiedName) {
    return declarations.containsKey(fullyQualifiedName);
  }

  private static boolean containsPrefix(
      String fullyQualifiedName, Iterable<String> prefixNamespaces) {
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

  void markProvided(String providedName) {
    checkNotNull(providedName);
    providedNamespaces.add(providedName);
  }
}
