/**
 * @license
 * Copyright The Closure Library Authors.
 * Copyright The Closure Compiler Authors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * @fileoverview ES6 module exporting symbols from base.js. Provided here for
 * legacy applications that need to move off of Closure Library and need to
 * continue using ES6 modules. In general, `goog.module` files are recommended
 * as they are better supported in the Closure Compiler.
 *
 * A special compiler pass enforces that you always import this file as
 * `import * as goog`.
 */

export const require = goog.require;
export const define = goog.define;
export const DEBUG = goog.DEBUG;
export const LOCALE = goog.LOCALE;
export const getGoogModule = goog.module.get;
export const forwardDeclare = goog.forwardDeclare;
export const getCssName = goog.getCssName;
export const setCssNameMapping = goog.setCssNameMapping;
export const getMsg = goog.getMsg;
export const getMsgWithFallback = goog.getMsgWithFallback;
export const exportSymbol = goog.exportSymbol;
export const exportProperty = goog.exportProperty;

// Export select properties of module. Do not export the function itself or
// goog.module.declareLegacyNamespace.
export const module = {
  get: goog.module.get,
};
