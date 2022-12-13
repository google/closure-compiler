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
 *   Nick Santos
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

package com.google.javascript.rhino.jstype;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JSTypeResolverTest {

  private static final JSTypeClass STUB_TYPE_CLASS = JSTypeClass.FUNCTION;

  private final JSTypeRegistry registry = new JSTypeRegistry(null, null);
  private final JSTypeResolver resolver = registry.getResolver();

  @Test
  public void capturesAllTypes_beforeOpening_isVerified() {
    // Given
    new CustomTypeBuilder()
        .setCtor(
            (t) -> {
              // Forget to call `resolveIfClosed` here.
            })
        .build();

    // Then
    assertThrows(Exception.class, this.resolver::openForDefinition);
  }

  @Test
  @SuppressWarnings("MustBeClosedChecker")
  public void capturesAllTypes_beforeClosing_isVerified() {
    // Given
    JSTypeResolver.Closer closer = this.resolver.openForDefinition();

    new CustomTypeBuilder()
        .setCtor(
            (t) -> {
              // Forget to call `resolveIfClosed` here.
            })
        .build();

    // Then
    assertThrows(Exception.class, closer::close);
  }

  @Test
  public void capturesAllTypes_inPostorder_isVerified() {
    // When
    ThrowingRunnable when =
        () -> {
          new CustomTypeBuilder()
              .setCtor(
                  (t1) -> {
                    new CustomTypeBuilder()
                        .setCtor(
                            (t2) -> {
                              // Forget to call `resolveIfClosed` here.
                            })
                        .build();

                    this.resolver.resolveIfClosed(t1, STUB_TYPE_CLASS);
                  })
              .build();
        };

    // Then
    assertThrows(Exception.class, when);
  }

  @Test
  public void capturesAllTypes_allowsNestedConstructorCalls() {
    // Test this does not throw.
    new CustomTypeBuilder()
        .setCtor(
            (t1) -> {
              new CustomTypeBuilder()
                  .setCtor(
                      (t2) -> {
                        this.resolver.resolveIfClosed(t2, STUB_TYPE_CLASS);
                      })
                  .build();

              this.resolver.resolveIfClosed(t1, STUB_TYPE_CLASS);
            })
        .build();
  }

  @Test
  public void capturesAllTypes_onlyOnce_isVerified() {
    // Given
    JSType type =
        new CustomTypeBuilder()
            .setCtor(
                (t) -> {
                  this.resolver.resolveIfClosed(t, STUB_TYPE_CLASS);
                })
            .build();

    // When
    ThrowingRunnable when =
        () -> {
          this.resolver.resolveIfClosed(type, STUB_TYPE_CLASS);
        };

    // Then
    assertThrows(Exception.class, when);
  }

  @Test
  public void capturesAllTypes_ignoresNonLowestSubclassConstructor() {
    // Given
    JSTypeClass superclass = JSTypeClass.ALL;
    assertThat(superclass).isNotEqualTo(STUB_TYPE_CLASS);

    // Test this does not throw.
    new CustomTypeBuilder()
        .setCtor(
            (t) -> {
              this.resolver.resolveIfClosed(t, superclass);
              this.resolver.resolveIfClosed(t, STUB_TYPE_CLASS);
            })
        .build();
  }

  @Test
  public void resolvesAllTypes_eagerly_whileClosed() {
    // Given
    JSType example =
        new CustomTypeBuilder()
            .setCtor((t) -> this.resolver.resolveIfClosed(t, STUB_TYPE_CLASS))
            .build();

    // Then
    assertThat(example.isResolved()).isTrue();
  }

  @Test
  public void resolvesAllTypes_eagerly_whileClosing() {
    // Given
    ArrayList<String> events = new ArrayList<>();

    // When
    try (JSTypeResolver.Closer closer = this.resolver.openForDefinition()) {
      new CustomTypeBuilder()
          .setCtor(
              (t1) -> {
                events.add("t1_ctor_start");
                this.resolver.resolveIfClosed(t1, STUB_TYPE_CLASS);
                events.add("t1_ctor_end");
              })
          .setResolve(
              (t1) -> {
                events.add("t1_resolve_start");
                new CustomTypeBuilder()
                    .setCtor(
                        (t2) -> {
                          events.add("t2_ctor_start");
                          this.resolver.resolveIfClosed(t2, STUB_TYPE_CLASS);
                          events.add("t2_ctor_end");
                        })
                    .setResolve(
                        (t2) -> {
                          events.add("t2_resolve");
                        })
                    .build();
                events.add("t1_resolve_end");
              })
          .build();
    }

    // Then
    assertThat(events)
        .isEqualTo(
            ImmutableList.of(
                "t1_ctor_start",
                "t1_ctor_end",
                "t1_resolve_start",
                "t2_ctor_start",
                "t2_resolve",
                "t2_ctor_end",
                "t1_resolve_end"));
  }

  @Test
  public void resolvesAllTypes_lazily_whileOpen() {
    // Given
    ArrayList<String> events = new ArrayList<>();

    // When
    try (JSTypeResolver.Closer closer = this.resolver.openForDefinition()) {
      new CustomTypeBuilder()
          .setCtor(
              (t1) -> {
                events.add("ctor_start");
                this.resolver.resolveIfClosed(t1, STUB_TYPE_CLASS);
                events.add("ctor_end");
              })
          .setResolve(
              (t1) -> {
                events.add("resolve");
              })
          .build();
      events.add("close");
    }

    // Then
    assertThat(events)
        .isEqualTo(
            ImmutableList.of(
                "ctor_start", //
                "ctor_end",
                "close",
                "resolve"));
  }

  @Test
  @SuppressWarnings("MustBeClosedChecker")
  public void cannotBeOpened_whileOpen() {
    // Given
    this.resolver.openForDefinition();

    // Then
    assertThrows(Exception.class, this.resolver::openForDefinition);
  }

  @Test
  @SuppressWarnings("MustBeClosedChecker")
  public void cannotBeOpened_whileClosing() {
    // Given
    JSTypeResolver.Closer closer = this.resolver.openForDefinition();
    new CustomTypeBuilder() //
        .setResolve((t) -> this.resolver.openForDefinition())
        .build();

    // Then
    assertThrows(Exception.class, closer::close);
  }

  @Test
  @SuppressWarnings("MustBeClosedChecker")
  public void closer_cannotBeReused() {
    // Given
    JSTypeResolver.Closer closer = this.resolver.openForDefinition();
    closer.close();

    // Then
    assertThrows(Exception.class, closer::close);
  }

  @Test
  public void types_cannotBeResolved_whileOpen() {
    try (JSTypeResolver.Closer closer = this.resolver.openForDefinition()) {
      assertThrows(
          Exception.class,
          () ->
              new CustomTypeBuilder()
                  .setCtor((jstype) -> resolver.resolveIfClosed(jstype, jstype.getTypeClass()))
                  .build()
                  .resolve(registry.getErrorReporter()));
    }
  }

  private final class CustomTypeBuilder {
    private Consumer<JSType> ctor = (t) -> {};
    private Consumer<JSType> resolve = (t) -> {};

    CustomTypeBuilder setCtor(Consumer<JSType> x) {
      this.ctor = x;
      return this;
    }

    CustomTypeBuilder setResolve(Consumer<JSType> x) {
      this.resolve = x;
      return this;
    }

    JSType build() {
      JSTypeRegistry testRegistry = JSTypeResolverTest.this.registry;
      Consumer<JSType> ctor = this.ctor;
      Consumer<JSType> resolve = this.resolve;

      class CustomJSType extends UnitTestingJSType {
        CustomJSType() {
          super(testRegistry);
          ctor.accept(this);
        }

        @Override
        JSTypeClass getTypeClass() {
          return STUB_TYPE_CLASS;
        }

        @Override
        JSType resolveInternal(ErrorReporter reporter) {
          resolve.accept(this);
          return this;
        }
      }

      return new CustomJSType();
    }
  }
}
