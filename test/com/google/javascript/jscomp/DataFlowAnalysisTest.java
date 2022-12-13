/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowJoiner;
import com.google.javascript.jscomp.DataFlowAnalysis.LinearFlowState;
import com.google.javascript.jscomp.NodeUtil.AllVarsDeclaredInFunction;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A test suite with a very small programming language that has two types of instructions: {@link
 * BranchInstruction} and {@link ArithmeticInstruction}. Test cases must construct a small program
 * with these instructions and manually put each instruction in a {@code ControlFlowGraph}.
 *
 */
@RunWith(JUnit4.class)
public final class DataFlowAnalysisTest {

  /**
   * Operations supported by ArithmeticInstruction.
   */
  enum Operation {
    ADD("+"), SUB("-"), DIV("/"), MUL("*");
    private final String stringRep;

    private Operation(String stringRep) {
      this.stringRep = stringRep;
    }

    @Override
    public String toString() {
      return stringRep;
    }
  }

  /**
   * A simple value.
   */
  abstract static class Value {

    boolean isNumber() {
      return this instanceof NumberValue;
    }

    boolean isVariable() {
      return this instanceof Variable;
    }
  }

  /**
   * A variable.
   */
  static class Variable extends Value {
    private final String name;

    /**
     * Constructor.
     *
     * @param n Name of the variable.
     */
    Variable(String n) {
      name = n;
    }

    String getName() {
      return name;
    }

    @Override
    public boolean equals(Object other) {
      // Use the String's .equals()
      if (!(other instanceof Variable)) {
        return false;
      }
      return ((Variable) other).name.equals(name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  /**
   * A number constant.
   */
  static class NumberValue extends Value {
    private final int value;

    /**
     * Constructor
     *
     * @param v Value
     */
    NumberValue(int v) {
      value = v;
    }

    int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "" + value;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof NumberValue)) {
        return false;
      }
      return ((NumberValue) other).value == value;
    }

    @Override
    public int hashCode() {
      return value;
    }
  }

  /**
   * An instruction of the dummy program.
   */
  abstract static class Instruction {

    int order = 0;

    /**
     * Check whether this is an arithmetic instruction.
     *
     * @return {@code true} if it is an arithmetic instruction.
     */
    boolean isArithmetic() {
      return this instanceof ArithmeticInstruction;
    }

    /**
     * Check whether this is a branch instruction.
     *
     * @return {@code true} if it is a branch instruction.
     */
    boolean isBranch() {
      return this instanceof BranchInstruction;
    }
  }

  /**
   * Basic arithmetic instruction that only takes the form of:
   *
   * <pre>
   * Result = Operand1 operator Operand2
   * </pre>
   */
  static class ArithmeticInstruction extends Instruction {
    private Operation operation;
    private Value operand1;
    private Value operand2;
    private Variable result;

    /**
     * Constructor
     *
     * @param res Result.
     * @param op1 First Operand.
     * @param o Operator.
     * @param op2 Second Operand.
     */
    ArithmeticInstruction(Variable res, int op1, Operation o, int op2) {
      this(res, new NumberValue(op1), o, new NumberValue(op2));
    }

    /**
     * Constructor
     *
     * @param res Result.
     * @param op1 First Operand.
     * @param o Operator.
     * @param op2 Second Operand.
     */
    ArithmeticInstruction(Variable res, Value op1, Operation o, int op2) {
      this(res, op1, o, new NumberValue(op2));
    }

    /**
     * Constructor
     *
     * @param res Result.
     * @param op1 First Operand.
     * @param o Operator.
     * @param op2 Second Operand.
     */
    ArithmeticInstruction(Variable res, int op1, Operation o, Value op2) {
      this(res, new NumberValue(op1), o, op2);
    }

