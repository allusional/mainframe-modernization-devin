# Running the CardDemo interest calculator locally

This guide takes you from a machine with **nothing installed** to a working end-to-end run of
the Java port of `CBACT04C`, the batch program that calculates monthly credit card interest.
No mainframe, no emulator, no AWS account is needed: the port reads the same fixed-width
sample data files that ship in this repo (`app/data/ASCII`).

If you want to know what the program actually does before running it, read
[`CBACT04C-EXPLAINED.md`](CBACT04C-EXPLAINED.md).

---

## 1. Install the toolchain (macOS)

You need two things: **JDK 17 or newer** and **Maven**. Copy and paste the commands below
into Terminal, in order.

### 1a. Install Homebrew (skip if `brew --version` already works)

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

The installer prints two `echo`/`eval` commands at the end telling you to add Homebrew to
your `PATH`. **Run them** — on Apple Silicon they look like this:

```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

On an Intel Mac, Homebrew lives in `/usr/local` instead and usually needs no `PATH` change.

Verify:

```bash
brew --version
```

### 1b. Install JDK 17 and Maven

```bash
brew install --cask temurin@17
brew install maven
```

`temurin@17` is the Eclipse Temurin build of OpenJDK 17. Installing it as a cask requires
your macOS password (it writes to `/Library/Java/JavaVirtualMachines`).

### 1c. Point your shell at JDK 17

macOS can have several JDKs installed at once, and Maven uses whichever `JAVA_HOME` points at.
Set it for this session, and add the same line to `~/.zshrc` to make it permanent:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### 1d. Verify

```bash
java -version   # should print: openjdk version "17.x.x"
mvn -version    # should print Apache Maven 3.x and the same Java 17 home
```

Both commands must succeed before you continue. If `java -version` prints a version older
than 17, re-run the `JAVA_HOME` export in step 1c.

<details>
<summary>Linux (Ubuntu/Debian) equivalent</summary>

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

</details>

---

## 2. Get the code

```bash
git clone https://github.com/allusional/mainframe-modernization-devin.git
cd mainframe-modernization-devin
```

---

## 3. Run the whole demo with one command

```bash
./scripts/run-java-demo.sh
```

That single script does everything:

1. checks that `java` (17+) and `mvn` are installed and prints their versions;
2. builds the module and runs the full test suite (`mvn -B verify`);
3. runs the program against the untouched sample data in `app/data/ASCII`;
4. runs it again with a few non-zero balances patched in, so there is real interest to see;
5. prints the generated interest transactions and the before/after account balances.

It takes about a minute the first time (Maven downloads its dependencies) and a few seconds
after that. Nothing outside the repo is modified — all output goes to
`java/cbact04c/target/demo/`, and the sample data files themselves are copied, never edited.

### What you should see

```
== 1/5 Checking the toolchain
  openjdk version "17.0.13" 2024-10-15
  Apache Maven 3.9.x

== 2/5 Building and testing
  ...
  Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS

== 3/5 Running against the untouched sample data (app/data/ASCII)
START OF EXECUTION OF PROGRAM CBACT04C
CATEGORY BALANCES READ  : 50
INTEREST TRANSACTIONS   : 50
ACCOUNTS UPDATED        : 50
END OF EXECUTION OF PROGRAM CBACT04C
  Every balance in the shipped tcatbal.txt is zero, so the amounts below are 0.00.

== 4/5 Running again with non zero balances
START OF EXECUTION OF PROGRAM CBACT04C
CATEGORY BALANCES READ  : 3
INTEREST TRANSACTIONS   : 3
ACCOUNTS UPDATED        : 3
END OF EXECUTION OF PROGRAM CBACT04C

== 5/5 Results
Interest transactions generated (copybook CVTRA05Y, 350 bytes each):
  TRAN ID          TYPE CAT    AMOUNT       CARD             DESCRIPTION
  2022071800000001 01   0005   125.00       9680294154603697 Int. for a/c 00000000001
  2022071800000002 01   0005   125.00       0923877193247330 Int. for a/c 00000000002
  2022071800000003 01   0005   125.00       3999169246375885 Int. for a/c 00000000003

Account master, before and after (copybook CVACT01Y):
  ACCOUNT      BALANCE BEFORE BALANCE AFTER  INTEREST POSTED
  00000000001  194.00         319.00         125.00
  00000000002  158.00         283.00         125.00
  00000000003  147.00         272.00         125.00
