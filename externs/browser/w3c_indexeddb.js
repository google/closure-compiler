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

/**
 * @fileoverview Definitions for W3C's IndexedDB API. In Chrome all the
 * IndexedDB classes are prefixed with 'webkit'. In order to access constants
 * and static methods of these classes they must be duplicated with the
 * prefix here.
 * @see http://www.w3.org/TR/IndexedDB/
 *
 * @externs
 * @author guido.tapia@picnet.com.au (Guido Tapia)
 * @author vobruba.martin@gmail.com (Martin Vobruba)
 */

/** @type {!IDBFactory|undefined} */
Window.prototype.moz_indexedDB;

/** @type {!IDBFactory|undefined} */
Window.prototype.mozIndexedDB;

/** @type {!IDBFactory|undefined} */
Window.prototype.webkitIndexedDB;

/** @type {!IDBFactory|undefined} */
Window.prototype.msIndexedDB;

/** @type {!IDBFactory|undefined} */
Window.prototype.indexedDB;


/**
 * Possible values: 'readonly', 'readwrite', 'versionchange'
 *
 * @typedef {string}
 * @see https://www.w3.org/TR/IndexedDB/#idl-def-IDBTransactionMode
 */
var IDBTransactionMode;


/**
 * Possible values: 'pending', 'done'
 *
 * @typedef {string}
 * @see https://www.w3.org/TR/IndexedDB/#idl-def-IDBRequestReadyState
 */
var IDBRequestReadyState;


/**
 * Possible values: 'next', 'nextunique', 'prev', 'prevunique'
 *
 * @typedef {string}
 * @see https://www.w3.org/TR/IndexedDB/#idl-def-IDBCursorDirection
 */
var IDBCursorDirection;


/**
 * @record
 * @see https://www.w3.org/TR/IndexedDB/#idl-def-IDBIndexParameters
 */
function IDBIndexParameters(){};

/** @type {(undefined|boolean)} */
IDBIndexParameters.prototype.unique;

/** @type {(undefined|boolean)} */
IDBIndexParameters.prototype.multiEntry;


/**
 * @record
 * @extends {EventInit}
 * @see https://www.w3.org/TR/IndexedDB/#idl-def-IDBVersionChangeEventInit
 */
function IDBVersionChangeEventInit(){};

/** @type {(undefined|number)} */
IDBVersionChangeEventInit.prototype.oldVersion;

/** @type {(undefined|number|null)} */
IDBVersionChangeEventInit.prototype.newVersion;



/**
 * @record
 * @see https://www.w3.org/TR/IndexedDB/#idl-def-IDBObjectStoreParameters
 */
function IDBObjectStoreParameters() {};

/** @type {(undefined|string|!Array<string>|null)} */
IDBObjectStoreParameters.prototype.keyPath;

/** @type {(undefined|boolean)} */
IDBObjectStoreParameters.prototype.autoIncrement;


/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBFactory
 */
function IDBFactory() {}

/**
 * @param {string} name The name of the database to open.
 * @param {number=} opt_version The version at which to open the database.
 * @return {!IDBOpenDBRequest} The IDBRequest object.
 */
IDBFactory.prototype.open = function(name, opt_version) {};

/**
 * @param {string} name The name of the database to delete.
 * @return {!IDBOpenDBRequest} The IDBRequest object.
 */
IDBFactory.prototype.deleteDatabase = function(name) {};

/**
 * @param {*} first
 * @param {*} second
 * @return {number}
 */
IDBFactory.prototype.cmp = function(first, second) {};


/**
 * @constructor
 * @implements {EventTarget}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBRequest
 */
function IDBRequest() {}

/** @override */
IDBRequest.prototype.addEventListener = function(type, listener, opt_options) {
};

/** @override */
IDBRequest.prototype.removeEventListener = function(
    type, listener, opt_options) {};

/** @override */
IDBRequest.prototype.dispatchEvent = function(evt) {};


/**
 * @constructor
 * @extends {IDBRequest}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBRequest
 */
function webkitIDBRequest() {}

/**
 * @type {!IDBRequestReadyState}
 */
