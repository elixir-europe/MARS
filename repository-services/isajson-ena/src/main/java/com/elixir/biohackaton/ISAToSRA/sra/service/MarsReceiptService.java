/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.ReceiptObject;
import com.elixir.mars.repository.MarsReceiptProvider;
import com.elixir.mars.repository.ReceiptAccessionsMap;
import com.elixir.mars.repository.models.isa.DataFile;
import com.elixir.mars.repository.models.isa.IsaJson;
import com.elixir.mars.repository.models.isa.OtherMaterial;
import com.elixir.mars.repository.models.isa.Study;
import com.elixir.mars.repository.models.receipt.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@Scope("prototype")
@Service
public class MarsReceiptService extends MarsReceiptProvider {

  private final ObjectMapper jsonMapper = new ObjectMapper();

  private void setupJsonMapper() {
    jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  public MarsReceiptService() {
    super("ena");
    setupJsonMapper();
  }

  public String convertMarsReceiptToJson(final MarsReceipt marsReceipt) {
    try {
      return jsonMapper.writeValueAsString(marsReceipt);
    } catch (Exception ex) {
      throw new RuntimeException("receipt", ex);
    }
  }

  public void setMarsReceiptErrors(String... errors) {
    super.setMarsReceiptErrors(MarsErrorType.INVALID_METADATA, errors);
  }

  public void setMarsReceiptErrors(MarsError... errors) {
    super.setMarsReceiptErrors(MarsErrorType.INVALID_METADATA, errors);
  }

  /**
   * Converting ENA receipt to Mars data format
   *
   * @param receipt {@link Receipt} Receipt from ENA
   * @param isaJson {@link IsaJson} Requested ISA-Json
   * @return {@link MarsReceipt} Mars response data
   * @see https://elixir-europe.github.io/MARS/repository-services/repository-api
   */
  public MarsReceipt convertReceiptToMars(
      final Receipt receipt,
      final IsaJson isaJson) {
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
    return getMarsReceipt();
  }

  private ReceiptAccessionsMap getAliasAccessionPairs(
      String keyNameInput, final List<ReceiptObject> items) {
    Predicate<ReceiptObject> aliasAccessionPairValidateFn = this::aliasAccessionPairFilter;
    Function<ReceiptObject, String> getPreRandomizedAliasFn = this::getPreRandomizedAlias;

    return new ReceiptAccessionsMap() {
      {
        isaItemName = keyNameInput;
        accessionMap = new HashMap<>(
            Optional.ofNullable(items).orElse(new ArrayList<>()).stream()
                .filter(aliasAccessionPairValidateFn)
                .collect(
                    Collectors.toMap(getPreRandomizedAliasFn, ReceiptObject::getAccession)));
      }
    };
  }

  private boolean aliasAccessionPairFilter(ReceiptObject item) {
    if (item == null) {
      setMarsReceiptErrors("ENA receipt: Item is NULL");
      return false;
    }
    boolean valid = true;
    if (item.getAlias() == null) {
      setMarsReceiptErrors("ENA receipt: Alias is NULL");
      valid = false;
    }
    if (item.getAccession() == null) {
      setMarsReceiptErrors(
          String.format("ENA receipt: Accession number of %s is NULL", item.getAlias()));
      valid = false;
    }
    return valid;
  }

  private String getPreRandomizedAlias(@NotNull ReceiptObject receiptObject) {
    // EXAMPLE: Convert "Arabidopsis thaliana-0.49105604184136276" to "Arabidopsis
    // thaliana"
    final String alias = receiptObject.getAlias();
    final int lastIndexOfAcceptableAlias = alias.lastIndexOf('-');
    return alias.substring(
        0, lastIndexOfAcceptableAlias > 0 ? lastIndexOfAcceptableAlias : alias.length());
  }

  @Override
  public String convertMarsReceiptToJson() {
    return convertMarsReceiptToJson(getMarsReceipt());
  }
}
