/*
 * Copyright 2016 The Closure Compiler Authors.
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
 * @fileoverview An externs file for the Angular Material library.
 * @externs
 */


/**
 * @const
 */
var md = {};


/******************************************************************************
 * $bottonSheet Service
 *****************************************************************************/


/** @interface */
md.$bottomSheet = function() {};

/**
 * @typedef {{
 *   templateUrl: (string|!Object|undefined),
 *   template: (string|undefined),
 *   scope: (!Object|undefined),
 *   preserveScope: (boolean|undefined),
 *   controller: (!angular.Injectable|string|undefined),
 *   locals: (!Object|undefined),
 *   targetEvent: (!Object|undefined),
 *   resolve: (!Object|undefined),
 *   controllerAs: (string|undefined),
 *   parent: (!Element|undefined),
 *   disableParentScroll: (boolean|undefined),
 *   bindToController: (boolean|undefined),
 * }}
 */
md.$bottomSheet.options;


/**
 * @param {!md.$bottomSheet.options} options
 * @return {!angular.$q.Promise}
 */
md.$bottomSheet.prototype.show = function(options) {};


/**
 * @param {*=} opt_response An argument for the resolved promise.
 */
md.$bottomSheet.prototype.hide = function(opt_response) {};


/**
 * @param {*=} opt_response An argument for the resolved promise.
 */
md.$bottomSheet.prototype.cancel = function(opt_response) {};


/******************************************************************************
 * $dialog Service
 *****************************************************************************/


/** @interface */
md.$dialog = function() {};


/**
 * @typedef {{
 *   templateUrl: (string|!Object|undefined),
 *   template: (string|undefined),
 *   contentElement: (string|Element|undefined),
 *   autoWrap: (boolean|undefined),
 *   targetEvent: (Object|undefined),
 *   openFrom: (string|!Element|!Object|undefined),
 *   closeTo: (string|!Element|!Object|undefined),
 *   scope: (!angular.Scope|undefined),
 *   preserveScope: (boolean|undefined),
 *   disableParentScroll: (boolean|undefined),
 *   hasBackdrop: (boolean|undefined),
 *   clickOutsideToClose: (boolean|undefined),
 *   escapeToClose: (boolean|undefined),
 *   focusOnOpen: (boolean|undefined),
 *   controller: (angular.Injectable|string|undefined),
 *   locals: (Object|undefined),
 *   bindToController: (boolean|undefined),
 *   resolve: (Object|undefined),
 *   controllerAs: (string|undefined),
 *   parent: (angular.JQLite|Element|undefined),
 *   onShowing: (Function|undefined),
 *   onComplete: (Function|undefined),
 *   onRemoving: (Function|undefined),
 *   fullscreen: (boolean|undefined)
 * }}
 */
md.$dialog.options;


/** @interface */
md.$dialog.AlertConfig_ = function() {};

/**
 * @param {string} title
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.title = function(title) {};

/**
 * @deprecated `$mdDialog.alert().content` is deprecated!
 *     Use textContent or htmlContent instead.
 * @param {?string} textContent
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.content = function(textContent) {};

/**
 * @param {?string} textContent
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.textContent = function(textContent) {};

/**
 * @param {string} htmlContent
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.htmlContent = function(htmlContent) {};

/**
 * @param {string} ariaLabel
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.ariaLabel = function(ariaLabel) {};

/**
 * @param {string} ok
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.ok = function(ok) {};

/**
 * @param {string} theme
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.theme = function(theme) {};

/**
 * @param {boolean} close
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.clickOutsideToClose = function(close) {};

/**
 * @param {boolean} close
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.escapeToClose = function(close) {};

/**
 * @param {boolean} multiple
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.multiple = function(multiple) {};

/**
 * @param {Object|undefined} event
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.targetEvent = function(event) {};

/**
 * @param {string} css
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.css = function(css) {};

/**
 * @param {angular.JQLite|Element|undefined} parent
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.AlertConfig_.prototype.parent = function(parent) {};


/** @interface */
md.$dialog.ConfirmConfig_ = function() {};

