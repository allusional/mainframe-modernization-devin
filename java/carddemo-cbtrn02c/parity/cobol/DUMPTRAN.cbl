      ******************************************************************
      * Parity harness utility: dump the indexed TRANSACT file to a
      * flat 350-byte record-sequential file in ascending key order.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    DUMPTRAN.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IDX-FILE  ASSIGN TO TRANFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS IDX-KEY
                  FILE STATUS  IS IDX-STATUS.
           SELECT FLAT-FILE ASSIGN TO TRANFLAT
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS FLAT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  IDX-FILE.
       01  IDX-REC.
           05 IDX-KEY                 PIC X(16).
           05 IDX-DATA                PIC X(334).
       FD  FLAT-FILE.
       01  FLAT-REC                   PIC X(350).

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
           DISPLAY 'DUMPTRAN RECORDS DUMPED: ' WS-COUNT
           GOBACK.
