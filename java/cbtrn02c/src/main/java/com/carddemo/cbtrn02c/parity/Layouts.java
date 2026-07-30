package com.carddemo.cbtrn02c.parity;

import java.util.List;

/** Copybook field maps used to report parity differences field by field. */
public final class Layouts {

    /** One field of a fixed width record. */
    public record Field(String name, int offset, int length) {
    }

    /** A comparable output file: its logical name, record length and field map. */
    public record RecordLayout(String name, String fileName, int recordLength, List<Field> fields) {
    }

    public static final RecordLayout TRANSACT = new RecordLayout("TRANSACT (posted transactions, CVTRA05Y)",
            "transact.dat", 350, List.of(
            new Field("TRAN-ID", 0, 16),
            new Field("TRAN-TYPE-CD", 16, 2),
            new Field("TRAN-CAT-CD", 18, 4),
            new Field("TRAN-SOURCE", 22, 10),
            new Field("TRAN-DESC", 32, 100),
            new Field("TRAN-AMT", 132, 11),
            new Field("TRAN-MERCHANT-ID", 143, 9),
            new Field("TRAN-MERCHANT-NAME", 152, 50),
            new Field("TRAN-MERCHANT-CITY", 202, 50),
            new Field("TRAN-MERCHANT-ZIP", 252, 10),
            new Field("TRAN-CARD-NUM", 262, 16),
            new Field("TRAN-ORIG-TS", 278, 26),
            new Field("TRAN-PROC-TS", 304, 26),
            new Field("FILLER", 330, 20)));

    public static final RecordLayout ACCOUNT = new RecordLayout("ACCTDATA (updated accounts, CVACT01Y)",
            "acctdata.dat", 300, List.of(
            new Field("ACCT-ID", 0, 11),
            new Field("ACCT-ACTIVE-STATUS", 11, 1),
            new Field("ACCT-CURR-BAL", 12, 12),
            new Field("ACCT-CREDIT-LIMIT", 24, 12),
            new Field("ACCT-CASH-CREDIT-LIMIT", 36, 12),
            new Field("ACCT-OPEN-DATE", 48, 10),
            new Field("ACCT-EXPIRAION-DATE", 58, 10),
            new Field("ACCT-REISSUE-DATE", 68, 10),
            new Field("ACCT-CURR-CYC-CREDIT", 78, 12),
            new Field("ACCT-CURR-CYC-DEBIT", 90, 12),
            new Field("ACCT-ADDR-ZIP", 102, 10),
            new Field("ACCT-GROUP-ID", 112, 10),
            new Field("FILLER", 122, 178)));

    public static final RecordLayout TCATBAL = new RecordLayout("TCATBALF (category balances, CVTRA01Y)",
            "tcatbal.dat", 50, List.of(
            new Field("TRANCAT-ACCT-ID", 0, 11),
            new Field("TRANCAT-TYPE-CD", 11, 2),
            new Field("TRANCAT-CD", 13, 4),
            new Field("TRAN-CAT-BAL", 17, 11),
            new Field("FILLER", 28, 22)));

    public static final RecordLayout DALYREJS = new RecordLayout("DALYREJS (rejected transactions)",
            "dalyrejs.dat", 430, List.of(
            new Field("REJECT-TRAN-DATA", 0, 350),
            new Field("WS-VALIDATION-FAIL-REASON", 350, 4),
            new Field("WS-VALIDATION-FAIL-REASON-DESC", 354, 76)));

    public static final List<RecordLayout> ALL = List.of(TRANSACT, ACCOUNT, TCATBAL, DALYREJS);

    private Layouts() {
    }
}