/**
 * @param {string} title
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.title = function(title) {};

/**
 * @deprecated `$mdDialog.confirm().content` is deprecated!
 *     Use textContent or htmlContent instead.
 * @param {?string} textContent
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.content = function(textContent) {};

/**
 * @param {?string} textContent
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.textContent = function(textContent) {};

/**
 * @param {string} htmlContent
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.htmlContent = function(htmlContent) {};

/**
 * @param {string} ariaLabel
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.ariaLabel = function(ariaLabel) {};

/**
 * @param {string} ok
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.ok = function(ok) {};

/**
 * @param {string} cancel
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.cancel = function(cancel) {};

/**
 * @param {string} theme
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.theme = function(theme) {};

/**
 * @param {boolean} close
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.clickOutsideToClose = function(close) {};

/**
 * @param {boolean} close
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.escapeToClose = function(close) {};

/**
 * @param {boolean} multiple
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.multiple = function(multiple) {};

/**
 * @param {Object|undefined} event
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.targetEvent = function(event) {};

/**
 * @param {angular.JQLite|Element|undefined} parent
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.parent = function(parent) {};

/**
 * @param {string} css
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.css = function(css) {};

/**
 * @param {boolean} scroll
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.ConfirmConfig_.prototype.disableParentScroll = function(scroll) {};


/** @interface */
md.$dialog.PromptConfig_ = function() {};

/**
 * @param {string} title
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.title = function(title) {};

/**
 * @deprecated `$mdDialog.prompt().content` is deprecated!
 *     Use textContent or htmlContent instead.
 * @param {?string} textContent
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.content = function(textContent) {};

/**
 * @param {?string} textContent
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.textContent = function(textContent) {};

/**
 * @param {string} htmlContent
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.htmlContent = function(htmlContent) {};

/**
 * @param {string} ariaLabel
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.ariaLabel = function(ariaLabel) {};

/**
 * @param {string} placeholder
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.placeholder = function(placeholder) {};

/**
 * @param {string} initialValue
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.initialValue = function(initialValue) {};

/**
 * @param {string} ok
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.ok = function(ok) {};

/**
 * @param {string} cancel
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.cancel = function(cancel) {};

/**
 * @param {string} theme
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.theme = function(theme) {};

/**
 * @param {boolean} close
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.clickOutsideToClose = function(close) {};

/**
 * @param {boolean} close
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.escapeToClose = function(close) {};

/**
 * @param {boolean} multiple
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.multiple = function(multiple) {};

/**
 * @param {Object|undefined} event
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.targetEvent = function(event) {};

/**
 * @param {angular.JQLite|Element|undefined} parent
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.parent = function(parent) {};

/**
 * @param {string} css
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.css = function(css) {};

/**
 * @param {boolean} required
 * @return {!md.$dialog.PromptConfig_}
 */
md.$dialog.PromptConfig_.prototype.required = function(required) {};

/** @typedef {!md.$dialog.options|!md.$dialog.ConfirmConfig_|
 *     !md.$dialog.AlertConfig_|!md.$dialog.PromptConfig_} */
md.$dialog.Config;


/**
 * @param {!md.$dialog.Config} options
 * @return {!angular.$q.Promise}
 */
md.$dialog.prototype.show = function(options) {};


/**
 * @param {*=} opt_response An argument for the resolved promise.
 */
md.$dialog.prototype.hide = function(opt_response) {};


/**
 * @param {*=} opt_response An argument for the resolved promise.
 */
md.$dialog.prototype.cancel = function(opt_response) {};


/**
 * @param {!md.$dialog.Config=} options
 * @return {!md.$dialog.AlertConfig_}
 */
md.$dialog.prototype.alert = function(options) {};


/**
 * @param {!md.$dialog.Config=} options
 * @return {!md.$dialog.ConfirmConfig_}
 */
md.$dialog.prototype.confirm = function(options) {};


/**
 * @param {!md.$dialog.Config=} options
 * @return {!md.$dialog.PromptConfig_}
*/
md.$dialog.prototype.prompt = function(options) {};


