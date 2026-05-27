/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptException;
import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptProvider;
import com.elixir.biohackaton.ISAToSRA.receipt.ReceiptAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Assay;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.DataFile;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.IsaJson;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.OtherMaterial;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Output;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.ProcessSequence;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsError;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsErrorType;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsReceipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.ReceiptObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
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
   * @see
   *     https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   */
  public MarsReceipt convertReceiptToMars(final Receipt receipt, final IsaJson isaJson) {
    buildMarsReceipt(
        getAliasAccessionPairs(
            // ENA study/project aliases are assay-based, so the returned accession path points to
            // the assay rather than the parent study title.
            Assay.Fields.id,
            Optional.ofNullable(receipt.getStudies()).orElse(receipt.getProjects())),
        null,
        null,
        getAliasAccessionPairs(OtherMaterial.Fields.id, receipt.getExperiments()),
        getRunAliasAccessionPairs(receipt.getRuns(), isaJson),
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
        accessionMap =
            new HashMap<>(
                Optional.ofNullable(items).orElse(new ArrayList<>()).stream()
                    .filter(aliasAccessionPairValidateFn)
                    .collect(
                        Collectors.toMap(getPreRandomizedAliasFn, ReceiptObject::getAccession)));
      }
    };
  }

  private ReceiptAccessionsMap getRunAliasAccessionPairs(
      final List<ReceiptObject> items, final IsaJson isaJson) {
    Predicate<ReceiptObject> aliasAccessionPairValidateFn = this::aliasAccessionPairFilter;
    final Map<String, String> accessionMap = new HashMap<>();
    final List<List<String>> sequencingProcessDataFiles =
        getSequencingProcessDataFileIdsInSubmissionOrder(isaJson);
    int sequencingProcessIndex = 0;

    Optional.ofNullable(items)
        .orElse(new ArrayList<>())
        .stream()
        .filter(aliasAccessionPairValidateFn)
        .forEach(
            receiptObject -> {
              final String processId = getPreRandomizedAlias(receiptObject);
              List<String> dataFileIds = getDataFileIdsForSequencingProcess(isaJson, processId);
              if (dataFileIds.isEmpty()
                  && sequencingProcessIndex < sequencingProcessDataFiles.size()) {
                dataFileIds = sequencingProcessDataFiles.get(sequencingProcessIndex);
              }

              dataFileIds.forEach(
                  dataFileId -> accessionMap.put(dataFileId, receiptObject.getAccession()));
              sequencingProcessIndex++;
            });

    return new ReceiptAccessionsMap() {
      {
        isaItemName = DataFile.Fields.id;
        this.accessionMap = accessionMap;
      }
    };
  }

  private List<String> getDataFileIdsForSequencingProcess(
      final IsaJson isaJson, final String sequencingProcessId) {
    final List<String> dataFileIds = new ArrayList<>();

    Optional.ofNullable(isaJson.getInvestigation())
        .map(investigation -> investigation.getStudies())
        .orElse(new ArrayList<>())
        .forEach(
            study ->
                Optional.ofNullable(study.getAssays())
                    .orElse(new ArrayList<>())
                    .forEach(
                        assay ->
                            Optional.ofNullable(assay.getProcessSequence())
                                .orElse(new ArrayList<>())
                                .stream()
                                .filter(process -> sequencingProcessId.equals(process.getId()))
                                .findFirst()
                                .ifPresent(
                                    process ->
                                        Optional.ofNullable(process.getOutputs())
                                            .orElse(new ArrayList<>())
                                            .stream()
                                            .map(Output::getId)
                                            .filter(id -> id != null && !id.isBlank())
                                            .map(this::normalizeDataFileId)
                                            .forEach(dataFileIds::add))));

    return dataFileIds;
  }

  private List<List<String>> getSequencingProcessDataFileIdsInSubmissionOrder(final IsaJson isaJson) {
    final List<List<String>> sequencingProcessDataFiles = new ArrayList<>();
    final Set<String> processedSequencingProcesses = new HashSet<>();

    Optional.ofNullable(isaJson.getInvestigation())
        .map(investigation -> investigation.getStudies())
        .orElse(new ArrayList<>())
        .forEach(
            study ->
                Optional.ofNullable(study.getAssays())
                    .orElse(new ArrayList<>())
                    .forEach(assay -> addAssaySequencingProcessOutputs(
                        sequencingProcessDataFiles, processedSequencingProcesses, assay)));

    return sequencingProcessDataFiles;
  }

  private void addAssaySequencingProcessOutputs(
      final List<List<String>> sequencingProcessDataFiles,
      final Set<String> processedSequencingProcesses,
      final Assay assay) {
    if (assay.getDataFiles() == null || assay.getProcessSequence() == null) {
      return;
    }

    for (final DataFile dataFile : assay.getDataFiles()) {
      final ProcessSequence sequencingProcess =
          findProcessByOutputId(assay.getProcessSequence(), dataFile.getId());
      if (sequencingProcess == null || !processedSequencingProcesses.add(sequencingProcess.getId())) {
        continue;
      }

      final List<String> dataFileIds = new ArrayList<>();
      Optional.ofNullable(sequencingProcess.getOutputs())
          .orElse(new ArrayList<>())
          .stream()
          .map(Output::getId)
          .filter(id -> id != null && !id.isBlank())
          .map(this::normalizeDataFileId)
          .forEach(dataFileIds::add);
      sequencingProcessDataFiles.add(dataFileIds);
    }
  }

  private ProcessSequence findProcessByOutputId(
      final List<ProcessSequence> processSequence, final String outputId) {
    if (processSequence == null || outputId == null) {
      return null;
    }

    final String normalizedOutputId = normalizeDataFileId(outputId);

    for (final ProcessSequence process : processSequence) {
      if (process.getOutputs() == null) {
        continue;
      }

      for (final Output output : process.getOutputs()) {
        if (output.getId() == null) {
          continue;
        }

        if (normalizeDataFileId(output.getId()).equals(normalizedOutputId)) {
          return process;
        }
      }
    }

    return null;
  }

  private String normalizeDataFileId(final String id) {
    return id == null ? null : id.replace("#data_file/", "#data/");
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
    // Convert #assay/18_20_21-0.49105604184136276 -> #assay/18_20_21
    final String alias = receiptObject.getAlias();
    final int lastIndexOfAcceptableAlias = alias.lastIndexOf('-');
    return alias.substring(
        0, lastIndexOfAcceptableAlias > 0 ? lastIndexOfAcceptableAlias : alias.length());
  }

  @Override
  public String convertMarsReceiptToJson() {
    throw new MarsReceiptException("METHOD NOT IMPLEMENTED");
  }
}
