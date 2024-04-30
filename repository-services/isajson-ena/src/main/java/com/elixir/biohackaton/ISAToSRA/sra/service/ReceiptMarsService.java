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
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptPath;
import com.elixir.biohackaton.ISAToSRA.sra.model.MarsReceiptWhere;
import com.elixir.biohackaton.ISAToSRA.sra.model.Messages;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.model.ReceiptObject;

@Service
public class ReceiptMarsService {

  /**
   * Converting ENA receipt to Mars data format
   * 
   * @see https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   * @param receipt Receipt from ENA
   * @param isaJson Requested ISA-Json
   * @return
   */
  public MarsReceipt convertReceiptToMars(Receipt receipt, IsaJson isaJson) {
    Messages messages = receipt.getMessages();
    List<String> errors = Optional.ofNullable(messages.getErrorMessages()).orElse(new ArrayList<>());
    List<String> info = Optional.ofNullable(messages.getInfoMessages()).orElse(new ArrayList<>());
    return MarsReceipt.builder()
        .targetRepository("ena.embl") // https://registry.identifiers.org/registry/ena.embl
        .accessions(getMarsAccessions(receipt, isaJson, errors))
        .errors(errors.toArray(String[]::new))
        .info(info.toArray(String[]::new))
        .build();
  }

  private MarsReceiptAccession[] getMarsAccessions(Receipt receipt, IsaJson isaJson, List<String> errors) {
    List<MarsReceiptAccession> accessions = new ArrayList<>();
    if (receipt.isSuccess()) {
      Optional.ofNullable(isaJson.investigation.studies).orElse(new ArrayList<>()).forEach(study -> {
        setStudyMarsAccession(accessions, errors, receipt, study);
        Optional.ofNullable(study.materials.samples).orElse(new ArrayList<>()).forEach(sample -> {
          setSampleMarsAccession(accessions, errors, receipt, study.title, sample);
        });
        Optional.ofNullable(study.assays).orElse(new ArrayList<>()).forEach(assay -> {
          Optional.ofNullable(assay.materials.otherMaterials).orElse(new ArrayList<>()).forEach(otherMaterial -> {
            setExperimentMarsAccession(accessions, errors, receipt, study.title, assay.id, otherMaterial);
          });
          Optional.ofNullable(assay.dataFiles).orElse(new ArrayList<>()).forEach(dataFile -> {
            setRunMarsAccession(accessions, errors, receipt, study.title, assay.id, dataFile);
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
      List<MarsReceiptAccession> accessions,
      List<String> errors,
      Receipt receipt,
      Study study) {
    List<ReceiptObject> studies = Optional.ofNullable(receipt.getStudies()).orElse(receipt.getProjects());
    Optional<ReceiptObject> studyReceipt = Optional.ofNullable(studies)
        .orElse(new ArrayList<>())
        .stream()
        .filter(s -> s.getAlias().equals(study.title))
        .findAny();
    if (studyReceipt.isEmpty()) {
      errors.add(String.format("Cannot find a study with the alias %s in the ENA receipt", study.title));
      return;
    }
    accessions.add(getStudyMarsAccession(study.title, studyReceipt.get()));
  }

  private void setSampleMarsAccession(List<MarsReceiptAccession> accessions,
      List<String> errors,
      Receipt receipt,
      String studyTitle,
      Sample sample) {
    Optional<ReceiptObject> sampleReceipt = Optional.ofNullable(receipt.getSamples())
        .orElse(new ArrayList<>())
        .stream()
        .filter(s -> s.getAlias().equals(sample.id))
        .findAny();
    if (sampleReceipt.isEmpty()) {
      errors.add(String.format("Cannot find a sample with the alias %s in the ENA receipt", sample.id));
      return;
    }
    accessions.add(getSampleMarsAccession(studyTitle, sample.id, sampleReceipt.get()));
  }

  private void setExperimentMarsAccession(List<MarsReceiptAccession> accessions,
      List<String> errors,
      Receipt receipt,
      String studyTitle,
      String assayId,
      OtherMaterial otherMaterial) {
    Optional<ReceiptObject> experimentReceipt = Optional.ofNullable(receipt.getExperiments())
        .orElse(new ArrayList<>())
        .stream()
        .filter(s -> s.getAlias().equals(otherMaterial.id))
        .findAny();
    if (experimentReceipt.isEmpty()) {
      errors.add(String.format("Cannot find an experiment with the alias %s in the ENA receipt", otherMaterial.id));
      return;
    }
    accessions.add(getExperimentMarsAccession(studyTitle, assayId, otherMaterial.id, experimentReceipt.get()));
  }

  private void setRunMarsAccession(List<MarsReceiptAccession> accessions,
      List<String> errors,
      Receipt receipt,
      String studyTitle,
      String assayId,
      DataFile dataFile) {
    Optional<ReceiptObject> runReceipt = Optional.ofNullable(receipt.getRuns())
        .orElse(new ArrayList<>())
        .stream()
        .filter(s -> s.getAlias().equals(dataFile.id))
        .findAny();
    if (runReceipt.isEmpty()) {
      errors.add(String.format("Cannot find a run with the alias %s in the ENA receipt", dataFile.id));
      return;
    }
    accessions.add(getRunMarsAccession(studyTitle, assayId, dataFile.id, runReceipt.get()));
  }
  //
  // ---------------------------------
  // | Making Mars accession objects |
  // ---------------------------------

  private MarsReceiptAccession getStudyMarsAccession(String title, ReceiptObject studyReceipt) {
    return MarsReceiptAccession.builder()
        .path(new MarsReceiptPath[] {
            MarsReceiptPath.builder().key("investigation").build(),
            MarsReceiptPath.builder().key("studies")
                .where(MarsReceiptWhere.builder().key("title").value(title).build()).build()
        })
        .value(studyReceipt.getAccession())
        .build();
  }

  private MarsReceiptAccession getSampleMarsAccession(String studyTitle, String sampleId, ReceiptObject sampleReceipt) {
    return MarsReceiptAccession.builder()
        .path(new MarsReceiptPath[] {
            MarsReceiptPath.builder().key("investigation").build(),
            MarsReceiptPath.builder().key("studies")
                .where(MarsReceiptWhere.builder().key("title").value(studyTitle).build()).build(),
            MarsReceiptPath.builder().key("materials").build(),
            MarsReceiptPath.builder().key("samples")
                .where(MarsReceiptWhere.builder().key("@id").value(sampleId).build()).build()
        })
        .value(sampleReceipt.getAccession())
        .build();
  }

  private MarsReceiptAccession getExperimentMarsAccession(
      String studyTitle,
      String assayId,
      String otherMaterialId,
      ReceiptObject experimentReceipt) {
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
        .value(experimentReceipt.getAccession())
        .build();
  }

  private MarsReceiptAccession getRunMarsAccession(
      String studyTitle,
      String assayId,
      String dataFileId,
      ReceiptObject runReceipt) {
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
        .value(runReceipt.getAccession())
        .build();
  }
}
