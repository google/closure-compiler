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
/**@const */$jscomp.global = $jscomp.getGlobal(this);
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
      /**@const */var descriptor = $jscomp.global.Object.getOwnPropertyDescriptor(parentCtor, p);
      if (descriptor !== undefined) {
        $jscomp.global.Object.defineProperty(childCtor, p, descriptor);
      }
    } else {
      childCtor[p] = parentCtor[p];
    }
  }
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
  var sources$1 = $jscomp$restParams;
  for (var $jscomp$iter$0 = $jscomp.makeIterator(sources$1), $jscomp$key$source = $jscomp$iter$0.next();!$jscomp$key$source.done;$jscomp$key$source = $jscomp$iter$0.next()) {
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


