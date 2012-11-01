/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.classfile;

import org.mozilla.javascript.ObjToIntMap;
import org.mozilla.javascript.ObjArray;
import org.mozilla.javascript.UintMap;

import java.io.*;
import java.util.Arrays;

/**
 * ClassFileWriter
 *
 * A ClassFileWriter is used to write a Java class file. Methods are
 * provided to create fields and methods, and within methods to write
 * Java bytecodes.
 *
 */
public class ClassFileWriter {

    /**
     * Thrown for cases where the error in generating the class file is
     * due to a program size constraints rather than a likely bug in the
     * compiler.
     */
    public static class ClassFileFormatException extends RuntimeException {

        private static final long serialVersionUID = 1263998431033790599L;

        ClassFileFormatException(String message) {
            super(message);
        }
    }

    /**
     * Construct a ClassFileWriter for a class.
     *
     * @param className the name of the class to write, including
     *        full package qualification.
     * @param superClassName the name of the superclass of the class
     *        to write, including full package qualification.
     * @param sourceFileName the name of the source file to use for
     *        producing debug information, or null if debug information
     *        is not desired
     */
    public ClassFileWriter(String className, String superClassName,
                           String sourceFileName)
    {
        generatedClassName = className;
        itsConstantPool = new ConstantPool(this);
        itsThisClassIndex = itsConstantPool.addClass(className);
        itsSuperClassIndex = itsConstantPool.addClass(superClassName);
        if (sourceFileName != null)
            itsSourceFileNameIndex = itsConstantPool.addUtf8(sourceFileName);
        // All "new" implementations are supposed to output ACC_SUPER as a
        // class flag. This is specified in the first JVM spec, so it should
        // be old enough that it's okay to always set it.
        itsFlags = ACC_PUBLIC | ACC_SUPER;
    }

    public final String getClassName()
    {
        return generatedClassName;
    }

    /**
     * Add an interface implemented by this class.
     *
     * This method may be called multiple times for classes that
     * implement multiple interfaces.
     *
     * @param interfaceName a name of an interface implemented
     *        by the class being written, including full package
     *        qualification.
     */
    public void addInterface(String interfaceName) {
        short interfaceIndex = itsConstantPool.addClass(interfaceName);
        itsInterfaces.add(Short.valueOf(interfaceIndex));
    }

    public static final short
        ACC_PUBLIC = 0x0001,
        ACC_PRIVATE = 0x0002,
        ACC_PROTECTED = 0x0004,
        ACC_STATIC = 0x0008,
        ACC_FINAL = 0x0010,
        ACC_SUPER = 0x0020,
        ACC_SYNCHRONIZED = 0x0020,
        ACC_VOLATILE = 0x0040,
        ACC_TRANSIENT = 0x0080,
        ACC_NATIVE = 0x0100,
        ACC_ABSTRACT = 0x0400;

    /**
     * Set the class's flags.
     *
     * Flags must be a set of the following flags, bitwise or'd
     * together:
     *      ACC_PUBLIC
     *      ACC_PRIVATE
     *      ACC_PROTECTED
     *      ACC_FINAL
     *      ACC_ABSTRACT
     * TODO: check that this is the appropriate set
     * @param flags the set of class flags to set
     */
    public void setFlags(short flags) {
        itsFlags = flags;
    }

    static String getSlashedForm(String name)
    {
        return name.replace('.', '/');
    }

    /**
     * Convert Java class name in dot notation into
     * "Lname-with-dots-replaced-by-slashes;" form suitable for use as
     * JVM type signatures.
     */
    public static String classNameToSignature(String name)
    {
        int nameLength = name.length();
        int colonPos = 1 + nameLength;
        char[] buf = new char[colonPos + 1];
        buf[0] = 'L';
        buf[colonPos] = ';';
        name.getChars(0, nameLength, buf, 1);
        for (int i = 1; i != colonPos; ++i) {
            if (buf[i] == '.') {
                buf[i] = '/';
            }
        }
        return new String(buf, 0, colonPos + 1);
    }

    /**
     * Add a field to the class.
     *
     * @param fieldName the name of the field
     * @param type the type of the field using ...
     * @param flags the attributes of the field, such as ACC_PUBLIC, etc.
     *        bitwise or'd together
     */
    public void addField(String fieldName, String type, short flags) {
        short fieldNameIndex = itsConstantPool.addUtf8(fieldName);
        short typeIndex = itsConstantPool.addUtf8(type);
        itsFields.add(new ClassFileField(fieldNameIndex, typeIndex, flags));
    }

    /**
     * Add a field to the class.
     *
     * @param fieldName the name of the field
     * @param type the type of the field using ...
     * @param flags the attributes of the field, such as ACC_PUBLIC, etc.
     *        bitwise or'd together
     * @param value an initial integral value
     */
    public void addField(String fieldName, String type, short flags,
                         int value)
    {
        short fieldNameIndex = itsConstantPool.addUtf8(fieldName);
        short typeIndex = itsConstantPool.addUtf8(type);
        ClassFileField field = new ClassFileField(fieldNameIndex, typeIndex,
                                                  flags);
        field.setAttributes(itsConstantPool.addUtf8("ConstantValue"),
                            (short)0,
                            (short)0,
                            itsConstantPool.addConstant(value));
        itsFields.add(field);
    }

    /**
     * Add a field to the class.
     *
     * @param fieldName the name of the field
     * @param type the type of the field using ...
     * @param flags the attributes of the field, such as ACC_PUBLIC, etc.
     *        bitwise or'd together
     * @param value an initial long value
     */
    public void addField(String fieldName, String type, short flags,
                         long value)
    {
        short fieldNameIndex = itsConstantPool.addUtf8(fieldName);
        short typeIndex = itsConstantPool.addUtf8(type);
        ClassFileField field = new ClassFileField(fieldNameIndex, typeIndex,
                                                  flags);
        field.setAttributes(itsConstantPool.addUtf8("ConstantValue"),
                            (short)0,
                            (short)2,
                            itsConstantPool.addConstant(value));
        itsFields.add(field);
    }

    /**
     * Add a field to the class.
     *
     * @param fieldName the name of the field
     * @param type the type of the field using ...
     * @param flags the attributes of the field, such as ACC_PUBLIC, etc.
     *        bitwise or'd together
     * @param value an initial double value
     */
    public void addField(String fieldName, String type, short flags,
                         double value)
    {
        short fieldNameIndex = itsConstantPool.addUtf8(fieldName);
        short typeIndex = itsConstantPool.addUtf8(type);
        ClassFileField field = new ClassFileField(fieldNameIndex, typeIndex,
                                                  flags);
        field.setAttributes(itsConstantPool.addUtf8("ConstantValue"),
                            (short)0,
                            (short)2,
                            itsConstantPool.addConstant(value));
        itsFields.add(field);
    }

    /**
     * Add Information about java variable to use when generating the local
     * variable table.
     *
     * @param name variable name.
     * @param type variable type as bytecode descriptor string.
     * @param startPC the starting bytecode PC where this variable is live,
     *                 or -1 if it does not have a Java register.
     * @param register the Java register number of variable
     *                 or -1 if it does not have a Java register.
     */
    public void addVariableDescriptor(String name, String type, int startPC, int register)
    {
        int nameIndex = itsConstantPool.addUtf8(name);
        int descriptorIndex = itsConstantPool.addUtf8(type);
        int [] chunk = { nameIndex, descriptorIndex, startPC, register };
        if (itsVarDescriptors == null) {
            itsVarDescriptors = new ObjArray();
        }
        itsVarDescriptors.add(chunk);
    }

    /**
     * Add a method and begin adding code.
     *
     * This method must be called before other methods for adding code,
     * exception tables, etc. can be invoked.
     *
     * @param methodName the name of the method
     * @param type a string representing the type
     * @param flags the attributes of the field, such as ACC_PUBLIC, etc.
     *        bitwise or'd together
     */
    public void startMethod(String methodName, String type, short flags) {
        short methodNameIndex = itsConstantPool.addUtf8(methodName);
        short typeIndex = itsConstantPool.addUtf8(type);
        itsCurrentMethod = new ClassFileMethod(methodName, methodNameIndex,
                                               type, typeIndex, flags);
        itsJumpFroms = new UintMap();
        itsMethods.add(itsCurrentMethod);
        addSuperBlockStart(0);
    }

    /**
     * Complete generation of the method.
     *
     * After this method is called, no more code can be added to the
     * method begun with <code>startMethod</code>.
     *
     * @param maxLocals the maximum number of local variable slots
     *        (a.k.a. Java registers) used by the method
     */
    public void stopMethod(short maxLocals) {
        if (itsCurrentMethod == null)
            throw new IllegalStateException("No method to stop");

        fixLabelGotos();

        itsMaxLocals = maxLocals;

        StackMapTable stackMap = null;
        if (GenerateStackMap) {
            finalizeSuperBlockStarts();
            stackMap = new StackMapTable();
            stackMap.generate();
        }

        int lineNumberTableLength = 0;
        if (itsLineNumberTable != null) {
            // 6 bytes for the attribute header
            // 2 bytes for the line number count
            // 4 bytes for each entry
            lineNumberTableLength = 6 + 2 + (itsLineNumberTableTop * 4);
        }

        int variableTableLength = 0;
        if (itsVarDescriptors != null) {
            // 6 bytes for the attribute header
            // 2 bytes for the variable count
            // 10 bytes for each entry
            variableTableLength = 6 + 2 + (itsVarDescriptors.size() * 10);
        }

        int stackMapTableLength = 0;
        if (stackMap != null) {
            int stackMapWriteSize = stackMap.computeWriteSize();
            if (stackMapWriteSize > 0) {
                stackMapTableLength = 6 + stackMapWriteSize;
            }
        }

        int attrLength = 2 +                    // attribute_name_index
                         4 +                    // attribute_length
                         2 +                    // max_stack
                         2 +                    // max_locals
                         4 +                    // code_length
                         itsCodeBufferTop +
                         2 +                    // exception_table_length
                         (itsExceptionTableTop * 8) +
                         2 +                    // attributes_count
                         lineNumberTableLength +
                         variableTableLength +
                         stackMapTableLength;

        if (attrLength > 65536) {
            // See http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html,
            // section 4.10, "The amount of code per non-native, non-abstract
            // method is limited to 65536 bytes...
            throw new ClassFileFormatException(
                "generated bytecode for method exceeds 64K limit.");
        }
        byte[] codeAttribute = new byte[attrLength];
        int index = 0;
        int codeAttrIndex = itsConstantPool.addUtf8("Code");
        index = putInt16(codeAttrIndex, codeAttribute, index);
        attrLength -= 6;                 // discount the attribute header
        index = putInt32(attrLength, codeAttribute, index);
        index = putInt16(itsMaxStack, codeAttribute, index);
        index = putInt16(itsMaxLocals, codeAttribute, index);
        index = putInt32(itsCodeBufferTop, codeAttribute, index);
        System.arraycopy(itsCodeBuffer, 0, codeAttribute, index,
                         itsCodeBufferTop);
        index += itsCodeBufferTop;

        if (itsExceptionTableTop > 0) {
            index = putInt16(itsExceptionTableTop, codeAttribute, index);
            for (int i = 0; i < itsExceptionTableTop; i++) {
                ExceptionTableEntry ete = itsExceptionTable[i];
                short startPC = (short)getLabelPC(ete.itsStartLabel);
                short endPC = (short)getLabelPC(ete.itsEndLabel);
                short handlerPC = (short)getLabelPC(ete.itsHandlerLabel);
                short catchType = ete.itsCatchType;
                if (startPC == -1)
                    throw new IllegalStateException("start label not defined");
                if (endPC == -1)
                    throw new IllegalStateException("end label not defined");
                if (handlerPC == -1)
                    throw new IllegalStateException(
                        "handler label not defined");

                index = putInt16(startPC, codeAttribute, index);
                index = putInt16(endPC, codeAttribute, index);
                index = putInt16(handlerPC, codeAttribute, index);
                index = putInt16(catchType, codeAttribute, index);
            }
        }
        else {
            // write 0 as exception table length
            index = putInt16(0, codeAttribute, index);
        }

        int attributeCount = 0;
        if (itsLineNumberTable != null)
            attributeCount++;
        if (itsVarDescriptors != null)
            attributeCount++;
        if (stackMapTableLength > 0) {
            attributeCount++;
        }
        index = putInt16(attributeCount, codeAttribute, index);

        if (itsLineNumberTable != null) {
            int lineNumberTableAttrIndex
                    = itsConstantPool.addUtf8("LineNumberTable");
            index = putInt16(lineNumberTableAttrIndex, codeAttribute, index);
            int tableAttrLength = 2 + (itsLineNumberTableTop * 4);
            index = putInt32(tableAttrLength, codeAttribute, index);
            index = putInt16(itsLineNumberTableTop, codeAttribute, index);
            for (int i = 0; i < itsLineNumberTableTop; i++) {
                index = putInt32(itsLineNumberTable[i], codeAttribute, index);
            }
        }

        if (itsVarDescriptors != null) {
            int variableTableAttrIndex
                    = itsConstantPool.addUtf8("LocalVariableTable");
            index = putInt16(variableTableAttrIndex, codeAttribute, index);
            int varCount = itsVarDescriptors.size();
            int tableAttrLength = 2 + (varCount * 10);
            index = putInt32(tableAttrLength, codeAttribute, index);
            index = putInt16(varCount, codeAttribute, index);
            for (int i = 0; i < varCount; i++) {
                int[] chunk = (int[])itsVarDescriptors.get(i);
                int nameIndex       = chunk[0];
                int descriptorIndex = chunk[1];
                int startPC         = chunk[2];
                int register        = chunk[3];
                int length = itsCodeBufferTop - startPC;

                index = putInt16(startPC, codeAttribute, index);
                index = putInt16(length, codeAttribute, index);
                index = putInt16(nameIndex, codeAttribute, index);
                index = putInt16(descriptorIndex, codeAttribute, index);
                index = putInt16(register, codeAttribute, index);
            }
        }

        if (stackMapTableLength > 0) {
            int stackMapTableAttrIndex =
                    itsConstantPool.addUtf8("StackMapTable");
            int start = index;
            index = putInt16(stackMapTableAttrIndex, codeAttribute, index);
            index = stackMap.write(codeAttribute, index);
        }

        itsCurrentMethod.setCodeAttribute(codeAttribute);

        itsExceptionTable = null;
        itsExceptionTableTop = 0;
        itsLineNumberTableTop = 0;
        itsCodeBufferTop = 0;
        itsCurrentMethod = null;
        itsMaxStack = 0;
        itsStackTop = 0;
        itsLabelTableTop = 0;
        itsFixupTableTop = 0;
        itsVarDescriptors = null;
        itsSuperBlockStarts = null;
        itsSuperBlockStartsTop = 0;
        itsJumpFroms = null;
    }

    /**
     * Add the single-byte opcode to the current method.
     *
     * @param theOpCode the opcode of the bytecode
     */
    public void add(int theOpCode) {
        if (opcodeCount(theOpCode) != 0)
            throw new IllegalArgumentException("Unexpected operands");
        int newStack = itsStackTop + stackChange(theOpCode);
        if (newStack < 0 || Short.MAX_VALUE < newStack) badStack(newStack);
        if (DEBUGCODE)
            System.out.println("Add " + bytecodeStr(theOpCode));
        addToCodeBuffer(theOpCode);
        itsStackTop = (short)newStack;
        if (newStack > itsMaxStack) itsMaxStack = (short)newStack;
        if (DEBUGSTACK) {
            System.out.println("After "+bytecodeStr(theOpCode)
                               +" stack = "+itsStackTop);
        }
        if (theOpCode == ByteCode.ATHROW) {
            addSuperBlockStart(itsCodeBufferTop);
        }
    }

    /**
     * Add a single-operand opcode to the current method.
     *
     * @param theOpCode the opcode of the bytecode
     * @param theOperand the operand of the bytecode
     */
    public void add(int theOpCode, int theOperand) {
        if (DEBUGCODE) {
            System.out.println("Add "+bytecodeStr(theOpCode)
                               +", "+Integer.toHexString(theOperand));
        }
        int newStack = itsStackTop + stackChange(theOpCode);
        if (newStack < 0 || Short.MAX_VALUE < newStack) badStack(newStack);

        switch (theOpCode) {
            case ByteCode.GOTO :
                // This is necessary because dead code is seemingly being
                // generated and Sun's verifier is expecting type state to be
                // placed even at dead blocks of code.
                addSuperBlockStart(itsCodeBufferTop + 3);
                // fallthru...
            case ByteCode.IFEQ :
            case ByteCode.IFNE :
            case ByteCode.IFLT :
            case ByteCode.IFGE :
            case ByteCode.IFGT :
            case ByteCode.IFLE :
            case ByteCode.IF_ICMPEQ :
            case ByteCode.IF_ICMPNE :
            case ByteCode.IF_ICMPLT :
            case ByteCode.IF_ICMPGE :
            case ByteCode.IF_ICMPGT :
            case ByteCode.IF_ICMPLE :
            case ByteCode.IF_ACMPEQ :
            case ByteCode.IF_ACMPNE :
            case ByteCode.JSR :
            case ByteCode.IFNULL :
            case ByteCode.IFNONNULL : {
                    if ((theOperand & 0x80000000) != 0x80000000) {
                        if ((theOperand < 0) || (theOperand > 65535))
                            throw new IllegalArgumentException(
                                "Bad label for branch");
                    }
                    int branchPC = itsCodeBufferTop;
                    addToCodeBuffer(theOpCode);
                    if ((theOperand & 0x80000000) != 0x80000000) {
                            // hard displacement
                        addToCodeInt16(theOperand);
                        int target = theOperand + branchPC;
                        addSuperBlockStart(target);
                        itsJumpFroms.put(target, branchPC);
                    }
                    else {  // a label
                        int targetPC = getLabelPC(theOperand);
                        if (DEBUGLABELS) {
                            int theLabel = theOperand & 0x7FFFFFFF;
                            System.out.println("Fixing branch to " +
                                               theLabel + " at " + targetPC +
                                               " from " + branchPC);
                        }
                        if (targetPC != -1) {
                            int offset = targetPC - branchPC;
                            addToCodeInt16(offset);
                            addSuperBlockStart(targetPC);
                            itsJumpFroms.put(targetPC, branchPC);
                        }
                        else {
                            addLabelFixup(theOperand, branchPC + 1);
                            addToCodeInt16(0);
                        }
                    }
                }
                break;

            case ByteCode.BIPUSH :
                if ((byte)theOperand != theOperand)
                    throw new IllegalArgumentException("out of range byte");
                addToCodeBuffer(theOpCode);
                addToCodeBuffer((byte)theOperand);
                break;

            case ByteCode.SIPUSH :
                if ((short)theOperand != theOperand)
                    throw new IllegalArgumentException("out of range short");
                addToCodeBuffer(theOpCode);
                   addToCodeInt16(theOperand);
                break;

            case ByteCode.NEWARRAY :
                if (!(0 <= theOperand && theOperand < 256))
                    throw new IllegalArgumentException("out of range index");
                addToCodeBuffer(theOpCode);
                addToCodeBuffer(theOperand);
                break;

            case ByteCode.GETFIELD :
            case ByteCode.PUTFIELD :
                if (!(0 <= theOperand && theOperand < 65536))
                    throw new IllegalArgumentException("out of range field");
                addToCodeBuffer(theOpCode);
                addToCodeInt16(theOperand);
                break;

            case ByteCode.LDC :
            case ByteCode.LDC_W :
            case ByteCode.LDC2_W :
                if (!(0 <= theOperand && theOperand < 65536))
                    throw new IllegalArgumentException("out of range index");
                if (theOperand >= 256
                    || theOpCode == ByteCode.LDC_W
                    || theOpCode == ByteCode.LDC2_W)
                {
                    if (theOpCode == ByteCode.LDC) {
                        addToCodeBuffer(ByteCode.LDC_W);
                    } else {
                        addToCodeBuffer(theOpCode);
                    }
                    addToCodeInt16(theOperand);
                } else {
                    addToCodeBuffer(theOpCode);
                    addToCodeBuffer(theOperand);
                }
                break;

            case ByteCode.RET :
            case ByteCode.ILOAD :
            case ByteCode.LLOAD :
            case ByteCode.FLOAD :
            case ByteCode.DLOAD :
            case ByteCode.ALOAD :
            case ByteCode.ISTORE :
            case ByteCode.LSTORE :
            case ByteCode.FSTORE :
            case ByteCode.DSTORE :
            case ByteCode.ASTORE :
                if (!(0 <= theOperand && theOperand < 65536))
                    throw new ClassFileFormatException("out of range variable");
                if (theOperand >= 256) {
                    addToCodeBuffer(ByteCode.WIDE);
                    addToCodeBuffer(theOpCode);
                    addToCodeInt16(theOperand);
                }
                else {
                    addToCodeBuffer(theOpCode);
                    addToCodeBuffer(theOperand);
                }
                break;

            default :
                throw new IllegalArgumentException(
                    "Unexpected opcode for 1 operand");
        }

        itsStackTop = (short)newStack;
        if (newStack > itsMaxStack) itsMaxStack = (short)newStack;
        if (DEBUGSTACK) {
            System.out.println("After "+bytecodeStr(theOpCode)
                               +" stack = "+itsStackTop);
        }
    }

    /**
     * Generate the load constant bytecode for the given integer.
     *
     * @param k the constant
     */
    public void addLoadConstant(int k) {
        switch (k) {
            case 0: add(ByteCode.ICONST_0); break;
            case 1: add(ByteCode.ICONST_1); break;
            case 2: add(ByteCode.ICONST_2); break;
            case 3: add(ByteCode.ICONST_3); break;
            case 4: add(ByteCode.ICONST_4); break;
            case 5: add(ByteCode.ICONST_5); break;
            default:
                add(ByteCode.LDC, itsConstantPool.addConstant(k));
                break;
        }
    }

