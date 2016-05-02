/*
 * Copyright 2015 The Closure Compiler Authors.
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

// GENERATED FILE. DO NOT EDIT. REBUILD WITH build_runtime.sh.

'use strict';"require base";
"declare window global";
/**
 @param {!Object} maybeGlobal
 @return {!Object}
 @suppress {undefinedVars}
 */
$jscomp.getGlobal = function(maybeGlobal) {
  return typeof window != "undefined" && window === maybeGlobal ? maybeGlobal : typeof global != "undefined" ? global : maybeGlobal;
};
/** @const @type {!Object} */ $jscomp.global = $jscomp.getGlobal(this);
/**
 @suppress {reportUnknownTypes}
 */
$jscomp.initSymbol = function() {
  if (!$jscomp.global.Symbol) {
    $jscomp.global.Symbol = $jscomp.Symbol;
  }
  $jscomp.initSymbol = function() {
  };
};
/** @private @type {number} */ $jscomp.symbolCounter_ = 0;
/**
 @param {string} description
 @return {symbol}
 @suppress {reportUnknownTypes}
 */
$jscomp.Symbol = function(description) {
  return /** @type {symbol} */ ("jscomp_symbol_" + description + $jscomp.symbolCounter_++);
};
/**
 @suppress {reportUnknownTypes}
 */
$jscomp.initSymbolIterator = function() {
  $jscomp.initSymbol();
  if (!$jscomp.global.Symbol.iterator) {
    $jscomp.global.Symbol.iterator = $jscomp.global.Symbol("iterator");
  }
  $jscomp.initSymbolIterator = function() {
  };
};
/**
 @param {(string|!Array<T>|!Iterable<T>|!Iterator<T>|!Arguments<T>)} iterable
 @return {!Iterator<T>}
 @template T
 @suppress {reportUnknownTypes}
 */
$jscomp.makeIterator = function(iterable) {
  $jscomp.initSymbolIterator();
  if (iterable[$jscomp.global.Symbol.iterator]) {
    return iterable[$jscomp.global.Symbol.iterator]();
  }
  var index = 0;
  return /** @type {!Iterator} */ ({next:function() {
    if (index == iterable.length) {
      return {done:true};
    } else {
      return {done:false, value:iterable[index++]};
    }
  }});
};
/**
 @param {!Iterator<T>} iterator
 @return {!Array<T>}
 @template T
 */
$jscomp.arrayFromIterator = function(iterator) {
  var i;
  /** @const */ var arr = [];
  while (!(i = iterator.next()).done) {
    arr.push(i.value);
  }
  return arr;
};
/**
 @param {(string|!Array<T>|!Iterable<T>|!Arguments<T>)} iterable
 @return {!Array<T>}
 @template T
 */
$jscomp.arrayFromIterable = function(iterable) {
  if (iterable instanceof Array) {
    return iterable;
  } else {
    return $jscomp.arrayFromIterator($jscomp.makeIterator(iterable));
  }
};
/**
 @param {!Function} childCtor
 @param {!Function} parentCtor
 */
$jscomp.inherits = function(childCtor, parentCtor) {
  /** @constructor */ function tempCtor() {
  }
  tempCtor.prototype = parentCtor.prototype;
  childCtor.prototype = new tempCtor;
  /** @override */ childCtor.prototype.constructor = childCtor;
  for (var p in parentCtor) {
    if ($jscomp.global.Object.defineProperties) {
      var descriptor = $jscomp.global.Object.getOwnPropertyDescriptor(parentCtor, p);
      if (descriptor) {
        $jscomp.global.Object.defineProperty(childCtor, p, descriptor);
      }
    } else {
      childCtor[p] = parentCtor[p];
    }
  }
};
$jscomp.array = $jscomp.array || {};
/**
 @private
 @return {{done:boolean}}
 */
$jscomp.array.done_ = function() {
  return {done:true, value:void 0};
};
/**
 @private
 @param {!IArrayLike<INPUT>} array
 @param {function(number,INPUT):OUTPUT} func
 @return {!IteratorIterable<OUTPUT>}
 @template INPUT,OUTPUT
 @suppress {checkTypes}
 */
$jscomp.array.arrayIterator_ = function(array, func) {
  if (array instanceof String) {
    array = String(array);
  }
  var i = 0;
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  var $jscomp$compprop0 = {};
  /** @const */ var iter = ($jscomp$compprop0.next = function() {
    if (i < array.length) {
      /** @const */ var index = i++;
      return {value:func(index, array[index]), done:false};
    }
    iter.next = $jscomp.array.done_;
    return $jscomp.array.done_();
  }, $jscomp$compprop0[Symbol.iterator] = function() {
    return iter;
  }, $jscomp$compprop0);
  return iter;
};
/**
 @private
 @param {!IArrayLike<VALUE>} array
 @param {function(this:THIS,VALUE,number,!IArrayLike<VALUE>):*} callback
 @param {THIS} thisArg
 @return {{i:number,v:(VALUE|undefined)}}
 @template THIS,VALUE
 */
