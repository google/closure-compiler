/*
 * Copyright 2022 The Closure Compiler Authors.
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
 * @fileoverview Definitions for the API related to accessibility.
 * Definitions for Accessible Rich Internet Applications suit (WAI-ARIA).
 * This file is based on the W3C Candidate Recommendation Draft 08 December
 * 2021.
 * @see https://www.w3.org/TR/wai-aria-1.2/
 *
 * @externs
 */

/**
 * @interface
 * @mixin
 * @see https://www.w3.org/TR/wai-aria-1.2/#ARIAMixin
 */
function ARIAMixin() {}

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#introroles
 */
ARIAMixin.prototype.role;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-atomic
 */
ARIAMixin.prototype.ariaAtomic;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-autocomplete
 */
ARIAMixin.prototype.ariaAutoComplete;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-busy
 */
ARIAMixin.prototype.ariaBusy;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-checked
 */
ARIAMixin.prototype.ariaChecked;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-colcount
 */
ARIAMixin.prototype.ariaColCount;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-colindex
 */
ARIAMixin.prototype.ariaColIndex;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-colspan
 */
ARIAMixin.prototype.ariaColSpan;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-current
 */
ARIAMixin.prototype.ariaCurrent;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-disabled
 */
ARIAMixin.prototype.ariaDisabled;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-expanded
 */
ARIAMixin.prototype.ariaExpanded;


/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-haspopup
 */
ARIAMixin.prototype.ariaHasPopup;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-hidden
 */
ARIAMixin.prototype.ariaHidden;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-invalid
 */
ARIAMixin.prototype.ariaInvalid;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-keyshortcuts
 */
ARIAMixin.prototype.ariaKeyShortcuts;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-label
 */
ARIAMixin.prototype.ariaLabel;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-level
 */
ARIAMixin.prototype.ariaLevel;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-live
 */
ARIAMixin.prototype.ariaLive;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-modal
 */
ARIAMixin.prototype.ariaModal;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-multiline
 */
ARIAMixin.prototype.ariaMultiLine;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-multiselectable
 */
ARIAMixin.prototype.ariaMultiSelectable;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-orientation
 */
ARIAMixin.prototype.ariaOrientation;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-placeholder
 */
ARIAMixin.prototype.ariaPlaceholder;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-posinset
 */
ARIAMixin.prototype.ariaPosInSet;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-pressed
 */
ARIAMixin.prototype.ariaPressed;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-readonly
 */
ARIAMixin.prototype.ariaReadOnly;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-required
 */
ARIAMixin.prototype.ariaRequired;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-roledescription
 */
ARIAMixin.prototype.ariaRoleDescription;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-rowcount
 */
ARIAMixin.prototype.ariaRowCount;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-rowindex
 */
ARIAMixin.prototype.ariaRowIndex;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-rowspan
 */
ARIAMixin.prototype.ariaRowSpan;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-selected
 */
ARIAMixin.prototype.ariaSelected;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-setsize
 */
ARIAMixin.prototype.ariaSetSize;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-sort
 */
ARIAMixin.prototype.ariaSort;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-valuemax
 */
ARIAMixin.prototype.ariaValueMax;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-valuemin
 */
ARIAMixin.prototype.ariaValueMin;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-valuenow
 */
ARIAMixin.prototype.ariaValueNow;

/**
 * @type {string|undefined}
 * @see https://www.w3.org/TR/wai-aria-1.2/#aria-valuetext
 */
ARIAMixin.prototype.ariaValueText;