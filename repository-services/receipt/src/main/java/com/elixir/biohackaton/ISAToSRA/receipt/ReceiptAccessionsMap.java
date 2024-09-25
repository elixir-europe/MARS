package com.elixir.biohackaton.ISAToSRA.receipt;

import java.util.HashMap;

public class ReceiptAccessionsMap {
    public HashMap<String, String> accessionMap;

    public String isaKeyName;

    public ReceiptAccessionsMap() {
        accessionMap = new HashMap<>();
    }

    public ReceiptAccessionsMap(String isaKeyName, String isaKeyValue) {
        this.isaKeyName = isaKeyName;
        this.accessionMap = new HashMap<>() {
            {
                put(isaKeyValue, null);
            }
        };
    }
}