    /**
     * Generate the load constant bytecode for the given long.
     *
     * @param k the constant
     */
    public void addLoadConstant(long k) {
        add(ByteCode.LDC2_W, itsConstantPool.addConstant(k));
    }

    /**
     * Generate the load constant bytecode for the given float.
     *
     * @param k the constant
     */
    public void addLoadConstant(float k) {
        add(ByteCode.LDC, itsConstantPool.addConstant(k));
    }

    /**
     * Generate the load constant bytecode for the given double.
     *
     * @param k the constant
     */
    public void addLoadConstant(double k) {
        add(ByteCode.LDC2_W, itsConstantPool.addConstant(k));
    }

    /**
     * Generate the load constant bytecode for the given string.
     *
     * @param k the constant
     */
    public void addLoadConstant(String k) {
        add(ByteCode.LDC, itsConstantPool.addConstant(k));
    }

    /**
     * Add the given two-operand bytecode to the current method.
     *
     * @param theOpCode the opcode of the bytecode
     * @param theOperand1 the first operand of the bytecode
     * @param theOperand2 the second operand of the bytecode
     */
    public void add(int theOpCode, int theOperand1, int theOperand2) {
        if (DEBUGCODE) {
            System.out.println("Add "+bytecodeStr(theOpCode)
                               +", "+Integer.toHexString(theOperand1)
                               +", "+Integer.toHexString(theOperand2));
        }
        int newStack = itsStackTop + stackChange(theOpCode);
        if (newStack < 0 || Short.MAX_VALUE < newStack) badStack(newStack);

        if (theOpCode == ByteCode.IINC) {
            if (!(0 <= theOperand1 && theOperand1 < 65536))
                throw new ClassFileFormatException("out of range variable");
            if (!(0 <= theOperand2 && theOperand2 < 65536))
                throw new ClassFileFormatException("out of range increment");

            if (theOperand1 > 255 || theOperand2 < -128 || theOperand2 > 127) {
                addToCodeBuffer(ByteCode.WIDE);
                addToCodeBuffer(ByteCode.IINC);
                addToCodeInt16(theOperand1);
                addToCodeInt16(theOperand2);
            }
            else {
                addToCodeBuffer(ByteCode.IINC);
                addToCodeBuffer(theOperand1);
                addToCodeBuffer(theOperand2);
            }
        }
        else if (theOpCode == ByteCode.MULTIANEWARRAY) {
            if (!(0 <= theOperand1 && theOperand1 < 65536))
                throw new IllegalArgumentException("out of range index");
            if (!(0 <= theOperand2 && theOperand2 < 256))
                throw new IllegalArgumentException("out of range dimensions");

            addToCodeBuffer(ByteCode.MULTIANEWARRAY);
            addToCodeInt16(theOperand1);
            addToCodeBuffer(theOperand2);
        }
        else {
            throw new IllegalArgumentException(
                "Unexpected opcode for 2 operands");
        }
        itsStackTop = (short)newStack;
        if (newStack > itsMaxStack) itsMaxStack = (short)newStack;
        if (DEBUGSTACK) {
            System.out.println("After "+bytecodeStr(theOpCode)
                               +" stack = "+itsStackTop);
        }

    }

    public void add(int theOpCode, String className) {
        if (DEBUGCODE) {
            System.out.println("Add "+bytecodeStr(theOpCode)
                               +", "+className);
        }
        int newStack = itsStackTop + stackChange(theOpCode);
        if (newStack < 0 || Short.MAX_VALUE < newStack) badStack(newStack);
        switch (theOpCode) {
            case ByteCode.NEW :
            case ByteCode.ANEWARRAY :
            case ByteCode.CHECKCAST :
            case ByteCode.INSTANCEOF : {
                short classIndex = itsConstantPool.addClass(className);
                addToCodeBuffer(theOpCode);
                addToCodeInt16(classIndex);
            }
            break;

            default :
                throw new IllegalArgumentException(
                    "bad opcode for class reference");
        }
        itsStackTop = (short)newStack;
        if (newStack > itsMaxStack) itsMaxStack = (short)newStack;
        if (DEBUGSTACK) {
            System.out.println("After "+bytecodeStr(theOpCode)
                               +" stack = "+itsStackTop);
        }
    }


    public void add(int theOpCode, String className, String fieldName,
                    String fieldType)
    {
        if (DEBUGCODE) {
            System.out.println("Add "+bytecodeStr(theOpCode)
                               +", "+className+", "+fieldName+", "+fieldType);
        }
        int newStack = itsStackTop + stackChange(theOpCode);
        char fieldTypeChar = fieldType.charAt(0);
        int fieldSize = (fieldTypeChar == 'J' || fieldTypeChar == 'D')
                        ? 2 : 1;
        switch (theOpCode) {
            case ByteCode.GETFIELD :
            case ByteCode.GETSTATIC :
                newStack += fieldSize;
                break;
            case ByteCode.PUTSTATIC :
            case ByteCode.PUTFIELD :
                newStack -= fieldSize;
                break;
            default :
                throw new IllegalArgumentException(
                    "bad opcode for field reference");
        }
        if (newStack < 0 || Short.MAX_VALUE < newStack) badStack(newStack);
        short fieldRefIndex = itsConstantPool.addFieldRef(className,
                                             fieldName, fieldType);
        addToCodeBuffer(theOpCode);
        addToCodeInt16(fieldRefIndex);

        itsStackTop = (short)newStack;
        if (newStack > itsMaxStack) itsMaxStack = (short)newStack;
        if (DEBUGSTACK) {
            System.out.println("After "+bytecodeStr(theOpCode)
                               +" stack = "+itsStackTop);
        }
    }

    public void addInvoke(int theOpCode, String className, String methodName,
                          String methodType)
    {
        if (DEBUGCODE) {
            System.out.println("Add "+bytecodeStr(theOpCode)
                               +", "+className+", "+methodName+", "
                               +methodType);
        }
        int parameterInfo = sizeOfParameters(methodType);
        int parameterCount = parameterInfo >>> 16;
        int stackDiff = (short)parameterInfo;

        int newStack = itsStackTop + stackDiff;
        newStack += stackChange(theOpCode);     // adjusts for 'this'
        if (newStack < 0 || Short.MAX_VALUE < newStack) badStack(newStack);

        switch (theOpCode) {
            case ByteCode.INVOKEVIRTUAL :
            case ByteCode.INVOKESPECIAL :
            case ByteCode.INVOKESTATIC :
            case ByteCode.INVOKEINTERFACE : {
                    addToCodeBuffer(theOpCode);
                    if (theOpCode == ByteCode.INVOKEINTERFACE) {
                        short ifMethodRefIndex
                                    = itsConstantPool.addInterfaceMethodRef(
                                               className, methodName,
                                               methodType);
                        addToCodeInt16(ifMethodRefIndex);
                        addToCodeBuffer(parameterCount + 1);
                        addToCodeBuffer(0);
                    }
                    else {
                        short methodRefIndex = itsConstantPool.addMethodRef(
                                               className, methodName,
                                               methodType);
                        addToCodeInt16(methodRefIndex);
                    }
                }
                break;

            default :
                throw new IllegalArgumentException(
                    "bad opcode for method reference");
        }
        itsStackTop = (short)newStack;
        if (newStack > itsMaxStack) itsMaxStack = (short)newStack;
        if (DEBUGSTACK) {
            System.out.println("After "+bytecodeStr(theOpCode)
                               +" stack = "+itsStackTop);
        }
    }

    /**
     * Generate code to load the given integer on stack.
     *
     * @param k the constant
     */
    public void addPush(int k)
    {
        if ((byte)k == k) {
            if (k == -1) {
                add(ByteCode.ICONST_M1);
            } else if (0 <= k && k <= 5) {
                add((byte)(ByteCode.ICONST_0 + k));
            } else {
                add(ByteCode.BIPUSH, (byte)k);
            }
        } else if ((short)k == k) {
            add(ByteCode.SIPUSH, (short)k);
        } else {
            addLoadConstant(k);
        }
    }

    public void addPush(boolean k)
    {
        add(k ? ByteCode.ICONST_1 : ByteCode.ICONST_0);
    }

    /**
     * Generate code to load the given long on stack.
     *
     * @param k the constant
     */
    public void addPush(long k)
    {
        int ik = (int)k;
        if (ik == k) {
            addPush(ik);
            add(ByteCode.I2L);
        } else {
            addLoadConstant(k);
        }
    }

    /**
     * Generate code to load the given double on stack.
     *
     * @param k the constant
     */
    public void addPush(double k)
    {
        if (k == 0.0) {
            // zero
            add(ByteCode.DCONST_0);
            if (1.0 / k < 0) {
                // Negative zero
                add(ByteCode.DNEG);
            }
        } else if (k == 1.0 || k == -1.0) {
            add(ByteCode.DCONST_1);
            if (k < 0) {
                add(ByteCode.DNEG);
            }
        } else {
            addLoadConstant(k);
        }
    }

    /**
     * Generate the code to leave on stack the given string even if the
     * string encoding exeeds the class file limit for single string constant
     *
     * @param k the constant
     */
    public void addPush(String k) {
        int length = k.length();
        int limit = itsConstantPool.getUtfEncodingLimit(k, 0, length);
        if (limit == length) {
            addLoadConstant(k);
            return;
        }
        // Split string into picies fitting the UTF limit and generate code for
        // StringBuffer sb = new StringBuffer(length);
        // sb.append(loadConstant(piece_1));
        // ...
        // sb.append(loadConstant(piece_N));
        // sb.toString();
        final String SB = "java/lang/StringBuffer";
        add(ByteCode.NEW, SB);
        add(ByteCode.DUP);
        addPush(length);
        addInvoke(ByteCode.INVOKESPECIAL, SB, "<init>", "(I)V");
        int cursor = 0;
        for (;;) {
            add(ByteCode.DUP);
            String s = k.substring(cursor, limit);
            addLoadConstant(s);
            addInvoke(ByteCode.INVOKEVIRTUAL, SB, "append",
                      "(Ljava/lang/String;)Ljava/lang/StringBuffer;");
            add(ByteCode.POP);
            if (limit == length) {
                break;
            }
            cursor = limit;
            limit = itsConstantPool.getUtfEncodingLimit(k, limit, length);
        }
        addInvoke(ByteCode.INVOKEVIRTUAL, SB, "toString",
                  "()Ljava/lang/String;");
    }

    /**
     * Check if k fits limit on string constant size imposed by class file
     * format.
     *
     * @param k the string constant
     */
    public boolean isUnderStringSizeLimit(String k)
    {
        return itsConstantPool.isUnderUtfEncodingLimit(k);
    }

    /**
     * Store integer from stack top into the given local.
     *
     * @param local number of local register
     */
    public void addIStore(int local)
    {
        xop(ByteCode.ISTORE_0, ByteCode.ISTORE, local);
    }

    /**
     * Store long from stack top into the given local.
     *
     * @param local number of local register
     */
    public void addLStore(int local)
    {
        xop(ByteCode.LSTORE_0, ByteCode.LSTORE, local);
    }

    /**
     * Store float from stack top into the given local.
     *
     * @param local number of local register
     */
    public void addFStore(int local)
    {
        xop(ByteCode.FSTORE_0, ByteCode.FSTORE, local);
    }

    /**
     * Store double from stack top into the given local.
     *
     * @param local number of local register
     */
    public void addDStore(int local)
    {
        xop(ByteCode.DSTORE_0, ByteCode.DSTORE, local);
    }

    /**
     * Store object from stack top into the given local.
     *
     * @param local number of local register
     */
    public void addAStore(int local)
    {
        xop(ByteCode.ASTORE_0, ByteCode.ASTORE, local);
    }

    /**
     * Load integer from the given local into stack.
     *
     * @param local number of local register
     */
    public void addILoad(int local)
    {
        xop(ByteCode.ILOAD_0, ByteCode.ILOAD, local);
    }

    /**
     * Load long from the given local into stack.
     *
     * @param local number of local register
     */
    public void addLLoad(int local)
    {
        xop(ByteCode.LLOAD_0, ByteCode.LLOAD, local);
    }

    /**
     * Load float from the given local into stack.
     *
     * @param local number of local register
     */
    public void addFLoad(int local)
    {
        xop(ByteCode.FLOAD_0, ByteCode.FLOAD, local);
    }

    /**
     * Load double from the given local into stack.
     *
     * @param local number of local register
     */
    public void addDLoad(int local)
    {
        xop(ByteCode.DLOAD_0, ByteCode.DLOAD, local);
    }

    /**
     * Load object from the given local into stack.
     *
     * @param local number of local register
     */
    public void addALoad(int local)
    {
        xop(ByteCode.ALOAD_0, ByteCode.ALOAD, local);
    }

    /**
     * Load "this" into stack.
     */
    public void addLoadThis()
    {
        add(ByteCode.ALOAD_0);
    }

    private void xop(int shortOp, int op, int local)
    {
        switch (local) {
          case 0:
            add(shortOp);
            break;
          case 1:
            add(shortOp + 1);
            break;
          case 2:
            add(shortOp + 2);
            break;
          case 3:
            add(shortOp + 3);
            break;
          default:
            add(op, local);
        }
    }

    public int addTableSwitch(int low, int high)
    {
        if (DEBUGCODE) {
            System.out.println("Add "+bytecodeStr(ByteCode.TABLESWITCH)
                               +" "+low+" "+high);
        }
        if (low > high)
            throw new ClassFileFormatException("Bad bounds: "+low+' '+ high);

        int newStack = itsStackTop + stackChange(ByteCode.TABLESWITCH);
        if (newStack < 0 || Short.MAX_VALUE < newStack) badStack(newStack);

        int entryCount = high - low + 1;
        int padSize = 3 & ~itsCodeBufferTop; // == 3 - itsCodeBufferTop % 4

        int N = addReservedCodeSpace(1 + padSize + 4 * (1 + 2 + entryCount));
        int switchStart = N;
        itsCodeBuffer[N++] = (byte)ByteCode.TABLESWITCH;
        while (padSize != 0) {
            itsCodeBuffer[N++] = 0;
            --padSize;
        }
        N += 4; // skip default offset
        N = putInt32(low, itsCodeBuffer, N);
        putInt32(high, itsCodeBuffer, N);

        itsStackTop = (short)newStack;
        if (newStack > itsMaxStack) itsMaxStack = (short)newStack;
        if (DEBUGSTACK) {
            System.out.println("After "+bytecodeStr(ByteCode.TABLESWITCH)
                               +" stack = "+itsStackTop);
        }

        return switchStart;
    }

    public final void markTableSwitchDefault(int switchStart)
    {
        addSuperBlockStart(itsCodeBufferTop);
        itsJumpFroms.put(itsCodeBufferTop, switchStart);
        setTableSwitchJump(switchStart, -1, itsCodeBufferTop);
    }

    public final void markTableSwitchCase(int switchStart, int caseIndex)
    {
        addSuperBlockStart(itsCodeBufferTop);
        itsJumpFroms.put(itsCodeBufferTop, switchStart);
        setTableSwitchJump(switchStart, caseIndex, itsCodeBufferTop);
    }

    public final void markTableSwitchCase(int switchStart, int caseIndex,
                                          int stackTop)
    {
        if (!(0 <= stackTop && stackTop <= itsMaxStack))
            throw new IllegalArgumentException("Bad stack index: "+stackTop);
        itsStackTop = (short)stackTop;
        addSuperBlockStart(itsCodeBufferTop);
        itsJumpFroms.put(itsCodeBufferTop, switchStart);
        setTableSwitchJump(switchStart, caseIndex, itsCodeBufferTop);
    }

    /**
     * Set a jump case for a tableswitch instruction. The jump target should
     * be marked as a super block start for stack map generation.
     */
    public void setTableSwitchJump(int switchStart, int caseIndex,
                                   int jumpTarget)
    {
        if (!(0 <= jumpTarget && jumpTarget <= itsCodeBufferTop))
            throw new IllegalArgumentException("Bad jump target: "+jumpTarget);
        if (!(caseIndex >= -1))
            throw new IllegalArgumentException("Bad case index: "+caseIndex);

        int padSize = 3 & ~switchStart; // == 3 - switchStart % 4
        int caseOffset;
        if (caseIndex < 0) {
            // default label
            caseOffset = switchStart + 1 + padSize;
        } else {
            caseOffset = switchStart + 1 + padSize + 4 * (3 + caseIndex);
        }
        if (!(0 <= switchStart
              && switchStart <= itsCodeBufferTop - 4 * 4 - padSize - 1))
        {
            throw new IllegalArgumentException(
                switchStart+" is outside a possible range of tableswitch"
                +" in already generated code");
        }
        if ((0xFF & itsCodeBuffer[switchStart]) != ByteCode.TABLESWITCH) {
            throw new IllegalArgumentException(
                switchStart+" is not offset of tableswitch statement");
        }
        if (!(0 <= caseOffset && caseOffset + 4 <= itsCodeBufferTop)) {
            // caseIndex >= -1 does not guarantee that caseOffset >= 0 due
            // to a possible overflow.
            throw new ClassFileFormatException(
                "Too big case index: "+caseIndex);
        }
        // ALERT: perhaps check against case bounds?
        putInt32(jumpTarget - switchStart, itsCodeBuffer, caseOffset);
    }

    public int acquireLabel()
    {
        int top = itsLabelTableTop;
        if (itsLabelTable == null || top == itsLabelTable.length) {
            if (itsLabelTable == null) {
                itsLabelTable = new int[MIN_LABEL_TABLE_SIZE];
            }else {
                int[] tmp = new int[itsLabelTable.length * 2];
                System.arraycopy(itsLabelTable, 0, tmp, 0, top);
                itsLabelTable = tmp;
            }
        }
        itsLabelTableTop = top + 1;
        itsLabelTable[top] = -1;
        return top | 0x80000000;
    }

    public void markLabel(int label)
    {
        if (!(label < 0))
            throw new IllegalArgumentException("Bad label, no biscuit");

        label &= 0x7FFFFFFF;
        if (label > itsLabelTableTop)
            throw new IllegalArgumentException("Bad label");

        if (itsLabelTable[label] != -1) {
            throw new IllegalStateException("Can only mark label once");
        }

        itsLabelTable[label] = itsCodeBufferTop;
    }

    public void markLabel(int label, short stackTop)
    {
        markLabel(label);
        itsStackTop = stackTop;
    }

    public void markHandler(int theLabel) {
        itsStackTop = 1;
        markLabel(theLabel);
    }

    public int getLabelPC(int label)
    {
        if (!(label < 0))
            throw new IllegalArgumentException("Bad label, no biscuit");
        label &= 0x7FFFFFFF;
        if (!(label < itsLabelTableTop))
            throw new IllegalArgumentException("Bad label");
        return itsLabelTable[label];
    }

    private void addLabelFixup(int label, int fixupSite)
    {
        if (!(label < 0))
            throw new IllegalArgumentException("Bad label, no biscuit");
        label &= 0x7FFFFFFF;
        if (!(label < itsLabelTableTop))
            throw new IllegalArgumentException("Bad label");
        int top = itsFixupTableTop;
        if (itsFixupTable == null || top == itsFixupTable.length) {
            if (itsFixupTable == null) {
                itsFixupTable = new long[MIN_FIXUP_TABLE_SIZE];
            }else {
                long[] tmp = new long[itsFixupTable.length * 2];
                System.arraycopy(itsFixupTable, 0, tmp, 0, top);
                itsFixupTable = tmp;
            }
        }
        itsFixupTableTop = top + 1;
        itsFixupTable[top] = ((long)label << 32) | fixupSite;
    }

    private  void fixLabelGotos()
    {
        byte[] codeBuffer = itsCodeBuffer;
        for (int i = 0; i < itsFixupTableTop; i++) {
            long fixup = itsFixupTable[i];
            int label = (int)(fixup >> 32);
            int fixupSite = (int)fixup;
            int pc = itsLabelTable[label];
            if (pc == -1) {
                // Unlocated label
                throw new RuntimeException();
            }
            // -1 to get delta from instruction start
            addSuperBlockStart(pc);
            itsJumpFroms.put(pc, fixupSite - 1);
            int offset = pc - (fixupSite - 1);
            if ((short)offset != offset) {
                throw new ClassFileFormatException
                    ("Program too complex: too big jump offset");
            }
            codeBuffer[fixupSite] = (byte)(offset >> 8);
            codeBuffer[fixupSite + 1] = (byte)offset;
        }
        itsFixupTableTop = 0;
    }

    /**
     * Get the current offset into the code of the current method.
     *
     * @return an integer representing the offset
     */
    public int getCurrentCodeOffset() {
        return itsCodeBufferTop;
    }

    public short getStackTop() {
        return itsStackTop;
    }

    public void setStackTop(short n) {
        itsStackTop = n;
    }

    public void adjustStackTop(int delta) {
        int newStack = itsStackTop + delta;
        if (newStack < 0 || Short.MAX_VALUE < newStack) badStack(newStack);
        itsStackTop = (short)newStack;
        if (newStack > itsMaxStack) itsMaxStack = (short)newStack;
        if (DEBUGSTACK) {
            System.out.println("After "+"adjustStackTop("+delta+")"
                               +" stack = "+itsStackTop);
        }
    }

    private void addToCodeBuffer(int b)
    {
        int N = addReservedCodeSpace(1);
        itsCodeBuffer[N] = (byte)b;
    }

    private void addToCodeInt16(int value)
    {
        int N = addReservedCodeSpace(2);
        putInt16(value, itsCodeBuffer, N);
    }

