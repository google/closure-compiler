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
 * @fileoverview Defines methods to allow the Promises/A+ compliance tests to
 * create Promises.
 */
// TODO(bradfordcsmith): This assignment is only needed until we start really
// polyfilling Promises.
Promise = $jscomp_Promise;
exports.resolved = function(value) { return Promise.resolve(value); };
exports.rejected = function(reason) { return Promise.reject(reason); };
exports.deferred = function() {
  var capability = {};
  capability.promise = new Promise(function(resolve, reject) {
    capability.resolve = resolve;
    capability.reject = reject;
  });
  return capability;
};
