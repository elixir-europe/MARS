/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptProvider;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.*;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.ReceiptObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MarsReceiptService extends MarsReceiptProvider {
  private final ObjectMapper jsonMapper = new ObjectMapper();

  private void setupJsonMapper() {
    jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  public MarsReceiptService() {
    setupJsonMapper();
  }

  public String convertMarsReceiptToJson(final MarsReceipt marsReceipt) {
    try {
      return jsonMapper.writeValueAsString(marsReceipt);
    } catch (Exception ex) {
      throw new RuntimeException("receipt", ex);
    }
  }

  /**
   * Converting ENA receipt to Mars data format
   *
   * @see
   *     https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   * @param receipt {@link Receipt} Receipt from ENA
   * @param isaJson {@link IsaJson} Requested ISA-Json
   * @return {@link MarsReceipt} Mars response data
   */
  public MarsReceipt convertReceiptToMars(final Receipt receipt, final IsaJson isaJson) {
    return buildMarsReceipt(
        "ena.embl", // https://registry.identifiers.org/registry/ena.embl
        getAliasAccessionPairs(
            Optional.ofNullable(receipt.getStudies()).orElse(receipt.getProjects())),
        getAliasAccessionPairs(receipt.getSamples()),
        getAliasAccessionPairs(receipt.getExperiments()),
        getAliasAccessionPairs(receipt.getRuns()),
        receipt.getMessages().getInfoMessages(),
        receipt.getMessages().getErrorMessages(),
        isaJson);
  }

  private HashMap<String, String> getAliasAccessionPairs(final List<ReceiptObject> items) {
    return new HashMap<String, String>(
        Optional.ofNullable(items).orElse(new ArrayList<>()).stream()
            .filter(item -> item != null)
            .collect(Collectors.toMap(ReceiptObject::getAlias, ReceiptObject::getAccession)));
  }
}