```

### How to read that

- A **10,000.00** balance at the **15.00%** annual rate produces one month of interest:
  `10000.00 * 15.00 / 1200 = 125.00`. That is exactly the formula in the COBOL.
- Each interest charge becomes a **transaction record** (type `01`, category `0005`, which the
  reference data calls "Interest Amount") and is also **added to the account balance**.
- The amounts in the raw files look strange (`0000001250{`) because COBOL stores numbers as
  digits with the sign folded into the last character. `{` means "positive, and the final
  digit is 0", so `0000001250{` is `125.00`. The script decodes this for you.

---

## 4. Running the individual pieces

Everything below is run from `java/cbact04c/`.

```bash
cd java/cbact04c
```

| What | Command |
| --- | --- |
| Run the tests only | `mvn test` |
| Build the runnable jar | `mvn package` |
| Tests + jar | `mvn verify` |
| Start from scratch | `mvn clean verify` |

To run the program yourself against files of your choosing:

```bash
java -jar target/cbact04c-1.0.0-SNAPSHOT.jar \
  --parm 2022071800 \
  --tcatbal ../../app/data/ASCII/tcatbal.txt \
  --acct    /tmp/acctdata.txt \
  --xref    ../../app/data/ASCII/cardxref.txt \
  --discgrp ../../app/data/ASCII/discgrp.txt \
  --out-transact /tmp/systran.txt
```

`--acct` is **updated in place**, because the COBOL opens the account master for update.
Copy the sample file first (`cp ../../app/data/ASCII/acctdata.txt /tmp/acctdata.txt`) or pass
`--out-acct` to write the updated master elsewhere. Run with no arguments to see the full
usage text, including `--emulate-final-account-quirk`, which reproduces a defect in the
original COBOL. See [`java/cbact04c/README.md`](java/cbact04c/README.md) for the details.

---

## 5. The browser version (nothing to install at all)

For showing the calculation on a screen there is a standalone page at
[`web/index.html`](web/index.html). Open it directly — no server, no build, no toolchain:

```bash
open web/index.html          # macOS; on Linux use: xdg-open web/index.html
```

It runs the same logic in JavaScript over the same sample data and renders one row per
account: balance before, the interest rate applied, the interest charged, and the balance
after. The shipped balances are all `0.00`, so by default the page charges a balance of
`10,000.00` per category to make the arithmetic visible; tick the checkbox to see the raw
shipped figures instead.

The data is embedded in `web/data.js` because browsers refuse to read sibling files over
`file://`. Regenerate it after changing anything in `app/data/ASCII`:

```bash
./scripts/build-web-data.sh
```

---

## 6. Checking the Java against the real COBOL

The tests prove the Java does what we *read* the COBOL to do. To prove it does what the COBOL
*does*, there is a differential harness that compiles `app/cbl/CBACT04C.cbl` unmodified with
GnuCOBOL, runs both programs over the same inputs, and diffs the output files byte for byte:

```bash
sudo apt-get install -y gnucobol3      # macOS: brew install gnu-cobol
./scripts/cobol-parity/run-parity.sh
```

All 50 transaction records match byte for byte, and the only account that differs is the last
one — the defect documented in `CBACT04C-EXPLAINED.md`, which the port fixes. Details, caveats
and the GnuCOBOL flags that matter are in [`COBOL-PARITY.md`](COBOL-PARITY.md).

---

## 7. Continuous integration

`.github/workflows/java-cbact04c.yml` runs the same build, the same tests, and the same demo
script on every pull request and on every push to `main`, against both JDK 17 and JDK 21, plus
the COBOL/Java differential test above. If `./scripts/run-java-demo.sh` passes locally, CI
should pass too.

---

## Troubleshooting

**`zsh: command not found: brew`**
Homebrew is installed but not on your `PATH`. Run the `eval "$(/opt/homebrew/bin/brew shellenv)"`
line from step 1a, then open a new Terminal window.

**`mvn` uses the wrong Java version**
`mvn -version` prints the JDK it is actually using. If it is not 17+, re-run
`export JAVA_HOME=$(/usr/libexec/java_home -v 17)` in the same shell you run Maven from.

**`/usr/libexec/java_home -v 17` says "Unable to find any JVMs matching version 17"**
The Temurin cask did not install. Re-run `brew install --cask temurin@17` and watch for a
password prompt or a permission error.

**`Could not transfer artifact ... 429`**
Maven Central is rate-limiting your IP. Wait a few minutes and retry, or add a mirror to
`~/.m2/settings.xml`:

```xml
<settings>
  <mirrors>
    <mirror>
      <id>gcs-central</id>
      <url>https://maven-central.storage-download.googleapis.com/maven2</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

**`permission denied: ./scripts/run-java-demo.sh`**
Run `chmod +x scripts/run-java-demo.sh` once, or invoke it as `bash scripts/run-java-demo.sh`.

**Something abends with `ACCOUNT NOT FOUND` or `ERROR READING DEFAULT DISCLOSURE GROUP`**
That is the port faithfully reproducing the COBOL's error handling: an account, cross
reference, or `DEFAULT` rate row referenced by your input data is missing. Check that all
four input files come from the same data set.

---

## The nightly posting job (CBTRN02C), in one command

`CBACT04C` is the interest calculator. The other program modernised in this repo is
`CBTRN02C`, the job that posts the previous day's card transactions overnight — see
[`CBTRN02C-EXPLAINED.md`](CBTRN02C-EXPLAINED.md).

From a clean machine:

```bash
./scripts/run-cbtrn02c.sh
```

It installs what is missing (JDK 17, Maven, GnuCOBOL 3 — with `sudo apt-get` on
Debian/Ubuntu; on macOS run `brew install openjdk@17 maven gnu-cobol` first and pass
`--skip-install`), builds the Java port, runs its unit tests, compiles the **real COBOL** and
diffs it against the Java byte for byte, and writes a static HTML page of the run. The script
prints a `file://` URL at the end; open it in any browser — no server, no build step, one
self-contained file:

```
target/cbtrn02c-report/index.html
```

The page shows the daily feed that went in, each transaction posted or rejected with its
reason code, the balance movement on every account, and the totals. The figures come from the
files the COBOL actually wrote.

Individual pieces, if you want them separately:

| Command | What it does |
| --- | --- |
| `(cd java && mvn -B test)` | the unit tests for both ports |
| `./scripts/cobol-parity/run-posting-parity.sh` | COBOL vs Java, shipped data and adversarial edge cases |
| `./scripts/cobol-parity/run-posting-fuzz.sh --seeds 200` | COBOL vs Java on 200 random feeds drawn from the record grammar |
| `python3 scripts/report/build-posting-report.py target/cbtrn02c-parity/shipped out.html` | rebuild the report from an existing run |

Both are also run on every pull request by the `Java CBTRN02C` GitHub Actions workflow.
