/** Elixir BioHackathon 2023 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.elixir.biohackaton.ISAToSRA.model.DataFile;
import com.elixir.biohackaton.ISAToSRA.model.IsaJson;
import com.elixir.biohackaton.ISAToSRA.model.OtherMaterial;
import com.elixir.biohackaton.ISAToSRA.model.Sample;
import com.elixir.biohackaton.ISAToSRA.model.Study;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptAccession;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptError;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptErrorType;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptInfo;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptMessage;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptPath;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptWhere;
import com.elixir.biohackaton.ISAToSRA.sra.model.Messages;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.ReceiptObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Service
public class ReceiptMarsService {
  private final ObjectMapper jsonMapper = new ObjectMapper();

  private void setupJsonMapper() {
    jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  public ReceiptMarsService() {
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
   * @see https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   * @param receipt {@link Receipt} Receipt from ENA
   * @param isaJson {@link IsaJson} Requested ISA-Json
   * @return {@link MarsReceipt} Mars response data
   */
  public MarsReceipt convertReceiptToMars(final Receipt receipt, final IsaJson isaJson) {
    final MarsReceiptMessage marsMessage = MarsReceiptMessage.builder().build();
    final Messages messages = receipt.getMessages();
    setMarsReceiptErrors(messages, marsMessage);
    setMarsReceiptInfo(messages, marsMessage);
    return MarsReceipt.builder()
        .targetRepository("ena.embl") // https://registry.identifiers.org/registry/ena.embl
        .accessions(getMarsAccessions(receipt, isaJson, marsMessage))
        .errors(marsMessage.getErrors().toArray(MarsReceiptError[]::new))
        .info(marsMessage.getInfo().toArray(MarsReceiptInfo[]::new))
        .build();
  }

  private void setMarsReceiptErrors(final Messages messages, final MarsReceiptMessage marsMessage) {
    Optional.ofNullable(messages.getErrorMessages()).orElse(new ArrayList<>()).forEach(error -> {
      marsMessage.getErrors().add(MarsReceiptError.builder()
          .message(error)
          .type(MarsReceiptErrorType.INVALID_METADATA)
          .build());
    });
  }

  private void setMarsReceiptInfo(final Messages messages, final MarsReceiptMessage marsMessage) {
    Optional.ofNullable(messages.getInfoMessages()).orElse(new ArrayList<>()).forEach(info -> {
      marsMessage.getInfo().add(MarsReceiptInfo.builder()
          .message(info)
          .build());
    });
  }

  private MarsReceiptAccession[] getMarsAccessions(
      final Receipt receipt,
      final IsaJson isaJson,
      final MarsReceiptMessage marsMessage) {
    final List<MarsReceiptAccession> accessions = new ArrayList<>();
    if (receipt.isSuccess()) {
      Optional.ofNullable(isaJson.investigation.studies).orElse(new ArrayList<>()).forEach(study -> {
        setStudyMarsAccession(accessions, receipt, study, marsMessage);
        Optional.ofNullable(study.materials.samples).orElse(new ArrayList<>()).forEach(sample -> {
          setSampleMarsAccession(accessions, receipt, study.title, sample, marsMessage);
        });
        Optional.ofNullable(study.assays).orElse(new ArrayList<>()).forEach(assay -> {
          Optional.ofNullable(assay.materials.otherMaterials).orElse(new ArrayList<>()).forEach(otherMaterial -> {
            setExperimentMarsAccession(accessions, receipt, study.title, assay.id, otherMaterial, marsMessage);
          });
          Optional.ofNullable(assay.dataFiles).orElse(new ArrayList<>()).forEach(dataFile -> {
            setRunMarsAccession(accessions, receipt, study.title, assay.id, dataFile, marsMessage);
          });
        });
      });
    }

    return accessions.toArray(MarsReceiptAccession[]::new);
  }

  //
  // ---------------------------------
  // | Setting Mars accession objects |
  // ---------------------------------

  private void setStudyMarsAccession(
      final List<MarsReceiptAccession> accessions,
      final Receipt receipt,
      final Study study,
      final MarsReceiptMessage marsMessage) {
    final List<ReceiptObject> studies = Optional.ofNullable(receipt.getStudies()).orElse(receipt.getProjects());
    final Optional<ReceiptObject> studyReceipt = Optional.ofNullable(studies)
        .orElse(new ArrayList<>())
        .stream()
        .filter(s -> s.getAlias().equals(study.title))
        .findAny();
    final MarsReceiptAccession marsStudyReceipt = getStudyMarsAccession(study.title, studyReceipt);
    if (studyReceipt.isEmpty()) {
      marsMessage.getErrors().add(MarsReceiptError.builder()
          .message(String.format("Cannot find a Study with the alias %s in the ENA receipt", study.title))
          .type(MarsReceiptErrorType.INVALID_METADATA)
          .path(marsStudyReceipt.getPath())
          .build());
      return;
    }
    accessions.add(marsStudyReceipt);
  }

  private void setSampleMarsAccession(
      final List<MarsReceiptAccession> accessions,
      final Receipt receipt,
      final String studyTitle,
      final Sample sample,
      final MarsReceiptMessage marsMessage) {
    final Optional<ReceiptObject> sampleReceipt = Optional.ofNullable(receipt.getSamples())
        .orElse(new ArrayList<>())
        .stream()
        .filter(s -> s.getAlias().equals(sample.id))
        .findAny();
    final MarsReceiptAccession marsSampleReceipt = getSampleMarsAccession(studyTitle, sample.id, sampleReceipt);
    if (sampleReceipt.isEmpty()) {
      marsMessage.getErrors().add(MarsReceiptError.builder()
          .message(String.format("Cannot find a Sample with the alias %s in the ENA receipt", sample.id))
          .type(MarsReceiptErrorType.INVALID_METADATA)
          .path(marsSampleReceipt.getPath())
          .build());
      return;
    }
    accessions.add(marsSampleReceipt);
  }

  private void setExperimentMarsAccession(
      final List<MarsReceiptAccession> accessions,
      final Receipt receipt,
      final String studyTitle,
      final String assayId,
      final OtherMaterial otherMaterial,
      final MarsReceiptMessage marsMessage) {
    final Optional<ReceiptObject> experimentReceipt = Optional.ofNullable(receipt.getExperiments())
        .orElse(new ArrayList<>())
        .stream()
        .filter(s -> s.getAlias().equals(otherMaterial.id))
        .findAny();
    final MarsReceiptAccession marsExperimentReceipt = getExperimentMarsAccession(studyTitle, assayId, otherMaterial.id,
        experimentReceipt);
    if (experimentReceipt.isEmpty()) {
      marsMessage.getErrors().add(MarsReceiptError.builder()
          .message(
              String.format("Cannot find an Experiment with the alias %s in the ENA receipt", otherMaterial.id))
          .type(MarsReceiptErrorType.INVALID_METADATA)
          .path(marsExperimentReceipt.getPath())
          .build());
      return;
    }
    accessions.add(marsExperimentReceipt);
  }

  private void setRunMarsAccession(
      final List<MarsReceiptAccession> accessions,
      final Receipt receipt,
      final String studyTitle,
      final String assayId,
      final DataFile dataFile,
      final MarsReceiptMessage marsMessage) {
    final Optional<ReceiptObject> runReceipt = Optional.ofNullable(receipt.getRuns())
        .orElse(new ArrayList<>())
        .stream()
        .filter(s -> s.getAlias().equals(dataFile.id))
        .findAny();
    final MarsReceiptAccession marsRunReceipt = getRunMarsAccession(studyTitle, assayId, dataFile.id, runReceipt);
    if (runReceipt.isEmpty()) {
      marsMessage.getErrors().add(MarsReceiptError.builder()
          .message(String.format("Cannot find a Run with the alias %s in the ENA receipt", dataFile.id))
          .type(MarsReceiptErrorType.INVALID_METADATA)
          .path(marsRunReceipt.getPath())
          .build());
      return;
    }
    accessions.add(marsRunReceipt);
  }

  //
  // ---------------------------------
  // | Making Mars accession objects |
  // ---------------------------------

  private MarsReceiptAccession getStudyMarsAccession(
      final String title,
      final Optional<ReceiptObject> studyReceipt) {
    return MarsReceiptAccession.builder()
        .path(new MarsReceiptPath[] {
            MarsReceiptPath.builder().key("investigation").build(),
            MarsReceiptPath.builder().key("studies")
                .where(MarsReceiptWhere.builder().key("title").value(title).build()).build()
        })
        .value(studyReceipt.isPresent() ? studyReceipt.get().getAccession() : null)
        .build();
  }

  private MarsReceiptAccession getSampleMarsAccession(
      final String studyTitle,
      final String sampleId,
      final Optional<ReceiptObject> sampleReceipt) {
    return MarsReceiptAccession.builder()
        .path(new MarsReceiptPath[] {
            MarsReceiptPath.builder().key("investigation").build(),
            MarsReceiptPath.builder().key("studies")
                .where(MarsReceiptWhere.builder().key("title").value(studyTitle).build()).build(),
            MarsReceiptPath.builder().key("materials").build(),
            MarsReceiptPath.builder().key("samples")
                .where(MarsReceiptWhere.builder().key("@id").value(sampleId).build()).build()
        })
        .value(sampleReceipt.isPresent() ? sampleReceipt.get().getAccession() : null)
        .build();
  }

  private MarsReceiptAccession getExperimentMarsAccession(
      final String studyTitle,
      final String assayId,
      final String otherMaterialId,
      final Optional<ReceiptObject> experimentReceipt) {
    return MarsReceiptAccession.builder()
        .path(new MarsReceiptPath[] {
            MarsReceiptPath.builder().key("investigation").build(),
            MarsReceiptPath.builder().key("studies")
                .where(MarsReceiptWhere.builder().key("title").value(studyTitle).build()).build(),
            MarsReceiptPath.builder().key("assays")
                .where(MarsReceiptWhere.builder().key("@id").value(assayId).build()).build(),
            MarsReceiptPath.builder().key("materials").build(),
            MarsReceiptPath.builder().key("otherMaterials")
                .where(MarsReceiptWhere.builder().key("@id").value(otherMaterialId).build()).build(),

        })
        .value(experimentReceipt.isPresent() ? experimentReceipt.get().getAccession() : null)
        .build();
  }

  private MarsReceiptAccession getRunMarsAccession(
      final String studyTitle,
      final String assayId,
      final String dataFileId,
      final Optional<ReceiptObject> runReceipt) {
    return MarsReceiptAccession.builder()
        .path(new MarsReceiptPath[] {
            MarsReceiptPath.builder().key("investigation").build(),
            MarsReceiptPath.builder().key("studies")
                .where(MarsReceiptWhere.builder().key("title").value(studyTitle).build()).build(),
            MarsReceiptPath.builder().key("assays")
                .where(MarsReceiptWhere.builder().key("@id").value(assayId).build()).build(),
            MarsReceiptPath.builder().key("dataFiles")
                .where(MarsReceiptWhere.builder().key("@id").value(dataFileId).build()).build(),

        })
        .value(runReceipt.isPresent() ? runReceipt.get().getAccession() : null)
        .build();
  }
}
