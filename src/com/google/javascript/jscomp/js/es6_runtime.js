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
/** @const @type {?} */ $jscomp.global = $jscomp.getGlobal(this);
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
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  var iteratorFunction = /** @type {?} */ (iterable)[Symbol.iterator];
  if (iteratorFunction) {
    return iteratorFunction.call(iterable);
  }
  var index = 0;
  var arr = /** @type {!Array} */ (iterable);
  return /** @type {!Iterator} */ ({next:function() {
    if (index < arr.length) {
      return {done:false, value:arr[index++]};
    } else {
      return {done:true};
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
  var arr = [];
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
    if (Object.defineProperties) {
      var descriptor = Object.getOwnPropertyDescriptor(parentCtor, p);
      if (descriptor) {
        Object.defineProperty(childCtor, p, descriptor);
      }
    } else {
      childCtor[p] = parentCtor[p];
    }
  }
};
$jscomp.array = $jscomp.array || {};
/**
 @param {!IArrayLike<INPUT>} array
 @param {function(number,INPUT):OUTPUT} transform
 @return {!IteratorIterable<OUTPUT>}
 @template INPUT,OUTPUT
 @suppress {checkTypes}
 */
$jscomp.iteratorFromArray = function(array, transform) {
  $jscomp.initSymbolIterator();
  if (array instanceof String) {
    array = array + "";
  }
  var i = 0;
  var iter = {next:function() {
    if (i < array.length) {
      var index = i++;
      return {value:transform(index, array[index]), done:false};
    }
    iter.next = function() {
      return {done:true, value:void 0};
    };
    return iter.next();
  }};
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  iter[Symbol.iterator] = function() {
    return iter;
  };
  return iter;
};
/**
 @param {!IArrayLike<VALUE>} array
 @param {function(this:THIS,VALUE,number,!IArrayLike<VALUE>):*} callback
 @param {THIS} thisArg
 @return {{i:number,v:(VALUE|undefined)}}
 @template THIS,VALUE
 */
$jscomp.findInternal = function(array, callback, thisArg) {
  if (array instanceof String) {
    array = /** @type {!IArrayLike} */ (String(array));
  }
  var len = array.length;
  for (var i = 0;i < len;i++) {
    var value = array[i];
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
  $jscomp.initSymbolIterator();
  opt_mapFn = opt_mapFn != null ? opt_mapFn : function(x) {
    return x;
  };
  var result = [];
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  var iteratorFunction = /** @type {?} */ (arrayLike)[Symbol.iterator];
  if (typeof iteratorFunction == "function") {
    arrayLike = iteratorFunction.call(arrayLike);
  }
  if (typeof arrayLike.next == "function") {
    var next;
    while (!(next = arrayLike.next()).done) {
      result.push(opt_mapFn.call(/** @type {?} */ (opt_thisArg), next.value));
    }
  } else {
    var len = arrayLike.length;
    for (var i = 0;i < len;i++) {
      result.push(opt_mapFn.call(/** @type {?} */ (opt_thisArg), arrayLike[i]));
    }
  }
  return result;
};
/**
 @param {...T} var_args
 @return {!Array<T>}
 @template T
 */
$jscomp.array.of = function(var_args) {
  return $jscomp.array.from(arguments);
};
/**
 @this {!IArrayLike<VALUE>}
 @return {!IteratorIterable<!Array<(number|VALUE)>>}
 @template VALUE
 */
$jscomp.array.entries = function() {
  return $jscomp.iteratorFromArray(this, function(i, v) {
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
  return $jscomp.iteratorFromArray(this, function(i) {
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
  return $jscomp.iteratorFromArray(this, function(k, v) {
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
  var len = this.length;
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
  var length = this.length || 0;
  if (opt_start < 0) {
    opt_start = Math.max(0, length + /** @type {number} */ (opt_start));
  }
  if (opt_end == null || opt_end > length) {
    opt_end = length;
  }
  opt_end = Number(opt_end);
  if (opt_end < 0) {
    opt_end = Math.max(0, length + opt_end);
  }
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
  return $jscomp.findInternal(this, callback, opt_thisArg).v;
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
 @return {number}
 @template VALUE,THIS
 */
$jscomp.array.findIndex = function(callback, opt_thisArg) {
  return $jscomp.findInternal(this, callback, opt_thisArg).i;
};
/**
 @suppress {checkTypes,const}
 */
$jscomp.array.findIndex$install = function() {
  $jscomp.array.installHelper_("findIndex", $jscomp.array.findIndex);
};
/** @define {boolean} */ $jscomp.ASSUME_NO_NATIVE_MAP = false;
/**
 @return {boolean}
 */
$jscomp.Map$isConformant = function() {
  if ($jscomp.ASSUME_NO_NATIVE_MAP) {
    return false;
  }
  var NativeMap = $jscomp.global.Map;
  if (!NativeMap || !NativeMap.prototype.entries || typeof Object.seal != "function") {
    return false;
  }
  try {
    NativeMap = /** @type {function(new:Map,!Iterator=)} */ (NativeMap);
    var key = Object.seal({x:4});
    var map = new NativeMap($jscomp.makeIterator([[key, "s"]]));
    if (map.get(key) != "s" || map.size != 1 || map.get({x:4}) || map.set({x:4}, "t") != map || map.size != 2) {
      return false;
    }
    var /** !Iterator<!Array> */ iter = map.entries();
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
 @struct
 @constructor
 @implements {Iterable<!Array<(KEY|VALUE)>>}
 @param {(!Iterable<!Array<(KEY|VALUE)>>|!Array<!Array<(KEY|VALUE)>>|null)=} opt_iterable
 @template KEY,VALUE
 */
$jscomp.Map = function(opt_iterable) {
  /** @private @type {!Object<!Array<!$jscomp.Map.Entry<KEY,VALUE>>>} */ this.data_ = {};
  /** @private @type {!$jscomp.Map.Entry<KEY,VALUE>} */ this.head_ = $jscomp.Map.createHead();
  /** @type {number} */ this.size = 0;
  if (opt_iterable) {
    var iter = $jscomp.makeIterator(opt_iterable);
    var entry;
    while (!(entry = iter.next()).done) {
      var item = /** @type {!IIterableResult<!Array<(KEY|VALUE)>>} */ (entry).value;
      this.set(/** @type {KEY} */ (item[0]), /** @type {VALUE} */ (item[1]));
    }
  }
};
/**
 @param {KEY} key
 @param {VALUE} value
 */
$jscomp.Map.prototype.set = function(key, value) {
  var r = $jscomp.Map.maybeGetEntry(this, key);
  if (!r.list) {
    r.list = this.data_[r.id] = [];
  }
  if (!r.entry) {
    r.entry = {next:this.head_, previous:this.head_.previous, head:this.head_, key:key, value:value};
    r.list.push(r.entry);
    this.head_.previous.next = r.entry;
    this.head_.previous = r.entry;
    this.size++;
  } else {
    r.entry.value = value;
  }
  return this;
};
/**
 @param {KEY} key
 @return {boolean}
 */
$jscomp.Map.prototype.delete = function(key) {
  var r = $jscomp.Map.maybeGetEntry(this, key);
  if (r.entry && r.list) {
    r.list.splice(r.index, 1);
    if (!r.list.length) {
      delete this.data_[r.id];
    }
    r.entry.previous.next = r.entry.next;
    r.entry.next.previous = r.entry.previous;
    r.entry.head = null;
    this.size--;
    return true;
  }
  return false;
};
$jscomp.Map.prototype.clear = function() {
  this.data_ = {};
  this.head_ = this.head_.previous = $jscomp.Map.createHead();
  this.size = 0;
};
/**
 @param {KEY} key
 @return {boolean}
 */
$jscomp.Map.prototype.has = function(key) {
  return !!$jscomp.Map.maybeGetEntry(this, key).entry;
};
/**
 @param {KEY} key
 @return {VALUE}
 */
$jscomp.Map.prototype.get = function(key) {
  var entry = $jscomp.Map.maybeGetEntry(this, key).entry;
  return /** @type {VALUE} */ (entry && entry.value);
};
/**
 @return {!IteratorIterable<!Array<(KEY|VALUE)>>}
 */
$jscomp.Map.prototype.entries = function() {
  return $jscomp.Map.makeIterator_(this, function(entry) {
    return [entry.key, entry.value];
  });
};
/**
 @return {!IteratorIterable<KEY>}
 */
$jscomp.Map.prototype.keys = function() {
  return $jscomp.Map.makeIterator_(this, function(entry) {
    return entry.key;
  });
};
/**
 @return {!IteratorIterable<VALUE>}
 */
$jscomp.Map.prototype.values = function() {
  return $jscomp.Map.makeIterator_(this, function(entry) {
    return entry.value;
  });
};
/**
 @param {function(this:THIS,VALUE,KEY,!$jscomp.Map<KEY,VALUE>)} callback
 @param {THIS=} opt_thisArg
 @template THIS
 */
$jscomp.Map.prototype.forEach = function(callback, opt_thisArg) {
  var iter = this.entries();
  var item;
  while (!(item = iter.next()).done) {
    var entry = item.value;
    callback.call(/** @type {?} */ (opt_thisArg), /** @type {VALUE} */ (entry[1]), /** @type {KEY} */ (entry[0]), this);
  }
};
/**
 @param {!$jscomp.Map<KEY,VALUE>} map
 @param {KEY} key
 @return {{id:string,list:(!Array<!$jscomp.Map.Entry<KEY,VALUE>>|undefined),index:number,entry:(!$jscomp.Map.Entry<KEY,VALUE>|undefined)}}
 @template KEY,VALUE
 */
$jscomp.Map.maybeGetEntry = function(map, key) {
  var id = $jscomp.Map.getId(key);
  var list = map.data_[id];
  if (list && Object.prototype.hasOwnProperty.call(map.data_, id)) {
    for (var index = 0;index < list.length;index++) {
      var entry = list[index];
      if (key !== key && entry.key !== entry.key || key === entry.key) {
        return {id:id, list:list, index:index, entry:entry};
      }
    }
  }
  return {id:id, list:list, index:-1, entry:undefined};
};
/**
 @private
 @param {!$jscomp.Map<KEY,VALUE>} map
 @param {function(!$jscomp.Map.Entry<KEY,VALUE>):T} func
 @return {!IteratorIterable<T>}
 @template KEY,VALUE,T
 */
$jscomp.Map.makeIterator_ = function(map, func) {
  var entry = map.head_;
  var iter = {next:function() {
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
  }};
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  iter[Symbol.iterator] = function() {
    return /** @type {!Iterator} */ (iter);
  };
  return /** @type {!IteratorIterable} */ (iter);
};
/** @private @type {number} */ $jscomp.Map.mapIndex_ = 0;
/**
 @return {!$jscomp.Map.Entry<KEY,VALUE>}
 @template KEY,VALUE
 @suppress {checkTypes}
 */
$jscomp.Map.createHead = function() {
  var head = {};
  head.previous = head.next = head.head = head;
  return head;
};
/**
 @param {*} obj
 @return {string}
 */
$jscomp.Map.getId = function(obj) {
  if (!(obj instanceof Object)) {
    return "p_" + obj;
  }
  if (!($jscomp.Map.idKey in obj)) {
    try {
      $jscomp.Map.defineProperty(obj, $jscomp.Map.idKey, {value:++$jscomp.Map.mapIndex_});
    } catch (ignored) {
    }
  }
  if (!($jscomp.Map.idKey in obj)) {
    return "o_ " + obj;
  }
  return obj[$jscomp.Map.idKey];
};
$jscomp.Map.defineProperty = Object.defineProperty ? function(obj, key, value) {
  Object.defineProperty(obj, key, {value:String(value)});
} : function(obj, key, value) {
  obj[key] = String(value);
};
/**
 @record
 @template KEY,VALUE
 */
$jscomp.Map.Entry = function() {
};
/** @type {!$jscomp.Map.Entry<KEY,VALUE>} */ $jscomp.Map.Entry.prototype.previous;
/** @type {!$jscomp.Map.Entry<KEY,VALUE>} */ $jscomp.Map.Entry.prototype.next;
/** @type {?Object} */ $jscomp.Map.Entry.prototype.head;
/** @type {KEY} */ $jscomp.Map.Entry.prototype.key;
/** @type {VALUE} */ $jscomp.Map.Entry.prototype.value;
$jscomp.Map$install = function() {
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  if ($jscomp.Map$isConformant()) {
    $jscomp.Map = $jscomp.global.Map;
    return;
  }
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  $jscomp.Map.prototype[Symbol.iterator] = $jscomp.Map.prototype.entries;
  $jscomp.initSymbol();
  /** @const @type {symbol} */ $jscomp.Map.idKey = Symbol("map-id-key");
  $jscomp.Map$install = function() {
  };
};
$jscomp.math = $jscomp.math || {};
/**
 @param {number} x
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
 @param {number} a
 @param {number} b
 @return {number}
 */
$jscomp.math.imul = function(a, b) {
  a = Number(a);
  b = Number(b);
  var ah = a >>> 16 & 65535;
  var al = a & 65535;
  var bh = b >>> 16 & 65535;
  var bl = b & 65535;
  var lh = ah * bl + al * bh << 16 >>> 0;
  return al * bl + lh | 0;
};
/**
 @param {number} x
 @return {number}
 */
$jscomp.math.sign = function(x) {
  x = Number(x);
  return x === 0 || isNaN(x) ? x : x > 0 ? 1 : -1;
};
/**
 @param {number} x
 @return {number}
 */
$jscomp.math.log10 = function(x) {
  return Math.log(x) / Math.LN10;
};
/**
 @param {number} x
 @return {number}
 */
$jscomp.math.log2 = function(x) {
  return Math.log(x) / Math.LN2;
};
/**
 @param {number} x
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
 @param {number} x
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
 @param {number} x
 @return {number}
 */
$jscomp.math.cosh = function(x) {
  x = Number(x);
  return (Math.exp(x) + Math.exp(-x)) / 2;
};
/**
 @param {number} x
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
 @param {number} x
 @return {number}
 */
$jscomp.math.tanh = function(x) {
  x = Number(x);
  if (x === 0) {
    return x;
  }
  var y = Math.exp(-2 * Math.abs(x));
  var z = (1 - y) / (1 + y);
  return x < 0 ? -z : z;
};
/**
 @param {number} x
 @return {number}
 */
$jscomp.math.acosh = function(x) {
  x = Number(x);
  return Math.log(x + Math.sqrt(x * x - 1));
};
/**
 @param {number} x
 @return {number}
 */
$jscomp.math.asinh = function(x) {
  x = Number(x);
  if (x === 0) {
    return x;
  }
  var y = Math.log(Math.abs(x) + Math.sqrt(x * x + 1));
  return x < 0 ? -y : y;
};
/**
 @param {number} x
 @return {number}
 */
$jscomp.math.atanh = function(x) {
  x = Number(x);
  return ($jscomp.math.log1p(x) - $jscomp.math.log1p(-x)) / 2;
};
/**
 @param {number} x
 @param {number} y
 @param {...*} var_args
 @return {number}
 */
$jscomp.math.hypot = function(x, y, var_args) {
  x = Number(x);
  y = Number(y);
  var i, z, sum;
  var max = Math.max(Math.abs(x), Math.abs(y));
  for (i = 2;i < arguments.length;i++) {
    max = Math.max(max, Math.abs(arguments[i]));
  }
  if (max > 1E100 || max < 1E-100) {
    x = x / max;
    y = y / max;
    sum = x * x + y * y;
    for (i = 2;i < arguments.length;i++) {
      z = Number(arguments[i]) / max;
      sum += z * z;
    }
    return Math.sqrt(sum) * max;
  } else {
    sum = x * x + y * y;
    for (i = 2;i < arguments.length;i++) {
      z = Number(arguments[i]);
      sum += z * z;
    }
    return Math.sqrt(sum);
  }
};
/**
 @param {number} x
 @return {number}
 */
$jscomp.math.trunc = function(x) {
  x = Number(x);
  if (isNaN(x) || x === Infinity || x === -Infinity || x === 0) {
    return x;
  }
  var y = Math.floor(Math.abs(x));
  return x < 0 ? -y : y;
};
/**
 @param {number} x
 @return {number}
 */
$jscomp.math.cbrt = function(x) {
  if (x === 0) {
    return x;
  }
  x = Number(x);
  var y = Math.pow(Math.abs(x), 1 / 3);
  return x < 0 ? -y : y;
};
$jscomp.number = $jscomp.number || {};
/**
 @param {number} x
 @return {boolean}
 */
$jscomp.number.isFinite = function(x) {
  if (typeof x !== "number") {
    return false;
  }
  return !isNaN(x) && x !== Infinity && x !== -Infinity;
};
/**
 @param {number} x
 @return {boolean}
 */
$jscomp.number.isInteger = function(x) {
  if (!$jscomp.number.isFinite(x)) {
    return false;
  }
  return x === Math.floor(x);
};
/**
 @param {number} x
 @return {boolean}
 */
$jscomp.number.isNaN = function(x) {
  return typeof x === "number" && isNaN(x);
};
/**
 @param {number} x
 @return {boolean}
 */
$jscomp.number.isSafeInteger = function(x) {
  return $jscomp.number.isInteger(x) && Math.abs(x) <= $jscomp.number.MAX_SAFE_INTEGER;
};
$jscomp.number.EPSILON = function() {
  return Math.pow(2, -52);
}();
$jscomp.number.MAX_SAFE_INTEGER = function() {
  return 9007199254740991;
}();
$jscomp.number.MIN_SAFE_INTEGER = function() {
  return -9007199254740991;
}();
$jscomp.object = $jscomp.object || {};
/**
 @param {!Object} target
 @param {...?Object} var_args
 @return {!Object}
 */
$jscomp.object.assign = function(target, var_args) {
  for (var i = 1;i < arguments.length;i++) {
    var source = arguments[i];
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
/** @define {boolean} */ $jscomp.ASSUME_NO_NATIVE_SET = false;
/**
 @return {boolean}
 */
$jscomp.Set$isConformant = function() {
  if ($jscomp.ASSUME_NO_NATIVE_SET) {
    return false;
  }
  var NativeSet = $jscomp.global.Set;
  if (!NativeSet || !NativeSet.prototype.entries || typeof Object.seal != "function") {
    return false;
  }
  try {
    NativeSet = /** @type {function(new:Set,!Iterator=)} */ (NativeSet);
    var value = Object.seal({x:4});
    var set = new NativeSet($jscomp.makeIterator([value]));
    if (!set.has(value) || set.size != 1 || set.add(value) != set || set.size != 1 || set.add({x:4}) != set || set.size != 2) {
      return false;
    }
    var iter = set.entries();
    var item = iter.next();
    if (item.done || item.value[0] != value || item.value[1] != value) {
      return false;
    }
    item = iter.next();
    if (item.done || item.value[0] == value || item.value[0].x != 4 || item.value[1] != item.value[0]) {
      return false;
    }
    return iter.next().done;
  } catch (err) {
    return false;
  }
};
/**
 @struct
 @constructor
 @implements {Iterable<VALUE>}
 @param {(!Iterable<VALUE>|!Array<VALUE>|null)=} opt_iterable
 @template VALUE
 */
$jscomp.Set = function(opt_iterable) {
  /** @private @const */ this.map_ = new $jscomp.Map;
  if (opt_iterable) {
    var iter = $jscomp.makeIterator(opt_iterable);
    var entry;
    while (!(entry = iter.next()).done) {
      var item = /** @type {!IIterableResult<VALUE>} */ (entry).value;
      this.add(item);
    }
  }
  this.size = this.map_.size;
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
  var result = this.map_.delete(value);
  this.size = this.map_.size;
  return result;
};
$jscomp.Set.prototype.clear = function() {
  this.map_.clear();
  this.size = 0;
};
/**
 @param {VALUE} value
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
  var set = this;
  this.map_.forEach(function(value) {
    return callback.call(/** @type {?} */ (opt_thisArg), value, value, set);
  });
};
$jscomp.Set$install = function() {
  $jscomp.Map$install();
  if ($jscomp.Set$isConformant()) {
    $jscomp.Set = $jscomp.global.Set;
    return;
  }
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  $jscomp.Set.prototype[Symbol.iterator] = $jscomp.Set.prototype.values;
  $jscomp.Set$install = function() {
  };
};
$jscomp.string = $jscomp.string || {};
/**
 @param {?} thisArg
 @param {*} arg
 @param {string} func
 @return {string}
 */
$jscomp.checkStringArgs = function(thisArg, arg, func) {
  if (thisArg == null) {
    throw new TypeError("The 'this' value for String.prototype." + func + " must not be null or undefined");
  }
  if (arg instanceof RegExp) {
    throw new TypeError("First argument to String.prototype." + func + " must not be a regular expression");
  }
  return thisArg + "";
};
/**
 @param {...number} var_args
 @return {string}
 */
$jscomp.string.fromCodePoint = function(var_args) {
  var result = "";
  for (var i = 0;i < arguments.length;i++) {
    var code = Number(arguments[i]);
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
 @this {string}
 @param {number} copies
 @return {string}
 */
$jscomp.string.repeat = function(copies) {
  var string = $jscomp.checkStringArgs(this, null, "repeat");
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
 @this {string}
 @param {number} position
 @return {(number|undefined)}
 */
$jscomp.string.codePointAt = function(position) {
  var string = $jscomp.checkStringArgs(this, null, "codePointAt");
  var size = string.length;
  position = Number(position) || 0;
  if (!(position >= 0 && position < size)) {
    return void 0;
  }
  position = position | 0;
  var first = string.charCodeAt(position);
  if (first < 55296 || first > 56319 || position + 1 === size) {
    return first;
  }
  var second = string.charCodeAt(position + 1);
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
 @this {string}
 @param {string} searchString
 @param {number=} opt_position
 @return {boolean}
 */
$jscomp.string.includes = function(searchString, opt_position) {
  var string = $jscomp.checkStringArgs(this, searchString, "includes");
  return string.indexOf(searchString, opt_position || 0) !== -1;
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
 @this {string}
 @param {string} searchString
 @param {number=} opt_position
 @return {boolean}
 */
$jscomp.string.startsWith = function(searchString, opt_position) {
  var string = $jscomp.checkStringArgs(this, searchString, "startsWith");
  searchString = searchString + "";
  var strLen = string.length;
  var searchLen = searchString.length;
  var i = Math.max(0, Math.min(/** @type {number} */ (opt_position) | 0, string.length));
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
 @this {string}
 @param {string} searchString
 @param {number=} opt_position
 @return {boolean}
 */
$jscomp.string.endsWith = function(searchString, opt_position) {
  var string = $jscomp.checkStringArgs(this, searchString, "endsWith");
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


