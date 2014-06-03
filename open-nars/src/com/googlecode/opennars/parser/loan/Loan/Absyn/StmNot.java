package com.googlecode.opennars.parser.loan.Loan.Absyn; // Java Package generated by the BNF Converter.

public class StmNot extends Stm {
  public final Stm stm_;

  public StmNot(Stm p1) { stm_ = p1; }

  public <R,A> R accept(com.googlecode.opennars.parser.loan.Loan.Absyn.Stm.Visitor<R,A> v, A arg) { return v.visit(this, arg); }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof com.googlecode.opennars.parser.loan.Loan.Absyn.StmNot) {
      com.googlecode.opennars.parser.loan.Loan.Absyn.StmNot x = (com.googlecode.opennars.parser.loan.Loan.Absyn.StmNot)o;
      return this.stm_.equals(x.stm_);
    }
    return false;
  }

  public int hashCode() {
    return this.stm_.hashCode();
  }


}
