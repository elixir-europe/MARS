package com.elixir.biohackaton.ISAToSRA.receipt;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.*;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class MarsReceiptProvider {

  private class ReceiptAccessionMap {
    public String keyName;
    public String isaKeyName;
    public String keyValue;
    public String accession;
  }

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
   * @param isaJson          Requested ISA-Json
   * @param info             List of info messages
   * @param errors           List of error messages
   * @return {@link MarsReceipt} Mars response data
   */
  protected MarsReceipt buildMarsReceipt(
      final String targetRepository,
      final ReceiptAccessionsMap studiesAccessionsMap,
      final ReceiptAccessionsMap samplesAccessionsMap,
      final ReceiptAccessionsMap sourcesAccessionsMap,
      final ReceiptAccessionsMap otherMaterialsAccessionsMap,
      final ReceiptAccessionsMap dataFilesAccessionsMap,
      final List<String> info,
      final List<String> errors,
      final IsaJson isaJson) {
    final MarsMessage marsMessage = MarsMessage.builder().build();
    final List<MarsAccession> marsAccessions = new ArrayList<>();
    setMarsReceiptErrors(errors, marsMessage);
    setMarsReceiptInfo(info, marsMessage);
    setMarsAccessions(
        studiesAccessionsMap,
        samplesAccessionsMap,
        sourcesAccessionsMap,
        otherMaterialsAccessionsMap,
        dataFilesAccessionsMap,
        isaJson,
        marsMessage,
        marsAccessions);
    System.out.println("Mars accessions: " + marsAccessions);
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
      final ReceiptAccessionsMap studiesAccessionsMap,
      final ReceiptAccessionsMap samplesAccessionsMap,
      final ReceiptAccessionsMap sourcesAccessionsMap,
      final ReceiptAccessionsMap otherMaterialsAccessionsMap,
      final ReceiptAccessionsMap dataFilesAccessionsMap,
      final IsaJson isaJson,
      final MarsMessage marsMessage,
      final List<MarsAccession> accessions) {
    Optional.ofNullable(isaJson.investigation.studies)
        .orElse(new ArrayList<>())
        .forEach(
            study -> {
              if (studiesAccessionsMap != null) {
                ReceiptAccessionMap studyAccessionMap = getAccessionMapEntry(
                    studiesAccessionsMap, study, marsMessage);
                if (studyAccessionMap.accession != null) {
                  accessions.add(getStudyMarsAccession(studyAccessionMap));
                }
                if (samplesAccessionsMap != null) {
                  Optional.ofNullable(study.materials.samples)
                      .orElse(new ArrayList<>())
                      .forEach(
                          sample -> {
                            ReceiptAccessionMap samplAccessionMap = getAccessionMapEntry(
                                samplesAccessionsMap, sample, marsMessage);
                            if (samplAccessionMap.accession != null) {
                              accessions.add(getSampleMarsAccession(studyAccessionMap, samplAccessionMap));
                            }
                          });
                }
                if (sourcesAccessionsMap != null) {
                  Optional.ofNullable(study.materials.sources)
                      .orElse(new ArrayList<>())
                      .forEach(
                          source -> {
                            ReceiptAccessionMap sourceAccessionMap = getAccessionMapEntry(
                                sourcesAccessionsMap, source, marsMessage);
                            if (sourceAccessionMap.accession != null) {
                              accessions.add(getSourceMarsAccession(studyAccessionMap, sourceAccessionMap));
                            }
                          });
                }
                if (otherMaterialsAccessionsMap != null || dataFilesAccessionsMap != null) {
                  Optional.ofNullable(study.assays)
                      .orElse(new ArrayList<>())
                      .forEach(
                          assay -> {
                            if (otherMaterialsAccessionsMap != null) {
                              Optional.ofNullable(assay.materials.otherMaterials)
                                  .orElse(new ArrayList<>())
                                  .forEach(
                                      otherMaterial -> {
                                        ReceiptAccessionMap otherMaterialAccessionMap = getAccessionMapEntry(
                                            otherMaterialsAccessionsMap, otherMaterial, marsMessage);
                                        if (otherMaterialAccessionMap.accession != null) {
                                          accessions.add(getOtherMaterialMarsAccession(
                                              studyAccessionMap,
                                              assay.id,
                                              otherMaterialAccessionMap));
                                        }
                                      });
                            }
                            if (dataFilesAccessionsMap != null) {
                              Optional.ofNullable(
                                  assay.dataFiles)
                                  .orElse(new ArrayList<>())
                                  .forEach(
                                      dataFile -> {
                                        ReceiptAccessionMap dataFileAccessionMap = getAccessionMapEntry(
                                            dataFilesAccessionsMap, dataFile, marsMessage);
                                        if (dataFileAccessionMap.accession != null) {
                                          accessions.add(getDataFileMarsAccession(
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

    return accessions;
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
