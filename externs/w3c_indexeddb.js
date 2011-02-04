/*
 * Copyright 2011 PicNet Pty Ltd.
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
 * @fileoverview Definitions for w3c indexed db api
 *    http://www.w3.org/TR/IndexedDB/.
 *
 * @externs
 */

/**
 * @return {!IDBFactory} Provides applications a mechanism for accessing
 *    capabilities of indexed databases.
 */
function moz_indexedDB() {}

/**
 * @return {!IDBFactory} Provides applications a mechanism for accessing
 *    capabilities of indexed databases.
 */
function mozIndexedDB() {}

/**
 * @return {!IDBFactory} Provides applications a mechanism for accessing
 *    capabilities of indexed databases.
 */
function webkitIndexedDB() {}

/**
 * @return {!IDBFactory} Provides applications a mechanism for accessing
 *    capabilities of indexed databases.
 */
function indexedDB() {}

/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBFactory
 */
function IDBFactory() {}

/**
 * @type {Array.<string>}
 * @const
 */
IDBFactory.prototype.databases;

/**
 * @param {string} name The name of the database to open.
 * @param {string} description The description of the database.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBFactory.prototype.open = function(name, description) {};

/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBDatabaseException
 */
function IDBDatabaseException() {}

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.UNKNOWN_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.NON_TRANSIENT_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.NOT_FOUND_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.CONSTRAINT_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.DATA_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.NOT_ALLOWED_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.SERIAL_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.RECOVERABLE_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.TRANSIENT_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.TIMEOUT_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.DEADLOCK_ERR;

/**
 * @const
 * @type {number}
 */
IDBDatabaseException.code;

/**
 * @const
 * @type {string}
 */
IDBDatabaseException.message;

/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBEvent
 */
function IDBEvent() {}

/**
 * @type {*}
 * @const
 */
IDBEvent.prototype.source;

/**
 * @constructor
 * @extends {IDBEvent}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBSuccessEvent
 */
function IDBSuccessEvent() {}

/**
 * @type {*}
 * @const
 */
IDBSuccessEvent.prototype.result;

/**
 * @constructor
 * @extends {IDBSuccessEvent}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBTransactionEvent
 */
function IDBTransactionEvent() {}

/**
 * @type {!IDBTransaction}
 * @const
 */
IDBTransactionEvent.prototype.transaction;

/**
 * @constructor
 * @extends {IDBEvent}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBErrorEvent
 */
function IDBErrorEvent() {}

/**
 * @type {number}
 * @const
 */
IDBErrorEvent.prototype.code;

/**
 * @type {string}
 * @const
 */
IDBErrorEvent.prototype.message;

/**
 * @constructor
 * @extends {IDBEvent}
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBVersionChangeEvent
 */
function IDBVersionChangeEvent() {}

/**
 * @type {string}
 * @const
 */
IDBVersionChangeEvent.prototype.version;

/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBRequest
 */
function IDBRequest() {}

IDBRequest.prototype.abort = function() {};

/**
 * @type {number}
 * @const
 */
IDBRequest.LOADING;

/**
 * @type {number}
 * @const
 */
IDBRequest.DONE;

/**
 * @type {number}
 * @const
 */
IDBRequest.prototype.readyState;

/**
 * @type {function(IDBSuccessEvent)}
 * @const
 */
IDBRequest.prototype.onsuccess = function() {};

/**
 * @type {function(IDBErrorEvent)}
 * @const
 */
IDBRequest.prototype.onerror = function() {};

/**
 * FF4b9 Introduced this property that is not currently in the formal specs.  So
 *    now instead of getting the result from the IDBSuccessEvent of onsuccess
 *    callback we have to get it from the original request (only if successful).
 * 
 * @type {*}
 * @const
 */
IDBRequest.prototype.result;

/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBDatabase
 */
function IDBDatabase() {}

/**
 * @type {string}
 * @const
 */
IDBDatabase.prototype.name;

/**
 * @type {string}
 * @const
 */
IDBDatabase.prototype.description;

/**
 * @type {string}
 * @const
 */
IDBDatabase.prototype.version;

/**
 * @type {Array.<string>}
 * @const
 */
IDBDatabase.prototype.objectStoreNames;

/**
 * @param {string} name The name of the object store.
 * @param {string=} keyPath The path to the key property of the documents.
 * @param {boolean=} autoIncrement Wether to auto increment the ID.
 * @return {!IDBObjectStore} The created/open object store.
 */
IDBDatabase.prototype.createObjectStore =
    function(name, keyPath, autoIncrement)  {};