/******************************************************************************
 * $toast Service
 *****************************************************************************/


/** @constructor */
md.$toast = function() {};


/**
 * @typedef {{
 *   templateUrl: (string|!Object|undefined),
 *   template: (string|undefined),
 *   hideDelay: (boolean|number|undefined),
 *   position: (string|undefined),
 *   controller: (angular.Injectable|string|undefined),
 *   locals: (Object|undefined),
 *   bindToController: (boolean|undefined),
 *   resolve: (Object|undefined),
 *   controllerAs: (string|undefined),
 *   toastClass: (string|undefined),
 * }}
 */
md.$toast.options;


/**
 * @param {md.$toast.options|md.$toast.preset} optionsOrPreset
 * @return {!angular.$q.Promise}
 */
md.$toast.prototype.show = function(optionsOrPreset) {};


/**
 * @param {string} text
 * @return {!angular.$q.Promise}
 */
md.$toast.prototype.showSimple = function(text) {};


/**
 * @param {*=} opt_response An argument for the resolved promise.
 */
md.$toast.prototype.hide = function(opt_response) {};


/**
 * @param {*=} opt_response An argument for the resolved promise.
 */
md.$toast.prototype.cancel = function(opt_response) {};


/** @param {string} content */
md.$toast.prototype.updateTextContent = function(content) {};


/** @record */
md.$toast.preset = function() {};

/**
 * @param {string} action
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.action = function(action) {};

/**
 * @param {string} actionKey
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.actionKey = function(actionKey) {};

/**
 * @param {boolean} capsule
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.capsule = function(capsule) {};

/**
 * @param {string} content
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.content = function(content) {};

/**
 * @param {boolean|number} delay
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.hideDelay = function(delay) {};

/**
 * @param {string} content
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.textContent = function(content) {};

/**
 * @param {boolean} highlight
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.highlightAction = function(highlight) {};

/**
 * @param {string} theme
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.theme = function(theme) {};

/**
 * @param {?JQLiteSelector} parent
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.parent = function(parent) {};

/**
 * @param {string} position
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.position = function(position) {};

/**
 * @param {string} className
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.highlightClass = function(className) {};

/**
 * @param {string} className
 * @return {!md.$toast.preset}
 */
md.$toast.preset.prototype.toastClass = function(className) {};


/**
 * @param {!md.$toast.options=} options
 * @return {!md.$toast.preset}
 */
md.$toast.prototype.simple = function(options) {};

/******************************************************************************
 * $sidenav Service
 *****************************************************************************/


/**
 * @typedef {{
 *   isLockedOpen: function():boolean,
 *   isOpen: function():boolean,
 *   toggle: function(),
 *   open: function(),
 *   close: function(),
 *   onClose: function(function()),
 * }}
 */
md._sidenavService;

/**
 * Sidenav service is actually a function that returns an object.
 * @typedef {
 *   function(string, boolean=):md._sidenavService
 * }
 */
md.$sidenav;


/******************************************************************************
 * $mdAriaProvider service
 *****************************************************************************/

/** @interface */
md.$mdAriaProvider = function() {};

md.$mdAriaProvider.prototype.disableWarnings = function() {};

/******************************************************************************
 * $mdThemingProvider
 *****************************************************************************/

/** @interface */
md.$mdThemingProvider = function() {};

/** @param {boolean} alwaysWatch */
md.$mdThemingProvider.prototype.alwaysWatchTheme = function(alwaysWatch) {};

/** @param {boolean} onDemand */
md.$mdThemingProvider.prototype.generateThemesOnDemand = function(onDemand) {};

/**
 * @param {string} name
 * @param {!Object<string,string>} palette
 * @return {!md.$mdThemingProvider}
 */
md.$mdThemingProvider.prototype.definePalette = function(name, palette) {};

/**
 * @param {string} name
 * @param {!Object<string,string>} extension
 * @return {!Object<string,string>}
 */
md.$mdThemingProvider.prototype.extendPalette = function(name, extension) {};

/** @param {string} name */
md.$mdThemingProvider.prototype.setDefaultTheme = function(name) {};