    private int addReservedCodeSpace(int size)
    {
        if (itsCurrentMethod == null)
            throw new IllegalArgumentException("No method to add to");
        int oldTop = itsCodeBufferTop;
        int newTop = oldTop + size;
        if (newTop > itsCodeBuffer.length) {
            int newSize = itsCodeBuffer.length * 2;
            if (newTop > newSize) { newSize = newTop; }
            byte[] tmp = new byte[newSize];
            System.arraycopy(itsCodeBuffer, 0, tmp, 0, oldTop);
            itsCodeBuffer = tmp;
        }
        itsCodeBufferTop = newTop;
        return oldTop;
    }

    public void addExceptionHandler(int startLabel, int endLabel,
                                    int handlerLabel, String catchClassName)
    {
        if ((startLabel & 0x80000000) != 0x80000000)
            throw new IllegalArgumentException("Bad startLabel");
        if ((endLabel & 0x80000000) != 0x80000000)
            throw new IllegalArgumentException("Bad endLabel");
        if ((handlerLabel & 0x80000000) != 0x80000000)
            throw new IllegalArgumentException("Bad handlerLabel");

        /*
         * If catchClassName is null, use 0 for the catch_type_index; which
         * means catch everything.  (Even when the verifier has let you throw
         * something other than a Throwable.)
         */
        short catch_type_index = (catchClassName == null)
                                 ? 0
                                 : itsConstantPool.addClass(catchClassName);
        ExceptionTableEntry newEntry = new ExceptionTableEntry(
                                           startLabel,
                                           endLabel,
                                           handlerLabel,
                                           catch_type_index);
        int N = itsExceptionTableTop;
        if (N == 0) {
            itsExceptionTable = new ExceptionTableEntry[ExceptionTableSize];
        } else if (N == itsExceptionTable.length) {
            ExceptionTableEntry[] tmp = new ExceptionTableEntry[N * 2];
            System.arraycopy(itsExceptionTable, 0, tmp, 0, N);
            itsExceptionTable = tmp;
        }
        itsExceptionTable[N] = newEntry;
        itsExceptionTableTop = N + 1;

    }

    public void addLineNumberEntry(short lineNumber) {
        if (itsCurrentMethod == null)
            throw new IllegalArgumentException("No method to stop");
        int N = itsLineNumberTableTop;
        if (N == 0) {
            itsLineNumberTable = new int[LineNumberTableSize];
        } else if (N == itsLineNumberTable.length) {
            int[] tmp = new int[N * 2];
            System.arraycopy(itsLineNumberTable, 0, tmp, 0, N);
            itsLineNumberTable = tmp;
        }
        itsLineNumberTable[N] = (itsCodeBufferTop << 16) + lineNumber;
        itsLineNumberTableTop = N + 1;
    }

    /**
     * A stack map table is a code attribute introduced in Java 6 that
     * gives type information at key points in the method body (namely, at
     * the beginning of each super block after the first). Each frame of a
     * stack map table contains the state of local variable and operand stack
     * for a given super block.
     */
    final class StackMapTable {
        StackMapTable() {
            superBlocks = null;
            locals = stack = null;
            workList = null;
            rawStackMap = null;
            localsTop = 0;
            stackTop = 0;
            workListTop = 0;
            rawStackMapTop = 0;
            wide = false;
        }

        void generate() {
            superBlocks = new SuperBlock[itsSuperBlockStartsTop];
            int[] initialLocals = createInitialLocals();

            for (int i = 0; i < itsSuperBlockStartsTop; i++) {
                int start = itsSuperBlockStarts[i];
                int end;
                if (i == itsSuperBlockStartsTop - 1) {
                    end = itsCodeBufferTop;
                } else {
                    end = itsSuperBlockStarts[i + 1];
                }
                superBlocks[i] = new SuperBlock(i, start, end, initialLocals);
            }

            if (DEBUGSTACKMAP) {
                System.out.println("super blocks: ");
                for (int i = 0;
                     i < superBlocks.length && superBlocks[i] != null; i++) {
                    System.out.println("sb " + i + ": [" +
                                       superBlocks[i].getStart() + ", " +
                                       superBlocks[i].getEnd() + ")");
                }
            }

            superBlockDeps = getSuperBlockDependencies();

            verify();

            if (DEBUGSTACKMAP) {
                System.out.println("type information:");
                for (int i = 0; i < superBlocks.length; i++) {
                    SuperBlock sb = superBlocks[i];
                    System.out.println("sb " + i + ":");
                    TypeInfo.print(sb.getLocals(), sb.getStack(),
                                   itsConstantPool);
                }
            }
        }

        private SuperBlock getSuperBlockFromOffset(int offset) {
            for (int i = 0; i < superBlocks.length; i++) {
                SuperBlock sb = superBlocks[i];
                if (sb == null) {
                    break;
                } else if (offset >= sb.getStart() && offset < sb.getEnd()) {
                    return sb;
                }
            }
            throw new IllegalArgumentException("bad offset: " + offset);
        }