IDBRequest.prototype.readyState; // readonly

/**
 * @type {function(!Event)}
 */
IDBRequest.prototype.onsuccess = function(e) {};

/**
 * @type {function(!Event)}
 */
IDBRequest.prototype.onerror = function(e) {};

/** @type {*} */
IDBRequest.prototype.result;  // readonly

/**
 * @type {number}
 * @deprecated Use "error"
 */
IDBRequest.prototype.errorCode;  // readonly


/** @type {!DOMError} */
IDBRequest.prototype.error; // readonly

/** @type {?Object} */
IDBRequest.prototype.source; // readonly

/** @type {!IDBTransaction} */
IDBRequest.prototype.transaction; // readonly


/**
 * @constructor
 * @extends {IDBRequest}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBOpenDBRequest
 */
function IDBOpenDBRequest() {}

/**
 * @type {function(!IDBVersionChangeEvent)}
 */
IDBOpenDBRequest.prototype.onblocked = function(e) {};

/**
 * @type {function(!IDBVersionChangeEvent)}
 */
IDBOpenDBRequest.prototype.onupgradeneeded = function(e) {};


/**
 * @constructor
 * @implements {EventTarget}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBDatabase
 */
function IDBDatabase() {}

/**
 * @type {string}
 * @const
 */
IDBDatabase.prototype.name;

/**
 * @type {number}
 * @const
 */
IDBDatabase.prototype.version;

/**
 * @type {!DOMStringList}
 * @const
 */
IDBDatabase.prototype.objectStoreNames;

/**
 * @param {string} name The name of the object store.
 * @param {!IDBObjectStoreParameters=} opt_parameters Parameters to be passed
 *     creating the object store.
 * @return {!IDBObjectStore} The created/open object store.
 */
IDBDatabase.prototype.createObjectStore =
    function(name, opt_parameters)  {};

/**
 * @param {string} name The name of the object store to remove.
 * @return {undefined}
 */
IDBDatabase.prototype.deleteObjectStore = function(name) {};

/**
 * @param {(string|!Array<string>)} storeNames The stores to open in this transaction.
 * @param {!IDBTransactionMode=} mode The mode for opening the object stores.
 * @return {!IDBTransaction} The IDBRequest object.
 */
IDBDatabase.prototype.transaction = function(storeNames, mode) {};

/**
 * Closes the database connection.
 * @return {undefined}
 */
IDBDatabase.prototype.close = function() {};

/**
 * @type {?function(!Event)}
 */
IDBDatabase.prototype.onabort;

/**
 * @type {?function(!Event)}
 */
IDBDatabase.prototype.onclose;

/**
 * @type {?function(!Event)}
 */
IDBDatabase.prototype.onerror;

/**
 * @type {?function(!IDBVersionChangeEvent)}
 */
IDBDatabase.prototype.onversionchange;

/** @override */
IDBDatabase.prototype.addEventListener = function(type, listener, opt_options) {
};

/** @override */
IDBDatabase.prototype.removeEventListener = function(
    type, listener, opt_options) {};

/** @override */
IDBDatabase.prototype.dispatchEvent = function(evt) {};


/**
 * Typedef for valid key types according to the w3 specification. Note that this
 * is slightly wider than what is actually allowed, as all Array elements must
 * have a valid key type.
 * @see http://www.w3.org/TR/IndexedDB/#key-construct
 * @typedef {number|string|!Date|!Array<?>}
 */
var IDBKeyType;


/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBObjectStore
 */
function IDBObjectStore() {}

/**
 * @type {string}
 */
IDBObjectStore.prototype.name;

/**
 * @type {*}
 */
IDBObjectStore.prototype.keyPath;

/**
 * @type {!DOMStringList}
 */
IDBObjectStore.prototype.indexNames;

/** @type {!IDBTransaction} */
IDBObjectStore.prototype.transaction;

/** @type {boolean} */
IDBObjectStore.prototype.autoIncrement;