/**
 * @param {string} name
 * @param {string=} opt_parentTheme
 * @return {!md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.prototype.theme = function(name, opt_parentTheme) {};

/** @param {string} nonce */
md.$mdThemingProvider.prototype.setNonce = function(nonce) {};

/** @param {string} styles */
md.$mdThemingProvider.prototype.registerStyles = function(styles) {};

/**
 * @param {?Object=} options
 * @return {!Function}
 */
md.$mdThemingProvider.prototype.enableBrowserColor = function(options) {};

/******************************************************************************
 * $mdTheming service
 *****************************************************************************/

/** @interface */
md.$mdTheming = function() {};

/**
 * @param {string} name
 * @return {boolean}
 */
md.$mdTheming.prototype.registered = function(name) {};

/** @return {string} */
md.$mdTheming.prototype.defaultTheme = function() {};

/** @param {string} name */
md.$mdTheming.prototype.generateTheme = function(name) {};

/** @type {boolean} */
md.$mdTheming.prototype.ALWAYS_WATCH;

/** @type {!Object<string, !Object<string,string>>} */
md.$mdTheming.prototype.PALETTES;

/** @type {!Object<string, !md.$mdThemingProvider.Theme>} */
md.$mdTheming.prototype.THEMES;

/******************************************************************************/


/**
 * @param {string} name
 * @interface
 */
md.$mdThemingProvider.Theme = function(name) {};

/** @type {string} */
md.$mdThemingProvider.Theme.prototype.name;

/** @type {!Object<string,string>} */
md.$mdThemingProvider.Theme.prototype.colors;

/** @type {boolean} */
md.$mdThemingProvider.Theme.prototype.isDark;

/**
 * @param {string} primaryPalette
 * @param {Object<string,string>=} opt_hues
 * @return {!md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.primaryPalette =
    function(primaryPalette, opt_hues) {};

/**
 * @param {string} accentPalette
 * @param {Object<string,string>=} opt_hues
 * @return {!md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.accentPalette =
    function(accentPalette, opt_hues) {};

/**
 * @param {string} backgroundPalette
 * @param {Object<string,string>=} opt_hues
 * @return {!md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.backgroundPalette =
    function(backgroundPalette, opt_hues) {};

/**
 * @param {string} warnPalette
 * @param {Object<string,string>=} opt_hues
 * @return {!md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.warnPalette =
     function(warnPalette, opt_hues) {};

/**
 * @param {boolean=} opt_isDark
 * @return {!md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.dark = function(opt_isDark) {};

/******************************************************************************
 * $mdColors Service
 *****************************************************************************/

/** @interface */
md.$mdColors = function() {};

/**
 * @param {!angular.JQLite} element
 * @param {?Object} colorExpression
 */
md.$mdColors.prototype.applyThemeColors = function(element, colorExpression) {};

/**
 * @param {string} expression
 * @return {string}
 */
md.$mdColors.prototype.getThemeColor = function(expression) {};

/******************************************************************************
 * $mdIcon Service
 *****************************************************************************/

/** @typedef {function(string): !angular.$q.Promise<!Element>} */
md.$mdIcon;


/******************************************************************************
 * $mdIconProvider Service
 *****************************************************************************/

/** @interface */
md.$mdIconProvider = function() {};

/**
 * @param {string} id
 * @param {string|!Object} url
 * @param {number=} opt_iconSize
 * @return {!md.$mdIconProvider}
 */
md.$mdIconProvider.prototype.icon = function(id, url, opt_iconSize) {};

/**
 * @param {string} id
 * @param {string|!Object} url
 * @param {number=} opt_iconSize
 * @return {!md.$mdIconProvider}
 */
md.$mdIconProvider.prototype.iconSet = function(id, url, opt_iconSize) {};

/**
 * @param {string|!Object} url
 * @param {number=} opt_iconSize
 * @return {!md.$mdIconProvider}
 */
md.$mdIconProvider.prototype.defaultIconSet = function(url, opt_iconSize) {};

