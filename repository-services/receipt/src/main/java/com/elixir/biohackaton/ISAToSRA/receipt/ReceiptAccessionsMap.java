package com.elixir.biohackaton.ISAToSRA.receipt;

import java.util.HashMap;

public class ReceiptAccessionsMap {
    public HashMap<String, String> accessionMap;

    public String keyName;

    public ReceiptAccessionsMap() {
        accessionMap = new HashMap<>();
    }

    public ReceiptAccessionsMap(String keyName, String keyValue) {
        this.keyName = keyName;
        this.accessionMap = new HashMap<>() {
            {
                put(keyValue, null);
            }
        };
    }

    public String toString() {
        String result = "ReceiptAccessionsMap:" + keyName + "\n";
        for (String key: accessionMap.keySet()) {
            result += key + ":" + accessionMap.get(key) + "\n";
        }
        return result;
    }
}
