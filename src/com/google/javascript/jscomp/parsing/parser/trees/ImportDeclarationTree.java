/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser.trees;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.IdentifierToken;
import com.google.javascript.jscomp.parsing.parser.LiteralToken;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

public class ImportDeclarationTree extends ParseTree {
  // The identifier for the "default" import from the module.
  public final IdentifierToken defaultBindingIdentifier;

  // The list of names imported from the module when using the
  // `import {...} from 'specifier'` form.
  public final ImmutableList<ParseTree> importSpecifierList;

  // The namespace into which imported names are put when using the
  // `import * as nameSpaceImportIdentifier from 'specifier'` form.
  public final IdentifierToken nameSpaceImportIdentifier;

  // The string identifying the module.
  public final LiteralToken moduleSpecifier;

  public ImportDeclarationTree(SourceRange location,
      IdentifierToken defaultBindingIdentifier,
      ImmutableList<ParseTree> importSpecifierList,
      IdentifierToken nameSpaceImportIdentifier,
      LiteralToken moduleSpecifier) {
    super(ParseTreeType.IMPORT_DECLARATION, location);
    this.defaultBindingIdentifier = defaultBindingIdentifier;
    this.importSpecifierList = importSpecifierList;
    this.nameSpaceImportIdentifier = nameSpaceImportIdentifier;
    this.moduleSpecifier = moduleSpecifier;
  }
}
