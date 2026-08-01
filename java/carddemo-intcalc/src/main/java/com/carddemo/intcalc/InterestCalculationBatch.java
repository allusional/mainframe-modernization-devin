package com.carddemo.intcalc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Java port of the PROCEDURE DIVISION of {@code app/cbl/CBACT04C.cbl} (INTCALC), the CardDemo
 * monthly interest calculator, paragraph for paragraph. It reads TCATBALF in key sequence,
 * looks up the interest rate of every account group / transaction type / category, writes one
 * TRANFILE transaction per interest amount and, when the account changes, adds the interest
 * totalled so far to the account balance and resets the current cycle credit and debit.
 *
 * <p>The class holds no I/O: the five COBOL files are behind the reader/repository/writer
 * interfaces and every COBOL {@code DISPLAY} goes to the {@code display} sink.
 */
public class InterestCalculationBatch {

    private static final BigDecimal MONTHS_PER_YEAR_PERCENT = new BigDecimal("1200");

    private final TranCatBalanceReader tranCatBalances;
    private final XrefRepository xrefs;
    private final DiscGroupRepository discGroups;
    private final AccountRepository accounts;
    private final TransactionWriter transactions;
    private final Db2Timestamp timestamp;
    private final Consumer<String> display;
    private final String parmDate;

