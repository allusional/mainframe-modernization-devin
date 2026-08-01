      ******************************************************************
      * Program     : PTDUMP.CBL
      * Function    : Harness only. Dumps the files CBACT04C produced
      *               (TRANSACT QSAM, ACCTFILE KSDS) to flat text so the
      *               run can be compared byte-for-byte with the Java
      *               port. TCATBALF and DISCGRP are input only.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    PTDUMP.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TRAN-IN   ASSIGN TO TRANSACT
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
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

       DATA DIVISION.
       FILE SECTION.
       FD  TRAN-IN.
       01  TI-REC                              PIC X(350).
       FD  TRAN-OUT.
       01  TO-REC                              PIC X(350).
       FD  ACCT-IN.
       01  AI-REC.
           05 AI-ACCT-ID                       PIC X(11).
           05 AI-DATA                          PIC X(289).
       FD  ACCT-OUT.
       01  AO-REC                              PIC X(300).

       WORKING-STORAGE SECTION.
       01  WS-STATUS                           PIC X(02).
       01  WS-EOF                              PIC X(01)  VALUE 'N'.
       01  WS-COUNT                            PIC 9(07)  VALUE 0.

       PROCEDURE DIVISION.
           PERFORM 1000-DUMP-TRANFILE.
           PERFORM 2000-DUMP-ACCTFILE.
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
