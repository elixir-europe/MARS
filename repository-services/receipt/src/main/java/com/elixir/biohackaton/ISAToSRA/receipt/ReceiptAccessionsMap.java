package com.elixir.biohackaton.ISAToSRA.receipt;

import java.util.HashMap;

public class ReceiptAccessionsMap {
    /**
     * Key and accession number of ISA-JSON item
     */
    public HashMap<String, String> accessionMap;

    /**
     * ISA-JSON item name
     */
    public String isaItemName;

    public ReceiptAccessionsMap() {
        accessionMap = new HashMap<>();
    }

    /**
     * @param itemName ISA-JSON key name
     * @param key      ISA-JSON key value
     */
    public ReceiptAccessionsMap(String itemName, String key) {
        this.isaItemName = itemName;
        this.accessionMap = new HashMap<>() {
            {
                put(key, null);
            }
        };
    }

    public String toString() {
        String result = "ReceiptAccessionsMap:" + isaItemName + "\n";
        for (String key : accessionMap.keySet()) {
            result += key + ":" + accessionMap.get(key) + "\n";
        }
        return result;
    }
}
