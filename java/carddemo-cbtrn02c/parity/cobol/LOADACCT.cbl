      ******************************************************************
      * Parity harness utility: load flat ACCTDATA records (300 bytes)
      * into an indexed (KSDS-equivalent) file for CBTRN02C to update.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    LOADACCT.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT FLAT-FILE ASSIGN TO ACCTFLAT
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS FLAT-STATUS.
           SELECT IDX-FILE  ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS IDX-KEY
                  FILE STATUS  IS IDX-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  FLAT-FILE.
       01  FLAT-REC                   PIC X(300).
       FD  IDX-FILE.
       01  IDX-REC.
           05 IDX-KEY                 PIC 9(11).
           05 IDX-DATA                PIC X(289).

       WORKING-STORAGE SECTION.
       01  FLAT-STATUS                PIC XX.
       01  IDX-STATUS                 PIC XX.
       01  WS-EOF                     PIC X VALUE 'N'.
       01  WS-COUNT                   PIC 9(9) VALUE 0.

       PROCEDURE DIVISION.
           OPEN INPUT FLAT-FILE
           OPEN OUTPUT IDX-FILE
           PERFORM UNTIL WS-EOF = 'Y'
              READ FLAT-FILE INTO IDX-REC
                 AT END MOVE 'Y' TO WS-EOF
                 NOT AT END
                    WRITE IDX-REC
                    ADD 1 TO WS-COUNT
              END-READ
           END-PERFORM
           CLOSE FLAT-FILE
           CLOSE IDX-FILE
           DISPLAY 'LOADACCT RECORDS LOADED: ' WS-COUNT
           GOBACK.
