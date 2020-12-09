/*
 * Copyright 2019 The Closure Compiler Authors.
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
/**
 * @fileoverview Datatypes used by "SourceMapperObjectParser.java" that cross
 * the handwritten/J2CL boundry.
 * @externs
 */

class com_google_debugging_sourcemap_SourceMapObjectParserJs$JsonMap {
  constructor() {
    /** @type {number} */ this.version;
    /** @type {string} */ this.file;
    /** @type {string} */ this.mappings;
    /** @type {string} */ this.sourceRoot;
    /**
     * @type {!Array<com_google_debugging_sourcemap_SourceMapObjectParserJs$Section>}
     */
    this.sections;
    /** @type {!Array<string>} */ this.sources;
    /** @type {!Array<string>} */ this.names;
  }
}

class com_google_debugging_sourcemap_SourceMapObjectParserJs$Section {
  constructor() {
    /**
     * @type {!com_google_debugging_sourcemap_SourceMapObjectParserJs$Offset}
     */
    this.offset;
    /** @type {string} */ this.url;
    /** @type {string} */ this.map;
  }
}

class com_google_debugging_sourcemap_SourceMapObjectParserJs$Offset {
  constructor() {
    /** @type {number} */ this.line;
    /** @type {number} */ this.column;
  }
}