/**
 * @param {string} name The name of the object store to retreive.
 * @param {number=} mode The mode to use when retreiving.
 * @param {number=} timeout The timeout allowed.
 * @return {!IDBObjectStore} The opene object store.
 */
IDBDatabase.prototype.objectStore = function(name, mode, timeout) {};

/**
 * @param {string} name The name of the object store to remove.
 */
IDBDatabase.prototype.deleteObjectStore = function(name) {};

/**
 * @param {string} version The new version of the database.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBDatabase.prototype.setVersion = function(version) {};

/**
 * @param {Array.<string>=} storeNames The stores to open in this transaction.
 * @param {number=} mode The mode for opening the object stores.
 * @param {number=} timeout The timeout allowed.
 * @return {!IDBTransaction} The IDBRequest object.
 */
IDBDatabase.prototype.transaction = function(storeNames, mode, timeout) {};

IDBDatabase.prototype.close = function() {};

/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBObjectStore
 */
function IDBObjectStore() {}

/**
 * @type {string}
 * @const
 */
IDBObjectStore.prototype.name;

/**
 * @type {string}
 * @const
 */
IDBObjectStore.prototype.keyPath;

/**
 * @type {Array.<string>}
 * @const
 */
IDBObjectStore.prototype.indexNames;

/**
 * @param {*} value The value to put into the object store.
 * @param {*=} key The key of this value.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.put = function(value, key) {};

/**
 * @param {*} value The value to add into the object store.
 * @param {*=} key The key of this value.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.add = function(value, key) {};

/**
 * @param {*} key The key of the document to remove.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.remove = function(key) {};

/**
 * @param {*} key The key of the document to retreive.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.get = function(key) {};

/**
 * @param {IDBKeyRange=} range The range of the cursor.
 * @param {number=} direction The direction of cursor enumeration.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.openCursor = function(range, direction) {};

/**
 * @param {string} name The name of the index.
 * @param {string} keyPath The path to the index key.
 * @param {boolean=} unique Wether the index enforces unique values.
 * @return {!IDBIndex} The IDBIndex object.
 */
IDBObjectStore.prototype.createIndex = function(name, keyPath, unique) {};

/**
 * @param {string} name The name of the index to retreive.
 * @return {!IDBIndex} The IDBIndex object.
 */
IDBObjectStore.prototype.index = function(name) {};

/**
 * @param {string} indexName The name of the index to remove.
 */
IDBObjectStore.prototype.removeIndex = function(indexName) {};

/**
 * Note: This is currently only supported by Mozilla implementation
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.clear = function() {};

/**
 * Note: This is currently only supported by Mozilla implementation
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBObjectStore.prototype.getAll = function() {};

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
 * @type {string}
 * @const
 */
IDBIndex.prototype.storeName;

/**
 * @type {string}
 * @const
 */
IDBIndex.prototype.keyPath;

/**
 * @type {boolean}
 * @const
 */
IDBIndex.prototype.unique;

/**
 * @param {IDBKeyRange=} range The range of the cursor.
 * @param {number=} direction The direction of cursor enumeration.
 * @return {!IDBCursor} This method returns immediately and creates a cursor
 *    over the records of this index. The range of this cursor matches the key
 *    range specified as the range parameter, or if that parameter is not
 *    specified or null, then the range includes all the records.
 */
IDBIndex.prototype.openObjectCursor = function(range, direction) {};

/**
 * @param {IDBKeyRange=} range The range of the cursor.
 * @param {number=} direction The direction of cursor enumeration.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBIndex.prototype.openCursor = function(range, direction) {};

/**
 * @param {*} key The id of the object to retreive.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBIndex.prototype.getObject = function(key) {};

/**
 * @param {*} key The id of the object to retreive.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBIndex.prototype.get = function(key) {};

/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBCursor
 */
function IDBCursor() {}

/**
 * @const
 * @type {number}
 */
IDBCursor.NEXT;

/**
 * @const
 * @type {number}
 */
IDBCursor.NEXT_NO_DUPLICATE;

/**
 * @const
 * @type {number}
 */
IDBCursor.PREV;

/**
 * @const
 * @type {number}
 */
IDBCursor.PREV_NO_DUPLICATE;

/**
 * @type {number}
 * @const
 */
IDBCursor.prototype.direction;

/**
 * @type {*}
 * @const
 */
IDBCursor.prototype.key;

/**
 * @type {*}
 * @const
 */
IDBCursor.prototype.value;

/**
 * @type {number}
 * @const
 */
IDBCursor.prototype.count;

