      ******************************************************************
      * Utility  : LOADTCAT
      * Function : Load the flat ASCII TCATBAL fixture (50 byte fixed
      *            length records) into a GnuCOBOL indexed file.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    LOADTCAT.

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
       01  IN-REC                    PIC X(50).
       FD  OUT-FILE.
       01  OUT-REC.
           05 OUT-KEY.
              10 OUT-ACCT-ID         PIC 9(11).
              10 OUT-TYPE-CD         PIC X(02).
              10 OUT-CAT-CD          PIC 9(04).
           05 OUT-DATA               PIC X(33).

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
           DISPLAY 'LOADTCAT RECORDS LOADED :' WS-COUNT
           GOBACK.