/**
 * @param {number} iconSize
 * @return {!md.$mdIconProvider}
 */
md.$mdIconProvider.prototype.defaultIconSize = function(iconSize) {};

/**
 * @param {string} name
 * @return {!md.$mdIconProvider}
 */
md.$mdIconProvider.prototype.defaultFontSet = function(name) {};

/******************************************************************************
 * $mdMedia Service
 *****************************************************************************/

/**
 * @typedef {function(string): boolean}
 */
md.$mdMedia;

/******************************************************************************
 * $mdMenu Service
 *****************************************************************************/

/** @interface */
md.$mdMenu = function() {};

/**
 * @param {*=} opt_reason Data to resolve the promise with.
 * @param {*=} opt_options
 * @return {!angular.$q.Promise}
 */
md.$mdMenu.prototype.hide = function(opt_reason, opt_options) {};

/******************************************************************************
 * $mdMenu that is provided to the scope of the trigger element so that the
 * menu can be closed/opened from the template.
 * https://material.angularjs.org/latest/api/directive/mdMenu
 *****************************************************************************/

/** @interface */
md.menu = function() {};

/** @param {!Event=} opt_event */
md.menu.prototype.open = function(opt_event) {};

/**
 * @param {boolean=} opt_skipFocus
 * @param {*=} opt_closeOptions
 */
md.menu.prototype.close = function(opt_skipFocus, opt_closeOptions) {};

/******************************************************************************
 * $mdSelect Service
 *****************************************************************************/

/** @interface */
md.$select = function() {};

/**
 * @param {*} options
 * @return {!angular.$q.Promise}
 */
md.$select.prototype.show = function(options) {};

/**
 * @param {VALUE=} opt_reason Data to resolve the promise with.
 * @param {*=} opt_options
 * @return {!angular.$q.Promise<VALUE>}
 * @template VALUE
 */
md.$select.prototype.hide = function(opt_reason, opt_options) {};

/**
 * @param {VALUE=} opt_reason Data to reject the promise with.
 * @param {*=} opt_options
 * @return {!angular.$q.Promise<VALUE>}
 * @template VALUE
 */
md.$select.prototype.cancel = function(opt_reason, opt_options) {};

/******************************************************************************
 * $mdDateLocaleProvider
 *****************************************************************************/

/** @interface */
md.$mdDateLocaleProvider = function() {};

/** @type {Array<string>} */
md.$mdDateLocaleProvider.prototype.months;

/** @type {Array<string>} */
md.$mdDateLocaleProvider.prototype.shortMonths;

/** @type {Array<string>} */
md.$mdDateLocaleProvider.prototype.days;

/** @type {Array<string>} */
md.$mdDateLocaleProvider.prototype.shortDays;

/** @type {number} */
md.$mdDateLocaleProvider.prototype.firstDayOfWeek;

/** @type {Array<string|number>} */
md.$mdDateLocaleProvider.prototype.dates;

/** @type {string} */
md.$mdDateLocaleProvider.prototype.msgCalendar;

/** @type {string} */
md.$mdDateLocaleProvider.prototype.msgOpenCalendar;

// The functions on $mdDateLocaleProvider are defined as fields because
// they are meant to be directly set by consuming code.

/** @type {function(string): Date} */
md.$mdDateLocaleProvider.prototype.parseDate = function(dateString) {};

/** @type {function(Date): string} */
md.$mdDateLocaleProvider.prototype.formatDate = function(date) {};

/** @type {function(Date): string} */
md.$mdDateLocaleProvider.prototype.monthHeaderFormatter = function(date) {};

/** @type {function(number): string} */
md.$mdDateLocaleProvider.prototype.weekNumberFormatter = function(weekNum) {};

/** @type {function(Date): string} */
md.$mdDateLocaleProvider.prototype.longDateFormatter = function(date) {};


/******************************************************************************
 * $mdGestureProvider
 *****************************************************************************/

/** @interface */
md.$mdGestureProvider = function() {};

/**
 * @return {boolean}
 */
md.$mdGestureProvider.prototype.skipClickHijack = function() {};