        /**
         * Determine whether or not an opcode is an actual end to a super
         * block. This includes any returns or unconditional jumps.
         */
        private boolean isSuperBlockEnd(int opcode) {
            switch (opcode) {
                case ByteCode.ARETURN:
                case ByteCode.FRETURN:
                case ByteCode.IRETURN:
                case ByteCode.LRETURN:
                case ByteCode.RETURN:
                case ByteCode.ATHROW:
                case ByteCode.GOTO:
                case ByteCode.GOTO_W:
                case ByteCode.TABLESWITCH:
                case ByteCode.LOOKUPSWITCH:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Calculate partial dependencies for super blocks.
         *
         * This is used as a workaround for dead code that is generated. Only
         * one dependency per super block is given.
         */
        private SuperBlock[] getSuperBlockDependencies() {
            SuperBlock[] deps = new SuperBlock[superBlocks.length];

            for (int i = 0; i < itsExceptionTableTop; i++) {
                ExceptionTableEntry ete = itsExceptionTable[i];
                short startPC = (short) getLabelPC(ete.itsStartLabel);
                short handlerPC = (short) getLabelPC(ete.itsHandlerLabel);
                SuperBlock handlerSB = getSuperBlockFromOffset(handlerPC);
                SuperBlock dep = getSuperBlockFromOffset(startPC);
                deps[handlerSB.getIndex()] = dep;
            }
            int[] targetPCs = itsJumpFroms.getKeys();
            for (int i = 0; i < targetPCs.length; i++) {
                int targetPC = targetPCs[i];
                int branchPC = itsJumpFroms.getInt(targetPC, -1);
                SuperBlock branchSB = getSuperBlockFromOffset(branchPC);
                SuperBlock targetSB = getSuperBlockFromOffset(targetPC);
                deps[targetSB.getIndex()] = branchSB;
            }

            return deps;
        }

        /**
         * Get the target super block of a branch instruction.
         *
         * @param bci the index of the branch instruction in the code buffer
         */
        private SuperBlock getBranchTarget(int bci) {
            int target;
            if ((itsCodeBuffer[bci] & 0xFF) == ByteCode.GOTO_W) {
                target = bci + getOperand(bci + 1, 4);
            } else {
                target = bci + (short) getOperand(bci + 1, 2);
            }
            return getSuperBlockFromOffset(target);
        }

        /**
         * Determine whether or not an opcode is a conditional or unconditional
         * jump.
         */
        private boolean isBranch(int opcode) {
            switch (opcode) {
                case ByteCode.GOTO:
                case ByteCode.GOTO_W:
                case ByteCode.IFEQ:
                case ByteCode.IFGE:
                case ByteCode.IFGT:
                case ByteCode.IFLE:
                case ByteCode.IFLT:
                case ByteCode.IFNE:
                case ByteCode.IFNONNULL:
                case ByteCode.IFNULL:
                case ByteCode.IF_ACMPEQ:
                case ByteCode.IF_ACMPNE:
                case ByteCode.IF_ICMPEQ:
                case ByteCode.IF_ICMPGE:
                case ByteCode.IF_ICMPGT:
                case ByteCode.IF_ICMPLE:
                case ByteCode.IF_ICMPLT:
                case ByteCode.IF_ICMPNE:
                    return true;
                default:
                    return false;
            }
        }

        private int getOperand(int offset) {
            return getOperand(offset, 1);
        }

        /**
         * Extract a logical operand from the byte code.
         *
         * This is used, for example, to get branch offsets.
         */
        private int getOperand(int start, int size) {
            int result = 0;
            if (size > 4) {
                throw new IllegalArgumentException("bad operand size");
            }
            for (int i = 0; i < size; i++) {
                result = (result << 8) | (itsCodeBuffer[start + i] & 0xFF);
            }
            return result;
        }

        /**
         * Calculate initial local variable and op stack types for each super
         * block in the method.
         */
        private void verify() {
            int[] initialLocals = createInitialLocals();
            superBlocks[0].merge(initialLocals, initialLocals.length,
                                 new int[0], 0, itsConstantPool);

            // Start from the top of the method and queue up block dependencies
            // as they come along.
            workList = new SuperBlock[] { superBlocks[0] };
            workListTop = 1;
            executeWorkList();

            // Replace dead code with no-ops.
            for (int i = 0; i < superBlocks.length; i++) {
                SuperBlock sb = superBlocks[i];
                if (!sb.isInitialized()) {
                    killSuperBlock(sb);
                }
            }
            executeWorkList();
        }

        /**
         * Replace the contents of a super block with no-ops.
         *
         * The above description is not strictly true; the last instruction is
         * an athrow instruction. This technique is borrowed from ASM's
         * developer guide: http://asm.ow2.org/doc/developer-guide.html#deadcode
         *
         * The proposed algorithm fills a block with nop, ending it with an
         * athrow. The stack map generated would be empty locals with an
         * exception on the stack. In theory, it shouldn't matter what the
         * locals are, as long as the stack has an exception for the athrow bit.
         * However, it turns out that if the code being modified falls into an
         * exception handler, it causes problems. Therefore, if it does, then
         * we steal the locals from the exception block.
         *
         * If the block itself is an exception handler, we remove it from the
         * exception table to simplify block dependencies.
         */
        private void killSuperBlock(SuperBlock sb) {
            int[] locals = new int[0];
            int[] stack = new int[] { TypeInfo.OBJECT("java/lang/Throwable",
                                                      itsConstantPool) };

            // If the super block is handled by any exception handler, use its
            // locals as the killed block's locals. Ignore uninitialized
            // handlers, because they will also be killed and removed from the
            // exception table.
            for (int i = 0; i < itsExceptionTableTop; i++) {
                ExceptionTableEntry ete = itsExceptionTable[i];
                int eteStart = getLabelPC(ete.itsStartLabel);
                int eteEnd = getLabelPC(ete.itsEndLabel);
                int handlerPC = getLabelPC(ete.itsHandlerLabel);
                SuperBlock handlerSB = getSuperBlockFromOffset(handlerPC);
                if ((sb.getStart() > eteStart && sb.getStart() < eteEnd) ||
                    (eteStart > sb.getStart() && eteStart < sb.getEnd()) &&
                    handlerSB.isInitialized()) {
                    locals = handlerSB.getLocals();
                    break;
                }
            }

            // Remove any exception table entry whose handler is the killed
            // block. This removes block dependencies to make stack maps for
            // dead blocks easier to create.
            for (int i = 0; i < itsExceptionTableTop; i++) {
                ExceptionTableEntry ete = itsExceptionTable[i];
                int eteStart = getLabelPC(ete.itsStartLabel);
                if (eteStart == sb.getStart()) {
                    for (int j = i + 1; j < itsExceptionTableTop; j++) {
                        itsExceptionTable[j - 1] = itsExceptionTable[j];
                    }
                    itsExceptionTableTop--;
                    i--;
                }
            }

            sb.merge(locals, locals.length, stack, stack.length,
                     itsConstantPool);

            int end = sb.getEnd() - 1;
            itsCodeBuffer[end] = (byte) ByteCode.ATHROW;
            for (int bci = sb.getStart(); bci < end; bci++) {
                itsCodeBuffer[bci] = (byte) ByteCode.NOP;
            }
        }

        private void executeWorkList() {
            while (workListTop > 0) {
                SuperBlock work = workList[--workListTop];
                work.setInQueue(false);
                locals = work.getLocals();
                stack = work.getStack();
                localsTop = locals.length;
                stackTop = stack.length;
                executeBlock(work);
            }
        }

        /**
         * Simulate the local variable and op stack for a super block.
         */
        private void executeBlock(SuperBlock work) {
            int bc = 0;
            int next = 0;

            if (DEBUGSTACKMAP) {
                System.out.println("working on sb " + work.getIndex());
                System.out.println("initial type state:");
                TypeInfo.print(locals, localsTop, stack, stackTop,
                               itsConstantPool);
            }

            for (int bci = work.getStart(); bci < work.getEnd(); bci += next) {
                bc = itsCodeBuffer[bci] & 0xFF;
                next = execute(bci);

                // If we have a branch to some super block, we need to merge
                // the current state of the local table and op stack with what's
                // currently stored as the initial state of the super block. If
                // something actually changed, we need to add it to the work
                // list.
                if (isBranch(bc)) {
                    SuperBlock targetSB = getBranchTarget(bci);
                    if (DEBUGSTACKMAP) {
                        System.out.println("sb " + work.getIndex() +
                                           " points to sb " +
                                           targetSB.getIndex() +
                                           " (offset " + bci + " -> " +
                                           targetSB.getStart() + ")");
                        System.out.println("type state at " + bci + ":");
                        TypeInfo.print(locals, localsTop, stack, stackTop,
                                       itsConstantPool);
                    }
                    flowInto(targetSB);
                    if (DEBUGSTACKMAP) {
                        System.out.println("type state of " + targetSB +
                                           " after merge:");
                        TypeInfo.print(targetSB.getLocals(),
                                       targetSB.getStack(), itsConstantPool);
                    }
                } else if (bc == ByteCode.TABLESWITCH) {
                    int switchStart = bci + 1 + (3 & ~bci); // 3 - bci % 4
                    int defaultOffset = getOperand(switchStart, 4);
                    SuperBlock targetSB =
                            getSuperBlockFromOffset(bci + defaultOffset);
                    if (DEBUGSTACK) {
                        System.out.println("merging sb " + work.getIndex() +
                                           " with sb " + targetSB.getIndex());
                    }
                    flowInto(targetSB);
                    int low = getOperand(switchStart + 4, 4);
                    int high = getOperand(switchStart + 8, 4);
                    int numCases = high - low + 1;
                    int caseBase = switchStart + 12;
                    for (int i = 0; i < numCases; i++) {
                        int label = bci + getOperand(caseBase + 4 * i, 4);
                        targetSB = getSuperBlockFromOffset(label);
                        if (DEBUGSTACKMAP) {
                            System.out.println("merging sb " +
                                               work.getIndex() + " with sb " +
                                               targetSB.getIndex());
                        }
                        flowInto(targetSB);
                    }
                }

                for (int i = 0; i < itsExceptionTableTop; i++) {
                    ExceptionTableEntry ete = itsExceptionTable[i];
                    short startPC = (short) getLabelPC(ete.itsStartLabel);
                    short endPC = (short) getLabelPC(ete.itsEndLabel);
                    if (bci < startPC || bci >= endPC) {
                        continue;
                    }
                    short handlerPC =
                            (short) getLabelPC(ete.itsHandlerLabel);
                    SuperBlock sb = getSuperBlockFromOffset(handlerPC);
                    int exceptionType;

                    if (ete.itsCatchType == 0) {
                        exceptionType = TypeInfo.OBJECT(
                            itsConstantPool.addClass("java/lang/Throwable"));
                    } else {
                        exceptionType = TypeInfo.OBJECT(ete.itsCatchType);
                    }
                    sb.merge(locals, localsTop, new int[] { exceptionType }, 1,
                             itsConstantPool);
                    addToWorkList(sb);
                }
            }

            if (DEBUGSTACKMAP) {
                System.out.println("end of sb " + work.getIndex() + ":");
                TypeInfo.print(locals, localsTop, stack, stackTop,
                               itsConstantPool);
            }

            // Check the last instruction to see if it is a true end of a
            // super block (ie., if the instruction is a return). If it
            // isn't, we need to continue processing the next chunk.
            if (!isSuperBlockEnd(bc)) {
                int nextIndex = work.getIndex() + 1;
                if (nextIndex < superBlocks.length) {
                    if (DEBUGSTACKMAP) {
                        System.out.println("continuing from sb " +
                                           work.getIndex() + " into sb " +
                                           nextIndex);
                    }
                    flowInto(superBlocks[nextIndex]);
                }
            }
        }

        /**
         * Perform a merge of type state and add the super block to the work
         * list if the merge changed anything.
         */
        private void flowInto(SuperBlock sb) {
            if (sb.merge(locals, localsTop, stack, stackTop, itsConstantPool)) {
                addToWorkList(sb);
            }
        }

        private void addToWorkList(SuperBlock sb) {
            if (!sb.isInQueue()) {
                sb.setInQueue(true);
                sb.setInitialized(true);
                if (workListTop == workList.length) {
                    SuperBlock[] tmp = new SuperBlock[workListTop * 2];
                    System.arraycopy(workList, 0, tmp, 0, workListTop);
                    workList = tmp;
                }
                workList[workListTop++] = sb;
            }
        }

        /**
         * Execute a single byte code instruction.
         *
         * @param bci the index of the byte code instruction to execute
         * @return the length of the byte code instruction
         */
        private int execute(int bci) {
            int bc = itsCodeBuffer[bci] & 0xFF;
            int type, type2, index;
            int length = 0;
            long lType, lType2;
            String className;

            switch (bc) {
                case ByteCode.NOP:
                case ByteCode.IINC:
                case ByteCode.GOTO:
                case ByteCode.GOTO_W:
                    // No change
                    break;
                case ByteCode.CHECKCAST:
                    pop();
                    push(TypeInfo.OBJECT(getOperand(bci + 1, 2)));
                    break;
                case ByteCode.IASTORE: // pop; pop; pop
                case ByteCode.LASTORE:
                case ByteCode.FASTORE:
                case ByteCode.DASTORE:
                case ByteCode.AASTORE:
                case ByteCode.BASTORE:
                case ByteCode.CASTORE:
                case ByteCode.SASTORE:
                    pop();
                case ByteCode.PUTFIELD: // pop; pop
                case ByteCode.IF_ICMPEQ:
                case ByteCode.IF_ICMPNE:
                case ByteCode.IF_ICMPLT:
                case ByteCode.IF_ICMPGE:
                case ByteCode.IF_ICMPGT:
                case ByteCode.IF_ICMPLE:
                case ByteCode.IF_ACMPEQ:
                case ByteCode.IF_ACMPNE:
                    pop();
                case ByteCode.IFEQ: // pop
                case ByteCode.IFNE:
                case ByteCode.IFLT:
                case ByteCode.IFGE:
                case ByteCode.IFGT:
                case ByteCode.IFLE:
                case ByteCode.IFNULL:
                case ByteCode.IFNONNULL:
                case ByteCode.POP:
                case ByteCode.MONITORENTER:
                case ByteCode.MONITOREXIT:
                case ByteCode.PUTSTATIC:
                    pop();
                    break;
                case ByteCode.POP2:
                    pop2();
                    break;
                case ByteCode.ACONST_NULL:
                    push(TypeInfo.NULL);
                    break;
                case ByteCode.IALOAD: // pop; pop; push(INTEGER)
                case ByteCode.BALOAD:
                case ByteCode.CALOAD:
                case ByteCode.SALOAD:
                case ByteCode.IADD:
                case ByteCode.ISUB:
                case ByteCode.IMUL:
                case ByteCode.IDIV:
                case ByteCode.IREM:
                case ByteCode.ISHL:
                case ByteCode.ISHR:
                case ByteCode.IUSHR:
                case ByteCode.IAND:
                case ByteCode.IOR:
                case ByteCode.IXOR:
                case ByteCode.LCMP:
                case ByteCode.FCMPL:
                case ByteCode.FCMPG:
                case ByteCode.DCMPL:
                case ByteCode.DCMPG:
                    pop();
                case ByteCode.INEG: // pop; push(INTEGER)
                case ByteCode.L2I:
                case ByteCode.F2I:
                case ByteCode.D2I:
                case ByteCode.I2B:
                case ByteCode.I2C:
                case ByteCode.I2S:
                case ByteCode.ARRAYLENGTH:
                case ByteCode.INSTANCEOF:
                    pop();
                case ByteCode.ICONST_M1: // push(INTEGER)
                case ByteCode.ICONST_0:
                case ByteCode.ICONST_1:
                case ByteCode.ICONST_2:
                case ByteCode.ICONST_3:
                case ByteCode.ICONST_4:
                case ByteCode.ICONST_5:
                case ByteCode.ILOAD:
                case ByteCode.ILOAD_0:
                case ByteCode.ILOAD_1:
                case ByteCode.ILOAD_2:
                case ByteCode.ILOAD_3:
                case ByteCode.BIPUSH:
                case ByteCode.SIPUSH:
                    push(TypeInfo.INTEGER);
                    break;
                case ByteCode.LALOAD: // pop; pop; push(LONG)
                case ByteCode.LADD:
                case ByteCode.LSUB:
                case ByteCode.LMUL:
                case ByteCode.LDIV:
                case ByteCode.LREM:
                case ByteCode.LSHL:
                case ByteCode.LSHR:
                case ByteCode.LUSHR:
                case ByteCode.LAND:
                case ByteCode.LOR:
                case ByteCode.LXOR:
                    pop();
                case ByteCode.LNEG: // pop; push(LONG)
                case ByteCode.I2L:
                case ByteCode.F2L:
                case ByteCode.D2L:
                    pop();
                case ByteCode.LCONST_0: // push(LONG)
                case ByteCode.LCONST_1:
                case ByteCode.LLOAD:
                case ByteCode.LLOAD_0:
                case ByteCode.LLOAD_1:
                case ByteCode.LLOAD_2:
                case ByteCode.LLOAD_3:
                    push(TypeInfo.LONG);
                    break;
                case ByteCode.FALOAD: // pop; pop; push(FLOAT)
                case ByteCode.FADD:
                case ByteCode.FSUB:
                case ByteCode.FMUL:
                case ByteCode.FDIV:
                case ByteCode.FREM:
                    pop();
                case ByteCode.FNEG: // pop; push(FLOAT)
                case ByteCode.I2F:
                case ByteCode.L2F:
                case ByteCode.D2F:
                    pop();
                case ByteCode.FCONST_0: // push(FLOAT)
                case ByteCode.FCONST_1:
                case ByteCode.FCONST_2:
                case ByteCode.FLOAD:
                case ByteCode.FLOAD_0:
                case ByteCode.FLOAD_1:
                case ByteCode.FLOAD_2:
                case ByteCode.FLOAD_3:
                    push(TypeInfo.FLOAT);
                    break;
                case ByteCode.DALOAD: // pop; pop; push(DOUBLE)
                case ByteCode.DADD:
                case ByteCode.DSUB:
                case ByteCode.DMUL:
                case ByteCode.DDIV:
                case ByteCode.DREM:
                    pop();
                case ByteCode.DNEG: // pop; push(DOUBLE)
                case ByteCode.I2D:
                case ByteCode.L2D:
                case ByteCode.F2D:
                    pop();
                case ByteCode.DCONST_0: // push(DOUBLE)
                case ByteCode.DCONST_1:
                case ByteCode.DLOAD:
                case ByteCode.DLOAD_0:
                case ByteCode.DLOAD_1:
                case ByteCode.DLOAD_2:
                case ByteCode.DLOAD_3:
                    push(TypeInfo.DOUBLE);
                    break;
                case ByteCode.ISTORE:
                    executeStore(getOperand(bci + 1, wide ? 2 : 1), TypeInfo.INTEGER);
                    break;
                case ByteCode.ISTORE_0:
                case ByteCode.ISTORE_1:
                case ByteCode.ISTORE_2:
                case ByteCode.ISTORE_3:
                    executeStore(bc - ByteCode.ISTORE_0, TypeInfo.INTEGER);
                    break;
                case ByteCode.LSTORE:
                    executeStore(getOperand(bci + 1, wide ? 2 : 1), TypeInfo.LONG);
                    break;
                case ByteCode.LSTORE_0:
                case ByteCode.LSTORE_1:
                case ByteCode.LSTORE_2:
                case ByteCode.LSTORE_3:
                    executeStore(bc - ByteCode.LSTORE_0, TypeInfo.LONG);
                    break;
                case ByteCode.FSTORE:
                    executeStore(getOperand(bci + 1, wide ? 2 : 1), TypeInfo.FLOAT);
                    break;
                case ByteCode.FSTORE_0:
                case ByteCode.FSTORE_1:
                case ByteCode.FSTORE_2:
                case ByteCode.FSTORE_3:
                    executeStore(bc - ByteCode.FSTORE_0, TypeInfo.FLOAT);
                    break;
                case ByteCode.DSTORE:
                    executeStore(getOperand(bci + 1, wide ? 2 : 1), TypeInfo.DOUBLE);
                    break;
                case ByteCode.DSTORE_0:
                case ByteCode.DSTORE_1:
                case ByteCode.DSTORE_2:
                case ByteCode.DSTORE_3:
                    executeStore(bc - ByteCode.DSTORE_0, TypeInfo.DOUBLE);
                    break;
                case ByteCode.ALOAD:
                    executeALoad(getOperand(bci + 1, wide ? 2 : 1));
                    break;
                case ByteCode.ALOAD_0:
                case ByteCode.ALOAD_1:
                case ByteCode.ALOAD_2:
                case ByteCode.ALOAD_3:
                    executeALoad(bc - ByteCode.ALOAD_0);
                    break;
                case ByteCode.ASTORE:
                    executeAStore(getOperand(bci + 1, wide ? 2 : 1));
                    break;
                case ByteCode.ASTORE_0:
                case ByteCode.ASTORE_1:
                case ByteCode.ASTORE_2:
                case ByteCode.ASTORE_3:
                    executeAStore(bc - ByteCode.ASTORE_0);
                    break;
                case ByteCode.IRETURN:
                case ByteCode.LRETURN:
                case ByteCode.FRETURN:
                case ByteCode.DRETURN:
                case ByteCode.ARETURN:
                case ByteCode.RETURN:
                    clearStack();
                    break;
                case ByteCode.ATHROW:
                    type = pop();
                    clearStack();
                    push(type);
                    break;
                case ByteCode.SWAP:
                    type = pop();
                    type2 = pop();
                    push(type);
                    push(type2);
                    break;
                case ByteCode.LDC:
                case ByteCode.LDC_W:
                case ByteCode.LDC2_W:
                    if (bc == ByteCode.LDC) {
                        index = getOperand(bci + 1);
                    } else {
                        index = getOperand(bci + 1, 2);
                    }
                    byte constType = itsConstantPool.getConstantType(index);
                    switch (constType) {
                        case ConstantPool.CONSTANT_Double:
                            push(TypeInfo.DOUBLE);
                            break;
                        case ConstantPool.CONSTANT_Float:
                            push(TypeInfo.FLOAT);
                            break;
                        case ConstantPool.CONSTANT_Long:
                            push(TypeInfo.LONG);
                            break;
                        case ConstantPool.CONSTANT_Integer:
                            push(TypeInfo.INTEGER);
                            break;
                        case ConstantPool.CONSTANT_String:
                            push(TypeInfo.OBJECT("java/lang/String",
                                                 itsConstantPool));
                            break;
                        default:
                            throw new IllegalArgumentException(
                                "bad const type " + constType);
                    }
                    break;
                case ByteCode.NEW:
                    push(TypeInfo.UNINITIALIZED_VARIABLE(bci));
                    break;
                case ByteCode.NEWARRAY:
                    pop();
                    char componentType =
                            arrayTypeToName(itsCodeBuffer[bci + 1]);
                    index = itsConstantPool.addClass("[" + componentType);
                    push(TypeInfo.OBJECT((short) index));
                    break;
                case ByteCode.ANEWARRAY:
                    index = getOperand(bci + 1, 2);
                    className = (String) itsConstantPool.getConstantData(index);
                    pop();
                    push(TypeInfo.OBJECT("[L" + className + ';',
                                         itsConstantPool));
                    break;
                case ByteCode.INVOKEVIRTUAL:
                case ByteCode.INVOKESPECIAL:
                case ByteCode.INVOKESTATIC:
                case ByteCode.INVOKEINTERFACE:
                    index = getOperand(bci + 1, 2);
                    FieldOrMethodRef m = (FieldOrMethodRef)
                            itsConstantPool.getConstantData(index);
                    String methodType = m.getType();
                    String methodName = m.getName();
                    int parameterCount = sizeOfParameters(methodType) >>> 16;
                    for (int i = 0; i < parameterCount; i++) {
                        pop();
                    }
                    if (bc != ByteCode.INVOKESTATIC) {
                        int instType = pop();
                        int tag = TypeInfo.getTag(instType);
                        if (tag == TypeInfo.UNINITIALIZED_VARIABLE(0) ||
                            tag == TypeInfo.UNINITIALIZED_THIS) {
                            if ("<init>".equals(methodName)) {
                                int newType =
                                        TypeInfo.OBJECT(itsThisClassIndex);
                                initializeTypeInfo(instType, newType);
                            } else {
                                throw new IllegalStateException("bad instance");
                            }
                        }
                    }
                    int rParen = methodType.indexOf(')');
                    String returnType = methodType.substring(rParen + 1);
                    returnType = descriptorToInternalName(returnType);
                    if (!returnType.equals("V")) {
                        push(TypeInfo.fromType(returnType, itsConstantPool));
                    }
                    break;
                case ByteCode.GETFIELD:
                    pop();
                case ByteCode.GETSTATIC:
                    index = getOperand(bci + 1, 2);
                    FieldOrMethodRef f = (FieldOrMethodRef)
                            itsConstantPool.getConstantData(index);
                    String fieldType = descriptorToInternalName(f.getType());
                    push(TypeInfo.fromType(fieldType, itsConstantPool));
                    break;
                case ByteCode.DUP:
                    type = pop();
                    push(type);
                    push(type);
                    break;
                case ByteCode.DUP_X1:
                    type = pop();
                    type2 = pop();
                    push(type);
                    push(type2);
                    push(type);
                    break;
                case ByteCode.DUP_X2:
                    type = pop();
                    lType = pop2();
                    push(type);
                    push2(lType);
                    push(type);
                    break;
                case ByteCode.DUP2:
                    lType = pop2();
                    push2(lType);
                    push2(lType);
                    break;
                case ByteCode.DUP2_X1:
                    lType = pop2();
                    type = pop();
                    push2(lType);
                    push(type);
                    push2(lType);
                    break;
                case ByteCode.DUP2_X2:
                    lType = pop2();
                    lType2 = pop2();
                    push2(lType);
                    push2(lType2);
                    push2(lType);
                    break;
                case ByteCode.TABLESWITCH:
                    int switchStart = bci + 1 + (3 & ~bci);
                    int low = getOperand(switchStart + 4, 4);
                    int high = getOperand(switchStart + 8, 4);
                    length = 4 * (high - low + 4) + switchStart - bci;
                    pop();
                    break;
                case ByteCode.AALOAD:
                    pop();
                    int typeIndex = pop() >>> 8;
                    className =
                            (String) itsConstantPool.getConstantData(typeIndex);
                    String arrayType = className;
                    if (arrayType.charAt(0) != '[') {
                        throw new IllegalStateException("bad array type");
                    }
                    String elementDesc = arrayType.substring(1);
                    String elementType = descriptorToInternalName(elementDesc);
                    typeIndex = itsConstantPool.addClass(elementType);
                    push(TypeInfo.OBJECT(typeIndex));
                    break;
                case ByteCode.WIDE:
                    // Alters behaviour of next instruction
                    wide = true;
                    break;
                case ByteCode.MULTIANEWARRAY:
                case ByteCode.LOOKUPSWITCH:
                    // Currently not used in any part of Rhino, so ignore it
                case ByteCode.JSR: // TODO: JSR is deprecated
                case ByteCode.RET:
                case ByteCode.JSR_W:
                default:
                    throw new IllegalArgumentException("bad opcode: " + bc);
            }

            if (length == 0) {
                length = opcodeLength(bc, wide);
            }
            if (wide && bc != ByteCode.WIDE) {
                wide = false;
            }
            return length;
        }

        private void executeALoad(int localIndex) {
            int type = getLocal(localIndex);
            int tag = TypeInfo.getTag(type);
            if (tag == TypeInfo.OBJECT_TAG ||
                tag == TypeInfo.UNINITIALIZED_THIS ||
                tag == TypeInfo.UNINITIALIZED_VAR_TAG ||
                tag == TypeInfo.NULL) {
                push(type);
            } else {
                throw new IllegalStateException("bad local variable type: " +
                                                type + " at index: " +
                                                localIndex);
            }
        }

        private void executeAStore(int localIndex) {
            setLocal(localIndex, pop());
        }

        private void executeStore(int localIndex, int typeInfo) {
            pop();
            setLocal(localIndex, typeInfo);
        }

        /**
         * Change an UNINITIALIZED_OBJECT or UNINITIALIZED_THIS to the proper
         * type of the object. This occurs when the proper constructor is
         * invoked.
         */
        private void initializeTypeInfo(int prevType, int newType) {
            initializeTypeInfo(prevType, newType, locals, localsTop);
            initializeTypeInfo(prevType, newType, stack, stackTop);
        }

        private void initializeTypeInfo(int prevType, int newType, int[] data,
                                        int dataTop) {
            for (int i = 0; i < dataTop; i++) {
                if (data[i] == prevType) {
                    data[i] = newType;
                }
            }
        }

        private int getLocal(int localIndex) {
            if (localIndex < localsTop) {
                return locals[localIndex];
            } else {
                return TypeInfo.TOP;
            }
        }

        private void setLocal(int localIndex, int typeInfo) {
            if (localIndex >= localsTop) {
                int[] tmp = new int[localIndex + 1];
                System.arraycopy(locals, 0, tmp, 0, localsTop);
                locals = tmp;
                localsTop = localIndex + 1;
            }
            locals[localIndex] = typeInfo;
        }

        private void push(int typeInfo) {
            if (stackTop == stack.length) {
                int[] tmp = new int[Math.max(stackTop * 2, 4)];
                System.arraycopy(stack, 0, tmp, 0, stackTop);
                stack = tmp;
            }
            stack[stackTop++] = typeInfo;
        }

        private int pop() {
            return stack[--stackTop];
        }

        /**
         * Push two words onto the op stack.
         *
         * This is only meant to be used as a complement to pop2(), and both
         * methods are helpers for the more complex DUP operations.
         */
        private void push2(long typeInfo) {
            push((int) (typeInfo & 0xFFFFFF));
            typeInfo >>>= 32;
            if (typeInfo != 0) {
                push((int) (typeInfo & 0xFFFFFF));
            }
        }

        /**
         * Pop two words from the op stack.
         *
         * If the top of the stack is a DOUBLE or LONG, then the bottom 32 bits
         * reflects the appropriate type and the top 32 bits are 0. Otherwise,
         * the top 32 bits are the first word on the stack and the lower 32
         * bits are the second word on the stack.
         */
        private long pop2() {
            long type = pop();
            if (TypeInfo.isTwoWords((int) type)) {
                return type;
            } else {
                return type << 32 | (pop() & 0xFFFFFF);
            }
        }

        private void clearStack() {
            stackTop = 0;
        }

        /**
         * Compute the output size of the stack map table.
         *
         * Because this would share much in common with actual writing of the
         * stack map table, we instead just write the stack map table to a
         * buffer and return the size from it. The buffer is later used in
         * the actual writing of bytecode.
         */
        int computeWriteSize() {
            // Allocate a buffer that can handle the worst case size of the
            // stack map to prevent lots of reallocations.
            int writeSize = getWorstCaseWriteSize();
            rawStackMap = new byte[writeSize];
            computeRawStackMap();
            return rawStackMapTop + 2;
        }

        int write(byte[] data, int offset) {
            offset = putInt32(rawStackMapTop + 2, data, offset);
            offset = putInt16(superBlocks.length - 1, data, offset);
            System.arraycopy(rawStackMap, 0, data, offset, rawStackMapTop);
            return offset + rawStackMapTop;
        }

        /**
         * Compute a space-optimal stack map table.
         */
        private void computeRawStackMap() {
            SuperBlock prev = superBlocks[0];
            int[] prevLocals = prev.getTrimmedLocals();
            int prevOffset = -1;
            for (int i = 1; i < superBlocks.length; i++) {
                SuperBlock current = superBlocks[i];
                int[] currentLocals = current.getTrimmedLocals();
                int[] currentStack = current.getStack();
                int offsetDelta = current.getStart() - prevOffset - 1;

                if (currentStack.length == 0) {
                    int last = prevLocals.length > currentLocals.length ?
                            currentLocals.length : prevLocals.length;
                    int delta = Math.abs(prevLocals.length -
                                         currentLocals.length);
                    int j;
                    // Compare locals until one is different or the end of a
                    // local variable array is reached
                    for (j = 0; j < last; j++) {
                        if (prevLocals[j] != currentLocals[j]) {
                            break;
                        }
                    }
                    if (j == currentLocals.length && delta == 0) {
                        // All of the compared locals are equal and the local
                        // arrays are of equal size
                        writeSameFrame(currentLocals, offsetDelta);
                    } else if (j == currentLocals.length && delta <= 3) {
                        // All of the compared locals are equal and the current
                        // frame has less locals than the previous frame
                        writeChopFrame(delta, offsetDelta);
                    } else if (j == prevLocals.length && delta <= 3) {
                        // All of the compared locals are equal and the current
                        // frame has more locals than the previous frame
                        writeAppendFrame(currentLocals, delta, offsetDelta);
                    } else {
                        // Not all locals were compared were equal, so a full
                        // frame is necessary
                        writeFullFrame(currentLocals, currentStack,
                                       offsetDelta);
                    }
                } else if (currentStack.length == 1) {
                    if (Arrays.equals(prevLocals, currentLocals)) {
                       writeSameLocalsOneStackItemFrame(currentLocals,
                                                        currentStack,
                                                        offsetDelta);
                    } else {
                        // Output a full frame, since no other frame types have
                        // one operand stack item.
                        writeFullFrame(currentLocals, currentStack,
                                       offsetDelta);
                    }
                } else {
                    // Any stack map frame that has more than one operand stack
                    // item has to be a full frame. All other frame types have
                    // at most one item on the stack.
                    writeFullFrame(currentLocals, currentStack, offsetDelta);
                }

                prev = current;
                prevLocals = currentLocals;
                prevOffset = current.getStart();
            }
        }

        /**
         * Get the worst case write size of the stack map table.
         *
         * This computes how much full frames would take, if each full frame
         * contained the maximum number of locals and stack operands, and each
         * verification type was 3 bytes.
         */
        private int getWorstCaseWriteSize() {
            return (superBlocks.length - 1) * (7 + itsMaxLocals * 3 +
                                               itsMaxStack * 3);
        }

        private void writeSameFrame(int[] locals, int offsetDelta) {
            if (offsetDelta <= 63) {
                // Output a same_frame frame. Despite the name,
                // the operand stack may differ, but the current
                // operand stack must be empty.
                rawStackMap[rawStackMapTop++] = (byte) offsetDelta;
            } else {
                // Output a same_frame_extended frame. Similar to
                // the above, except with a larger offset delta.
                rawStackMap[rawStackMapTop++] = (byte) 251;
                rawStackMapTop = putInt16(offsetDelta, rawStackMap,
                                          rawStackMapTop);
            }
        }

        private void writeSameLocalsOneStackItemFrame(int[] locals,
                                                      int[] stack,
                                                      int offsetDelta) {
            if (offsetDelta <= 63) {
                // Output a same_locals_1_stack_item frame. Similar
                // to same_frame, only with one item on the operand
                // stack instead of zero.
                rawStackMap[rawStackMapTop++] = (byte) (64 + offsetDelta);
            } else {
                // Output a same_locals_1_stack_item_extended frame.
                // Similar to same_frame_extended, only with one
                // item on the operand stack instead of zero.
                rawStackMap[rawStackMapTop++] = (byte) 247;
                rawStackMapTop = putInt16(offsetDelta, rawStackMap,
                                          rawStackMapTop);
            }
            writeType(stack[0]);
        }

        private void writeFullFrame(int[] locals, int[] stack,
                                    int offsetDelta) {
            rawStackMap[rawStackMapTop++] = (byte) 255;
            rawStackMapTop = putInt16(offsetDelta, rawStackMap, rawStackMapTop);
            rawStackMapTop = putInt16(locals.length, rawStackMap,
                                      rawStackMapTop);
            rawStackMapTop = writeTypes(locals);
            rawStackMapTop = putInt16(stack.length, rawStackMap,
                                      rawStackMapTop);
            rawStackMapTop = writeTypes(stack);
        }

        private void writeAppendFrame(int[] locals, int localsDelta,
                                      int offsetDelta) {
            int start = locals.length - localsDelta;
            rawStackMap[rawStackMapTop++] = (byte) (251 + localsDelta);
            rawStackMapTop = putInt16(offsetDelta, rawStackMap, rawStackMapTop);
            rawStackMapTop = writeTypes(locals, start);
        }

        private void writeChopFrame(int localsDelta, int offsetDelta) {
            rawStackMap[rawStackMapTop++] = (byte) (251 - localsDelta);
            rawStackMapTop = putInt16(offsetDelta, rawStackMap, rawStackMapTop);
        }

        private int writeTypes(int[] types) {
            return writeTypes(types, 0);
        }

        private int writeTypes(int[] types, int start) {
            int startOffset = rawStackMapTop;
            for (int i = start; i < types.length; i++) {
                rawStackMapTop = writeType(types[i]);
            }
            return rawStackMapTop;
        }

        private int writeType(int type) {
            int tag = type & 0xFF;
            rawStackMap[rawStackMapTop++] = (byte) tag;
            if (tag == TypeInfo.OBJECT_TAG ||
                tag == TypeInfo.UNINITIALIZED_VAR_TAG) {
                rawStackMapTop = putInt16(type >>> 8, rawStackMap,
                                          rawStackMapTop);
            }
            return rawStackMapTop;
        }

        // Intermediate operand stack and local variable state. During
        // execution of a block, these are initialized to copies of the initial
        // block type state and are modified by the actual stack/local
        // emulation.
        private int[] locals;
        private int localsTop;
        private int[] stack;
        private int stackTop;

        private SuperBlock[] workList;
        private int workListTop;

        private SuperBlock[] superBlocks;
        private SuperBlock[] superBlockDeps;

        private byte[] rawStackMap;
        private int rawStackMapTop;

        private boolean wide;

        static final boolean DEBUGSTACKMAP = false;
    }

    /**
     * Convert a newarray operand into an internal type.
     */
    private static char arrayTypeToName(int type) {
        switch (type) {
            case ByteCode.T_BOOLEAN:
                return 'Z';
            case ByteCode.T_CHAR:
                return 'C';
            case ByteCode.T_FLOAT:
                return 'F';
            case ByteCode.T_DOUBLE:
                return 'D';
            case ByteCode.T_BYTE:
                return 'B';
            case ByteCode.T_SHORT:
                return 'S';
            case ByteCode.T_INT:
                return 'I';
            case ByteCode.T_LONG:
                return 'J';
            default:
                throw new IllegalArgumentException("bad operand");
        }
    }

    /**
     * Convert a class descriptor into an internal name.
     *
     * For example, descriptor Ljava/lang/Object; becomes java/lang/Object.
     */
    private static String classDescriptorToInternalName(String descriptor) {
        return descriptor.substring(1, descriptor.length() - 1);
    }

    /**
     * Convert a non-method type descriptor into an internal type.
     *
     * @param descriptor the simple type descriptor to convert
     */
    private static String descriptorToInternalName(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 'V':
            case '[':
                return descriptor;
            case 'L':
                return classDescriptorToInternalName(descriptor);
            default:
                throw new IllegalArgumentException("bad descriptor:" +
                                                   descriptor);
        }
    }

