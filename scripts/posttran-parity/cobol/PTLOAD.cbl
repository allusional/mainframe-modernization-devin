      ******************************************************************
      * Program     : PTLOAD.CBL
      * Function    : Harness only. Loads the ASCII sample datasets in
      *               app/data/ASCII into the file organizations that
      *               CBTRN02C expects (VSAM KSDS -> GnuCOBOL INDEXED,
      *               QSAM -> record SEQUENTIAL). CBTRN02C itself is run
      *               unmodified.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    PTLOAD.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT XREF-IN   ASSIGN TO XREFIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT XREF-OUT  ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS XO-CARD-NUM
                  FILE STATUS  IS WS-STATUS.

           SELECT ACCT-IN   ASSIGN TO ACCTIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT ACCT-OUT  ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS AO-ACCT-ID
                  FILE STATUS  IS WS-STATUS.

           SELECT TCAT-IN   ASSIGN TO TCATIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT TCAT-OUT  ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS TO-TRAN-CAT-KEY
                  FILE STATUS  IS WS-STATUS.

           SELECT DTRAN-IN  ASSIGN TO DTRANIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

           SELECT DTRAN-OUT ASSIGN TO DALYTRAN
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS WS-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  XREF-IN.
       01  XI-REC                              PIC X(50).
       FD  XREF-OUT.
       01  XO-REC.
           05 XO-CARD-NUM                      PIC X(16).
           05 XO-DATA                          PIC X(34).
       FD  ACCT-IN.
       01  AI-REC                              PIC X(300).
       FD  ACCT-OUT.
       01  AO-REC.
           05 AO-ACCT-ID                       PIC X(11).
           05 AO-DATA                          PIC X(289).
       FD  TCAT-IN.
       01  TI-REC                              PIC X(50).
       FD  TCAT-OUT.
       01  TO-REC.
           05 TO-TRAN-CAT-KEY                  PIC X(17).
           05 TO-DATA                          PIC X(33).
       FD  DTRAN-IN.
       01  DI-REC                              PIC X(350).
       FD  DTRAN-OUT.
       01  DO-REC                              PIC X(350).

       WORKING-STORAGE SECTION.
       01  WS-STATUS                           PIC X(02).
       01  WS-EOF                              PIC X(01)  VALUE 'N'.
       01  WS-COUNT                            PIC 9(07)  VALUE 0.

       PROCEDURE DIVISION.
           PERFORM 1000-LOAD-XREF.
           PERFORM 2000-LOAD-ACCT.
           PERFORM 3000-LOAD-TCATBAL.
           PERFORM 4000-LOAD-DALYTRAN.
           GOBACK.

       1000-LOAD-XREF.
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

       2000-LOAD-ACCT.
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

       3000-LOAD-TCATBAL.
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

       4000-LOAD-DALYTRAN.
           OPEN INPUT DTRAN-IN
           OPEN OUTPUT DTRAN-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ DTRAN-IN INTO DO-REC
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE DO-REC
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE DTRAN-IN
           CLOSE DTRAN-OUT
           DISPLAY 'PTLOAD DALYTRAN RECORDS  :' WS-COUNT.