/******************************************************************************
 * $mdInkRippleProvider
 *****************************************************************************/

/** @interface */
md.$mdInkRippleProvider = function() {};


md.$mdInkRippleProvider.prototype.disableInkRipple = function() {};


/******************************************************************************
 * VirtualRepeatContainerController
 *****************************************************************************/

/** @interface */
md.VirtualRepeatContainerController = function() {};

/** @return {boolean} */
md.VirtualRepeatContainerController.prototype.isHorizontal = function() {};

/** @return {number} */
md.VirtualRepeatContainerController.prototype.getSize = function() {};

/** @return {number} */
md.VirtualRepeatContainerController.prototype.getScrollSize = function() {};

/** @return {number} */
md.VirtualRepeatContainerController.prototype.getScrollOffset = function() {};

/** @param {number} position */
md.VirtualRepeatContainerController.prototype.scrollTo = function(position) {};

/** @param {number} index */
md.VirtualRepeatContainerController.prototype.scrollToIndex =
    function(index) {};

md.VirtualRepeatContainerController.prototype.resetScroll = function() {};


/******************************************************************************
 * VirtualRepeatController
 *****************************************************************************/

/** @interface */
md.VirtualRepeatController = function() {};

/** @return {?number} */
md.VirtualRepeatController.prototype.getItemSize = function() {};


/******************************************************************************
 * $panel Service
 *****************************************************************************/

/** @constructor */
md.$panel = function() {};

/**
 * @typedef {{
 *   template: (string|undefined),
 *   templateUrl: (string|!Object|undefined),
 *   controller: (angular.Injectable|string|undefined),
 *   controllerAs: (string|undefined),
 *   locals: (!Object|undefined),
 *   resolve: (!Object|undefined),
 *   attachTo: (!angular.JQLite|!Element|undefined),
 *   propagateContainerEvents: (boolean|undefined),
 *   panelClass: (string|undefined),
 *   position: (!md.$panel.MdPanelPosition|undefined),
 *   clickOutsideToClose: (boolean|undefined),
 *   escapeToClose: (boolean|undefined),
 *   trapFocus: (boolean|undefined),
 *   focusOnOpen: (boolean|undefined),
 *   fullscreen: (boolean|undefined),
 *   animation: (!md.$panel.MdPanelAnimation|undefined),
 *   hasBackdrop: (boolean|undefined),
 *   disableParentScroll: (boolean|undefined),
 *   onDomAdded: (Function|undefined),
 *   onOpenComplete: (Function|undefined),
 *   onRemoving: (Function|undefined),
 *   onDomRemoved: (Function|undefined),
 *   origin: (!angular.JQLite|!Element|undefined),
 *   onCloseSuccess: (function(!md.$panel.MdPanelRef, string)|undefined),
 *   groupName: (string|!Array<string>|undefined),
 * }}
 */
md.$panel.config;

/**
 * @param {!md.$panel.config=} opt_config
 * @return {!md.$panel.MdPanelRef}
 */
md.$panel.prototype.create = function(opt_config) {};

/**
 * @param {!md.$panel.config=} opt_config
 * @return {!angular.$q.Promise<!md.$panel.MdPanelRef>}
 */
md.$panel.prototype.open = function(opt_config) {};

/** @return {!md.$panel.MdPanelPosition} */
md.$panel.prototype.newPanelPosition = function() {};

/** @return {!md.$panel.MdPanelAnimation} */
md.$panel.prototype.newPanelAnimation = function() {};

/**
 * @param {string} groupName
 * @param {{maxOpen: (number|undefined)}=} opt_config
 * @return {{
 *   panels: !Array<!md.$panel.MdPanelRef>,
 *   openPanels: !Array<!md.$panel.MdPanelRef>,
 *   maxOpen: number,
 * }}
 */
md.$panel.prototype.newPanelGroup = function(groupName, opt_config) {};

/**
 * @param {string} groupName
 * @param {number} maxOpen
 */
md.$panel.prototype.setGroupMaxOpen = function(groupName, maxOpen) {};

