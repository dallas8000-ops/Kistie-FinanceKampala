package com.financeapp.uganda.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "journal_lines")
public class JournalLine {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private int journalEntryId;
    private int accountId;
    private double debit;
    private double credit;
    private String memo;

    public JournalLine() {}

    public JournalLine(int journalEntryId, int accountId, double debit, double credit, String memo) {
        this.journalEntryId = journalEntryId;
        this.accountId = accountId;
        this.debit = debit;
        this.credit = credit;
        this.memo = memo;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public double getDebit() { return debit; }
    public void setDebit(double debit) { this.debit = debit; }
    public double getCredit() { return credit; }
    public void setCredit(double credit) { this.credit = credit; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}