$jscomp.array.findInternal_ = function(array, callback, thisArg) {
  if (array instanceof String) {
    array = /** @type {!IArrayLike} */ (String(array));
  }
  /** @const */ var len = array.length;
  for (var i = 0;i < len;i++) {
    /** @const */ var value = array[i];
    if (callback.call(thisArg, value, i, array)) {
      return {i:i, v:value};
    }
  }
  return {i:-1, v:void 0};
};
/**
 @param {(!IArrayLike<INPUT>|!Iterator<INPUT>|!Iterable<INPUT>)} arrayLike
 @param {function(this:THIS,INPUT):OUTPUT=} opt_mapFn
 @param {THIS=} opt_thisArg
 @return {!Array<OUTPUT>}
 @template INPUT,OUTPUT,THIS
 */
$jscomp.array.from = function(arrayLike, opt_mapFn, opt_thisArg) {
  opt_mapFn = opt_mapFn === undefined ? function(x) {
    return x;
  } : opt_mapFn;
  /** @const */ var result = [];
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  if (arrayLike[Symbol.iterator]) {
    $jscomp.initSymbol();
    $jscomp.initSymbolIterator();
    /** @const */ var iter = arrayLike[Symbol.iterator]();
    var next;
    while (!(next = iter.next()).done) {
      result.push(opt_mapFn.call(opt_thisArg, next.value));
    }
  } else {
    /** @const */ var len = arrayLike.length;
    for (var i = 0;i < len;i++) {
      result.push(opt_mapFn.call(opt_thisArg, arrayLike[i]));
    }
  }
  return result;
};
/**
 @param {...*} elements
 @return {!Array<*>}
 */
$jscomp.array.of = function(elements) {
  var $jscomp$restParams = [];
  for (var $jscomp$restIndex = 0;$jscomp$restIndex < arguments.length;++$jscomp$restIndex) {
    $jscomp$restParams[$jscomp$restIndex - 0] = arguments[$jscomp$restIndex];
  }
  var /** @type {!Array<*>} */ elements$10 = $jscomp$restParams;
  return $jscomp.array.from(elements$10);
};
/**
 @this {!IArrayLike<VALUE>}
 @return {!IteratorIterable<!Array<(number|VALUE)>>}
 @template VALUE
 */
$jscomp.array.entries = function() {
  return $jscomp.array.arrayIterator_(this, function(i, v) {
    return [i, v];
  });
};
/**
 @private
 @param {string} method
 @param {!Function} fn
 */
