/*
 * Copyright 2018 The Closure Compiler Authors.
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
 * @fileoverview Light weight implementation of a module loader that is based on
 * CommonJS.
 *
 * This is meant to be used by the Closure Library to help debug load transpiled
 * ES6 modules. Closure can transpile ES6 modules to a function that is
 * compatible with registerModule. Then it can call the global $jscomp.require
 * when it wants to retrieve a reference to the module object.
 *
 * Example:
 * "import {x} from './other.js'; export {x as Y}; use(x);"
 *
 * Might be transpiled as:
 *
 * $jscomp.registerModule(function($$exports, $$require, $$module) {
 *   Object.defineProperties($$exports, {
 *     Y: enumerable: true, get: function() { return module$other.x }
 *   });
 *   const module$other = $$require('./other.js');
 *   use(module$other.x);
 * }, 'example.js', ['./other.js']);
 *
 * @suppress {uselessCode} The require statements below are not useless.
 */

'require base';
'require es6/map';
'require es6/set';
'require util/global';

(function() {
/**
 * @param {string} id
 * @param {?=} opt_exports
 *
 * @struct @constructor @final
 */
var Module = function(id, opt_exports) {
  /** @const {string} */
  this.id = id;
  /** @type {?} */
  this.exports = opt_exports || {};
};


/**
 * @param {?} other
 */
Module.prototype.exportAllFrom = function(other) {
  var module = this;
  var define = {};
  for (var key in other) {
    if (key == 'default' || key in module.exports || key in define) {
      continue;
    }
    define[key] = {
      enumerable: true,
      get: (function(key) {
        return function() {
          return other[key];
        };
      })(key)
    };
  }
  $jscomp.global.Object.defineProperties(module.exports, define);
};


/**
 * @param {?function(function(string), ?, !Module)} def The module definition
 *     function which has the arguments (require, exports, module).
 * @param {!Module} module
 * @param {string} path
 *
 * @struct @constructor @final
 */
var CacheEntry = function(def, module, path) {
  /** @type {?function(function(string), ?, !Module)} */
  this.def = def;
  /** @type {!Module} */
  this.module = module;
  /** @type {string} */
  this.path = path;
  /** @const {!Set<string>} */
  this.blockingDeps = new Set();
};


/**
 * Loads the module by calling its module definition function if it has not
 * already been loaded.
 *
 * @return {?} The module's exports property.
 */
CacheEntry.prototype.load = function() {
  if (this.def) {
    var def = this.def;
    this.def = null;
    callRequireCallback(def, this.module);
  }

  return this.module.exports;
};


/**
 * @param {function(function(string), ?, !Module)|function(function(string))}
 *     callback A module definition function with arguments (require, exports,
 *     module) or a require.ensure callback which has the argument (require).
 * @param {!Module=} opt_module If provided then the callback is assumed to be
 *     this module's definition function.
 */
function callRequireCallback(callback, opt_module) {
  var oldPath = currentModulePath;

  try {
    if (opt_module) {
      currentModulePath = opt_module.id;
      callback.call(
          opt_module, createRequire(opt_module), opt_module.exports,
          opt_module);
    } else {
      callback($jscomp.require);
    }
  } finally {
    currentModulePath = oldPath;
  }
}


/** @type {!Map<string, !CacheEntry>} */
var moduleCache = new Map();


/** @type {string} */
var currentModulePath = '';


/**
 * Normalize a file path by removing redundant ".." and extraneous "." file
 * path components.
 *
 * @param {string} path
 * @return {string}
 */
function normalizePath(path) {
  var components = path.split('/');
  var i = 0;
  while (i < components.length) {
    if (components[i] == '.') {
      components.splice(i, 1);
    } else if (
        i && components[i] == '..' && components[i - 1] &&
        components[i - 1] != '..') {
      components.splice(--i, 2);
    } else {
      i++;
    }
  }
  return components.join('/');
}


/** @return {?string} */
$jscomp.getCurrentModulePath = function() {
  return currentModulePath;
};


/**
 * @param {string} id
 * @return {!CacheEntry}
 */
function getCacheEntry(id) {
  var cacheEntry = moduleCache.get(id);
  if (cacheEntry === undefined) {
    throw new Error('Module ' + id + ' does not exist.');
  }
  return cacheEntry;
}


/**
 * Map of absolute module path to list of require.ensure callbacks waiting for
 * the given module to load.
 *
 * @const {!Map<string, !Array<!CallbackEntry>>}
 */
var ensureMap = new Map();


/**
 * @param {!Set<string>} requireSet
 * @param {function(function(string))} callback
 *
 * @struct @constructor @final
 */
var CallbackEntry = function(requireSet, callback) {
  /** @const */
  this.requireSet = requireSet;
  /** @const */
  this.callback = callback;
};


/**
 * Normalizes two paths if the second is relative.
 *
 * @param {string} root
 * @param {string} absOrRelativePath
 * @return {string}
 */
function maybeNormalizePath(root, absOrRelativePath) {
  if (absOrRelativePath.startsWith('./') ||
      absOrRelativePath.startsWith('../')) {
    return normalizePath(root + '/../' + absOrRelativePath);
  } else {
    return absOrRelativePath;
  }
}


/**
 * Creates a require function which resolves paths against the given module, if
 * any.
 *
 * @param {!Module=} opt_module
 * @return {function(string):?}
 */
function createRequire(opt_module) {
  /**
   * @param {string} absOrRelativePath
   * @return {?}
   */
  function require(absOrRelativePath) {
    var absPath = absOrRelativePath;
    if (opt_module) {
      absPath = maybeNormalizePath(opt_module.id, absPath);
    }
    return getCacheEntry(absPath).load();
  }

  /**
   * @param {!Array<string>} requires
   * @param {function(function(string))} callback
   */
  function requireEnsure(requires, callback) {
    if (currentModulePath) {
      for (var i = 0; i < requires.length; i++) {
        requires[i] = maybeNormalizePath(currentModulePath, requires[i]);
      }
    }

    var blockingRequires = [];
    for (var i = 0; i < requires.length; i++) {
      var required = moduleCache.get(requires[i]);
      if (!required || required.blockingDeps.size) {
        blockingRequires.push(requires[i]);
      }
    }

    if (blockingRequires.length) {
      var requireSet = new Set(blockingRequires);
      var callbackEntry = new CallbackEntry(requireSet, callback);
      requireSet.forEach(function(require) {
        var arr = ensureMap.get(require);
        if (!arr) {
          arr = [];
          ensureMap.set(require, arr);
        }
        arr.push(callbackEntry);
      });
    } else {
      callback(require);
    }
  }
  require.ensure = requireEnsure;

  return require;
}


/** @const {function(string): ?} */
$jscomp.require = createRequire();


/**
 * @param {string} id
 * @return {boolean}
 */
$jscomp.hasModule = function(id) {
  return moduleCache.has(id);
};


/**
 * Marks the given module as being available and calls any require.ensure
 * callbacks waiting for it.
 *
 * @param {string} absModulePath
 */
function markAvailable(absModulePath) {
  var ensures = ensureMap.get(absModulePath);

  if (ensures) {
    for (var i = 0; i < ensures.length; i++) {
      var entry = ensures[i];
      entry.requireSet.delete(absModulePath);
      if (!entry.requireSet.size) {
        ensures.splice(i--, 1);
        callRequireCallback(entry.callback);
      }
    }

    if (!ensures.length) {
      ensureMap.delete(absModulePath);
    }
  }
}


/**
 * Registers a CommonJS-like module for use with this runtime. Does not execute
 * the module until it is required.
 *
 * @param {function(function(string), ?, !Module)} moduleDef The module
 *     definition.
 * @param {string} absModulePath
 * @param {!Array<string>=} opt_shallowDeps List of dependencies this module
 *     directly depends on. Paths can be relative to the given module. This
 *     module will considered available until all of its dependencies are also
 *     available for require.
 */
$jscomp.registerModule = function(moduleDef, absModulePath, opt_shallowDeps) {
  if (moduleCache.has(absModulePath)) {
    throw new Error(
        'Module ' + absModulePath + ' has already been registered.');
  }

  if (currentModulePath) {
    throw new Error('Cannot nest modules.');
  }

  var shallowDeps = opt_shallowDeps || [];
  for (var i = 0; i < shallowDeps.length; i++) {
    shallowDeps[i] = maybeNormalizePath(absModulePath, shallowDeps[i]);
  }

  var /** !Set<string> */ blockingDeps = new Set();
  for (var i = 0; i < shallowDeps.length; i++) {
    getTransitiveBlockingDepsOf(shallowDeps[i]).forEach(function(transitive) {
      blockingDeps.add(transitive);
    });
  }

  // Make sure this module isn't blocking itself in the event of a cycle.
  blockingDeps.delete(absModulePath);

  var cacheEntry =
      new CacheEntry(moduleDef, new Module(absModulePath), absModulePath);
  moduleCache.set(absModulePath, cacheEntry);

  blockingDeps.forEach(function(blocker) {
    addAsBlocking(cacheEntry, blocker);
  });

  if (!blockingDeps.size) {
    markAvailable(cacheEntry.module.id);
  }

  removeAsBlocking(cacheEntry);
};


/**
 * @param {string} moduleId
 * @return {!Set<string>}
 */
function getTransitiveBlockingDepsOf(moduleId) {
  var cacheEntry = moduleCache.get(moduleId);
  var /** !Set<string> */ blocking = new Set();

  if (cacheEntry) {
    cacheEntry.blockingDeps.forEach(function(dep) {
      getTransitiveBlockingDepsOf(dep).forEach(function(transitive) {
        blocking.add(transitive);
      });
    });
  } else {
    blocking.add(moduleId);
  }

  return blocking;
}


/** @const {!Map<string, !Set<!CacheEntry>>} */
var blockingModulePathToBlockedModules = new Map();


/**
 * @param {!CacheEntry} blocked
 * @param {string} blocker
 */
function addAsBlocking(blocked, blocker) {
  if (blocked.module.id != blocker) {
    var blockedModules = blockingModulePathToBlockedModules.get(blocker);

    if (!blockedModules) {
      blockedModules = new Set();
      blockingModulePathToBlockedModules.set(blocker, blockedModules);
    }

    blockedModules.add(blocked);
    blocked.blockingDeps.add(blocker);
  }
}


/**
 * Marks the given module as no longer blocking any modules. Instead marks the
 * module's blockers as blocking these modules. If this totally unblocks a
 * module it is marked as available.
 *
 * @param {!CacheEntry} cacheEntry
 */
function removeAsBlocking(cacheEntry) {
  var blocked = blockingModulePathToBlockedModules.get(cacheEntry.module.id);

  if (blocked) {
    blockingModulePathToBlockedModules.delete(cacheEntry.module.id);

    blocked.forEach(function(blockedCacheEntry) {
      blockedCacheEntry.blockingDeps.delete(cacheEntry.module.id);

      cacheEntry.blockingDeps.forEach(function(blocker) {
        addAsBlocking(blockedCacheEntry, blocker);
      });

      if (!blockedCacheEntry.blockingDeps.size) {
        removeAsBlocking(blockedCacheEntry);
        markAvailable(blockedCacheEntry.module.id);
      }
    });
  }
}


/**
 * Forces module evaluation as soon as it is available for require.
 *
 * @param {function(function(string), ?, !Module)} moduleDef
 * @param {string} absModulePath
 * @param {!Array<string>} shallowDeps
 * @suppress {strictMissingProperties} "ensure" is not declared.
 */
$jscomp.registerAndLoadModule = function(
    moduleDef, absModulePath, shallowDeps) {
  $jscomp.require.ensure([absModulePath], function(require) {
    require(absModulePath);
  });
  $jscomp.registerModule(moduleDef, absModulePath, shallowDeps);
};


/**
 * Registers an object as if it is the exports of an ES6 module so that it may
 * be retrieved via $jscomp.require.
 *
 * Used by Closure Library in the event that only some ES6 modules need
 * transpilation.
 *
 * @param {string} absModulePath
 * @param {?} exports
 */
$jscomp.registerEs6ModuleExports = function(absModulePath, exports) {
  if (moduleCache.has(absModulePath)) {
    throw new Error(
        'Module at path ' + absModulePath + ' is already registered.');
  }

  var entry =
      new CacheEntry(null, new Module(absModulePath, exports), absModulePath);
  moduleCache.set(absModulePath, entry);
  markAvailable(absModulePath);
};


/**
 * Hook to clear all loaded modules. Meant to only be used by tests.
 */
$jscomp.clearModules = function() {
  moduleCache.clear();
};
})();