    /**
     * Constructor
     *
     * @param res Result.
     * @param op1 First Operand.
     * @param o Operator.
     * @param op2 Second Operand.
     */
    ArithmeticInstruction(Variable res, Value op1, Operation o, Value op2) {
      result = res;
      operand1 = op1;
      operand2 = op2;
      operation = o;
    }

    Operation getOperator() {
      return operation;
    }

    void setOperator(Operation op) {
      this.operation = op;
    }

    Value getOperand1() {
      return operand1;
    }

    void setOperand1(Value operand1) {
      this.operand1 = operand1;
    }

    Value getOperand2() {
      return operand2;
    }

    void setOperand2(Value operand2) {
      this.operand2 = operand2;
    }

    Variable getResult() {
      return result;
    }

    void setResult(Variable result) {
      this.result = result;
    }

    @Override
    public String toString() {
      StringBuilder out = new StringBuilder();
      out.append(result);
      out.append(" = ");
      out.append(operand1);
      out.append(operation);
      out.append(operand2);
      return out.toString();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ArithmeticInstruction)) {
        return false;
      }
      ArithmeticInstruction that = (ArithmeticInstruction) other;
      return that.order == this.order
          && that.operation.equals(this.operation)
          && that.operand1.equals(this.operand1)
          && that.operand2.equals(this.operand2)
          && that.result.equals(this.result);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }
  }

  public static ArithmeticInstruction newAssignNumberToVariableInstruction(Variable res, int num) {
    return new ArithmeticInstruction(res, num, Operation.ADD, 0);
  }

  public static ArithmeticInstruction newAssignVariableToVariableInstruction(
      Variable lhs, Variable rhs) {
    return new ArithmeticInstruction(lhs, rhs, Operation.ADD, 0);
  }

  /**
   * Branch instruction based on a {@link Value} as a condition.
   */
  static class BranchInstruction extends Instruction {
    private Value condition;

    BranchInstruction(Value cond) {
      condition = cond;
    }

    Value getCondition() {
      return condition;
    }

    void setCondition(Value condition) {
      this.condition = condition;
    }
  }

  /**
   * A lattice to represent constant states. Each variable of the program will
   * have a lattice defined as:
   *
   * <pre>
   *        TOP
   *   / / |         \
   *  0  1 2 3 ..... MAX_VALUE
   *  \  \ |         /
   *       BOTTOM
   * </pre>
   *
   * Where BOTTOM represents the variable is not a constant.
   * <p>
   * This class will represent a product lattice of each variable's lattice. The
   * whole lattice is store in a {@code HashMap}. If variable {@code x} is
   * defined to be constant 10. The map will contain the value 10 with the
   * variable {@code x} as key. Otherwise, {@code x} is not a constant.
   */
  private static class ConstPropLatticeElement implements LatticeElement {
    private final Map<Variable, Integer> constMap;
    private final boolean isTop;

    /**
     * Constructor.
     *
     * @param isTop To define if the lattice is top.
     */
    ConstPropLatticeElement(boolean isTop) {
      this.isTop = isTop;
      this.constMap = new HashMap<>();
    }

    /**
     * Create a lattice where every variable is defined to be not constant.
     */
    ConstPropLatticeElement() {
      this(false);
    }

    ConstPropLatticeElement(ConstPropLatticeElement other) {
      this.isTop = other.isTop;
      this.constMap = new HashMap<>(other.constMap);
    }

    @Override
    public String toString() {
      if (isTop) {
        return "TOP";
      }
      StringBuilder out = new StringBuilder();

      out.append("{");
      for (Variable var : constMap.keySet()) {
        out.append(var);
        out.append("=");
        out.append(constMap.get(var));
        out.append(" ");
      }
      out.append("}");
      return out.toString();
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof ConstPropLatticeElement) {
        ConstPropLatticeElement otherLattice = (ConstPropLatticeElement) other;
        return (this.isTop == otherLattice.isTop) &&
            this.constMap.equals(otherLattice.constMap);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return constMap.hashCode();
    }
  }

  private static class ConstPropJoinOp implements FlowJoiner<ConstPropLatticeElement> {

    ConstPropLatticeElement result;

    @Override
    public void joinFlow(ConstPropLatticeElement input) {
      this.result = (this.result == null) ? input : apply(this.result, input);
    }

    @Override
    public ConstPropLatticeElement finish() {
      return this.result;
    }

    private static ConstPropLatticeElement apply(
        ConstPropLatticeElement a, ConstPropLatticeElement b) {
      ConstPropLatticeElement result = new ConstPropLatticeElement();
      // By the definition of TOP of the lattice.
      if (a.isTop) {
        return new ConstPropLatticeElement(a);
      }
      if (b.isTop) {
        return new ConstPropLatticeElement(b);
      }
      // Do the join for each variable's lattice.
      for (Variable var : a.constMap.keySet()) {
        if (b.constMap.containsKey(var)) {
          Integer number = b.constMap.get(var);

          // The result will contain that variable as a known constant
          // if both lattice has that variable the same constant.
          if (a.constMap.get(var).equals(number)) {
            result.constMap.put(var, number);
          }
        }
      }
      return result;
    }
  }

  /**
   * A simple forward constant propagation.
   */
  static class DummyConstPropagation extends
      DataFlowAnalysis<Instruction, ConstPropLatticeElement> {

    /**
     * Constructor.
     *
     * @param targetCfg Control Flow Graph.
     */
    DummyConstPropagation(ControlFlowGraph<Instruction> targetCfg) {
      super(targetCfg);
    }

    @Override
    boolean isForward() {
      return true;
    }

    @Override
    FlowJoiner<ConstPropLatticeElement> createFlowJoiner() {
      return new ConstPropJoinOp();
    }

    @Override
    ConstPropLatticeElement flowThrough(Instruction node,
        ConstPropLatticeElement input) {
      if (node.isBranch()) {
        return new ConstPropLatticeElement(input);
      } else {
        return flowThroughArithmeticInstruction((ArithmeticInstruction) node,
            input);
      }
    }

    @Override
    ConstPropLatticeElement createEntryLattice() {
      return new ConstPropLatticeElement();
    }

    @Override
    ConstPropLatticeElement createInitialEstimateLattice() {
      return new ConstPropLatticeElement(true);
    }
  }

  static ConstPropLatticeElement flowThroughArithmeticInstruction(
      ArithmeticInstruction aInst, ConstPropLatticeElement input) {

    ConstPropLatticeElement out = new ConstPropLatticeElement(input);
    // Try to see if left is a number. If it is a variable, it might already
    // be a constant coming in.
    Integer leftConst = null;
    if (aInst.operand1.isNumber()) {
      leftConst = ((NumberValue) aInst.operand1).value;
    } else {
      if (input.constMap.containsKey(aInst.operand1)) {
        leftConst = input.constMap.get(aInst.operand1);
      }
    }

    // Do the same thing to the right.
    Integer rightConst = null;
    if (aInst.operand2.isNumber()) {
      rightConst = ((NumberValue) aInst.operand2).value;
    } else {
      if (input.constMap.containsKey(aInst.operand2)) {
        rightConst = input.constMap.get(aInst.operand2);
      }
    }

    // If both are known constant we can perform the operation.
    if (leftConst != null && rightConst != null) {
      Integer constResult = null;
      if (aInst.operation == Operation.ADD) {
        constResult = leftConst.intValue() + rightConst.intValue();
      } else if (aInst.operation == Operation.SUB) {
        constResult = leftConst.intValue() - rightConst.intValue();
      } else if (aInst.operation == Operation.MUL) {
        constResult = leftConst.intValue() * rightConst.intValue();
      } else if (aInst.operation == Operation.DIV) {
        constResult = leftConst.intValue() / rightConst.intValue();
      }
      // Put it in the map. (Possibly replacing the existing constant value)
      out.constMap.put(aInst.result, constResult);
    } else {
      // If we cannot find a constant for it
      out.constMap.remove(aInst.result);
    }
    return out;
  }

  @Test
  public void testSimpleIf() {
    // if (a) { b = 1; } else { b = 1; } c = b;
    Variable a = new Variable("a");
    Variable b = new Variable("b");
    Variable c = new Variable("c");
    Instruction inst1 = new BranchInstruction(a);
    Instruction inst2 = newAssignNumberToVariableInstruction(b, 1);
    Instruction inst3 = newAssignNumberToVariableInstruction(b, 1);
    Instruction inst4 = newAssignVariableToVariableInstruction(c, b);
    ControlFlowGraph<Instruction> cfg = new ControlFlowGraph<>(inst1, true, true);
    GraphNode<Instruction, Branch> n1 = cfg.createNode(inst1);
    GraphNode<Instruction, Branch> n2 = cfg.createNode(inst2);
    GraphNode<Instruction, Branch> n3 = cfg.createNode(inst3);
    GraphNode<Instruction, Branch> n4 = cfg.createNode(inst4);
    cfg.connect(inst1, ControlFlowGraph.Branch.ON_FALSE, inst2);
    cfg.connect(inst1, ControlFlowGraph.Branch.ON_TRUE, inst3);
    cfg.connect(inst2, ControlFlowGraph.Branch.UNCOND, inst4);
    cfg.connect(inst3, ControlFlowGraph.Branch.UNCOND, inst4);

    DummyConstPropagation constProp = new DummyConstPropagation(cfg);
    constProp.analyze();

    // We cannot conclude anything from if (a).
    verifyInHas(n1, a, null);
    verifyInHas(n1, b, null);
    verifyInHas(n1, c, null);
    verifyOutHas(n1, a, null);
    verifyOutHas(n1, b, null);
    verifyOutHas(n1, c, null);

    // We can conclude b = 1 after the instruction.
    verifyInHas(n2, a, null);
    verifyInHas(n2, b, null);
    verifyInHas(n2, c, null);
    verifyOutHas(n2, a, null);
    verifyOutHas(n2, b, 1);
    verifyOutHas(n2, c, null);

    // Same as above.
    verifyInHas(n3, a, null);
    verifyInHas(n3, b, null);
    verifyInHas(n3, c, null);
    verifyOutHas(n3, a, null);
    verifyOutHas(n3, b, 1);
    verifyOutHas(n3, c, null);

    // After the merge we should still have b = 1.
    verifyInHas(n4, a, null);
    verifyInHas(n4, b, 1);
    verifyInHas(n4, c, null);
    verifyOutHas(n4, a, null);
    // After the instruction both b and c are 1.
    verifyOutHas(n4, b, 1);
    verifyOutHas(n4, c, 1);
  }

  @Test
  public void testSimpleLoop() {
    // a = 0; do { a = a + 1 } while (b); c = a;
    Variable a = new Variable("a");
    Variable b = new Variable("b");
    Variable c = new Variable("c");
    Instruction inst1 = newAssignNumberToVariableInstruction(a, 0);
    Instruction inst2 = new ArithmeticInstruction(a, a, Operation.ADD, 1);
    Instruction inst3 = new BranchInstruction(b);
    Instruction inst4 = newAssignVariableToVariableInstruction(c, a);
    ControlFlowGraph<Instruction> cfg = new ControlFlowGraph<>(inst1, true, true);
    GraphNode<Instruction, Branch> n1 = cfg.createNode(inst1);
    GraphNode<Instruction, Branch> n2 = cfg.createNode(inst2);
    GraphNode<Instruction, Branch> n3 = cfg.createNode(inst3);
    GraphNode<Instruction, Branch> n4 = cfg.createNode(inst4);
    cfg.connect(inst1, ControlFlowGraph.Branch.UNCOND, inst2);
    cfg.connect(inst2, ControlFlowGraph.Branch.UNCOND, inst3);
    cfg.connect(inst3, ControlFlowGraph.Branch.ON_TRUE, inst2);
    cfg.connect(inst3, ControlFlowGraph.Branch.ON_FALSE, inst4);

    DummyConstPropagation constProp = new DummyConstPropagation(cfg);
    // This will also show that the framework terminates properly.
    constProp.analyze();

    // a = 0 is the only thing we know.
    verifyInHas(n1, a, null);
    verifyInHas(n1, b, null);
    verifyInHas(n1, c, null);
    verifyOutHas(n1, a, 0);
    verifyOutHas(n1, b, null);
    verifyOutHas(n1, c, null);

    // Nothing is provable in this program, so confirm that we haven't
    // erroneously "proven" something.
    verifyInHas(n2, a, null);
    verifyInHas(n2, b, null);
    verifyInHas(n2, c, null);
    verifyOutHas(n2, a, null);
    verifyOutHas(n2, b, null);
    verifyOutHas(n2, c, null);

    verifyInHas(n3, a, null);
    verifyInHas(n3, b, null);
    verifyInHas(n3, c, null);
    verifyOutHas(n3, a, null);
    verifyOutHas(n3, b, null);
    verifyOutHas(n3, c, null);

    verifyInHas(n4, a, null);
    verifyInHas(n4, b, null);
    verifyInHas(n4, c, null);
    verifyOutHas(n4, a, null);
    verifyOutHas(n4, b, null);
    verifyOutHas(n4, c, null);
  }

  // tests for computeEscaped method

  @Test
  public void testEscaped() {
    assertThat(
            computeEscapedLocals(
                "function f() {",
                "    var x = 0; ",
                "    setTimeout(function() { x++; }); ",
                "    alert(x);",
                "}"))
        .hasSize(1);
    assertThat(computeEscapedLocals("function f() {var _x}")).hasSize(1);
    assertThat(computeEscapedLocals("function f() {try{} catch(e){}}")).hasSize(1);
  }

  @Test
  public void testEscapedFunctionLayered() {
    assertThat(
            computeEscapedLocals(
                "function f() {",
                "    function ff() {",
                "        var x = 0; ",
                "        setTimeout(function() { x++; }); ",
                "        alert(x);",
                "    }",
                "}"))
        .isEmpty();
  }

  @Test
  public void testEscapedLetConstSimple() {
    assertThat(computeEscapedLocals("function f() { let x = 0; x ++; x; }")).isEmpty();
  }

  @Test
  public void testEscapedFunctionAssignment() {
    assertThat(computeEscapedLocals("function f() {var x = function () { return 1; }; }"))
        .isEmpty();
    assertThat(computeEscapedLocals("function f() {var x = function (y) { return y; }; }"))
        .isEmpty();
    assertThat(computeEscapedLocals("function f() {let x = function () { return 1; }; }"))
        .isEmpty();
  }

  @Test
  public void testEscapedArrowFunction() {
    // When the body of the arrow fn is analyzed, x is considered an escaped var. When the outer
    // block containing "const value ..." is analyzed, 'x' is not considered an escaped var
    assertThat(
            computeEscapedLocals(
                "function f() {const value = () => {",
                "    var x = 0; ",
                "    setTimeout(function() { x++; }); ",
                "    alert(x);",
                " };}"))
        .isEmpty();
  }

  // test computeEscaped helper method that returns the liveness analysis performed by the
  // LiveVariablesAnalysis class
  public Set<? extends Var> computeEscapedLocals(String... lines) {
    // Set up compiler
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.initOptions(options);
    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);

    String src = CompilerTestCase.lines(lines);
    Node n = compiler.parseTestCode(src).removeFirstChild();
    Node script = new Node(Token.SCRIPT, n);
    script.setInputId(new InputId("test"));
    assertThat(compiler.getErrors()).isEmpty();

    // Create scopes
    SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);
    Scope scope = scopeCreator.createScope(n, Scope.createGlobalScope(script));
    Scope childScope;
    if (script.getFirstChild().isFunction()) {
      childScope = scopeCreator.createScope(NodeUtil.getFunctionBody(n), scope);
    } else {
      childScope = null;
    }

    // Control flow graph
    ControlFlowGraph<Node> cfg =
        ControlFlowAnalysis.builder()
            .setCompiler(compiler)
            .setCfgRoot(script)
            .setIncludeEdgeAnnotations(true)
            .computeCfg();

    // All variables declared in function
    AllVarsDeclaredInFunction allVarsDeclaredInFunction =
        NodeUtil.getAllVarsDeclaredInFunction(compiler, scopeCreator, scope);

    // Compute liveness of variables
    LiveVariablesAnalysis analysis =
        new LiveVariablesAnalysis(
            cfg, scope, childScope, compiler, scopeCreator, allVarsDeclaredInFunction);
    analysis.analyze();
    return analysis.getEscapedLocals();
  }

  /** A simple forward constant propagation. */
  static class BranchedDummyConstPropagation
      extends DataFlowAnalysis<Instruction, ConstPropLatticeElement> {

    BranchedDummyConstPropagation(ControlFlowGraph<Instruction> targetCfg) {
      super(targetCfg);
    }

    @Override
    boolean isForward() {
      return true;
    }

    @Override
    boolean isBranched() {
      return true;
    }

    @Override
    ConstPropLatticeElement flowThrough(Instruction node,
        ConstPropLatticeElement input) {
      if (node.isArithmetic()) {
        return flowThroughArithmeticInstruction(
            (ArithmeticInstruction) node, input);
      } else {
        return new ConstPropLatticeElement(input);
      }
    }

    @Override
    FlowBrancher<ConstPropLatticeElement> createFlowBrancher(
        Instruction node, ConstPropLatticeElement input) {
      ConstPropLatticeElement aResult =
          node.isArithmetic()
              ? flowThroughArithmeticInstruction((ArithmeticInstruction) node, input)
              : null;

      return (Branch branch) -> {
        if (aResult != null) {
          return aResult;
        }

        BranchInstruction branchInst = (BranchInstruction) node;
        ConstPropLatticeElement edgeResult = new ConstPropLatticeElement(input);
        if (branch == Branch.ON_FALSE && branchInst.getCondition().isVariable()) {
          edgeResult.constMap.put((Variable) branchInst.getCondition(), 0);
        }
        return edgeResult;
      };
    }

    @Override
    ConstPropLatticeElement createEntryLattice() {
      return new ConstPropLatticeElement();
    }

    @Override
    ConstPropLatticeElement createInitialEstimateLattice() {
      return new ConstPropLatticeElement(true);
    }

    @Override
    FlowJoiner<ConstPropLatticeElement> createFlowJoiner() {
      return new ConstPropJoinOp();
    }
  }

  @Test
  public void testBranchedSimpleIf() {
    // if (a) { a = 0; } else { b = 0; } c = b;
    Variable a = new Variable("a");
    Variable b = new Variable("b");
    Variable c = new Variable("c");
    Instruction inst1 = new BranchInstruction(a);
    Instruction inst2 = newAssignNumberToVariableInstruction(a, 0);
    Instruction inst3 = newAssignNumberToVariableInstruction(b, 0);
    Instruction inst4 = newAssignVariableToVariableInstruction(c, b);
    ControlFlowGraph<Instruction> cfg = new ControlFlowGraph<>(inst1, true, true);
    GraphNode<Instruction, Branch> n1 = cfg.createNode(inst1);
    GraphNode<Instruction, Branch> n2 = cfg.createNode(inst2);
    GraphNode<Instruction, Branch> n3 = cfg.createNode(inst3);
    GraphNode<Instruction, Branch> n4 = cfg.createNode(inst4);
    cfg.connect(inst1, ControlFlowGraph.Branch.ON_TRUE, inst2);
    cfg.connect(inst1, ControlFlowGraph.Branch.ON_FALSE, inst3);
    cfg.connect(inst2, ControlFlowGraph.Branch.UNCOND, inst4);
    cfg.connect(inst3, ControlFlowGraph.Branch.UNCOND, inst4);

    BranchedDummyConstPropagation constProp =
        new BranchedDummyConstPropagation(cfg);
    constProp.analyze();

    // We cannot conclude anything from if (a).
    verifyInHas(n1, a, null);
    verifyInHas(n1, b, null);
    verifyInHas(n1, c, null);

    // Nothing is known on the true branch.
    verifyInHas(n2, a, null);
    verifyInHas(n2, b, null);
    verifyInHas(n2, c, null);

    // Verify that we have a = 0 on the false branch.
    verifyInHas(n3, a, 0);
    verifyInHas(n3, b, null);
    verifyInHas(n3, c, null);

    // After the merge we should still have a = 0.
    verifyInHas(n4, a, 0);
  }

  static final class DivergentAnalysis
      extends DataFlowAnalysis<DivergentAnalysis.Counter, DivergentAnalysis.Step> {

    static final class Counter {
      int count = 0;
    }

    static final class Step implements LatticeElement {

      Step() {}

      @Override
      public int hashCode() {
        return 0;
      }

      @Override
      public boolean equals(Object o) {
        return false;
      }
    }

    /**
     * Constructor.
     *
     * @param targetCfg Control Flow Graph.
     */
    DivergentAnalysis(ControlFlowGraph<Counter> targetCfg) {
      super(targetCfg);
    }

    @Override
    boolean isForward() {
      return true;
    }

    @Override
    Step flowThrough(Counter node, Step input) {
      node.count++;
      return input;
    }

    @Override
    Step createEntryLattice() {
      return new Step();
    }

    @Override
    Step createInitialEstimateLattice() {
      return new Step();
    }

    @Override
    FlowJoiner<DivergentAnalysis.Step> createFlowJoiner() {
      return new FlowJoiner<DivergentAnalysis.Step>() {
        @Override
        public void joinFlow(DivergentAnalysis.Step x) {}

        @Override
        public DivergentAnalysis.Step finish() {
          return new Step();
        }
      };
    }
  }

  @Test
  public void testMaxIterationsExceededException() {
    DivergentAnalysis.Counter entrypoint = new DivergentAnalysis.Counter();
    ControlFlowGraph<DivergentAnalysis.Counter> cfg =
        new ControlFlowGraph<>(entrypoint, true, true);

    cfg.connect(entrypoint, ControlFlowGraph.Branch.UNCOND, entrypoint);
    DivergentAnalysis constProp = new DivergentAnalysis(cfg);

    Exception e = assertThrows(Exception.class, constProp::analyze);
    assertThat(entrypoint.count).isEqualTo(DataFlowAnalysis.MAX_STEPS_PER_NODE + 1);
    assertThat(e).hasMessageThat().startsWith("Dataflow analysis appears to diverge around: ");
  }

  static void verifyInHas(
      GraphNode<Instruction, Branch> node, Variable var, @Nullable Integer constant) {
    LinearFlowState<ConstPropLatticeElement> fState = node.getAnnotation();
    veritfyLatticeElementHas(fState.getIn(), var, constant);
  }

  static void verifyOutHas(
      GraphNode<Instruction, Branch> node, Variable var, @Nullable Integer constant) {
    LinearFlowState<ConstPropLatticeElement> fState = node.getAnnotation();
    veritfyLatticeElementHas(fState.getOut(), var, constant);
  }

  static void veritfyLatticeElementHas(ConstPropLatticeElement el, Variable var, Integer constant) {
    if (constant == null) {
      assertThat(el.constMap).doesNotContainKey(var);
    } else {
      assertThat(el.constMap).containsEntry(var, constant);
    }
  }
}
