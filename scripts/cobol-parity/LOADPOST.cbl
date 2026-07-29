      *****************************************************************
      * Program     : LOADPOST.CBL
      * Function    : Load the flat ASCII sample datasets in
      *               app/data/ASCII into GnuCOBOL indexed (ISAM)
      *               files, so that CBTRN02C can be run unmodified
      *               against them off the mainframe.
      *
      * This is test scaffolding for the COBOL/Java parity harness.
      * It is NOT part of the CardDemo application: on z/OS the
      * equivalent step is IDCAMS REPRO into a VSAM KSDS (see
      * app/jcl/TRANFILE.jcl and app/jcl/ACCTFILE.jcl).
      *
      * The three files loaded here are the ones POSTTRAN.jcl points
      * at that CBTRN02C reads by key: XREFFILE, ACCTFILE, TCATBALF.
      * TRANFILE is not loaded, because CBTRN02C opens it OUTPUT and
      * therefore creates it itself - see CBTRN02C-EXPLAINED.md R22.
      *
      * Record layouts and keys are copied from the SELECT clauses in
      * CBTRN02C so the indexed files match what it expects exactly.
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    LOADPOST.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
      * ---- flat inputs (one record per line, short lines padded) ----
           SELECT XREF-IN     ASSIGN TO XREFTXT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STATUS.
           SELECT ACCT-IN     ASSIGN TO ACCTTXT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STATUS.
           SELECT TCATBAL-IN  ASSIGN TO TCATBALT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STATUS.

      * ---- indexed outputs, keyed exactly as CBTRN02C expects ----
           SELECT XREF-OUT    ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-XREF-CARD-NUM
                  FILE STATUS  IS WS-OUT-STATUS.
           SELECT ACCT-OUT    ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-ACCT-ID
                  FILE STATUS  IS WS-OUT-STATUS.
           SELECT TCATBAL-OUT ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-TRAN-CAT-KEY
                  FILE STATUS  IS WS-OUT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  XREF-IN.
       01  XREF-IN-REC                          PIC X(50).
       FD  ACCT-IN.
       01  ACCT-IN-REC                          PIC X(300).
       FD  TCATBAL-IN.
       01  TCATBAL-IN-REC                       PIC X(50).

       FD  XREF-OUT.
       01  FD-XREFFILE-REC.
           05 FD-XREF-CARD-NUM                  PIC X(16).
           05 FD-XREF-DATA                      PIC X(34).

       FD  ACCT-OUT.
       01  FD-ACCTFILE-REC.
           05 FD-ACCT-ID                        PIC 9(11).
           05 FD-ACCT-DATA                      PIC X(289).

       FD  TCATBAL-OUT.
       01  FD-TRAN-CAT-BAL-RECORD.
           05 FD-TRAN-CAT-KEY.
              10 FD-TRANCAT-ACCT-ID             PIC 9(11).
              10 FD-TRANCAT-TYPE-CD             PIC X(02).
              10 FD-TRANCAT-CD                  PIC 9(04).
           05 FD-FD-TRAN-CAT-DATA               PIC X(33).

       WORKING-STORAGE SECTION.
       01  WS-IN-STATUS                         PIC X(02).
       01  WS-OUT-STATUS                        PIC X(02).
       01  WS-EOF                               PIC X(01).
       01  WS-COUNT                             PIC 9(09).

       PROCEDURE DIVISION.
           PERFORM 1000-LOAD-XREF
           PERFORM 2000-LOAD-ACCT
           PERFORM 3000-LOAD-TCATBAL
           GOBACK.

       1000-LOAD-XREF.
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           OPEN INPUT XREF-IN
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT XREF-OUT
           PERFORM 9010-CHECK-OUT
           PERFORM UNTIL WS-EOF = 'Y'
               READ XREF-IN
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE XREF-IN-REC TO FD-XREFFILE-REC
                       WRITE FD-XREFFILE-REC
                       PERFORM 9010-CHECK-OUT
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE XREF-IN XREF-OUT
           DISPLAY 'LOADED XREFFILE RECORDS: ' WS-COUNT.

       2000-LOAD-ACCT.
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           OPEN INPUT ACCT-IN
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT ACCT-OUT
           PERFORM 9010-CHECK-OUT
           PERFORM UNTIL WS-EOF = 'Y'
               READ ACCT-IN
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE ACCT-IN-REC TO FD-ACCTFILE-REC
                       WRITE FD-ACCTFILE-REC
                       PERFORM 9010-CHECK-OUT
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE ACCT-IN ACCT-OUT
           DISPLAY 'LOADED ACCTFILE RECORDS: ' WS-COUNT.

       3000-LOAD-TCATBAL.
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           OPEN INPUT TCATBAL-IN
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT TCATBAL-OUT
           PERFORM 9010-CHECK-OUT
           PERFORM UNTIL WS-EOF = 'Y'
               READ TCATBAL-IN
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE TCATBAL-IN-REC TO FD-TRAN-CAT-BAL-RECORD
                       WRITE FD-TRAN-CAT-BAL-RECORD
                       PERFORM 9010-CHECK-OUT
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE TCATBAL-IN TCATBAL-OUT
           DISPLAY 'LOADED TCATBALF RECORDS: ' WS-COUNT.

       9000-CHECK-IN.
           IF WS-IN-STATUS NOT = '00' AND WS-IN-STATUS NOT = '10'
               DISPLAY 'LOADPOST INPUT ERROR, STATUS: ' WS-IN-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.

       9010-CHECK-OUT.
           IF WS-OUT-STATUS NOT = '00'
               DISPLAY 'LOADPOST OUTPUT ERROR, STATUS: ' WS-OUT-STATUS
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.
