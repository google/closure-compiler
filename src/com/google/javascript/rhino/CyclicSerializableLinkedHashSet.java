/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */
package com.google.javascript.rhino;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ForwardingSet;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A {@link LinkedHashSet} that can be safely (de)serialized when involved in a reference cycle.
 *
 * <p>The motivating usecase of this class is the afformentioned cycles. Consider the following
 * example:
 *
 * <pre>{@code
 * class Foo implements Serializable {
 *   private LinkedHashSet<Foo> children = new LinkedHashSet<>();
 *
 *   private Object hashSource = new Object();
 *
 *   public void addChild(Foo child) {
 *     children.addChild(child);
 *   }
 *
 *   {@literal @}Override
 *   public int hashCode() {
 *     return hashSouce.hashCode();
 *   }
 * }
 *
 * ****
 *
 *   Foo f = new Foo();
 *   f.addChild(f);
 *
 * ****
 * }</pre>
 *
 * If {@code f} were serialized and reserialized, there is a possiblity of exceptions via the
 * following steps. This class is designed to eliminate such issue.
 *
 * <ol>
 *   <li>{@code f} begins deserialization
 *   <li>{@code f.children} begins deserialization
 *   <li>{@code f.children} attempts to add {@code f} to itself
 *   <li>{@code f.hashCode()} is called
 *   <li>{@code f.hashSource} is {@code null}, throwing a {@code NullPointerException}
 * </ol>
 */
public final class CyclicSerializableLinkedHashSet<T> extends ForwardingSet<T>
    implements Serializable {

  // This field will be non-null between `readObject` and `lazyCompleteDeserialization`.
  @Nullable private transient List<T> deserializedElementList;

  // This field will be null between `readObject` and `lazyCompleteDeserialization`.
  @Nullable private transient LinkedHashSet<T> backingSet = new LinkedHashSet<>();

  @Override
  protected Set<T> delegate() {
    lazyCompleteDeserialization();
    return backingSet;
  }

  @GwtIncompatible
  private void writeObject(ObjectOutputStream out) throws IOException {
    // Serialize the contents of this set as a list so that they can be deserialized without
    // immediately reconstructing the set. We want to prevent `hashCode` from being called on the
    // elements until deserialization is totally done.
    out.writeObject(new ArrayList<>(this));
  }

  @GwtIncompatible
  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
    // Deserialize the contents of this set from a list so that we don't immediately reconstruct the
    // set. We want to prevent `hashCode` from being called on the elements until deserialization is
    // totally done.
    deserializedElementList = (ArrayList<T>) in.readObject();
  }

  private void lazyCompleteDeserialization() {
    if (!deserializationAwaitingCompletion()) {
      checkState(
          backingSet != null,
          "Deserialization is not awaiting completion, but the backing set is not initialized. "
              + "Is another class calling methods on this object during deserialization?");
      return;
    }

    backingSet = new LinkedHashSet<>();
    backingSet.addAll(deserializedElementList);
    deserializedElementList = null;
  }

  private boolean deserializationAwaitingCompletion() {
    return deserializedElementList != null;
  }
}
