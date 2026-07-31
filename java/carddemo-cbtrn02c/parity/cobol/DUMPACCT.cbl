      ******************************************************************
      * Parity harness utility: dump the indexed ACCTDATA file to a
      * flat 300-byte record-sequential file in ascending key order.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    DUMPACCT.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IDX-FILE  ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS IDX-KEY
                  FILE STATUS  IS IDX-STATUS.
           SELECT FLAT-FILE ASSIGN TO ACCTFLATO
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS FLAT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  IDX-FILE.
       01  IDX-REC.
           05 IDX-KEY                 PIC 9(11).
           05 IDX-DATA                PIC X(289).
       FD  FLAT-FILE.
       01  FLAT-REC                   PIC X(300).

       WORKING-STORAGE SECTION.
       01  FLAT-STATUS                PIC XX.
       01  IDX-STATUS                 PIC XX.
       01  WS-EOF                     PIC X VALUE 'N'.
       01  WS-COUNT                   PIC 9(9) VALUE 0.

       PROCEDURE DIVISION.
           OPEN INPUT IDX-FILE
           OPEN OUTPUT FLAT-FILE
           PERFORM UNTIL WS-EOF = 'Y'
              READ IDX-FILE NEXT INTO FLAT-REC
                 AT END MOVE 'Y' TO WS-EOF
                 NOT AT END
                    WRITE FLAT-REC
                    ADD 1 TO WS-COUNT
              END-READ
           END-PERFORM
           CLOSE IDX-FILE
           CLOSE FLAT-FILE
           DISPLAY 'DUMPACCT RECORDS DUMPED: ' WS-COUNT
           GOBACK.
