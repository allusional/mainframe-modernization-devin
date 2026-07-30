package com.carddemo.posttran;

/** REJECT-RECORD: the rejected daily transaction plus WS-VALIDATION-TRAILER. */
public record RejectRecord(DailyTransaction transaction, int failReason, String failReasonDesc) {
}
