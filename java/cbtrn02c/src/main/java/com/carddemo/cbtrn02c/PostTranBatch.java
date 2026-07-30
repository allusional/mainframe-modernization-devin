package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.CardXrefRecord;
import com.carddemo.cbtrn02c.copybook.CobolField;
import com.carddemo.cbtrn02c.copybook.DalyTranRecord;
import com.carddemo.cbtrn02c.copybook.RejectRecord;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import com.carddemo.cbtrn02c.copybook.TranRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Java port of the COBOL batch program CBTRN02C (job POSTTRAN): posts the records of the daily
 * transaction file to the transaction master, the account master and the transaction category
 * balance file, rejecting the transactions that fail validation.
 *
 * <p>The COBOL program reads the XREF / ACCOUNT / TCATBAL VSAM KSDS files randomly by key and
 * writes the TRANSACT KSDS; here those keyed files are in-memory maps ordered by their COBOL key,
 * so unloading them in key order reproduces the COBOL files byte for byte. Every monetary field
 * is a {@link BigDecimal} truncated to the capacity of the corresponding {@code PIC S9(n)V99}
 * field, so no rounding difference can appear.
 *
 * <p>Paragraph names of the original program are kept in the method javadoc to keep the port
 * traceable.
 */
public final class PostTranBatch {

    /** Validation failure reasons and messages, verbatim from 1500-VALIDATE-TRAN. */
    public static final int REASON_INVALID_CARD = 100;
    public static final int REASON_ACCOUNT_NOT_FOUND = 101;
    public static final int REASON_OVERLIMIT = 102;
    public static final int REASON_EXPIRED = 103;
    public static final String DESC_INVALID_CARD = "INVALID CARD NUMBER FOUND";
    public static final String DESC_ACCOUNT_NOT_FOUND = "ACCOUNT RECORD NOT FOUND";
    public static final String DESC_OVERLIMIT = "OVERLIMIT TRANSACTION";
    public static final String DESC_EXPIRED = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";

    /** WS-TEMP-BAL is PIC S9(09)V99. */
    private static final int TEMP_BAL_LENGTH = 11;
    private static final int TEMP_BAL_SCALE = 2;

    /** XREFFILE: keyed by XREF-CARD-NUM, read only. */
    private final Map<String, CardXrefRecord> xrefFile;
    /** ACCTFILE: keyed by ACCT-ID, updated in place (OPEN I-O). */
    private final NavigableMap<String, AccountRecord> accountFile;
    /** TCATBALF: keyed by TRAN-CAT-KEY, updated and extended (OPEN I-O). */
    private final NavigableMap<String, TranCatBalRecord> tranCatBalFile;
    /** TRANFILE: keyed by TRAN-ID, written (OPEN OUTPUT). */
    private final NavigableMap<String, TranRecord> transactFile = new TreeMap<>();
    /** DALYREJS: sequential output. */
    private final List<RejectRecord> rejectFile = new ArrayList<>();

    private final Supplier<String> processingTimestamp;
    private final Consumer<String> display;

    /** WS-COUNTERS. */
    private long transactionCount;
    private long rejectCount;
    /** WS-VALIDATION-TRAILER. */
    private int validationFailReason;
    private String validationFailReasonDescription = "";
    /** The record areas the COBOL program keeps in WORKING-STORAGE between paragraphs. */
    private CardXrefRecord cardXrefRecord;
    private AccountRecord accountRecord;
    /**
     * The FILLER bytes of the TRAN-CAT-BAL-RECORD working storage area: a created record inherits
     * them from the last record read into that area, because INITIALIZE does not touch FILLER.
     * Before the first successful read the area holds its working storage initial value (spaces).
     */
    private String tranCatBalRecordAreaFiller = " ".repeat(22);

    public PostTranBatch(Map<String, CardXrefRecord> xrefFile,
                         NavigableMap<String, AccountRecord> accountFile,
                         NavigableMap<String, TranCatBalRecord> tranCatBalFile,
                         Supplier<String> processingTimestamp,
                         Consumer<String> display) {
        this.xrefFile = xrefFile;
        this.accountFile = accountFile;
        this.tranCatBalFile = tranCatBalFile;
        this.processingTimestamp = processingTimestamp;
        this.display = display;
    }

    /** Outcome of a run: the two counters displayed at end of job and the COBOL RETURN-CODE. */
    public record Result(long transactionsProcessed, long transactionsRejected, int returnCode) {
    }

    /** The main PROCEDURE DIVISION loop. */
    public Result run(Iterable<DalyTranRecord> dalyTranFile) {
        display.accept("START OF EXECUTION OF PROGRAM CBTRN02C");
        for (DalyTranRecord dalyTran : dalyTranFile) {
            transactionCount++;
            validationFailReason = 0;
            validationFailReasonDescription = "";
            validateTran(dalyTran);
            if (validationFailReason == 0) {
                postTransaction(dalyTran);
            } else {
                rejectCount++;
                writeRejectRecord(dalyTran);
            }
        }
        display.accept("TRANSACTIONS PROCESSED :" + counter(transactionCount));
        display.accept("TRANSACTIONS REJECTED  :" + counter(rejectCount));
        int returnCode = rejectCount > 0 ? 4 : 0;
        display.accept("END OF EXECUTION OF PROGRAM CBTRN02C");
        return new Result(transactionCount, rejectCount, returnCode);
    }

    /** 1500-VALIDATE-TRAN. */
    private void validateTran(DalyTranRecord dalyTran) {
        lookupXref(dalyTran);
        if (validationFailReason == 0) {
            lookupAccount(dalyTran);
        }
    }

