/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered set that moves values to the front when added (even if already contained) and which
 * allows for the marking of named points in time in the series from which point an accumulation of
 * all following values can be (efficiently) requested.
 */
// TODO(stalcup): save memory by unlinking events older than the oldest marked time.
final class Timeline<T> {

  private static class Event<T> {
    Event<?> nextEvent;
    Event<?> previousEvent;
    T value;

    Event(T value) {
      checkNotNull(value);
      this.value = value;
    }
  }

  private static class Time {
    final String name;

    Time(String name) {
      checkNotNull(name);
      this.name = name;
    }

    @Override
    public boolean equals(Object anObject) {
      if (anObject instanceof Time) {
        return name.equals(((Time) anObject).name);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  private static <V> Event<V> addEvent(V value, Map<V, Event<V>> eventsByKey, Event<?> headEvent) {
    Event<V> event = eventsByKey.get(value);

    // If the event already exists and is already at the front, do nothing.
    if (headEvent == event) {
      return event;
    }

    // If the event already exists somewhere else in the history then...
    if (event != null) {
      // cut it out of the linked list...
      event.previousEvent.nextEvent = event.nextEvent;
      event.nextEvent.previousEvent = event.previousEvent;
      event.nextEvent = null;
    } else {
      // Otherwise create and track an event for the given value.
      event = new Event<V>(value);
      eventsByKey.put(value, event);
    }

    // Regardless, stick the event at the front.
    event.previousEvent = headEvent;
    headEvent.nextEvent = event;
    headEvent = event;

    return event;
  }

  private final Map<Time, Event<Time>> eventsByTime = new HashMap<>();
  private final Map<T, Event<T>> eventsByValue = new HashMap<>();
  private Event<?> headEvent = new Event<>(new Time("-beginning-"));

  void add(T value) {
    headEvent = addEvent(value, eventsByValue, headEvent);
  }

  void remove(T value) {
    Event<T> event = eventsByValue.remove(value);

    // If the event already exists somewhere else in the history then...
    if (event != null) {
      // make the rest of the list not reference it...
      if (event.nextEvent != null) {
        event.nextEvent.previousEvent = event.previousEvent;
      } else {
        // if it was the head element then back the head reference up one node.
        headEvent = event.previousEvent;
      }
      event.previousEvent.nextEvent = event.nextEvent;

      // make it not reference the rest of the list.
      event.nextEvent = null;
      event.previousEvent = null;
    }
  }

  void mark(String timeName) {
    headEvent = addEvent(new Time(timeName), eventsByTime, headEvent);
  }

  @SuppressWarnings("unchecked")
  List<T> getSince(String timeName) {
    List<T> values = new ArrayList<>();

    Event<?> firstEvent = eventsByTime.get(new Time(timeName));
    if (firstEvent == null) {
      return null;
    }

    for (Event<?> event = firstEvent; event != null; event = event.nextEvent) {
      // If the event contains a user provided value, accumulate it.
      if (!(event.value instanceof Time)) {
        values.add((T) event.value);
      }
    }

    return values;
  }
}
