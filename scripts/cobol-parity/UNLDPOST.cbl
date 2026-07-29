      *****************************************************************
      * Program     : UNLDPOST.CBL
      * Function    : Unload the three indexed files CBTRN02C writes
      *               back to flat files so they can be diffed against
      *               the Java port's output byte for byte:
      *                 ACCTFILE  300 bytes  (rewritten in place)
      *                 TCATBALF   50 bytes  (rewritten and inserted)
      *                 TRANFILE  350 bytes  (created, posted records)
      *
      * Test scaffolding for the parity harness, not part of CardDemo.
      * Records come out in key order, which is how a sequential read
      * of a VSAM KSDS returns them on z/OS as well.
      *
      * DALYREJS needs no unload step: CBTRN02C writes it as a plain
      * 430 byte RECFM=F sequential file already.
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    UNLDPOST.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-IN     ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-ACCT-ID
                  FILE STATUS  IS WS-IN-STATUS.
           SELECT TCATBAL-IN  ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-TRAN-CAT-KEY
                  FILE STATUS  IS WS-IN-STATUS.
           SELECT TRAN-IN     ASSIGN TO TRANFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-TRANS-ID
                  FILE STATUS  IS WS-IN-STATUS.

           SELECT ACCT-OUT    ASSIGN TO ACCTOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STATUS.
           SELECT TCATBAL-OUT ASSIGN TO TCATBALOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STATUS.
           SELECT TRAN-OUT    ASSIGN TO TRANOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  ACCT-IN.
       01  FD-ACCTFILE-REC.
           05 FD-ACCT-ID                        PIC 9(11).
           05 FD-ACCT-DATA                      PIC X(289).
       FD  TCATBAL-IN.
       01  FD-TRAN-CAT-BAL-RECORD.
           05 FD-TRAN-CAT-KEY.
              10 FD-TRANCAT-ACCT-ID             PIC 9(11).
              10 FD-TRANCAT-TYPE-CD             PIC X(02).
              10 FD-TRANCAT-CD                  PIC 9(04).
           05 FD-FD-TRAN-CAT-DATA               PIC X(33).
       FD  TRAN-IN.
       01  FD-TRANFILE-REC.
           05 FD-TRANS-ID                       PIC X(16).
           05 FD-ACCT-DATA-2                    PIC X(334).

       FD  ACCT-OUT.
       01  ACCT-OUT-REC                         PIC X(300).
       FD  TCATBAL-OUT.
       01  TCATBAL-OUT-REC                      PIC X(50).
       FD  TRAN-OUT.
       01  TRAN-OUT-REC                         PIC X(350).

       WORKING-STORAGE SECTION.
       01  WS-IN-STATUS                         PIC X(02).
       01  WS-OUT-STATUS                        PIC X(02).
       01  WS-EOF                               PIC X(01).
       01  WS-COUNT                             PIC 9(09).

       PROCEDURE DIVISION.
           PERFORM 1000-UNLOAD-ACCT
           PERFORM 2000-UNLOAD-TCATBAL
           PERFORM 3000-UNLOAD-TRAN
           GOBACK.

       1000-UNLOAD-ACCT.
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           OPEN INPUT ACCT-IN
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT ACCT-OUT
           PERFORM UNTIL WS-EOF = 'Y'
               READ ACCT-IN NEXT
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE FD-ACCTFILE-REC TO ACCT-OUT-REC
                       WRITE ACCT-OUT-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE ACCT-IN ACCT-OUT
           DISPLAY 'UNLOADED ACCTFILE RECORDS: ' WS-COUNT.

       2000-UNLOAD-TCATBAL.
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           OPEN INPUT TCATBAL-IN
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT TCATBAL-OUT
           PERFORM UNTIL WS-EOF = 'Y'
               READ TCATBAL-IN NEXT
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE FD-TRAN-CAT-BAL-RECORD TO TCATBAL-OUT-REC
                       WRITE TCATBAL-OUT-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE TCATBAL-IN TCATBAL-OUT
           DISPLAY 'UNLOADED TCATBALF RECORDS: ' WS-COUNT.

       3000-UNLOAD-TRAN.
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           OPEN INPUT TRAN-IN
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT TRAN-OUT
           PERFORM UNTIL WS-EOF = 'Y'
               READ TRAN-IN NEXT
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE FD-TRANFILE-REC TO TRAN-OUT-REC
                       WRITE TRAN-OUT-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE TRAN-IN TRAN-OUT
           DISPLAY 'UNLOADED TRANFILE RECORDS: ' WS-COUNT.

       9000-CHECK-IN.
           IF WS-IN-STATUS NOT = '00'
               DISPLAY 'UNLDPOST OPEN ERROR, STATUS: ' WS-IN-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.
