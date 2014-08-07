/*
 * Operator.java
 *
 * Copyright (C) 2008  Pei Wang
 *
 * This file is part of Open-NARS.
 *
 * Open-NARS is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Open-NARS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open-NARS.  If not, see <http://www.gnu.org/licenses/>.
 */

package nars.operator;

import java.util.List;
import nars.entity.Task;
import nars.io.Output.EXE;
import nars.language.Atom;
import nars.language.Statement;
import nars.language.Term;
import nars.storage.Memory;

/**
 * An individual operator that can be execute by the system, which can be either
 * inside NARS or outside it, in another system or device.
 * <p>
 * This is the only file to modify when registering a new operator into NARS.
 */
public abstract class Operator extends Atom {
        
    protected Operator(String name) {
        super(name);
    }
    
    /**
     * Required method for every operator, specifying the corresponding
     * operation
     *
     * @param args The task with the arguments to be passed to the operator
     * @return The direct collectable results and feedback of the
     * reportExecution
     */
    protected abstract List<Task> execute(Term[] args, Memory memory);

    /**
    * The standard way to carry out an operation, which invokes the execute
    * method defined for the operator, and handles feedback tasks as input
    *
    * @param op The operator to be executed
    * @param args The arguments to be taken by the operator
    * @param memory The memory on which the operation is executed
    */
    public void call(final Operator op, final Term[] args, final Memory memory) {
        List<Task> feedback = op.execute(args, memory);
        reportExecution(op, args, memory);
        if (feedback != null) {
            for (Task t : feedback) {
                memory.inputTask(t);
            }
        }
    }
    
   
    /**
     * Display a message in the output stream to indicate the reportExecution of
     * an operation
     * <p>
     * @param operation The content of the operation to be executed
     */
    public static void reportExecution(final Operator operator, final Term[] args, final Memory memory) {
        StringBuilder buffer = new StringBuilder();
        for (Object obj : args) {
            buffer.append(obj).append(",");
        }
        System.out.println("EXECUTE: " + operator + "(" + buffer.toString() + ")");
        memory.output(EXE.class, operator + "(" + args);
    }
    
    public static String operationExecutionString(final Statement operation) {
        Term operator = operation.getPredicate();
        Term arguments = operation.getSubject();
        String argList = arguments.toString().substring(3);         // skip the product prefix "(*,"
        return operator + "(" + argList;        
    }

    
}

