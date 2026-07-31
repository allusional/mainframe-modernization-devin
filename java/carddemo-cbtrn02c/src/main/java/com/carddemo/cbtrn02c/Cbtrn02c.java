package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.CardXrefRecord;
import com.carddemo.cbtrn02c.copybook.DalytranRecord;
import com.carddemo.cbtrn02c.copybook.Pic;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import com.carddemo.cbtrn02c.copybook.TranRecord;
import com.carddemo.cbtrn02c.io.IndexedFile;
import com.carddemo.cbtrn02c.io.RecordFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Java port of the CardDemo batch program {@code CBTRN02C} (job POSTTRAN):
 * posts the daily transaction file against the account master, the transaction
 * category balance file and the transaction master, rejecting transactions
 * that fail validation.
 *
 * <p>The control flow, validation order, reject reason codes and record
 * layouts follow {@code app/cbl/CBTRN02C.cbl} paragraph by paragraph.
 */
public final class Cbtrn02c {

    /** Reason 100: the card number has no cross reference record. */
    public static final int REASON_INVALID_CARD = 100;
    /** Reason 101: the cross referenced account does not exist. */
    public static final int REASON_ACCOUNT_NOT_FOUND = 101;
    /** Reason 102: the projected cycle balance exceeds the credit limit. */
    public static final int REASON_OVERLIMIT = 102;
    /** Reason 103: the transaction is dated after the account expiration date. */
    public static final int REASON_EXPIRED = 103;

    /** DD names of the POSTTRAN step, mapped to flat files. */
    public record Datasets(Path dalytran, Path tranfile, Path xreffile, Path dalyrejs,
                           Path acctfile, Path tcatbalf) {
    }

    /** What the job produced: the DISPLAY output, the counters and the return code. */
    public record Result(long transactionCount, long rejectCount, int returnCode, List<String> display) {
    }

    /** Raised where the COBOL program performs 9999-ABEND-PROGRAM. */
    public static final class AbendException extends RuntimeException {
        public AbendException(String message) {
            super(message);
        }
    }

    private final Clock clock;
    private final Consumer<String> display;
    private final List<String> displayLines = new ArrayList<>();

    // WORKING-STORAGE
    private DalytranRecord dalytran;
    private CardXrefRecord cardXref;
    private AccountRecord account;
    /** WORKING-STORAGE copy of CVTRA01Y; its FILLER survives across records. */
    private final TranCatBalRecord tranCatBal = new TranCatBalRecord();
    private final TranRecord tranRecord = new TranRecord();
    private int validationFailReason;
    private String validationFailReasonDesc = "";
    private long transactionCount;
    private long rejectCount;

    private IndexedFile transactFile;
    private IndexedFile xrefFile;
    private IndexedFile accountFile;
    private IndexedFile tcatbalFile;
    private final List<String> rejectRecords = new ArrayList<>();

    public Cbtrn02c() {
        this(Clock.systemDefaultZone(), System.out::println);
    }

    public Cbtrn02c(Clock clock, Consumer<String> display) {
        this.clock = clock;
        this.display = display;
    }

    public Result run(Datasets datasets) throws IOException {
        show("START OF EXECUTION OF PROGRAM CBTRN02C");

        List<String> dalytranRecords = RecordFile.read(datasets.dalytran(), DalytranRecord.LENGTH);
        transactFile = IndexedFile.empty(TranRecord.LENGTH, 16);                       // OPEN OUTPUT
        xrefFile = IndexedFile.load(datasets.xreffile(), CardXrefRecord.LENGTH, 16);   // OPEN INPUT
        accountFile = IndexedFile.load(datasets.acctfile(), AccountRecord.LENGTH, 11); // OPEN I-O
        tcatbalFile = IndexedFile.load(datasets.tcatbalf(), TranCatBalRecord.LENGTH,
                TranCatBalRecord.KEY_LENGTH);                                          // OPEN I-O
        rejectRecords.clear();

        for (String record : dalytranRecords) {
            dalytran = DalytranRecord.parse(record);
            transactionCount++;
            validationFailReason = 0;
            validationFailReasonDesc = "";
            validateTran();
            if (validationFailReason == 0) {
                postTransaction();
            } else {
                rejectCount++;
                writeRejectRec();
            }
        }

        transactFile.save(datasets.tranfile());
        accountFile.save(datasets.acctfile());
        tcatbalFile.save(datasets.tcatbalf());
        RecordFile.write(datasets.dalyrejs(), rejectRecords);

        show("TRANSACTIONS PROCESSED :" + Pic.encodeUnsigned(transactionCount, 9));
        show("TRANSACTIONS REJECTED  :" + Pic.encodeUnsigned(rejectCount, 9));
        int returnCode = rejectCount > 0 ? 4 : 0;
        show("END OF EXECUTION OF PROGRAM CBTRN02C");
        return new Result(transactionCount, rejectCount, returnCode, List.copyOf(displayLines));
    }

    /** 1500-VALIDATE-TRAN. */
    private void validateTran() {
        lookupXref();
        if (validationFailReason == 0) {
            lookupAcct();
        }
    }

    /** 1500-A-LOOKUP-XREF. */
    private void lookupXref() {
        String record = xrefFile.read(dalytran.cardNum);
        if (record == null) {
            validationFailReason = REASON_INVALID_CARD;
            validationFailReasonDesc = "INVALID CARD NUMBER FOUND";
        } else {
            cardXref = CardXrefRecord.parse(record);
        }
    }

