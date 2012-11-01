/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import org.mozilla.javascript.*;

/**
 * An example WrapFactory that can be used to avoid wrapping of Java types
 * that can be converted to ECMA primitive values.
 * So java.lang.String is mapped to ECMA string, all java.lang.Numbers are
 * mapped to ECMA numbers, and java.lang.Booleans are mapped to ECMA booleans
 * instead of being wrapped as objects. Additionally java.lang.Character is
 * converted to ECMA string with length 1.
 * Other types have the default behavior.
 * <p>
 * Note that calling "new java.lang.String('foo')" in JavaScript with this
 * wrap factory enabled will still produce a wrapped Java object since the
 * WrapFactory.wrapNewObject method is not overridden.
 * <p>
 * The PrimitiveWrapFactory is enabled on a Context by calling setWrapFactory
 * on that context.
 */
public class PrimitiveWrapFactory extends WrapFactory {
  @Override
  public Object wrap(Context cx, Scriptable scope, Object obj,
                     Class<?> staticType)
  {
    if (obj instanceof String || obj instanceof Number ||
        obj instanceof Boolean)
    {
      return obj;
    } else if (obj instanceof Character) {
      char[] a = { ((Character)obj).charValue() };
      return new String(a);
    }
    return super.wrap(cx, scope, obj, staticType);
  }
}
