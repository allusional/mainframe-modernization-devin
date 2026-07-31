      ******************************************************************
      * Parity harness utility: dump the indexed TCATBALF file to a
      * flat 50-byte record-sequential file in ascending key order.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    DUMPTCAT.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IDX-FILE  ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS IDX-KEY
                  FILE STATUS  IS IDX-STATUS.
           SELECT FLAT-FILE ASSIGN TO TCATFLATO
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS FLAT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  IDX-FILE.
       01  IDX-REC.
           05 IDX-KEY.
              10 IDX-ACCT-ID          PIC 9(11).
              10 IDX-TYPE-CD          PIC X(02).
              10 IDX-CD               PIC 9(04).
           05 IDX-DATA                PIC X(33).
       FD  FLAT-FILE.
       01  FLAT-REC                   PIC X(50).

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
           DISPLAY 'DUMPTCAT RECORDS DUMPED: ' WS-COUNT
           GOBACK.
