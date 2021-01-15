/**
 * MIT License
 *
 * Copyright (c) 2021 Peter Tillema
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/**
 * @typedef {Object}
 * @property {string|DocObj|undefined} value
 * @property {string|Object|undefined} mode
 * @property {?string|undefined} lineSeparator
 * @property {string|undefined} theme
 * @property {number|undefined} indentUnit
 * @property {boolean|undefined} smartIndent
 * @property {number|undefined} tabSize
 * @property {boolean|undefined} indentWithTabs
 * @property {boolean|undefined} electricChars
 * @property {RegExp|undefined} specialChars
 * @property {undefined|function(RegExp): Element} specialCharPlaceholder
 * @property {('ltr'|'rtl')|undefined} direction
 * @property {boolean|undefined} rtlMoveVisually
 * @property {string|undefined} keyMap
 * @property {Object|undefined} extraKeys
 * @property {undefined|function(CodeMirror, ('single'|'double'|'triple'), Event): {unit: ('char'|'word'|'line'|'rectangle')}} configureMouse
 */
let optionsObj;

/**
 * @typedef {{line: number, ch: number}}
 */
let lineCharObj;

/**
 * @constructor
 */
let LineHandle = function() {};

/**
 * @constructor
 */
let LineWidget = function() {};
/**
 * @type {number}
 */
LineWidget.prototype.line = 0;
/**
 */
LineWidget.prototype.clear = function() {};
/**
 */
LineWidget.prototype.changed = function() {};

/**
 * @constructor
 */
let CodeMirrorObj = function() {};
/**
 * @returns {boolean}
 */
CodeMirrorObj.prototype.hasFocus = function() {};
/**
 * @param {lineCharObj} start
 * @param {number} amount
 * @param {string} unit
 * @param {boolean} visually
 * @returns {{line: number, ch: number, hitside: (boolean|undefined)}}
 */
CodeMirrorObj.prototype.findPosH = function(start, amount, unit, visually) {};
/**
 * @param {lineCharObj} start
 * @param {number} amount
 * @param {boolean} visually
 * @returns {{line: number, ch: number, hitside: (boolean|undefined)}}
 */
CodeMirrorObj.prototype.findPosV = function(start, amount, visually) {};
/**
 * @param {lineCharObj} pos
 * @returns {{anchor: lineCharObj, head: lineCharObj}}
 */
CodeMirrorObj.prototype.findWordAt = function(pos) {};
/**
 * @param {string} option
 * @param {*} value
 */
CodeMirrorObj.prototype.setOption = function(option, value) {};
/**
 * @param {string} option
 * @returns {*}
 */
CodeMirrorObj.prototype.getOption = function(option) {};
/**
 * @param {Object} map
 * @param {boolean} bottom
 */
CodeMirrorObj.prototype.addKeyMap = function(map, bottom) {};
/**
 * @param {Object} map
 */
CodeMirrorObj.prototype.removeKeyMap = function(map) {};
/**
 * @param {string|Object} mode
 * @param {{opaque: (boolean|undefined), priority: (number|undefined)}=} options
 */
CodeMirrorObj.prototype.addOverlay = function(mode, options) {};
/**
 * @param {string|Object} mode
 */
CodeMirrorObj.prototype.removeOverlay = function(mode) {};
/**
 * @param {string} type
 * @param {function(CodeMirrorObj, ...*)} func
 */
CodeMirrorObj.prototype.on = function(type, func) {};
/**
 * @param {string} type
 * @param {function(CodeMirrorObj, ...*)} func
 */
CodeMirrorObj.prototype.off = function(type, func) {};
/**
 * @returns {DocObj}
 */
CodeMirrorObj.prototype.getDoc = function() {};
/**
 * @param {DocObj} doc
 * @returns {DocObj}
 */
CodeMirrorObj.prototype.swapDoc = function(doc) {};
/**
 * @param {lineCharObj} pos
 * @param {Element} node
 * @param {boolean} scrollIntoView
 */
CodeMirrorObj.prototype.addWidget = function(pos, node, scrollIntoView) {};
/**
 * @param {number|string} width
 * @param {number|string} height
 */
CodeMirrorObj.prototype.setSize = function(width, height) {};
/**
 * @param {number} x
 * @param {number} y
 */
