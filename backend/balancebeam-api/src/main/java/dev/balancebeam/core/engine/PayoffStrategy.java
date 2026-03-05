package dev.balancebeam.core.engine;

import java.util.Map;

import dev.balancebeam.core.model.Debt;

import java.util.List;

public interface PayoffStrategy {

    Map<String, Long> allocateExtra(List<Debt> debts, long extraCents);

}
