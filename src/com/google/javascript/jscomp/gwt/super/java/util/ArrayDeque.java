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

package java.util;

import java.io.Serializable;

/**
 * An ArrayDeque that simply wraps around a LinkedList.
 * TODO(moz): Remove once GWT emulates ArrayDeque. Pending changelist at:
 * https://gwt-review.googlesource.com/#/c/6037/
 *
 * @author moz@google.com (Michael Zhou)
 */
public class ArrayDeque<E> extends AbstractCollection<E> implements
    Cloneable, Deque<E>, Serializable {
  private final LinkedList<E> delegate;

  public ArrayDeque() {
    delegate = new LinkedList<>();
  }

  public ArrayDeque(Collection<? extends E> c) {
    delegate = new LinkedList<>(c);
  }

  public int size() {
    return delegate.size();
  }

  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  public E getFirst() {
    return delegate.getFirst();
  }

  public E getLast() {
    return delegate.getLast();
  }

  public E peek() {
    return delegate.peek();
  }

  public E peekFirst() {
    return delegate.peekFirst();
  }

  public E peekLast() {
    return delegate.peekLast();
  }

  public void push(E e) {
    delegate.push(e);
  }

  public E pop() {
    return delegate.pop();
  }

  public E poll() {
    return delegate.poll();
  }

  public E pollFirst() {
    return delegate.pollFirst();
  }

  public E pollLast() {
    return delegate.pollLast();
  }

  public boolean add(E e) {
    return delegate.add(e);
  }

  public void addLast(E e) {
    delegate.addLast(e);
  }

  public void addFirst(E e) {
    delegate.addFirst(e);
  }

  public boolean addAll(Collection<? extends E> c) {
    return delegate.addAll(c);
  }

  public E remove() {
    return delegate.remove();
  }

  public E removeFirst() {
    return delegate.removeFirst();
  }

  public E removeLast() {
    return delegate.removeLast();
  }

  public boolean offer(E e) {
    return delegate.offer(e);
  }

  public boolean offerFirst(E e) {
    return delegate.offerFirst(e);
  }

  public boolean offerLast(E e) {
    return delegate.offerLast(e);
  }

  public E element() {
    return delegate.element();
  }

  public Iterator<E> iterator() {
    return delegate.iterator();
  }

  public Iterator<E> descendingIterator() {
    return delegate.descendingIterator();
  }

  public boolean removeFirstOccurrence(Object o) {
    return delegate.removeFirstOccurrence(o);
  }

  public boolean removeLastOccurrence(Object o) {
    return delegate.removeLastOccurrence(o);
  }
}