    /** 1500-A-LOOKUP-XREF. */
    private void lookupXref(DalyTranRecord dalyTran) {
        cardXrefRecord = xrefFile.get(dalyTran.cardNum());
        if (cardXrefRecord == null) {
            validationFailReason = REASON_INVALID_CARD;
            validationFailReasonDescription = DESC_INVALID_CARD;
        }
    }

    /**
     * 1500-B-LOOKUP-ACCT: the credit limit and the expiration date are both checked, so a
     * transaction that is over limit <em>and</em> received after expiration is reported with the
     * later reason (103), exactly as the COBOL does.
     */
    private void lookupAccount(DalyTranRecord dalyTran) {
        accountRecord = accountFile.get(cardXrefRecord.accountId());
        if (accountRecord == null) {
            validationFailReason = REASON_ACCOUNT_NOT_FOUND;
            validationFailReasonDescription = DESC_ACCOUNT_NOT_FOUND;
            return;
        }
        BigDecimal tempBalance = CobolField.truncate(
                accountRecord.currentCycleCredit()
                        .subtract(accountRecord.currentCycleDebit())
                        .add(dalyTran.amount()),
                TEMP_BAL_LENGTH, TEMP_BAL_SCALE);
        if (accountRecord.creditLimit().compareTo(tempBalance) < 0) {
            validationFailReason = REASON_OVERLIMIT;
            validationFailReasonDescription = DESC_OVERLIMIT;
        }
        if (accountRecord.expirationDate().compareTo(dalyTran.origTs().substring(0, 10)) < 0) {
            validationFailReason = REASON_EXPIRED;
            validationFailReasonDescription = DESC_EXPIRED;
        }
    }

    /** 2000-POST-TRANSACTION. */
    private void postTransaction(DalyTranRecord dalyTran) {
        TranRecord tranRecord = TranRecord.fromDalyTran(dalyTran, processingTimestamp.get());
        updateTranCatBal(dalyTran);
        updateAccountRecord(dalyTran);
        writeTransactionFile(tranRecord);
    }

    /** 2700-UPDATE-TCATBAL, 2700-A-CREATE-TCATBAL-REC and 2700-B-UPDATE-TCATBAL-REC. */
    private void updateTranCatBal(DalyTranRecord dalyTran) {
        String accountId = cardXrefRecord.accountId();
        String key = accountId + dalyTran.typeCd() + dalyTran.catCd();
        TranCatBalRecord tranCatBal = tranCatBalFile.get(key);
        if (tranCatBal == null) {
            display.accept("TCATBAL record not found for key : " + key + ".. Creating.");
            tranCatBal = TranCatBalRecord.create(accountId, dalyTran.typeCd(), dalyTran.catCd(),
                    tranCatBalRecordAreaFiller);
            tranCatBal.addToBalance(dalyTran.amount());
            tranCatBalFile.put(key, tranCatBal);
        } else {
            tranCatBalRecordAreaFiller = tranCatBal.filler();
            tranCatBal.addToBalance(dalyTran.amount());
        }
    }

    /**
     * 2800-UPDATE-ACCOUNT-REC. The COBOL REWRITE cannot fail here (the record was just read on
     * its key), so the 109 / 'ACCOUNT RECORD NOT FOUND' INVALID KEY branch is unreachable.
     */
    private void updateAccountRecord(DalyTranRecord dalyTran) {
        accountRecord.addToCurrentBalance(dalyTran.amount());
        if (dalyTran.amount().signum() >= 0) {
            accountRecord.addToCurrentCycleCredit(dalyTran.amount());
        } else {
            accountRecord.addToCurrentCycleDebit(dalyTran.amount());
        }
    }

    /**
     * 2900-WRITE-TRANSACTION-FILE. A duplicate TRAN-ID would make the COBOL WRITE fail with file
     * status 22 and abend the program, so it is an error here as well.
     */
    private void writeTransactionFile(TranRecord tranRecord) {
        TranRecord existing = transactFile.put(tranRecord.id(), tranRecord);
        if (existing != null) {
            throw new IllegalStateException("duplicate key on TRANFILE write: TRAN-ID " + tranRecord.id());
        }
    }

    /** 2500-WRITE-REJECT-REC: the daily transaction record verbatim plus the validation trailer. */
    private void writeRejectRecord(DalyTranRecord dalyTran) {
        rejectFile.add(new RejectRecord(dalyTran.raw(), validationFailReason,
                CobolField.moveAlpha(validationFailReasonDescription, 76)));
    }

    /** TRANFILE content in TRAN-ID (key) order, i.e. as the KSDS would be unloaded. */
    public List<TranRecord> transactFile() {
        return List.copyOf(transactFile.values());
    }

    /** ACCTFILE content in ACCT-ID order, including the accounts no transaction touched. */
    public List<AccountRecord> accountFile() {
        return List.copyOf(accountFile.values());
    }

    /** TCATBALF content in TRAN-CAT-KEY order, including the records created during the run. */
    public List<TranCatBalRecord> tranCatBalFile() {
        return List.copyOf(tranCatBalFile.values());
    }

    /** DALYREJS content in the order the records were written. */
    public List<RejectRecord> rejectFile() {
        return List.copyOf(rejectFile);
    }

    /** Renders a PIC 9(09) counter the way COBOL DISPLAY does. */
    private static String counter(long value) {
        return String.format("%09d", value);
    }
}