    /**
     * Compute the initial local variable array for the current method.
     *
     * Creates an array of the size of the method's max locals, regardless of
     * the number of parameters in the method.
     */
    private int[] createInitialLocals() {
        int[] initialLocals = new int[itsMaxLocals];
        int localsTop = 0;
        // Instance methods require the first local variable in the array
        // to be "this". However, if the method being created is a
        // constructor, aka the method is <init>, then the type of "this"
        // should be StackMapTable.UNINITIALIZED_THIS
        if ((itsCurrentMethod.getFlags() & ACC_STATIC) == 0) {
            if ("<init>".equals(itsCurrentMethod.getName())) {
                initialLocals[localsTop++] = TypeInfo.UNINITIALIZED_THIS;
            } else {
                initialLocals[localsTop++] = TypeInfo.OBJECT(itsThisClassIndex);
            }
        }

        // No error checking should be necessary, sizeOfParameters does this
        String type = itsCurrentMethod.getType();
        int lParenIndex = type.indexOf('(');
        int rParenIndex = type.indexOf(')');
        if (lParenIndex != 0 || rParenIndex < 0) {
            throw new IllegalArgumentException("bad method type");
        }
        int start = lParenIndex + 1;
        StringBuilder paramType = new StringBuilder();
        while (start < rParenIndex) {
            switch (type.charAt(start)) {
                case 'B':
                case 'C':
                case 'D':
                case 'F':
                case 'I':
                case 'J':
                case 'S':
                case 'Z':
                    paramType.append(type.charAt(start));
                    ++start;
                    break;
                case 'L':
                    int end = type.indexOf(';', start) + 1;
                    String name = type.substring(start, end);
                    paramType.append(name);
                    start = end;
                    break;
                case '[':
                    paramType.append('[');
                    ++start;
                    continue;
            }
            String internalType =
                    descriptorToInternalName(paramType.toString());
            int typeInfo = TypeInfo.fromType(internalType, itsConstantPool);
            initialLocals[localsTop++] = typeInfo;
            if (TypeInfo.isTwoWords(typeInfo)) {
                localsTop++;
            }
            paramType.setLength(0);
        }
        return initialLocals;
    }

    /**
     * Write the class file to the OutputStream.
     *
     * @param oStream the stream to write to
     * @throws IOException if writing to the stream produces an exception
     */
    public void write(OutputStream oStream)
        throws IOException
    {
        byte[] array = toByteArray();
        oStream.write(array);
    }

    private int getWriteSize()
    {
        int size = 0;

        if (itsSourceFileNameIndex != 0) {
            itsConstantPool.addUtf8("SourceFile");
        }

        size += 8; //writeLong(FileHeaderConstant);
        size += itsConstantPool.getWriteSize();
        size += 2; //writeShort(itsFlags);
        size += 2; //writeShort(itsThisClassIndex);
        size += 2; //writeShort(itsSuperClassIndex);
        size += 2; //writeShort(itsInterfaces.size());
        size += 2 * itsInterfaces.size();

        size += 2; //writeShort(itsFields.size());
        for (int i = 0; i < itsFields.size(); i++) {
            size += ((ClassFileField)(itsFields.get(i))).getWriteSize();
        }

        size += 2; //writeShort(itsMethods.size());
        for (int i = 0; i < itsMethods.size(); i++) {
            size += ((ClassFileMethod)(itsMethods.get(i))).getWriteSize();
        }

        if (itsSourceFileNameIndex != 0) {
            size += 2; //writeShort(1);  attributes count
            size += 2; //writeShort(sourceFileAttributeNameIndex);
            size += 4; //writeInt(2);
            size += 2; //writeShort(itsSourceFileNameIndex);
        }else {
            size += 2; //out.writeShort(0);  no attributes
        }

        return size;
    }

    /**
     * Get the class file as array of bytesto the OutputStream.
     */
    public byte[] toByteArray()
    {
        int dataSize = getWriteSize();
        byte[] data = new byte[dataSize];
        int offset = 0;

        short sourceFileAttributeNameIndex = 0;
        if (itsSourceFileNameIndex != 0) {
            sourceFileAttributeNameIndex = itsConstantPool.addUtf8(
                                               "SourceFile");
        }

        offset = putInt32(FileHeaderConstant, data, offset);
        offset = putInt16(MinorVersion, data, offset);
        offset = putInt16(MajorVersion, data, offset);
        offset = itsConstantPool.write(data, offset);
        offset = putInt16(itsFlags, data, offset);
        offset = putInt16(itsThisClassIndex, data, offset);
        offset = putInt16(itsSuperClassIndex, data, offset);
        offset = putInt16(itsInterfaces.size(), data, offset);
        for (int i = 0; i < itsInterfaces.size(); i++) {
            int interfaceIndex = ((Short)(itsInterfaces.get(i))).shortValue();
            offset = putInt16(interfaceIndex, data, offset);
        }
        offset = putInt16(itsFields.size(), data, offset);
        for (int i = 0; i < itsFields.size(); i++) {
            ClassFileField field = (ClassFileField)itsFields.get(i);
            offset = field.write(data, offset);
        }
        offset = putInt16(itsMethods.size(), data, offset);
        for (int i = 0; i < itsMethods.size(); i++) {
            ClassFileMethod method = (ClassFileMethod)itsMethods.get(i);
            offset = method.write(data, offset);
        }
        if (itsSourceFileNameIndex != 0) {
            offset = putInt16(1, data, offset); // attributes count
            offset = putInt16(sourceFileAttributeNameIndex, data, offset);
            offset = putInt32(2, data, offset);
            offset = putInt16(itsSourceFileNameIndex, data, offset);
        } else {
            offset = putInt16(0, data, offset); // no attributes
        }

        if (offset != dataSize) {
            // Check getWriteSize is consistent with write!
            throw new RuntimeException();
        }

        return data;
    }

    static int putInt64(long value, byte[] array, int offset)
    {
        offset = putInt32((int)(value >>> 32), array, offset);
        return putInt32((int)value, array, offset);
    }

    private static void badStack(int value)
    {
        String s;
        if (value < 0) { s = "Stack underflow: "+value; }
        else { s = "Too big stack: "+value; }
        throw new IllegalStateException(s);
    }

    /*
        Really weird. Returns an int with # parameters in hi 16 bits, and
        stack difference removal of parameters from stack and pushing the
        result (it does not take into account removal of this in case of
        non-static methods).
        If Java really supported references we wouldn't have to be this
        perverted.
    */
    private static int sizeOfParameters(String pString)
    {
        int length = pString.length();
        int rightParenthesis = pString.lastIndexOf(')');
        if (3 <= length /* minimal signature takes at least 3 chars: ()V */
            && pString.charAt(0) == '('
            && 1 <= rightParenthesis && rightParenthesis + 1 < length)
        {
            boolean ok = true;
            int index = 1;
            int stackDiff = 0;
            int count = 0;
        stringLoop:
            while (index != rightParenthesis) {
                switch (pString.charAt(index)) {
                    default:
                        ok = false;
                        break stringLoop;
                    case 'J' :
                    case 'D' :
                        --stackDiff;
                        // fall thru
                    case 'B' :
                    case 'S' :
                    case 'C' :
                    case 'I' :
                    case 'Z' :
                    case 'F' :
                        --stackDiff;
                        ++count;
                        ++index;
                        continue;
                    case '[' :
                        ++index;
                        int c = pString.charAt(index);
                        while (c == '[') {
                            ++index;
                            c = pString.charAt(index);
                        }
                        switch (c) {
                            default:
                                ok = false;
                                break stringLoop;
                            case 'J' :
                            case 'D' :
                            case 'B' :
                            case 'S' :
                            case 'C' :
                            case 'I' :
                            case 'Z' :
                            case 'F' :
                                --stackDiff;
                                ++count;
                                ++index;
                                continue;
                            case 'L':
                                // fall thru
                        }
                          // fall thru
                    case 'L' : {
                        --stackDiff;
                        ++count;
                        ++index;
                        int semicolon = pString.indexOf(';',  index);
                        if (!(index + 1 <= semicolon
                            && semicolon < rightParenthesis))
                        {
                            ok = false;
                            break stringLoop;
                        }
                        index = semicolon + 1;
                        continue;
                    }
                }
            }
            if (ok) {
                switch (pString.charAt(rightParenthesis + 1)) {
                    default:
                        ok = false;
                        break;
                    case 'J' :
                    case 'D' :
                        ++stackDiff;
                        // fall thru
                    case 'B' :
                    case 'S' :
                    case 'C' :
                    case 'I' :
                    case 'Z' :
                    case 'F' :
                    case 'L' :
                    case '[' :
                        ++stackDiff;
                        // fall thru
                    case 'V' :
                        break;
                }
                if (ok) {
                    return ((count << 16) | (0xFFFF & stackDiff));
                }
            }
        }
        throw new IllegalArgumentException(
            "Bad parameter signature: "+pString);
    }

    static int putInt16(int value, byte[] array, int offset)
    {
        array[offset + 0] = (byte)(value >>> 8);
        array[offset + 1] = (byte)value;
        return offset + 2;
    }

    static int putInt32(int value, byte[] array, int offset)
    {
        array[offset + 0] = (byte)(value >>> 24);
        array[offset + 1] = (byte)(value >>> 16);
        array[offset + 2] = (byte)(value >>> 8);
        array[offset + 3] = (byte)value;
        return offset + 4;
    }

    /**
     * Size of a bytecode instruction, counting the opcode and its operands.
     *
     * This is different from opcodeCount, since opcodeCount counts logical
     * operands.
     */
    static int opcodeLength(int opcode, boolean wide) {
        switch (opcode) {
            case ByteCode.AALOAD:
            case ByteCode.AASTORE:
            case ByteCode.ACONST_NULL:
            case ByteCode.ALOAD_0:
            case ByteCode.ALOAD_1:
            case ByteCode.ALOAD_2:
            case ByteCode.ALOAD_3:
            case ByteCode.ARETURN:
            case ByteCode.ARRAYLENGTH:
            case ByteCode.ASTORE_0:
            case ByteCode.ASTORE_1:
            case ByteCode.ASTORE_2:
            case ByteCode.ASTORE_3:
            case ByteCode.ATHROW:
            case ByteCode.BALOAD:
            case ByteCode.BASTORE:
            case ByteCode.BREAKPOINT:
            case ByteCode.CALOAD:
            case ByteCode.CASTORE:
            case ByteCode.D2F:
            case ByteCode.D2I:
            case ByteCode.D2L:
            case ByteCode.DADD:
            case ByteCode.DALOAD:
            case ByteCode.DASTORE:
            case ByteCode.DCMPG:
            case ByteCode.DCMPL:
            case ByteCode.DCONST_0:
            case ByteCode.DCONST_1:
            case ByteCode.DDIV:
            case ByteCode.DLOAD_0:
            case ByteCode.DLOAD_1:
            case ByteCode.DLOAD_2:
            case ByteCode.DLOAD_3:
            case ByteCode.DMUL:
            case ByteCode.DNEG:
            case ByteCode.DREM:
            case ByteCode.DRETURN:
            case ByteCode.DSTORE_0:
            case ByteCode.DSTORE_1:
            case ByteCode.DSTORE_2:
            case ByteCode.DSTORE_3:
            case ByteCode.DSUB:
            case ByteCode.DUP:
            case ByteCode.DUP2:
            case ByteCode.DUP2_X1:
            case ByteCode.DUP2_X2:
            case ByteCode.DUP_X1:
            case ByteCode.DUP_X2:
            case ByteCode.F2D:
            case ByteCode.F2I:
            case ByteCode.F2L:
            case ByteCode.FADD:
            case ByteCode.FALOAD:
            case ByteCode.FASTORE:
            case ByteCode.FCMPG:
            case ByteCode.FCMPL:
            case ByteCode.FCONST_0:
            case ByteCode.FCONST_1:
            case ByteCode.FCONST_2:
            case ByteCode.FDIV:
            case ByteCode.FLOAD_0:
            case ByteCode.FLOAD_1:
            case ByteCode.FLOAD_2:
            case ByteCode.FLOAD_3:
            case ByteCode.FMUL:
            case ByteCode.FNEG:
            case ByteCode.FREM:
            case ByteCode.FRETURN:
            case ByteCode.FSTORE_0:
            case ByteCode.FSTORE_1:
            case ByteCode.FSTORE_2:
            case ByteCode.FSTORE_3:
            case ByteCode.FSUB:
            case ByteCode.I2B:
            case ByteCode.I2C:
            case ByteCode.I2D:
            case ByteCode.I2F:
            case ByteCode.I2L:
            case ByteCode.I2S:
            case ByteCode.IADD:
            case ByteCode.IALOAD:
            case ByteCode.IAND:
            case ByteCode.IASTORE:
            case ByteCode.ICONST_0:
            case ByteCode.ICONST_1:
            case ByteCode.ICONST_2:
            case ByteCode.ICONST_3:
            case ByteCode.ICONST_4:
            case ByteCode.ICONST_5:
            case ByteCode.ICONST_M1:
            case ByteCode.IDIV:
            case ByteCode.ILOAD_0:
            case ByteCode.ILOAD_1:
            case ByteCode.ILOAD_2:
            case ByteCode.ILOAD_3:
            case ByteCode.IMPDEP1:
            case ByteCode.IMPDEP2:
            case ByteCode.IMUL:
            case ByteCode.INEG:
            case ByteCode.IOR:
            case ByteCode.IREM:
            case ByteCode.IRETURN:
            case ByteCode.ISHL:
            case ByteCode.ISHR:
            case ByteCode.ISTORE_0:
            case ByteCode.ISTORE_1:
            case ByteCode.ISTORE_2:
            case ByteCode.ISTORE_3:
            case ByteCode.ISUB:
            case ByteCode.IUSHR:
            case ByteCode.IXOR:
            case ByteCode.L2D:
            case ByteCode.L2F:
            case ByteCode.L2I:
            case ByteCode.LADD:
            case ByteCode.LALOAD:
            case ByteCode.LAND:
            case ByteCode.LASTORE:
            case ByteCode.LCMP:
            case ByteCode.LCONST_0:
            case ByteCode.LCONST_1:
            case ByteCode.LDIV:
            case ByteCode.LLOAD_0:
            case ByteCode.LLOAD_1:
            case ByteCode.LLOAD_2:
            case ByteCode.LLOAD_3:
            case ByteCode.LMUL:
            case ByteCode.LNEG:
            case ByteCode.LOR:
            case ByteCode.LREM:
            case ByteCode.LRETURN:
            case ByteCode.LSHL:
            case ByteCode.LSHR:
            case ByteCode.LSTORE_0:
            case ByteCode.LSTORE_1:
            case ByteCode.LSTORE_2:
            case ByteCode.LSTORE_3:
            case ByteCode.LSUB:
            case ByteCode.LUSHR:
            case ByteCode.LXOR:
            case ByteCode.MONITORENTER:
            case ByteCode.MONITOREXIT:
            case ByteCode.NOP:
            case ByteCode.POP:
            case ByteCode.POP2:
            case ByteCode.RETURN:
            case ByteCode.SALOAD:
            case ByteCode.SASTORE:
            case ByteCode.SWAP:
            case ByteCode.WIDE:
                return 1;
            case ByteCode.BIPUSH:
            case ByteCode.LDC:
            case ByteCode.NEWARRAY:
                return 2;
            case ByteCode.ALOAD:
            case ByteCode.ASTORE:
            case ByteCode.DLOAD:
            case ByteCode.DSTORE:
            case ByteCode.FLOAD:
            case ByteCode.FSTORE:
            case ByteCode.ILOAD:
            case ByteCode.ISTORE:
            case ByteCode.LLOAD:
            case ByteCode.LSTORE:
            case ByteCode.RET:
                return wide ? 3 : 2;

            case ByteCode.ANEWARRAY:
            case ByteCode.CHECKCAST:
            case ByteCode.GETFIELD:
            case ByteCode.GETSTATIC:
            case ByteCode.GOTO:
            case ByteCode.IFEQ:
            case ByteCode.IFGE:
            case ByteCode.IFGT:
            case ByteCode.IFLE:
            case ByteCode.IFLT:
            case ByteCode.IFNE:
            case ByteCode.IFNONNULL:
            case ByteCode.IFNULL:
            case ByteCode.IF_ACMPEQ:
            case ByteCode.IF_ACMPNE:
            case ByteCode.IF_ICMPEQ:
            case ByteCode.IF_ICMPGE:
            case ByteCode.IF_ICMPGT:
            case ByteCode.IF_ICMPLE:
            case ByteCode.IF_ICMPLT:
            case ByteCode.IF_ICMPNE:
            case ByteCode.INSTANCEOF:
            case ByteCode.INVOKESPECIAL:
            case ByteCode.INVOKESTATIC:
            case ByteCode.INVOKEVIRTUAL:
            case ByteCode.JSR:
            case ByteCode.LDC_W:
            case ByteCode.LDC2_W:
            case ByteCode.NEW:
            case ByteCode.PUTFIELD:
            case ByteCode.PUTSTATIC:
            case ByteCode.SIPUSH:
                return 3;

            case ByteCode.IINC:
                return wide ? 5 : 3;

            case ByteCode.MULTIANEWARRAY:
                return 4;

            case ByteCode.GOTO_W:
            case ByteCode.INVOKEINTERFACE:
            case ByteCode.JSR_W:
                return 5;

            /*
            case ByteCode.LOOKUPSWITCH:
            case ByteCode.TABLESWITCH:
                return -1;
            */
        }
        throw new IllegalArgumentException("Bad opcode: " + opcode);
    }

