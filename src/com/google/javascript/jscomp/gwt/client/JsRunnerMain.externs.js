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
 * @fileoverview Datatypes used by "JsRunnerMain.java" that cross
 * the handwritten/J2CL boundry.
 * @externs
 */

class com_google_javascript_jscomp_gwt_client_JsRunnerMain$Flags {
  constructor() {
    /** @type {boolean|undefined} */ this.angularPass;
    /** @type {boolean|undefined} */ this.applyInputSourceMaps;
    /** @type {boolean|undefined} */ this.assumeFunctionWrapper;
    /** @type {boolean|undefined} */ this.checksOnly;
    /** @type {!Array<String>|undefined} */ this.chunk;
    /** @type {!Array<String>|undefined} */ this.chunkWrapper;
    /** @type {string|undefined} */ this.chunkOutputPathPrefix;
    /** @type {string|undefined} */ this.compilationLevel;
    /** @type {*|undefined} */ this.createSourceMap;
    /** @type {boolean|undefined} */ this.dartPass;
    /** @type {boolean|undefined} */ this.debug;
    /** @type {!Array<String>|undefined} */ this.define;
    /** @type {string|undefined} */ this.dependencyMode;
    /** @type {!Array<String>|undefined} */ this.entryPoint;
    /** @type {string|undefined} */ this.env;
    /** @type {boolean|undefined} */ this.exportLocalPropertyDefinitions;
    /** @type {!Array<Object>|undefined} */ this.externs;
    /** @type {!Array<String>|undefined} */ this.extraAnnotationName;
    /** @type {!Array<String>|undefined} */ this.forceInjectLibraries;
    /** @type {!Array<String>|undefined} */ this.formatting;
    /** @type {boolean|undefined} */ this.generateExports;
    /** @type {!Array<String>|undefined} */ this.hideWarningsFor;
    /** @type {boolean|undefined} */ this.injectLibraries;
    /** @type {string|undefined} */ this.isolationMode;
    /** @type {!Array<String>|undefined} */ this.js;
    /** @type {!Array<String>|undefined} */ this.jscompError;
    /** @type {!Array<String>|undefined} */ this.jscompOff;
    /** @type {!Array<String>|undefined} */ this.jscompWarning;
    /** @type {!Array<String>|undefined} */ this.jsModuleRoot;
    /** @type {string|undefined} */ this.jsOutputFile;
    /** @type {string|undefined} */ this.languageIn;
    /** @type {string|undefined} */ this.languageOut;
    /** @type {string|undefined} */ this.moduleResolution;
    /** @deprecated @type {boolean|undefined} */ this.newTypeInf;
    /** @type {string|undefined} */ this.outputWrapper;
    /** @type {string|undefined} */ this.packageJsonEntryNames;
    /** @type {boolean|undefined} */ this.parseInlineSourceMaps;
    /** @deprecated @type {boolean|undefined} */ this.polymerPass;
    /** @type {number|undefined} */ this.polymerVersion;
    /** @type {boolean|undefined} */ this.preserveTypeAnnotations;
    /** @type {boolean|undefined} */ this.processClosurePrimitives;
    /** @type {boolean|undefined} */ this.processCommonJsModules;
    /** @type {boolean|undefined} */ this.renaming;
    /** @type {string|undefined} */ this.renamePrefixNamespace;
    /** @type {string|undefined} */ this.renameVariablePrefix;
    /** @type {boolean|undefined} */ this.rewritePolyfills;
    /** @type {boolean|undefined} */ this.sourceMapIncludeContent;
    /** @type {boolean|undefined} */ this.strictModeInput;
    /** @type {string|undefined} */ this.tracerMode;
    /** @type {boolean|undefined} */ this.useTypesForOptimization;
    /** @type {string|undefined} */ this.warningLevel;

    // These flags do not match the Java compiler JAR.
    /** @deprecated @type {!Array<  File>|undefined} */ this.jsCode;
    /** @type {!Object|undefined} */ this.defines;
  }
}

class com_google_javascript_jscomp_gwt_client_JsRunnerMain$File {
  constructor() {
    /** @type {string|undefined} */ this.path;
    /** @type {string|undefined} */ this.src;
    /** @type {string|undefined} */ this.sourceMap;
    /** @type {string|undefined} */ this.webpackId;
  }
}

class com_google_javascript_jscomp_gwt_client_JsRunnerMain$ChunkOutput {
  constructor() {
    /** @deprecated @type {string|undefined} */ this.compiledCode;
    /** @deprecated @type {string|undefined} */ this.sourceMap;
    /**
     * @type{!Array<com_google_javascript_jscomp_gwt_client_JsRunnerMain$File>|undefined}
     */
    this.compiledFiles;
    /** @type{!Array<!Object>|undefined} */ this.errors;
    /** @type{!Array<!Object>|undefined} */ this.warnings;
  }
}
