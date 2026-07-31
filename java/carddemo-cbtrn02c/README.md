# CBTRN02C (POSTTRAN) — Java port

Java port of the CardDemo batch program [`app/cbl/CBTRN02C.cbl`](../../app/cbl/CBTRN02C.cbl),
run on the mainframe by [`app/jcl/POSTTRAN.jcl`](../../app/jcl/POSTTRAN.jcl): it posts the
daily transaction file against the account master, the transaction category balance file and
the transaction master, and writes rejected transactions to DALYREJS.

The port is byte-for-byte compatible with the COBOL program — see
[PARITY_REPORT.md](PARITY_REPORT.md).

## Layout

| Path | Contents |
| --- | --- |
| `src/main/java/.../copybook/` | One class per copybook (CVTRA06Y, CVTRA05Y, CVACT01Y, CVACT03Y, CVTRA01Y) with PIC-accurate fixed-width serialization |
| `src/main/java/.../io/` | `RecordFile` (QSAM-style fixed length records) and `IndexedFile` (KSDS-style keyed access) |
| `src/main/java/.../Cbtrn02c.java` | The port itself, one method per COBOL paragraph |
| `src/main/java/.../parity/ParityCompare.java` | Record-by-record diff of COBOL vs Java output datasets |
| `parity/cobol/` | GnuCOBOL load/dump utilities used to build the COBOL baseline |
| `parity/scripts/` | `run_cobol.sh`, `run_java.sh`, `run_parity.sh`, `prep_inputs.sh`, `gen_branches_dataset.py` |
| `parity/data/branches/` | Crafted dataset covering every validation branch |
| `parity/golden/` | COBOL baseline outputs committed as the parity reference |

## Build and test

```bash
mvn verify
```

## Run

Datasets are passed with the DD names of POSTTRAN, as `DD_<ddname>` environment variables
(the same convention GnuCOBOL uses, so both implementations are driven by identical scripts).
All files are fixed length records without delimiters; the indexed files are held in key order.

```bash
mvn -DskipTests package
DD_DALYTRAN=DALYTRAN.dat DD_XREFFILE=CARDXREF.dat DD_ACCTFILE=ACCTDATA.dat \
DD_TCATBALF=TCATBAL.dat DD_TRANFILE=TRANSACT.dat DD_DALYREJS=DALYREJS.dat \
  java -jar target/carddemo-cbtrn02c-1.0.0-SNAPSHOT.jar
```

Exit code is 4 when any transaction was rejected, matching `MOVE 4 TO RETURN-CODE`.

## Parity run

```bash
sudo apt-get install -y gnucobol3      # provides cobc
./parity/scripts/run_parity.sh         # scenarios: branches, full
```
