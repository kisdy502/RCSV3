package com.jizhi.modbus.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class TransactionIdGenerator {
    private static final AtomicInteger tid = new AtomicInteger(0);

    public static int generateTid() {
        return tid.incrementAndGet();
    }
}
