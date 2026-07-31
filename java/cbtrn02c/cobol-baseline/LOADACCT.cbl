      ******************************************************************
      * Utility  : LOADACCT
      * Function : Load the flat ASCII ACCTDATA fixture (300 byte fixed
      *            length records) into a GnuCOBOL indexed file.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    LOADACCT.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE  ASSIGN TO INFILE
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS IN-STATUS.
           SELECT OUT-FILE ASSIGN TO OUTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS OUT-KEY
                  FILE STATUS  IS OUT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  IN-FILE.
       01  IN-REC                    PIC X(300).
       FD  OUT-FILE.
       01  OUT-REC.
           05 OUT-KEY                PIC 9(11).
           05 OUT-DATA               PIC X(289).

       WORKING-STORAGE SECTION.
       01  IN-STATUS                 PIC X(02).
       01  OUT-STATUS                PIC X(02).
       01  WS-EOF                    PIC X VALUE 'N'.
       01  WS-COUNT                  PIC 9(09) VALUE 0.

       PROCEDURE DIVISION.
           OPEN INPUT IN-FILE
           OPEN OUTPUT OUT-FILE
           PERFORM UNTIL WS-EOF = 'Y'
              READ IN-FILE
                 AT END MOVE 'Y' TO WS-EOF
                 NOT AT END
                    MOVE IN-REC TO OUT-REC
                    WRITE OUT-REC
                    ADD 1 TO WS-COUNT
              END-READ
           END-PERFORM
           CLOSE IN-FILE
           CLOSE OUT-FILE
           DISPLAY 'LOADACCT RECORDS LOADED :' WS-COUNT
           GOBACK.
