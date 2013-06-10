/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.io.Serializable;
import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.v8dtoa.FastDtoa;
import org.mozilla.javascript.xml.XMLObject;
import org.mozilla.javascript.xml.XMLLib;

/**
 * This is the class that implements the runtime.
 *
 */

public class ScriptRuntime {

    /**
     * No instances should be created.
     */
    protected ScriptRuntime() {
    }


    /**
     * Returns representation of the [[ThrowTypeError]] object.
     * See ECMA 5 spec, 13.2.3
     */
    public static BaseFunction typeErrorThrower() {
      if (THROW_TYPE_ERROR == null) {
        BaseFunction thrower = new BaseFunction() {
          static final long serialVersionUID = -5891740962154902286L;

          @Override
          public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            throw typeError0("msg.op.not.allowed");
          }
          @Override
          public int getLength() {
            return 0;
          }
        };
        thrower.preventExtensions();
        THROW_TYPE_ERROR = thrower;
      }
      return THROW_TYPE_ERROR;
    }
    private static BaseFunction THROW_TYPE_ERROR = null;

    static class NoSuchMethodShim implements Callable {
        String methodName;
        Callable noSuchMethodMethod;

        NoSuchMethodShim(Callable noSuchMethodMethod, String methodName)
        {
            this.noSuchMethodMethod = noSuchMethodMethod;
            this.methodName = methodName;
        }
        /**
         * Perform the call.
         *
         * @param cx the current Context for this thread
         * @param scope the scope to use to resolve properties.
         * @param thisObj the JavaScript <code>this</code> object
         * @param args the array of arguments
         * @return the result of the call
         */
        public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                           Object[] args)
        {
            Object[] nestedArgs = new Object[2];

            nestedArgs[0] = methodName;
            nestedArgs[1] = newArrayLiteral(args, null, cx, scope);
            return noSuchMethodMethod.call(cx, scope, thisObj, nestedArgs);
        }

    }
    /*
     * There's such a huge space (and some time) waste for the Foo.class
     * syntax: the compiler sticks in a test of a static field in the
     * enclosing class for null and the code for creating the class value.
     * It has to do this since the reference has to get pushed off until
     * execution time (i.e. can't force an early load), but for the
     * 'standard' classes - especially those in java.lang, we can trust
     * that they won't cause problems by being loaded early.
     */

    public final static Class<?>
        BooleanClass      = Kit.classOrNull("java.lang.Boolean"),
        ByteClass         = Kit.classOrNull("java.lang.Byte"),
        CharacterClass    = Kit.classOrNull("java.lang.Character"),
        ClassClass        = Kit.classOrNull("java.lang.Class"),
        DoubleClass       = Kit.classOrNull("java.lang.Double"),
        FloatClass        = Kit.classOrNull("java.lang.Float"),
        IntegerClass      = Kit.classOrNull("java.lang.Integer"),
        LongClass         = Kit.classOrNull("java.lang.Long"),
        NumberClass       = Kit.classOrNull("java.lang.Number"),
        ObjectClass       = Kit.classOrNull("java.lang.Object"),
        ShortClass        = Kit.classOrNull("java.lang.Short"),
        StringClass       = Kit.classOrNull("java.lang.String"),
        DateClass         = Kit.classOrNull("java.util.Date");

    public final static Class<?>
        ContextClass
            = Kit.classOrNull("org.mozilla.javascript.Context"),
        ContextFactoryClass
            = Kit.classOrNull("org.mozilla.javascript.ContextFactory"),
        FunctionClass
            = Kit.classOrNull("org.mozilla.javascript.Function"),
        ScriptableObjectClass
            = Kit.classOrNull("org.mozilla.javascript.ScriptableObject");
    public static final Class<Scriptable> ScriptableClass =
        Scriptable.class;

    // Locale object used to request locale-neutral operations.
    public static Locale ROOT_LOCALE = new Locale("");

    private static final Object LIBRARY_SCOPE_KEY = "LIBRARY_SCOPE";

    public static boolean isRhinoRuntimeType(Class<?> cl)
    {
        if (cl.isPrimitive()) {
            return (cl != Character.TYPE);
        } else {
            return (cl == StringClass || cl == BooleanClass
                    || NumberClass.isAssignableFrom(cl)
                    || ScriptableClass.isAssignableFrom(cl));
        }
    }

    public static ScriptableObject initStandardObjects(Context cx,
                                                       ScriptableObject scope,
                                                       boolean sealed)
    {
        if (scope == null) {
            scope = new NativeObject();
        }
        scope.associateValue(LIBRARY_SCOPE_KEY, scope);
        (new ClassCache()).associate(scope);

        BaseFunction.init(scope, sealed);
        NativeObject.init(scope, sealed);

        Scriptable objectProto = ScriptableObject.getObjectPrototype(scope);

        // Function.prototype.__proto__ should be Object.prototype
        Scriptable functionProto = ScriptableObject.getClassPrototype(scope, "Function");
        functionProto.setPrototype(objectProto);

        // Set the prototype of the object passed in if need be
        if (scope.getPrototype() == null)
            scope.setPrototype(objectProto);

        // must precede NativeGlobal since it's needed therein
        NativeError.init(scope, sealed);
        NativeGlobal.init(cx, scope, sealed);

        NativeArray.init(scope, sealed);
        if (cx.getOptimizationLevel() > 0) {
            // When optimizing, attempt to fulfill all requests for new Array(N)
            // with a higher threshold before switching to a sparse
            // representation
            NativeArray.setMaximumInitialCapacity(200000);
        }
        NativeString.init(scope, sealed);
        NativeBoolean.init(scope, sealed);
        NativeNumber.init(scope, sealed);
        NativeDate.init(scope, sealed);
        NativeMath.init(scope, sealed);
        NativeJSON.init(scope, sealed);

        NativeWith.init(scope, sealed);
        NativeCall.init(scope, sealed);
        NativeScript.init(scope, sealed);

        NativeIterator.init(scope, sealed); // Also initializes NativeGenerator

        boolean withXml = cx.hasFeature(Context.FEATURE_E4X) &&
                          cx.getE4xImplementationFactory() != null;

        // define lazy-loaded properties using their class name
        new LazilyLoadedCtor(scope, "RegExp",
                "org.mozilla.javascript.regexp.NativeRegExp", sealed, true);
        new LazilyLoadedCtor(scope, "Packages",
                "org.mozilla.javascript.NativeJavaTopPackage", sealed, true);
        new LazilyLoadedCtor(scope, "getClass",
                "org.mozilla.javascript.NativeJavaTopPackage", sealed, true);
        new LazilyLoadedCtor(scope, "JavaAdapter",
                "org.mozilla.javascript.JavaAdapter", sealed, true);
        new LazilyLoadedCtor(scope, "JavaImporter",
                "org.mozilla.javascript.ImporterTopLevel", sealed, true);
        new LazilyLoadedCtor(scope, "Continuation",
                "org.mozilla.javascript.NativeContinuation", sealed, true);

        for (String packageName : getTopPackageNames()) {
            new LazilyLoadedCtor(scope, packageName,
                    "org.mozilla.javascript.NativeJavaTopPackage", sealed, true);
        }

        if (withXml) {
            String xmlImpl = cx.getE4xImplementationFactory().getImplementationClassName();
            new LazilyLoadedCtor(scope, "XML", xmlImpl, sealed, true);
            new LazilyLoadedCtor(scope, "XMLList", xmlImpl, sealed, true);
            new LazilyLoadedCtor(scope, "Namespace", xmlImpl, sealed, true);
            new LazilyLoadedCtor(scope, "QName", xmlImpl, sealed, true);
        }

        if (scope instanceof TopLevel) {
            ((TopLevel)scope).cacheBuiltins();
        }

        return scope;
    }

    static String[] getTopPackageNames() {
        // Include "android" top package if running on Android
        return "Dalvik".equals(System.getProperty("java.vm.name")) ?
            new String[] { "java", "javax", "org", "com", "edu", "net", "android" } :
            new String[] { "java", "javax", "org", "com", "edu", "net" };
    }

    public static ScriptableObject getLibraryScopeOrNull(Scriptable scope)
    {
        ScriptableObject libScope;
        libScope = (ScriptableObject)ScriptableObject.
                       getTopScopeValue(scope, LIBRARY_SCOPE_KEY);
        return libScope;
    }

    // It is public so NativeRegExp can access it.
    public static boolean isJSLineTerminator(int c)
    {
        // Optimization for faster check for eol character:
        // they do not have 0xDFD0 bits set
        if ((c & 0xDFD0) != 0) {
            return false;
        }
        return c == '\n' || c == '\r' || c == 0x2028 || c == 0x2029;
    }

    public static boolean isJSWhitespaceOrLineTerminator(int c) {
      return (isStrWhiteSpaceChar(c) || isJSLineTerminator(c));
    }

    /**
     * Indicates if the character is a Str whitespace char according to ECMA spec:
     * StrWhiteSpaceChar :::
      <TAB>
      <SP>
      <NBSP>
      <FF>
      <VT>
      <CR>
      <LF>
      <LS>
      <PS>
      <USP>
      <BOM>
     */
    static boolean isStrWhiteSpaceChar(int c)
    {
    	switch (c) {
    		case ' ': // <SP>
    		case '\n': // <LF>
    		case '\r': // <CR>
    		case '\t': // <TAB>
    		case '\u00A0': // <NBSP>
    		case '\u000C': // <FF>
    		case '\u000B': // <VT>
    		case '\u2028': // <LS>
    		case '\u2029': // <PS>
        case '\uFEFF': // <BOM>
    			return true;
    		default:
    			return Character.getType(c) == Character.SPACE_SEPARATOR;
    	}
    }

    public static Boolean wrapBoolean(boolean b)
    {
        return b ? Boolean.TRUE : Boolean.FALSE;
    }

    public static Integer wrapInt(int i)
    {
        return Integer.valueOf(i);
    }

    public static Number wrapNumber(double x)
    {
        if (Double.isNaN(x)) {
            return ScriptRuntime.NaNobj;
        }
        return new Double(x);
    }

    /**
     * Convert the value to a boolean.
     *
     * See ECMA 9.2.
     */
    public static boolean toBoolean(Object val)
    {
        for (;;) {
            if (val instanceof Boolean)
                return ((Boolean) val).booleanValue();
            if (val == null || val == Undefined.instance)
                return false;
            if (val instanceof CharSequence)
                return ((CharSequence) val).length() != 0;
            if (val instanceof Number) {
                double d = ((Number) val).doubleValue();
                return (!Double.isNaN(d) && d != 0.0);
            }
            if (val instanceof Scriptable) {
                if (val instanceof ScriptableObject &&
                    ((ScriptableObject) val).avoidObjectDetection())
                {
                    return false;
                }
                if (Context.getContext().isVersionECMA1()) {
                    // pure ECMA
                    return true;
                }
                // ECMA extension
                val = ((Scriptable) val).getDefaultValue(BooleanClass);
                if (val instanceof Scriptable)
                    throw errorWithClassName("msg.primitive.expected", val);
                continue;
            }
            warnAboutNonJSObject(val);
            return true;
        }
    }

    /**
     * Convert the value to a number.
     *
     * See ECMA 9.3.
     */
    public static double toNumber(Object val)
    {
        for (;;) {
            if (val instanceof Number)
                return ((Number) val).doubleValue();
            if (val == null)
                return +0.0;
            if (val == Undefined.instance)
                return NaN;
            if (val instanceof String)
                return toNumber((String) val);
            if (val instanceof CharSequence)
                return toNumber(val.toString());
            if (val instanceof Boolean)
                return ((Boolean) val).booleanValue() ? 1 : +0.0;
            if (val instanceof Scriptable) {
                val = ((Scriptable) val).getDefaultValue(NumberClass);
                if (val instanceof Scriptable)
                    throw errorWithClassName("msg.primitive.expected", val);
                continue;
            }
            warnAboutNonJSObject(val);
            return NaN;
        }
    }

    public static double toNumber(Object[] args, int index) {
        return (index < args.length) ? toNumber(args[index]) : NaN;
    }

    // Can not use Double.NaN defined as 0.0d / 0.0 as under the Microsoft VM,
    // versions 2.01 and 3.0P1, that causes some uses (returns at least) of
    // Double.NaN to be converted to 1.0.
    // So we use ScriptRuntime.NaN instead of Double.NaN.
    public static final double
        NaN = Double.longBitsToDouble(0x7ff8000000000000L);

    // A similar problem exists for negative zero.
    public static final double
        negativeZero = Double.longBitsToDouble(0x8000000000000000L);

    public static final Double NaNobj = new Double(NaN);

    /*
     * Helper function for toNumber, parseInt, and TokenStream.getToken.
     */
    static double stringToNumber(String s, int start, int radix) {
        char digitMax = '9';
        char lowerCaseBound = 'a';
        char upperCaseBound = 'A';
        int len = s.length();
        if (radix < 10) {
            digitMax = (char) ('0' + radix - 1);
        }
        if (radix > 10) {
            lowerCaseBound = (char) ('a' + radix - 10);
            upperCaseBound = (char) ('A' + radix - 10);
        }
        int end;
        double sum = 0.0;
        for (end=start; end < len; end++) {
            char c = s.charAt(end);
            int newDigit;
            if ('0' <= c && c <= digitMax)
                newDigit = c - '0';
            else if ('a' <= c && c < lowerCaseBound)
                newDigit = c - 'a' + 10;
            else if ('A' <= c && c < upperCaseBound)
                newDigit = c - 'A' + 10;
            else
                break;
            sum = sum*radix + newDigit;
        }
        if (start == end) {
            return NaN;
        }
        if (sum >= 9007199254740992.0) {
            if (radix == 10) {
                /* If we're accumulating a decimal number and the number
                 * is >= 2^53, then the result from the repeated multiply-add
                 * above may be inaccurate.  Call Java to get the correct
                 * answer.
                 */
                try {
                    return Double.parseDouble(s.substring(start, end));
                } catch (NumberFormatException nfe) {
                    return NaN;
                }
            } else if (radix == 2 || radix == 4 || radix == 8 ||
                       radix == 16 || radix == 32)
            {
                /* The number may also be inaccurate for one of these bases.
                 * This happens if the addition in value*radix + digit causes
                 * a round-down to an even least significant mantissa bit
                 * when the first dropped bit is a one.  If any of the
                 * following digits in the number (which haven't been added
                 * in yet) are nonzero then the correct action would have
                 * been to round up instead of down.  An example of this
                 * occurs when reading the number 0x1000000000000081, which
                 * rounds to 0x1000000000000000 instead of 0x1000000000000100.
                 */
                int bitShiftInChar = 1;
                int digit = 0;

                final int SKIP_LEADING_ZEROS = 0;
                final int FIRST_EXACT_53_BITS = 1;
                final int AFTER_BIT_53         = 2;
                final int ZEROS_AFTER_54 = 3;
                final int MIXED_AFTER_54 = 4;

                int state = SKIP_LEADING_ZEROS;
                int exactBitsLimit = 53;
                double factor = 0.0;
                boolean bit53 = false;
                // bit54 is the 54th bit (the first dropped from the mantissa)
                boolean bit54 = false;

                for (;;) {
                    if (bitShiftInChar == 1) {
                        if (start == end)
                            break;
                        digit = s.charAt(start++);
                        if ('0' <= digit && digit <= '9')
                            digit -= '0';
                        else if ('a' <= digit && digit <= 'z')
                            digit -= 'a' - 10;
                        else
                            digit -= 'A' - 10;
                        bitShiftInChar = radix;
                    }
                    bitShiftInChar >>= 1;
                    boolean bit = (digit & bitShiftInChar) != 0;

                    switch (state) {
                      case SKIP_LEADING_ZEROS:
                          if (bit) {
                            --exactBitsLimit;
                            sum = 1.0;
                            state = FIRST_EXACT_53_BITS;
                        }
                        break;
                      case FIRST_EXACT_53_BITS:
                           sum *= 2.0;
                        if (bit)
                            sum += 1.0;
                        --exactBitsLimit;
                        if (exactBitsLimit == 0) {
                            bit53 = bit;
                            state = AFTER_BIT_53;
                        }
                        break;
                      case AFTER_BIT_53:
                        bit54 = bit;
                        factor = 2.0;
                        state = ZEROS_AFTER_54;
                        break;
                      case ZEROS_AFTER_54:
                        if (bit) {
                            state = MIXED_AFTER_54;
                        }
                        // fallthrough
                      case MIXED_AFTER_54:
                        factor *= 2;
                        break;
                    }
                }
                switch (state) {
                  case SKIP_LEADING_ZEROS:
                    sum = 0.0;
                    break;
                  case FIRST_EXACT_53_BITS:
                  case AFTER_BIT_53:
                    // do nothing
                    break;
                  case ZEROS_AFTER_54:
                    // x1.1 -> x1 + 1 (round up)
                    // x0.1 -> x0 (round down)
                    if (bit54 & bit53)
                        sum += 1.0;
                    sum *= factor;
                    break;
                  case MIXED_AFTER_54:
                    // x.100...1.. -> x + 1 (round up)
                    // x.0anything -> x (round down)
                    if (bit54)
                        sum += 1.0;
                    sum *= factor;
                    break;
                }
            }
            /* We don't worry about inaccurate numbers for any other base. */
        }
        return sum;
    }


    /**
     * ToNumber applied to the String type
     *
     * See ECMA 9.3.1
     */
    public static double toNumber(String s) {
        int len = s.length();
        int start = 0;
        char startChar;
        for (;;) {
            if (start == len) {
                // Empty or contains only whitespace
                return +0.0;
            }
            startChar = s.charAt(start);
            if (!ScriptRuntime.isStrWhiteSpaceChar(startChar))
                break;
            start++;
        }

        if (startChar == '0') {
            if (start + 2 < len) {
                int c1 = s.charAt(start + 1);
                if (c1 == 'x' || c1 == 'X') {
                    // A hexadecimal number
                    return stringToNumber(s, start + 2, 16);
                }
            }
        } else if (startChar == '+' || startChar == '-') {
            if (start + 3 < len && s.charAt(start + 1) == '0') {
                int c2 = s.charAt(start + 2);
                if (c2 == 'x' || c2 == 'X') {
                    // A hexadecimal number with sign
                    double val = stringToNumber(s, start + 3, 16);
                    return startChar == '-' ? -val : val;
                }
            }
        }

        int end = len - 1;
        char endChar;
        while (ScriptRuntime.isStrWhiteSpaceChar(endChar = s.charAt(end)))
            end--;
        if (endChar == 'y') {
            // check for "Infinity"
            if (startChar == '+' || startChar == '-')
                start++;
            if (start + 7 == end && s.regionMatches(start, "Infinity", 0, 8))
                return startChar == '-'
                    ? Double.NEGATIVE_INFINITY
                    : Double.POSITIVE_INFINITY;
            return NaN;
        }
        // A non-hexadecimal, non-infinity number:
        // just try a normal floating point conversion
        String sub = s.substring(start, end+1);
        // Quick test to check string contains only valid characters because
        // Double.parseDouble() can be slow and accept input we want to reject
        for (int i = sub.length() - 1; i >= 0; i--) {
            char c = sub.charAt(i);
            if (('0' <= c && c <= '9') || c == '.' ||
                    c == 'e' || c == 'E'  ||
                    c == '+' || c == '-')
                continue;
            return NaN;
        }
        try {
            return Double.parseDouble(sub);
        } catch (NumberFormatException ex) {
            return NaN;
        }
    }

    /**
     * Helper function for builtin objects that use the varargs form.
     * ECMA function formal arguments are undefined if not supplied;
     * this function pads the argument array out to the expected
     * length, if necessary.
     */
    public static Object[] padArguments(Object[] args, int count) {
        if (count < args.length)
            return args;

        int i;
        Object[] result = new Object[count];
        for (i = 0; i < args.length; i++) {
            result[i] = args[i];
        }

        for (; i < count; i++) {
            result[i] = Undefined.instance;
        }

        return result;
    }

    public static String escapeString(String s)
    {
        return escapeString(s, '"');
    }

    /**
     * For escaping strings printed by object and array literals; not quite
     * the same as 'escape.'
     */
    public static String escapeString(String s, char escapeQuote)
    {
        if (!(escapeQuote == '"' || escapeQuote == '\'')) Kit.codeBug();
        StringBuffer sb = null;

        for(int i = 0, L = s.length(); i != L; ++i) {
            int c = s.charAt(i);

            if (' ' <= c && c <= '~' && c != escapeQuote && c != '\\') {
                // an ordinary print character (like C isprint()) and not "
                // or \ .
                if (sb != null) {
                    sb.append((char)c);
                }
                continue;
            }
            if (sb == null) {
                sb = new StringBuffer(L + 3);
                sb.append(s);
                sb.setLength(i);
            }

            int escape = -1;
            switch (c) {
                case '\b':  escape = 'b';  break;
                case '\f':  escape = 'f';  break;
                case '\n':  escape = 'n';  break;
                case '\r':  escape = 'r';  break;
                case '\t':  escape = 't';  break;
                case 0xb:   escape = 'v';  break; // Java lacks \v.
                case ' ':   escape = ' ';  break;
                case '\\':  escape = '\\'; break;
            }
            if (escape >= 0) {
                // an \escaped sort of character
                sb.append('\\');
                sb.append((char)escape);
            } else if (c == escapeQuote) {
                sb.append('\\');
                sb.append(escapeQuote);
            } else {
                int hexSize;
                if (c < 256) {
                    // 2-digit hex
                    sb.append("\\x");
                    hexSize = 2;
                } else {
                    // Unicode.
                    sb.append("\\u");
                    hexSize = 4;
                }
                // append hexadecimal form of c left-padded with 0
                for (int shift = (hexSize - 1) * 4; shift >= 0; shift -= 4) {
                    int digit = 0xf & (c >> shift);
                    int hc = (digit < 10) ? '0' + digit : 'a' - 10 + digit;
                    sb.append((char)hc);
                }
            }
        }
        return (sb == null) ? s : sb.toString();
    }

    static boolean isValidIdentifierName(String s)
    {
        int L = s.length();
        if (L == 0)
            return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0)))
            return false;
        for (int i = 1; i != L; ++i) {
            if (!Character.isJavaIdentifierPart(s.charAt(i)))
                return false;
        }
        return !TokenStream.isKeyword(s);
    }

    public static CharSequence toCharSequence(Object val) {
        if (val instanceof NativeString) {
            return ((NativeString)val).toCharSequence();
        }
        return val instanceof CharSequence ? (CharSequence) val : toString(val);
    }

    /**
     * Convert the value to a string.
     *
     * See ECMA 9.8.
     */
    public static String toString(Object val) {
        for (;;) {
            if (val == null) {
                return "null";
            }
            if (val == Undefined.instance) {
                return "undefined";
            }
            if (val instanceof String) {
                return (String)val;
            }
            if (val instanceof CharSequence) {
                return val.toString();
            }
            if (val instanceof Number) {
                // XXX should we just teach NativeNumber.stringValue()
                // about Numbers?
                return numberToString(((Number)val).doubleValue(), 10);
            }
            if (val instanceof Scriptable) {
                val = ((Scriptable) val).getDefaultValue(StringClass);
                if (val instanceof Scriptable) {
                    throw errorWithClassName("msg.primitive.expected", val);
                }
                continue;
            }
            return val.toString();
        }
    }

    static String defaultObjectToString(Scriptable obj)
    {
        return "[object " + obj.getClassName() + ']';
    }

    public static String toString(Object[] args, int index)
    {
        return (index < args.length) ? toString(args[index]) : "undefined";
    }

    /**
     * Optimized version of toString(Object) for numbers.
     */
    public static String toString(double val) {
        return numberToString(val, 10);
    }

    public static String numberToString(double d, int base) {
        if (Double.isNaN(d))
            return "NaN";
        if (d == Double.POSITIVE_INFINITY)
            return "Infinity";
        if (d == Double.NEGATIVE_INFINITY)
            return "-Infinity";
        if (d == 0.0)
            return "0";

        if ((base < 2) || (base > 36)) {
            throw Context.reportRuntimeError1(
                "msg.bad.radix", Integer.toString(base));
        }

        if (base != 10) {
            return DToA.JS_dtobasestr(base, d);
        } else {
            // V8 FastDtoa can't convert all numbers, so try it first but
            // fall back to old DToA in case it fails
            String result = FastDtoa.numberToString(d);
            if (result != null) {
                return result;
            }
            StringBuilder buffer = new StringBuilder();
            DToA.JS_dtostr(buffer, DToA.DTOSTR_STANDARD, 0, d);
            return buffer.toString();
        }

    }

    static String uneval(Context cx, Scriptable scope, Object value)
    {
        if (value == null) {
            return "null";
        }
        if (value == Undefined.instance) {
            return "undefined";
        }
        if (value instanceof CharSequence) {
            String escaped = escapeString(value.toString());
            StringBuffer sb = new StringBuffer(escaped.length() + 2);
            sb.append('\"');
            sb.append(escaped);
            sb.append('\"');
            return sb.toString();
        }
        if (value instanceof Number) {
            double d = ((Number)value).doubleValue();
            if (d == 0 && 1 / d < 0) {
                return "-0";
            }
            return toString(d);
        }
        if (value instanceof Boolean) {
            return toString(value);
        }
        if (value instanceof Scriptable) {
            Scriptable obj = (Scriptable)value;
            // Wrapped Java objects won't have "toSource" and will report
            // errors for get()s of nonexistent name, so use has() first
            if (ScriptableObject.hasProperty(obj, "toSource")) {
                Object v = ScriptableObject.getProperty(obj, "toSource");
                if (v instanceof Function) {
                    Function f = (Function)v;
                    return toString(f.call(cx, scope, obj, emptyArgs));
                }
            }
            return toString(value);
        }
        warnAboutNonJSObject(value);
        return value.toString();
    }

    static String defaultObjectToSource(Context cx, Scriptable scope,
                                        Scriptable thisObj, Object[] args)
    {
        boolean toplevel, iterating;
        if (cx.iterating == null) {
            toplevel = true;
            iterating = false;
            cx.iterating = new ObjToIntMap(31);
        } else {
            toplevel = false;
            iterating = cx.iterating.has(thisObj);
        }

        StringBuffer result = new StringBuffer(128);
        if (toplevel) {
            result.append("(");
        }
        result.append('{');

        // Make sure cx.iterating is set to null when done
        // so we don't leak memory
        try {
            if (!iterating) {
                cx.iterating.intern(thisObj); // stop recursion.
                Object[] ids = thisObj.getIds();
                for (int i=0; i < ids.length; i++) {
                    Object id = ids[i];
                    Object value;
                    if (id instanceof Integer) {
                        int intId = ((Integer)id).intValue();
                        value = thisObj.get(intId, thisObj);
                        if (value == Scriptable.NOT_FOUND)
                            continue;   // a property has been removed
                        if (i > 0)
                            result.append(", ");
                        result.append(intId);
                    } else {
                        String strId = (String)id;
                        value = thisObj.get(strId, thisObj);
                        if (value == Scriptable.NOT_FOUND)
                            continue;   // a property has been removed
                        if (i > 0)
                            result.append(", ");
                        if (ScriptRuntime.isValidIdentifierName(strId)) {
                            result.append(strId);
                        } else {
                            result.append('\'');
                            result.append(
                                ScriptRuntime.escapeString(strId, '\''));
                            result.append('\'');
                        }
                    }
                    result.append(':');
                    result.append(ScriptRuntime.uneval(cx, scope, value));
                }
            }
        } finally {
            if (toplevel) {
                cx.iterating = null;
            }
        }

        result.append('}');
        if (toplevel) {
            result.append(')');
        }
        return result.toString();
    }

    public static Scriptable toObject(Scriptable scope, Object val)
    {
        if (val instanceof Scriptable) {
            return (Scriptable)val;
        }
        return toObject(Context.getContext(), scope, val);
    }

    /**
     * Warning: this doesn't allow to resolve primitive prototype properly when many top scopes are involved
     */
    public static Scriptable toObjectOrNull(Context cx, Object obj)
    {
        if (obj instanceof Scriptable) {
            return (Scriptable)obj;
        } else if (obj != null && obj != Undefined.instance) {
            return toObject(cx, getTopCallScope(cx), obj);
        }
        return null;
    }

    /**
     * @param scope the scope that should be used to resolve primitive prototype
     */
    public static Scriptable toObjectOrNull(Context cx, Object obj,
                                            final Scriptable scope)
    {
        if (obj instanceof Scriptable) {
            return (Scriptable)obj;
        } else if (obj != null && obj != Undefined.instance) {
            return toObject(cx, scope, obj);
        }
        return null;
    }

    /**
     * @deprecated Use {@link #toObject(Scriptable, Object)} instead.
     */
    public static Scriptable toObject(Scriptable scope, Object val,
                                      Class<?> staticClass)
    {
        if (val instanceof Scriptable) {
            return (Scriptable)val;
        }
        return toObject(Context.getContext(), scope, val);
    }

    /**
     * Convert the value to an object.
     *
     * See ECMA 9.9.
     */
    public static Scriptable toObject(Context cx, Scriptable scope, Object val)
    {
        if (val instanceof Scriptable) {
            return (Scriptable) val;
        }
        if (val instanceof CharSequence) {
            // FIXME we want to avoid toString() here, especially for concat()
            NativeString result = new NativeString((CharSequence)val);
            setBuiltinProtoAndParent(result, scope, TopLevel.Builtins.String);
            return result;
        }
        if (val instanceof Number) {
            NativeNumber result = new NativeNumber(((Number)val).doubleValue());
            setBuiltinProtoAndParent(result, scope, TopLevel.Builtins.Number);
            return result;
        }
        if (val instanceof Boolean) {
            NativeBoolean result = new NativeBoolean(((Boolean)val).booleanValue());
            setBuiltinProtoAndParent(result, scope, TopLevel.Builtins.Boolean);
            return result;
        }
        if (val == null) {
            throw typeError0("msg.null.to.object");
        }
        if (val == Undefined.instance) {
            throw typeError0("msg.undef.to.object");
        }

        // Extension: Wrap as a LiveConnect object.
        Object wrapped = cx.getWrapFactory().wrap(cx, scope, val, null);
        if (wrapped instanceof Scriptable)
            return (Scriptable) wrapped;
        throw errorWithClassName("msg.invalid.type", val);
    }

    /**
     * @deprecated Use {@link #toObject(Context, Scriptable, Object)} instead.
     */
    public static Scriptable toObject(Context cx, Scriptable scope, Object val,
                                      Class<?> staticClass)
    {
        return toObject(cx, scope, val);
    }

    /**
     * @deprecated The method is only present for compatibility.
     */
    public static Object call(Context cx, Object fun, Object thisArg,
                              Object[] args, Scriptable scope)
    {
        if (!(fun instanceof Function)) {
            throw notFunctionError(toString(fun));
        }
        Function function = (Function)fun;
        Scriptable thisObj = toObjectOrNull(cx, thisArg);
        if (thisObj == null) {
            throw undefCallError(thisObj, "function");
        }
        return function.call(cx, scope, thisObj, args);
    }

    public static Scriptable newObject(Context cx, Scriptable scope,
                                       String constructorName, Object[] args)
    {
        scope = ScriptableObject.getTopLevelScope(scope);
        Function ctor = getExistingCtor(cx, scope, constructorName);
        if (args == null) { args = ScriptRuntime.emptyArgs; }
        return ctor.construct(cx, scope, args);
    }

    public static Scriptable newBuiltinObject(Context cx, Scriptable scope,
                                              TopLevel.Builtins type,
                                              Object[] args)
    {
        scope = ScriptableObject.getTopLevelScope(scope);
        Function ctor = TopLevel.getBuiltinCtor(cx, scope, type);
        if (args == null) { args = ScriptRuntime.emptyArgs; }
        return ctor.construct(cx, scope, args);
    }

    /**
     *
     * See ECMA 9.4.
     */
    public static double toInteger(Object val) {
        return toInteger(toNumber(val));
    }

    // convenience method
    public static double toInteger(double d) {
        // if it's NaN
        if (Double.isNaN(d))
            return +0.0;

        if (d == 0.0 ||
            d == Double.POSITIVE_INFINITY ||
            d == Double.NEGATIVE_INFINITY)
            return d;

        if (d > 0.0)
            return Math.floor(d);
        else
            return Math.ceil(d);
    }

    public static double toInteger(Object[] args, int index) {
        return (index < args.length) ? toInteger(args[index]) : +0.0;
    }

    /**
     *
     * See ECMA 9.5.
     */
    public static int toInt32(Object val)
    {
        // short circuit for common integer values
        if (val instanceof Integer)
            return ((Integer)val).intValue();

        return toInt32(toNumber(val));
    }

    public static int toInt32(Object[] args, int index) {
        return (index < args.length) ? toInt32(args[index]) : 0;
    }

    public static int toInt32(double d) {
        int id = (int)d;
        if (id == d) {
            // This covers -0.0 as well
            return id;
        }

        if (Double.isNaN(d)
            || d == Double.POSITIVE_INFINITY
            || d == Double.NEGATIVE_INFINITY)
        {
            return 0;
        }

        d = (d >= 0) ? Math.floor(d) : Math.ceil(d);

        double two32 = 4294967296.0;
        d = Math.IEEEremainder(d, two32);
        // (double)(long)d == d should hold here

        long l = (long)d;
        // returning (int)d does not work as d can be outside int range
        // but the result must always be 32 lower bits of l
        return (int)l;
    }

    /**
     * See ECMA 9.6.
     * @return long value representing 32 bits unsigned integer
     */
    public static long toUint32(double d) {
        long l = (long)d;
        if (l == d) {
            // This covers -0.0 as well
            return l & 0xffffffffL;
        }

        if (Double.isNaN(d)
            || d == Double.POSITIVE_INFINITY
            || d == Double.NEGATIVE_INFINITY)
        {
            return 0;
        }

        d = (d >= 0) ? Math.floor(d) : Math.ceil(d);

        // 0x100000000 gives me a numeric overflow...
        double two32 = 4294967296.0;
        l = (long)Math.IEEEremainder(d, two32);

        return l & 0xffffffffL;
    }

    public static long toUint32(Object val) {
        return toUint32(toNumber(val));
    }

    /**
     *
     * See ECMA 9.7.
     */
    public static char toUint16(Object val) {
        double d = toNumber(val);

        int i = (int)d;
        if (i == d) {
            return (char)i;
        }

        if (Double.isNaN(d)
            || d == Double.POSITIVE_INFINITY
            || d == Double.NEGATIVE_INFINITY)
        {
            return 0;
        }

        d = (d >= 0) ? Math.floor(d) : Math.ceil(d);

        int int16 = 0x10000;
        i = (int)Math.IEEEremainder(d, int16);

        return (char)i;
    }

    // XXX: this is until setDefaultNamespace will learn how to store NS
    // properly and separates namespace form Scriptable.get etc.
    private static final String DEFAULT_NS_TAG = "__default_namespace__";

    public static Object setDefaultNamespace(Object namespace, Context cx)
    {
        Scriptable scope = cx.currentActivationCall;
        if (scope == null) {
            scope = getTopCallScope(cx);
        }

        XMLLib xmlLib = currentXMLLib(cx);
        Object ns = xmlLib.toDefaultXmlNamespace(cx, namespace);

        // XXX : this should be in separated namesapce from Scriptable.get/put
        if (!scope.has(DEFAULT_NS_TAG, scope)) {
            // XXX: this is racy of cause
            ScriptableObject.defineProperty(scope, DEFAULT_NS_TAG, ns,
                                            ScriptableObject.PERMANENT
                                            | ScriptableObject.DONTENUM);
        } else {
            scope.put(DEFAULT_NS_TAG, scope, ns);
        }

        return Undefined.instance;
    }

    public static Object searchDefaultNamespace(Context cx)
    {
        Scriptable scope = cx.currentActivationCall;
        if (scope == null) {
            scope = getTopCallScope(cx);
        }
        Object nsObject;
        for (;;) {
            Scriptable parent = scope.getParentScope();
            if (parent == null) {
                nsObject = ScriptableObject.getProperty(scope, DEFAULT_NS_TAG);
                if (nsObject == Scriptable.NOT_FOUND) {
                    return null;
                }
                break;
            }
            nsObject = scope.get(DEFAULT_NS_TAG, scope);
            if (nsObject != Scriptable.NOT_FOUND) {
                break;
            }
            scope = parent;
        }
        return nsObject;
    }

    public static Object getTopLevelProp(Scriptable scope, String id) {
        scope = ScriptableObject.getTopLevelScope(scope);
        return ScriptableObject.getProperty(scope, id);
    }

    static Function getExistingCtor(Context cx, Scriptable scope,
                                    String constructorName)
    {
        Object ctorVal = ScriptableObject.getProperty(scope, constructorName);
        if (ctorVal instanceof Function) {
            return (Function)ctorVal;
        }
        if (ctorVal == Scriptable.NOT_FOUND) {
            throw Context.reportRuntimeError1(
                "msg.ctor.not.found", constructorName);
        } else {
            throw Context.reportRuntimeError1(
                "msg.not.ctor", constructorName);
        }
    }

    /**
     * Return -1L if str is not an index, or the index value as lower 32
     * bits of the result. Note that the result needs to be cast to an int
     * in order to produce the actual index, which may be negative.
     */
    public static long indexFromString(String str)
    {
        // The length of the decimal string representation of
        //  Integer.MAX_VALUE, 2147483647
        final int MAX_VALUE_LENGTH = 10;

        int len = str.length();
        if (len > 0) {
            int i = 0;
            boolean negate = false;
            int c = str.charAt(0);
            if (c == '-') {
                if (len > 1) {
                    c = str.charAt(1);
                    if (c == '0') return -1L; // "-0" is not an index
                    i = 1;
                    negate = true;
                }
            }
            c -= '0';
            if (0 <= c && c <= 9
                && len <= (negate ? MAX_VALUE_LENGTH + 1 : MAX_VALUE_LENGTH))
            {
                // Use negative numbers to accumulate index to handle
                // Integer.MIN_VALUE that is greater by 1 in absolute value
                // then Integer.MAX_VALUE
                int index = -c;
                int oldIndex = 0;
                i++;
                if (index != 0) {
                    // Note that 00, 01, 000 etc. are not indexes
                    while (i != len && 0 <= (c = str.charAt(i) - '0') && c <= 9)
                    {
                        oldIndex = index;
                        index = 10 * index - c;
                        i++;
                    }
                }
                // Make sure all characters were consumed and that it couldn't
                // have overflowed.
                if (i == len &&
                    (oldIndex > (Integer.MIN_VALUE / 10) ||
                     (oldIndex == (Integer.MIN_VALUE / 10) &&
                      c <= (negate ? -(Integer.MIN_VALUE % 10)
                                   : (Integer.MAX_VALUE % 10)))))
                {
                    return 0xFFFFFFFFL & (negate ? index : -index);
                }
            }
        }
        return -1L;
    }

    /**
     * If str is a decimal presentation of Uint32 value, return it as long.
     * Othewise return -1L;
     */
    public static long testUint32String(String str)
    {
        // The length of the decimal string representation of
        //  UINT32_MAX_VALUE, 4294967296
        final int MAX_VALUE_LENGTH = 10;

        int len = str.length();
        if (1 <= len && len <= MAX_VALUE_LENGTH) {
            int c = str.charAt(0);
            c -= '0';
            if (c == 0) {
                // Note that 00,01 etc. are not valid Uint32 presentations
                return (len == 1) ? 0L : -1L;
            }
            if (1 <= c && c <= 9) {
                long v = c;
                for (int i = 1; i != len; ++i) {
                    c = str.charAt(i) - '0';
                    if (!(0 <= c && c <= 9)) {
                        return -1;
                    }
                    v = 10 * v + c;
                }
                // Check for overflow
                if ((v >>> 32) == 0) {
                    return v;
                }
            }
        }
        return -1;
    }

    /**
     * If s represents index, then return index value wrapped as Integer
     * and othewise return s.
     */
    static Object getIndexObject(String s)
    {
        long indexTest = indexFromString(s);
        if (indexTest >= 0) {
            return Integer.valueOf((int)indexTest);
        }
        return s;
    }

    /**
     * If d is exact int value, return its value wrapped as Integer
     * and othewise return d converted to String.
     */
    static Object getIndexObject(double d)
    {
        int i = (int)d;
        if (i == d) {
            return Integer.valueOf(i);
        }
        return toString(d);
    }

    /**
     * If toString(id) is a decimal presentation of int32 value, then id
     * is index. In this case return null and make the index available
     * as ScriptRuntime.lastIndexResult(cx). Otherwise return toString(id).
     */
    static String toStringIdOrIndex(Context cx, Object id)
    {
        if (id instanceof Number) {
            double d = ((Number)id).doubleValue();
            int index = (int)d;
            if (index == d) {
                storeIndexResult(cx, index);
                return null;
            }
            return toString(id);
        } else {
            String s;
            if (id instanceof String) {
                s = (String)id;
            } else {
                s = toString(id);
            }
            long indexTest = indexFromString(s);
            if (indexTest >= 0) {
                storeIndexResult(cx, (int)indexTest);
                return null;
            }
            return s;
        }
    }

    /**
     * Call obj.[[Get]](id)
     */
    public static Object getObjectElem(Object obj, Object elem, Context cx)
    {
    	return getObjectElem(obj, elem, cx, getTopCallScope(cx));
    }

    /**
     * Call obj.[[Get]](id)
     */
    public static Object getObjectElem(Object obj, Object elem, Context cx, final Scriptable scope)
    {
        Scriptable sobj = toObjectOrNull(cx, obj, scope);
        if (sobj == null) {
            throw undefReadError(obj, elem);
        }
        return getObjectElem(sobj, elem, cx);
    }

    public static Object getObjectElem(Scriptable obj, Object elem,
                                       Context cx)
    {

        Object result;

        if (obj instanceof XMLObject) {
            result = ((XMLObject)obj).get(cx, elem);
        } else {
            String s = toStringIdOrIndex(cx, elem);
            if (s == null) {
                int index = lastIndexResult(cx);
                result = ScriptableObject.getProperty(obj, index);
            } else {
                result = ScriptableObject.getProperty(obj, s);
            }
        }

        if (result == Scriptable.NOT_FOUND) {
            result = Undefined.instance;
        }

        return result;
    }

    /**
     * Version of getObjectElem when elem is a valid JS identifier name.
     */
    public static Object getObjectProp(Object obj, String property,
                                       Context cx)
    {
        Scriptable sobj = toObjectOrNull(cx, obj);
        if (sobj == null) {
            throw undefReadError(obj, property);
        }
        return getObjectProp(sobj, property, cx);
    }

    /**
     * @param scope the scope that should be used to resolve primitive prototype
     */
    public static Object getObjectProp(Object obj, String property,
                                       Context cx, final Scriptable scope)
    {
        Scriptable sobj = toObjectOrNull(cx, obj, scope);
        if (sobj == null) {
            throw undefReadError(obj, property);
        }
        return getObjectProp(sobj, property, cx);
    }

    public static Object getObjectProp(Scriptable obj, String property,
                                       Context cx)
    {

        Object result = ScriptableObject.getProperty(obj, property);
        if (result == Scriptable.NOT_FOUND) {
            if (cx.hasFeature(Context.FEATURE_STRICT_MODE)) {
                Context.reportWarning(ScriptRuntime.getMessage1(
                    "msg.ref.undefined.prop", property));
            }
            result = Undefined.instance;
        }

        return result;
    }

    public static Object getObjectPropNoWarn(Object obj, String property,
                                             Context cx)
    {
        Scriptable sobj = toObjectOrNull(cx, obj);
        if (sobj == null) {
            throw undefReadError(obj, property);
        }
        Object result = ScriptableObject.getProperty(sobj, property);
        if (result == Scriptable.NOT_FOUND) {
          return Undefined.instance;
        }
        return result;
    }

    /*
     * A cheaper and less general version of the above for well-known argument
     * types.
     */
    public static Object getObjectIndex(Object obj, double dblIndex,
                                        Context cx)
    {
        Scriptable sobj = toObjectOrNull(cx, obj);
        if (sobj == null) {
            throw undefReadError(obj, toString(dblIndex));
        }

        int index = (int)dblIndex;
        if (index == dblIndex) {
            return getObjectIndex(sobj, index, cx);
        } else {
            String s = toString(dblIndex);
            return getObjectProp(sobj, s, cx);
        }
    }

    public static Object getObjectIndex(Scriptable obj, int index,
                                        Context cx)
    {

        Object result = ScriptableObject.getProperty(obj, index);
        if (result == Scriptable.NOT_FOUND) {
            result = Undefined.instance;
        }

        return result;
    }

    /*
     * Call obj.[[Put]](id, value)
     */
    public static Object setObjectElem(Object obj, Object elem, Object value,
                                       Context cx)
    {
        Scriptable sobj = toObjectOrNull(cx, obj);
        if (sobj == null) {
            throw undefWriteError(obj, elem, value);
        }
        return setObjectElem(sobj, elem, value, cx);
    }

    public static Object setObjectElem(Scriptable obj, Object elem,
                                       Object value, Context cx)
    {
        if (obj instanceof XMLObject) {
            ((XMLObject)obj).put(cx, elem, value);
        } else {
            String s = toStringIdOrIndex(cx, elem);
            if (s == null) {
                int index = lastIndexResult(cx);
                ScriptableObject.putProperty(obj, index, value);
            } else {
                ScriptableObject.putProperty(obj, s, value);
            }
        }

        return value;
    }

    /**
     * Version of setObjectElem when elem is a valid JS identifier name.
     */
    public static Object setObjectProp(Object obj, String property,
                                       Object value, Context cx)
    {
        Scriptable sobj = toObjectOrNull(cx, obj);
        if (sobj == null) {
            throw undefWriteError(obj, property, value);
        }
        return setObjectProp(sobj, property, value, cx);
    }

    public static Object setObjectProp(Scriptable obj, String property,
                                       Object value, Context cx)
    {
        ScriptableObject.putProperty(obj, property, value);
        return value;
    }

    /*
     * A cheaper and less general version of the above for well-known argument
     * types.
     */
    public static Object setObjectIndex(Object obj, double dblIndex,
                                        Object value, Context cx)
    {
        Scriptable sobj = toObjectOrNull(cx, obj);
        if (sobj == null) {
            throw undefWriteError(obj, String.valueOf(dblIndex), value);
        }

        int index = (int)dblIndex;
        if (index == dblIndex) {
            return setObjectIndex(sobj, index, value, cx);
        } else {
            String s = toString(dblIndex);
            return setObjectProp(sobj, s, value, cx);
        }
    }

    public static Object setObjectIndex(Scriptable obj, int index, Object value,
                                        Context cx)
    {
        ScriptableObject.putProperty(obj, index, value);
        return value;
    }

    public static boolean deleteObjectElem(Scriptable target, Object elem,
                                           Context cx)
    {
        String s = toStringIdOrIndex(cx, elem);
        if (s == null) {
            int index = lastIndexResult(cx);
            target.delete(index);
            return !target.has(index, target);
        } else {
            target.delete(s);
            return !target.has(s, target);
        }
    }

    public static boolean hasObjectElem(Scriptable target, Object elem,
                                        Context cx)
    {
        boolean result;

        String s = toStringIdOrIndex(cx, elem);
        if (s == null) {
            int index = lastIndexResult(cx);
            result = ScriptableObject.hasProperty(target, index);
        } else {
            result = ScriptableObject.hasProperty(target, s);
        }

        return result;
    }

    public static Object refGet(Ref ref, Context cx)
    {
        return ref.get(cx);
    }

    public static Object refSet(Ref ref, Object value, Context cx)
    {
        return ref.set(cx, value);
    }

    public static Object refDel(Ref ref, Context cx)
    {
        return wrapBoolean(ref.delete(cx));
    }

    static boolean isSpecialProperty(String s)
    {
        return s.equals("__proto__") || s.equals("__parent__");
    }

    public static Ref specialRef(Object obj, String specialProperty,
                                 Context cx)
    {
        return SpecialRef.createSpecial(cx, obj, specialProperty);
    }

    /**
     * @deprecated
     */
    public static Object delete(Object obj, Object id, Context cx)
    {
        return delete(obj, id, cx, false);
    }

    /**
     * The delete operator
     *
     * See ECMA 11.4.1
     *
     * In ECMA 0.19, the description of the delete operator (11.4.1)
     * assumes that the [[Delete]] method returns a value. However,
     * the definition of the [[Delete]] operator (8.6.2.5) does not
     * define a return value. Here we assume that the [[Delete]]
     * method doesn't return a value.
     */
    public static Object delete(Object obj, Object id, Context cx, boolean isName)
    {
        Scriptable sobj = toObjectOrNull(cx, obj);
        if (sobj == null) {
            if (isName) {
                return Boolean.TRUE;
            }
            throw undefDeleteError(obj, id);
        }
        boolean result = deleteObjectElem(sobj, id, cx);
        return wrapBoolean(result);
    }

    /**
     * Looks up a name in the scope chain and returns its value.
     */
    public static Object name(Context cx, Scriptable scope, String name)
    {
        Scriptable parent = scope.getParentScope();
        if (parent == null) {
            Object result = topScopeName(cx, scope, name);
            if (result == Scriptable.NOT_FOUND) {
                throw notFoundError(scope, name);
            }
            return result;
        }

        return nameOrFunction(cx, scope, parent, name, false);
    }

    private static Object nameOrFunction(Context cx, Scriptable scope,
                                         Scriptable parentScope, String name,
                                         boolean asFunctionCall)
    {
        Object result;
        Scriptable thisObj = scope; // It is used only if asFunctionCall==true.

        XMLObject firstXMLObject = null;
        for (;;) {
            if (scope instanceof NativeWith) {
                Scriptable withObj = scope.getPrototype();
                if (withObj instanceof XMLObject) {
                    XMLObject xmlObj = (XMLObject)withObj;
                    if (xmlObj.has(name, xmlObj)) {
                        // function this should be the target object of with
                        thisObj = xmlObj;
                        result = xmlObj.get(name, xmlObj);
                        break;
                    }
                    if (firstXMLObject == null) {
                        firstXMLObject = xmlObj;
                    }
                } else {
                    result = ScriptableObject.getProperty(withObj, name);
                    if (result != Scriptable.NOT_FOUND) {
                        // function this should be the target object of with
                        thisObj = withObj;
                        break;
                    }
                }
            } else if (scope instanceof NativeCall) {
                // NativeCall does not prototype chain and Scriptable.get
                // can be called directly.
                result = scope.get(name, scope);
                if (result != Scriptable.NOT_FOUND) {
                    if (asFunctionCall) {
                        // ECMA 262 requires that this for nested funtions
                        // should be top scope
                        thisObj = ScriptableObject.
                                      getTopLevelScope(parentScope);
                    }
                    break;
                }
            } else {
                // Can happen if Rhino embedding decided that nested
                // scopes are useful for what ever reasons.
                result = ScriptableObject.getProperty(scope, name);
                if (result != Scriptable.NOT_FOUND) {
                    thisObj = scope;
                    break;
                }
            }
            scope = parentScope;
            parentScope = parentScope.getParentScope();
            if (parentScope == null) {
                result = topScopeName(cx, scope, name);
                if (result == Scriptable.NOT_FOUND) {
                    if (firstXMLObject == null || asFunctionCall) {
                        throw notFoundError(scope, name);
                    }
                    // The name was not found, but we did find an XML
                    // object in the scope chain and we are looking for name,
                    // not function. The result should be an empty XMLList
                    // in name context.
                    result = firstXMLObject.get(name, firstXMLObject);
                }
                // For top scope thisObj for functions is always scope itself.
                thisObj = scope;
                break;
            }
        }

        if (asFunctionCall) {
            if (!(result instanceof Callable)) {
                throw notFunctionError(result, name);
            }
            storeScriptable(cx, thisObj);
        }

        return result;
    }

    private static Object topScopeName(Context cx, Scriptable scope,
                                       String name)
    {
        if (cx.useDynamicScope) {
            scope = checkDynamicScope(cx.topCallScope, scope);
        }
        return ScriptableObject.getProperty(scope, name);
    }


    /**
     * Returns the object in the scope chain that has a given property.
     *
     * The order of evaluation of an assignment expression involves
     * evaluating the lhs to a reference, evaluating the rhs, and then
     * modifying the reference with the rhs value. This method is used
     * to 'bind' the given name to an object containing that property
     * so that the side effects of evaluating the rhs do not affect
     * which property is modified.
     * Typically used in conjunction with setName.
     *
     * See ECMA 10.1.4
     */
    public static Scriptable bind(Context cx, Scriptable scope, String id)
    {
        Scriptable firstXMLObject = null;
        Scriptable parent = scope.getParentScope();
        childScopesChecks: if (parent != null) {
            // Check for possibly nested "with" scopes first
            while (scope instanceof NativeWith) {
                Scriptable withObj = scope.getPrototype();
                if (withObj instanceof XMLObject) {
                    XMLObject xmlObject = (XMLObject)withObj;
                    if (xmlObject.has(cx, id)) {
                        return xmlObject;
                    }
                    if (firstXMLObject == null) {
                        firstXMLObject = xmlObject;
                    }
                } else {
                    if (ScriptableObject.hasProperty(withObj, id)) {
                        return withObj;
                    }
                }
                scope = parent;
                parent = parent.getParentScope();
                if (parent == null) {
                    break childScopesChecks;
                }
            }
            for (;;) {
                if (ScriptableObject.hasProperty(scope, id)) {
                    return scope;
                }
                scope = parent;
                parent = parent.getParentScope();
                if (parent == null) {
                    break childScopesChecks;
                }
            }
        }
        // scope here is top scope
        if (cx.useDynamicScope) {
            scope = checkDynamicScope(cx.topCallScope, scope);
        }
        if (ScriptableObject.hasProperty(scope, id)) {
            return scope;
        }
        // Nothing was found, but since XML objects always bind
        // return one if found
        return firstXMLObject;
    }

    public static Object setName(Scriptable bound, Object value,
                                 Context cx, Scriptable scope, String id)
    {
        if (bound != null) {
            // TODO: we used to special-case XMLObject here, but putProperty
            // seems to work for E4X and it's better to optimize  the common case
            ScriptableObject.putProperty(bound, id, value);
        } else {
            // "newname = 7;", where 'newname' has not yet
            // been defined, creates a new property in the
            // top scope unless strict mode is specified.
            if (cx.hasFeature(Context.FEATURE_STRICT_MODE) ||
                cx.hasFeature(Context.FEATURE_STRICT_VARS))
            {
                Context.reportWarning(
                    ScriptRuntime.getMessage1("msg.assn.create.strict", id));
            }
            // Find the top scope by walking up the scope chain.
            bound = ScriptableObject.getTopLevelScope(scope);
            if (cx.useDynamicScope) {
                bound = checkDynamicScope(cx.topCallScope, bound);
            }
            bound.put(id, bound, value);
        }
        return value;
    }

    public static Object strictSetName(Scriptable bound, Object value,
            Context cx, Scriptable scope, String id) {
        if (bound != null) {
            // TODO: The LeftHandSide also may not be a reference to a
            // data property with the attribute value {[[Writable]]:false},
            // to an accessor property with the attribute value
            // {[[Put]]:undefined}, nor to a non-existent property of an
            // object whose [[Extensible]] internal property has the value
            // false. In these cases a TypeError exception is thrown (11.13.1).
            // TODO: we used to special-case XMLObject here, but putProperty
            // seems to work for E4X and we should optimize  the common case
            ScriptableObject.putProperty(bound, id, value);
            return value;
        } else {
            // See ES5 8.7.2
            String msg = "Assignment to undefined \"" + id + "\" in strict mode";
            throw constructError("ReferenceError", msg);
        }
    }

    public static Object setConst(Scriptable bound, Object value,
                                 Context cx, String id)
    {
        if (bound instanceof XMLObject) {
            bound.put(id, bound, value);
        } else {
            ScriptableObject.putConstProperty(bound, id, value);
        }
        return value;
    }

    /**
     * This is the enumeration needed by the for..in statement.
     *
     * See ECMA 12.6.3.
     *
     * IdEnumeration maintains a ObjToIntMap to make sure a given
     * id is enumerated only once across multiple objects in a
     * prototype chain.
     *
     * XXX - ECMA delete doesn't hide properties in the prototype,
     * but js/ref does. This means that the js/ref for..in can
     * avoid maintaining a hash table and instead perform lookups
     * to see if a given property has already been enumerated.
     *
     */
    private static class IdEnumeration implements Serializable
    {
        private static final long serialVersionUID = 1L;
        Scriptable obj;
        Object[] ids;
        int index;
        ObjToIntMap used;
        Object currentId;
        int enumType; /* one of ENUM_INIT_KEYS, ENUM_INIT_VALUES,
                         ENUM_INIT_ARRAY */

        // if true, integer ids will be returned as numbers rather than strings
        boolean enumNumbers;

        Scriptable iterator;
    }

    public static Scriptable toIterator(Context cx, Scriptable scope,
                                        Scriptable obj, boolean keyOnly)
    {
        if (ScriptableObject.hasProperty(obj,
            NativeIterator.ITERATOR_PROPERTY_NAME))
        {
            Object v = ScriptableObject.getProperty(obj,
                NativeIterator.ITERATOR_PROPERTY_NAME);
            if (!(v instanceof Callable)) {
               throw typeError0("msg.invalid.iterator");
            }
            Callable f = (Callable) v;
            Object[] args = new Object[] { keyOnly ? Boolean.TRUE
                                                   : Boolean.FALSE };
            v = f.call(cx, scope, obj, args);
            if (!(v instanceof Scriptable)) {
                throw typeError0("msg.iterator.primitive");
            }
            return (Scriptable) v;
        }
        return null;
    }

    // for backwards compatibility with generated class files
    public static Object enumInit(Object value, Context cx, boolean enumValues)
    {
        return enumInit(value, cx, enumValues ? ENUMERATE_VALUES
                                              : ENUMERATE_KEYS);
    }

    public static final int ENUMERATE_KEYS = 0;
    public static final int ENUMERATE_VALUES = 1;
    public static final int ENUMERATE_ARRAY = 2;
    public static final int ENUMERATE_KEYS_NO_ITERATOR = 3;
    public static final int ENUMERATE_VALUES_NO_ITERATOR = 4;
    public static final int ENUMERATE_ARRAY_NO_ITERATOR = 5;

    public static Object enumInit(Object value, Context cx, int enumType)
    {
        IdEnumeration x = new IdEnumeration();
        x.obj = toObjectOrNull(cx, value);
        if (x.obj == null) {
            // null or undefined do not cause errors but rather lead to empty
            // "for in" loop
            return x;
        }
        x.enumType = enumType;
        x.iterator = null;
        if (enumType != ENUMERATE_KEYS_NO_ITERATOR &&
            enumType != ENUMERATE_VALUES_NO_ITERATOR &&
            enumType != ENUMERATE_ARRAY_NO_ITERATOR)
        {
            x.iterator = toIterator(cx, x.obj.getParentScope(), x.obj,
                                    enumType == ScriptRuntime.ENUMERATE_KEYS);
        }
        if (x.iterator == null) {
            // enumInit should read all initial ids before returning
            // or "for (a.i in a)" would wrongly enumerate i in a as well
            enumChangeObject(x);
        }

        return x;
    }

    public static void setEnumNumbers(Object enumObj, boolean enumNumbers) {
        ((IdEnumeration)enumObj).enumNumbers = enumNumbers;
    }

    public static Boolean enumNext(Object enumObj)
    {
        IdEnumeration x = (IdEnumeration)enumObj;
        if (x.iterator != null) {
            Object v = ScriptableObject.getProperty(x.iterator, "next");
            if (!(v instanceof Callable))
                return Boolean.FALSE;
            Callable f = (Callable) v;
            Context cx = Context.getContext();
            try {
                x.currentId = f.call(cx, x.iterator.getParentScope(),
                                     x.iterator, emptyArgs);
                return Boolean.TRUE;
            } catch (JavaScriptException e) {
                if (e.getValue() instanceof NativeIterator.StopIteration) {
                  return Boolean.FALSE;
                }
                throw e;
            }
        }
        for (;;) {
            if (x.obj == null) {
                return Boolean.FALSE;
            }
            if (x.index == x.ids.length) {
                x.obj = x.obj.getPrototype();
                enumChangeObject(x);
                continue;
            }
            Object id = x.ids[x.index++];
            if (x.used != null && x.used.has(id)) {
                continue;
            }
            if (id instanceof String) {
                String strId = (String)id;
                if (!x.obj.has(strId, x.obj))
                    continue;   // must have been deleted
                x.currentId = strId;
            } else {
                int intId = ((Number)id).intValue();
                if (!x.obj.has(intId, x.obj))
                    continue;   // must have been deleted
                x.currentId = x.enumNumbers ? (Object) (Integer.valueOf(intId))
                                            : String.valueOf(intId);
            }
            return Boolean.TRUE;
        }
    }

    public static Object enumId(Object enumObj, Context cx)
    {
        IdEnumeration x = (IdEnumeration)enumObj;
        if (x.iterator != null) {
            return x.currentId;
        }
        switch (x.enumType) {
          case ENUMERATE_KEYS:
          case ENUMERATE_KEYS_NO_ITERATOR:
            return x.currentId;
          case ENUMERATE_VALUES:
          case ENUMERATE_VALUES_NO_ITERATOR:
            return enumValue(enumObj, cx);
          case ENUMERATE_ARRAY:
          case ENUMERATE_ARRAY_NO_ITERATOR:
            Object[] elements = { x.currentId, enumValue(enumObj, cx) };
            return cx.newArray(ScriptableObject.getTopLevelScope(x.obj), elements);
          default:
            throw Kit.codeBug();
        }
    }

    public static Object enumValue(Object enumObj, Context cx) {
        IdEnumeration x = (IdEnumeration)enumObj;

        Object result;

        String s = toStringIdOrIndex(cx, x.currentId);
        if (s == null) {
            int index = lastIndexResult(cx);
            result = x.obj.get(index, x.obj);
        } else {
            result = x.obj.get(s, x.obj);
        }

        return result;
    }

    private static void enumChangeObject(IdEnumeration x)
    {
        Object[] ids = null;
        while (x.obj != null) {
            ids = x.obj.getIds();
            if (ids.length != 0) {
                break;
            }
            x.obj = x.obj.getPrototype();
        }
        if (x.obj != null && x.ids != null) {
            Object[] previous = x.ids;
            int L = previous.length;
            if (x.used == null) {
                x.used = new ObjToIntMap(L);
            }
            for (int i = 0; i != L; ++i) {
                x.used.intern(previous[i]);
            }
        }
        x.ids = ids;
        x.index = 0;
    }

    /**
     * Prepare for calling name(...): return function corresponding to
     * name and make current top scope available
     * as ScriptRuntime.lastStoredScriptable() for consumption as thisObj.
     * The caller must call ScriptRuntime.lastStoredScriptable() immediately
     * after calling this method.
     */
    public static Callable getNameFunctionAndThis(String name,
                                                  Context cx,
                                                  Scriptable scope)
    {
        Scriptable parent = scope.getParentScope();
        if (parent == null) {
            Object result = topScopeName(cx, scope, name);
            if (!(result instanceof Callable)) {
                if (result == Scriptable.NOT_FOUND) {
                    throw notFoundError(scope, name);
                } else {
                    throw notFunctionError(result, name);
                }
            }
            // Top scope is not NativeWith or NativeCall => thisObj == scope
            Scriptable thisObj = scope;
            storeScriptable(cx, thisObj);
            return (Callable)result;
        }

        // name will call storeScriptable(cx, thisObj);
        return (Callable)nameOrFunction(cx, scope, parent, name, true);
    }

    /**
     * Prepare for calling obj[id](...): return function corresponding to
     * obj[id] and make obj properly converted to Scriptable available
     * as ScriptRuntime.lastStoredScriptable() for consumption as thisObj.
     * The caller must call ScriptRuntime.lastStoredScriptable() immediately
     * after calling this method.
     */
    public static Callable getElemFunctionAndThis(Object obj,
                                                  Object elem,
                                                  Context cx)
    {
        String str = toStringIdOrIndex(cx, elem);
        if (str != null) {
            return getPropFunctionAndThis(obj, str, cx);
        }
        int index = lastIndexResult(cx);

        Scriptable thisObj = toObjectOrNull(cx, obj);
        if (thisObj == null) {
            throw undefCallError(obj, String.valueOf(index));
        }

        Object value = ScriptableObject.getProperty(thisObj, index);
        if (!(value instanceof Callable)) {
            throw notFunctionError(value, elem);
        }

        storeScriptable(cx, thisObj);
        return (Callable)value;
    }

    /**
     * Prepare for calling obj.property(...): return function corresponding to
     * obj.property and make obj properly converted to Scriptable available
     * as ScriptRuntime.lastStoredScriptable() for consumption as thisObj.
     * The caller must call ScriptRuntime.lastStoredScriptable() immediately
     * after calling this method.
     * Warning: this doesn't allow to resolve primitive prototype properly when
     * many top scopes are involved.
     */
    public static Callable getPropFunctionAndThis(Object obj,
                                                  String property,
                                                  Context cx)
    {
        Scriptable thisObj = toObjectOrNull(cx, obj);
        return getPropFunctionAndThisHelper(obj, property, cx, thisObj);
    }

    /**
     * Prepare for calling obj.property(...): return function corresponding to
     * obj.property and make obj properly converted to Scriptable available
     * as ScriptRuntime.lastStoredScriptable() for consumption as thisObj.
     * The caller must call ScriptRuntime.lastStoredScriptable() immediately
     * after calling this method.
     */
    public static Callable getPropFunctionAndThis(Object obj,
                                                  String property,
                                                  Context cx, final Scriptable scope)
    {
        Scriptable thisObj = toObjectOrNull(cx, obj, scope);
        return getPropFunctionAndThisHelper(obj, property, cx, thisObj);
    }

    private static Callable getPropFunctionAndThisHelper(Object obj,
          String property, Context cx, Scriptable thisObj)
    {
        if (thisObj == null) {
            throw undefCallError(obj, property);
        }

        Object value = ScriptableObject.getProperty(thisObj, property);
        if (!(value instanceof Callable)) {
            Object noSuchMethod = ScriptableObject.getProperty(thisObj, "__noSuchMethod__");
            if (noSuchMethod instanceof Callable)
                value = new NoSuchMethodShim((Callable)noSuchMethod, property);
        }

        if (!(value instanceof Callable)) {
            throw notFunctionError(thisObj, value, property);
        }

        storeScriptable(cx, thisObj);
        return (Callable)value;
    }

    /**
     * Prepare for calling <expression>(...): return function corresponding to
     * <expression> and make parent scope of the function available
     * as ScriptRuntime.lastStoredScriptable() for consumption as thisObj.
     * The caller must call ScriptRuntime.lastStoredScriptable() immediately
     * after calling this method.
     */
    public static Callable getValueFunctionAndThis(Object value, Context cx)
    {
        if (!(value instanceof Callable)) {
            throw notFunctionError(value);
        }

        Callable f = (Callable)value;
        Scriptable thisObj = null;
        if (f instanceof Scriptable) {
            thisObj = ((Scriptable)f).getParentScope();
        }
        if (thisObj == null) {
            if (cx.topCallScope == null) throw new IllegalStateException();
            thisObj = cx.topCallScope;
        }
        if (thisObj.getParentScope() != null) {
            if (thisObj instanceof NativeWith) {
                // functions defined inside with should have with target
                // as their thisObj
            } else if (thisObj instanceof NativeCall) {
                // nested functions should have top scope as their thisObj
                thisObj = ScriptableObject.getTopLevelScope(thisObj);
            }
        }
        storeScriptable(cx, thisObj);
        return f;
    }

    /**
     * Perform function call in reference context. Should always
     * return value that can be passed to
     * {@link #refGet(Ref, Context)} or {@link #refSet(Ref, Object, Context)}
     * arbitrary number of times.
     * The args array reference should not be stored in any object that is
     * can be GC-reachable after this method returns. If this is necessary,
     * store args.clone(), not args array itself.
     */
    public static Ref callRef(Callable function, Scriptable thisObj,
                              Object[] args, Context cx)
    {
        if (function instanceof RefCallable) {
            RefCallable rfunction = (RefCallable)function;
            Ref ref = rfunction.refCall(cx, thisObj, args);
            if (ref == null) {
                throw new IllegalStateException(rfunction.getClass().getName()+".refCall() returned null");
            }
            return ref;
        }
        // No runtime support for now
        String msg = getMessage1("msg.no.ref.from.function",
                                 toString(function));
        throw constructError("ReferenceError", msg);
    }

    /**
     * Operator new.
     *
     * See ECMA 11.2.2
     */
    public static Scriptable newObject(Object fun, Context cx,
                                       Scriptable scope, Object[] args)
    {
        if (!(fun instanceof Function)) {
            throw notFunctionError(fun);
        }
        Function function = (Function)fun;
        return function.construct(cx, scope, args);
    }

    public static Object callSpecial(Context cx, Callable fun,
                                     Scriptable thisObj,
                                     Object[] args, Scriptable scope,
                                     Scriptable callerThis, int callType,
                                     String filename, int lineNumber)
    {
        if (callType == Node.SPECIALCALL_EVAL) {
            if (thisObj.getParentScope() == null && NativeGlobal.isEvalFunction(fun)) {
                return evalSpecial(cx, scope, callerThis, args,
                                   filename, lineNumber);
            }
        } else if (callType == Node.SPECIALCALL_WITH) {
            if (NativeWith.isWithFunction(fun)) {
                throw Context.reportRuntimeError1("msg.only.from.new",
                                                  "With");
            }
        } else {
            throw Kit.codeBug();
        }

        return fun.call(cx, scope, thisObj, args);
    }

    public static Object newSpecial(Context cx, Object fun,
                                    Object[] args, Scriptable scope,
                                    int callType)
    {
        if (callType == Node.SPECIALCALL_EVAL) {
            if (NativeGlobal.isEvalFunction(fun)) {
                throw typeError1("msg.not.ctor", "eval");
            }
        } else if (callType == Node.SPECIALCALL_WITH) {
            if (NativeWith.isWithFunction(fun)) {
                return NativeWith.newWithSpecial(cx, scope, args);
            }
        } else {
            throw Kit.codeBug();
        }

        return newObject(fun, cx, scope, args);
    }

    /**
     * Function.prototype.apply and Function.prototype.call
     *
     * See Ecma 15.3.4.[34]
     */
    public static Object applyOrCall(boolean isApply,
                                     Context cx, Scriptable scope,
                                     Scriptable thisObj, Object[] args)
    {
        int L = args.length;
        Callable function = getCallable(thisObj);

        Scriptable callThis = null;
        if (L != 0) {
            callThis = toObjectOrNull(cx, args[0]);
        }
        if (callThis == null) {
            // This covers the case of args[0] == (null|undefined) as well.
            callThis = getTopCallScope(cx);
        }

        Object[] callArgs;
        if (isApply) {
            // Follow Ecma 15.3.4.3
            callArgs = L <= 1 ? ScriptRuntime.emptyArgs :
                getApplyArguments(cx, args[1]);
        } else {
            // Follow Ecma 15.3.4.4
            if (L <= 1) {
                callArgs = ScriptRuntime.emptyArgs;
            } else {
                callArgs = new Object[L - 1];
                System.arraycopy(args, 1, callArgs, 0, L - 1);
            }
        }

        return function.call(cx, scope, callThis, callArgs);
    }

    static Object[] getApplyArguments(Context cx, Object arg1)
    {
        if (arg1 == null || arg1 == Undefined.instance) {
            return ScriptRuntime.emptyArgs;
        } else if (arg1 instanceof NativeArray || arg1 instanceof Arguments) {
            return cx.getElements((Scriptable) arg1);
        } else {
            throw ScriptRuntime.typeError0("msg.arg.isnt.array");
        }
    }

    static Callable getCallable(Scriptable thisObj)
    {
        Callable function;
        if (thisObj instanceof Callable) {
            function = (Callable)thisObj;
        } else {
            Object value = thisObj.getDefaultValue(ScriptRuntime.FunctionClass);
            if (!(value instanceof Callable)) {
                throw ScriptRuntime.notFunctionError(value, thisObj);
            }
            function = (Callable)value;
        }
        return function;
    }

    /**
     * The eval function property of the global object.
     *
     * See ECMA 15.1.2.1
     */
    public static Object evalSpecial(Context cx, Scriptable scope,
                                     Object thisArg, Object[] args,
                                     String filename, int lineNumber)
    {
        if (args.length < 1)
            return Undefined.instance;
        Object x = args[0];
        if (!(x instanceof CharSequence)) {
            if (cx.hasFeature(Context.FEATURE_STRICT_MODE) ||
                cx.hasFeature(Context.FEATURE_STRICT_EVAL))
            {
                throw Context.reportRuntimeError0("msg.eval.nonstring.strict");
            }
            String message = ScriptRuntime.getMessage0("msg.eval.nonstring");
            Context.reportWarning(message);
            return x;
        }
        if (filename == null) {
            int[] linep = new int[1];
            filename = Context.getSourcePositionFromStack(linep);
            if (filename != null) {
                lineNumber = linep[0];
            } else {
                filename = "";
            }
        }
        String sourceName = ScriptRuntime.
            makeUrlForGeneratedScript(true, filename, lineNumber);

        ErrorReporter reporter;
        reporter = DefaultErrorReporter.forEval(cx.getErrorReporter());

        Evaluator evaluator = Context.createInterpreter();
        if (evaluator == null) {
            throw new JavaScriptException("Interpreter not present",
                    filename, lineNumber);
        }

        // Compile with explicit interpreter instance to force interpreter
        // mode.
        Script script = cx.compileString(x.toString(), evaluator,
                                         reporter, sourceName, 1, null);
        evaluator.setEvalScriptFlag(script);
        Callable c = (Callable)script;
        return c.call(cx, scope, (Scriptable)thisArg, ScriptRuntime.emptyArgs);
    }

    /**
     * The typeof operator
     */
    public static String typeof(Object value)
    {
        if (value == null)
            return "object";
        if (value == Undefined.instance)
            return "undefined";
        if (value instanceof ScriptableObject)
        	return ((ScriptableObject) value).getTypeOf();
        if (value instanceof Scriptable)
            return (value instanceof Callable) ? "function" : "object";
        if (value instanceof CharSequence)
            return "string";
        if (value instanceof Number)
            return "number";
        if (value instanceof Boolean)
            return "boolean";
        throw errorWithClassName("msg.invalid.type", value);
    }

    /**
     * The typeof operator that correctly handles the undefined case
     */
    public static String typeofName(Scriptable scope, String id)
    {
        Context cx = Context.getContext();
        Scriptable val = bind(cx, scope, id);
        if (val == null)
            return "undefined";
        return typeof(getObjectProp(val, id, cx));
    }

    // neg:
    // implement the '-' operator inline in the caller
    // as "-toNumber(val)"

    // not:
    // implement the '!' operator inline in the caller
    // as "!toBoolean(val)"

    // bitnot:
    // implement the '~' operator inline in the caller
    // as "~toInt32(val)"

    public static Object add(Object val1, Object val2, Context cx)
    {
        if(val1 instanceof Number && val2 instanceof Number) {
            return wrapNumber(((Number)val1).doubleValue() +
                              ((Number)val2).doubleValue());
        }
        if (val1 instanceof XMLObject) {
            Object test = ((XMLObject)val1).addValues(cx, true, val2);
            if (test != Scriptable.NOT_FOUND) {
                return test;
            }
        }
        if (val2 instanceof XMLObject) {
            Object test = ((XMLObject)val2).addValues(cx, false, val1);
            if (test != Scriptable.NOT_FOUND) {
                return test;
            }
        }
        if (val1 instanceof Scriptable)
            val1 = ((Scriptable) val1).getDefaultValue(null);
        if (val2 instanceof Scriptable)
            val2 = ((Scriptable) val2).getDefaultValue(null);
        if (!(val1 instanceof CharSequence) && !(val2 instanceof CharSequence))
            if ((val1 instanceof Number) && (val2 instanceof Number))
                return wrapNumber(((Number)val1).doubleValue() +
                                  ((Number)val2).doubleValue());
            else
                return wrapNumber(toNumber(val1) + toNumber(val2));
        return new ConsString(toCharSequence(val1), toCharSequence(val2));
    }

    public static CharSequence add(CharSequence val1, Object val2) {
        return new ConsString(val1, toCharSequence(val2));
    }

    public static CharSequence add(Object val1, CharSequence val2) {
        return new ConsString(toCharSequence(val1), val2);
    }

    /**
     * @deprecated The method is only present for compatibility.
     */
    public static Object nameIncrDecr(Scriptable scopeChain, String id,
                                      int incrDecrMask)
    {
        return nameIncrDecr(scopeChain, id, Context.getContext(), incrDecrMask);
    }

    public static Object nameIncrDecr(Scriptable scopeChain, String id,
                                      Context cx, int incrDecrMask)
    {
        Scriptable target;
        Object value;
      search: {
            do {
                if (cx.useDynamicScope && scopeChain.getParentScope() == null) {
                    scopeChain = checkDynamicScope(cx.topCallScope, scopeChain);
                }
                target = scopeChain;
                do {
                    if (target instanceof NativeWith &&
                            target.getPrototype() instanceof XMLObject) {
                        break;
                    }
                    value = target.get(id, scopeChain);
                    if (value != Scriptable.NOT_FOUND) {
                        break search;
                    }
                    target = target.getPrototype();
                } while (target != null);
                scopeChain = scopeChain.getParentScope();
            } while (scopeChain != null);
            throw notFoundError(scopeChain, id);
        }
        return doScriptableIncrDecr(target, id, scopeChain, value,
                                    incrDecrMask);
    }

    public static Object propIncrDecr(Object obj, String id,
                                      Context cx, int incrDecrMask)
    {
        Scriptable start = toObjectOrNull(cx, obj);
        if (start == null) {
            throw undefReadError(obj, id);
        }

        Scriptable target = start;
        Object value;
      search: {
            do {
                value = target.get(id, start);
                if (value != Scriptable.NOT_FOUND) {
                    break search;
                }
                target = target.getPrototype();
            } while (target != null);
            start.put(id, start, NaNobj);
            return NaNobj;
        }
        return doScriptableIncrDecr(target, id, start, value,
                                    incrDecrMask);
    }

    private static Object doScriptableIncrDecr(Scriptable target,
                                               String id,
                                               Scriptable protoChainStart,
                                               Object value,
                                               int incrDecrMask)
    {
        boolean post = ((incrDecrMask & Node.POST_FLAG) != 0);
        double number;
        if (value instanceof Number) {
            number = ((Number)value).doubleValue();
        } else {
            number = toNumber(value);
            if (post) {
                // convert result to number
                value = wrapNumber(number);
            }
        }
        if ((incrDecrMask & Node.DECR_FLAG) == 0) {
            ++number;
        } else {
            --number;
        }
        Number result = wrapNumber(number);
        target.put(id, protoChainStart, result);
        if (post) {
            return value;
        } else {
            return result;
        }
    }

    public static Object elemIncrDecr(Object obj, Object index,
                                      Context cx, int incrDecrMask)
    {
        Object value = getObjectElem(obj, index, cx);
        boolean post = ((incrDecrMask & Node.POST_FLAG) != 0);
        double number;
        if (value instanceof Number) {
            number = ((Number)value).doubleValue();
        } else {
            number = toNumber(value);
            if (post) {
                // convert result to number
                value = wrapNumber(number);
            }
        }
        if ((incrDecrMask & Node.DECR_FLAG) == 0) {
            ++number;
        } else {
            --number;
        }
        Number result = wrapNumber(number);
        setObjectElem(obj, index, result, cx);
        if (post) {
            return value;
        } else {
            return result;
        }
    }

    public static Object refIncrDecr(Ref ref, Context cx, int incrDecrMask)
    {
        Object value = ref.get(cx);
        boolean post = ((incrDecrMask & Node.POST_FLAG) != 0);
        double number;
        if (value instanceof Number) {
            number = ((Number)value).doubleValue();
        } else {
            number = toNumber(value);
            if (post) {
                // convert result to number
                value = wrapNumber(number);
            }
        }
        if ((incrDecrMask & Node.DECR_FLAG) == 0) {
            ++number;
        } else {
            --number;
        }
        Number result = wrapNumber(number);
        ref.set(cx, result);
        if (post) {
            return value;
        } else {
            return result;
        }
    }

    public static Object toPrimitive(Object val) {
        return toPrimitive(val, null);
    }

    public static Object toPrimitive(Object val, Class<?> typeHint)
    {
        if (!(val instanceof Scriptable)) {
            return val;
        }
        Scriptable s = (Scriptable)val;
        Object result = s.getDefaultValue(typeHint);
        if (result instanceof Scriptable)
            throw typeError0("msg.bad.default.value");
        return result;
    }

    /**
     * Equality
     *
     * See ECMA 11.9
     */
    public static boolean eq(Object x, Object y)
    {
        if (x == null || x == Undefined.instance) {
            if (y == null || y == Undefined.instance) {
                return true;
            }
            if (y instanceof ScriptableObject) {
                Object test = ((ScriptableObject)y).equivalentValues(x);
                if (test != Scriptable.NOT_FOUND) {
                    return ((Boolean)test).booleanValue();
                }
            }
            return false;
        } else if (x instanceof Number) {
            return eqNumber(((Number)x).doubleValue(), y);
        } else if (x == y) {
            return true;
        } else if (x instanceof CharSequence) {
            return eqString((CharSequence)x, y);
        } else if (x instanceof Boolean) {
            boolean b = ((Boolean)x).booleanValue();
            if (y instanceof Boolean) {
                return b == ((Boolean)y).booleanValue();
            }
            if (y instanceof ScriptableObject) {
                Object test = ((ScriptableObject)y).equivalentValues(x);
                if (test != Scriptable.NOT_FOUND) {
                    return ((Boolean)test).booleanValue();
                }
            }
            return eqNumber(b ? 1.0 : 0.0, y);
        } else if (x instanceof Scriptable) {
            if (y instanceof Scriptable) {
                if (x instanceof ScriptableObject) {
                    Object test = ((ScriptableObject)x).equivalentValues(y);
                    if (test != Scriptable.NOT_FOUND) {
                        return ((Boolean)test).booleanValue();
                    }
                }
                if (y instanceof ScriptableObject) {
                    Object test = ((ScriptableObject)y).equivalentValues(x);
                    if (test != Scriptable.NOT_FOUND) {
                        return ((Boolean)test).booleanValue();
                    }
                }
                if (x instanceof Wrapper && y instanceof Wrapper) {
                    // See bug 413838. Effectively an extension to ECMA for
                    // the LiveConnect case.
                    Object unwrappedX = ((Wrapper)x).unwrap();
                    Object unwrappedY = ((Wrapper)y).unwrap();
                    return unwrappedX == unwrappedY ||
                           (isPrimitive(unwrappedX) &&
                            isPrimitive(unwrappedY) &&
                            eq(unwrappedX, unwrappedY));
                }
                return false;
            } else if (y instanceof Boolean) {
                if (x instanceof ScriptableObject) {
                    Object test = ((ScriptableObject)x).equivalentValues(y);
                    if (test != Scriptable.NOT_FOUND) {
                        return ((Boolean)test).booleanValue();
                    }
                }
                double d = ((Boolean)y).booleanValue() ? 1.0 : 0.0;
                return eqNumber(d, x);
            } else if (y instanceof Number) {
                return eqNumber(((Number)y).doubleValue(), x);
            } else if (y instanceof CharSequence) {
                return eqString((CharSequence)y, x);
            }
            // covers the case when y == Undefined.instance as well
            return false;
        } else {
            warnAboutNonJSObject(x);
            return x == y;
        }
    }

    public static boolean isPrimitive(Object obj) {
        return obj == null || obj == Undefined.instance ||
                (obj instanceof Number) || (obj instanceof String) ||
                (obj instanceof Boolean);
    }

    static boolean eqNumber(double x, Object y)
    {
        for (;;) {
            if (y == null || y == Undefined.instance) {
                return false;
            } else if (y instanceof Number) {
                return x == ((Number)y).doubleValue();
            } else if (y instanceof CharSequence) {
                return x == toNumber(y);
            } else if (y instanceof Boolean) {
                return x == (((Boolean)y).booleanValue() ? 1.0 : +0.0);
            } else if (y instanceof Scriptable) {
                if (y instanceof ScriptableObject) {
                    Object xval = wrapNumber(x);
                    Object test = ((ScriptableObject)y).equivalentValues(xval);
                    if (test != Scriptable.NOT_FOUND) {
                        return ((Boolean)test).booleanValue();
                    }
                }
                y = toPrimitive(y);
            } else {
                warnAboutNonJSObject(y);
                return false;
            }
        }
    }

    private static boolean eqString(CharSequence x, Object y)
    {
        for (;;) {
            if (y == null || y == Undefined.instance) {
                return false;
            } else if (y instanceof CharSequence) {
                CharSequence c = (CharSequence)y;
                return x.length() == c.length() && x.toString().equals(c.toString());
            } else if (y instanceof Number) {
                return toNumber(x.toString()) == ((Number)y).doubleValue();
            } else if (y instanceof Boolean) {
                return toNumber(x.toString()) == (((Boolean)y).booleanValue() ? 1.0 : 0.0);
            } else if (y instanceof Scriptable) {
                if (y instanceof ScriptableObject) {
                    Object test = ((ScriptableObject)y).equivalentValues(x.toString());
                    if (test != Scriptable.NOT_FOUND) {
                        return ((Boolean)test).booleanValue();
                    }
                }
                y = toPrimitive(y);
                continue;
            } else {
                warnAboutNonJSObject(y);
                return false;
            }
        }
    }
    public static boolean shallowEq(Object x, Object y)
    {
        if (x == y) {
            if (!(x instanceof Number)) {
                return true;
            }
            // NaN check
            double d = ((Number)x).doubleValue();
            return !Double.isNaN(d);
        }
        if (x == null || x == Undefined.instance) {
            return false;
        } else if (x instanceof Number) {
            if (y instanceof Number) {
                return ((Number)x).doubleValue() == ((Number)y).doubleValue();
            }
        } else if (x instanceof CharSequence) {
            if (y instanceof CharSequence) {
                return x.toString().equals(y.toString());
            }
        } else if (x instanceof Boolean) {
            if (y instanceof Boolean) {
                return x.equals(y);
            }
        } else if (x instanceof Scriptable) {
            if (x instanceof Wrapper && y instanceof Wrapper) {
                return ((Wrapper)x).unwrap() == ((Wrapper)y).unwrap();
            }
        } else {
            warnAboutNonJSObject(x);
            return x == y;
        }
        return false;
    }

    /**
     * The instanceof operator.
     *
     * @return a instanceof b
     */
    public static boolean instanceOf(Object a, Object b, Context cx)
    {
        // Check RHS is an object
        if (! (b instanceof Scriptable)) {
            throw typeError0("msg.instanceof.not.object");
        }

        // for primitive values on LHS, return false
        if (! (a instanceof Scriptable))
            return false;

        return ((Scriptable)b).hasInstance((Scriptable)a);
    }

    /**
     * Delegates to
     *
     * @return true iff rhs appears in lhs' proto chain
     */
    public static boolean jsDelegatesTo(Scriptable lhs, Scriptable rhs) {
        Scriptable proto = lhs.getPrototype();

        while (proto != null) {
            if (proto.equals(rhs)) return true;
            proto = proto.getPrototype();
        }

        return false;
    }

    /**
     * The in operator.
     *
     * This is a new JS 1.3 language feature.  The in operator mirrors
     * the operation of the for .. in construct, and tests whether the
     * rhs has the property given by the lhs.  It is different from the
     * for .. in construct in that:
     * <BR> - it doesn't perform ToObject on the right hand side
     * <BR> - it returns true for DontEnum properties.
     * @param a the left hand operand
     * @param b the right hand operand
     *
     * @return true if property name or element number a is a property of b
     */
    public static boolean in(Object a, Object b, Context cx)
    {
        if (!(b instanceof Scriptable)) {
            throw typeError0("msg.in.not.object");
        }

        return hasObjectElem((Scriptable)b, a, cx);
    }

    public static boolean cmp_LT(Object val1, Object val2)
    {
        double d1, d2;
        if (val1 instanceof Number && val2 instanceof Number) {
            d1 = ((Number)val1).doubleValue();
            d2 = ((Number)val2).doubleValue();
        } else {
            if (val1 instanceof Scriptable)
                val1 = ((Scriptable) val1).getDefaultValue(NumberClass);
            if (val2 instanceof Scriptable)
                val2 = ((Scriptable) val2).getDefaultValue(NumberClass);
            if (val1 instanceof CharSequence && val2 instanceof CharSequence) {
                return val1.toString().compareTo(val2.toString()) < 0;
            }
            d1 = toNumber(val1);
            d2 = toNumber(val2);
        }
        return d1 < d2;
    }

    public static boolean cmp_LE(Object val1, Object val2)
    {
        double d1, d2;
        if (val1 instanceof Number && val2 instanceof Number) {
            d1 = ((Number)val1).doubleValue();
            d2 = ((Number)val2).doubleValue();
        } else {
            if (val1 instanceof Scriptable)
                val1 = ((Scriptable) val1).getDefaultValue(NumberClass);
            if (val2 instanceof Scriptable)
                val2 = ((Scriptable) val2).getDefaultValue(NumberClass);
            if (val1 instanceof CharSequence && val2 instanceof CharSequence) {
                return val1.toString().compareTo(val2.toString()) <= 0;
            }
            d1 = toNumber(val1);
            d2 = toNumber(val2);
        }
        return d1 <= d2;
    }

    // ------------------
    // Statements
    // ------------------

    public static ScriptableObject getGlobal(Context cx) {
        final String GLOBAL_CLASS = "org.mozilla.javascript.tools.shell.Global";
        Class<?> globalClass = Kit.classOrNull(GLOBAL_CLASS);
        if (globalClass != null) {
            try {
                Class<?>[] parm = { ScriptRuntime.ContextClass };
                Constructor<?> globalClassCtor = globalClass.getConstructor(parm);
                Object[] arg = { cx };
                return (ScriptableObject) globalClassCtor.newInstance(arg);
            }
            catch (RuntimeException e) {
                throw e;
            }
            catch (Exception e) {
                // fall through...
            }
        }
        return new ImporterTopLevel(cx);
    }

    public static boolean hasTopCall(Context cx)
    {
        return (cx.topCallScope != null);
    }

    public static Scriptable getTopCallScope(Context cx)
    {
        Scriptable scope = cx.topCallScope;
        if (scope == null) {
            throw new IllegalStateException();
        }
        return scope;
    }

    public static Object doTopCall(Callable callable,
                                   Context cx, Scriptable scope,
                                   Scriptable thisObj, Object[] args)
    {
        if (scope == null)
            throw new IllegalArgumentException();
        if (cx.topCallScope != null) throw new IllegalStateException();

        Object result;
        cx.topCallScope = ScriptableObject.getTopLevelScope(scope);
        cx.useDynamicScope = cx.hasFeature(Context.FEATURE_DYNAMIC_SCOPE);
        ContextFactory f = cx.getFactory();
        try {
            result = f.doTopCall(callable, cx, scope, thisObj, args);
        } finally {
            cx.topCallScope = null;
            // Cleanup cached references
            cx.cachedXMLLib = null;

            if (cx.currentActivationCall != null) {
                // Function should always call exitActivationFunction
                // if it creates activation record
                throw new IllegalStateException();
            }
        }
        return result;
    }

    /**
     * Return <tt>possibleDynamicScope</tt> if <tt>staticTopScope</tt>
     * is present on its prototype chain and return <tt>staticTopScope</tt>
     * otherwise.
     * Should only be called when <tt>staticTopScope</tt> is top scope.
     */
    static Scriptable checkDynamicScope(Scriptable possibleDynamicScope,
                                        Scriptable staticTopScope)
    {
        // Return cx.topCallScope if scope
        if (possibleDynamicScope == staticTopScope) {
            return possibleDynamicScope;
        }
        Scriptable proto = possibleDynamicScope;
        for (;;) {
            proto = proto.getPrototype();
            if (proto == staticTopScope) {
                return possibleDynamicScope;
            }
            if (proto == null) {
                return staticTopScope;
            }
        }
    }

    public static void addInstructionCount(Context cx, int instructionsToAdd)
    {
    	cx.instructionCount += instructionsToAdd;
        if (cx.instructionCount > cx.instructionThreshold)
        {
            cx.observeInstructionCount(cx.instructionCount);
            cx.instructionCount = 0;
        }
    }

    public static void initScript(NativeFunction funObj, Scriptable thisObj,
                                  Context cx, Scriptable scope,
                                  boolean evalScript)
    {
        if (cx.topCallScope == null)
            throw new IllegalStateException();

        int varCount = funObj.getParamAndVarCount();
        if (varCount != 0) {

            Scriptable varScope = scope;
            // Never define any variables from var statements inside with
            // object. See bug 38590.
            while (varScope instanceof NativeWith) {
                varScope = varScope.getParentScope();
            }

            for (int i = varCount; i-- != 0;) {
                String name = funObj.getParamOrVarName(i);
                boolean isConst = funObj.getParamOrVarConst(i);
                // Don't overwrite existing def if already defined in object
                // or prototypes of object.
                if (!ScriptableObject.hasProperty(scope, name)) {
                    if (isConst) {
                        ScriptableObject.defineConstProperty(varScope, name);
                    } else if (!evalScript) {
                        // Global var definitions are supposed to be DONTDELETE
                        ScriptableObject.defineProperty(
                            varScope, name, Undefined.instance,
                            ScriptableObject.PERMANENT);
                    } else {
                        varScope.put(name, varScope, Undefined.instance);
                    }
                } else {
                    ScriptableObject.redefineProperty(scope, name, isConst);
                }
            }
        }
    }

    public static Scriptable createFunctionActivation(NativeFunction funObj,
                                                      Scriptable scope,
                                                      Object[] args)
    {
        return new NativeCall(funObj, scope, args);
    }


    public static void enterActivationFunction(Context cx,
                                               Scriptable scope)
    {
        if (cx.topCallScope == null)
            throw new IllegalStateException();
        NativeCall call = (NativeCall)scope;
        call.parentActivationCall = cx.currentActivationCall;
        cx.currentActivationCall = call;
    }

    public static void exitActivationFunction(Context cx)
    {
        NativeCall call = cx.currentActivationCall;
        cx.currentActivationCall = call.parentActivationCall;
        call.parentActivationCall = null;
    }

    static NativeCall findFunctionActivation(Context cx, Function f)
    {
        NativeCall call = cx.currentActivationCall;
        while (call != null) {
            if (call.function == f)
                return call;
            call = call.parentActivationCall;
        }
        return null;
    }

    public static Scriptable newCatchScope(Throwable t,
                                           Scriptable lastCatchScope,
                                           String exceptionName,
                                           Context cx, Scriptable scope)
    {
        Object obj;
        boolean cacheObj;

        if (t instanceof JavaScriptException) {
            cacheObj = false;
            obj = ((JavaScriptException)t).getValue();
        } else {
            cacheObj = true;

            // Create wrapper object unless it was associated with
            // the previous scope object

            if (lastCatchScope != null) {
                NativeObject last = (NativeObject)lastCatchScope;
                obj = last.getAssociatedValue(t);
                if (obj == null) Kit.codeBug();
            } else {
                obj = wrapException(t, scope, cx);
            }
        }

        NativeObject catchScopeObject = new NativeObject();
        // See ECMA 12.4
        catchScopeObject.defineProperty(
            exceptionName, obj, ScriptableObject.PERMANENT);

        if (isVisible(cx, t)) {
            // Add special Rhino object __exception__ defined in the catch
            // scope that can be used to retrieve the Java exception associated
            // with the JavaScript exception (to get stack trace info, etc.)
            catchScopeObject.defineProperty(
                "__exception__", Context.javaToJS(t, scope),
                ScriptableObject.PERMANENT|ScriptableObject.DONTENUM);
        }

        if (cacheObj) {
            catchScopeObject.associateValue(t, obj);
        }
        return catchScopeObject;
    }

    public static Scriptable wrapException(Throwable t,
                                           Scriptable scope,
                                           Context cx) {
        RhinoException re;
        String errorName;
        String errorMsg;
        Throwable javaException = null;

        if (t instanceof EcmaError) {
            EcmaError ee = (EcmaError)t;
            re = ee;
            errorName = ee.getName();
            errorMsg = ee.getErrorMessage();
        } else if (t instanceof WrappedException) {
            WrappedException we = (WrappedException)t;
            re = we;
            javaException = we.getWrappedException();
            errorName = "JavaException";
            errorMsg = javaException.getClass().getName()
                       +": "+javaException.getMessage();
        } else if (t instanceof EvaluatorException) {
            // Pure evaluator exception, nor WrappedException instance
            EvaluatorException ee = (EvaluatorException)t;
            re = ee;
            errorName = "InternalError";
            errorMsg = ee.getMessage();
        } else if (cx.hasFeature(Context.FEATURE_ENHANCED_JAVA_ACCESS)) {
            // With FEATURE_ENHANCED_JAVA_ACCESS, scripts can catch
            // all exception types
            re = new WrappedException(t);
            errorName = "JavaException";
            errorMsg = t.toString();
        } else {
            // Script can catch only instances of JavaScriptException,
            // EcmaError and EvaluatorException
            throw Kit.codeBug();
        }

        String sourceUri = re.sourceName();
        if (sourceUri == null) {
            sourceUri = "";
        }
        int line = re.lineNumber();
        Object args[];
        if (line > 0) {
            args = new Object[] { errorMsg, sourceUri, Integer.valueOf(line) };
        } else {
            args = new Object[] { errorMsg, sourceUri };
        }

        Scriptable errorObject = cx.newObject(scope, errorName, args);
        ScriptableObject.putProperty(errorObject, "name", errorName);
        // set exception in Error objects to enable non-ECMA "stack" property
        if (errorObject instanceof NativeError) {
            ((NativeError) errorObject).setStackProvider(re);
        }

        if (javaException != null && isVisible(cx, javaException)) {
            Object wrap = cx.getWrapFactory().wrap(cx, scope, javaException,
                                                   null);
            ScriptableObject.defineProperty(
                errorObject, "javaException", wrap,
                ScriptableObject.PERMANENT | ScriptableObject.READONLY);
        }
        if (isVisible(cx, re)) {
            Object wrap = cx.getWrapFactory().wrap(cx, scope, re, null);
            ScriptableObject.defineProperty(
                    errorObject, "rhinoException", wrap,
                    ScriptableObject.PERMANENT | ScriptableObject.READONLY);
        }
        return errorObject;
    }

    private static boolean isVisible(Context cx, Object obj) {
        ClassShutter shutter = cx.getClassShutter();
        return shutter == null ||
            shutter.visibleToScripts(obj.getClass().getName());
    }

    public static Scriptable enterWith(Object obj, Context cx,
                                       Scriptable scope)
    {
        Scriptable sobj = toObjectOrNull(cx, obj);
        if (sobj == null) {
            throw typeError1("msg.undef.with", toString(obj));
        }
        if (sobj instanceof XMLObject) {
            XMLObject xmlObject = (XMLObject)sobj;
            return xmlObject.enterWith(scope);
        }
        return new NativeWith(scope, sobj);
    }

    public static Scriptable leaveWith(Scriptable scope)
    {
        NativeWith nw = (NativeWith)scope;
        return nw.getParentScope();
    }

    public static Scriptable enterDotQuery(Object value, Scriptable scope)
    {
        if (!(value instanceof XMLObject)) {
            throw notXmlError(value);
        }
        XMLObject object = (XMLObject)value;
        return object.enterDotQuery(scope);
    }

    public static Object updateDotQuery(boolean value, Scriptable scope)
    {
        // Return null to continue looping
        NativeWith nw = (NativeWith)scope;
        return nw.updateDotQuery(value);
    }

    public static Scriptable leaveDotQuery(Scriptable scope)
    {
        NativeWith nw = (NativeWith)scope;
        return nw.getParentScope();
    }

    public static void setFunctionProtoAndParent(BaseFunction fn,
                                                 Scriptable scope)
    {
        fn.setParentScope(scope);
        fn.setPrototype(ScriptableObject.getFunctionPrototype(scope));
    }

    public static void setObjectProtoAndParent(ScriptableObject object,
                                               Scriptable scope)
    {
        // Compared with function it always sets the scope to top scope
        scope = ScriptableObject.getTopLevelScope(scope);
        object.setParentScope(scope);
        Scriptable proto
            = ScriptableObject.getClassPrototype(scope, object.getClassName());
        object.setPrototype(proto);
    }

    public static void setBuiltinProtoAndParent(ScriptableObject object,
                                                Scriptable scope,
                                                TopLevel.Builtins type)
    {
        scope = ScriptableObject.getTopLevelScope(scope);
        object.setParentScope(scope);
        object.setPrototype(TopLevel.getBuiltinPrototype(scope, type));
    }


    public static void initFunction(Context cx, Scriptable scope,
                                    NativeFunction function, int type,
                                    boolean fromEvalCode)
    {
        if (type == FunctionNode.FUNCTION_STATEMENT) {
            String name = function.getFunctionName();
            if (name != null && name.length() != 0) {
                if (!fromEvalCode) {
                    // ECMA specifies that functions defined in global and
                    // function scope outside eval should have DONTDELETE set.
                    ScriptableObject.defineProperty
                        (scope, name, function, ScriptableObject.PERMANENT);
                } else {
                    scope.put(name, scope, function);
                }
            }
        } else if (type == FunctionNode.FUNCTION_EXPRESSION_STATEMENT) {
            String name = function.getFunctionName();
            if (name != null && name.length() != 0) {
                // Always put function expression statements into initial
                // activation object ignoring the with statement to follow
                // SpiderMonkey
                while (scope instanceof NativeWith) {
                    scope = scope.getParentScope();
                }
                scope.put(name, scope, function);
            }
        } else {
            throw Kit.codeBug();
        }
    }

    public static Scriptable newArrayLiteral(Object[] objects,
                                             int[] skipIndices,
                                             Context cx, Scriptable scope)
    {
        final int SKIP_DENSITY = 2;
        int count = objects.length;
        int skipCount = 0;
        if (skipIndices != null) {
            skipCount = skipIndices.length;
        }
        int length = count + skipCount;
        if (length > 1 && skipCount * SKIP_DENSITY < length) {
            // If not too sparse, create whole array for constructor
            Object[] sparse;
            if (skipCount == 0) {
                sparse = objects;
            } else {
                sparse = new Object[length];
                int skip = 0;
                for (int i = 0, j = 0; i != length; ++i) {
                    if (skip != skipCount && skipIndices[skip] == i) {
                        sparse[i] = Scriptable.NOT_FOUND;
                        ++skip;
                        continue;
                    }
                    sparse[i] = objects[j];
                    ++j;
                }
            }
            return cx.newArray(scope, sparse);
        }

        Scriptable array = cx.newArray(scope, length);

        int skip = 0;
        for (int i = 0, j = 0; i != length; ++i) {
            if (skip != skipCount && skipIndices[skip] == i) {
                ++skip;
                continue;
            }
            ScriptableObject.putProperty(array, i, objects[j]);
            ++j;
        }
        return array;
    }

  /**
   * This method is here for backward compat with existing compiled code.  It
   * is called when an object literal is compiled.  The next instance will be
   * the version called from new code.
   * @deprecated This method only present for compatibility.
   */
    public static Scriptable newObjectLiteral(Object[] propertyIds,
                                              Object[] propertyValues,
                                              Context cx, Scriptable scope)
    {
        // Passing null for getterSetters means no getters or setters
        return newObjectLiteral(propertyIds, propertyValues, null, cx, scope);

    }

    public static Scriptable newObjectLiteral(Object[] propertyIds,
                                              Object[] propertyValues,
                                              int [] getterSetters,
                                              Context cx, Scriptable scope)
    {
        Scriptable object = cx.newObject(scope);
        for (int i = 0, end = propertyIds.length; i != end; ++i) {
            Object id = propertyIds[i];
            int getterSetter = getterSetters == null ? 0 : getterSetters[i];
            Object value = propertyValues[i];
            if (id instanceof String) {
                if (getterSetter == 0) {
                    if (isSpecialProperty((String)id)) {
                        specialRef(object, (String)id, cx).set(cx, value);
                    } else {
                        object.put((String)id, object, value);
                    }
                } else {
                    ScriptableObject so = (ScriptableObject)object;
                    Callable getterOrSetter = (Callable)value;
                    boolean isSetter = getterSetter == 1;
                    so.setGetterOrSetter((String)id, 0, getterOrSetter, isSetter);
                }
            } else {
                int index = ((Integer)id).intValue();
                object.put(index, object, value);
            }
        }
        return object;
    }

    public static boolean isArrayObject(Object obj)
    {
        return obj instanceof NativeArray || obj instanceof Arguments;
    }

    public static Object[] getArrayElements(Scriptable object)
    {
        Context cx = Context.getContext();
        long longLen = NativeArray.getLengthProperty(cx, object);
        if (longLen > Integer.MAX_VALUE) {
            // arrays beyond  MAX_INT is not in Java in any case
            throw new IllegalArgumentException();
        }
        int len = (int) longLen;
        if (len == 0) {
            return ScriptRuntime.emptyArgs;
        } else {
            Object[] result = new Object[len];
            for (int i=0; i < len; i++) {
                Object elem = ScriptableObject.getProperty(object, i);
                result[i] = (elem == Scriptable.NOT_FOUND) ? Undefined.instance
                                                           : elem;
            }
            return result;
        }
    }

    static void checkDeprecated(Context cx, String name) {
        int version = cx.getLanguageVersion();
        if (version >= Context.VERSION_1_4 || version == Context.VERSION_DEFAULT) {
            String msg = getMessage1("msg.deprec.ctor", name);
            if (version == Context.VERSION_DEFAULT)
                Context.reportWarning(msg);
            else
                throw Context.reportRuntimeError(msg);
        }
    }

    public static String getMessage0(String messageId)
    {
        return getMessage(messageId, null);
    }

    public static String getMessage1(String messageId, Object arg1)
    {
        Object[] arguments = {arg1};
        return getMessage(messageId, arguments);
    }

    public static String getMessage2(
        String messageId, Object arg1, Object arg2)
    {
        Object[] arguments = {arg1, arg2};
        return getMessage(messageId, arguments);
    }

    public static String getMessage3(
        String messageId, Object arg1, Object arg2, Object arg3)
    {
        Object[] arguments = {arg1, arg2, arg3};
        return getMessage(messageId, arguments);
    }

    public static String getMessage4(
        String messageId, Object arg1, Object arg2, Object arg3, Object arg4)
    {
        Object[] arguments = {arg1, arg2, arg3, arg4};
        return getMessage(messageId, arguments);
    }

    /**
     * This is an interface defining a message provider. Create your
     * own implementation to override the default error message provider.
     *
     */
    public interface MessageProvider {

        /**
         * Returns a textual message identified by the given messageId,
         * parameterized by the given arguments.
         *
         * @param messageId the identifier of the message
         * @param arguments the arguments to fill into the message
         */
        String getMessage(String messageId, Object[] arguments);
    }

    public static MessageProvider messageProvider = new DefaultMessageProvider();

    public static String getMessage(String messageId, Object[] arguments)
    {
        return messageProvider.getMessage(messageId, arguments);
    }

    /* OPT there's a noticable delay for the first error!  Maybe it'd
     * make sense to use a ListResourceBundle instead of a properties
     * file to avoid (synchronized) text parsing.
     */
    private static class DefaultMessageProvider implements MessageProvider {
        public String getMessage(String messageId, Object[] arguments) {
            final String defaultResource
                = "org.mozilla.javascript.resources.Messages";

            Context cx = Context.getCurrentContext();
            Locale locale = cx != null ? cx.getLocale() : Locale.getDefault();

            // ResourceBundle does caching.
            ResourceBundle rb = ResourceBundle.getBundle(defaultResource, locale);

            String formatString;
            try {
                formatString = rb.getString(messageId);
            } catch (java.util.MissingResourceException mre) {
                throw new RuntimeException
                    ("no message resource found for message property "+ messageId);
            }

            /*
             * It's OK to format the string, even if 'arguments' is null;
             * we need to format it anyway, to make double ''s collapse to
             * single 's.
             */
            MessageFormat formatter = new MessageFormat(formatString);
            return formatter.format(arguments);
        }
    }

    public static EcmaError constructError(String error, String message)
    {
        int[] linep = new int[1];
        String filename = Context.getSourcePositionFromStack(linep);
        return constructError(error, message, filename, linep[0], null, 0);
    }

    public static EcmaError constructError(String error,
                                           String message,
                                           int lineNumberDelta)
    {
        int[] linep = new int[1];
        String filename = Context.getSourcePositionFromStack(linep);
        if (linep[0] != 0) {
            linep[0] += lineNumberDelta;
        }
        return constructError(error, message, filename, linep[0], null, 0);
    }

    public static EcmaError constructError(String error,
                                           String message,
                                           String sourceName,
                                           int lineNumber,
                                           String lineSource,
                                           int columnNumber)
    {
        return new EcmaError(error, message, sourceName,
                             lineNumber, lineSource, columnNumber);
    }

    public static EcmaError typeError(String message)
    {
        return constructError("TypeError", message);
    }

    public static EcmaError typeError0(String messageId)
    {
        String msg = getMessage0(messageId);
        return typeError(msg);
    }

    public static EcmaError typeError1(String messageId, String arg1)
    {
        String msg = getMessage1(messageId, arg1);
        return typeError(msg);
    }

    public static EcmaError typeError2(String messageId, String arg1,
                                       String arg2)
    {
        String msg = getMessage2(messageId, arg1, arg2);
        return typeError(msg);
    }

    public static EcmaError typeError3(String messageId, String arg1,
                                       String arg2, String arg3)
    {
        String msg = getMessage3(messageId, arg1, arg2, arg3);
        return typeError(msg);
    }

    public static RuntimeException undefReadError(Object object, Object id)
    {
        return typeError2("msg.undef.prop.read", toString(object), toString(id));
    }

    public static RuntimeException undefCallError(Object object, Object id)
    {
        return typeError2("msg.undef.method.call", toString(object), toString(id));
    }

    public static RuntimeException undefWriteError(Object object,
                                                   Object id,
                                                   Object value)
    {
        return typeError3("msg.undef.prop.write", toString(object), toString(id),
                          toString(value));
    }

    private static RuntimeException undefDeleteError(Object object, Object id)
    {
        throw typeError2("msg.undef.prop.delete", toString(object), toString(id));
    }

    public static RuntimeException notFoundError(Scriptable object,
                                                 String property)
    {
        // XXX: use object to improve the error message
        String msg = getMessage1("msg.is.not.defined", property);
        throw constructError("ReferenceError", msg);
    }

    public static RuntimeException notFunctionError(Object value)
    {
        return notFunctionError(value, value);
    }

    public static RuntimeException notFunctionError(Object value,
                                                    Object messageHelper)
    {
        // Use value for better error reporting
        String msg = (messageHelper == null)
                     ? "null" : messageHelper.toString();
        if (value == Scriptable.NOT_FOUND) {
            return typeError1("msg.function.not.found", msg);
        }
        return typeError2("msg.isnt.function", msg, typeof(value));
    }

    public static RuntimeException notFunctionError(Object obj, Object value,
            String propertyName)
    {
        // Use obj and value for better error reporting
        String objString = toString(obj);
        if (obj instanceof NativeFunction) {
            // Omit function body in string representations of functions
            int paren = objString.indexOf(')');
            int curly = objString.indexOf('{', paren);
            if (curly > -1) {
                objString = objString.substring(0, curly + 1) + "...}";
            }
        }
        if (value == Scriptable.NOT_FOUND) {
            return typeError2("msg.function.not.found.in", propertyName,
                    objString);
        }
        return typeError3("msg.isnt.function.in", propertyName, objString,
                          typeof(value));
    }

    private static RuntimeException notXmlError(Object value)
    {
        throw typeError1("msg.isnt.xml.object", toString(value));
    }

    private static void warnAboutNonJSObject(Object nonJSObject)
    {
        String message =
"RHINO USAGE WARNING: Missed Context.javaToJS() conversion:\n"
+"Rhino runtime detected object "+nonJSObject+" of class "+nonJSObject.getClass().getName()+" where it expected String, Number, Boolean or Scriptable instance. Please check your code for missing Context.javaToJS() call.";
        Context.reportWarning(message);
        // Just to be sure that it would be noticed
        System.err.println(message);
    }

    public static RegExpProxy getRegExpProxy(Context cx)
    {
        return cx.getRegExpProxy();
    }

    public static void setRegExpProxy(Context cx, RegExpProxy proxy)
    {
        if (proxy == null) throw new IllegalArgumentException();
        cx.regExpProxy = proxy;
    }

    public static RegExpProxy checkRegExpProxy(Context cx)
    {
        RegExpProxy result = getRegExpProxy(cx);
        if (result == null) {
            throw Context.reportRuntimeError0("msg.no.regexp");
        }
        return result;
    }

    public static Scriptable wrapRegExp(Context cx, Scriptable scope,
                                        Object compiled) {
        return cx.getRegExpProxy().wrapRegExp(cx, scope, compiled);
    }

    private static XMLLib currentXMLLib(Context cx)
    {
        // Scripts should be running to access this
        if (cx.topCallScope == null)
            throw new IllegalStateException();

        XMLLib xmlLib = cx.cachedXMLLib;
        if (xmlLib == null) {
            xmlLib = XMLLib.extractFromScope(cx.topCallScope);
            if (xmlLib == null)
                throw new IllegalStateException();
            cx.cachedXMLLib = xmlLib;
        }

        return xmlLib;
    }

    /**
     * Escapes the reserved characters in a value of an attribute
     *
     * @param value Unescaped text
     * @return The escaped text
     */
    public static String escapeAttributeValue(Object value, Context cx)
    {
        XMLLib xmlLib = currentXMLLib(cx);
        return xmlLib.escapeAttributeValue(value);
    }

    /**
     * Escapes the reserved characters in a value of a text node
     *
     * @param value Unescaped text
     * @return The escaped text
     */
    public static String escapeTextValue(Object value, Context cx)
    {
        XMLLib xmlLib = currentXMLLib(cx);
        return xmlLib.escapeTextValue(value);
    }

    public static Ref memberRef(Object obj, Object elem,
                                Context cx, int memberTypeFlags)
    {
        if (!(obj instanceof XMLObject)) {
            throw notXmlError(obj);
        }
        XMLObject xmlObject = (XMLObject)obj;
        return xmlObject.memberRef(cx, elem, memberTypeFlags);
    }

    public static Ref memberRef(Object obj, Object namespace, Object elem,
                                Context cx, int memberTypeFlags)
    {
        if (!(obj instanceof XMLObject)) {
            throw notXmlError(obj);
        }
        XMLObject xmlObject = (XMLObject)obj;
        return xmlObject.memberRef(cx, namespace, elem, memberTypeFlags);
    }

    public static Ref nameRef(Object name, Context cx,
                              Scriptable scope, int memberTypeFlags)
    {
        XMLLib xmlLib = currentXMLLib(cx);
        return xmlLib.nameRef(cx, name, scope, memberTypeFlags);
    }

    public static Ref nameRef(Object namespace, Object name, Context cx,
                              Scriptable scope, int memberTypeFlags)
    {
        XMLLib xmlLib = currentXMLLib(cx);
        return xmlLib.nameRef(cx, namespace, name, scope, memberTypeFlags);
    }

    private static void storeIndexResult(Context cx, int index)
    {
        cx.scratchIndex = index;
    }

    static int lastIndexResult(Context cx)
    {
        return cx.scratchIndex;
    }

    public static void storeUint32Result(Context cx, long value)
    {
        if ((value >>> 32) != 0)
            throw new IllegalArgumentException();
        cx.scratchUint32 = value;
    }

    public static long lastUint32Result(Context cx)
    {
        long value = cx.scratchUint32;
        if ((value >>> 32) != 0)
            throw new IllegalStateException();
        return value;
    }

    private static void storeScriptable(Context cx, Scriptable value)
    {
        // The previously stored scratchScriptable should be consumed
        if (cx.scratchScriptable != null)
            throw new IllegalStateException();
        cx.scratchScriptable = value;
    }

    public static Scriptable lastStoredScriptable(Context cx)
    {
        Scriptable result = cx.scratchScriptable;
        cx.scratchScriptable = null;
        return result;
    }

    static String makeUrlForGeneratedScript
        (boolean isEval, String masterScriptUrl, int masterScriptLine)
    {
        if (isEval) {
            return masterScriptUrl+'#'+masterScriptLine+"(eval)";
        } else {
            return masterScriptUrl+'#'+masterScriptLine+"(Function)";
        }
    }

    static boolean isGeneratedScript(String sourceUrl) {
        // ALERT: this may clash with a valid URL containing (eval) or
        // (Function)
        return sourceUrl.indexOf("(eval)") >= 0
               || sourceUrl.indexOf("(Function)") >= 0;
    }

    private static RuntimeException errorWithClassName(String msg, Object val)
    {
        return Context.reportRuntimeError1(msg, val.getClass().getName());
    }

    /**
     * Equivalent to executing "new Error(message)" from JavaScript.
     * @param cx the current context
     * @param scope the current scope
     * @param message the message
     * @return a JavaScriptException you should throw
     */
    public static JavaScriptException throwError(Context cx, Scriptable scope,
            String message) {
      int[] linep = { 0 };
      String filename = Context.getSourcePositionFromStack(linep);
        final Scriptable error = newBuiltinObject(cx, scope,
                TopLevel.Builtins.Error, new Object[] { message, filename, Integer.valueOf(linep[0]) });
        return new JavaScriptException(error, filename, linep[0]);
    }

    public static final Object[] emptyArgs = new Object[0];
    public static final String[] emptyStrings = new String[0];

}
