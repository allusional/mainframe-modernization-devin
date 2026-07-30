package com.carddemo.posttran;

import java.util.ArrayList;
import java.util.List;

/** In-memory stand-in for the DALYREJS output file. */
public class InMemoryRejectWriter implements RejectWriter {

    private final List<RejectRecord> written = new ArrayList<>();

    @Override
    public void write(RejectRecord reject) {
        written.add(reject);
    }

    public List<RejectRecord> written() {
        return written;
    }
}
