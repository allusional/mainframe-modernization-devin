package com.aws.carddemo.cbact04c.repository;

import com.aws.carddemo.cbact04c.model.AccountRecord;

import java.util.Optional;

/** Random-access view of the Account master file (ACCTFILE / VSAM KSDS). */
public interface AccountRepository {

    /** Read by account id; empty mirrors a COBOL INVALID KEY / file status '23'. */
    Optional<AccountRecord> read(long accountId);

    /** Persist an updated account record, mirroring the COBOL REWRITE. */
    void rewrite(AccountRecord account);
}
