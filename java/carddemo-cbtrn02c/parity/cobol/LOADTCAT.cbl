      ******************************************************************
      * Parity harness utility: load flat TCATBAL records (50 bytes)
      * into an indexed (KSDS-equivalent) file for CBTRN02C to update.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    LOADTCAT.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT FLAT-FILE ASSIGN TO TCATFLAT
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS FLAT-STATUS.
           SELECT IDX-FILE  ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS IDX-KEY
                  FILE STATUS  IS IDX-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  FLAT-FILE.
       01  FLAT-REC                   PIC X(50).
       FD  IDX-FILE.
       01  IDX-REC.
           05 IDX-KEY.
              10 IDX-ACCT-ID          PIC 9(11).
              10 IDX-TYPE-CD          PIC X(02).
              10 IDX-CD               PIC 9(04).
           05 IDX-DATA                PIC X(33).

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
           DISPLAY 'LOADTCAT RECORDS LOADED: ' WS-COUNT
           GOBACK.