/**
 * Possible values of xPosition.
 * @enum {string}
 */
md.$panel.prototype.xPosition = {
  CENTER: 'center',
  ALIGN_START: 'align-start',
  ALIGN_END: 'align-end',
  OFFSET_START: 'offset-start',
  OFFSET_END: 'offset-end',
};

/**
 * Possible values of yPosition.
 * @enum {string}
 */
md.$panel.prototype.yPosition = {
  CENTER: 'center',
  ALIGN_TOPS: 'align-tops',
  ALIGN_BOTTOMS: 'align-bottoms',
  ABOVE: 'above',
  BELOW: 'below',
};

/**
 * Possible values of absPosition.
 * @enum {string}
 */
md.$panel.prototype.absPosition = {
  TOP: 'top',
  RIGHT: 'right',
  BOTTOM: 'bottom',
  LEFT: 'left',
};

/**
 * Possible default animations.
 * @enum {string}
 */
md.$panel.prototype.animation = {
  SLIDE: 'md-panel-animate-slide',
  SCALE: 'md-panel-animate-scale',
  FADE: 'md-panel-animate-fade',
};

/**
 * Possible types of interceptors.
 * @enum {string}
 */
md.$panel.prototype.interceptorTypes = {
  CLOSE: 'onClose',
};

/**
 * Possible reasons for close.
 * @enum {string}
 */
md.$panel.prototype.closeReasons = {
  CLICK_OUTSIDE: 'clickOutsideToClose',
  ESCAPE: 'escapeToClose',
};


/**
 * @param {!md.$panel.config} config
 * @param {!angular.$injector} $injector
 * @interface
 */
md.$panel.MdPanelRef = function(config, $injector) {};

/** @type {string} */
md.$panel.MdPanelRef.prototype.id;

/** @type {!md.$panel.config} */
md.$panel.MdPanelRef.prototype.config;

/** @type {!angular.JQLite|undefined} */
md.$panel.MdPanelRef.prototype.panelContainer;

/** @type {!angular.JQLite|undefined} */
md.$panel.MdPanelRef.prototype.panelEl;

/** @type {boolean} */
md.$panel.MdPanelRef.prototype.isAttached;

/** @return {!angular.$q.Promise<!md.$panel.MdPanelRef>} */
md.$panel.MdPanelRef.prototype.open = function() {};

/** @return {!angular.$q.Promise<!md.$panel.MdPanelRef>} */
md.$panel.MdPanelRef.prototype.close = function() {};

/** @return {!angular.$q.Promise<!md.$panel.MdPanelRef>} */
md.$panel.MdPanelRef.prototype.attach = function() {};

/** @return {!angular.$q.Promise<!md.$panel.MdPanelRef>} */
md.$panel.MdPanelRef.prototype.detach = function() {};

/** @return {!angular.$q.Promise<!md.$panel.MdPanelRef>} */
md.$panel.MdPanelRef.prototype.show = function() {};

/** @return {!angular.$q.Promise<!md.$panel.MdPanelRef>} */
md.$panel.MdPanelRef.prototype.hide = function() {};

md.$panel.MdPanelRef.prototype.destroy = function() {};

/**
 * @param {string} type
 * @param {function(): !angular.$q.Promise<*>} callback
 * @return {!md.$panel.MdPanelRef}
 */
md.$panel.MdPanelRef.prototype.registerInterceptor =
    function(type, callback) {};

/**
 * @param {string} type
 * @param {function(): !angular.$q.Promise<*>} callback
 * @return {!md.$panel.MdPanelRef}
 */
md.$panel.MdPanelRef.prototype.removeInterceptor = function(type, callback) {};

/**
 * @param {string=} opt_type
 * @return {!md.$panel.MdPanelRef}
 */
md.$panel.MdPanelRef.prototype.removeAllInterceptors = function(opt_type) {};

/**
 * @param {string} classToAdd
 * @param {boolean} toElement
 */
md.$panel.MdPanelRef.prototype.addClass =
    function(classToAdd, toElement) {};

/**
 * @param {string} classToRemove
 * @param {boolean} fromElement
 */
