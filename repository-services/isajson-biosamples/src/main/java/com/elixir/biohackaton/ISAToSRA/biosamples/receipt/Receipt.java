package com.elixir.biohackaton.ISAToSRA.biosamples.receipt;

import lombok.Getter;

import java.util.List;

@Getter
public class Receipt {
    private String targetRepository;
    private List<Accession> accessions;
    private List<Error> errors;
    private List<Info> info;

    public void setTargetRepository(String targetRepository) {
        this.targetRepository = targetRepository;
    }

    public void setAccessions(List<Accession> accessions) {
        this.accessions = accessions;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public void setInfo(List<Info> info) {
        this.info = info;
    }

    @Getter
    public static class Accession {
        private String id;
        private String data;

        public Accession(String id, String data) {
            this.id = id;
            this.data = data;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    // Error class
    @Getter
    static class Error {
        private String code;
        private String message;

        public Error(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    // Info class
    @Getter
    static class Info {
        // Define fields, constructor, getters, and setters
        private String key;
        private String value;

        public Info(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
