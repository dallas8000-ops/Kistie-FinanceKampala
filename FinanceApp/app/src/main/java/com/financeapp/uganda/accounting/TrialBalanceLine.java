package com.financeapp.uganda.accounting;

public class TrialBalanceLine {
    public int accountId;
    public String code;
    public String name;
    public String type;
    public double debit;
    public double credit;

    public TrialBalanceLine(int accountId, String code, String name, String type, double debit, double credit) {
        this.accountId = accountId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.debit = debit;
        this.credit = credit;
    }
}
