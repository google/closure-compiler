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

let /** number */ A = 0;
let /** number */ B = 1;

export {A as a, B as b};

/** number */
export let c = 2;

/**
 * @param {number} newA
 * @param {number} newB
 * @param {number} newC
 */
export function set(newA, newB, newC) {
  A = newA;
  B = newB;
  c = newC;
}