CodeMirrorObj.prototype.scrollTo = function(x, y) {};
/**
 * @returns {{
 *     left, top, width, height, clientWidth, clientHeight
 * }}
 */
CodeMirrorObj.prototype.getScrollInfo = function() {};
/**
 * @param {lineCharObj|{left, top, right, bottom}|{from, to}|null} what
 * @param {number=} margin
 */
CodeMirrorObj.prototype.scrollIntoView = function(what, margin) {};
/**
 * @param {boolean|lineCharObj} where
 * @param {string} mode
 * @returns {{
 *     left, top, bottom
 * }}
 */
CodeMirrorObj.prototype.cursorCoords = function(where, mode) {};
/**
 * @param {lineCharObj} pos
 * @param {string} mode
 * @returns {{
 *     left, right, top, bottom
 * }}
 */
CodeMirrorObj.prototype.charCoords = function(pos, mode) {};
/**
 * @param {lineCharObj} object
 * @param {string=} mode
 * @returns {lineCharObj}
 */
CodeMirrorObj.prototype.coordsChar = function(object, mode) {};
/**
 * @param {number} height
 * @param {string=} mode
 * @returns {number}
 */
CodeMirrorObj.prototype.lineAtHeight = function(height, mode) {};
/**
 * @param {number|LineHandle} line
 * @param {string=} mode
 * @param {boolean=} includeWidgets
 * @returns {number}
 */
CodeMirrorObj.prototype.heightAtLine = function(line, mode, includeWidgets) {};
/**
 * @returns {number}
 */
CodeMirrorObj.prototype.defaultTextHeight = function() {};
/**
 * @returns {number}
 */
CodeMirrorObj.prototype.defaultCharWidth = function() {};
/**
 * @returns {{
 *     from: number,
 *     to: number
 * }}
 */
CodeMirrorObj.prototype.getViewport = function() {};
/**
 */
CodeMirrorObj.prototype.refresh = function() {};
/**
 * @param {lineCharObj} pos
 * @returns {Object}
 */
CodeMirrorObj.prototype.getModeAt = function(pos) {};
/**
 * @param {lineCharObj} pos
 * @param {boolean=} precise
 * @returns {{
 *     start, end, string, type, state
 * }}
 */
CodeMirrorObj.prototype.getTokenAt = function(pos, precise) {};
/**
 * @param {number} line
 * @param {boolean=} precise
 * @returns {Array<{
 *     start, end, string, type, state
 * }>}
 */
CodeMirrorObj.prototype.getLineTokens = function(line, precise) {};
/**
 * @param {lineCharObj} pos
 * @returns {string}
 */
CodeMirrorObj.prototype.getTokenTypeAt = function(pos) {};
/**
 * @param {lineCharObj} pos
 * @param {string} type
 * @returns {Array<helperObj>}
 */
CodeMirrorObj.prototype.getHelpers = function(pos, type) {};
/**
 * @param {lineCharObj} pos
 * @param {string} type
 * @returns {helperObj}
 */
CodeMirrorObj.prototype.getHelper = function(pos, type) {};
/**
 * @param {number=} line
 * @param {boolean=} precise
 * @returns {Object}
 */
CodeMirrorObj.prototype.getStateAfter = function(line, precise) {};
/**
 * @param {function(...*): *} func
 * @returns {*}
 */
CodeMirrorObj.prototype.operation = function(func) {};
/**
 */
CodeMirrorObj.prototype.startOperation = function() {};
/**
 */
CodeMirrorObj.prototype.endOperation = function() {};
/**
 * @param {number} line
 * @param {(string|number)=}dir
 */
CodeMirrorObj.prototype.indentLine = function(line, dir) {};
/**
 * @param {boolean} value
 */
CodeMirrorObj.prototype.toggleOverwrite = function(value) {};
/**
 * @returns {boolean}
 */
CodeMirrorObj.prototype.isReadOnly = function() {};
/**
 * @returns {string}
 */
CodeMirrorObj.prototype.lineSeparator = function() {};
/**
 * @param {string} name
 */
CodeMirrorObj.prototype.execCommand = function(name) {};
/**
 */
