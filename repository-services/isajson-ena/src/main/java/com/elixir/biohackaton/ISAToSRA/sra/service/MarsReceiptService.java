/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.MarsReceiptProvider;
import com.elixir.mars.repository.ReceiptAccessionsMap;
import com.elixir.mars.repository.IsaJsonGraphLookup;
import com.elixir.mars.repository.models.isa.Assay;
import com.elixir.mars.repository.models.isa.DataFile;
import com.elixir.mars.repository.models.isa.IsaJson;
import com.elixir.mars.repository.models.isa.OtherMaterial;
import com.elixir.mars.repository.models.isa.ProcessSequence;
import com.elixir.mars.repository.models.receipt.MarsError;
import com.elixir.mars.repository.models.receipt.MarsErrorType;
import com.elixir.mars.repository.models.receipt.MarsReceipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.ReceiptObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Converts ENA Webin receipt objects into the repository-neutral MARS receipt format.
 *
 * <p>ENA returns accessions keyed by generated submission aliases. This service strips the random
 * submission suffix and maps those accessions back onto the ISA study, library, and data file items
 * used by the MARS receipt model.
 */
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
   * @see
   *     https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   */
  public MarsReceipt convertReceiptToMars(final Receipt receipt, final IsaJson isaJson) {
    // The service is a singleton, so discard accessions collected for an earlier request.
    resetMarsReceipt();
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

  /**
   * Extracts receipt alias/accession pairs for a single ISA item type.
   *
   * <p>The alias stored in the ENA receipt includes the random submission suffix, so keys are
   * normalized back to the original ISA IDs before building the MARS accession map.
   */
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

  /**
   * Maps ENA RUN accessions back to ISA data file IDs.
   *
   * <p>ENA RUN aliases are generated from sequencing process IDs, while MARS receipts need the run
   * accession attached to each submitted data file produced by that process.
   */
  private ReceiptAccessionsMap getRunAliasAccessionPairs(
      final List<ReceiptObject> items, final IsaJson isaJson) {
    Predicate<ReceiptObject> aliasAccessionPairValidateFn = this::aliasAccessionPairFilter;
    final Map<String, String> runAccessionMap = new HashMap<>();
    final List<List<String>> sequencingProcessDataFiles =
        getSequencingProcessDataFileIdsInSubmissionOrder(isaJson);
    int sequencingProcessIndex = 0;

    for (ReceiptObject receiptObject : Optional.ofNullable(items).orElse(new ArrayList<>())) {
      if (!aliasAccessionPairValidateFn.test(receiptObject)) {
        continue;
      }

      final String processId = getPreRandomizedAlias(receiptObject);
      List<String> dataFileIds = getDataFileIdsForSequencingProcess(isaJson, processId);
      if (dataFileIds.isEmpty() && sequencingProcessIndex < sequencingProcessDataFiles.size()) {
        dataFileIds = sequencingProcessDataFiles.get(sequencingProcessIndex);
      }

      dataFileIds.forEach(
          dataFileId -> runAccessionMap.put(dataFileId, receiptObject.getAccession()));
      sequencingProcessIndex++;
    }

    return new ReceiptAccessionsMap() {
      {
        isaItemName = DataFile.Fields.id;
        this.accessionMap = new HashMap<>(runAccessionMap);
      }
    };
  }

  /**
   * Resolves all ISA data files produced by a specific sequencing process.
   */
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
                                        IsaJsonGraphLookup.findDataFilesFromProcessOutputs(
                                                process, assay.getDataFiles())
                                            .stream()
                                            .map(DataFile::getId)
                                            .forEach(dataFileIds::add))));

    return dataFileIds;
  }

  /**
   * Records sequencing-process output data files in the same order runs are submitted to ENA.
   *
   * <p>This gives receipt conversion a fallback when an ENA run alias cannot be matched back to a
   * sequencing process ID but receipt objects still arrive in submission order.
   */
  private List<List<String>> getSequencingProcessDataFileIdsInSubmissionOrder(
      final IsaJson isaJson) {
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

  /**
   * Adds one output data-file group for each sequencing process in an assay.
   */
  private void addAssaySequencingProcessOutputs(
      final List<List<String>> sequencingProcessDataFiles,
      final Set<String> processedSequencingProcesses,
      final Assay assay) {
    if (assay.getDataFiles() == null || assay.getProcessSequence() == null) {
      return;
    }

    for (final DataFile dataFile : assay.getDataFiles()) {
      final ProcessSequence sequencingProcess =
          IsaJsonGraphLookup.findProcessByOutputId(assay.getProcessSequence(), dataFile.getId());
      if (sequencingProcess == null || !processedSequencingProcesses.add(sequencingProcess.getId())) {
        continue;
      }

      final List<String> dataFileIds = new ArrayList<>();
      IsaJsonGraphLookup.findDataFilesFromProcessOutputs(
              sequencingProcess, assay.getDataFiles())
          .stream()
          .map(DataFile::getId)
          .forEach(dataFileIds::add);
      sequencingProcessDataFiles.add(dataFileIds);
    }
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

  /**
   * Removes the generated submission suffix from an ENA alias to recover the original ISA item ID.
   */
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
