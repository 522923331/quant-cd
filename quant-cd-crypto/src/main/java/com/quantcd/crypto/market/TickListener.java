package com.quantcd.crypto.market;

public interface TickListener {

    void onTick(LiveQuote quote);

    void onGone(String symbol);
}
