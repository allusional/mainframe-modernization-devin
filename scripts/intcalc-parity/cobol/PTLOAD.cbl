      ******************************************************************
      * Program     : PTLOAD.CBL
      * Function    : Harness only. Loads the ASCII sample datasets in
      *               app/data/ASCII into the file organizations that
      *               CBACT04C expects (VSAM KSDS -> GnuCOBOL INDEXED),
      *               including the XREFFILE account id alternate index.
      *               CBACT04C itself is run unmodified.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    PTLOAD.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TCAT-IN   ASSIGN TO TCATIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT TCAT-OUT  ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS TO-TRAN-CAT-KEY
                  FILE STATUS  IS WS-STATUS.

           SELECT XREF-IN   ASSIGN TO XREFIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT XREF-OUT  ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS XO-CARD-NUM
                  ALTERNATE RECORD KEY IS XO-ACCT-ID
                  FILE STATUS  IS WS-STATUS.

           SELECT DISC-IN   ASSIGN TO DISCIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT DISC-OUT  ASSIGN TO DISCGRP
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS DO-DISCGRP-KEY
                  FILE STATUS  IS WS-STATUS.

           SELECT ACCT-IN   ASSIGN TO ACCTIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT ACCT-OUT  ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS AO-ACCT-ID
                  FILE STATUS  IS WS-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  TCAT-IN.
       01  TI-REC                              PIC X(50).
       FD  TCAT-OUT.
       01  TO-REC.
           05 TO-TRAN-CAT-KEY                  PIC X(17).
           05 TO-DATA                          PIC X(33).
       FD  XREF-IN.
       01  XI-REC                              PIC X(50).
       FD  XREF-OUT.
       01  XO-REC.
           05 XO-CARD-NUM                      PIC X(16).
           05 XO-CUST-NUM                      PIC 9(09).
           05 XO-ACCT-ID                       PIC 9(11).
           05 XO-FILLER                        PIC X(14).
       FD  DISC-IN.
       01  DI-REC                              PIC X(50).
       FD  DISC-OUT.
       01  DO-REC.
           05 DO-DISCGRP-KEY                   PIC X(16).
           05 DO-DATA                          PIC X(34).
       FD  ACCT-IN.
       01  AI-REC                              PIC X(300).
       FD  ACCT-OUT.
       01  AO-REC.
           05 AO-ACCT-ID                       PIC X(11).
           05 AO-DATA                          PIC X(289).

       WORKING-STORAGE SECTION.
       01  WS-STATUS                           PIC X(02).
       01  WS-EOF                              PIC X(01)  VALUE 'N'.
       01  WS-COUNT                            PIC 9(07)  VALUE 0.

       PROCEDURE DIVISION.
           PERFORM 1000-LOAD-TCATBAL.
           PERFORM 2000-LOAD-XREF.
           PERFORM 3000-LOAD-DISCGRP.
           PERFORM 4000-LOAD-ACCT.
           GOBACK.

       1000-LOAD-TCATBAL.
           OPEN INPUT TCAT-IN
           OPEN OUTPUT TCAT-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ TCAT-IN INTO TO-REC
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE TO-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE TCAT-IN
           CLOSE TCAT-OUT
           DISPLAY 'PTLOAD TCATBALF RECORDS  :' WS-COUNT.

       2000-LOAD-XREF.
           OPEN INPUT XREF-IN
           OPEN OUTPUT XREF-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ XREF-IN INTO XO-REC
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE XO-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE XREF-IN
           CLOSE XREF-OUT
           DISPLAY 'PTLOAD XREFFILE RECORDS  :' WS-COUNT.

       3000-LOAD-DISCGRP.
           OPEN INPUT DISC-IN
           OPEN OUTPUT DISC-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ DISC-IN INTO DO-REC
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE DO-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE DISC-IN
           CLOSE DISC-OUT
           DISPLAY 'PTLOAD DISCGRP RECORDS   :' WS-COUNT.

       4000-LOAD-ACCT.
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
           DISPLAY 'PTLOAD ACCTFILE RECORDS  :' WS-COUNT.