    /**
     * Number of operands accompanying the opcode.
     */
    static int opcodeCount(int opcode)
    {
        switch (opcode) {
            case ByteCode.AALOAD:
            case ByteCode.AASTORE:
            case ByteCode.ACONST_NULL:
            case ByteCode.ALOAD_0:
            case ByteCode.ALOAD_1:
            case ByteCode.ALOAD_2:
            case ByteCode.ALOAD_3:
            case ByteCode.ARETURN:
            case ByteCode.ARRAYLENGTH:
            case ByteCode.ASTORE_0:
            case ByteCode.ASTORE_1:
            case ByteCode.ASTORE_2:
            case ByteCode.ASTORE_3:
            case ByteCode.ATHROW:
            case ByteCode.BALOAD:
            case ByteCode.BASTORE:
            case ByteCode.BREAKPOINT:
            case ByteCode.CALOAD:
            case ByteCode.CASTORE:
            case ByteCode.D2F:
            case ByteCode.D2I:
            case ByteCode.D2L:
            case ByteCode.DADD:
            case ByteCode.DALOAD:
            case ByteCode.DASTORE:
            case ByteCode.DCMPG:
            case ByteCode.DCMPL:
            case ByteCode.DCONST_0:
            case ByteCode.DCONST_1:
            case ByteCode.DDIV:
            case ByteCode.DLOAD_0:
            case ByteCode.DLOAD_1:
            case ByteCode.DLOAD_2:
            case ByteCode.DLOAD_3:
            case ByteCode.DMUL:
            case ByteCode.DNEG:
            case ByteCode.DREM:
            case ByteCode.DRETURN:
            case ByteCode.DSTORE_0:
            case ByteCode.DSTORE_1:
            case ByteCode.DSTORE_2:
            case ByteCode.DSTORE_3:
            case ByteCode.DSUB:
            case ByteCode.DUP:
            case ByteCode.DUP2:
            case ByteCode.DUP2_X1:
            case ByteCode.DUP2_X2:
            case ByteCode.DUP_X1:
            case ByteCode.DUP_X2:
            case ByteCode.F2D:
            case ByteCode.F2I:
            case ByteCode.F2L:
            case ByteCode.FADD:
            case ByteCode.FALOAD:
            case ByteCode.FASTORE:
            case ByteCode.FCMPG:
            case ByteCode.FCMPL:
            case ByteCode.FCONST_0:
            case ByteCode.FCONST_1:
            case ByteCode.FCONST_2:
            case ByteCode.FDIV:
            case ByteCode.FLOAD_0:
            case ByteCode.FLOAD_1:
            case ByteCode.FLOAD_2:
            case ByteCode.FLOAD_3:
            case ByteCode.FMUL:
            case ByteCode.FNEG:
            case ByteCode.FREM:
            case ByteCode.FRETURN:
            case ByteCode.FSTORE_0:
            case ByteCode.FSTORE_1:
            case ByteCode.FSTORE_2:
            case ByteCode.FSTORE_3:
            case ByteCode.FSUB:
            case ByteCode.I2B:
            case ByteCode.I2C:
            case ByteCode.I2D:
            case ByteCode.I2F:
            case ByteCode.I2L:
            case ByteCode.I2S:
            case ByteCode.IADD:
            case ByteCode.IALOAD:
            case ByteCode.IAND:
            case ByteCode.IASTORE:
            case ByteCode.ICONST_0:
            case ByteCode.ICONST_1:
            case ByteCode.ICONST_2:
            case ByteCode.ICONST_3:
            case ByteCode.ICONST_4:
            case ByteCode.ICONST_5:
            case ByteCode.ICONST_M1:
            case ByteCode.IDIV:
            case ByteCode.ILOAD_0:
            case ByteCode.ILOAD_1:
            case ByteCode.ILOAD_2:
            case ByteCode.ILOAD_3:
            case ByteCode.IMPDEP1:
            case ByteCode.IMPDEP2:
            case ByteCode.IMUL:
            case ByteCode.INEG:
            case ByteCode.IOR:
            case ByteCode.IREM:
            case ByteCode.IRETURN:
            case ByteCode.ISHL:
            case ByteCode.ISHR:
            case ByteCode.ISTORE_0:
            case ByteCode.ISTORE_1:
            case ByteCode.ISTORE_2:
            case ByteCode.ISTORE_3:
            case ByteCode.ISUB:
            case ByteCode.IUSHR:
            case ByteCode.IXOR:
            case ByteCode.L2D:
            case ByteCode.L2F:
            case ByteCode.L2I:
            case ByteCode.LADD:
            case ByteCode.LALOAD:
            case ByteCode.LAND:
            case ByteCode.LASTORE:
            case ByteCode.LCMP:
            case ByteCode.LCONST_0:
            case ByteCode.LCONST_1:
            case ByteCode.LDIV:
            case ByteCode.LLOAD_0:
            case ByteCode.LLOAD_1:
            case ByteCode.LLOAD_2:
            case ByteCode.LLOAD_3:
            case ByteCode.LMUL:
            case ByteCode.LNEG:
            case ByteCode.LOR:
            case ByteCode.LREM:
            case ByteCode.LRETURN:
            case ByteCode.LSHL:
            case ByteCode.LSHR:
            case ByteCode.LSTORE_0:
            case ByteCode.LSTORE_1:
            case ByteCode.LSTORE_2:
            case ByteCode.LSTORE_3:
            case ByteCode.LSUB:
            case ByteCode.LUSHR:
            case ByteCode.LXOR:
            case ByteCode.MONITORENTER:
            case ByteCode.MONITOREXIT:
            case ByteCode.NOP:
            case ByteCode.POP:
            case ByteCode.POP2:
            case ByteCode.RETURN:
            case ByteCode.SALOAD:
            case ByteCode.SASTORE:
            case ByteCode.SWAP:
            case ByteCode.WIDE:
                return 0;
            case ByteCode.ALOAD:
            case ByteCode.ANEWARRAY:
            case ByteCode.ASTORE:
            case ByteCode.BIPUSH:
            case ByteCode.CHECKCAST:
            case ByteCode.DLOAD:
            case ByteCode.DSTORE:
            case ByteCode.FLOAD:
            case ByteCode.FSTORE:
            case ByteCode.GETFIELD:
            case ByteCode.GETSTATIC:
            case ByteCode.GOTO:
            case ByteCode.GOTO_W:
            case ByteCode.IFEQ:
            case ByteCode.IFGE:
            case ByteCode.IFGT:
            case ByteCode.IFLE:
            case ByteCode.IFLT:
            case ByteCode.IFNE:
            case ByteCode.IFNONNULL:
            case ByteCode.IFNULL:
            case ByteCode.IF_ACMPEQ:
            case ByteCode.IF_ACMPNE:
            case ByteCode.IF_ICMPEQ:
            case ByteCode.IF_ICMPGE:
            case ByteCode.IF_ICMPGT:
            case ByteCode.IF_ICMPLE:
            case ByteCode.IF_ICMPLT:
            case ByteCode.IF_ICMPNE:
            case ByteCode.ILOAD:
            case ByteCode.INSTANCEOF:
            case ByteCode.INVOKEINTERFACE:
            case ByteCode.INVOKESPECIAL:
            case ByteCode.INVOKESTATIC:
            case ByteCode.INVOKEVIRTUAL:
            case ByteCode.ISTORE:
            case ByteCode.JSR:
            case ByteCode.JSR_W:
            case ByteCode.LDC:
            case ByteCode.LDC2_W:
            case ByteCode.LDC_W:
            case ByteCode.LLOAD:
            case ByteCode.LSTORE:
            case ByteCode.NEW:
            case ByteCode.NEWARRAY:
            case ByteCode.PUTFIELD:
            case ByteCode.PUTSTATIC:
            case ByteCode.RET:
            case ByteCode.SIPUSH:
                return 1;

            case ByteCode.IINC:
            case ByteCode.MULTIANEWARRAY:
                return 2;

            case ByteCode.LOOKUPSWITCH:
            case ByteCode.TABLESWITCH:
                return -1;
        }
        throw new IllegalArgumentException("Bad opcode: "+opcode);
    }

    /**
     *  The effect on the operand stack of a given opcode.
     */
    static int stackChange(int opcode)
    {
        // For INVOKE... accounts only for popping this (unless static),
        // ignoring parameters and return type
        switch (opcode) {
            case ByteCode.DASTORE:
            case ByteCode.LASTORE:
                return -4;

            case ByteCode.AASTORE:
            case ByteCode.BASTORE:
            case ByteCode.CASTORE:
            case ByteCode.DCMPG:
            case ByteCode.DCMPL:
            case ByteCode.FASTORE:
            case ByteCode.IASTORE:
            case ByteCode.LCMP:
            case ByteCode.SASTORE:
                return -3;

            case ByteCode.DADD:
            case ByteCode.DDIV:
            case ByteCode.DMUL:
            case ByteCode.DREM:
            case ByteCode.DRETURN:
            case ByteCode.DSTORE:
            case ByteCode.DSTORE_0:
            case ByteCode.DSTORE_1:
            case ByteCode.DSTORE_2:
            case ByteCode.DSTORE_3:
            case ByteCode.DSUB:
            case ByteCode.IF_ACMPEQ:
            case ByteCode.IF_ACMPNE:
            case ByteCode.IF_ICMPEQ:
            case ByteCode.IF_ICMPGE:
            case ByteCode.IF_ICMPGT:
            case ByteCode.IF_ICMPLE:
            case ByteCode.IF_ICMPLT:
            case ByteCode.IF_ICMPNE:
            case ByteCode.LADD:
            case ByteCode.LAND:
            case ByteCode.LDIV:
            case ByteCode.LMUL:
            case ByteCode.LOR:
            case ByteCode.LREM:
            case ByteCode.LRETURN:
            case ByteCode.LSTORE:
            case ByteCode.LSTORE_0:
            case ByteCode.LSTORE_1:
            case ByteCode.LSTORE_2:
            case ByteCode.LSTORE_3:
            case ByteCode.LSUB:
            case ByteCode.LXOR:
            case ByteCode.POP2:
                return -2;

            case ByteCode.AALOAD:
            case ByteCode.ARETURN:
            case ByteCode.ASTORE:
            case ByteCode.ASTORE_0:
            case ByteCode.ASTORE_1:
            case ByteCode.ASTORE_2:
            case ByteCode.ASTORE_3:
            case ByteCode.ATHROW:
            case ByteCode.BALOAD:
            case ByteCode.CALOAD:
            case ByteCode.D2F:
            case ByteCode.D2I:
            case ByteCode.FADD:
            case ByteCode.FALOAD:
            case ByteCode.FCMPG:
            case ByteCode.FCMPL:
            case ByteCode.FDIV:
            case ByteCode.FMUL:
            case ByteCode.FREM:
            case ByteCode.FRETURN:
            case ByteCode.FSTORE:
            case ByteCode.FSTORE_0:
            case ByteCode.FSTORE_1:
            case ByteCode.FSTORE_2:
            case ByteCode.FSTORE_3:
            case ByteCode.FSUB:
            case ByteCode.GETFIELD:
            case ByteCode.IADD:
            case ByteCode.IALOAD:
            case ByteCode.IAND:
            case ByteCode.IDIV:
            case ByteCode.IFEQ:
            case ByteCode.IFGE:
            case ByteCode.IFGT:
            case ByteCode.IFLE:
            case ByteCode.IFLT:
            case ByteCode.IFNE:
            case ByteCode.IFNONNULL:
            case ByteCode.IFNULL:
            case ByteCode.IMUL:
            case ByteCode.INVOKEINTERFACE:       //
            case ByteCode.INVOKESPECIAL:         // but needs to account for
            case ByteCode.INVOKEVIRTUAL:         // pops 'this' (unless static)
            case ByteCode.IOR:
            case ByteCode.IREM:
            case ByteCode.IRETURN:
            case ByteCode.ISHL:
            case ByteCode.ISHR:
            case ByteCode.ISTORE:
            case ByteCode.ISTORE_0:
            case ByteCode.ISTORE_1:
            case ByteCode.ISTORE_2:
            case ByteCode.ISTORE_3:
            case ByteCode.ISUB:
            case ByteCode.IUSHR:
            case ByteCode.IXOR:
            case ByteCode.L2F:
            case ByteCode.L2I:
            case ByteCode.LOOKUPSWITCH:
            case ByteCode.LSHL:
            case ByteCode.LSHR:
            case ByteCode.LUSHR:
            case ByteCode.MONITORENTER:
            case ByteCode.MONITOREXIT:
            case ByteCode.POP:
            case ByteCode.PUTFIELD:
            case ByteCode.SALOAD:
            case ByteCode.TABLESWITCH:
                return -1;

            case ByteCode.ANEWARRAY:
            case ByteCode.ARRAYLENGTH:
            case ByteCode.BREAKPOINT:
            case ByteCode.CHECKCAST:
            case ByteCode.D2L:
            case ByteCode.DALOAD:
            case ByteCode.DNEG:
            case ByteCode.F2I:
            case ByteCode.FNEG:
            case ByteCode.GETSTATIC:
            case ByteCode.GOTO:
            case ByteCode.GOTO_W:
            case ByteCode.I2B:
            case ByteCode.I2C:
            case ByteCode.I2F:
            case ByteCode.I2S:
            case ByteCode.IINC:
            case ByteCode.IMPDEP1:
            case ByteCode.IMPDEP2:
            case ByteCode.INEG:
            case ByteCode.INSTANCEOF:
            case ByteCode.INVOKESTATIC:
            case ByteCode.L2D:
            case ByteCode.LALOAD:
            case ByteCode.LNEG:
            case ByteCode.NEWARRAY:
            case ByteCode.NOP:
            case ByteCode.PUTSTATIC:
            case ByteCode.RET:
            case ByteCode.RETURN:
            case ByteCode.SWAP:
            case ByteCode.WIDE:
                return 0;

            case ByteCode.ACONST_NULL:
            case ByteCode.ALOAD:
            case ByteCode.ALOAD_0:
            case ByteCode.ALOAD_1:
            case ByteCode.ALOAD_2:
            case ByteCode.ALOAD_3:
            case ByteCode.BIPUSH:
            case ByteCode.DUP:
            case ByteCode.DUP_X1:
            case ByteCode.DUP_X2:
            case ByteCode.F2D:
            case ByteCode.F2L:
            case ByteCode.FCONST_0:
            case ByteCode.FCONST_1:
            case ByteCode.FCONST_2:
            case ByteCode.FLOAD:
            case ByteCode.FLOAD_0:
            case ByteCode.FLOAD_1:
            case ByteCode.FLOAD_2:
            case ByteCode.FLOAD_3:
            case ByteCode.I2D:
            case ByteCode.I2L:
            case ByteCode.ICONST_0:
            case ByteCode.ICONST_1:
            case ByteCode.ICONST_2:
            case ByteCode.ICONST_3:
            case ByteCode.ICONST_4:
            case ByteCode.ICONST_5:
            case ByteCode.ICONST_M1:
            case ByteCode.ILOAD:
            case ByteCode.ILOAD_0:
            case ByteCode.ILOAD_1:
            case ByteCode.ILOAD_2:
            case ByteCode.ILOAD_3:
            case ByteCode.JSR:
            case ByteCode.JSR_W:
            case ByteCode.LDC:
            case ByteCode.LDC_W:
            case ByteCode.MULTIANEWARRAY:
            case ByteCode.NEW:
            case ByteCode.SIPUSH:
                return 1;

            case ByteCode.DCONST_0:
            case ByteCode.DCONST_1:
            case ByteCode.DLOAD:
            case ByteCode.DLOAD_0:
            case ByteCode.DLOAD_1:
            case ByteCode.DLOAD_2:
            case ByteCode.DLOAD_3:
            case ByteCode.DUP2:
            case ByteCode.DUP2_X1:
            case ByteCode.DUP2_X2:
            case ByteCode.LCONST_0:
            case ByteCode.LCONST_1:
            case ByteCode.LDC2_W:
            case ByteCode.LLOAD:
            case ByteCode.LLOAD_0:
            case ByteCode.LLOAD_1:
            case ByteCode.LLOAD_2:
            case ByteCode.LLOAD_3:
                return 2;
        }
        throw new IllegalArgumentException("Bad opcode: "+opcode);
    }

