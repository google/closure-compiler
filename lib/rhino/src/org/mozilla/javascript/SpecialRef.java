/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

class SpecialRef extends Ref
{
    static final long serialVersionUID = -7521596632456797847L;

    private static final int SPECIAL_NONE = 0;
    private static final int SPECIAL_PROTO = 1;
    private static final int SPECIAL_PARENT = 2;

    private Scriptable target;
    private int type;
    private String name;

    private SpecialRef(Scriptable target, int type, String name)
    {
        this.target = target;
        this.type = type;
        this.name = name;
    }

    static Ref createSpecial(Context cx, Object object, String name)
    {
        Scriptable target = ScriptRuntime.toObjectOrNull(cx, object);
        if (target == null) {
            throw ScriptRuntime.undefReadError(object, name);
        }

        int type;
        if (name.equals("__proto__")) {
            type = SPECIAL_PROTO;
        } else if (name.equals("__parent__")) {
            type = SPECIAL_PARENT;
        } else {
            throw new IllegalArgumentException(name);
        }

        if (!cx.hasFeature(Context.FEATURE_PARENT_PROTO_PROPERTIES)) {
            // Clear special after checking for valid name!
            type = SPECIAL_NONE;
        }

        return new SpecialRef(target, type, name);
    }

    @Override
    public Object get(Context cx)
    {
        switch (type) {
          case SPECIAL_NONE:
            return ScriptRuntime.getObjectProp(target, name, cx);
          case SPECIAL_PROTO:
            return target.getPrototype();
          case SPECIAL_PARENT:
            return target.getParentScope();
          default:
            throw Kit.codeBug();
        }
    }

    @Override
    public Object set(Context cx, Object value)
    {
        switch (type) {
          case SPECIAL_NONE:
            return ScriptRuntime.setObjectProp(target, name, value, cx);
          case SPECIAL_PROTO:
          case SPECIAL_PARENT:
            {
                Scriptable obj = ScriptRuntime.toObjectOrNull(cx, value);
                if (obj != null) {
                    // Check that obj does not contain on its prototype/scope
                    // chain to prevent cycles
                    Scriptable search = obj;
                    do {
                        if (search == target) {
                            throw Context.reportRuntimeError1(
                                "msg.cyclic.value", name);
                        }
                        if (type == SPECIAL_PROTO) {
                            search = search.getPrototype();
                        } else {
                            search = search.getParentScope();
                        }
                    } while (search != null);
                }
                if (type == SPECIAL_PROTO) {
                    target.setPrototype(obj);
                } else {
                    target.setParentScope(obj);
                }
                return obj;
            }
          default:
            throw Kit.codeBug();
        }
    }

    @Override
    public boolean has(Context cx)
    {
        if (type == SPECIAL_NONE) {
            return ScriptRuntime.hasObjectElem(target, name, cx);
        }
        return true;
    }

    @Override
    public boolean delete(Context cx)
    {
        if (type == SPECIAL_NONE) {
            return ScriptRuntime.deleteObjectElem(target, name, cx);
        }
        return false;
    }
}

