      *****************************************************************
      * Program     : RUNINTC.CBL
      * Function    : Stand in for the JCL step
      *                 //STEP15 EXEC PGM=CBACT04C,PARM='2022071800'
      *               by calling CBACT04C with the same PARM structure
      *               (halfword length + 10 character date) that z/OS
      *               passes in.
      *
      * Test scaffolding for the parity harness. CBACT04C itself is
      * compiled and run completely unmodified.
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    RUNINTC.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  EXTERNAL-PARMS.
           05  PARM-LENGTH             PIC S9(04) COMP VALUE 10.
           05  PARM-DATE               PIC X(10)  VALUE '2022071800'.

       PROCEDURE DIVISION.
           CALL 'CBACT04C' USING EXTERNAL-PARMS
           GOBACK.