        /*
         * Number of bytes of operands generated after the opcode.
         * Not in use currently.
         */
/*
    int extra(int opcode)
    {
        switch (opcode) {
            case ByteCode.AALOAD:
            case ByteCode.AASTORE:
            case ByteCode.ACONST_NULL:
            case ByteCode.ALOAD_0:
            case ByteCode.ALOAD_1:
            case ByteCode.ALOAD_2:
            case ByteCode.ALOAD_3:
            case ByteCode.ARETURN:
            case ByteCode.ARRAYLENGTH:
            case ByteCode.ASTORE_0:
            case ByteCode.ASTORE_1:
            case ByteCode.ASTORE_2:
            case ByteCode.ASTORE_3:
            case ByteCode.ATHROW:
            case ByteCode.BALOAD:
            case ByteCode.BASTORE:
            case ByteCode.BREAKPOINT:
            case ByteCode.CALOAD:
            case ByteCode.CASTORE:
            case ByteCode.D2F:
            case ByteCode.D2I:
            case ByteCode.D2L:
            case ByteCode.DADD:
            case ByteCode.DALOAD:
            case ByteCode.DASTORE:
            case ByteCode.DCMPG:
            case ByteCode.DCMPL:
            case ByteCode.DCONST_0:
            case ByteCode.DCONST_1:
            case ByteCode.DDIV:
            case ByteCode.DLOAD_0:
            case ByteCode.DLOAD_1:
            case ByteCode.DLOAD_2:
            case ByteCode.DLOAD_3:
            case ByteCode.DMUL:
            case ByteCode.DNEG:
            case ByteCode.DREM:
            case ByteCode.DRETURN:
            case ByteCode.DSTORE_0:
            case ByteCode.DSTORE_1:
            case ByteCode.DSTORE_2:
            case ByteCode.DSTORE_3:
            case ByteCode.DSUB:
            case ByteCode.DUP2:
            case ByteCode.DUP2_X1:
            case ByteCode.DUP2_X2:
            case ByteCode.DUP:
            case ByteCode.DUP_X1:
            case ByteCode.DUP_X2:
            case ByteCode.F2D:
            case ByteCode.F2I:
            case ByteCode.F2L:
            case ByteCode.FADD:
            case ByteCode.FALOAD:
            case ByteCode.FASTORE:
            case ByteCode.FCMPG:
            case ByteCode.FCMPL:
            case ByteCode.FCONST_0:
            case ByteCode.FCONST_1:
            case ByteCode.FCONST_2:
            case ByteCode.FDIV:
            case ByteCode.FLOAD_0:
            case ByteCode.FLOAD_1:
            case ByteCode.FLOAD_2:
            case ByteCode.FLOAD_3:
            case ByteCode.FMUL:
            case ByteCode.FNEG:
            case ByteCode.FREM:
            case ByteCode.FRETURN:
            case ByteCode.FSTORE_0:
            case ByteCode.FSTORE_1:
            case ByteCode.FSTORE_2:
            case ByteCode.FSTORE_3:
            case ByteCode.FSUB:
            case ByteCode.I2B:
            case ByteCode.I2C:
            case ByteCode.I2D:
            case ByteCode.I2F:
            case ByteCode.I2L:
            case ByteCode.I2S:
            case ByteCode.IADD:
            case ByteCode.IALOAD:
            case ByteCode.IAND:
            case ByteCode.IASTORE:
            case ByteCode.ICONST_0:
            case ByteCode.ICONST_1:
            case ByteCode.ICONST_2:
            case ByteCode.ICONST_3:
            case ByteCode.ICONST_4:
            case ByteCode.ICONST_5:
            case ByteCode.ICONST_M1:
            case ByteCode.IDIV:
            case ByteCode.ILOAD_0:
            case ByteCode.ILOAD_1:
            case ByteCode.ILOAD_2:
            case ByteCode.ILOAD_3:
            case ByteCode.IMPDEP1:
            case ByteCode.IMPDEP2:
            case ByteCode.IMUL:
            case ByteCode.INEG:
            case ByteCode.IOR:
            case ByteCode.IREM:
            case ByteCode.IRETURN:
            case ByteCode.ISHL:
            case ByteCode.ISHR:
            case ByteCode.ISTORE_0:
            case ByteCode.ISTORE_1:
            case ByteCode.ISTORE_2:
            case ByteCode.ISTORE_3:
            case ByteCode.ISUB:
            case ByteCode.IUSHR:
            case ByteCode.IXOR:
            case ByteCode.L2D:
            case ByteCode.L2F:
            case ByteCode.L2I:
            case ByteCode.LADD:
            case ByteCode.LALOAD:
            case ByteCode.LAND:
            case ByteCode.LASTORE:
            case ByteCode.LCMP:
            case ByteCode.LCONST_0:
            case ByteCode.LCONST_1:
            case ByteCode.LDIV:
            case ByteCode.LLOAD_0:
            case ByteCode.LLOAD_1:
            case ByteCode.LLOAD_2:
            case ByteCode.LLOAD_3:
            case ByteCode.LMUL:
            case ByteCode.LNEG:
            case ByteCode.LOR:
            case ByteCode.LREM:
            case ByteCode.LRETURN:
            case ByteCode.LSHL:
            case ByteCode.LSHR:
            case ByteCode.LSTORE_0:
            case ByteCode.LSTORE_1:
            case ByteCode.LSTORE_2:
            case ByteCode.LSTORE_3:
            case ByteCode.LSUB:
            case ByteCode.LUSHR:
            case ByteCode.LXOR:
            case ByteCode.MONITORENTER:
            case ByteCode.MONITOREXIT:
            case ByteCode.NOP:
            case ByteCode.POP2:
            case ByteCode.POP:
            case ByteCode.RETURN:
            case ByteCode.SALOAD:
            case ByteCode.SASTORE:
            case ByteCode.SWAP:
            case ByteCode.WIDE:
                return 0;

            case ByteCode.ALOAD:
            case ByteCode.ASTORE:
            case ByteCode.BIPUSH:
            case ByteCode.DLOAD:
            case ByteCode.DSTORE:
            case ByteCode.FLOAD:
            case ByteCode.FSTORE:
            case ByteCode.ILOAD:
            case ByteCode.ISTORE:
            case ByteCode.LDC:
            case ByteCode.LLOAD:
            case ByteCode.LSTORE:
            case ByteCode.NEWARRAY:
            case ByteCode.RET:
                return 1;

            case ByteCode.ANEWARRAY:
            case ByteCode.CHECKCAST:
            case ByteCode.GETFIELD:
            case ByteCode.GETSTATIC:
            case ByteCode.GOTO:
            case ByteCode.IFEQ:
            case ByteCode.IFGE:
            case ByteCode.IFGT:
            case ByteCode.IFLE:
            case ByteCode.IFLT:
            case ByteCode.IFNE:
            case ByteCode.IFNONNULL:
            case ByteCode.IFNULL:
            case ByteCode.IF_ACMPEQ:
            case ByteCode.IF_ACMPNE:
            case ByteCode.IF_ICMPEQ:
            case ByteCode.IF_ICMPGE:
            case ByteCode.IF_ICMPGT:
            case ByteCode.IF_ICMPLE:
            case ByteCode.IF_ICMPLT:
            case ByteCode.IF_ICMPNE:
            case ByteCode.IINC:
            case ByteCode.INSTANCEOF:
            case ByteCode.INVOKEINTERFACE:
            case ByteCode.INVOKESPECIAL:
            case ByteCode.INVOKESTATIC:
            case ByteCode.INVOKEVIRTUAL:
            case ByteCode.JSR:
            case ByteCode.LDC2_W:
            case ByteCode.LDC_W:
            case ByteCode.NEW:
            case ByteCode.PUTFIELD:
            case ByteCode.PUTSTATIC:
            case ByteCode.SIPUSH:
                return 2;

            case ByteCode.MULTIANEWARRAY:
                return 3;

            case ByteCode.GOTO_W:
            case ByteCode.JSR_W:
                return 4;

            case ByteCode.LOOKUPSWITCH:    // depends on alignment
            case ByteCode.TABLESWITCH: // depends on alignment
                return -1;
        }
        throw new IllegalArgumentException("Bad opcode: "+opcode);
    }
*/
    private static String bytecodeStr(int code)
    {
        if (DEBUGSTACK || DEBUGCODE) {
            switch (code) {
                case ByteCode.NOP:              return "nop";
                case ByteCode.ACONST_NULL:      return "aconst_null";
                case ByteCode.ICONST_M1:        return "iconst_m1";
                case ByteCode.ICONST_0:         return "iconst_0";
                case ByteCode.ICONST_1:         return "iconst_1";
                case ByteCode.ICONST_2:         return "iconst_2";
                case ByteCode.ICONST_3:         return "iconst_3";
                case ByteCode.ICONST_4:         return "iconst_4";
                case ByteCode.ICONST_5:         return "iconst_5";
                case ByteCode.LCONST_0:         return "lconst_0";
                case ByteCode.LCONST_1:         return "lconst_1";
                case ByteCode.FCONST_0:         return "fconst_0";
                case ByteCode.FCONST_1:         return "fconst_1";
                case ByteCode.FCONST_2:         return "fconst_2";
                case ByteCode.DCONST_0:         return "dconst_0";
                case ByteCode.DCONST_1:         return "dconst_1";
                case ByteCode.BIPUSH:           return "bipush";
                case ByteCode.SIPUSH:           return "sipush";
                case ByteCode.LDC:              return "ldc";
                case ByteCode.LDC_W:            return "ldc_w";
                case ByteCode.LDC2_W:           return "ldc2_w";
                case ByteCode.ILOAD:            return "iload";
                case ByteCode.LLOAD:            return "lload";
                case ByteCode.FLOAD:            return "fload";
                case ByteCode.DLOAD:            return "dload";
                case ByteCode.ALOAD:            return "aload";
                case ByteCode.ILOAD_0:          return "iload_0";
                case ByteCode.ILOAD_1:          return "iload_1";
                case ByteCode.ILOAD_2:          return "iload_2";
                case ByteCode.ILOAD_3:          return "iload_3";
                case ByteCode.LLOAD_0:          return "lload_0";
                case ByteCode.LLOAD_1:          return "lload_1";
                case ByteCode.LLOAD_2:          return "lload_2";
                case ByteCode.LLOAD_3:          return "lload_3";
                case ByteCode.FLOAD_0:          return "fload_0";
                case ByteCode.FLOAD_1:          return "fload_1";
                case ByteCode.FLOAD_2:          return "fload_2";
                case ByteCode.FLOAD_3:          return "fload_3";
                case ByteCode.DLOAD_0:          return "dload_0";
                case ByteCode.DLOAD_1:          return "dload_1";
                case ByteCode.DLOAD_2:          return "dload_2";
                case ByteCode.DLOAD_3:          return "dload_3";
                case ByteCode.ALOAD_0:          return "aload_0";
                case ByteCode.ALOAD_1:          return "aload_1";
                case ByteCode.ALOAD_2:          return "aload_2";
                case ByteCode.ALOAD_3:          return "aload_3";
                case ByteCode.IALOAD:           return "iaload";
                case ByteCode.LALOAD:           return "laload";
                case ByteCode.FALOAD:           return "faload";
                case ByteCode.DALOAD:           return "daload";
                case ByteCode.AALOAD:           return "aaload";
                case ByteCode.BALOAD:           return "baload";
                case ByteCode.CALOAD:           return "caload";
                case ByteCode.SALOAD:           return "saload";
                case ByteCode.ISTORE:           return "istore";
                case ByteCode.LSTORE:           return "lstore";
                case ByteCode.FSTORE:           return "fstore";
                case ByteCode.DSTORE:           return "dstore";
                case ByteCode.ASTORE:           return "astore";
                case ByteCode.ISTORE_0:         return "istore_0";
                case ByteCode.ISTORE_1:         return "istore_1";
                case ByteCode.ISTORE_2:         return "istore_2";
                case ByteCode.ISTORE_3:         return "istore_3";
                case ByteCode.LSTORE_0:         return "lstore_0";
                case ByteCode.LSTORE_1:         return "lstore_1";
                case ByteCode.LSTORE_2:         return "lstore_2";
                case ByteCode.LSTORE_3:         return "lstore_3";
                case ByteCode.FSTORE_0:         return "fstore_0";
                case ByteCode.FSTORE_1:         return "fstore_1";
                case ByteCode.FSTORE_2:         return "fstore_2";
                case ByteCode.FSTORE_3:         return "fstore_3";
                case ByteCode.DSTORE_0:         return "dstore_0";
                case ByteCode.DSTORE_1:         return "dstore_1";
                case ByteCode.DSTORE_2:         return "dstore_2";
                case ByteCode.DSTORE_3:         return "dstore_3";
                case ByteCode.ASTORE_0:         return "astore_0";
                case ByteCode.ASTORE_1:         return "astore_1";
                case ByteCode.ASTORE_2:         return "astore_2";
                case ByteCode.ASTORE_3:         return "astore_3";
                case ByteCode.IASTORE:          return "iastore";
                case ByteCode.LASTORE:          return "lastore";
                case ByteCode.FASTORE:          return "fastore";
                case ByteCode.DASTORE:          return "dastore";
                case ByteCode.AASTORE:          return "aastore";
                case ByteCode.BASTORE:          return "bastore";
                case ByteCode.CASTORE:          return "castore";
                case ByteCode.SASTORE:          return "sastore";
                case ByteCode.POP:              return "pop";
                case ByteCode.POP2:             return "pop2";
                case ByteCode.DUP:              return "dup";
                case ByteCode.DUP_X1:           return "dup_x1";
                case ByteCode.DUP_X2:           return "dup_x2";
                case ByteCode.DUP2:             return "dup2";
                case ByteCode.DUP2_X1:          return "dup2_x1";
                case ByteCode.DUP2_X2:          return "dup2_x2";
                case ByteCode.SWAP:             return "swap";
                case ByteCode.IADD:             return "iadd";
                case ByteCode.LADD:             return "ladd";
                case ByteCode.FADD:             return "fadd";
                case ByteCode.DADD:             return "dadd";
                case ByteCode.ISUB:             return "isub";
                case ByteCode.LSUB:             return "lsub";
                case ByteCode.FSUB:             return "fsub";
                case ByteCode.DSUB:             return "dsub";
                case ByteCode.IMUL:             return "imul";
                case ByteCode.LMUL:             return "lmul";
                case ByteCode.FMUL:             return "fmul";
                case ByteCode.DMUL:             return "dmul";
                case ByteCode.IDIV:             return "idiv";
                case ByteCode.LDIV:             return "ldiv";
                case ByteCode.FDIV:             return "fdiv";
                case ByteCode.DDIV:             return "ddiv";
                case ByteCode.IREM:             return "irem";
                case ByteCode.LREM:             return "lrem";
                case ByteCode.FREM:             return "frem";
                case ByteCode.DREM:             return "drem";
                case ByteCode.INEG:             return "ineg";
                case ByteCode.LNEG:             return "lneg";
                case ByteCode.FNEG:             return "fneg";
                case ByteCode.DNEG:             return "dneg";
                case ByteCode.ISHL:             return "ishl";
                case ByteCode.LSHL:             return "lshl";
                case ByteCode.ISHR:             return "ishr";
                case ByteCode.LSHR:             return "lshr";
                case ByteCode.IUSHR:            return "iushr";
                case ByteCode.LUSHR:            return "lushr";
                case ByteCode.IAND:             return "iand";
                case ByteCode.LAND:             return "land";
                case ByteCode.IOR:              return "ior";
                case ByteCode.LOR:              return "lor";
                case ByteCode.IXOR:             return "ixor";
                case ByteCode.LXOR:             return "lxor";
                case ByteCode.IINC:             return "iinc";
                case ByteCode.I2L:              return "i2l";
                case ByteCode.I2F:              return "i2f";
                case ByteCode.I2D:              return "i2d";
                case ByteCode.L2I:              return "l2i";
                case ByteCode.L2F:              return "l2f";
                case ByteCode.L2D:              return "l2d";
                case ByteCode.F2I:              return "f2i";
                case ByteCode.F2L:              return "f2l";
                case ByteCode.F2D:              return "f2d";
                case ByteCode.D2I:              return "d2i";
                case ByteCode.D2L:              return "d2l";
                case ByteCode.D2F:              return "d2f";
                case ByteCode.I2B:              return "i2b";
                case ByteCode.I2C:              return "i2c";
                case ByteCode.I2S:              return "i2s";
                case ByteCode.LCMP:             return "lcmp";
                case ByteCode.FCMPL:            return "fcmpl";
                case ByteCode.FCMPG:            return "fcmpg";
                case ByteCode.DCMPL:            return "dcmpl";
                case ByteCode.DCMPG:            return "dcmpg";
                case ByteCode.IFEQ:             return "ifeq";
                case ByteCode.IFNE:             return "ifne";
                case ByteCode.IFLT:             return "iflt";
                case ByteCode.IFGE:             return "ifge";
                case ByteCode.IFGT:             return "ifgt";
                case ByteCode.IFLE:             return "ifle";
                case ByteCode.IF_ICMPEQ:        return "if_icmpeq";
                case ByteCode.IF_ICMPNE:        return "if_icmpne";
                case ByteCode.IF_ICMPLT:        return "if_icmplt";
                case ByteCode.IF_ICMPGE:        return "if_icmpge";
                case ByteCode.IF_ICMPGT:        return "if_icmpgt";
                case ByteCode.IF_ICMPLE:        return "if_icmple";
                case ByteCode.IF_ACMPEQ:        return "if_acmpeq";
                case ByteCode.IF_ACMPNE:        return "if_acmpne";
                case ByteCode.GOTO:             return "goto";
                case ByteCode.JSR:              return "jsr";
                case ByteCode.RET:              return "ret";
                case ByteCode.TABLESWITCH:      return "tableswitch";
                case ByteCode.LOOKUPSWITCH:     return "lookupswitch";
                case ByteCode.IRETURN:          return "ireturn";
                case ByteCode.LRETURN:          return "lreturn";
                case ByteCode.FRETURN:          return "freturn";
                case ByteCode.DRETURN:          return "dreturn";
                case ByteCode.ARETURN:          return "areturn";
                case ByteCode.RETURN:           return "return";
                case ByteCode.GETSTATIC:        return "getstatic";
                case ByteCode.PUTSTATIC:        return "putstatic";
                case ByteCode.GETFIELD:         return "getfield";
                case ByteCode.PUTFIELD:         return "putfield";
                case ByteCode.INVOKEVIRTUAL:    return "invokevirtual";
                case ByteCode.INVOKESPECIAL:    return "invokespecial";
                case ByteCode.INVOKESTATIC:     return "invokestatic";
                case ByteCode.INVOKEINTERFACE:  return "invokeinterface";
                case ByteCode.NEW:              return "new";
                case ByteCode.NEWARRAY:         return "newarray";
                case ByteCode.ANEWARRAY:        return "anewarray";
                case ByteCode.ARRAYLENGTH:      return "arraylength";
                case ByteCode.ATHROW:           return "athrow";
                case ByteCode.CHECKCAST:        return "checkcast";
                case ByteCode.INSTANCEOF:       return "instanceof";
                case ByteCode.MONITORENTER:     return "monitorenter";
                case ByteCode.MONITOREXIT:      return "monitorexit";
                case ByteCode.WIDE:             return "wide";
                case ByteCode.MULTIANEWARRAY:   return "multianewarray";
                case ByteCode.IFNULL:           return "ifnull";
                case ByteCode.IFNONNULL:        return "ifnonnull";
                case ByteCode.GOTO_W:           return "goto_w";
                case ByteCode.JSR_W:            return "jsr_w";
                case ByteCode.BREAKPOINT:       return "breakpoint";

                case ByteCode.IMPDEP1:          return "impdep1";
                case ByteCode.IMPDEP2:          return "impdep2";
            }
        }
        return "";
    }

    final char[] getCharBuffer(int minimalSize)
    {
        if (minimalSize > tmpCharBuffer.length) {
            int newSize = tmpCharBuffer.length * 2;
            if (minimalSize > newSize) { newSize = minimalSize; }
            tmpCharBuffer = new char[newSize];
        }
        return tmpCharBuffer;
    }

    /**
     * Add a pc as the start of super block.
     *
     * A pc is the beginning of a super block if:
     * - pc == 0
     * - it is the target of a branch instruction
     * - it is the beginning of an exception handler
     * - it is directly after an unconditional jump
     */
    private void addSuperBlockStart(int pc) {
        if (GenerateStackMap) {
            if (itsSuperBlockStarts == null) {
                itsSuperBlockStarts = new int[SuperBlockStartsSize];
            } else if (itsSuperBlockStarts.length == itsSuperBlockStartsTop) {
                int[] tmp = new int[itsSuperBlockStartsTop * 2];
                System.arraycopy(itsSuperBlockStarts, 0, tmp, 0,
                                 itsSuperBlockStartsTop);
                itsSuperBlockStarts = tmp;
            }
            itsSuperBlockStarts[itsSuperBlockStartsTop++] = pc;
        }
    }

    /**
     * Sort the list of recorded super block starts and remove duplicates.
     *
     * Also adds exception handling blocks as block starts, since there is no
     * explicit control flow to these. Used for stack map table generation.
     */
    private void finalizeSuperBlockStarts() {
        if (GenerateStackMap) {
            for (int i = 0; i < itsExceptionTableTop; i++) {
                ExceptionTableEntry ete = itsExceptionTable[i];
                short handlerPC = (short) getLabelPC(ete.itsHandlerLabel);
                addSuperBlockStart(handlerPC);
            }
            Arrays.sort(itsSuperBlockStarts, 0, itsSuperBlockStartsTop);
            int prev = itsSuperBlockStarts[0];
            int copyTo = 1;
            for (int i = 1; i < itsSuperBlockStartsTop; i++) {
                int curr = itsSuperBlockStarts[i];
                if (prev != curr) {
                    if (copyTo != i) {
                        itsSuperBlockStarts[copyTo] = curr;
                    }
                    copyTo++;
                    prev = curr;
                }
            }
            itsSuperBlockStartsTop = copyTo;
            if (itsSuperBlockStarts[copyTo - 1] == itsCodeBufferTop) {
                itsSuperBlockStartsTop--;
            }
        }
    }

    private int[] itsSuperBlockStarts = null;
    private int itsSuperBlockStartsTop = 0;
    private static final int SuperBlockStartsSize = 4;

    // Used to find blocks of code with no dependencies (aka dead code).
    // Necessary for generating type information for dead code, which is
    // expected by the Sun verifier. It is only necessary to store a single
    // jump source to determine if a block is reachable or not.
    private UintMap itsJumpFroms = null;

    private static final int LineNumberTableSize = 16;
    private static final int ExceptionTableSize = 4;

    private static final int MajorVersion;
    private static final int MinorVersion;
    private static final boolean GenerateStackMap;

