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

package com.google.common.hash;

import java.nio.charset.Charset;

/** No-op J2CL impl */
public abstract class Hasher {

  public abstract Hasher putInt(int i);

  public abstract Hasher putLong(long x);

  public abstract Hasher putString(CharSequence charSequence, Charset charset);

  public abstract HashCode hash();

  private Hasher() {}
}