/**
 * @param {*} value The new value for the current object in the cursor.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBCursor.prototype.update = function(value) {};

/**
 * Note: Must be quoted to avoid parse error.
 * @param {*=} key Continue enumerating the cursor from the specified key
 *    (or next).
 * @return {boolean} Wether the continue operation was successfull.
 */
IDBCursor.prototype['continue'] = function(key) {};

/**
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBCursor.prototype.remove = function() {};

/**
 * Note: Must be quoted to avoid parse error.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBCursor.prototype['delete'] = function() {};

/**
 * @constructor 
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBTransaction
 */
function IDBTransaction() {}

/**
 * @type {IDBTransaction}
 */
function webkitIDBTransaction() {}

/**
 * @const
 * @type {number}
 */
IDBTransaction.READ_WRITE;

/**
 * @const
 * @type {number}
 */
IDBTransaction.READ_ONLY;

/**
 * @const
 * @type {number}
 */
IDBTransaction.SNAPSHOT_READ;

/**
 * @const
 * @type {number}
 */
IDBTransaction.VERSION_CHANGE;

/**
 * @type {number}
 * @const
 */
IDBTransaction.prototype.mode;

/**
 * @type {IDBDatabase}
 * @const
 */
IDBTransaction.prototype.db;

/**
 * @param {string} name The name of the object store to retreive.
 * @return {!IDBObjectStore} The object store.
 */
IDBTransaction.prototype.objectStore = function(name) {};

IDBTransaction.prototype.abort = function() {};

/**
 * @type {Function}
 */
IDBTransaction.prototype.onabort = function() {};

/**
 * @type {Function}
 */
IDBTransaction.prototype.oncomplete = function() {};

/**
 * @type {Function}
 */
IDBTransaction.prototype.ontimeout = function() {};

/**
 * @constructor 
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBDynamicTransaction
 */
function IDBDynamicTransaction() {}

/**
 * @type {IDBDatabase}
 * @const
 */
IDBDynamicTransaction.prototype.db;

/**
 * @param {string} name The name of the object store to open.
 * @param {number=} mode The mode to use when accessing this object store.
 * @return {!IDBRequest} The IDBRequest object.
 */
IDBDynamicTransaction.prototype.openObjectStore = function(name, mode) {};

IDBDynamicTransaction.prototype.abort = function() {};

/**
 * @type {Function}
 */
IDBDynamicTransaction.prototype.onabort = function() {};

/**
 * @type {Function}
 */
IDBDynamicTransaction.prototype.oncomplete = function() {};

/**
 * @type {Function}
 */
IDBDynamicTransaction.prototype.ontimeout = function() {};

/**
 * @constructor
 * @see http://www.w3.org/TR/IndexedDB/#idl-def-IDBKeyRange
 */
function IDBKeyRange() {}

/**
 * @type {IDBKeyRange}
 */
function webkitIDBKeyRange() {}

/**
 * @const
 * @type {number}
 */
IDBKeyRange.SINGLE;

/**
 * @const
 * @type {number}
 */
IDBKeyRange.LEFT_OPEN;

/**
 * @const
 * @type {number}
 */
IDBKeyRange.RIGHT_OPEN;

/**
 * @const
 * @type {number}
 */
IDBKeyRange.LEFT_BOUND;

/**
 * @const
 * @type {number}
 */
IDBKeyRange.RIGHT_BOUND;

/**
 * @type {*}
 * @const
 */
IDBKeyRange.prototype.left;

/**
 * @type {*}
 * @const
 */
IDBKeyRange.prototype.right;

/**
 * @type {number}
 * @const
 */
IDBKeyRange.prototype.flags;

/**
 * @param {*} value The single key value of this range.
 * @return {!IDBKeyRange} The key range.
 */
IDBKeyRange.prototype.only = function(value) {};

/**
 * @param {*} bound Creates a left bound key range.
 * @param {boolean=} open Open the key range.
 * @return {!IDBKeyRange} The key range.
 */
IDBKeyRange.prototype.leftBound = function(bound, open) {};

/**
 * @param {*} bound Creates a right bound key range.
 * @param {boolean=} open Open the key range.
 * @return {!IDBKeyRange} The key range.
 */
IDBKeyRange.prototype.rightBound = function(bound, open) {};

/**
 * @param {*} left The left bound value of openLeft is true.
 * @param {*} right The right bound value of openRight is true.
 * @param {boolean=} openLeft Wether to open a left bound range.
 * @param {boolean=} openRight Wether to open a right bound range.
 * @return {!IDBKeyRange} The key range.
 */
IDBKeyRange.prototype.bound = function(left, right, openLeft, openRight) {};
