/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.biosamples.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptProvider;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.IsaJson;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsError;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsErrorType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Service
public class MarsReceiptService extends MarsReceiptProvider implements HandlerInterceptor {
  private final ObjectMapper jsonMapper = new ObjectMapper();

  private void setupJsonMapper() {
    jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  public MarsReceiptService() {
    super("biosamples"); // TODO decide whether to use instead
    // https://registry.identifiers.org/registry/biosample
    setupJsonMapper();
  }

  // Reset MARS receipt per request
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    resetMarsReceipt();
    return HandlerInterceptor.super.preHandle(request, response, handler);
  }

  public String convertMarsReceiptToJson() {
    try {
      return jsonMapper.writeValueAsString(getMarsReceipt());
    } catch (Exception ex) {
      throw new RuntimeException("Receipt", ex);
    }
  }

  public void setMarsReceiptErrors(String... errors) {
    super.setMarsReceiptErrors(MarsErrorType.INVALID_METADATA, errors);
  }

  public void setMarsReceiptErrors(MarsError... errors) {
    super.setMarsReceiptErrors(MarsErrorType.INVALID_METADATA, errors);
  }

  /**
   * Converting BioSample receipt to Mars data format
   *
   * @see <a href='https://github.com/elixir-europe/MARS/blob/main/repository-services/repository-api.md#response'>Repository API Specification</a>
   * @param biosampleAccessionsMap {@link BiosampleAccessionsMap} Receipt from Biosample
   * @param isaJson                {@link IsaJson} Requested ISA-Json
   */
  public void convertReceiptToMars(final BiosampleAccessionsMap biosampleAccessionsMap, final IsaJson isaJson) {
    buildMarsReceipt(
        biosampleAccessionsMap.studyAccessionsMap,
        biosampleAccessionsMap.sampleAccessionsMap,
        biosampleAccessionsMap.sourceAccessionsMap,
        null,
        null,
        null,
        null,
        isaJson);
  }
}
