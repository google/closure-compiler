/*
 * Copyright 2017 The Closure Compiler Authors
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
 * @fileoverview Definitions for WebAssembly JS API
 *
 *  @see http://webassembly.org/docs/js/
 *
 * @externs
 * @author loorongjie@gmail.com (Loo Rong Jie)
 */

 /**
 * @const
 */
var WebAssembly = {};

/**
 * @record
 * @see https://webassembly.github.io/spec/js-api/#dictdef-webassemblycompileoptions
 */
WebAssembly.WebAssemblyCompileOptions = function() {};

/**
 * @type {!Array<string>|undefined}
 */
WebAssembly.WebAssemblyCompileOptions.prototype.builtins;

/**
 * @type {string|null|undefined}
 */
WebAssembly.WebAssemblyCompileOptions.prototype.importedStringConstants;

/**
 * @record
 * @see https://webassembly.github.io/spec/js-api/#dictdef-webassemblyinstantiatedsource
 */
WebAssembly.WebAssemblyInstantiatedSource = function() {};

/** @type {!WebAssembly.Instance} */
WebAssembly.WebAssemblyInstantiatedSource.prototype.instance;

/** @type {!WebAssembly.Module} */
WebAssembly.WebAssemblyInstantiatedSource.prototype.module;

/**
 * @constructor
 * @param {!BufferSource} bytes
 * @param {!WebAssembly.WebAssemblyCompileOptions=} opt_options
 */
WebAssembly.Module = function(bytes, opt_options) {};

/**
 * @constructor
 * @param {!WebAssembly.Module} moduleObject
 * @param {!WebAssembly.Imports=} importObject
 */
WebAssembly.Instance = function(moduleObject, importObject) {};

/**
 * @record
 * @see https://webassembly.github.io/spec/js-api/#dictdef-memorydescriptor
 */
WebAssembly.MemoryDescriptor = function() {};

/** @type {(!WebAssembly.AddressType|undefined)} */
WebAssembly.MemoryDescriptor.prototype.address;

/** @type {!WebAssembly.AddressValue} */
WebAssembly.MemoryDescriptor.prototype.initial;

/** @type {(!WebAssembly.AddressValue|undefined)} */
WebAssembly.MemoryDescriptor.prototype.maximum;

/** @type {(boolean|undefined)} */
WebAssembly.MemoryDescriptor.prototype.shared;

/**
 * @constructor
 * @param {!WebAssembly.MemoryDescriptor} memoryDescriptor
 * @see https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/JavaScript_interface/Memory
 */
WebAssembly.Memory = function(memoryDescriptor) {};

/**
 * @record
 * @see https://webassembly.github.io/spec/js-api/#dictdef-tabledescriptor
 */
WebAssembly.TableDescriptor = function() {};

/** @type {(!WebAssembly.AddressType|undefined)} */
WebAssembly.TableDescriptor.prototype.address;

/** @type {!WebAssembly.TableKind} */
WebAssembly.TableDescriptor.prototype.element;

/** @type {!WebAssembly.AddressValue} */
WebAssembly.TableDescriptor.prototype.initial;

/** @type {(!WebAssembly.AddressValue|undefined)} */
WebAssembly.TableDescriptor.prototype.maximum;

/**
 * @constructor
 * @param {!WebAssembly.TableDescriptor} tableDescriptor
 * @see https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/JavaScript_interface/Table
 */
WebAssembly.Table = function(tableDescriptor) {};

/**
 * @constructor
 * @extends {Error}
 */
WebAssembly.CompileError = function() {};

/**
 * @constructor
 * @extends {Error}
 */
WebAssembly.LinkError = function() {};

/**
 * @constructor
 * @param {string=} message
 * @param {string=} fileName
 * @param {number=} lineNumber
 * @extends {Error}
 */
WebAssembly.RuntimeError = function(message, fileName, lineNumber) {};

/**
 * @record
 * @see https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/JavaScript_interface/Tag
 */
WebAssembly.TagType = function() {};

/**
 * @type {!Array<!WebAssembly.ValueType>}
 */
WebAssembly.TagType.prototype.parameters;

/**
 * @constructor
 * @param {!WebAssembly.TagType} type
 * @see https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/JavaScript_interface/Tag
 */
WebAssembly.Tag = function(type) {};

/**
 * @type {!WebAssembly.Tag}
 */
WebAssembly.JSTag;

/**
 * @record
 */
function WebAssemblyExceptionOptions() {};

/**
 * @type {undefined|boolean}
 */
WebAssemblyExceptionOptions.prototype.traceStack;

/**
 * @constructor
 * @param {!WebAssembly.Tag} tag
 * @param {!Array} payload
 * @param {WebAssemblyExceptionOptions=} options
 * @see https://developer.mozilla.org/en-US/docs/WebAssembly/Reference/JavaScript_interface/Exception
 */
WebAssembly.Exception = function(tag, payload, options) {};

/**
 * @type {undefined|string}
 */
WebAssembly.Exception.prototype.stack;

/**
 * @param {!WebAssembly.Tag} exceptionTag
 * @param {number} index
 * @return {*}
 */
WebAssembly.Exception.prototype.getArg = function(exceptionTag, index) {};

/**
 * @param {!WebAssembly.Tag} exceptionTag
 * @return {boolean}
 */
WebAssembly.Exception.prototype.is = function(exceptionTag) {};