md.$panel.MdPanelRef.prototype.removeClass =
    function(classToRemove, fromElement) {};

/**
 * @param {string} classToToggle
 * @param {boolean} onElement
 */
md.$panel.MdPanelRef.prototype.toggleClass =
    function(classToToggle, onElement) {};

/** @param {!md.$panel.MdPanelPosition} position */
md.$panel.MdPanelRef.prototype.updatePosition = function(position) {};

/**
 * @param {!angular.$window} $window
 * @interface
 */
md.$panel.MdPanelPosition = function($window) {};

/** @return {!md.$panel.MdPanelPosition} */
md.$panel.MdPanelPosition.prototype.absolute = function() {};

/**
 * @param {string|!Element|!angular.JQLite} element
 * @return {!md.$panel.MdPanelPosition}
 */
md.$panel.MdPanelPosition.prototype.relativeTo = function(element) {};

/**
 * @param {string=} opt_top
 * @return {!md.$panel.MdPanelPosition}
 */
md.$panel.MdPanelPosition.prototype.top = function(opt_top) {};

/**
 * @param {string=} opt_bottom
 * @return {!md.$panel.MdPanelPosition}
 */
md.$panel.MdPanelPosition.prototype.bottom = function(opt_bottom) {};

/**
 * @param {string=} opt_left
 * @return {!md.$panel.MdPanelPosition}
 */
md.$panel.MdPanelPosition.prototype.left = function(opt_left) {};

/**
 * @param {string=} opt_right
 * @return {!md.$panel.MdPanelPosition}
 */
md.$panel.MdPanelPosition.prototype.right = function(opt_right) {};

/** @return {!md.$panel.MdPanelPosition} */
md.$panel.MdPanelPosition.prototype.centerHorizontally = function() {};

/** @return {!md.$panel.MdPanelPosition} */
md.$panel.MdPanelPosition.prototype.centerVertically = function() {};

/** @return {!md.$panel.MdPanelPosition} */
md.$panel.MdPanelPosition.prototype.center = function() {};

/**
 * @param {string} xPosition
 * @param {string} yPosition
 * @return {!md.$panel.MdPanelPosition}
 */
md.$panel.MdPanelPosition.prototype.addPanelPosition =
    function(xPosition, yPosition) {};

/**
 * @param {string|function(md.$panel.MdPanelPosition): string} offsetX
 * @return {!md.$panel.MdPanelPosition}
 */
md.$panel.MdPanelPosition.prototype.withOffsetX = function(offsetX) {};

/**
 * @param {string|function(md.$panel.MdPanelPosition): string} offsetY
 * @return {!md.$panel.MdPanelPosition}
 */
md.$panel.MdPanelPosition.prototype.withOffsetY = function(offsetY) {};

/**
 * @param {!angular.$injector} $injector
 * @interface
 */
md.$panel.MdPanelAnimation = function($injector) {};

/**
 * @param {string|!Element|!Event|{top: number, left: number}} openFrom
 * @return {!md.$panel.MdPanelAnimation}
 */
md.$panel.MdPanelAnimation.prototype.openFrom = function(openFrom) {};

/**
 * @param {string|!Element|{top: number, left: number}} closeTo
 * @return {!md.$panel.MdPanelAnimation}
 */
md.$panel.MdPanelAnimation.prototype.closeTo = function(closeTo) {};

/**
 * @param {number|{open: number, close: number}} duration
 * @returns {!md.$panel.MdPanelAnimation}
 */
md.$panel.MdPanelAnimation.prototype.duration = function(duration) {};

/**
 * @param {string|{open: string, close: string}} cssClass
 * @return {!md.$panel.MdPanelAnimation}
 */
md.$panel.MdPanelAnimation.prototype.withAnimation = function(cssClass) {};

/******************************************************************************
 * DatePickerCtrl
 *****************************************************************************/

/** @interface */
md.DatePickerCtrl = function() {};

/**
 * @param {Date=} opt_date
 */
md.DatePickerCtrl.prototype.updateErrorState = function(opt_date) {};
