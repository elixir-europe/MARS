/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptProvider;
import com.elixir.biohackaton.ISAToSRA.receipt.ReceiptAccessionsMap;
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;

@Service
public class MarsReceiptService extends MarsReceiptProvider implements HandlerInterceptor {
  private final ObjectMapper jsonMapper = new ObjectMapper();

  private void setupJsonMapper() {
    jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  public MarsReceiptService() {
    super("ena"); // TODO decide whether to use instead
    // https://registry.identifiers.org/registry/ena.embl
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
    super.setMarsReceiptErrors(MarsErrorType.INTERNAL_SERVER_ERROR, errors);
  }

  /**
   * Converting ENA receipt to Mars data format
   *
   * @see
   *     https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   * @param receipt {@link Receipt} Receipt from ENA
   * @param isaJson {@link IsaJson} Requested ISA-Json
   */
  public void convertReceiptToMars(final Receipt receipt, final IsaJson isaJson) {
    buildMarsReceipt(
        getAliasAccessionPairs(
            Study.Fields.title,
            Optional.ofNullable(receipt.getStudies()).orElse(receipt.getProjects())),
        null,
        null,
        getAliasAccessionPairs(OtherMaterial.Fields.id, receipt.getExperiments()),
        getAliasAccessionPairs(DataFile.Fields.id, receipt.getRuns()),
        receipt.getMessages().getInfoMessages(),
        receipt.getMessages().getErrorMessages(),
        isaJson);
  }

  private static String getPreRandomizedAlias(ReceiptObject receiptObject) {
    // Convert Arabidopsis thaliana-0.49105604184136276 -> Arabidopsis thaliana
    String alias = receiptObject.getAlias();
    return alias.substring(0, alias.lastIndexOf("-"));
  }

  private ReceiptAccessionsMap getAliasAccessionPairs(
      String keyNameInput, final List<ReceiptObject> items) {
    return new ReceiptAccessionsMap() {
      {
        keyName = keyNameInput;
        accessionMap =
            new HashMap<String, String>(
                Optional.ofNullable(items).orElse(new ArrayList<>()).stream()
                    .filter(item -> item != null)
                    .filter(item -> item.getAccession() != null)
                    .collect(
                        Collectors.toMap(
                            MarsReceiptService::getPreRandomizedAlias,
                            ReceiptObject::getAccession)));
      }
    };
  }
}
