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

package com.google.javascript.jscomp.gwt.client;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsOverlay;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

import java.util.AbstractList;
import java.util.List;

/**
 * GWT/J2CL utilities to abstract out JS interop.
 */
public class Util {

  /** Wraps native RegExp. */
  @JsType(isNative = true, name = "RegExp", namespace = JsPackage.GLOBAL)
  public static class JsRegExp {
    public JsRegExp(String regex, String flags) {}

    @JsProperty
    public native int getLastIndex();

    @JsProperty
    public native boolean getIgnoreCase();

    @JsProperty
    public native boolean getGlobal();

    @JsProperty
    public native boolean getMultiline();

    @JsProperty
    public native String getSource();

    public native boolean test(String str);

    public native Match exec(String str);

    /** Return type for RegExp.prototype.exec. */
    @JsType(isNative = true)
    public static class Match extends JsArray<String> {
      protected Match() {}

      @JsProperty
      public native int getIndex();

      @JsProperty
      public native String getInput();
    }
  }

  /** Wraps native Object. */
  @JsType(isNative = true, name = "Object", namespace = JsPackage.GLOBAL)
  public static class JsObject<T> {
    public JsObject() {}

    @JsOverlay
    public final T get(String key) {
      return objectGet(this, key);
    }

    @JsOverlay
    public final JsObject<T> set(String key, T value) {
      objectSet(this, key, value);
      return this;
    }
  }

  /** Wraps native Array. */
  @JsType(isNative = true, name = "Array", namespace = JsPackage.GLOBAL)
  public static class JsArray<T> extends JsObject<T> {
    public JsArray() {}

    @JsProperty
    public native int getLength();

    @JsProperty
    public native void setLength(int length);

    public native void push(T obj);

    public native JsArray<T> slice();

    public native JsArray<T> slice(int start);

    public native JsArray<T> slice(int start, int end);

    @SuppressWarnings("unusable-by-js")
    public native JsArray<T> splice(int start, int end, T... elems);

    @JsOverlay
    public final T get(int i) {
      return arrayGet(this, i);
    }

    @JsOverlay
    public final void set(int i, T value) {
      arraySet(this, i, value);
    }

    @JsOverlay
    public final List<T> asList() {
      return new JsArrayList<T>(this);
    }

    @JsOverlay
    public static <T> JsArray<T> of(T... elems) {
      return slice(elems);
    }

    @SuppressWarnings("unusable-by-js")
    @JsMethod(name = "call", namespace = "Array.prototype.slice")
    public static native <T> JsArray<T> slice(T[] elems);

    @JsOverlay
    public static <T> JsArray<T> copyOf(Iterable<? extends T> elems) {
      JsArray<T> arr = of();
      for (T elem : elems) {
        arr.push(elem);
      }
      return arr;
    }

    /** List implementation for {@link JsArray}. */
    private static class JsArrayList<T> extends AbstractList<T> {
      final JsArray<T> array;
      JsArrayList(JsArray<T> array) {
        this.array = array;
      }

      void checkBounds(int index) {
        if (index < 0 || index >= array.getLength()) {
          throw new IndexOutOfBoundsException();
        }
      }

      @Override public T get(int index) {
        checkBounds(index);
        return array.get(index);
      }

      @Override public T set(int index, T elem) {
        checkBounds(index);
        T prev = array.get(index);
        array.set(index, elem);
        return prev;
      }

      @Override public T remove(int index) {
        checkBounds(index);
        T prev = array.get(index);
        array.splice(index, 1);
        return prev;
      }

      @Override public boolean add(T elem) {
        array.push(elem);
        return true;
      }

      @Override public int size() {
        return array.getLength();
      }
    }
  }

  /** Wraps native String, to provide static methods. */
  @JsType(isNative = true, name = "String", namespace = JsPackage.GLOBAL)
  public static class JsString {
    public JsString() {}

    public static native String fromCharCode(int charCode);
  }

  // PRIVATE UTILITY METHODS

  @JsMethod(namespace = "util")
  private static native <T> T arrayGet(JsArray<T> array, int i);

  @JsMethod(namespace = "util")
  private static native <T> void arraySet(JsArray<T> array, int i, T value);

  @JsMethod(namespace = "util")
  private static native <T> T objectGet(JsObject<T> array, String key);

  @JsMethod(namespace = "util")
  private static native <T> void objectSet(JsObject<T> array, String key, T value);
}
