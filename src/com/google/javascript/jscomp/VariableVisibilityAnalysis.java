/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Map;

/**
 * An analysis pass that determines the visibility of variables -- that is,
 * whether a variable is truly local, a local captured by an inner scope, a
 * parameter, or a global variable.
 * 
 * SideEffectsAnalysis uses this class to partition a potentially infinite
 * number of concrete storage locations into a (small) finite number of
 * abstract storage locations based on a variable's storage visibility.
 * 
 * @author dcc@google.com (Devin Coughlin)
 */
public class VariableVisibilityAnalysis implements CompilerPass {

  enum VariableVisibility {
    
    /** Local variable, not captured by closure */
    LOCAL,
    
    /** Local variable captured by a closure */
    CAPTURED_LOCAL,
    
    /** 
     * Formal parameter declaration variable
     * 
     * Parameters are different than local variables because they can be
     * aliased by elements of the arguments object.
     */
    PARAMETER,
    
    /** A global variable */
    GLOBAL
  }
  
  private AbstractCompiler compiler;
  
  /**
   * Maps the declaring name node for a variable to that variable's
   * visibility.
   */
  private Map<Node, VariableVisibility> visibilityByDeclaringNameNode;
  
  public VariableVisibilityAnalysis(AbstractCompiler compiler) {
    this.compiler = compiler;
   
    visibilityByDeclaringNameNode = Maps.newHashMap();
  }
  
  /**
   * Returns the visibility of of a variable, given that variable's declaring
   * name node.
   * 
   * The name node's parent must be one of:
   * <pre>
   *    Token.VAR (for a variable declaration)
   *    Token.FUNCTION (for a function declaration)
   *    Token.LP (for a function formal parameter)
   * </pre> 
   * 
   * The returned visibility will be one of:
   * <pre>
   *    LOCAL_VARIABLE : the variable is a local variable used only in its
   *        declared scope
   *    CAPTURED_LOCAL_VARIABLE : A local variable that is used in a capturing
   *        closure
   *     PARAMETER_VARIABLE : the variable is a formal parameter
   *     GLOBAL_VARIABLE : the variable is declared in the global scope
   *  </pre>  
   *    
   * @param declaringNameNode The name node for a declaration.
   */
  public VariableVisibility getVariableVisibility(Node declaringNameNode) {
    Node parent = declaringNameNode.getParent();
    
    Preconditions.checkArgument(NodeUtil.isVar(parent)
        || NodeUtil.isFunction(parent)
        || parent.getType() == Token.LP);
    
    return visibilityByDeclaringNameNode.get(declaringNameNode);
  }
 
  /**
   * Determines the visibility class for each variable in root.
   */
  @Override
  public void process(Node externs, Node root) {
    ReferenceCollectingCallback callback = 
      new ReferenceCollectingCallback(compiler, 
          ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR);
    
    NodeTraversal.traverse(compiler, root, callback);
    
    for (Var variable : callback.getReferencedVariables()) {
      ReferenceCollection referenceCollection =
          callback.getReferenceCollection(variable);
      
      VariableVisibility visibility;
      
      if (variableIsParameter(variable)) {
        visibility = VariableVisibility.PARAMETER;     
      } else if (variable.isLocal()) {
        if (referenceCollection.isEscaped()) {
          visibility = VariableVisibility.CAPTURED_LOCAL;        
        } else {
          visibility = VariableVisibility.LOCAL;          
        }
      } else if (variable.isGlobal()) {
        visibility = VariableVisibility.GLOBAL;
      } else {
        throw new IllegalStateException("Un-handled variable visibility for " +
            variable);
      }
      
      visibilityByDeclaringNameNode.put(variable.getNameNode(), visibility);
    }   
  }
  
  /**
   * Returns true if the variable is a formal parameter.
   */
  private static boolean variableIsParameter(Var variable) {
    Node variableParent = variable.getParentNode();
    
    return variableParent != null && variableParent.getType() == Token.LP;
  }
}
