package org.mozilla.javascript.tests;

import junit.framework.TestCase;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=466207
 */
public class Bug466207Test extends TestCase {

    List<Object> list, reference;

    @SuppressWarnings("unchecked")
    @Override
    protected void setUp() {
        // set up a reference map
        reference = new ArrayList<Object>();
        reference.add("a");
        reference.add(Boolean.TRUE);
        reference.add(new HashMap<Object, Object>());
        reference.add(new Integer(42));
        reference.add("a");
        // get a js object as map
        Context context = Context.enter();
        ScriptableObject scope = context.initStandardObjects();
        list = (List<Object>) context.evaluateString(scope,
                "(['a', true, new java.util.HashMap(), 42, 'a']);",
                "testsrc", 1, null);
        Context.exit();
    }

    public void testEqual() {
        // FIXME we do not override equals() and hashCode() in NativeArray
        // so calling this with swapped argument fails. This breaks symmetry
        // of equals(), but overriding these methods might be risky.
        assertEquals(reference, list);
    }

    public void testIndexedAccess() {
        assertTrue(list.size() == 5);
        assertEquals(list.get(0), reference.get(0));
        assertEquals(list.get(1), reference.get(1));
        assertEquals(list.get(2), reference.get(2));
        assertEquals(list.get(3), reference.get(3));
        assertEquals(list.get(4), reference.get(4));
    }

    public void testContains() {
        assertTrue(list.contains("a"));
        assertTrue(list.contains(Boolean.TRUE));
        assertFalse(list.contains("x"));
        assertFalse(list.contains(Boolean.FALSE));
        assertFalse(list.contains(null));
    }

    public void testIndexOf() {
        assertTrue(list.indexOf("a") == 0);
        assertTrue(list.indexOf(Boolean.TRUE) == 1);
        assertTrue(list.lastIndexOf("a") == 4);
        assertTrue(list.lastIndexOf(Boolean.TRUE) == 1);
        assertTrue(list.indexOf("x") == -1);
        assertTrue(list.lastIndexOf("x") == -1);
        assertTrue(list.indexOf(null) == -1);
        assertTrue(list.lastIndexOf(null) == -1);
    }

    public void testToArray() {
        assertTrue(Arrays.equals(list.toArray(), reference.toArray()));
        assertTrue(Arrays.equals(list.toArray(new Object[5]), reference.toArray(new Object[5])));
        assertTrue(Arrays.equals(list.toArray(new Object[6]), reference.toArray(new Object[6])));
    }

    public void testIterator() {
        compareIterators(list.iterator(), reference.iterator());
        compareIterators(list.listIterator(), reference.listIterator());
        compareIterators(list.listIterator(2), reference.listIterator(2));
        compareIterators(list.listIterator(3), reference.listIterator(3));
        compareIterators(list.listIterator(5), reference.listIterator(5));
        compareListIterators(list.listIterator(), reference.listIterator());
        compareListIterators(list.listIterator(2), reference.listIterator(2));
        compareListIterators(list.listIterator(3), reference.listIterator(3));
        compareListIterators(list.listIterator(5), reference.listIterator(5));
    }

    private void compareIterators(Iterator it1, Iterator it2) {
        while (it1.hasNext()) {
            assertEquals(it1.next(), it2.next());
        }
        assertFalse(it2.hasNext());
    }

    private void compareListIterators(ListIterator it1, ListIterator it2) {
        while (it1.hasPrevious()) {
            assertEquals(it1.previous(), it2.previous());
        }
        assertFalse(it2.hasPrevious());
        compareIterators(it1, it2);
    }
}
