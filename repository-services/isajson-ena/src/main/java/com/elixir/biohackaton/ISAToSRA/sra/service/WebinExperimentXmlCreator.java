/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptException;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WebinExperimentXmlCreator {
  public static final String OTHER_MATERIAL_LIBRARY_NAME_DETERMINES_EXPERIMENT = "Library Name";
  private static final String DEFAULT_DESIGN_DESCRIPTION = "ISA-Test";

  public Map<String, String> createENAExperimentSetElement(
      final Map<String, String> typeToBioSamplesAccessionMap,
      final Element webinElement,
      final List<Study> studies,
      final String randomSubmissionIdentifier) {
    try {
      final Element root = webinElement.addElement("EXPERIMENT_SET");
      final Map<String, List<Parameter>> protocolToParameterMap =
          populateProtocolToParameterMap(studies);

      return mapExperiments(
          studies,
          root,
          protocolToParameterMap,
          typeToBioSamplesAccessionMap,
          randomSubmissionIdentifier);
    } catch (final Exception e) {
      throw new MarsReceiptException(
          e, "Failed to parse experiments from ISA Json file and create ENA Experiments");
    }
  }

  private Map<String, List<Parameter>> populateProtocolToParameterMap(final List<Study> studies) {
    final Map<String, List<Parameter>> protocolToParameterMap = new HashMap<>();

    studies.forEach(
        study ->
            study
                .getProtocols()
                .forEach(
                    protocol -> {
                      protocolToParameterMap.put(protocol.id, protocol.getParameters());
                    }));

    return protocolToParameterMap;
  }

  private Map<String, String> mapExperiments(
      final List<Study> studies,
      final Element root,
      final Map<String, List<Parameter>> protocolToParameterMap,
      final Map<String, String> bioSampleAccessions,
      final String randomSubmissionIdentifier) {
    final Map<String, String> experimentSequence = new HashMap<>();

    // Bottom-up approach: Start from DataFiles and work up to Libraries
    studies.forEach(
        study ->
            study
                .getAssays()
                .forEach(
                    assay -> {
                      // Start from DataFiles
                      if (assay.getDataFiles() != null) {
                        assay
                            .getDataFiles()
                            .forEach(
                                dataFile -> {
                                  // Find the process that produced this data file
                                  final ProcessSequence sequencingProcess =
                                      findProcessByOutputId(
                                          assay.getProcessSequence(), dataFile.getId());

                                  if (sequencingProcess != null) {
                                    // Get the Library (OtherMaterial) that was input to sequencing
                                    final OtherMaterial library =
                                        findLibraryFromProcessInput(
                                            sequencingProcess, assay.getMaterials());

                                    if (library != null) {
                                      // Create an experiment only once per library
                                      if (!experimentSequence.containsKey(library.getId())) {
                                        final String experimentId =
                                            library.getId() + "-" + randomSubmissionIdentifier;
                                        experimentSequence.put(library.getId(), experimentId);

                                        // Find the library construction process
                                        final ProcessSequence libraryConstructionProcess =
                                            findProcessByOutputId(
                                                assay.getProcessSequence(), library.getId());

                                        createExperimentElement(
                                            root,
                                            library,
                                            study,
                                            libraryConstructionProcess,
                                            sequencingProcess,
                                            protocolToParameterMap,
                                            bioSampleAccessions,
                                            experimentId,
                                            randomSubmissionIdentifier);
                                      }
                                    }
                                  }
                                });
                      }
                    }));

    return experimentSequence;
  }

  /**
   * Finds a process that has the given output ID. Handles both #data_file/334 and #data/334
   * formats.
   */
  private ProcessSequence findProcessByOutputId(
      final List<ProcessSequence> processSequence, final String outputId) {
    if (processSequence == null || outputId == null) {
      return null;
    }

    // Normalize the outputId (handle both #data_file/334 and #data/334)
    final String normalizedOutputId = normalizeDataFileId(outputId);

    for (final ProcessSequence process : processSequence) {
      if (process.getOutputs() != null) {
        for (final Output output : process.getOutputs()) {
          if (output.getId() != null) {
            final String normalizedProcessOutputId = normalizeDataFileId(output.getId());
            if (normalizedProcessOutputId.equals(normalizedOutputId)) {
              return process;
            }
          }
        }
      }
    }
    return null;
  }

  /** Normalizes data file IDs to handle both #data_file/334 and #data/334 formats. */
  private String normalizeDataFileId(final String id) {
    if (id == null) {
      return null;
    }
    // Convert #data_file/334 to #data/334 for comparison
    return id.replace("#data_file/", "#data/");
  }

  /** Finds the Library (OtherMaterial) that was used as input to a process. */
  private OtherMaterial findLibraryFromProcessInput(
      final ProcessSequence process, final Materials materials) {
    if (process.getInputs() == null || materials == null || materials.getOtherMaterials() == null) {
      return null;
    }

    for (final Input input : process.getInputs()) {
      if (input.getId() != null) {
        for (final OtherMaterial otherMaterial : materials.getOtherMaterials()) {
          if (otherMaterial.getId() != null && otherMaterial.getId().equals(input.getId())) {
            return otherMaterial;
          }
        }
      }
    }
    return null;
  }

  /** Creates an ENA EXPERIMENT element from a Library (OtherMaterial). */
  private void createExperimentElement(
      final Element root,
      final OtherMaterial library,
      final Study study,
      final ProcessSequence libraryConstructionProcess,
      final ProcessSequence sequencingProcess,
      final Map<String, List<Parameter>> protocolToParameterMap,
      final Map<String, String> bioSampleAccessions,
      final String experimentId,
      final String randomSubmissionIdentifier) {
    final Element experimentElement = root.addElement("EXPERIMENT");

    experimentElement.addAttribute("alias", experimentId);
    experimentElement.addElement("TITLE").addText(library.getName());
    experimentElement
        .addElement("STUDY_REF")
        .addAttribute("refname", study.getTitle() + "-" + randomSubmissionIdentifier);

    final Element designElement = experimentElement.addElement("DESIGN");

    final String sourceBioSampleAccession = bioSampleAccessions.get("SOURCE");
    designElement
        .addElement("SAMPLE_DESCRIPTOR")
        .addAttribute("accession", sourceBioSampleAccession);

    final Element libraryDescriptorElement = designElement.addElement("LIBRARY_DESCRIPTOR");

    final Map<String, String> libraryParameters =
        extractParameterValues(libraryConstructionProcess, protocolToParameterMap);
    final Map<String, String> sequencingParameters =
        extractParameterValues(sequencingProcess, protocolToParameterMap);

    final String designDescription = libraryParameters.get("design_description");
    designElement
        .addElement("DESIGN_DESCRIPTION")
        .addText(
            designDescription != null && !designDescription.isBlank()
                ? designDescription
                : DEFAULT_DESIGN_DESCRIPTION);

    addLibraryParameters(libraryDescriptorElement, library, libraryParameters);

    addPlatformInformation(experimentElement, sequencingParameters);
  }

  /**
   * Adds library parameters to the library descriptor in the correct order: 1. LIBRARY_NAME 2.
   * LIBRARY_STRATEGY 3. LIBRARY_SOURCE 4. LIBRARY_SELECTION 5. LIBRARY_LAYOUT
   */
  private void addLibraryParameters(
      final Element libraryDescriptorElement,
      final OtherMaterial library,
      final Map<String, String> libraryParameters) {
    String libraryName = library.getName() != null ? library.getName() : null;
    final String libraryStrategy = libraryParameters.get("library strategy");
    final String librarySource = libraryParameters.get("library source");
    final String librarySelection = libraryParameters.get("library selection");
    final String libraryLayout = libraryParameters.get("library layout");
    final String insertSize = libraryParameters.get("insert size");
    final String libraryConstructionProtocol =
        libraryParameters.get("library_construction_protocol");

    // Add elements in the required order
    // 1. LIBRARY_NAME
    if (libraryName != null) {
      libraryDescriptorElement.addElement("LIBRARY_NAME").addText(libraryName);
    }

    // 2. LIBRARY_STRATEGY
    if (libraryStrategy != null) {
      libraryDescriptorElement.addElement("LIBRARY_STRATEGY").addText(libraryStrategy);
    }

    // 3. LIBRARY_SOURCE
    if (librarySource != null) {
      libraryDescriptorElement.addElement("LIBRARY_SOURCE").addText(librarySource);
    }

    // 4. LIBRARY_SELECTION
    if (librarySelection != null) {
      libraryDescriptorElement.addElement("LIBRARY_SELECTION").addText(librarySelection);
    }

    // 5. LIBRARY_LAYOUT
    if (libraryLayout != null) {
      final Element libraryLayoutElement = libraryDescriptorElement.addElement("LIBRARY_LAYOUT");
      final String normalizedLayout = libraryLayout.trim().toUpperCase();
      if ("PAIRED".equals(normalizedLayout)) {
        final Element pairedElement = libraryLayoutElement.addElement("PAIRED");
        if (insertSize != null && !insertSize.isBlank()) {
          pairedElement.addAttribute("NOMINAL_LENGTH", insertSize.trim());
        }
      } else {
        libraryLayoutElement.addElement(normalizedLayout);
      }
    }

    if (libraryConstructionProtocol != null && !libraryConstructionProtocol.isBlank()) {
      libraryDescriptorElement
          .addElement("LIBRARY_CONSTRUCTION_PROTOCOL")
          .addText(libraryConstructionProtocol);
    }
  }

  private Map<String, String> extractParameterValues(
      final ProcessSequence processSequence,
      final Map<String, List<Parameter>> protocolToParameterMap) {
    final Map<String, String> parameterMap = new HashMap<>();

    if (processSequence == null
        || processSequence.getExecutesProtocol() == null
        || processSequence.getExecutesProtocol().getId() == null
        || processSequence.getParameterValues() == null) {
      return parameterMap;
    }

    final List<Parameter> protocolParameters =
        protocolToParameterMap.get(processSequence.getExecutesProtocol().getId());

    if (protocolParameters == null) {
      return parameterMap;
    }

    for (final Parameter parameter : protocolParameters) {
      if (parameter == null
          || parameter.getId() == null
          || parameter.getParameterName() == null
          || parameter.getParameterName().getAnnotationValue() == null) {
        continue;
      }

      for (final ParameterValue parameterValue : processSequence.getParameterValues()) {
        if (parameterValue == null
            || parameterValue.getCategory() == null
            || parameterValue.getCategory().getId() == null
            || parameterValue.getValue() == null
            || parameterValue.getValue().getAnnotationValue() == null) {
          continue;
        }

        if (parameterValue.getCategory().getId().equals(parameter.getId())) {
          final String normalizedName =
              normalizeParameterName(parameter.getParameterName().getAnnotationValue());
          final String normalizedValue = parameterValue.getValue().getAnnotationValue().trim();
          parameterMap.put(normalizedName, normalizedValue);
        }
      }
    }

    return parameterMap;
  }

  private void addPlatformInformation(
      final Element experimentElement, final Map<String, String> sequencingParameters) {
    final String instrumentModel = sequencingParameters.get("sequencing instrument");
    final Element platformElement = experimentElement.addElement("PLATFORM");

    if (instrumentModel == null || instrumentModel.isBlank()) {
      throw new MarsReceiptException("Missing sequencing instrument parameter for ENA platform");
    }

    final Element platformTypeElement =
        platformElement.addElement(getPlatformElementName(instrumentModel));
    platformTypeElement.addElement("INSTRUMENT_MODEL").addText(instrumentModel);
  }

  private String getPlatformElementName(final String instrumentModel) {
    final String normalizedInstrument = instrumentModel.trim().toLowerCase();

    if (normalizedInstrument.contains("minion")
        || normalizedInstrument.contains("gridion")
        || normalizedInstrument.contains("promethion")
        || normalizedInstrument.contains("nanopore")) {
      return "OXFORD_NANOPORE";
    }

    if (normalizedInstrument.contains("illumina")) {
      return "ILLUMINA";
    }

    if (normalizedInstrument.contains("pacbio")
        || normalizedInstrument.contains("sequel")
        || normalizedInstrument.contains("rs ii")) {
      return "PACBIO_SMRT";
    }

    if (normalizedInstrument.contains("ion torrent")
        || normalizedInstrument.contains("ion proton")
        || normalizedInstrument.contains("ion s5")
        || normalizedInstrument.contains("ion pgm")) {
      return "ION_TORRENT";
    }

    throw new MarsReceiptException(
        String.format("Unsupported sequencing instrument for ENA platform mapping: %s", instrumentModel));
  }

  private String normalizeParameterName(final String parameterName) {
    return parameterName == null ? "" : parameterName.trim().toLowerCase();
  }
}
