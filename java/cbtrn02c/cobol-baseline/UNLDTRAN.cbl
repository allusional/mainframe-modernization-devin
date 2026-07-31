      ******************************************************************
      * Utility  : UNLDTRAN
      * Function : Unload the indexed TRANSACT file written by CBTRN02C
      *            to a flat 350 byte fixed length file, in key order,
      *            for byte level comparison against the Java port.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    UNLDTRAN.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE  ASSIGN TO INFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS IN-KEY
                  FILE STATUS  IS IN-STATUS.
           SELECT OUT-FILE ASSIGN TO OUTFILE
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS OUT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  IN-FILE.
       01  IN-REC.
           05 IN-KEY                 PIC X(16).
           05 IN-DATA                PIC X(334).
       FD  OUT-FILE.
       01  OUT-REC                   PIC X(350).

       WORKING-STORAGE SECTION.
       01  IN-STATUS                 PIC X(02).
       01  OUT-STATUS                PIC X(02).
       01  WS-EOF                    PIC X VALUE 'N'.
       01  WS-COUNT                  PIC 9(09) VALUE 0.

       PROCEDURE DIVISION.
           OPEN INPUT IN-FILE
           OPEN OUTPUT OUT-FILE
           PERFORM UNTIL WS-EOF = 'Y'
              READ IN-FILE NEXT RECORD
                 AT END MOVE 'Y' TO WS-EOF
                 NOT AT END
                    MOVE IN-REC TO OUT-REC
                    WRITE OUT-REC
                    ADD 1 TO WS-COUNT
              END-READ
           END-PERFORM
           CLOSE IN-FILE
           CLOSE OUT-FILE
           DISPLAY 'UNLDTRAN RECORDS UNLOADED :' WS-COUNT
           GOBACK.