CodeMirrorObj.prototype.focus = function() {};
/**
 * @param {string} text
 * @returns {string}
 */
CodeMirrorObj.prototype.phase = function(text) {};
/**
 * @returns {Element}
 */
CodeMirrorObj.prototype.getInputField = function() {};
/**
 * @returns {Element}
 */
CodeMirrorObj.prototype.getWrapperElement = function() {};
/**
 * @returns {Element}
 */
CodeMirrorObj.prototype.getScrollerElement = function() {};
/**
 * @returns {Element}
 */
CodeMirrorObj.prototype.getGutterElement = function() {};
/**
 * @type {StringStream}
 */
CodeMirrorObj.StringStream = null;

/**
 * @constructor
 * @extends CodeMirrorObj
 */
let DocObj = function() {}
/**
 * @param {string=} separator
 * @returns {string}
 */
DocObj.prototype.getValue = function(separator) {};
/**
 * @param {string} content
 */
DocObj.prototype.setValue = function(content) {};
/**
 * @param {lineCharObj} from
 * @param {lineCharObj} to
 * @param {string=} separator
 * @returns {string}
 */
DocObj.prototype.getRange = function(from, to, separator) {};
/**
 *
 * @param {string} replacement
 * @param {lineCharObj} from
 * @param {lineCharObj} to
 * @param {string=} origin
 */
DocObj.prototype.replaceRange = function(replacement, from, to, origin) {};
/**
 * @param {number} n
 * @return {string}
 */
DocObj.prototype.getLine = function(n) {};
/**
 * @returns {number}
 */
DocObj.prototype.lineCount = function() {};
/**
 * @returns {number}
 */
DocObj.prototype.firstLine = function() {};
/**
 * @returns {number}
 */
DocObj.prototype.lastLine = function() {};
/**
 * @param {number} num
 * @returns {LineHandle}
 */
DocObj.prototype.getLineHandle = function(num) {};
/**
 * @param {LineHandle} handle
 * @returns {number}
 */
DocObj.prototype.getLineNumber = function(handle) {};
/**
 * @param {number|function(LineHandle)} start
 * @param {number=} end
 * @param {function(LineHandle)=} f
 */
DocObj.prototype.eachLine = function(start, end, f) {};
/**
 */
DocObj.prototype.markClean = function() {};
/**
 * @param {boolean=} closeEvent
 * @returns number
 */
DocObj.prototype.changeGeneration = function(closeEvent) {};
/**
 * @param {number=} generation
 * @returns {boolean}
 */
DocObj.prototype.isClean = function(generation) {};
/**
 * @param {string=} lineSep
 * @returns {string}
 */
DocObj.prototype.getSelection = function(lineSep) {};
/**
 * @param {string=} lineSep
 * @returns {Array<string>}
 */
DocObj.prototype.getSelections = function(lineSep) {};
/**
 * @param {string} replacement
 * @param {string=} select
 */
DocObj.prototype.replaceSelection = function(replacement, select) {};
/**
 * @param {Array<string>} replacements
 * @param {string=} select
 */
DocObj.prototype.replaceSelections = function(replacements, select) {};
/**
 * @param {string=} start
 * @returns {{line: number, ch: number}}
 */
DocObj.prototype.getCursor = function(start) {};
/**
 * @returns {Array<{anchor: {line: number, ch: number}, head: {line: number, ch: number}}>}
 */
DocObj.prototype.listSelections = function() {};
/**
 * @returns {boolean}
 */
DocObj.prototype.somethingSelected = function() {};
/**
 * @param {lineCharObj|number} pos
 * @param {number=} ch
 * @param {{scroll: (boolean|undefined), origin: (string|undefined), bias: (number|undefined)}=} options
 */
DocObj.prototype.setCursor = function(pos, ch, options) {};
/**
 * @param {lineCharObj} anchor
 * @param {lineCharObj} head
 * @param {{scroll: (boolean|undefined), origin: (string|undefined), bias: (number|undefined)}=} options
 */
DocObj.prototype.setSelection = function(anchor, head, options) {};
/**
 * @param {Array<{anchor: lineCharObj, head: lineCharObj}>} ranges
 * @param {number=} primary
 * @param {{scroll: (boolean|undefined), origin: (string|undefined), bias: (number|undefined)}=} options
 */