/**
 * @param {*} value The value to put into the object store.
 * @param {!IDBKeyType=} key The key of this value.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.put = function(value, key) {};

/**
 * @param {*} value The value to add into the object store.
 * @param {!IDBKeyType=} key The key of this value.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.add = function(value, key) {};

/**
 * @param {!IDBKeyType|!IDBKeyRange} key The key of this value.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.delete = function(key) {};

/**
 * @param {!IDBKeyType|!IDBKeyRange} key The key of the document to retrieve.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.get = function(key) {};

/**
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.clear = function() {};

/**
 * @param {?IDBKeyRange=} range The range of the cursor.
 *     Nullable because IE <11 has problems with undefined.
 * @param {!IDBCursorDirection=} direction The direction of cursor enumeration.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.openCursor = function(range, direction) {};

/**
 * @param {string} name The name of the index.
 * @param {string|!Array<string>} keyPath The path to the index key.
 * @param {!IDBIndexParameters=} opt_paramters Optional parameters
 *     for the created index.
 * @return {!IDBIndex} The IDBIndex object.
 */
IDBObjectStore.prototype.createIndex = function(name, keyPath, opt_paramters) {};

/**
 * @param {string} name The name of the index to retrieve.
 * @return {!IDBIndex} The IDBIndex object.
 */
IDBObjectStore.prototype.index = function(name) {};

/**
 * @param {string} indexName The name of the index to remove.
 * @return {undefined}
 */
IDBObjectStore.prototype.deleteIndex = function(indexName) {};

/**
 * @param {(!IDBKeyType|IDBKeyRange)=} key The key of this value.
 * @return {!IDBRequest} The IDBRequest object.
 * @see http://www.w3.org/TR/IndexedDB/#widl-IDBObjectStore-count
 */
IDBObjectStore.prototype.count = function(key) {};


/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBIndex
 */
function IDBIndex() {}

/**
 * @type {string}
 * @const
 */
IDBIndex.prototype.name;

/**
 * @type {!IDBObjectStore}
 * @const
 */
IDBIndex.prototype.objectStore;

/**
 * @type {*}
 * @const
 */
IDBIndex.prototype.keyPath;

/**
 * @type {boolean}
 * @const
 */
IDBIndex.prototype.multiEntry;

/**
 * @type {boolean}
 * @const
 */
IDBIndex.prototype.unique;

/**
 * @param {(!IDBKeyType|?IDBKeyRange)=} range The range of the cursor.
 *     Nullable because IE <11 has problems with undefined.
 * @param {!IDBCursorDirection=} direction The direction of cursor enumeration.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBIndex.prototype.openCursor = function(range, direction) {};

/**
 * @param {(!IDBKeyType|?IDBKeyRange)=} range The range of the cursor.
 *     Nullable because IE <11 has problems with undefined.
 * @param {!IDBCursorDirection=} direction The direction of cursor enumeration.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBIndex.prototype.openKeyCursor = function(range, direction) {};

/**
 * @param {!IDBKeyType|!IDBKeyRange} key The id of the object to retrieve.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBIndex.prototype.get = function(key) {};

/**
 * @param {!IDBKeyType|!IDBKeyRange} key The id of the object to retrieve.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBIndex.prototype.getKey = function(key) {};

/**
 * @param {(!IDBKeyType|!IDBKeyRange)=} opt_key
 * @return {!IDBRequest}
 */
IDBIndex.prototype.count = function(opt_key) {};


/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBCursor
 */
function IDBCursor() {}

/**
 * @constructor
 * @extends {IDBCursor}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBCursor
 */
function webkitIDBCursor() {}

/**
 * @type {(!IDBObjectStore|!IDBIndex)}
 * @const
 */
IDBCursor.prototype.source;

/**
 * @type {!IDBCursorDirection}
 * @const
 */
IDBCursor.prototype.direction;

/**
 * @type {!IDBKeyType}
 * @const
 */
IDBCursor.prototype.key;

/**
 * @type {!IDBKeyType}
 * @const
 */
IDBCursor.prototype.primaryKey;

/**
 * @param {*} value The new value for the current object in the cursor.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBCursor.prototype.update = function(value) {};

/**
 * Note: Must be quoted to avoid parse error.
 * @param {!IDBKeyType=} key Continue enumerating the cursor from the specified
 *     key (or next).
 * @return {undefined}
 */
