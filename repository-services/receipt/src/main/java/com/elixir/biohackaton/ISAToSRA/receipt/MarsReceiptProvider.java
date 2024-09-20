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

public abstract class MarsReceiptProvider {

    public MarsReceiptProvider() {
    }

    public abstract String convertMarsReceiptToJson(final MarsReceipt marsReceipt);

    /**
     * Converts target receipt to Mars data format
     *
     * @see
     *      https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
     * @param targetRepository Prefix of an item on
     *                         https://registry.identifiers.org/registry
     * @param studies          List of key-value contains a unique key and the
     *                         accession number of the item
     * @param samples          List of key-value contains a unique key and the
     *                         accession number of the item
     * @param otherMaterials   List of key-value contains a unique key and the
     *                         accession number of the item
     * @param dataFiles        List of key-value contains a unique key and the
     *                         accession number of the item
     * @param isaJson          Requested ISA-Json
     * @param info             List of info messages
     * @param errors           List of error messages
     * @return {@link MarsReceipt} Mars response data
     */
    protected MarsReceipt buildMarsReceipt(
            final String targetRepository,
            final HashMap<String, String> studies,
            final HashMap<String, String> samples,
            final HashMap<String, String> otherMaterials,
            final HashMap<String, String> dataFiles,
            final List<String> info,
            final List<String> errors,
            final IsaJson isaJson) {
        final MarsMessage marsMessage = MarsMessage.builder().build();
        final List<MarsAccession> marsAccessions = new ArrayList<>();
        setMarsReceiptErrors(errors, marsMessage);
        setMarsReceiptInfo(info, marsMessage);
        setMarsAccessions(
                studies,
                samples,
                otherMaterials,
                dataFiles,
                isaJson,
                marsMessage,
                marsAccessions);
        return MarsReceipt.builder()
                .targetRepository(targetRepository)
                .accessions(marsAccessions)
                .errors(marsMessage.errors)
                .info(marsMessage.info)
                .build();
    }

    protected void setMarsReceiptErrors(final List<String> errors, final MarsMessage marsMessage) {
        Optional.ofNullable(errors)
                .orElse(new ArrayList<>())
                .forEach(
                        error -> {
                            marsMessage.errors
                                    .add(
                                            MarsError.builder()
                                                    .message(error)
                                                    .type(MarsErrorType.INVALID_METADATA)
                                                    .build());
                        });
    }

    protected void setMarsReceiptInfo(final List<String> infoList, final MarsMessage marsMessage) {
        Optional.ofNullable(infoList)
                .orElse(new ArrayList<>())
                .forEach(
                        info -> {
                            marsMessage.info
                                    .add(
                                            MarsInfo.builder()
                                                    .message(info)
                                                    .build());
                        });
    }