    /** 1500-B-LOOKUP-ACCT. */
    private void lookupAcct() {
        String record = accountFile.read(Pic.encodeUnsigned(cardXref.acctId, 11));
        if (record == null) {
            validationFailReason = REASON_ACCOUNT_NOT_FOUND;
            validationFailReasonDesc = "ACCOUNT RECORD NOT FOUND";
            return;
        }
        account = AccountRecord.parse(record);

        BigDecimal tempBal = account.currCycCredit.subtract(account.currCycDebit).add(dalytran.amt);
        if (account.creditLimit.compareTo(tempBal) < 0) {
            validationFailReason = REASON_OVERLIMIT;
            validationFailReasonDesc = "OVERLIMIT TRANSACTION";
        }
        // Runs even when the credit limit check already failed, exactly as in
        // the COBOL: an expired account overwrites reason 102 with 103.
        if (account.expirationDate.compareTo(dalytran.origTs.substring(0, 10)) < 0) {
            validationFailReason = REASON_EXPIRED;
            validationFailReasonDesc = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
        }
    }

    /** 2000-POST-TRANSACTION. */
    private void postTransaction() {
        tranRecord.id = dalytran.id;
        tranRecord.typeCd = dalytran.typeCd;
        tranRecord.catCd = dalytran.catCd;
        tranRecord.source = dalytran.source;
        tranRecord.desc = dalytran.desc;
        tranRecord.amt = dalytran.amt;
        tranRecord.merchantId = dalytran.merchantId;
        tranRecord.merchantName = dalytran.merchantName;
        tranRecord.merchantCity = dalytran.merchantCity;
        tranRecord.merchantZip = dalytran.merchantZip;
        tranRecord.cardNum = dalytran.cardNum;
        tranRecord.origTs = dalytran.origTs;
        tranRecord.procTs = db2FormatTimestamp();

        updateTcatbal();
        updateAccountRec();
        writeTransactionFile();
    }

    /** 2500-WRITE-REJECT-REC. */
    private void writeRejectRec() {
        String trailer = Pic.encodeUnsigned(validationFailReason, 4) + Pic.text(validationFailReasonDesc, 76);
        rejectRecords.add(dalytran.raw() + trailer);
    }

    /** 2700-UPDATE-TCATBAL, 2700-A-CREATE-TCATBAL-REC, 2700-B-UPDATE-TCATBAL-REC. */
    private void updateTcatbal() {
        String key = TranCatBalRecord.key(cardXref.acctId, dalytran.typeCd, dalytran.catCd);
        String record = tcatbalFile.read(key);
        if (record == null) {
            show("TCATBAL record not found for key : " + key + ".. Creating.");
            tranCatBal.initialize();
            tranCatBal.acctId = cardXref.acctId;
            tranCatBal.typeCd = dalytran.typeCd;
            tranCatBal.catCd = dalytran.catCd;
            tranCatBal.balance = tranCatBal.balance.add(dalytran.amt);
            tcatbalFile.write(tranCatBal.toRecord());
        } else {
            tranCatBal.assign(record);
            tranCatBal.balance = tranCatBal.balance.add(dalytran.amt);
            tcatbalFile.rewrite(tranCatBal.toRecord());
        }
    }

    /** 2800-UPDATE-ACCOUNT-REC. */
    private void updateAccountRec() {
        account.currBal = account.currBal.add(dalytran.amt);
        if (dalytran.amt.signum() >= 0) {
            account.currCycCredit = account.currCycCredit.add(dalytran.amt);
        } else {
            account.currCycDebit = account.currCycDebit.add(dalytran.amt);
        }
        accountFile.rewrite(account.toRecord());
    }

    /** 2900-WRITE-TRANSACTION-FILE. */
    private void writeTransactionFile() {
        try {
            transactFile.write(tranRecord.toRecord());
        } catch (IllegalStateException e) {
            show("ERROR WRITING TO TRANSACTION FILE");
            show("FILE STATUS IS: NNNN0022");
            abend();
        }
    }

    /** Z-GET-DB2-FORMAT-TIMESTAMP: EEEE-MM-DD-UU.MM.SS.HH0000. */
    private String db2FormatTimestamp() {
        LocalDateTime now = LocalDateTime.now(clock);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond(),
                now.getNano() / 10_000_000);
    }

    /** 9999-ABEND-PROGRAM. */
    private void abend() {
        show("ABENDING PROGRAM");
        throw new AbendException("CEE3ABD abend code 999");
    }

    private void show(String line) {
        displayLines.add(line);
        display.accept(line);
    }

    /**
     * Runs the job using the DD names of {@code app/jcl/POSTTRAN.jcl}, taken
     * from the environment as {@code DD_<ddname>} (the GnuCOBOL convention),
     * so the COBOL program and this port can be driven by the same script.
     */
    public static void main(String[] args) throws IOException {
        Datasets datasets = new Datasets(
                dd("DALYTRAN"), dd("TRANFILE"), dd("XREFFILE"),
                dd("DALYREJS"), dd("ACCTFILE"), dd("TCATBALF"));
        Clock clock = Clock.systemDefaultZone();
        String fixedTs = System.getenv("CBTRN02C_FIXED_TIMESTAMP");
        if (fixedTs != null && !fixedTs.isBlank()) {
            clock = Clock.fixed(java.time.Instant.parse(fixedTs), java.time.ZoneOffset.UTC);
        }
        Result result = new Cbtrn02c(clock, System.out::println).run(datasets);
        System.exit(result.returnCode());
    }

    private static Path dd(String ddName) {
        String value = System.getenv("DD_" + ddName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing environment variable DD_" + ddName);
        }
        return Path.of(value);
    }
}
