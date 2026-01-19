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
    designElement.addElement("DESIGN_DESCRIPTION").addText("ISA-Test");

    final String sourceBioSampleAccession = bioSampleAccessions.get("SOURCE");
    designElement
        .addElement("SAMPLE_DESCRIPTOR")
        .addAttribute("accession", sourceBioSampleAccession);

    final Element libraryDescriptorElement = designElement.addElement("LIBRARY_DESCRIPTOR");

    // Extract library parameters from the library construction process
    if (libraryConstructionProcess != null
        && libraryConstructionProcess.getExecutesProtocol() != null) {
      final String protocolId = libraryConstructionProcess.getExecutesProtocol().getId();
      final List<Parameter> protocolParameters = protocolToParameterMap.get(protocolId);
      final List<ParameterValue> parameterValues = libraryConstructionProcess.getParameterValues();

      if (protocolParameters != null && parameterValues != null) {
        addLibraryParameters(
            libraryDescriptorElement, library, protocolParameters, parameterValues);
      }
    }

    // Add platform information (hardcoded for now, could be extracted from sequencing process)
    final Element platformElement = experimentElement.addElement("PLATFORM");
    final Element experimentTypeElement = platformElement.addElement("OXFORD_NANOPORE");
    experimentTypeElement.addElement("INSTRUMENT_MODEL").addText("MinION");
  }

  /**
   * Adds library parameters to the library descriptor in the correct order: 1. LIBRARY_NAME 2.
   * LIBRARY_STRATEGY 3. LIBRARY_SOURCE 4. LIBRARY_SELECTION 5. LIBRARY_LAYOUT
   */
  private void addLibraryParameters(
      final Element libraryDescriptorElement,
      final OtherMaterial library,
      final List<Parameter> protocolParameters,
      final List<ParameterValue> parameterValues) {
    // Collect parameter values first
    String libraryName = library.getName() != null ? library.getName() : null;
    String libraryStrategy = null;
    String librarySource = null;
    String librarySelection = null;
    String libraryLayout = null;

    for (final Parameter parameter : protocolParameters) {
      final String parameterId = parameter.getId();
      final String parameterName = parameter.getParameterName().getAnnotationValue();

      for (final ParameterValue parameterValue : parameterValues) {
        if (parameterValue.getCategory() != null
            && parameterValue.getCategory().getId() != null
            && parameterValue.getCategory().getId().equals(parameterId)) {
          final String value = parameterValue.getValue().getAnnotationValue();

          if (isALibraryStrategyParameterName(parameterName)) {
            libraryStrategy = value;
          } else if ("library source".equalsIgnoreCase(parameterName)) {
            librarySource = value;
          } else if ("library selection".equalsIgnoreCase(parameterName)) {
            librarySelection = value;
          } else if (isALibraryLayoutParameterName(parameterName)) {
            libraryLayout = value;
          }
        }
      }
    }

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
      libraryLayoutElement.addElement(libraryLayout.toUpperCase());
    }
  }

  private boolean isALibraryStrategyParameterName(final String parameterName) {
    return parameterName.equalsIgnoreCase("library strategy");
  }

  private boolean isALibraryLayoutParameterName(final String parameterName) {
    return parameterName.equalsIgnoreCase("library layout");
  }
}
