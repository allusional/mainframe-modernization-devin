      *****************************************************************
      * Program     : UNLDACCT.CBL
      * Function    : Unload the indexed ACCTFILE back to a flat file
      *               so the account master CBACT04C rewrote can be
      *               diffed against the Java port's output.
      *
      * Test scaffolding for the parity harness, not part of CardDemo.
      * Records are written in key order, which is how the flat sample
      * file app/data/ASCII/acctdata.txt is already ordered.
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    UNLDACCT.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-IN  ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-ACCT-ID
                  FILE STATUS  IS WS-IN-STATUS.
           SELECT ACCT-OUT ASSIGN TO ACCTOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  ACCT-IN.
       01  FD-ACCTFILE-REC.
           05 FD-ACCT-ID                        PIC 9(11).
           05 FD-ACCT-DATA                      PIC X(289).
       FD  ACCT-OUT.
       01  ACCT-OUT-REC                         PIC X(300).

       WORKING-STORAGE SECTION.
       01  WS-IN-STATUS                         PIC X(02).
       01  WS-OUT-STATUS                        PIC X(02).
       01  WS-EOF                               PIC X(01).
       01  WS-COUNT                             PIC 9(09).

       PROCEDURE DIVISION.
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           OPEN INPUT ACCT-IN
           IF WS-IN-STATUS NOT = '00'
               DISPLAY 'UNLDACCT OPEN ERROR, STATUS: ' WS-IN-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
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
           DISPLAY 'UNLOADED ACCTFILE RECORDS: ' WS-COUNT
           GOBACK.