IDBCursor.prototype.continue = function(key) {};

/**
 * @param {number} count Number of times to iterate the cursor.
 * @return {undefined}
 */
IDBCursor.prototype.advance = function(count) {};

/**
 * Note: Must be quoted to avoid parse error.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBCursor.prototype.delete = function() {};


/**
 * @constructor
 * @extends {IDBCursor}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBCursorWithValue
 */
function IDBCursorWithValue() {}

/** @type {*} */
IDBCursorWithValue.prototype.value; // readonly


/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBTransaction
 */
function IDBTransaction() {}

/**
 * @constructor
 * @extends {IDBTransaction}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBTransaction
 */
function webkitIDBTransaction() {}

/**
 * @type {!IDBTransactionMode}
 * @const
 */
IDBTransaction.prototype.mode;

/**
 * @type {!IDBDatabase}
 * @const
 */
IDBTransaction.prototype.db;

/**
 * @type {!DOMError}
 */
IDBTransaction.prototype.error;

/**
 * @param {string} name The name of the object store to retrieve.
 * @return {!IDBObjectStore} The object store.
 */
IDBTransaction.prototype.objectStore = function(name) {};

/**
 * Aborts the transaction.
 * @return {undefined}
 */
IDBTransaction.prototype.abort = function() {};

/**
 * @type {?function(!Event)}
 */
IDBTransaction.prototype.onabort;

/**
 * @type {?function(!Event)}
 */
IDBTransaction.prototype.oncomplete;

/**
 * @type {?function(!Event)}
 */
IDBTransaction.prototype.onerror;


/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBKeyRange
 */
function IDBKeyRange() {}

/**
 * @constructor
 * @extends {IDBKeyRange}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBKeyRange
 */
function webkitIDBKeyRange() {}

/**
 * @type {*}
 * @const
 */
IDBKeyRange.prototype.lower;

/**
 * @type {*}
 * @const
 */
IDBKeyRange.prototype.upper;

/**
 * @type {boolean}
 * @const
 */
IDBKeyRange.prototype.lowerOpen;

/**
 * @type {boolean}
 * @const
 */
IDBKeyRange.prototype.upperOpen;

/**
 * @param {!IDBKeyType} value The single key value of this range.
 * @return {!IDBKeyRange} The key range.
 */
IDBKeyRange.only = function(value) {};

/**
 * @param {!IDBKeyType} bound Creates a lower bound key range.
 * @param {boolean=} open Open the key range.
 * @return {!IDBKeyRange} The key range.
 */
IDBKeyRange.lowerBound = function(bound, open) {};

/**
 * @param {!IDBKeyType} bound Creates an upper bound key range.
 * @param {boolean=} open Open the key range.
 * @return {!IDBKeyRange} The key range.
 */
IDBKeyRange.upperBound = function(bound, open) {};

/**
 * @param {!IDBKeyType} left The left bound value.
 * @param {!IDBKeyType} right The right bound value.
 * @param {boolean=} openLeft Whether the left bound value should be excluded.
 * @param {boolean=} openRight Whether the right bound value should be excluded.
 * @return {!IDBKeyRange} The key range.
 */
IDBKeyRange.bound = function(left, right, openLeft, openRight) {};


/**
 * @param {string} type
 * @param {!IDBVersionChangeEventInit=} opt_eventInit
 * @constructor
 * @extends {Event}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBVersionChangeEvent
 */
function IDBVersionChangeEvent(type, opt_eventInit) {}

/**
 * @type {number}
 * @const
 */
IDBVersionChangeEvent.prototype.oldVersion;

/**
 * @type {?number}
 * @const
 */
IDBVersionChangeEvent.prototype.newVersion;


/**
 * @param {string} type
 * @param {!IDBVersionChangeEventInit=} opt_eventInit
 * @constructor
 * @extends {IDBVersionChangeEvent}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBVersionChangeEvent
 */
function webkitIDBVersionChangeEvent(type, opt_eventInit) {}

/**
 * @type {string}
 * @const
 */
webkitIDBVersionChangeEvent.prototype.version;