DocObj.prototype.setSelections = function(ranges, primary, options) {};
/**
 * @param {lineCharObj} anchor
 * @param {lineCharObj=} head
 */
DocObj.prototype.addSelection = function(anchor, head) {};
/**
 * @param {lineCharObj} from
 * @param {lineCharObj=} to
 * @param {{scroll: (boolean|undefined), origin: (string|undefined), bias: (number|undefined)}=} options
 */
DocObj.prototype.extendSelection = function(from, to, options) {};
/**
 * @param {Array<lineCharObj>} heads
 * @param {{scroll: (boolean|undefined), origin: (string|undefined), bias: (number|undefined)}=} options
 */
DocObj.prototype.extendSelections = function(heads, options) {};
/**
 * @param {function(lineCharObj): lineCharObj} f
 * @param {{scroll: (boolean|undefined), origin: (string|undefined), bias: (number|undefined)}=} options
 */
DocObj.prototype.extendSelectionBy = function(f, options) {};
/**
 * @param {boolean} value
 */
DocObj.prototype.setExtending = function(value) {};
/**
 * @returns {boolean}
 */
DocObj.prototype.getExtending = function() {};
/**
 * @returns {CodeMirror}
 */
DocObj.prototype.getEditor = function() {};
/**
 * @param {boolean} copyHistory
 * @returns {DocObj}
 */
DocObj.prototype.copy = function(copyHistory) {};
/**
 * @param {{
 * 		sharedHist: (boolean|undefined),
 * 		from: (number|undefined),
 * 		to: (number|undefined),
 * 		mode: (string|Object|undefined)
 * }} options
 * @returns {DocObj}
 */
DocObj.prototype.linkedDoc = function(options) {};
/**
 * @param {DocObj} doc
 */
DocObj.prototype.unlinkDoc = function(doc) {};
/**
 * @param {function(DocObj, boolean)} func
 */
DocObj.prototype.iterLinkedDocs = function(func) {};
/**
 */
DocObj.prototype.undo = function() {};
/**
 */
DocObj.prototype.redo = function() {};
/**
 */
DocObj.prototype.undoSelection = function() {};
/**
 */
DocObj.prototype.redoSelection = function() {};
/**
 * @returns {{undo: number, redo: number}}
 */
DocObj.prototype.historySize = function() {};
/**
 */
DocObj.prototype.clearHistory = function() {};
/**
 * @returns {Object}
 */
DocObj.prototype.getHistory = function() {};
/**
 * @param {Object} history
 */
DocObj.prototype.setHistory = function(history) {};
/**
 * @param {lineCharObj} from
 * @param {lineCharObj} to
 * @param {{
 *     className: (string|undefined),
 *     inclusiveLeft: (boolean|undefined),
 *     inclusiveRight: (boolean|undefined),
 *     selectLeft: (boolean|undefined),
 *     selectRight: (boolean|undefined),
 *     atomic: (boolean|undefined),
 *     collapsed: (boolean|undefined),
 *     clearOnEnter: (boolean|undefined),
 *     clearWhenEmpty: (boolean|undefined),
 *     replacedWith: (Element|undefined),
 *     handleMouseEvents: (boolean|undefined),
 *     readOnly: (boolean|undefined),
 *     addToHistory: (boolean|undefined),
 *     startStyle: (string|undefined),
 *     endStyle: (string|undefined),
 *     css: (string|undefined),
 *     attributes: (Object|undefined),
 *     shared: (boolean|undefined)
 * }=} options
 * @returns {TextMarker}
 */
DocObj.prototype.markText = function(from, to, options) {};
/**
 * @param {lineCharObj} pos
 * @param {{
 * 		widget: (Element|undefined),
 * 		insertLeft: (boolean|undefined),
 * 		shared: (boolean|undefined),
 * 		handleMouseEvents: (boolean|undefined)
 * }=} options
 * @returns {TextMarker}
 */
DocObj.prototype.setBookmark = function(pos, options) {};
/**
 * @param {lineCharObj} from
 * @param {lineCharObj} to
 * @returns {Array<TextMarker>}
 */