    protected List<MarsAccession> setMarsAccessions(
            final HashMap<String, String> studies,
            final HashMap<String, String> samples,
            final HashMap<String, String> otherMaterials,
            final HashMap<String, String> dataFiles,
            final IsaJson isaJson,
            final MarsMessage marsMessage,
            final List<MarsAccession> accessions) {
        Optional.ofNullable(isaJson.investigation.studies)
                .orElse(new ArrayList<>())
                .forEach(
                        study -> {
                            setStudyMarsAccession(
                                    studies.get(study.title),
                                    study,
                                    marsMessage,
                                    accessions);
                            Optional.ofNullable(study.materials.samples)
                                    .orElse(new ArrayList<>())
                                    .forEach(
                                            sample -> {
                                                setSampleMarsAccession(
                                                        samples.get(sample.id),
                                                        study.title,
                                                        sample,
                                                        marsMessage,
                                                        accessions);
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
                                                                            otherMaterials.get(otherMaterial.id),
                                                                            study.title,
                                                                            assay.id,
                                                                            otherMaterial,
                                                                            marsMessage,
                                                                            accessions);
                                                                });
                                                Optional.ofNullable(assay.dataFiles)
                                                        .orElse(new ArrayList<>())
                                                        .forEach(
                                                                dataFile -> {
                                                                    setDataFileMarsAccession(
                                                                            dataFiles.get(dataFile.id),
                                                                            study.title,
                                                                            assay.id,
                                                                            dataFile,
                                                                            marsMessage,
                                                                            accessions);
                                                                });
                                            });
                        });

        return accessions;
    }

    // ---------------------------------
    // | Setting Mars accession objects |
    // ---------------------------------

    protected void setStudyMarsAccession(
            final String accession,
            final Study study,
            final MarsMessage marsMessage,
            final List<MarsAccession> marsAccessions) {
        final MarsAccession marsStudyReceipt = getStudyMarsAccession(study.title, accession);
        if (accession == null) {
            marsMessage.errors
                    .add(
                            MarsError.builder()
                                    .message(
                                            String.format(
                                                    "Cannot find a Study with the key %s in the receipt", study.title))
                                    .type(MarsErrorType.INVALID_METADATA)
                                    .path(marsStudyReceipt.path)
                                    .build());
            return;
        }
        marsAccessions.add(marsStudyReceipt);
    }

    protected void setSampleMarsAccession(
            final String accession,
            final String studyTitle,
            final Sample sample,
            final MarsMessage marsMessage,
            final List<MarsAccession> marsAccessions) {
        final MarsAccession marsSampleReceipt = getSampleMarsAccession(studyTitle, sample.id, accession);
        if (accession == null) {
            marsMessage.errors
                    .add(
                            MarsError.builder()
                                    .message(
                                            String.format(
                                                    "Cannot find a Sample with the key %s in the receipt", sample.id))
                                    .type(MarsErrorType.INVALID_METADATA)
                                    .path(marsSampleReceipt.path)
                                    .build());
            return;
        }
        marsAccessions.add(marsSampleReceipt);
    }

    protected void setOtherMaterialMarsAccession(
            final String accession,
            final String studyTitle,
            final String assayId,
            final OtherMaterial otherMaterial,
            final MarsMessage marsMessage,
            final List<MarsAccession> marsAccessions) {
        final MarsAccession marsExperimentReceipt = getExperimentMarsAccession(studyTitle, assayId, otherMaterial.id,
                accession);
        if (accession == null) {
            marsMessage.errors
                    .add(
                            MarsError.builder()
                                    .message(
                                            String.format(
                                                    "Cannot find an Experiment with the key %s in the receipt",
                                                    otherMaterial.id))
                                    .type(MarsErrorType.INVALID_METADATA)
                                    .path(marsExperimentReceipt.path)
                                    .build());
            return;
        }
        marsAccessions.add(marsExperimentReceipt);
    }

    protected void setDataFileMarsAccession(
            final String accession,
            final String studyTitle,
            final String assayId,
            final DataFile dataFile,
            final MarsMessage marsMessage,
            final List<MarsAccession> marsAccessions) {
        final MarsAccession marsRunReceipt = getRunMarsAccession(studyTitle, assayId, dataFile.id, accession);
        if (accession == null) {
            marsMessage.errors
                    .add(
                            MarsError.builder()
                                    .message(
                                            String.format(
                                                    "Cannot find a Run with the key %s in the receipt", dataFile.id))
                                    .type(MarsErrorType.INVALID_METADATA)
                                    .path(marsRunReceipt.path)
                                    .build());
            return;
        }
        marsAccessions.add(marsRunReceipt);
    }

    // ---------------------------------
    // | Making Mars accession objects |
    // ---------------------------------

    protected MarsAccession getStudyMarsAccession(
            final String title,
            final String accession) {
        return MarsAccession.builder()
                .path(
                        new ArrayList<MarsPath>() {
                            {
                                add(MarsPath.builder().key("investigation").build());
                                add(MarsPath.builder()
                                        .key("studies")
                                        .where(MarsWhere.builder().key("title").value(title).build())
                                        .build());
                            }
                        })
                .value(accession)
                .build();
    }

    protected MarsAccession getSampleMarsAccession(
            final String studyTitle,
            final String sampleId,
            final String accession) {
        return MarsAccession.builder()
                .path(
                        new ArrayList<MarsPath>() {
                            {
                                add(MarsPath.builder().key("investigation").build());
                                add(MarsPath.builder()
                                        .key("studies")
                                        .where(MarsWhere.builder().key("title").value(studyTitle).build())
                                        .build());
                                add(MarsPath.builder().key("materials").build());
                                add(MarsPath.builder()
                                        .key("samples")
                                        .where(MarsWhere.builder().key("@id").value(sampleId).build())
                                        .build());
                            }
                        })
                .value(accession)
                .build();
    }

    protected MarsAccession getExperimentMarsAccession(
            final String studyTitle,
            final String assayId,
            final String otherMaterialId,
            final String accession) {
        return MarsAccession.builder()
                .path(
                        new ArrayList<MarsPath>() {
                            {
                                add(MarsPath.builder().key("investigation").build());
                                add(MarsPath.builder()
                                        .key("studies")
                                        .where(MarsWhere.builder().key("title").value(studyTitle).build())
                                        .build());
                                add(MarsPath.builder()
                                        .key("assays")
                                        .where(MarsWhere.builder().key("@id").value(assayId).build())
                                        .build());
                                add(MarsPath.builder().key("materials").build());
                                add(MarsPath.builder()
                                        .key("otherMaterials")
                                        .where(MarsWhere.builder().key("@id").value(otherMaterialId).build())
                                        .build());
                            }
                        })
                .value(accession)
                .build();
    }

    protected MarsAccession getRunMarsAccession(
            final String studyTitle,
            final String assayId,
            final String dataFileId,
            final String accession) {
        return MarsAccession.builder()
                .path(
                        new ArrayList<MarsPath>() {
                            {
                                add(MarsPath.builder().key("investigation").build());
                                add(MarsPath.builder()
                                        .key("studies")
                                        .where(MarsWhere.builder().key("title").value(studyTitle).build())
                                        .build());
                                add(MarsPath.builder()
                                        .key("assays")
                                        .where(MarsWhere.builder().key("@id").value(assayId).build())
                                        .build());
                                add(MarsPath.builder()
                                        .key("dataFiles")
                                        .where(MarsWhere.builder().key("@id").value(dataFileId).build())
                                        .build());
                            }
                        })
                .value(accession)
                .build();
    }
}