    /** WS-LAST-ACCT-NUM PIC X(11) VALUE SPACES. */
    private String lastAcctNum = " ".repeat(11);
    /** WS-FIRST-TIME PIC X(01) VALUE 'Y'. */
    private boolean firstTime = true;
    /** WS-TOTAL-INT PIC S9(09)V99. */
    private BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);
    /** WS-MONTHLY-INT PIC S9(09)V99. */
    private BigDecimal monthlyInterest = BigDecimal.ZERO.setScale(2);
    /** WS-RECORD-COUNT PIC 9(09). */
    private long recordCount;
    /** WS-TRANID-SUFFIX PIC 9(06). */
    private long tranIdSuffix;
    /** END-OF-FILE PIC X(01) VALUE 'N'. */
    private boolean endOfFile;

    private Account account;
    private CardXref xref;
    private DiscGroup discGroup;

    public InterestCalculationBatch(TranCatBalanceReader tranCatBalances,
                                    XrefRepository xrefs,
                                    DiscGroupRepository discGroups,
                                    AccountRepository accounts,
                                    TransactionWriter transactions,
                                    Db2Timestamp timestamp,
                                    Consumer<String> display,
                                    String parmDate) {
        this.tranCatBalances = tranCatBalances;
        this.xrefs = xrefs;
        this.discGroups = discGroups;
        this.accounts = accounts;
        this.transactions = transactions;
        this.timestamp = timestamp;
        this.display = display;
        this.parmDate = parmDate;
    }

    /**
     * The main PERFORM UNTIL loop of the PROCEDURE DIVISION. Returns the COBOL RETURN-CODE, which
     * CBACT04C never sets, so a run that does not abend ends with 0.
     */
    public int run() {
        while (!endOfFile) {
            TranCatBalance balance = getNextTranCatBalance();
            if (endOfFile) {
                // The ELSE branch of the COBOL loop (PERFORM 1050-UPDATE-ACCOUNT) is unreachable:
                // END-OF-FILE is only ever set inside the loop body, and the loop condition is
                // tested before the ELSE can be taken. The last account read is therefore never
                // rewritten - a defect of the program, reproduced here on purpose.
                continue;
            }
            recordCount++;
            display.accept(balance.image());
            if (!balance.getAcctIdText().equals(lastAcctNum)) {
                if (!firstTime) {
                    updateAccount();
                } else {
                    firstTime = false;
                }
                totalInterest = BigDecimal.ZERO.setScale(2);
                lastAcctNum = balance.getAcctIdText();
                getAcctData(balance.getAcctId());
                getXrefData(balance.getAcctId());
            }
            getInterestRate(new DiscGroupKey(account.getGroupId(), balance.getTypeCd(), balance.getCatCd()));
            if (discGroup.intRate().signum() != 0) {
                computeInterest(balance);
                computeFees();
            }
        }
        return 0;
    }

    /** 1000-TCATBALF-GET-NEXT. */
    private TranCatBalance getNextTranCatBalance() {
        Optional<TranCatBalance> next = tranCatBalances.next();
        if (next.isEmpty()) {
            endOfFile = true;
            return null;
        }
        return next.get();
    }

    /** 1050-UPDATE-ACCOUNT: reflect the interest posted for the account just finished. */
    private void updateAccount() {
        account.setCurrBal(account.getCurrBal().add(totalInterest));
        account.setCurrCycCredit(BigDecimal.ZERO);
        account.setCurrCycDebit(BigDecimal.ZERO);
        accounts.rewrite(account);
    }

    /** 1100-GET-ACCT-DATA. */
    private void getAcctData(long acctId) {
        Optional<Account> found = accounts.find(acctId);
        if (found.isEmpty()) {
            display.accept("ACCOUNT NOT FOUND: " + Cobol.putDigits(acctId, 11));
            display.accept("ERROR READING ACCOUNT FILE");
            abend("23");
        }
        account = found.get();
    }

    /** 1110-GET-XREF-DATA: read through the XREFFILE account id alternate key. */
    private void getXrefData(long acctId) {
        Optional<CardXref> found = xrefs.findByAcctId(acctId);
        if (found.isEmpty()) {
            display.accept("ACCOUNT NOT FOUND: " + Cobol.putDigits(acctId, 11));
            display.accept("ERROR READING XREF FILE");
            abend("23");
        }
        xref = found.get();
    }

    /** 1200-GET-INTEREST-RATE, falling back to the DEFAULT account group. */
    private void getInterestRate(DiscGroupKey key) {
        Optional<DiscGroup> found = discGroups.find(key);
        if (found.isPresent()) {
            discGroup = found.get();
            return;
        }
        display.accept("DISCLOSURE GROUP RECORD MISSING");
        display.accept("TRY WITH DEFAULT GROUP CODE");
        getDefaultInterestRate(new DiscGroupKey("DEFAULT", key.tranTypeCd(), key.tranCatCd()));
    }

    /** 1200-A-GET-DEFAULT-INT-RATE: no INVALID KEY clause here, a missing record abends. */
    private void getDefaultInterestRate(DiscGroupKey key) {
        Optional<DiscGroup> found = discGroups.find(key);
        if (found.isEmpty()) {
            display.accept("ERROR READING DEFAULT DISCLOSURE GROUP");
            abend("23");
        }
        discGroup = found.get();
    }

    /**
     * 1300-COMPUTE-INTEREST: {@code COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}.
     * The COMPUTE has no ROUNDED phrase, so the monthly interest is truncated to the two decimals
     * of {@code PIC S9(09)V99}.
     */
    private void computeInterest(TranCatBalance balance) {
        monthlyInterest = Cobol.amount(balance.getBalance()
                .multiply(discGroup.intRate())
                .divide(MONTHS_PER_YEAR_PERCENT, 2, RoundingMode.DOWN), 9, 2);
        totalInterest = Cobol.amount(totalInterest.add(monthlyInterest), 9, 2);
        writeTransaction();
    }

    /** 1300-B-WRITE-TX. */
    private void writeTransaction() {
        tranIdSuffix = (tranIdSuffix + 1) % 1_000_000L;
        Transaction transaction = new Transaction();
        transaction.setId(Cobol.putText(parmDate, 10) + Cobol.putDigits(tranIdSuffix, 6));
        transaction.setTypeCd("01");
        transaction.setCatCd("0005");
        transaction.setSource("System");
        transaction.setDesc("Int. for a/c " + Cobol.putDigits(account.getAcctId(), 11));
        transaction.setAmt(monthlyInterest);
        transaction.setMerchantId("000000000");
        transaction.setMerchantName("");
        transaction.setMerchantCity("");
        transaction.setMerchantZip("");
        transaction.setCardNum(xref.cardNum());
        String now = timestamp.now();
        transaction.setOrigTs(now);
        transaction.setProcTs(now);
        transactions.write(transaction);
    }

    /** 1400-COMPUTE-FEES: "To be implemented" in the COBOL, an empty paragraph. */
    private void computeFees() {
        // Intentionally empty, as in CBACT04C.
    }

    /** 9910-DISPLAY-IO-STATUS followed by 9999-ABEND-PROGRAM. */
    private void abend(String fileStatus) {
        display.accept("FILE STATUS IS: NNNN" + ioStatus(fileStatus));
        display.accept("ABENDING PROGRAM");
        throw new AbendException("CBACT04C abended, file status " + fileStatus, 999);
    }

    /** IO-STATUS-04 of 9910-DISPLAY-IO-STATUS. */
    private static String ioStatus(String fileStatus) {
        if (fileStatus.length() == 2 && fileStatus.charAt(0) == '9') {
            return "9" + Cobol.putDigits(Integer.toString(fileStatus.charAt(1)), 3);
        }
        return "00" + fileStatus;
    }

    /** WS-RECORD-COUNT: TCATBALF records read. */
    public long getRecordCount() {
        return recordCount;
    }

    /** WS-TRANID-SUFFIX: transactions written to TRANFILE. */
    public long getTranIdSuffix() {
        return tranIdSuffix;
    }

    /** WS-TOTAL-INT: interest totalled for the account currently being processed. */
    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    /** WS-MONTHLY-INT: interest of the category balance last processed. */
    public BigDecimal getMonthlyInterest() {
        return monthlyInterest;
    }
}