$jscomp.array.installHelper_ = function(method, fn) {
  if (!Array.prototype[method] && Object.defineProperties && Object.defineProperty) {
    Object.defineProperty(Array.prototype, method, {configurable:true, enumerable:false, writable:true, value:fn});
  }
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.array.entries$install = function() {
  $jscomp.array.installHelper_("entries", $jscomp.array.entries);
};
/**
 @this {!IArrayLike}
 @return {!IteratorIterable<number>}
 */
$jscomp.array.keys = function() {
  return $jscomp.array.arrayIterator_(this, function(i) {
    return i;
  });
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.array.keys$install = function() {
  $jscomp.array.installHelper_("keys", $jscomp.array.keys);
};
/**
 @this {!IArrayLike<VALUE>}
 @return {!IteratorIterable<VALUE>}
 @template VALUE
 */
$jscomp.array.values = function() {
  return $jscomp.array.arrayIterator_(this, function(_, v) {
    return v;
  });
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.array.values$install = function() {
  $jscomp.array.installHelper_("values", $jscomp.array.values);
};
/**
 @this {!IArrayLike<VALUE>}
 @param {number} target
 @param {number} start
 @param {number=} opt_end
 @return {!IArrayLike<VALUE>}
 @template VALUE
 */
$jscomp.array.copyWithin = function(target, start, opt_end) {
  /** @const */ var len = this.length;
  target = Number(target);
  start = Number(start);
  opt_end = Number(opt_end != null ? opt_end : len);
  if (target < start) {
    opt_end = Math.min(opt_end, len);
    while (start < opt_end) {
      if (start in this) {
        this[target++] = this[start++];
      } else {
        delete this[target++];
        start++;
      }
    }
  } else {
    opt_end = Math.min(opt_end, len + start - target);
    target += opt_end - start;
    while (opt_end > start) {
      if (--opt_end in this) {
        this[--target] = this[opt_end];
      } else {
        delete this[target];
      }
    }
  }
  return this;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.array.copyWithin$install = function() {
  $jscomp.array.installHelper_("copyWithin", $jscomp.array.copyWithin);
};
/**
 @this {!IArrayLike<VALUE>}
 @param {VALUE} value
 @param {number=} opt_start
 @param {number=} opt_end
 @return {!IArrayLike<VALUE>}
 @template VALUE
 */
$jscomp.array.fill = function(value, opt_start, opt_end) {
  opt_start = opt_start === undefined ? 0 : opt_start;
  if (opt_end == null || !value.length) {
    opt_end = this.length || 0;
  }
  opt_end = Number(opt_end);
  for (var i = Number(opt_start || 0);i < opt_end;i++) {
    this[i] = value;
  }
  return this;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.array.fill$install = function() {
  $jscomp.array.installHelper_("fill", $jscomp.array.fill);
};
/**
 @this {!IArrayLike<VALUE>}
 @param {function(this:THIS,VALUE,number,!IArrayLike<VALUE>):*} callback
 @param {THIS=} opt_thisArg
 @return {(VALUE|undefined)}
 @template VALUE,THIS
 */
$jscomp.array.find = function(callback, opt_thisArg) {
  return $jscomp.array.findInternal_(this, callback, opt_thisArg).v;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.array.find$install = function() {
  $jscomp.array.installHelper_("find", $jscomp.array.find);
};
/**
 @this {!IArrayLike<VALUE>}
 @param {function(this:THIS,VALUE,number,!IArrayLike<VALUE>):*} callback
 @param {THIS=} opt_thisArg
 @return {(VALUE|undefined)}
 @template VALUE,THIS
 */
$jscomp.array.findIndex = function(callback, opt_thisArg) {
  return $jscomp.array.findInternal_(this, callback, opt_thisArg).i;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.array.findIndex$install = function() {
  $jscomp.array.installHelper_("findIndex", $jscomp.array.findIndex);
};
/**
 @struct
 @constructor
 @implements {Iterable<!Array<(KEY|VALUE)>>}
 @param {(!Iterable<!Array<(KEY|VALUE)>>|!Array<!Array<(KEY|VALUE)>>)=} opt_iterable
 @template KEY,VALUE
 */
$jscomp.Map = function(opt_iterable) {
  opt_iterable = opt_iterable === undefined ? [] : opt_iterable;
  /** @private @type {!Object<!Array<!$jscomp.Map.Entry_<KEY,VALUE>>>} */ this.data_ = {};
  /** @private @type {!$jscomp.Map.Entry_<KEY,VALUE>} */ this.head_ = $jscomp.Map.createHead_();
  /** @type {number} */ this.size = 0;
  if (opt_iterable) {
    for (var $jscomp$iter$1 = $jscomp.makeIterator(opt_iterable), $jscomp$key$item = $jscomp$iter$1.next();!$jscomp$key$item.done;$jscomp$key$item = $jscomp$iter$1.next()) {
      /** @const */ var item = $jscomp$key$item.value;
      this.set(/** @type {KEY} */ (item[0]), /** @type {VALUE} */ (item[1]));
    }
  }
};
/**
 @private
 @return {boolean}
 */
$jscomp.Map.checkBrowserConformance_ = function() {
  /** @const @type {function(new:Map,!Iterator)} */ var Map = $jscomp.global["Map"];
  if (!Map || !Map.prototype.entries || !Object.seal) {
    return false;
  }
  try {
    /** @const */ var key = Object.seal({x:4});
    /** @const */ var map = new Map($jscomp.makeIterator([[key, "s"]]));
    if (map.get(key) != "s" || map.size != 1 || map.get({x:4}) || map.set({x:4}, "t") != map || map.size != 2) {
      return false;
    }
    /** @const @type {!Iterator<!Array>} */ var iter = map.entries();
    var item = iter.next();
    if (item.done || item.value[0] != key || item.value[1] != "s") {
      return false;
    }
    item = iter.next();
    if (item.done || item.value[0].x != 4 || item.value[1] != "t" || !iter.next().done) {
      return false;
    }
    return true;
  } catch (err) {
    return false;
  }
};
/**
 @private
 @return {!$jscomp.Map.Entry_<KEY,VALUE>}
 @template KEY,VALUE
 @suppress {checkTypes}
 */
$jscomp.Map.createHead_ = function() {
  /** @const */ var head = {};
  head.previous = head.next = head.head = head;
  return head;
};
/**
 @private
 @param {*} obj
 @return {string}
 */
$jscomp.Map.getId_ = function(obj) {
  if (!(obj instanceof Object)) {
    return String(obj);
  }
  if (!($jscomp.Map.key_ in obj)) {
    if (obj instanceof Object && Object.isExtensible && Object.isExtensible(obj)) {
      $jscomp.Map.defineProperty_(obj, $jscomp.Map.key_, ++$jscomp.Map.index_);
    }
  }
  if (!($jscomp.Map.key_ in obj)) {
    return " " + obj;
  }
  return obj[$jscomp.Map.key_];
};
/**
 @param {KEY} key
 @param {VALUE} value
 */
$jscomp.Map.prototype.set = function(key, value) {
  var $jscomp$destructuring$var0 = this.maybeGetEntry_(key);
  var id = $jscomp$destructuring$var0.id;
  var list = $jscomp$destructuring$var0.list;
  var entry = $jscomp$destructuring$var0.entry;
  if (!list) {
    list = this.data_[id] = [];
  }
  if (!entry) {
    entry = {next:this.head_, previous:this.head_.previous, head:this.head_, key:key, value:value};
    list.push(entry);
    this.head_.previous.next = entry;
    this.head_.previous = entry;
    this.size++;
  } else {
    entry.value = value;
  }
  return this;
};
/**
 @param {KEY} key
 @return {boolean}
 */
$jscomp.Map.prototype.delete = function(key) {
  /** @const */ var $jscomp$destructuring$var1 = this.maybeGetEntry_(key);
  /** @const */ var id = $jscomp$destructuring$var1.id;
  /** @const */ var list = $jscomp$destructuring$var1.list;
  /** @const */ var index = $jscomp$destructuring$var1.index;
  /** @const */ var entry = $jscomp$destructuring$var1.entry;
  if (entry && list) {
    list.splice(index, 1);
    if (!list.length) {
      delete this.data_[id];
    }
    entry.previous.next = entry.next;
    entry.next.previous = entry.previous;
    entry.head = null;
    this.size--;
    return true;
  }
  return false;
};
$jscomp.Map.prototype.clear = function() {
  this.data_ = {};
  this.head_ = this.head_.previous = $jscomp.Map.createHead_();
  this.size = 0;
};
/**
 @param {*} key
 @return {boolean}
 */
$jscomp.Map.prototype.has = function(key) {
  return Boolean(this.maybeGetEntry_(key).entry);
};
/**
 @param {*} key
 @return {(VALUE|undefined)}
 */
$jscomp.Map.prototype.get = function(key) {
  /** @const */ var $jscomp$destructuring$var2 = this.maybeGetEntry_(key);
  /** @const */ var entry = $jscomp$destructuring$var2.entry;
  return entry && entry.value;
};
/**
 @private
 @param {KEY} key
 @return {{id:string,list:(!Array<!$jscomp.Map.Entry_<KEY,VALUE>>|undefined),index:number,entry:(!$jscomp.Map.Entry_<KEY,VALUE>|undefined)}}
 */
$jscomp.Map.prototype.maybeGetEntry_ = function(key) {
  /** @const */ var id = $jscomp.Map.getId_(key);
  /** @const */ var list = this.data_[id];
  if (list) {
    for (var index = 0;index < list.length;index++) {
      /** @const */ var entry = list[index];
      if (key !== key && entry.key !== entry.key || key === entry.key) {
        return {id:id, list:list, index:index, entry:entry};
      }
    }
  }
  return {id:id, list:list, index:-1, entry:void 0};
};
/**
 @return {!IteratorIterable<!Array<(KEY|VALUE)>>}
 */
$jscomp.Map.prototype.entries = function() {
  return this.iter_(function(entry) {
    return [entry.key, entry.value];
  });
};
/**
 @return {!IteratorIterable<KEY>}
 */
$jscomp.Map.prototype.keys = function() {
  return this.iter_(function(entry) {
    return entry.key;
  });
};
/**
 @return {!IteratorIterable<VALUE>}
 */
$jscomp.Map.prototype.values = function() {
  return this.iter_(function(entry) {
    return entry.value;
  });
};
/**
 @param {function(this:THIS,VALUE,KEY,!$jscomp.Map<KEY,VALUE>)} callback
 @param {THIS=} opt_thisArg
 @template THIS
 */
$jscomp.Map.prototype.forEach = function(callback, opt_thisArg) {
  for (var $jscomp$iter$2 = $jscomp.makeIterator(this.entries()), $jscomp$key$entry = $jscomp$iter$2.next();!$jscomp$key$entry.done;$jscomp$key$entry = $jscomp$iter$2.next()) {
    /** @const */ var entry = $jscomp$key$entry.value;
    callback.call(opt_thisArg, /** @type {VALUE} */ (entry[1]), /** @type {KEY} */ (entry[0]), /** @type {!$jscomp.Map<KEY,VALUE>} */ (this));
  }
};
/**
 @private
 @param {function(!$jscomp.Map.Entry_<KEY,VALUE>):T} func
 @return {!IteratorIterable<T>}
 @template T
 */
$jscomp.Map.prototype.iter_ = function(func) {
  /** @const */ var map = this;
  var entry = this.head_;
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  var $jscomp$compprop3 = {};
  return /** @type {!IteratorIterable} */ ($jscomp$compprop3.next = function() {
    if (entry) {
      while (entry.head != map.head_) {
        entry = entry.previous;
      }
      while (entry.next != entry.head) {
        entry = entry.next;
        return {done:false, value:func(entry)};
      }
      entry = null;
    }
    return {done:true, value:void 0};
  }, $jscomp$compprop3[Symbol.iterator] = function() {
    return /** @type {!Iterator} */ (this);
  }, $jscomp$compprop3);
};
/** @private @type {number} */ $jscomp.Map.index_ = 0;
/**
 @private
 @param {!Object} obj
 @param {string} key
 @param {*} value
 */
$jscomp.Map.defineProperty_ = Object.defineProperty ? function(obj, key, value) {
  Object.defineProperty(obj, key, {value:String(value)});
} : function(obj, key, value) {
  obj[key] = String(value);
};
/**
 @private
 @record
 @template KEY,VALUE
 */
$jscomp.Map.Entry_ = function() {
};
/** @type {!$jscomp.Map.Entry_<KEY,VALUE>} */ $jscomp.Map.Entry_.prototype.previous;
/** @type {!$jscomp.Map.Entry_<KEY,VALUE>} */ $jscomp.Map.Entry_.prototype.next;
/** @type {?Object} */ $jscomp.Map.Entry_.prototype.head;
/** @type {KEY} */ $jscomp.Map.Entry_.prototype.key;
/** @type {VALUE} */ $jscomp.Map.Entry_.prototype.value;
/** @define {boolean} */ $jscomp.Map.ASSUME_NO_NATIVE = false;
$jscomp.Map$install = function() {
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  if (!$jscomp.Map.ASSUME_NO_NATIVE && $jscomp.Map.checkBrowserConformance_()) {
    $jscomp.Map = $jscomp.global["Map"];
  } else {
    $jscomp.initSymbol();
    $jscomp.initSymbolIterator();
    $jscomp.Map.prototype[Symbol.iterator] = $jscomp.Map.prototype.entries;
    $jscomp.initSymbol();
    /** @private @const @type {symbol} */ $jscomp.Map.key_ = Symbol("map-id-key");
  }
  $jscomp.Map$install = function() {
  };
};
$jscomp.math = $jscomp.math || {};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.clz32 = function(x) {
  x = Number(x) >>> 0;
  if (x === 0) {
    return 32;
  }
  var result = 0;
  if ((x & 4294901760) === 0) {
    x <<= 16;
    result += 16;
  }
  if ((x & 4278190080) === 0) {
    x <<= 8;
    result += 8;
  }
  if ((x & 4026531840) === 0) {
    x <<= 4;
    result += 4;
  }
  if ((x & 3221225472) === 0) {
    x <<= 2;
    result += 2;
  }
  if ((x & 2147483648) === 0) {
    result++;
  }
  return result;
};
/**
 @param {*} a
 @param {*} b
 @return {number}
 */
$jscomp.math.imul = function(a, b) {
  a = Number(a);
  b = Number(b);
  /** @const */ var ah = a >>> 16 & 65535;
  /** @const */ var al = a & 65535;
  /** @const */ var bh = b >>> 16 & 65535;
  /** @const */ var bl = b & 65535;
  /** @const */ var lh = ah * bl + al * bh << 16 >>> 0;
  return al * bl + lh | 0;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.sign = function(x) {
  x = Number(x);
  return x === 0 || isNaN(x) ? x : x > 0 ? 1 : -1;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.log10 = function(x) {
  return Math.log(x) / Math.LN10;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.log2 = function(x) {
  return Math.log(x) / Math.LN2;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.log1p = function(x) {
  x = Number(x);
  if (x < .25 && x > -.25) {
    var y = x;
    var d = 1;
    var z = x;
    var zPrev = 0;
    var s = 1;
    while (zPrev != z) {
      y *= x;
      s *= -1;
      z = (zPrev = z) + s * y / ++d;
    }
    return z;
  }
  return Math.log(1 + x);
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.expm1 = function(x) {
  x = Number(x);
  if (x < .25 && x > -.25) {
    var y = x;
    var d = 1;
    var z = x;
    var zPrev = 0;
    while (zPrev != z) {
      y *= x / ++d;
      z = (zPrev = z) + y;
    }
    return z;
  }
  return Math.exp(x) - 1;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.cosh = function(x) {
  x = Number(x);
  return (Math.exp(x) + Math.exp(-x)) / 2;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.sinh = function(x) {
  x = Number(x);
  if (x === 0) {
    return x;
  }
  return (Math.exp(x) - Math.exp(-x)) / 2;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.tanh = function(x) {
  x = Number(x);
  if (x === 0) {
    return x;
  }
  /** @const */ var y = Math.exp(2 * -Math.abs(x));
  /** @const */ var z = (1 - y) / (1 + y);
  return x < 0 ? -z : z;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.acosh = function(x) {
  x = Number(x);
  return Math.log(x + Math.sqrt(x * x - 1));
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.asinh = function(x) {
  x = Number(x);
  if (x === 0) {
    return x;
  }
  /** @const */ var y = Math.log(Math.abs(x) + Math.sqrt(x * x + 1));
  return x < 0 ? -y : y;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.atanh = function(x) {
  x = Number(x);
  return ($jscomp.math.log1p(x) - $jscomp.math.log1p(-x)) / 2;
};
/**
 @param {*} x
 @param {*} y
 @param {...*} rest
 @return {number}
 */
$jscomp.math.hypot = function(x, y, rest) {
  var $jscomp$restParams = [];
  for (var $jscomp$restIndex = 2;$jscomp$restIndex < arguments.length;++$jscomp$restIndex) {
    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];
  }
  var /** @type {!Array<*>} */ rest$11 = $jscomp$restParams;
  x = Number(x);
  y = Number(y);
  var max = Math.max(Math.abs(x), Math.abs(y));
  for (var $jscomp$iter$4 = $jscomp.makeIterator(rest$11), $jscomp$key$z = $jscomp$iter$4.next();!$jscomp$key$z.done;$jscomp$key$z = $jscomp$iter$4.next()) {
    var z = $jscomp$key$z.value;
    max = Math.max(max, Math.abs(z));
  }
  if (max > 1E100 || max < 1E-100) {
    x = x / max;
    y = y / max;
    var sum = x * x + y * y;
    for (var $jscomp$iter$5 = $jscomp.makeIterator(rest$11), $jscomp$key$z = $jscomp$iter$5.next();!$jscomp$key$z.done;$jscomp$key$z = $jscomp$iter$5.next()) {
      var z$12 = $jscomp$key$z.value;
      z$12 = Number(z$12) / max;
      sum += z$12 * z$12;
    }
    return Math.sqrt(sum) * max;
  } else {
    var sum$13 = x * x + y * y;
    for (var $jscomp$iter$6 = $jscomp.makeIterator(rest$11), $jscomp$key$z = $jscomp$iter$6.next();!$jscomp$key$z.done;$jscomp$key$z = $jscomp$iter$6.next()) {
      var z$14 = $jscomp$key$z.value;
      z$14 = Number(z$14);
      sum$13 += z$14 * z$14;
    }
    return Math.sqrt(sum$13);
  }
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.trunc = function(x) {
  x = Number(x);
  if (isNaN(x) || x === Infinity || x === -Infinity || x === 0) {
    return x;
  }
  /** @const */ var y = Math.floor(Math.abs(x));
  return x < 0 ? -y : y;
};
/**
 @param {*} x
 @return {number}
 */
$jscomp.math.cbrt = function(x) {
  if (x === 0) {
    return x;
  }
  x = Number(x);
  /** @const */ var y = Math.pow(Math.abs(x), 1 / 3);
  return x < 0 ? -y : y;
};
$jscomp.number = $jscomp.number || {};
/**
 @param {*} x
 @return {boolean}
 */
$jscomp.number.isFinite = function(x) {
  if (typeof x !== "number") {
    return false;
  }
  return !isNaN(x) && x !== Infinity && x !== -Infinity;
};
/**
 @param {*} x
 @return {boolean}
 */
$jscomp.number.isInteger = function(x) {
  if (!$jscomp.number.isFinite(x)) {
    return false;
  }
  return x === Math.floor(x);
};
/**
 @param {*} x
 @return {boolean}
 */
$jscomp.number.isNaN = function(x) {
  return typeof x === "number" && isNaN(x);
};
/**
 @param {*} x
 @return {boolean}
 */
$jscomp.number.isSafeInteger = function(x) {
  return $jscomp.number.isInteger(x) && Math.abs(x) <= $jscomp.number.MAX_SAFE_INTEGER;
};
/** @const @type {number} */ $jscomp.number.EPSILON = Math.pow(2, -52);
/** @const @type {number} */ $jscomp.number.MAX_SAFE_INTEGER = 9007199254740991;
/** @const @type {number} */ $jscomp.number.MIN_SAFE_INTEGER = -9007199254740991;
$jscomp.object = $jscomp.object || {};
/**
 @param {!Object} target
 @param {...?Object} sources
 @return {!Object}
 */
$jscomp.object.assign = function(target, sources) {
  var $jscomp$restParams = [];
  for (var $jscomp$restIndex = 1;$jscomp$restIndex < arguments.length;++$jscomp$restIndex) {
    $jscomp$restParams[$jscomp$restIndex - 1] = arguments[$jscomp$restIndex];
  }
  var /** @type {!Array<?Object>} */ sources$15 = $jscomp$restParams;
  for (var $jscomp$iter$7 = $jscomp.makeIterator(sources$15), $jscomp$key$source = $jscomp$iter$7.next();!$jscomp$key$source.done;$jscomp$key$source = $jscomp$iter$7.next()) {
    /** @const */ var source = $jscomp$key$source.value;
    if (!source) {
      continue;
    }
    for (var key in source) {
      if (Object.prototype.hasOwnProperty.call(source, key)) {
        target[key] = source[key];
      }
    }
  }
  return target;
};
/**
 @param {*} left
 @param {*} right
 @return {boolean}
 */
$jscomp.object.is = function(left, right) {
  if (left === right) {
    return left !== 0 || 1 / left === 1 / /** @type {number} */ (right);
  } else {
    return left !== left && right !== right;
  }
};
/**
 @struct
 @constructor
 @implements {Iterable<VALUE>}
 @param {(!Iterable<VALUE>|!Array<VALUE>)=} opt_iterable
 @template VALUE
 */
$jscomp.Set = function(opt_iterable) {
  opt_iterable = opt_iterable === undefined ? [] : opt_iterable;
  /** @private @const @type {!$jscomp.Map<VALUE,VALUE>} */ this.map_ = new $jscomp.Map;
  if (opt_iterable) {
    for (var $jscomp$iter$8 = $jscomp.makeIterator(opt_iterable), $jscomp$key$item = $jscomp$iter$8.next();!$jscomp$key$item.done;$jscomp$key$item = $jscomp$iter$8.next()) {
      /** @const */ var item = $jscomp$key$item.value;
      this.add(/** @type {VALUE} */ (item));
    }
  }
  this.size = this.map_.size;
};
/**
 @private
 @return {boolean}
 */
$jscomp.Set.checkBrowserConformance_ = function() {
  /** @const */ var Set = $jscomp.global["Set"];
  if (!Set || !Set.prototype.entries || !Object.seal) {
    return false;
  }
  /** @const */ var value = Object.seal({x:4});
  /** @const */ var set = new Set($jscomp.makeIterator([value]));
  if (set.has(value) || set.size != 1 || set.add(value) != set || set.size != 1 || set.add({x:4}) != set || set.size != 2) {
    return false;
  }
  /** @const */ var iter = set.entries();
  var item = iter.next();
  if (item.done || item.value[0] != value || item.value[1] != value) {
    return false;
  }
  item = iter.next();
  if (item.done || item.value[0] == value || item.value[0].x != 4 || item.value[1] != item.value[0]) {
    return false;
  }
  return iter.next().done;
};
/**
 @param {VALUE} value
 */
$jscomp.Set.prototype.add = function(value) {
  this.map_.set(value, value);
  this.size = this.map_.size;
  return this;
};
/**
 @param {VALUE} value
 @return {boolean}
 */
$jscomp.Set.prototype.delete = function(value) {
  /** @const */ var result = this.map_.delete(value);
  this.size = this.map_.size;
  return result;
};
$jscomp.Set.prototype.clear = function() {
  this.map_.clear();
  this.size = 0;
};
/**
 @param {*} value
 @return {boolean}
 */
$jscomp.Set.prototype.has = function(value) {
  return this.map_.has(value);
};
/**
 @return {!IteratorIterable<!Array<VALUE>>}
 */
$jscomp.Set.prototype.entries = function() {
  return this.map_.entries();
};
/**
 @return {!IteratorIterable<VALUE>}
 */
$jscomp.Set.prototype.values = function() {
  return this.map_.values();
};
/**
 @param {function(this:THIS,VALUE,VALUE,!$jscomp.Set<VALUE>)} callback
 @param {THIS=} opt_thisArg
 @template THIS
 */
$jscomp.Set.prototype.forEach = function(callback, opt_thisArg) {
  /** @const */ var $jscomp$this = this;
  this.map_.forEach(function(value) {
    return callback.call(opt_thisArg, value, value, $jscomp$this);
  });
};
/** @define {boolean} */ $jscomp.Set.ASSUME_NO_NATIVE = false;
$jscomp.Set$install = function() {
  if (!$jscomp.Set.ASSUME_NO_NATIVE && $jscomp.Set.checkBrowserConformance_()) {
    $jscomp.Set = $jscomp.global["Set"];
  } else {
    $jscomp.Map$install();
    $jscomp.initSymbol();
    $jscomp.initSymbolIterator();
    $jscomp.Set.prototype[Symbol.iterator] = $jscomp.Set.prototype.values;
  }
  $jscomp.Set$install = function() {
  };
};
$jscomp.string = $jscomp.string || {};
/**
 @private
 @param {*} str
 @param {string} func
 */
$jscomp.string.noNullOrUndefined_ = function(str, func) {
  if (str == null) {
    throw new TypeError("The 'this' value for String.prototype." + func + " " + "must not be null or undefined");
  }
};
/**
 @private
 @param {*} str
 @param {string} func
 */
$jscomp.string.noRegExp_ = function(str, func) {
  if (str instanceof RegExp) {
    throw new TypeError("First argument to String.prototype." + func + " " + "must not be a regular expression");
  }
};
/**
 @param {...number} codepoints
 @return {string}
 */
$jscomp.string.fromCodePoint = function(codepoints) {
  var $jscomp$restParams = [];
  for (var $jscomp$restIndex = 0;$jscomp$restIndex < arguments.length;++$jscomp$restIndex) {
    $jscomp$restParams[$jscomp$restIndex - 0] = arguments[$jscomp$restIndex];
  }
  var /** @type {!Array<number>} */ codepoints$16 = $jscomp$restParams;
  var result = "";
  for (var $jscomp$iter$9 = $jscomp.makeIterator(codepoints$16), $jscomp$key$code = $jscomp$iter$9.next();!$jscomp$key$code.done;$jscomp$key$code = $jscomp$iter$9.next()) {
    var code = $jscomp$key$code.value;
    code = +code;
    if (code < 0 || code > 1114111 || code !== Math.floor(code)) {
      throw new RangeError("invalid_code_point " + code);
    }
    if (code <= 65535) {
      result += String.fromCharCode(code);
    } else {
      code -= 65536;
      result += String.fromCharCode(code >>> 10 & 1023 | 55296);
      result += String.fromCharCode(code & 1023 | 56320);
    }
  }
  return result;
};
/**
 @this {*}
 @param {number} copies
 @return {string}
 */
$jscomp.string.repeat = function(copies) {
  $jscomp.string.noNullOrUndefined_(this, "repeat");
  var /** string */ string = String(this);
  if (copies < 0 || copies > 1342177279) {
    throw new RangeError("Invalid count value");
  }
  copies = copies | 0;
  var result = "";
  while (copies) {
    if (copies & 1) {
      result += string;
    }
    if (copies >>>= 1) {
      string += string;
    }
  }
  return result;
};
/**
 @const
 @suppress {checkTypes,const}
 */
$jscomp.string.repeat$install = function() {
  if (!String.prototype.repeat) {
    String.prototype.repeat = $jscomp.string.repeat;
  }
};
/**
 @this {*}
 @param {number} position
 @return {(number|undefined)}
 */
$jscomp.string.codePointAt = function(position) {
  $jscomp.string.noNullOrUndefined_(this, "codePointAt");
  /** @const */ var string = String(this);
  /** @const */ var size = string.length;
  position = Number(position) || 0;
  if (!(position >= 0 && position < size)) {
    return void 0;
  }
  position = position | 0;
  /** @const */ var first = string.charCodeAt(position);
  if (first < 55296 || first > 56319 || position + 1 === size) {
    return first;
  }
  /** @const */ var second = string.charCodeAt(position + 1);
  if (second < 56320 || second > 57343) {
    return first;
  }
  return (first - 55296) * 1024 + second + 9216;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.string.codePointAt$install = function() {
  if (!String.prototype.codePointAt) {
    String.prototype.codePointAt = $jscomp.string.codePointAt;
  }
};
/**
 @this {*}
 @param {string} searchString
 @param {number=} opt_position
 @return {boolean}
 */
$jscomp.string.includes = function(searchString, opt_position) {
  opt_position = opt_position === undefined ? 0 : opt_position;
  $jscomp.string.noRegExp_(searchString, "includes");
  $jscomp.string.noNullOrUndefined_(this, "includes");
  /** @const */ var string = String(this);
  return string.indexOf(searchString, opt_position) !== -1;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.string.includes$install = function() {
  if (!String.prototype.includes) {
    String.prototype.includes = $jscomp.string.includes;
  }
};
/**
 @this {*}
 @param {string} searchString
 @param {number=} opt_position
 @return {boolean}
 */
$jscomp.string.startsWith = function(searchString, opt_position) {
  opt_position = opt_position === undefined ? 0 : opt_position;
  $jscomp.string.noRegExp_(searchString, "startsWith");
  $jscomp.string.noNullOrUndefined_(this, "startsWith");
  /** @const */ var string = String(this);
  searchString = searchString + "";
  /** @const */ var strLen = string.length;
  /** @const */ var searchLen = searchString.length;
  var i = Math.max(0, Math.min(opt_position | 0, string.length));
  var j = 0;
  while (j < searchLen && i < strLen) {
    if (string[i++] != searchString[j++]) {
      return false;
    }
  }
  return j >= searchLen;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.string.startsWith$install = function() {
  if (!String.prototype.startsWith) {
    String.prototype.startsWith = $jscomp.string.startsWith;
  }
};
/**
 @this {*}
 @param {string} searchString
 @param {number=} opt_position
 @return {boolean}
 */
$jscomp.string.endsWith = function(searchString, opt_position) {
  $jscomp.string.noRegExp_(searchString, "endsWith");
  $jscomp.string.noNullOrUndefined_(this, "endsWith");
  /** @const */ var string = String(this);
  searchString = searchString + "";
  if (opt_position === void 0) {
    opt_position = string.length;
  }
  var i = Math.max(0, Math.min(opt_position | 0, string.length));
  var j = searchString.length;
  while (j > 0 && i > 0) {
    if (string[--i] != searchString[--j]) {
      return false;
    }
  }
  return j <= 0;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.string.endsWith$install = function() {
  if (!String.prototype.endsWith) {
    String.prototype.endsWith = $jscomp.string.endsWith;
  }
};


