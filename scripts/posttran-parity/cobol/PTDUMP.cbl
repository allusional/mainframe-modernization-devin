      ******************************************************************
      * Program     : PTDUMP.CBL
      * Function    : Harness only. Dumps the files CBTRN02C produced
      *               (TRANFILE KSDS, ACCTFILE KSDS, TCATBALF KSDS,
      *               DALYREJS QSAM) to flat text so the run can be
      *               compared byte-for-byte with the Java port.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    PTDUMP.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TRAN-IN   ASSIGN TO TRANFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS TI-TRANS-ID
                  FILE STATUS  IS WS-STATUS.

           SELECT TRAN-OUT  ASSIGN TO TRANOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT ACCT-IN   ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS AI-ACCT-ID
                  FILE STATUS  IS WS-STATUS.

           SELECT ACCT-OUT  ASSIGN TO ACCTOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT TCAT-IN   ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS CI-TRAN-CAT-KEY
                  FILE STATUS  IS WS-STATUS.

           SELECT TCAT-OUT  ASSIGN TO TCATOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT REJS-IN   ASSIGN TO DALYREJS
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT REJS-OUT  ASSIGN TO REJSOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  TRAN-IN.
       01  TI-REC.
           05 TI-TRANS-ID                      PIC X(16).
           05 TI-DATA                          PIC X(334).
       FD  TRAN-OUT.
       01  TO-REC                              PIC X(350).
       FD  ACCT-IN.
       01  AI-REC.
           05 AI-ACCT-ID                       PIC X(11).
           05 AI-DATA                          PIC X(289).
       FD  ACCT-OUT.
       01  AO-REC                              PIC X(300).
       FD  TCAT-IN.
       01  CI-REC.
           05 CI-TRAN-CAT-KEY                  PIC X(17).
           05 CI-DATA                          PIC X(33).
       FD  TCAT-OUT.
       01  CO-REC                              PIC X(50).
       FD  REJS-IN.
       01  RI-REC                              PIC X(430).
       FD  REJS-OUT.
       01  RO-REC                              PIC X(430).

       WORKING-STORAGE SECTION.
       01  WS-STATUS                           PIC X(02).
       01  WS-EOF                              PIC X(01)  VALUE 'N'.
       01  WS-COUNT                            PIC 9(07)  VALUE 0.

       PROCEDURE DIVISION.
           PERFORM 1000-DUMP-TRANFILE.
           PERFORM 2000-DUMP-ACCTFILE.
           PERFORM 3000-DUMP-TCATBALF.
           PERFORM 4000-DUMP-DALYREJS.
           GOBACK.

       1000-DUMP-TRANFILE.
           OPEN INPUT TRAN-IN
           OPEN OUTPUT TRAN-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ TRAN-IN INTO TO-REC
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE TO-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE TRAN-IN
           CLOSE TRAN-OUT
           DISPLAY 'PTDUMP TRANFILE RECORDS  :' WS-COUNT.

       2000-DUMP-ACCTFILE.
           OPEN INPUT ACCT-IN
           OPEN OUTPUT ACCT-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ ACCT-IN INTO AO-REC
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE AO-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE ACCT-IN
           CLOSE ACCT-OUT
           DISPLAY 'PTDUMP ACCTFILE RECORDS  :' WS-COUNT.

       3000-DUMP-TCATBALF.
           OPEN INPUT TCAT-IN
           OPEN OUTPUT TCAT-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ TCAT-IN INTO CO-REC
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE CO-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE TCAT-IN
           CLOSE TCAT-OUT
           DISPLAY 'PTDUMP TCATBALF RECORDS  :' WS-COUNT.

       4000-DUMP-DALYREJS.
           OPEN INPUT REJS-IN
           OPEN OUTPUT REJS-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ REJS-IN INTO RO-REC
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE RO-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE REJS-IN
           CLOSE REJS-OUT
           DISPLAY 'PTDUMP DALYREJS RECORDS  :' WS-COUNT.
