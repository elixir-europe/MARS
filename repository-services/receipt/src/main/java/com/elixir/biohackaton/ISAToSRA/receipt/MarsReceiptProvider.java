package com.elixir.biohackaton.ISAToSRA.receipt;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.IsaJson;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsAccession;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsError;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsErrorType;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsInfo;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsMessage;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsPath;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsReceipt;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.MarsWhere;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class MarsReceiptProvider {

  private MarsReceipt.MarsReceiptBuilder marsReceiptBuilder;
  private MarsMessage marsMessage;
  private List<MarsAccession> marsAccessions;
  private final String targetRepository;

  private class ReceiptAccessionMap {
    public String keyName;
    public String isaKeyName;
    public String keyValue;
    public String accession;
  }

  public MarsReceiptProvider(final String targetRepository) {
    this.targetRepository = targetRepository;
    resetMarsReceipt();
  }

  public abstract String convertMarsReceiptToJson();

  public void resetMarsReceipt() {
    marsMessage = MarsMessage.builder().build();
    marsAccessions = new ArrayList<>();
    marsReceiptBuilder = MarsReceipt.builder()
        .targetRepository(targetRepository)
        .accessions(marsAccessions)
        .errors(marsMessage.errors)
        .info(marsMessage.info);
  }

  public MarsReceipt getMarsReceipt() {
    return marsReceiptBuilder.build();
  }

  /**
   * Converts target receipt to Mars data format
   *
   * @see
   *      https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
   * @param isaJson Requested ISA-Json
   * @param info    List of info messages
   * @param errors  List of error messages
   */
  protected void buildMarsReceipt(
      final ReceiptAccessionsMap studiesAccessionsMap,
      final ReceiptAccessionsMap samplesAccessionsMap,
      final ReceiptAccessionsMap sourcesAccessionsMap,
      final ReceiptAccessionsMap otherMaterialsAccessionsMap,
      final ReceiptAccessionsMap dataFilesAccessionsMap,
      final List<String> info,
      final List<String> errors,
      final IsaJson isaJson) {
    final String[] errorArray = Optional.ofNullable(errors).orElse(new ArrayList<>()).toArray(String[]::new);
    final String[] infoArray = Optional.ofNullable(info).orElse(new ArrayList<>()).toArray(String[]::new);

    setMarsReceiptErrors(MarsErrorType.INVALID_METADATA, errorArray);
    setMarsReceiptInfo(infoArray);
    setMarsAccessions(
        studiesAccessionsMap,
        samplesAccessionsMap,
        sourcesAccessionsMap,
        otherMaterialsAccessionsMap,
        dataFilesAccessionsMap,
        isaJson);
    marsReceiptBuilder
        .accessions(marsAccessions)
        .errors(marsMessage.errors)
        .info(marsMessage.info);
  }

  protected void setMarsReceiptErrors(MarsErrorType type, final String... errors) {
    for (String error : Optional.ofNullable(errors).orElse(new String[0])) {
      marsMessage.errors
          .add(
              MarsError.builder()
                  .message(error)
                  .type(type)
                  .build());
    }
  }

  protected void setMarsReceiptInfo(final String... info) {
    for (String infoItem : Optional.ofNullable(info).orElse(new String[0])) {
      marsMessage.info
          .add(
              MarsInfo.builder()
                  .message(infoItem)
                  .build());
    }
  }

  protected void setMarsAccessions(
      final ReceiptAccessionsMap studiesAccessionsMap,
      final ReceiptAccessionsMap samplesAccessionsMap,
      final ReceiptAccessionsMap sourcesAccessionsMap,
      final ReceiptAccessionsMap otherMaterialsAccessionsMap,
      final ReceiptAccessionsMap dataFilesAccessionsMap,
      final IsaJson isaJson) {
    Optional.ofNullable(isaJson.investigation.studies)
        .orElse(new ArrayList<>())
        .forEach(
            study -> {
              if (studiesAccessionsMap != null) {
                ReceiptAccessionMap studyAccessionMap = getAccessionMapEntry(
                    studiesAccessionsMap, study, marsMessage);
                if (studyAccessionMap.accession != null) {
                  marsAccessions.add(getStudyMarsAccession(studyAccessionMap));
                }
                if (samplesAccessionsMap != null && study.materials != null) {
                  Optional.ofNullable(study.materials.samples)
                      .orElse(new ArrayList<>())
                      .forEach(
                          sample -> {
                            ReceiptAccessionMap samplAccessionMap = getAccessionMapEntry(
                                samplesAccessionsMap, sample, marsMessage);
                            if (samplAccessionMap.accession != null) {
                              marsAccessions.add(getSampleMarsAccession(studyAccessionMap, samplAccessionMap));
                            }
                          });
                }
                if (sourcesAccessionsMap != null && study.materials != null) {
                  Optional.ofNullable(study.materials.sources)
                      .orElse(new ArrayList<>())
                      .forEach(
                          source -> {
                            ReceiptAccessionMap sourceAccessionMap = getAccessionMapEntry(
                                sourcesAccessionsMap, source, marsMessage);
                            if (sourceAccessionMap.accession != null) {
                              marsAccessions.add(getSourceMarsAccession(studyAccessionMap, sourceAccessionMap));
                            }
                          });
                }
                if (otherMaterialsAccessionsMap != null || dataFilesAccessionsMap != null) {
                  Optional.ofNullable(study.assays)
                      .orElse(new ArrayList<>())
                      .forEach(
                          assay -> {
                            if (otherMaterialsAccessionsMap != null && assay.materials != null) {
                              Optional.ofNullable(assay.materials.otherMaterials)
                                  .orElse(new ArrayList<>())
                                  .forEach(
                                      otherMaterial -> {
                                        ReceiptAccessionMap otherMaterialAccessionMap = getAccessionMapEntry(
                                            otherMaterialsAccessionsMap, otherMaterial, marsMessage);
                                        if (otherMaterialAccessionMap.accession != null) {
                                          marsAccessions.add(getOtherMaterialMarsAccession(
                                              studyAccessionMap,
                                              assay.id,
                                              otherMaterialAccessionMap));
                                        }
                                      });
                            }
                            if (dataFilesAccessionsMap != null) {
                              Optional.ofNullable(assay.dataFiles)
                                  .orElse(new ArrayList<>())
                                  .forEach(
                                      dataFile -> {
                                        ReceiptAccessionMap dataFileAccessionMap = getAccessionMapEntry(
                                            dataFilesAccessionsMap, dataFile, marsMessage);
                                        if (dataFileAccessionMap.accession != null) {
                                          marsAccessions.add(getDataFileMarsAccession(
                                              studyAccessionMap,
                                              assay.id,
                                              dataFileAccessionMap));
                                        }
                                      });
                            }
                          });
                }
              }
            });
  }

  // ---------------------------------
  // | Making Mars accession objects |
  // ---------------------------------

  protected MarsAccession getStudyMarsAccession(final ReceiptAccessionMap studyAccessionMap) {
    return MarsAccession.builder()
        .path(
            new ArrayList<MarsPath>() {
              {
                add(MarsPath.builder().key("investigation").build());
                add(MarsPath.builder()
                    .key("studies")
                    .where(
                        MarsWhere.builder()
                            .key(studyAccessionMap.isaKeyName)
                            .value(studyAccessionMap.keyValue)
                            .build())
                    .build());
              }
            })
        .value(studyAccessionMap.accession)
        .build();
  }

  protected MarsAccession getSampleMarsAccession(
      final ReceiptAccessionMap studyAccessionMap,
      final ReceiptAccessionMap sampleAccessionMap) {
    return MarsAccession.builder()
        .path(
            new ArrayList<MarsPath>() {
              {
                add(MarsPath.builder().key("investigation").build());
                add(MarsPath.builder()
                    .key("studies")
                    .where(MarsWhere.builder()
                        .key(studyAccessionMap.isaKeyName)
                        .value(studyAccessionMap.keyValue)
                        .build())
                    .build());
                add(MarsPath.builder().key("materials").build());
                add(MarsPath.builder()
                    .key("samples")
                    .where(MarsWhere.builder()
                        .key(sampleAccessionMap.isaKeyName)
                        .value(sampleAccessionMap.keyValue)
                        .build())
                    .build());
              }
            })
        .value(sampleAccessionMap.accession)
        .build();
  }

  protected MarsAccession getSourceMarsAccession(
      final ReceiptAccessionMap studyAccessionMap,
      final ReceiptAccessionMap sourceAccessionMap) {
    return MarsAccession.builder()
        .path(
            new ArrayList<MarsPath>() {
              {
                add(MarsPath.builder().key("investigation").build());
                add(MarsPath.builder()
                    .key("studies")
                    .where(MarsWhere.builder()
                        .key(studyAccessionMap.isaKeyName)
                        .value(studyAccessionMap.keyValue)
                        .build())
                    .build());
                add(MarsPath.builder().key("materials").build());
                add(MarsPath.builder()
                    .key("sources")
                    .where(MarsWhere.builder()
                        .key(sourceAccessionMap.isaKeyName)
                        .value(sourceAccessionMap.keyValue)
                        .build())
                    .build());
              }
            })
        .value(sourceAccessionMap.accession)
        .build();
  }

  protected MarsAccession getOtherMaterialMarsAccession(
      final ReceiptAccessionMap studyAccessionMap,
      final String assayId,
      final ReceiptAccessionMap otherMaterialAccessionMap) {
    return MarsAccession.builder()
        .path(
            new ArrayList<MarsPath>() {
              {
                add(MarsPath.builder().key("investigation").build());
                add(MarsPath.builder()
                    .key("studies")
                    .where(MarsWhere.builder()
                        .key(studyAccessionMap.isaKeyName)
                        .value(studyAccessionMap.keyValue)
                        .build())
                    .build());
                add(MarsPath.builder()
                    .key("assays")
                    .where(MarsWhere.builder()
                        .key("@id")
                        .value(assayId)
                        .build())
                    .build());
                add(MarsPath.builder().key("materials").build());
                add(MarsPath.builder()
                    .key("otherMaterials")
                    .where(MarsWhere.builder()
                        .key(otherMaterialAccessionMap.isaKeyName)
                        .value(otherMaterialAccessionMap.keyValue)
                        .build())
                    .build());
              }
            })
        .value(otherMaterialAccessionMap.accession)
        .build();
  }

  protected MarsAccession getDataFileMarsAccession(
      final ReceiptAccessionMap studyAccessionMap,
      final String assayId,
      final ReceiptAccessionMap dataFileAccessionMap) {
    return MarsAccession.builder()
        .path(
            new ArrayList<MarsPath>() {
              {
                add(MarsPath.builder().key("investigation").build());
                add(MarsPath.builder()
                    .key("studies")
                    .where(MarsWhere.builder()
                        .key(studyAccessionMap.isaKeyName)
                        .value(studyAccessionMap.keyValue)
                        .build())
                    .build());
                add(MarsPath.builder()
                    .key("assays")
                    .where(MarsWhere.builder()
                        .key("@id")
                        .value(assayId)
                        .build())
                    .build());
                add(MarsPath.builder()
                    .key("dataFiles")
                    .where(MarsWhere.builder()
                        .key(dataFileAccessionMap.isaKeyName)
                        .value(dataFileAccessionMap.keyValue)
                        .build())
                    .build());
              }
            })
        .value(dataFileAccessionMap.accession)
        .build();
  }

  // ---------------------------------

  private <T> ReceiptAccessionMap getAccessionMapEntry(
      final ReceiptAccessionsMap accessionsMap,
      final T item,
      final MarsMessage marsMessage) {
    try {
      return new ReceiptAccessionMap() {
        {
          Field field = item.getClass().getField(accessionsMap.keyName);
          if (field.isAnnotationPresent(JsonProperty.class)) {
            isaKeyName = field.getAnnotation(JsonProperty.class).value();
          } else {
            isaKeyName = accessionsMap.keyName;
          }
          keyName = accessionsMap.keyName;
          keyValue = field.get(item).toString();
          accession = accessionsMap.accessionMap.get(keyValue);
        }
      };
    } catch (NoSuchFieldException | IllegalAccessException e) {
      marsMessage.errors
          .add(
              MarsError.builder()
                  .message(
                      String.format("Cannot find an item of %s with the key %s in the ISA-JSON input",
                          item.getClass().getSimpleName(),
                          accessionsMap.keyName))
                  .type(MarsErrorType.INVALID_METADATA)
                  .build());
      return new ReceiptAccessionMap();
    }
  }
}
