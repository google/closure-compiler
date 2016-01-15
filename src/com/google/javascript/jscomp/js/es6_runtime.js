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

/**@suppress {undefinedVars}
@param {!Object} maybeGlobal
@return {!Object} */$jscomp.getGlobal = function(maybeGlobal) {
  return typeof window != "undefined" && window === maybeGlobal ? maybeGlobal : typeof global != "undefined" ? global : maybeGlobal;
};
/**@const @type {!Object} */$jscomp.global = $jscomp.getGlobal(this);
/**@suppress {reportUnknownTypes} */$jscomp.initSymbol = function() {
  if (!$jscomp.global.Symbol) {
    $jscomp.global.Symbol = $jscomp.Symbol;
  }
  $jscomp.initSymbol = function() {
  };
};
/**@private @type {number} */$jscomp.symbolCounter_ = 0;
/**@suppress {reportUnknownTypes}
@param {string} description
@return {symbol} */$jscomp.Symbol = function(description) {
  return /**@type {symbol} */("jscomp_symbol_" + description + $jscomp.symbolCounter_++);
};
/**@suppress {reportUnknownTypes} */$jscomp.initSymbolIterator = function() {
  $jscomp.initSymbol();
  if (!$jscomp.global.Symbol.iterator) {
    $jscomp.global.Symbol.iterator = $jscomp.global.Symbol("iterator");
  }
  $jscomp.initSymbolIterator = function() {
  };
};
/**@suppress {reportUnknownTypes} @template T

@param {(string|!Array<T>|!Iterable<T>|!Iterator<T>)} iterable
@return {!Iterator<T>} */$jscomp.makeIterator = function(iterable) {
  $jscomp.initSymbolIterator();
  if (iterable[$jscomp.global.Symbol.iterator]) {
    return iterable[$jscomp.global.Symbol.iterator]();
  }
  if (!(iterable instanceof Array) && typeof iterable != "string" && !(iterable instanceof String)) {
    throw new TypeError(iterable + " is not iterable");
  }
  var index = 0;
  return /**@type {!Iterator} */({next:function() {
    if (index == iterable.length) {
      return {done:true};
    } else {
      return {done:false, value:iterable[index++]};
    }
  }});
};
/**@template T

@param {!Iterator<T>} iterator
@return {!Array<T>} */$jscomp.arrayFromIterator = function(iterator) {
  var i = undefined;
  /**@const */var arr = [];
  while (!(i = iterator.next()).done) {
    arr.push(i.value);
  }
  return arr;
};
/**@template T

@param {(string|!Array<T>|!Iterable<T>)} iterable
@return {!Array<T>} */$jscomp.arrayFromIterable = function(iterable) {
  if (iterable instanceof Array) {
    return iterable;
  } else {
    return $jscomp.arrayFromIterator($jscomp.makeIterator(iterable));
  }
};
/**
@param {!Arguments} args
@return {!Array} */$jscomp.arrayFromArguments = function(args) {
  /**@const */var result = [];
  for (var i = 0;i < args.length;i++) {
    result.push(args[i]);
  }
  return result;
};
/**
@param {!Function} childCtor
@param {!Function} parentCtor */$jscomp.inherits = function(childCtor, parentCtor) {
  /**@constructor */function tempCtor() {
  }
  tempCtor.prototype = parentCtor.prototype;
  childCtor.prototype = new tempCtor;
  /**@override */childCtor.prototype.constructor = childCtor;
  for (var p in parentCtor) {
    if ($jscomp.global.Object.defineProperties) {
      var descriptor = $jscomp.global.Object.getOwnPropertyDescriptor(parentCtor, p);
      $jscomp.global.Object.defineProperty(childCtor, p, descriptor);
    } else {
      childCtor[p] = parentCtor[p];
    }
  }
};
/***/ /**@constructor @struct @template KEY,VALUE

@param {(!Iterable<!Array<(KEY|VALUE)>>|!Array<!Array<(KEY|VALUE)>>)=} opt_iterable
@implements {Iterable<!Array<(KEY|VALUE)>>} */$jscomp.Map = function(opt_iterable) {
  opt_iterable = opt_iterable === undefined ? [] : opt_iterable;
  /**@private @type {!Object<!Array<!$jscomp.Map.Entry_<KEY,VALUE>>>} */this.data_ = {};
  /**@private @type {!$jscomp.Map.Entry_<KEY,VALUE>} */this.head_ = $jscomp.Map.createHead_();
  /**@type {number} */this.size = 0;
  if (opt_iterable) {
    for (var $jscomp$iter$0 = $jscomp.makeIterator(opt_iterable), $jscomp$key$item = $jscomp$iter$0.next();!$jscomp$key$item.done;$jscomp$key$item = $jscomp$iter$0.next()) {
      var item = $jscomp$key$item.value;
      this.set(/**@type {KEY} */(item[0]), /**@type {VALUE} */(item[1]));
    }
  }
};
/**@private
@return {boolean} */$jscomp.Map.checkBrowserConformance_ = function() {
  /**@const @type {function(new:Map,...?)} */var Map = $jscomp.global["Map"];
  if (!Map || !Map.prototype.entries || !Object.seal) {
    return false;
  }
  try {
    /**@const @type {!Object} */var key = Object.seal({x:4});
    /**@const */var map = new Map($jscomp.makeIterator([[key, "s"]]));
    if (map.get(key) != "s" || map.size != 1 || map.get({x:4}) || map.set({x:4}, "t") != map || map.size != 2) {
      return false;
    }
    /**@const @type {!Iterator<!Array<?>>} */var iter = map.entries();
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
/**@private @suppress {checkTypes} @template KEY,VALUE

@return {!$jscomp.Map.Entry_<KEY,VALUE>} */$jscomp.Map.createHead_ = function() {
  /**@const */var head = /***/{};
  head.previous = head.next = head.head = head;
  return head;
};
/**@private
@param {*} obj
@return {string} */$jscomp.Map.getId_ = function(obj) {
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
@param {VALUE} value */$jscomp.Map.prototype.set = function(key, value) {
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
@return {boolean} */$jscomp.Map.prototype["delete"] = function(key) {
  var $jscomp$destructuring$var1 = this.maybeGetEntry_(key);
  var id = $jscomp$destructuring$var1.id;
  var list = $jscomp$destructuring$var1.list;
  var index = $jscomp$destructuring$var1.index;
  var entry = $jscomp$destructuring$var1.entry;
  if (entry) {
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
/***/$jscomp.Map.prototype.clear = function() {
  this.data_ = {};
  this.head_ = this.head_.previous = $jscomp.Map.createHead_();
  this.size = 0;
};
/**
@param {*} key
@return {boolean} */$jscomp.Map.prototype.has = function(key) {
  return Boolean(this.maybeGetEntry_(key).entry);
};
/**
@param {*} key
@return {(VALUE|undefined)} */$jscomp.Map.prototype.get = function(key) {
  /**@const @type {(!$jscomp.Map.Entry_<KEY,VALUE>|undefined)} */var entry = this.maybeGetEntry_(key).entry;
  return entry && entry.value;
};
/**@private
@param {KEY} key
@return {{id:string,list:(!Array<!$jscomp.Map.Entry_<KEY,VALUE>>|undefined),index:number,entry:(!$jscomp.Map.Entry_<KEY,VALUE>|undefined)}} */$jscomp.Map.prototype.maybeGetEntry_ = function(key) {
  /**@const @type {string} */var id = $jscomp.Map.getId_(key);
  /**@const @type {!Array<!$jscomp.Map.Entry_<KEY,VALUE>>} */var list = this.data_[id];
  if (list) {
    for (var index = 0;index < list.length;index++) {
      var entry = list[index];
      if (key !== key && entry.key !== entry.key || key === entry.key) {
        return {id:id, list:list, index:index, entry:entry};
      }
    }
  }
  return {id:id, list:list, index:-1, entry:void 0};
};
/**
@return {!Iterator<!Array<(KEY|VALUE)>>} */$jscomp.Map.prototype.entries = function() {
  return this.iter_(function(entry) {
    return [entry.key, entry.value];
  });
};
/**
@return {!Iterator<KEY>} */$jscomp.Map.prototype.keys = function() {
  return this.iter_(function(entry) {
    return entry.key;
  });
};
/**
@return {!Iterator<VALUE>} */$jscomp.Map.prototype.values = function() {
  return this.iter_(function(entry) {
    return entry.value;
  });
};
/**@template THIS

@param {function(this:THIS,KEY,VALUE,!$jscomp.Map<KEY,VALUE>)} callback
@param {THIS=} opt_thisArg */$jscomp.Map.prototype.forEach = function(callback, opt_thisArg) {
  for (var $jscomp$iter$1 = $jscomp.makeIterator(this.entries()), $jscomp$key$entry = $jscomp$iter$1.next();!$jscomp$key$entry.done;$jscomp$key$entry = $jscomp$iter$1.next()) {
    var entry = $jscomp$key$entry.value;
    callback.call(opt_thisArg, /**@type {VALUE} */(entry[1]), /**@type {KEY} */(entry[0]), /**@type {!$jscomp.Map<KEY,VALUE>} */(this));
  }
};
/**@private @template T

@param {function(!$jscomp.Map.Entry_<KEY,VALUE>):T} func
@return {!Iterator<T>} */$jscomp.Map.prototype.iter_ = function(func) {
  /**@const @type {$jscomp.Map} */var map = this;
  var entry = this.head_;
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  var $jscomp$compprop2 = {};
  return /**@type {!Iterator} */($jscomp$compprop2.next = function() {
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
  }, $jscomp$compprop2[Symbol.iterator] = function() {
    return /**@type {!Iterator} */(this);
  }, $jscomp$compprop2);
};
/**@private @type {number} */$jscomp.Map.index_ = 0;
/**@private
@param {!Object} obj
@param {string} key
@param {*} value */$jscomp.Map.defineProperty_ = Object.defineProperty ? function(obj, key, value) {
  Object.defineProperty(obj, key, {value:String(value)});
} : function(obj, key, value) {
  obj[key] = String(value);
};
/**@record @private @template KEY,VALUE
*/$jscomp.Map.Entry_ = function() {
};
/**@type {!$jscomp.Map.Entry_<KEY,VALUE>} */$jscomp.Map.Entry_.prototype.previous;
/**@type {!$jscomp.Map.Entry_<KEY,VALUE>} */$jscomp.Map.Entry_.prototype.next;
/**@type {?Object} */$jscomp.Map.Entry_.prototype.head;
/**@type {KEY} */$jscomp.Map.Entry_.prototype.key;
/**@type {VALUE} */$jscomp.Map.Entry_.prototype.value;
/**@define {boolean} */$jscomp.Map.ASSUME_NO_NATIVE = false;
/***/$jscomp.Map$install = function() {
  $jscomp.initSymbol();
  $jscomp.initSymbolIterator();
  if (!$jscomp.Map.ASSUME_NO_NATIVE && $jscomp.Map.checkBrowserConformance_()) {
    $jscomp.Map = $jscomp.global["Map"];
  } else {
    $jscomp.initSymbol();
    $jscomp.initSymbolIterator();
    $jscomp.Map.prototype[Symbol.iterator] = $jscomp.Map.prototype.entries;
    $jscomp.initSymbol();
    /**@private */$jscomp.Map.key_ = Symbol("map-id-key");
  }
  $jscomp.Map$install = function() {
  };
};
/***/$jscomp.object = $jscomp.object || {};
/**
@param {!Object} target
@param {...!Object} sources
@return {!Object} */$jscomp.object.assign = function(target, sources) {
  var $jscomp$restParams = [];
  for (var $jscomp$restIndex = 1;$jscomp$restIndex < arguments.length;++$jscomp$restIndex) {
    $jscomp$restParams[$jscomp$restIndex - 1] = arguments[$jscomp$restIndex];
  }
  var sources$5 = $jscomp$restParams;
  for (var $jscomp$iter$3 = $jscomp.makeIterator(sources$5), $jscomp$key$source = $jscomp$iter$3.next();!$jscomp$key$source.done;$jscomp$key$source = $jscomp$iter$3.next()) {
    var source = $jscomp$key$source.value;
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
@return {boolean} */$jscomp.object.is = function(left, right) {
  if (left === right) {
    return left !== 0 || 1 / left === 1 / /**@type {number} */(right);
  } else {
    return left !== left && right !== right;
  }
};
/***/ /**@constructor @struct @template VALUE

@param {(!Iterable<VALUE>|!Array<VALUE>)=} opt_iterable
@implements {Iterable<VALUE>} */$jscomp.Set = function(opt_iterable) {
  opt_iterable = opt_iterable === undefined ? [] : opt_iterable;
  /**@const @private @type {!$jscomp.Map<VALUE,VALUE>} */this.map_ = new $jscomp.Map;
  if (opt_iterable) {
    for (var $jscomp$iter$4 = $jscomp.makeIterator(opt_iterable), $jscomp$key$item = $jscomp$iter$4.next();!$jscomp$key$item.done;$jscomp$key$item = $jscomp$iter$4.next()) {
      var item = $jscomp$key$item.value;
      this.add(/**@type {VALUE} */(item));
    }
  }
  this.size = this.map_.size;
};
/**@private
@return {boolean} */$jscomp.Set.checkBrowserConformance_ = function() {
  /**@const @type {function(new:Set,...?)} */var Set = $jscomp.global["Set"];
  if (!Set || !Set.prototype.entries || !Object.seal) {
    return false;
  }
  /**@const @type {!Object} */var value = Object.seal({x:4});
  /**@const */var set = new Set($jscomp.makeIterator([value]));
  if (set.has(value) || set.size != 1 || set.add(value) != set || set.size != 1 || set.add({x:4}) != set || set.size != 2) {
    return false;
  }
  /**@const @type {!Iterator<!Array<?>>} */var iter = set.entries();
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
@param {VALUE} value */$jscomp.Set.prototype.add = function(value) {
  this.map_.set(value, value);
  this.size = this.map_.size;
  return this;
};
/**@suppress {checkTypes}
@param {VALUE} value
@return {boolean} */$jscomp.Set.prototype["delete"] = function(value) {
  /**@const @type {boolean} */var result = this.map_["delete"](value);
  this.size = this.map_.size;
  return result;
};
/***/$jscomp.Set.prototype.clear = function() {
  this.map_.clear();
  this.size = 0;
};
/**
@param {*} value
@return {boolean} */$jscomp.Set.prototype.has = function(value) {
  return this.map_.has(value);
};
/**
@return {!Iterator<!Array<VALUE>>} */$jscomp.Set.prototype.entries = function() {
  return this.map_.entries();
};
/**
@return {!Iterator<VALUE>} */$jscomp.Set.prototype.values = function() {
  return this.map_.values();
};
/**@template THIS

@param {function(this:THIS,VALUE,VALUE,!$jscomp.Set<VALUE>)} callback
@param {THIS=} opt_thisArg */$jscomp.Set.prototype.forEach = function(callback, opt_thisArg) {
  var self = this;
  this.map_.forEach(function(value) {
    return callback.call(opt_thisArg, value, value, self);
  });
};
/**@define {boolean} */$jscomp.Set.ASSUME_NO_NATIVE = false;
/***/$jscomp.Set$install = function() {
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


