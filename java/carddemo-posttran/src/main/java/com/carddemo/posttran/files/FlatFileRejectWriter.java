package com.carddemo.posttran.files;

import com.carddemo.posttran.RejectRecord;
import com.carddemo.posttran.RejectWriter;
import java.util.ArrayList;
import java.util.List;

/** DALYREJS: sequential output, written by 2500-WRITE-REJECT-REC in arrival order. */
public class FlatFileRejectWriter implements RejectWriter {

    private final List<String> records = new ArrayList<>();

    @Override
    public void write(RejectRecord reject) {
        records.add(Layouts.reject(reject));
    }

    public List<String> records() {
        return List.copyOf(records);
    }
}
