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

package com.google.javascript.jscomp.newtypes;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;

/**
 * In rare cases, when joining or meeting sets of objects, we end up with a set
 * that includes two objects A and B with the same nominal type N.
 * This custom builder avoids that by replacing A and B with a new object of
 * the same nominal type N.
 */
class ObjectsBuilder {
  static enum ResolveConflictsBy {
    JOIN,
    MEET
  }

  private final ResolveConflictsBy resolution;
  private final ArrayList<ObjectType> objs;

  ObjectsBuilder(ResolveConflictsBy resolution) {
    this.resolution = resolution;
    this.objs = new ArrayList<>();
  }

  void add(ObjectType newObj) {
    boolean addedObj = false;
    // We use an ArrayList and an explicit loop, because we modify this.objs
    // while iterating over it.
    for (int i = 0; i < this.objs.size(); i++) {
      ObjectType oldObj = this.objs.get(i);
      if (NominalType.equalRawTypes(
          oldObj.getNominalType(), newObj.getNominalType())) {
        addedObj = true;
        if (this.resolution == ResolveConflictsBy.JOIN) {
          this.objs.set(i, ObjectType.join(oldObj, newObj));
        } else {
          this.objs.set(i, ObjectType.meet(oldObj, newObj));
        }
      }
    }
    if (!addedObj) {
      this.objs.add(newObj);
    }
  }

  ImmutableSet<ObjectType> build() {
    return ImmutableSet.copyOf(this.objs);
  }
}
