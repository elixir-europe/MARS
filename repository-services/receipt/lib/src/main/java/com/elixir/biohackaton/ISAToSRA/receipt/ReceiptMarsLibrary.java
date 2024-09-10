/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.receipt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.DataFile;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.IsaJson;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.OtherMaterial;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Sample;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsAccession;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsError;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsErrorType;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsInfo;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsMessage;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsPath;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsReceipt;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsWhere;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ReceiptMarsLibrary {
  private final ObjectMapper jsonMapper = new ObjectMapper();

  private void setupJsonMapper() {
    jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  public ReceiptMarsLibrary() {
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
   * Converts target receipt to Mars data format
   *
   * @see
   *      https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   * @param isaJson {@link IsaJson} Requested ISA-Json
   * @return {@link MarsReceipt} Mars response data
   */
  public MarsReceipt convertReceiptToMars(
      final String targetRepository,
      final HashMap<String, String> studies,
      final HashMap<String, String> samples,
      final HashMap<String, String> otherMaterials,
      final HashMap<String, String> dataFiles,
      final List<String> info,
      final List<String> errors,
      final IsaJson isaJson) {
    final MarsMessage marsMessage = MarsMessage.builder().build();
    setMarsReceiptErrors(errors, marsMessage);
    setMarsReceiptInfo(info, marsMessage);
    return MarsReceipt.builder()
        .targetRepository(targetRepository)
        .accessions(getMarsAccessions(
            studies,
            samples,
            otherMaterials,
            dataFiles,
            isaJson, marsMessage))
        .errors(marsMessage.getErrors().toArray(MarsError[]::new))
        .info(marsMessage.getInfo().toArray(MarsInfo[]::new))
        .build();
  }

  private void setMarsReceiptErrors(final List<String> errors, final MarsMessage marsMessage) {
    Optional.ofNullable(errors)
        .orElse(new ArrayList<>())
        .forEach(
            error -> {
              marsMessage
                  .getErrors()
                  .add(
                      MarsError.builder()
                          .message(error)
                          .type(MarsErrorType.INVALID_METADATA)
                          .build());
            });
  }

  private void setMarsReceiptInfo(final List<String> infoList, final MarsMessage marsMessage) {
    Optional.ofNullable(infoList)
        .orElse(new ArrayList<>())
        .forEach(
            info -> {
              marsMessage.getInfo().add(MarsInfo.builder().message(info).build());
            });
  }

  private MarsAccession[] getMarsAccessions(
      final HashMap<String, String> studies,
      final HashMap<String, String> samples,
      final HashMap<String, String> otherMaterials,
      final HashMap<String, String> dataFiles,
      final IsaJson isaJson,
      final MarsMessage marsMessage) {
    final List<MarsAccession> accessions = new ArrayList<>();
    Optional.ofNullable(isaJson.investigation.studies)
        .orElse(new ArrayList<>())
        .forEach(
            study -> {
              setStudyMarsAccession(accessions, studies, study, marsMessage);
              Optional.ofNullable(study.materials.samples)
                  .orElse(new ArrayList<>())
                  .forEach(
                      sample -> {
                        setSampleMarsAccession(
                            accessions, samples, study.title, sample, marsMessage);
                      });
              Optional.ofNullable(study.assays)
                  .orElse(new ArrayList<>())
                  .forEach(
                      assay -> {
                        Optional.ofNullable(assay.materials.otherMaterials)
                            .orElse(new ArrayList<>())
                            .forEach(
                                otherMaterial -> {
                                  setOtherMaterialMarsAccession(
                                      accessions,
                                      otherMaterials,
                                      study.title,
                                      assay.id,
                                      otherMaterial,
                                      marsMessage);
                                });
                        Optional.ofNullable(assay.dataFiles)
                            .orElse(new ArrayList<>())
                            .forEach(
                                dataFile -> {
                                  setDataFileMarsAccession(
                                      accessions,
                                      dataFiles,
                                      study.title,
                                      assay.id,
                                      dataFile,
                                      marsMessage);
                                });
                      });
            });

    return accessions.toArray(MarsAccession[]::new);
  }

  //
  // ---------------------------------
  // | Setting Mars accession objects |
  // ---------------------------------

  private void setStudyMarsAccession(
      final List<MarsAccession> marsAccessions,
      final HashMap<String, String> studies,
      final Study study,
      final MarsMessage marsMessage) {
    final String accession = studies.get(study.title);
    final MarsAccession marsStudyReceipt = getStudyMarsAccession(study.title, accession);
    if (accession == null) {
      marsMessage
          .getErrors()
          .add(
              MarsError.builder()
                  .message(
                      String.format(
                          "Cannot find a Study with the key %s in the receipt", study.title))
                  .type(MarsErrorType.INVALID_METADATA)
                  .path(marsStudyReceipt.getPath())
                  .build());
      return;
    }
    marsAccessions.add(marsStudyReceipt);
  }

  private void setSampleMarsAccession(
      final List<MarsAccession> marsAccessions,
      final HashMap<String, String> samples,
      final String studyTitle,
      final Sample sample,
      final MarsMessage marsMessage) {
    final String accession = samples.get(sample.id);
    final MarsAccession marsSampleReceipt = getSampleMarsAccession(studyTitle, sample.id, accession);
    if (accession == null) {
      marsMessage
          .getErrors()
          .add(
              MarsError.builder()
                  .message(
                      String.format(
                          "Cannot find a Sample with the key %s in the receipt", sample.id))
                  .type(MarsErrorType.INVALID_METADATA)
                  .path(marsSampleReceipt.getPath())
                  .build());
      return;
    }
    marsAccessions.add(marsSampleReceipt);
  }

  private void setOtherMaterialMarsAccession(
      final List<MarsAccession> marsAccessions,
      final HashMap<String, String> otherMaterials,
      final String studyTitle,
      final String assayId,
      final OtherMaterial otherMaterial,
      final MarsMessage marsMessage) {
    final String accession = otherMaterials.get(otherMaterial.id);
    final MarsAccession marsExperimentReceipt = getExperimentMarsAccession(studyTitle, assayId, otherMaterial.id,
        accession);
    if (accession == null) {
      marsMessage
          .getErrors()
          .add(
              MarsError.builder()
                  .message(
                      String.format(
                          "Cannot find an Experiment with the key %s in the receipt",
                          otherMaterial.id))
                  .type(MarsErrorType.INVALID_METADATA)
                  .path(marsExperimentReceipt.getPath())
                  .build());
      return;
    }
    marsAccessions.add(marsExperimentReceipt);
  }

  private void setDataFileMarsAccession(
      final List<MarsAccession> marsAccessions,
      final HashMap<String, String> dataFiles,
      final String studyTitle,
      final String assayId,
      final DataFile dataFile,
      final MarsMessage marsMessage) {
    final String accession = dataFiles.get(dataFile.id);
    final MarsAccession marsRunReceipt = getRunMarsAccession(studyTitle, assayId, dataFile.id, accession);
    if (accession == null) {
      marsMessage
          .getErrors()
          .add(
              MarsError.builder()
                  .message(
                      String.format(
                          "Cannot find a Run with the key %s in the receipt", dataFile.id))
                  .type(MarsErrorType.INVALID_METADATA)
                  .path(marsRunReceipt.getPath())
                  .build());
      return;
    }
    marsAccessions.add(marsRunReceipt);
  }

  //
  // ---------------------------------
  // | Making Mars accession objects |
  // ---------------------------------

  private MarsAccession getStudyMarsAccession(final String title, final String accession) {
    return MarsAccession.builder()
        .path(
            new MarsPath[] {
                MarsPath.builder().key("investigation").build(),
                MarsPath.builder()
                    .key("studies")
                    .where(MarsWhere.builder().key("title").value(title).build())
                    .build()
            })
        .value(accession)
        .build();
  }

  private MarsAccession getSampleMarsAccession(final String studyTitle, final String sampleId, final String accession) {
    return MarsAccession.builder()
        .path(
            new MarsPath[] {
                MarsPath.builder().key("investigation").build(),
                MarsPath.builder()
                    .key("studies")
                    .where(MarsWhere.builder().key("title").value(studyTitle).build())
                    .build(),
                MarsPath.builder().key("materials").build(),
                MarsPath.builder()
                    .key("samples")
                    .where(MarsWhere.builder().key("@id").value(sampleId).build())
                    .build()
            })
        .value(accession)
        .build();
  }

  private MarsAccession getExperimentMarsAccession(
      final String studyTitle,
      final String assayId,
      final String otherMaterialId,
      final String accession) {
    return MarsAccession.builder()
        .path(
            new MarsPath[] {
                MarsPath.builder().key("investigation").build(),
                MarsPath.builder()
                    .key("studies")
                    .where(MarsWhere.builder().key("title").value(studyTitle).build())
                    .build(),
                MarsPath.builder()
                    .key("assays")
                    .where(MarsWhere.builder().key("@id").value(assayId).build())
                    .build(),
                MarsPath.builder().key("materials").build(),
                MarsPath.builder()
                    .key("otherMaterials")
                    .where(MarsWhere.builder().key("@id").value(otherMaterialId).build())
                    .build(),
            })
        .value(accession)
        .build();
  }

  private MarsAccession getRunMarsAccession(
      final String studyTitle,
      final String assayId,
      final String dataFileId,
      final String accession) {
    return MarsAccession.builder()
        .path(
            new MarsPath[] {
                MarsPath.builder().key("investigation").build(),
                MarsPath.builder()
                    .key("studies")
                    .where(MarsWhere.builder().key("title").value(studyTitle).build())
                    .build(),
                MarsPath.builder()
                    .key("assays")
                    .where(MarsWhere.builder().key("@id").value(assayId).build())
                    .build(),
                MarsPath.builder()
                    .key("dataFiles")
                    .where(MarsWhere.builder().key("@id").value(dataFileId).build())
                    .build(),
            })
        .value(accession)
        .build();
  }
}