DocObj.prototype.findMarks = function(from, to) {};
/**
 * @param {lineCharObj} pos
 * @returns {Array<TextMarker>}
 */
DocObj.prototype.findMarksAt = function(pos) {};
/**
 * @returns {Array<TextMarker>}
 */
DocObj.prototype.getAllMarks = function() {};
/**
 * @param {number|LineHandle} line
 * @param {string} gutterID
 * @param {Element} value
 * @returns {LineHandle}
 */
DocObj.prototype.setGutterMarker = function(line, gutterID, value) {};
/**
 * @param {string} gutterID
 */
DocObj.prototype.clearGutter = function(gutterID) {};
/**
 * @param {number|LineHandle} line
 * @param {string} where
 * @param {string} class2
 * @returns {LineHandle}
 */
DocObj.prototype.addLineClass = function(line, where, class2) {};
/**
 * @param {number|LineHandle} line
 * @param {string} where
 * @param {string} class2
 * @returns {LineHandle}
 */
DocObj.prototype.removeLineClass = function(line, where, class2) {};
/**
 * @param {number|LineHandle} line
 * @returns {{
 *     line, handle, text, gutterMarkers, textClass, bgClass, wrapClass, widgets
 * }}
 */
DocObj.prototype.lineInfo = function(line) {};
/**
 * @param {number|LineHandle} line
 * @param {Element} node
 * @param {{
 *     coverGutter: (boolean|undefined),
 *     noHScroll: (boolean|undefined),
 *     above: (boolean|undefined),
 *     handleMouseEvents: (boolean|undefined),
 *     insertAt: (number|undefined),
 *     className: (string|undefined)
 * }=} options
 * @returns {LineWidget}
 */
DocObj.prototype.addLineWidget = function(line, node, options) {};
/**
 * @returns {Object}
 */
DocObj.prototype.getMode = function() {};
/**
 * @param {number} index
 * @returns {lineCharObj}
 */
DocObj.prototype.posFromIndex = function(index) {};
/**
 * @param {lineCharObj} object
 * @returns {number}
 */
DocObj.prototype.indexFromPos = function(object) {};

/**
 * @constructor
 * @extends {DocObj}
 */
let CodeMirrorFromTextAreaObj = function() {};
/**
 */
CodeMirrorFromTextAreaObj.prototype.save = function(){};
/**
 */
CodeMirrorFromTextAreaObj.prototype.toTextArea = function() {};
/**
 * @returns {HTMLTextAreaElement}
 */
CodeMirrorFromTextAreaObj.prototype.getTextArea = function() {};

/** @typedef {*} */
let helperObj;

/**
 * @constructor
 */
let PosObj = function() {};

/**
 * @constructor
 */
