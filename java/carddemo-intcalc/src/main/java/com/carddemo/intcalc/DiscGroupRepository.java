package com.carddemo.intcalc;

import java.util.Optional;

/**
 * DISCGRP: the disclosure group KSDS, read randomly by
 * account group id + transaction type + transaction category ({@code 1200-GET-INTEREST-RATE}).
 */
public interface DiscGroupRepository {

    Optional<DiscGroup> find(DiscGroupKey key);
}