/**
 * @param {!BufferSource|!WebAssembly.Module} bytesOrModuleObject
 * @param {!WebAssembly.Imports=} opt_importObject
 * @param {!WebAssembly.WebAssemblyCompileOptions=} opt_options
 * @return {!Promise<!WebAssembly.WebAssemblyInstantiatedSource|!WebAssembly.Instance>}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WebAssembly/instantiate
 */
WebAssembly.instantiate = function(
    bytesOrModuleObject, opt_importObject, opt_options) {};

/**
 * @param {!Promise<!Response>|!Response} source
 * @param {!WebAssembly.Imports=} opt_importObject
 * @param {!WebAssembly.WebAssemblyCompileOptions=} opt_options
 * @return {!Promise<!WebAssembly.WebAssemblyInstantiatedSource>}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WebAssembly/instantiateStreaming
 */
WebAssembly.instantiateStreaming = function(
    source, opt_importObject, opt_options) {};

/**
 * @param {!BufferSource} bytes
 * @param {!WebAssembly.WebAssemblyCompileOptions=} opt_options
 * @return {!Promise<!WebAssembly.Module>}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WebAssembly/compile
 */
WebAssembly.compile = function(bytes, opt_options) {};

/**
 * @param {!Promise<!Response>|!Response} source
 * @param {!WebAssembly.WebAssemblyCompileOptions=} opt_options
 * @return {!Promise<!WebAssembly.Module>}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WebAssembly/compileStreaming
 */
WebAssembly.compileStreaming = function(source, opt_options) {};

/**
 * @param {!BufferSource} bytes
 * @param {!WebAssembly.WebAssemblyCompileOptions=} opt_options
 * @return {boolean}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WebAssembly/validate
 */
WebAssembly.validate = function(bytes, opt_options) {};

/**
 * @param {!WebAssembly.Module} moduleObject
 * @return {!Array<{name:string, kind:string}>}
 */
WebAssembly.Module.exports = function(moduleObject) {};

/**
 * @param {!WebAssembly.Module} moduleObject
 * @return {!Array<{module:string, name:string, kind:string}>}
 */
WebAssembly.Module.imports = function(moduleObject) {};

/**
 * @param {!WebAssembly.Module} moduleObject
 * @param {string} sectionName
 * @return {!Array<!ArrayBuffer>}
 */
WebAssembly.Module.customSections = function(moduleObject, sectionName) {};

WebAssembly.Instance.prototype.exports;

/**
 * @param {number} delta
 * @return {number}
 */
WebAssembly.Memory.prototype.grow = function(delta) {};

/**
 * @return {!ArrayBuffer}
 */
WebAssembly.Memory.prototype.toFixedLengthBuffer = function() {};

/**
 * @return {!ArrayBuffer}
 */
WebAssembly.Memory.prototype.toResizableBuffer = function() {};

/**
 * @type {!ArrayBuffer}
 */
WebAssembly.Memory.prototype.buffer;

/**
 * @param {!WebAssembly.AddressValue} delta
 * @param {*=} opt_value
 * @return {!WebAssembly.AddressValue}
 */
WebAssembly.Table.prototype.grow = function(delta, opt_value) {};

/**
 * @type {number}
 */
WebAssembly.Table.prototype.length;

/** @typedef {function(...)} */
var TableFunction;

/**
 * @param {number} index
 * @return {TableFunction}
 */
WebAssembly.Table.prototype.get = function(index) {};

/**
 * @param {number} index
 * @param {?TableFunction} value
 * @return {undefined}
 */
WebAssembly.Table.prototype.set = function(index, value) {};

/**
 * @typedef {string}
 * Valid values: 'i32', 'i64'.
 */
WebAssembly.AddressType;

/**
 * @typedef {number}
 */
WebAssembly.AddressValue;

/**
 * @typedef {{
 *   anyfunc: !Function,
 *   externref: ?,
 *   f32: number,
 *   f64: number,
 *   i32: number,
 *   i64: bigint,
 *   v128: *
 * }}
 * Note: This declaration is only here to document the acceptable type strings
 * ("anyfunc"|"externref"|"f32"|"f64"|"i32"|"i64"|"v128") and to prevent
 * the properties to be marked as missing externs in d.ts files.
 */
WebAssembly.ValueTypeMap;

/**
 * @typedef {string}
 * Really: keyof ValueTypeMap, i.e. ("anyfunc"|"externref"|"f32"|"f64"|"i32"|"i64"|"v128")
 */
WebAssembly.ValueType;

/**
 * @typedef {string}
 * Valid values: 'anyfunc', 'externref'.
 * @see https://webassembly.github.io/spec/js-api/#enumdef-tablekind
 */
WebAssembly.TableKind;

/**
 * @typedef {{
 *   mutable: (boolean|undefined),
 *   value: WebAssembly.ValueType
 * }}
 */
WebAssembly.GlobalDescriptor;

/**
 * @constructor
 * @param {WebAssembly.GlobalDescriptor} descriptor
 * @param {?=} v
 */
WebAssembly.Global = function(descriptor, v) {};

/**
 * @type {?}
 */
WebAssembly.Global.prototype.value;

/**
 * @typedef {!Function|!WebAssembly.Global|!WebAssembly.Memory|!WebAssembly.Table}
 */
WebAssembly.ExportValue;

/**
 * @typedef {!WebAssembly.ExportValue|number}
 */
WebAssembly.ImportValue;

/**
 * @typedef {!Object<string, !WebAssembly.ModuleImports>}
 */
WebAssembly.Imports;

/**
 * @typedef {!Object<string, !WebAssembly.ImportValue>}
 */
WebAssembly.ModuleImports;