let commandsObj = function() {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.selectAll = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.singleSelection = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.killLine = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.deleteLine = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delLineLeft = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delWrappedLineLeft = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delWrappedLineRight = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.undo = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.redo = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.undoSelection = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.redoSelection = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goDocStart = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goDocEnd = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goLineStart = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goLineStartSmart = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goLineEnd = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goLineRight = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goLineLeft = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goLineLeftSmart = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goLineUp = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goLineDown = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goPageUp = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goPageDown = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goCharLeft = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goCharRight = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goColumnLeft = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goColumnRight = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goWordLeft = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goWordRight = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goGroupLeft = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.goGroupRight = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delCharBefore = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delCharAfter = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delWordBefore = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delWordAfter = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delGroupBefore = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.delGroupAfter = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.indentAuto = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.indentMore = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.indentLess = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.insertTab = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.insertSoftTab = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.defaultTab = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.transposeChars = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.newlineAndIndent = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.toggleOverwrite = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.save = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.find = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.findNext = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.findPrev = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.replace = function(cm) {};
/**
 * @param {DocObj} cm
 */
commandsObj.prototype.replaceAll = function(cm) {};

/**
 * @constructor
 */
let TextMarker = function() {};
/**
 */
TextMarker.prototype.clear = function() {};
/**
 * @returns {lineCharObj}
 */
TextMarker.prototype.find = function() {};
/**
 */
TextMarker.prototype.changed = function() {};

/**
 * @constructor
 */
let StringStream = function() {};

/**
 * @typedef {number}
 */
StringStream.pos;
/**
 * @return boolean
 */
StringStream.prototype.eol = function() {};

/**
 * @return boolean
 */
StringStream.prototype.sol = function() {};

/**
 * @return string
 */
StringStream.prototype.peek = function() {};

/**
 * @return string
 */
StringStream.prototype.next = function() {};

/**
 * @param {string|RegExp|function(string):boolean} match
 * @return string
 */
StringStream.prototype.eat = function(match) {};

/**
 * @param {string|RegExp|function(string):boolean} match
 * @return boolean
 */
StringStream.prototype.eatWhile = function(match) {};

/**
 * @return boolean
 */
StringStream.prototype.eatSpace = function() {};

/**
 * @return void
 */
StringStream.prototype.skipToEnd = function() {};

/**
 * @param {string} str
 * @return boolean
 */
StringStream.prototype.skipTo = function(str) {};

/**
 * @param {string|RegExp} pattern
 * @param {boolean} consume
 * @param {?boolean=} casefold
 * @return boolean|Array<string>
 */
StringStream.prototype.match = function(pattern, consume, casefold) {};

/**
 * @param {number} n
 * @return void
 */
StringStream.prototype.backUp = function(n) {};

/**
 * @return number
 */
StringStream.prototype.column = function() {};

/**
 * @return number
 */
StringStream.prototype.indentation = function() {};

/**
 * @return string
 */
StringStream.prototype.current = function() {};

/**
 * @param {number} n
 * @return ?string
 */
StringStream.prototype.lookAhead = function(n) {};

/**
 *  @typedef {{
 *    startState: function():Object,
 *    token: function(StringStream, Object):string,
 *    indent: function(Object, string):number
 *  }}
 */
let CodeMirrorMode;

/**
 * @constructor
 * @param {(Element|function(Element))} element
 * @param {Object} options
 * @returns {CodeMirrorObj}
 */
let CodeMirror = function(element, options) {};
/**
 * @type {string}
 */
CodeMirror.prototype.version = "0";
/**
 * @param {HTMLTextAreaElement} textArea
 * @param {optionsObj=} config
 * @returns {CodeMirrorFromTextAreaObj}
 */
CodeMirror.fromTextArea = function(textArea, config) {};
/**
 * @type {optionsObj}
 */
CodeMirror.defaults = {};
/**
 * @param {string} name
 * @param {*} value
 */
CodeMirror.defineExtension = function(name, value) {};
/**
 * @param {string} name
 * @param {*} value
 */
CodeMirror.defineDocExtension = function(name, value) {};
/**
 * @param {string} name
 * @param {*} def
 * @param {function(CodeMirrorObj, *)} updateFunc
 */
CodeMirror.defineOption = function(name, def, updateFunc) {};
/**
 * @param {function(CodeMirrorObj)} func
 */
CodeMirror.defineInitHook = function(func) {};
/**
 * @param {string} type
 * @param {string} name
 * @param {helperObj} value
 */
CodeMirror.registerHelper = function(type, name, value) {};
/**
 * @param {string} type
 * @param {string} name
 * @param {function((string|Object), CodeMirror)} predicate
 * @param {helperObj} value
 */
CodeMirror.registerGlobalHelper = function(type, name, predicate, value) {};
/**
 * @param {number} line
 * @param {number=} ch
 * @param {string=} sticky
 * @constructor
 * @returns {PosObj}
 */
CodeMirror.Pos = function(line, ch, sticky) {};
/**
 * @param {Object} change
 * @returns {lineCharObj}
 */
CodeMirror.changeEnd = function(change) {};
/**
 * @param {string} line
 * @param {number} index
 * @param {number} tabSize
 * @returns {number}
 */
CodeMirror.countColumn = function(line, index, tabSize) {};
/**
 * @constructor
 */
CodeMirror.Pass = function() {};
/**
 * @param {string} name
 * @param {function(Object, Object):CodeMirrorMode} mode
 */
CodeMirror.defineMode = function(name, mode) {};
/**
 * @param {string} mime_type
 * @param {string|Object} data
 */
CodeMirror.defineMIME = function(mime_type, data) {};
/**
 * @type {commandsObj}
 */
CodeMirror.commands = null;
