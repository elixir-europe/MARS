/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.biosamples.service;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptProvider;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
    super("biosamples");
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
   * Converting BioSample receipt to Mars data format
   *
   * @see
   *     https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   * @param biosampleAccessionsMap {@link BiosampleAccessionsMap} Receipt from Biosample
   * @param isaJson {@link IsaJson} Requested ISA-Json
   * @return {@link MarsReceipt} Mars response data
   */
  public MarsReceipt convertReceiptToMars(
      final BiosampleAccessionsMap biosampleAccessionsMap, final IsaJson isaJson) {
    buildMarsReceipt(
        biosampleAccessionsMap.studyAccessionsMap,
        biosampleAccessionsMap.sampleAccessionsMap,
        biosampleAccessionsMap.sourceAccessionsMap,
        null,
        null,
        null,
        null,
        isaJson);
    return getMarsReceipt();
  }

  @Override
  public String convertMarsReceiptToJson() {
    throw new RuntimeException("UNIMPLEMENTED");
  }
}