    static {
        // Figure out which classfile version should be generated. This assumes
        // that the runtime used to compile the JavaScript files is the same as
        // the one used to run them. This is important because there are cases
        // when bytecode is generated at runtime, where it is not easy to pass
        // along what version is necessary. Instead, we grab the version numbers
        // from the bytecode of this class and use that.
        //
        // Based on the version numbers we scrape, we can also determine what
        // bytecode features we need. For example, Java 6 bytecode (classfile
        // version 50) should have stack maps generated.
        InputStream is = null;
        int major = 48, minor = 0;
        try {
            is = ClassFileWriter.class.getResourceAsStream("ClassFileWriter.class");
            if (is == null) {
                is = ClassLoader.getSystemResourceAsStream(
                    "org/mozilla/classfile/ClassFileWriter.class");
            }
            byte[] header = new byte[8];
            // read loop is required since JDK7 will only provide 2 bytes
            // on the first read() - see bug #630111
            int read = 0;
            while (read < 8) {
                int c = is.read(header, read, 8 - read);
                if (c < 0) throw new IOException();
                read += c;
            }
            minor = (header[4] << 8) | (header[5] & 0xff);
            major = (header[6] << 8) | (header[7] & 0xff);
        } catch (Exception e) {
            // Unable to get class file, use default bytecode version
        } finally {
            MinorVersion = minor;
            MajorVersion = major;
            GenerateStackMap = major >= 50;
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private final static int FileHeaderConstant = 0xCAFEBABE;
    // Set DEBUG flags to true to get better checking and progress info.
    private static final boolean DEBUGSTACK = false;
    private static final boolean DEBUGLABELS = false;
    private static final boolean DEBUGCODE = false;

    private String generatedClassName;

    private ExceptionTableEntry itsExceptionTable[];
    private int itsExceptionTableTop;

    private int itsLineNumberTable[];   // pack start_pc & line_number together
    private int itsLineNumberTableTop;

    private byte[] itsCodeBuffer = new byte[256];
    private int itsCodeBufferTop;

    private ConstantPool itsConstantPool;

    private ClassFileMethod itsCurrentMethod;
    private short itsStackTop;

    private short itsMaxStack;
    private short itsMaxLocals;

    private ObjArray itsMethods = new ObjArray();
    private ObjArray itsFields = new ObjArray();
    private ObjArray itsInterfaces = new ObjArray();

    private short itsFlags;
    private short itsThisClassIndex;
    private short itsSuperClassIndex;
    private short itsSourceFileNameIndex;

    private static final int MIN_LABEL_TABLE_SIZE = 32;
    private int[] itsLabelTable;
    private int itsLabelTableTop;

// itsFixupTable[i] = (label_index << 32) | fixup_site
    private static final int MIN_FIXUP_TABLE_SIZE = 40;
    private long[] itsFixupTable;
    private int itsFixupTableTop;
    private ObjArray itsVarDescriptors;

    private char[] tmpCharBuffer = new char[64];
}

final class ExceptionTableEntry
{

    ExceptionTableEntry(int startLabel, int endLabel,
                        int handlerLabel, short catchType)
    {
        itsStartLabel = startLabel;
        itsEndLabel = endLabel;
        itsHandlerLabel = handlerLabel;
        itsCatchType = catchType;
    }

    int itsStartLabel;
    int itsEndLabel;
    int itsHandlerLabel;
    short itsCatchType;
}

final class ClassFileField
{

    ClassFileField(short nameIndex, short typeIndex, short flags)
    {
        itsNameIndex = nameIndex;
        itsTypeIndex = typeIndex;
        itsFlags = flags;
        itsHasAttributes = false;
    }

    void setAttributes(short attr1, short attr2, short attr3, int index)
    {
        itsHasAttributes = true;
        itsAttr1 = attr1;
        itsAttr2 = attr2;
        itsAttr3 = attr3;
        itsIndex = index;
    }

    int write(byte[] data, int offset)
    {
        offset = ClassFileWriter.putInt16(itsFlags, data, offset);
        offset = ClassFileWriter.putInt16(itsNameIndex, data, offset);
        offset = ClassFileWriter.putInt16(itsTypeIndex, data, offset);
        if (!itsHasAttributes) {
            // write 0 attributes
            offset = ClassFileWriter.putInt16(0, data, offset);
        } else {
            offset = ClassFileWriter.putInt16(1, data, offset);
            offset = ClassFileWriter.putInt16(itsAttr1, data, offset);
            offset = ClassFileWriter.putInt16(itsAttr2, data, offset);
            offset = ClassFileWriter.putInt16(itsAttr3, data, offset);
            offset = ClassFileWriter.putInt16(itsIndex, data, offset);
        }
        return offset;
    }

    int getWriteSize()
    {
        int size = 2 * 3;
        if (!itsHasAttributes) {
            size += 2;
        } else {
            size += 2 + 2 * 4;
        }
        return size;
    }

    private short itsNameIndex;
    private short itsTypeIndex;
    private short itsFlags;
    private boolean itsHasAttributes;
    private short itsAttr1, itsAttr2, itsAttr3;
    private int itsIndex;
}

final class ClassFileMethod
{

    ClassFileMethod(String name, short nameIndex, String type, short typeIndex,
                    short flags)
    {
        itsName = name;
        itsNameIndex = nameIndex;
        itsType = type;
        itsTypeIndex = typeIndex;
        itsFlags = flags;
    }

    void setCodeAttribute(byte codeAttribute[])
    {
        itsCodeAttribute = codeAttribute;
    }

    int write(byte[] data, int offset)
    {
        offset = ClassFileWriter.putInt16(itsFlags, data, offset);
        offset = ClassFileWriter.putInt16(itsNameIndex, data, offset);
        offset = ClassFileWriter.putInt16(itsTypeIndex, data, offset);
        // Code attribute only
        offset = ClassFileWriter.putInt16(1, data, offset);
        System.arraycopy(itsCodeAttribute, 0, data, offset,
                         itsCodeAttribute.length);
        offset += itsCodeAttribute.length;
        return offset;
    }

    int getWriteSize()
    {
        return 2 * 4 + itsCodeAttribute.length;
    }

    String getName()
    {
        return itsName;
    }

    String getType()
    {
        return itsType;
    }

    short getFlags()
    {
        return itsFlags;
    }

    private String itsName;
    private String itsType;
    private short itsNameIndex;
    private short itsTypeIndex;
    private short itsFlags;
    private byte[] itsCodeAttribute;

}

final class ConstantPool
{

    ConstantPool(ClassFileWriter cfw)
    {
        this.cfw = cfw;
        itsTopIndex = 1;       // the zero'th entry is reserved
        itsPool = new byte[ConstantPoolSize];
        itsTop = 0;
    }

    private static final int ConstantPoolSize = 256;
    static final byte
        CONSTANT_Class = 7,
        CONSTANT_Fieldref = 9,
        CONSTANT_Methodref = 10,
        CONSTANT_InterfaceMethodref = 11,
        CONSTANT_String = 8,
        CONSTANT_Integer = 3,
        CONSTANT_Float = 4,
        CONSTANT_Long = 5,
        CONSTANT_Double = 6,
        CONSTANT_NameAndType = 12,
        CONSTANT_Utf8 = 1;

    int write(byte[] data, int offset)
    {
        offset = ClassFileWriter.putInt16((short)itsTopIndex, data, offset);
        System.arraycopy(itsPool, 0, data, offset, itsTop);
        offset += itsTop;
        return offset;
    }

    int getWriteSize()
    {
        return 2 + itsTop;
    }

    int addConstant(int k)
    {
        ensure(5);
        itsPool[itsTop++] = CONSTANT_Integer;
        itsTop = ClassFileWriter.putInt32(k, itsPool, itsTop);
        itsPoolTypes.put(itsTopIndex, CONSTANT_Integer);
        return (short)(itsTopIndex++);
    }

    int addConstant(long k)
    {
        ensure(9);
        itsPool[itsTop++] = CONSTANT_Long;
        itsTop = ClassFileWriter.putInt64(k, itsPool, itsTop);
        int index = itsTopIndex;
        itsTopIndex += 2;
        itsPoolTypes.put(index, CONSTANT_Long);
        return index;
    }

    int addConstant(float k)
    {
        ensure(5);
        itsPool[itsTop++] = CONSTANT_Float;
        int bits = Float.floatToIntBits(k);
        itsTop = ClassFileWriter.putInt32(bits, itsPool, itsTop);
        itsPoolTypes.put(itsTopIndex, CONSTANT_Float);
        return itsTopIndex++;
    }

    int addConstant(double k)
    {
        ensure(9);
        itsPool[itsTop++] = CONSTANT_Double;
        long bits = Double.doubleToLongBits(k);
        itsTop = ClassFileWriter.putInt64(bits, itsPool, itsTop);
        int index = itsTopIndex;
        itsTopIndex += 2;
        itsPoolTypes.put(index, CONSTANT_Double);
        return index;
    }

    int addConstant(String k)
    {
        int utf8Index = 0xFFFF & addUtf8(k);
        int theIndex = itsStringConstHash.getInt(utf8Index, -1);
        if (theIndex == -1) {
            theIndex = itsTopIndex++;
            ensure(3);
            itsPool[itsTop++] = CONSTANT_String;
            itsTop = ClassFileWriter.putInt16(utf8Index, itsPool, itsTop);
            itsStringConstHash.put(utf8Index, theIndex);
        }
        itsPoolTypes.put(theIndex, CONSTANT_String);
        return theIndex;
    }

    boolean isUnderUtfEncodingLimit(String s)
    {
        int strLen = s.length();
        if (strLen * 3 <= MAX_UTF_ENCODING_SIZE) {
            return true;
        } else if (strLen > MAX_UTF_ENCODING_SIZE) {
            return false;
        }
        return strLen == getUtfEncodingLimit(s, 0, strLen);
    }

    /**
     * Get maximum i such that <tt>start <= i <= end</tt> and
     * <tt>s.substring(start, i)</tt> fits JVM UTF string encoding limit.
     */
    int getUtfEncodingLimit(String s, int start, int end)
    {
        if ((end - start) * 3 <= MAX_UTF_ENCODING_SIZE) {
            return end;
        }
        int limit = MAX_UTF_ENCODING_SIZE;
        for (int i = start; i != end; i++) {
            int c = s.charAt(i);
            if (0 != c && c <= 0x7F) {
                --limit;
            } else if (c < 0x7FF) {
                limit -= 2;
            } else {
                limit -= 3;
            }
            if (limit < 0) {
                return i;
            }
        }
        return end;
    }

    short addUtf8(String k)
    {
        int theIndex = itsUtf8Hash.get(k, -1);
        if (theIndex == -1) {
            int strLen = k.length();
            boolean tooBigString;
            if (strLen > MAX_UTF_ENCODING_SIZE) {
                tooBigString = true;
            } else {
                tooBigString = false;
                // Ask for worst case scenario buffer when each char takes 3
                // bytes
                ensure(1 + 2 + strLen * 3);
                int top = itsTop;

                itsPool[top++] = CONSTANT_Utf8;
                top += 2; // skip length

                char[] chars = cfw.getCharBuffer(strLen);
                k.getChars(0, strLen, chars, 0);

                for (int i = 0; i != strLen; i++) {
                    int c = chars[i];
                    if (c != 0 && c <= 0x7F) {
                        itsPool[top++] = (byte)c;
                    } else if (c > 0x7FF) {
                        itsPool[top++] = (byte)(0xE0 | (c >> 12));
                        itsPool[top++] = (byte)(0x80 | ((c >> 6) & 0x3F));
                        itsPool[top++] = (byte)(0x80 | (c & 0x3F));
                    } else {
                        itsPool[top++] = (byte)(0xC0 | (c >> 6));
                        itsPool[top++] = (byte)(0x80 | (c & 0x3F));
                    }
                }

                int utfLen = top - (itsTop + 1 + 2);
                if (utfLen > MAX_UTF_ENCODING_SIZE) {
                    tooBigString = true;
                } else {
                    // Write back length
                    itsPool[itsTop + 1] = (byte)(utfLen >>> 8);
                    itsPool[itsTop + 2] = (byte)utfLen;

                    itsTop = top;
                    theIndex = itsTopIndex++;
                    itsUtf8Hash.put(k, theIndex);
                }
            }
            if (tooBigString) {
                throw new IllegalArgumentException("Too big string");
            }
        }
        setConstantData(theIndex, k);
        itsPoolTypes.put(theIndex, CONSTANT_Utf8);
        return (short)theIndex;
    }

    private short addNameAndType(String name, String type)
    {
        short nameIndex = addUtf8(name);
        short typeIndex = addUtf8(type);
        ensure(5);
        itsPool[itsTop++] = CONSTANT_NameAndType;
        itsTop = ClassFileWriter.putInt16(nameIndex, itsPool, itsTop);
        itsTop = ClassFileWriter.putInt16(typeIndex, itsPool, itsTop);
        itsPoolTypes.put(itsTopIndex, CONSTANT_NameAndType);
        return (short)(itsTopIndex++);
    }

    short addClass(String className)
    {
        int theIndex = itsClassHash.get(className, -1);
        if (theIndex == -1) {
            String slashed = className;
            if (className.indexOf('.') > 0) {
                slashed = ClassFileWriter.getSlashedForm(className);
                theIndex = itsClassHash.get(slashed, -1);
                if (theIndex != -1) {
                    itsClassHash.put(className, theIndex);
                }
            }
            if (theIndex == -1) {
                int utf8Index = addUtf8(slashed);
                ensure(3);
                itsPool[itsTop++] = CONSTANT_Class;
                itsTop = ClassFileWriter.putInt16(utf8Index, itsPool, itsTop);
                theIndex = itsTopIndex++;
                itsClassHash.put(slashed, theIndex);
                if (className != slashed) {
                    itsClassHash.put(className, theIndex);
                }
            }
        }
        setConstantData(theIndex, className);
        itsPoolTypes.put(theIndex, CONSTANT_Class);
        return (short)theIndex;
    }

    short addFieldRef(String className, String fieldName, String fieldType)
    {
        FieldOrMethodRef ref = new FieldOrMethodRef(className, fieldName,
                                                    fieldType);

        int theIndex = itsFieldRefHash.get(ref, -1);
        if (theIndex == -1) {
            short ntIndex = addNameAndType(fieldName, fieldType);
            short classIndex = addClass(className);
            ensure(5);
            itsPool[itsTop++] = CONSTANT_Fieldref;
            itsTop = ClassFileWriter.putInt16(classIndex, itsPool, itsTop);
            itsTop = ClassFileWriter.putInt16(ntIndex, itsPool, itsTop);
            theIndex = itsTopIndex++;
            itsFieldRefHash.put(ref, theIndex);
        }
        setConstantData(theIndex, ref);
        itsPoolTypes.put(theIndex, CONSTANT_Fieldref);
        return (short)theIndex;
    }

    short addMethodRef(String className, String methodName,
                       String methodType)
    {
        FieldOrMethodRef ref = new FieldOrMethodRef(className, methodName,
                                                    methodType);

        int theIndex = itsMethodRefHash.get(ref, -1);
        if (theIndex == -1) {
            short ntIndex = addNameAndType(methodName, methodType);
            short classIndex = addClass(className);
            ensure(5);
            itsPool[itsTop++] = CONSTANT_Methodref;
            itsTop = ClassFileWriter.putInt16(classIndex, itsPool, itsTop);
            itsTop = ClassFileWriter.putInt16(ntIndex, itsPool, itsTop);
            theIndex = itsTopIndex++;
            itsMethodRefHash.put(ref, theIndex);
        }
        setConstantData(theIndex, ref);
        itsPoolTypes.put(theIndex, CONSTANT_Methodref);
        return (short)theIndex;
    }

    short addInterfaceMethodRef(String className,
                                String methodName, String methodType)
    {
        short ntIndex = addNameAndType(methodName, methodType);
        short classIndex = addClass(className);
        ensure(5);
        itsPool[itsTop++] = CONSTANT_InterfaceMethodref;
        itsTop = ClassFileWriter.putInt16(classIndex, itsPool, itsTop);
        itsTop = ClassFileWriter.putInt16(ntIndex, itsPool, itsTop);
        FieldOrMethodRef r = new FieldOrMethodRef(className, methodName,
                                                  methodType);
        setConstantData(itsTopIndex, r);
        itsPoolTypes.put(itsTopIndex, CONSTANT_InterfaceMethodref);
        return (short)(itsTopIndex++);
    }

    Object getConstantData(int index)
    {
        return itsConstantData.getObject(index);
    }

    void setConstantData(int index, Object data)
    {
        itsConstantData.put(index, data);
    }

    byte getConstantType(int index)
    {
        return (byte) itsPoolTypes.getInt(index, 0);
    }

    void ensure(int howMuch)
    {
        if (itsTop + howMuch > itsPool.length) {
            int newCapacity = itsPool.length * 2;
            if (itsTop + howMuch > newCapacity) {
                newCapacity = itsTop + howMuch;
            }
            byte[] tmp = new byte[newCapacity];
            System.arraycopy(itsPool, 0, tmp, 0, itsTop);
            itsPool = tmp;
        }
    }

    private ClassFileWriter cfw;

    private static final int MAX_UTF_ENCODING_SIZE = 65535;

    private UintMap itsStringConstHash = new UintMap();
    private ObjToIntMap itsUtf8Hash = new ObjToIntMap();
    private ObjToIntMap itsFieldRefHash = new ObjToIntMap();
    private ObjToIntMap itsMethodRefHash = new ObjToIntMap();
    private ObjToIntMap itsClassHash = new ObjToIntMap();

    private int itsTop;
    private int itsTopIndex;
    private UintMap itsConstantData = new UintMap();
    private UintMap itsPoolTypes = new UintMap();
    private byte itsPool[];
}

final class FieldOrMethodRef
{
    FieldOrMethodRef(String className, String name, String type)
    {
        this.className = className;
        this.name = name;
        this.type = type;
    }

    public String getClassName()
    {
        return className;
    }

    public String getName()
    {
        return name;
    }

    public String getType()
    {
        return type;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof FieldOrMethodRef)) { return false; }
        FieldOrMethodRef x = (FieldOrMethodRef)obj;
        return className.equals(x.className)
            && name.equals(x.name)
            && type.equals(x.type);
    }

    @Override
    public int hashCode()
    {
        if (hashCode == -1) {
            int h1 = className.hashCode();
            int h2 = name.hashCode();
            int h3 = type.hashCode();
            hashCode = h1 ^ h2 ^ h3;
        }
        return hashCode;
    }

    private String className;
    private String name;
    private String type;
    private int hashCode = -1;
}

/**
 * A super block is defined as a contiguous chunk of code with a single entry
 * point and multiple exit points (therefore ending in an unconditional jump
 * or the end of the method). This is used to emulate OpenJDK's compiler, which
 * outputs stack map frames at the start of every super block except the method
 * start.
 */
final class SuperBlock {
    SuperBlock(int index, int start, int end, int[] initialLocals) {
        this.index = index;
        this.start = start;
        this.end = end;
        locals = new int[initialLocals.length];
        System.arraycopy(initialLocals, 0, locals, 0, initialLocals.length);
        stack = new int[0];
        isInitialized = false;
        isInQueue = false;
    }

    int getIndex() {
        return index;
    }

    int[] getLocals() {
        int[] copy = new int[locals.length];
        System.arraycopy(locals, 0, copy, 0, locals.length);
        return copy;
    }

    /**
     * Get a copy of the super block's locals without any trailing TOP types.
     *
     * This is useful for actual writing stack maps; during the computation of
     * stack map types, all local arrays have the same size; the max locals for
     * the method. In addition, DOUBLE and LONG types have trailing TOP types
     * because they occupy two words. For writing purposes, these are not
     * useful.
     */
    int[] getTrimmedLocals() {
        int last = locals.length - 1;
        // Exclude all of the trailing TOPs not bound to a DOUBLE/LONG
        while (last >= 0 && locals[last] == TypeInfo.TOP &&
               !TypeInfo.isTwoWords(locals[last - 1])) {
            last--;
        }
        last++;
        // Exclude trailing TOPs following a DOUBLE/LONG
        int size = last;
        for (int i = 0; i < last; i++) {
            if (TypeInfo.isTwoWords(locals[i])) {
                size--;
            }
        }
        int[] copy = new int[size];
        for (int i = 0, j = 0; i < size; i++, j++) {
            copy[i] = locals[j];
            if (TypeInfo.isTwoWords(locals[j])) {
                j++;
            }
        }
        return copy;
    }

    int[] getStack() {
        int[] copy = new int[stack.length];
        System.arraycopy(stack, 0, copy, 0, stack.length);
        return copy;
    }

    boolean merge(int[] locals, int localsTop, int[] stack, int stackTop,
                  ConstantPool pool) {
        if (!isInitialized) {
            System.arraycopy(locals, 0, this.locals, 0, localsTop);
            this.stack = new int[stackTop];
            System.arraycopy(stack, 0, this.stack, 0, stackTop);
            isInitialized = true;
            return true;
        } else if (this.locals.length == localsTop &&
                   this.stack.length == stackTop) {
            boolean localsChanged = mergeState(this.locals, locals, localsTop,
                                               pool);
            boolean stackChanged = mergeState(this.stack, stack, stackTop,
                                              pool);
            return localsChanged || stackChanged;
        } else {
            if (ClassFileWriter.StackMapTable.DEBUGSTACKMAP) {
                System.out.println("bad merge");
                System.out.println("current type state:");
                TypeInfo.print(this.locals, this.stack, pool);
                System.out.println("incoming type state:");
                TypeInfo.print(locals, localsTop, stack, stackTop, pool);
            }
            throw new IllegalArgumentException("bad merge attempt");
        }
    }

    /**
     * Merge an operand stack or local variable array with incoming state.
     *
     * They are treated the same way; by this point, it should already be
     * ensured that the array sizes are the same, which is the only additional
     * constraint that is imposed on merging operand stacks (the local variable
     * array is always the same size).
     */
    private boolean mergeState(int[] current, int[] incoming, int size,
                               ConstantPool pool) {
        boolean changed = false;
        for (int i = 0; i < size; i++) {
            int currentType = current[i];

            current[i] = TypeInfo.merge(current[i], incoming[i], pool);
            if (currentType != current[i]) {
                changed = true;
            }
        }
        return changed;
    }

    int getStart() {
        return start;
    }

    int getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return "sb " + index;
    }

    boolean isInitialized() {
        return isInitialized;
    }

    void setInitialized(boolean b) {
        isInitialized = b;
    }

    boolean isInQueue() {
        return isInQueue;
    }

    void setInQueue(boolean b) {
        isInQueue = b;
    }

    private int index;
    private int start;
    private int end;
    private int[] locals;
    private int[] stack;
    private boolean isInitialized;
    private boolean isInQueue;
}

/**
 * Helper class for internal representations of type information. In most
 * cases, type information can be represented by a constant, but in some
 * cases, a payload is included. Despite the payload coming after the type
 * tag in the output, we store it in bits 8-23 for uniformity; the tag is
 * always in bits 0-7.
 */
final class TypeInfo {
    private TypeInfo() { }

    static final int TOP = 0;
    static final int INTEGER = 1;
    static final int FLOAT = 2;
    static final int DOUBLE = 3;
    static final int LONG = 4;
    static final int NULL = 5;
    static final int UNINITIALIZED_THIS = 6;
    static final int OBJECT_TAG = 7;
    static final int UNINITIALIZED_VAR_TAG = 8;

    static final int OBJECT(int constantPoolIndex) {
        return ((constantPoolIndex & 0xFFFF) << 8) | OBJECT_TAG;
    }

    static final int OBJECT(String type, ConstantPool pool) {
        return OBJECT(pool.addClass(type));
    }

    static final int UNINITIALIZED_VARIABLE(int bytecodeOffset) {
        return ((bytecodeOffset & 0xFFFF) << 8) | UNINITIALIZED_VAR_TAG;
    }

    static final int getTag(int typeInfo) {
        return typeInfo & 0xFF;
    }

    static final int getPayload(int typeInfo) {
        return typeInfo >>> 8;
    }

    /**
     * Treat the result of getPayload as a constant pool index and fetch the
     * corresponding String mapped to it.
     *
     * Only works on OBJECT types.
     */
    static final String getPayloadAsType(int typeInfo, ConstantPool pool) {
        if (getTag(typeInfo) == OBJECT_TAG) {
            return (String) pool.getConstantData(getPayload(typeInfo));
        }
        throw new IllegalArgumentException("expecting object type");
    }

    /**
     * Create type information from an internal type.
     */
    static final int fromType(String type, ConstantPool pool) {
        if (type.length() == 1) {
            switch (type.charAt(0)) {
                case 'B': // sbyte
                case 'C': // unicode char
                case 'S': // short
                case 'Z': // boolean
                case 'I': // all of the above are verified as integers
                    return INTEGER;
                case 'D':
                    return DOUBLE;
                case 'F':
                    return FLOAT;
                case 'J':
                    return LONG;
                default:
                    throw new IllegalArgumentException("bad type");
            }
        }
        return TypeInfo.OBJECT(type, pool);
    }

    static boolean isTwoWords(int type) {
        return type == DOUBLE || type == LONG;
    }

    /**
     * Merge two verification types.
     *
     * In most cases, the verification types must be the same. For example,
     * INTEGER and DOUBLE cannot be merged and an exception will be thrown.
     * The basic rules are:
     *
     * - If the types are equal, simply return one.
     * - If either type is TOP, return TOP.
     * - If either type is NULL, return the other type.
     * - If both types are objects, find the lowest common ancestor in the
     *   class hierarchy.
     *
     * This method uses reflection to traverse the class hierarchy. Therefore,
     * it is assumed that the current class being generated is never the target
     * of a full object-object merge, which would need to load the current
     * class reflectively.
     */
    static int merge(int current, int incoming, ConstantPool pool) {
        int currentTag = getTag(current);
        int incomingTag = getTag(incoming);
        boolean currentIsObject = currentTag == TypeInfo.OBJECT_TAG;
        boolean incomingIsObject = incomingTag == TypeInfo.OBJECT_TAG;

        if (current == incoming || (currentIsObject && incoming == NULL)) {
            return current;
        } else if (currentTag == TypeInfo.TOP ||
            incomingTag == TypeInfo.TOP) {
            return TypeInfo.TOP;
        } else if (current == NULL && incomingIsObject) {
            return incoming;
        } else if (currentIsObject && incomingIsObject) {
            String currentName = getPayloadAsType(current, pool);
            String incomingName = getPayloadAsType(incoming, pool);
            // The class file always has the class and super names in the same
            // spot. The constant order is: class_data, class_name, super_data,
            // super_name.
            String currentlyGeneratedName = (String) pool.getConstantData(2);
            String currentlyGeneratedSuperName =
                    (String) pool.getConstantData(4);

            // If any of the merged types are the class that's currently being
            // generated, automatically start at the super class instead. At
            // this point, we already know the classes are different, so we
            // don't need to handle that case.
            if (currentName.equals(currentlyGeneratedName)) {
                currentName = currentlyGeneratedSuperName;
            }
            if (incomingName.equals(currentlyGeneratedName)) {
                incomingName = currentlyGeneratedSuperName;
            }

            Class<?> currentClass = getClassFromInternalName(currentName);
            Class<?> incomingClass = getClassFromInternalName(incomingName);

            if (currentClass.isAssignableFrom(incomingClass)) {
                return current;
            } else if (incomingClass.isAssignableFrom(currentClass)) {
                return incoming;
            } else if (incomingClass.isInterface() ||
                       currentClass.isInterface()) {
                // For verification purposes, Sun specifies that interfaces are
                // subtypes of Object. Therefore, we know that the merge result
                // involving interfaces where one is not assignable to the
                // other results in Object.
                return OBJECT("java/lang/Object", pool);
            } else {
                Class<?> commonClass = incomingClass.getSuperclass();
                while (commonClass != null) {
                    if (commonClass.isAssignableFrom(currentClass)) {
                        String name = commonClass.getName();
                        name = ClassFileWriter.getSlashedForm(name);
                        return OBJECT(name, pool);
                    }
                    commonClass = commonClass.getSuperclass();
                }
            }
        }
        throw new IllegalArgumentException("bad merge attempt between " +
                                           toString(current, pool) + " and " +
                                           toString(incoming, pool));
    }

    static String toString(int type, ConstantPool pool) {
        int tag = getTag(type);
        switch (tag) {
            case TypeInfo.TOP:
                return "top";
            case TypeInfo.INTEGER:
                return "int";
            case TypeInfo.FLOAT:
                return "float";
            case TypeInfo.DOUBLE:
                return "double";
            case TypeInfo.LONG:
                return "long";
            case TypeInfo.NULL:
                return "null";
            case TypeInfo.UNINITIALIZED_THIS:
                return "uninitialized_this";
            default:
                if (tag == TypeInfo.OBJECT_TAG) {
                    return getPayloadAsType(type, pool);
                } else if (tag == TypeInfo.UNINITIALIZED_VAR_TAG) {
                    return "uninitialized";
                } else {
                    throw new IllegalArgumentException("bad type");
                }
        }
    }

    /**
     * Take an internal name and return a java.lang.Class instance that
     * represents it.
     *
     * For example, given "java/lang/Object", returns the equivalent of
     * Class.forName("java.lang.Object"), but also handles exceptions.
     */
    static Class getClassFromInternalName(String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static String toString(int[] types, ConstantPool pool) {
        return toString(types, types.length, pool);
    }

    static String toString(int[] types, int typesTop, ConstantPool pool) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < typesTop; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(toString(types[i], pool));
        }
        sb.append("]");
        return sb.toString();
    }

    static void print(int[] locals, int[] stack, ConstantPool pool) {
        print(locals, locals.length, stack, stack.length, pool);
    }

    static void print(int[] locals, int localsTop, int[] stack, int stackTop,
                       ConstantPool pool) {
        System.out.print("locals: ");
        System.out.println(toString(locals, localsTop, pool));
        System.out.print("stack: ");
        System.out.println(toString(stack, stackTop, pool));
        System.out.println();
    }
}
