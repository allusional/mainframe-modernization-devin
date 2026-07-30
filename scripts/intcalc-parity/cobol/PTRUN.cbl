      ******************************************************************
      * Program     : PTRUN.CBL
      * Function    : Harness only. Job step driver for CBACT04C, which
      *               has a PROCEDURE DIVISION USING clause (the z/OS
      *               PARM of STEP15 in app/jcl/INTCALC.jcl) and so
      *               cannot be compiled as a GnuCOBOL main program.
      *               CBACT04C is compiled unmodified as a module and
      *               called from here with the same halfword length +
      *               X(10) date parameter the JCL passes.
      *               The parm date is the first command line argument
      *               and defaults to the PARM of the JCL, 2022071800.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    PTRUN.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-ARG                  PIC X(10)  VALUE SPACES.
       01  EXTERNAL-PARMS.
           05  PARM-LENGTH         PIC S9(04) COMP.
           05  PARM-DATE           PIC X(10).

       PROCEDURE DIVISION.
           ACCEPT WS-ARG FROM COMMAND-LINE
           IF WS-ARG = SPACES
               MOVE '2022071800' TO PARM-DATE
           ELSE
               MOVE WS-ARG       TO PARM-DATE
           END-IF
           MOVE 10 TO PARM-LENGTH
           CALL 'CBACT04C' USING EXTERNAL-PARMS
           GOBACK.
