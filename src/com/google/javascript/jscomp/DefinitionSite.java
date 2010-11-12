/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.Node;

/**
 * Information about the context in which a Definition appears.
 * Includes the definition node, and context in which the definition
 * occurs - including the definition module.
 *
 */

class DefinitionSite {
  final Node node;
  final Definition definition;
  final JSModule module;
  final boolean inGlobalScope;
  final boolean inExterns;

  DefinitionSite(Node node,
                 Definition definition,
                 JSModule module,
                 boolean inGlobalScope,
                 boolean inExterns) {
    this.node = node;
    this.definition = definition;
    this.module = module;
    this.inGlobalScope = inGlobalScope;
    this.inExterns = inExterns;
  }
}
