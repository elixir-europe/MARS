/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptException;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Category;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Characteristic;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.CharacteristicCategory;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.DataFile;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.DerivesFrom;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Input;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Materials;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.OtherMaterial;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Output;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Parameter;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.ParameterValue;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.ProcessSequence;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Sample;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WebinExperimentXmlCreator {
  public static final String OTHER_MATERIAL_LIBRARY_NAME_DETERMINES_EXPERIMENT = "Library Name";

  /**
   * Creates ENA EXPERIMENT XML from ISA-JSON assays.
   *
   * <p>The conversion keeps the original bottom-up approach: start from assay data files, resolve
   * the sequencing process that produced them, walk back to the library, and then create one ENA
   * experiment per library.
   */
  public Map<String, String> createENAExperimentSetElement(
      final Map<String, String> typeToBioSamplesAccessionMap,
      final Element webinElement,
      final List<Study> studies,
      final String randomSubmissionIdentifier) {
    try {
      final Element root = webinElement.addElement("EXPERIMENT_SET");
      final Map<String, Map<String, String>> protocolToParameterNameMap =
          populateProtocolToParameterNameMap(studies);

      return mapExperiments(
          studies,
          root,
          protocolToParameterNameMap,
          typeToBioSamplesAccessionMap,
          randomSubmissionIdentifier);
    } catch (final Exception e) {
      throw new MarsReceiptException(
          e, "Failed to parse experiments from ISA Json file and create ENA Experiments");
    }
  }

  private Map<String, Map<String, String>> populateProtocolToParameterNameMap(
      final List<Study> studies) {
    final Map<String, Map<String, String>> protocolToParameterNameMap = new HashMap<>();

    studies.forEach(
        study -> {
          if (study.getProtocols() == null) {
            return;
          }

          study
              .getProtocols()
              .forEach(
                  protocol -> {
                    final Map<String, String> parameterNameMap = new HashMap<>();

                    if (protocol.getParameters() != null) {
                      protocol
                          .getParameters()
                          .forEach(
                              parameter -> addParameterName(parameterNameMap, parameter));
                    }

                    protocolToParameterNameMap.put(protocol.id, parameterNameMap);
                  });
        });

    return protocolToParameterNameMap;
  }

  private void addParameterName(
      final Map<String, String> parameterNameMap, final Parameter parameter) {
    if (parameter == null
        || parameter.getId() == null
        || parameter.getParameterName() == null
        || parameter.getParameterName().getAnnotationValue() == null
        || parameter.getParameterName().getAnnotationValue().isBlank()) {
      return;
    }

    parameterNameMap.put(parameter.getId(), parameter.getParameterName().getAnnotationValue());
  }

  private Map<String, String> mapExperiments(
      final List<Study> studies,
      final Element root,
      final Map<String, Map<String, String>> protocolToParameterNameMap,
      final Map<String, String> bioSampleAccessions,
      final String randomSubmissionIdentifier) {
    final Map<String, String> experimentSequence = new HashMap<>();

    // Bottom-up approach: Start from DataFiles and work up to Libraries
    studies.forEach(
        study -> {
          if (study.getAssays() == null) {
            return;
          }

          study
              .getAssays()
              .forEach(
                  assay -> {
                    // Start from DataFiles
                    if (assay.getDataFiles() == null) {
                      return;
                    }

                    assay
                        .getDataFiles()
                        .forEach(
                            dataFile ->
                                mapExperimentForDataFile(
                                    root,
                                    protocolToParameterNameMap,
                                    bioSampleAccessions,
                                    randomSubmissionIdentifier,
                                    experimentSequence,
                                    study,
                                    assay,
                                    dataFile));
                  });
        });

    return experimentSequence;
  }

  /**
   * Resolves the library associated with a data file and creates the corresponding ENA experiment
   * once for that library.
   */
  private void mapExperimentForDataFile(
      final Element root,
      final Map<String, Map<String, String>> protocolToParameterNameMap,
      final Map<String, String> bioSampleAccessions,
      final String randomSubmissionIdentifier,
      final Map<String, String> experimentSequence,
      final Study study,
      final com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Assay assay,
      final DataFile dataFile) {
    final ProcessSequence sequencingProcess =
        findProcessByOutputId(assay.getProcessSequence(), dataFile.getId());

    if (sequencingProcess == null) {
      return;
    }

    final OtherMaterial library =
        findLibraryFromProcessInput(sequencingProcess, assay.getMaterials());

    if (library == null || experimentSequence.containsKey(library.getId())) {
      return;
    }

    final String experimentId = library.getId() + "-" + randomSubmissionIdentifier;
    experimentSequence.put(library.getId(), experimentId);

    final ProcessSequence libraryConstructionProcess =
        findProcessByOutputId(assay.getProcessSequence(), library.getId());

    createExperimentElement(
        root,
        study,
        library,
        assay,
        libraryConstructionProcess,
        sequencingProcess,
        protocolToParameterNameMap,
        bioSampleAccessions,
        experimentId,
        randomSubmissionIdentifier);
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

        final String normalizedProcessOutputId = normalizeDataFileId(output.getId());
        if (normalizedProcessOutputId.equals(normalizedOutputId)) {
          return process;
        }
      }
    }

    return null;
  }

  private String normalizeDataFileId(final String id) {
    if (id == null) {
      return null;
    }
    return id.replace("#data_file/", "#data/");
  }

  private OtherMaterial findLibraryFromProcessInput(
      final ProcessSequence process, final Materials materials) {
    if (process.getInputs() == null || materials == null || materials.getOtherMaterials() == null) {
      return null;
    }

    for (final Input input : process.getInputs()) {
      if (input.getId() == null) {
        continue;
      }

      for (final OtherMaterial otherMaterial : materials.getOtherMaterials()) {
        if (otherMaterial.getId() != null && otherMaterial.getId().equals(input.getId())) {
          return otherMaterial;
        }
      }
    }

    return null;
  }

  /** Creates one ENA EXPERIMENT element using ENA-native assay/process parameter names. */
  private void createExperimentElement(
      final Element root,
      final Study study,
      final OtherMaterial library,
      final com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Assay assay,
      final ProcessSequence libraryConstructionProcess,
      final ProcessSequence sequencingProcess,
      final Map<String, Map<String, String>> protocolToParameterNameMap,
      final Map<String, String> bioSampleAccessions,
      final String experimentId,
      final String randomSubmissionIdentifier) {
    final Map<String, String> libraryParameters =
        extractParameterValues(libraryConstructionProcess, protocolToParameterNameMap);
    final Map<String, String> sequencingParameters =
        extractParameterValues(sequencingProcess, protocolToParameterNameMap);

    final Element experimentElement = root.addElement("EXPERIMENT");
    experimentElement.addAttribute("alias", experimentId);
    experimentElement
        .addElement("TITLE")
        .addText(firstNonBlank(libraryParameters.get("TITLE"), libraryParameters.get("LIBRARY_NAME"), library.getName()));
    experimentElement
        .addElement("STUDY_REF")
        .addAttribute("refname", assay.getId() + "-" + randomSubmissionIdentifier);

    final Element designElement = experimentElement.addElement("DESIGN");
    designElement
        .addElement("DESIGN_DESCRIPTION")
        .addText(requireParameter(libraryParameters, "DESIGN_DESCRIPTION"));

    final String sampleAccession = resolveSampleAccessionForLibrary(study, assay, library);
    designElement
        .addElement("SAMPLE_DESCRIPTOR")
        .addAttribute("accession", requireValue(sampleAccession, "BioSamples sample accession"));

    final Element libraryDescriptorElement = designElement.addElement("LIBRARY_DESCRIPTOR");
    addLibraryDescriptor(libraryDescriptorElement, library, libraryParameters);
    addPlatform(experimentElement, sequencingParameters);
  }

  /**
   * Resolves the BioSamples accession for the biological sample that the library derives from.
   */
  private String resolveSampleAccessionForLibrary(
      final Study study,
      final com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Assay assay,
      final OtherMaterial library) {
    if (study == null || study.getMaterials() == null || study.getMaterials().getSamples() == null) {
      return "";
    }

    final Map<String, Sample> samplesById = new HashMap<>();
    for (final Sample sample : study.getMaterials().getSamples()) {
      if (sample != null && sample.getId() != null) {
        samplesById.put(sample.getId(), sample);
      }
    }

    final Map<String, OtherMaterial> otherMaterialsById = new HashMap<>();
    if (assay.getMaterials() != null && assay.getMaterials().getOtherMaterials() != null) {
      for (final OtherMaterial otherMaterial : assay.getMaterials().getOtherMaterials()) {
        if (otherMaterial != null && otherMaterial.getId() != null) {
          otherMaterialsById.put(otherMaterial.getId(), otherMaterial);
        }
      }
    }

    final Sample sample =
        findSampleForMaterialId(library.getId(), samplesById, otherMaterialsById, new HashSet<>());
    if (sample == null) {
      return "";
    }

    final Map<String, String> characteristicKeyLookup = buildCharacteristicKeyLookup(study);
    return getCharacteristicAnnotation(sample.getCharacteristics(), characteristicKeyLookup);
  }

  private Sample findSampleForMaterialId(
      final String materialId,
      final Map<String, Sample> samplesById,
      final Map<String, OtherMaterial> otherMaterialsById,
      final Set<String> visitedIds) {
    if (materialId == null || !visitedIds.add(materialId)) {
      return null;
    }

    final Sample sample = samplesById.get(materialId);
    if (sample != null) {
      return sample;
    }

    final OtherMaterial otherMaterial = otherMaterialsById.get(materialId);
    if (otherMaterial == null || otherMaterial.getDerivesFrom() == null) {
      return null;
    }

    for (final DerivesFrom derivesFrom : otherMaterial.getDerivesFrom()) {
      if (derivesFrom == null || derivesFrom.getId() == null) {
        continue;
      }

      final Sample derivedSample =
          findSampleForMaterialId(
              derivesFrom.getId(), samplesById, otherMaterialsById, visitedIds);
      if (derivedSample != null) {
        return derivedSample;
      }
    }

    return null;
  }

  private Map<String, String> buildCharacteristicKeyLookup(final Study study) {
    final Map<String, String> keyLookup = new HashMap<>();

    if (study == null || study.characteristicCategories == null) {
      return keyLookup;
    }

    for (CharacteristicCategory characteristicCategory : study.characteristicCategories) {
      if (characteristicCategory == null
          || characteristicCategory.id == null
          || characteristicCategory.characteristicType == null
          || characteristicCategory.characteristicType.annotationValue == null
          || characteristicCategory.characteristicType.annotationValue.isBlank()) {
        continue;
      }

      keyLookup.put(
          characteristicCategory.id,
          characteristicCategory.characteristicType.annotationValue);
    }

    return keyLookup;
  }

  private String getCharacteristicAnnotation(
      final List<Characteristic> characteristics, final Map<String, String> characteristicKeyLookup) {
    if (characteristics == null) {
      return "";
    }

    for (Characteristic characteristic : characteristics) {
      if (characteristic.category == null) {
        continue;
      }

      final Category category = characteristic.category;
      final String characteristicName =
          category.id != null ? characteristicKeyLookup.get(category.id) : null;
      final boolean accessionCategoryNameMatches =
          "accession".equalsIgnoreCase(characteristicName)
              || (category.characteristicType != null
                  && "accession".equalsIgnoreCase(category.characteristicType.annotationValue));
      final boolean accessionCategoryIdMatches =
          !accessionCategoryNameMatches
              && category.id != null
              && category.id.startsWith("#characteristic_category/accession");

      if ((accessionCategoryIdMatches || accessionCategoryNameMatches)
          && characteristic.value != null) {
        return characteristic.value.annotationValue;
      }
    }

    return "";
  }

  /**
   * Reads process parameter values by their declared ISA parameter names so ENA field names can be
   * used directly in the ISA without an extra mapping layer.
   */
  private Map<String, String> extractParameterValues(
      final ProcessSequence processSequence,
      final Map<String, Map<String, String>> protocolToParameterNameMap) {
    final Map<String, String> parameterValuesByName = new HashMap<>();

    if (processSequence == null
        || processSequence.getExecutesProtocol() == null
        || processSequence.getExecutesProtocol().getId() == null
        || processSequence.getParameterValues() == null) {
      return parameterValuesByName;
    }

    final Map<String, String> parameterNamesById =
        protocolToParameterNameMap.get(processSequence.getExecutesProtocol().getId());

    if (parameterNamesById == null) {
      return parameterValuesByName;
    }

    for (ParameterValue parameterValue : processSequence.getParameterValues()) {
      if (parameterValue == null
          || parameterValue.getCategory() == null
          || parameterValue.getCategory().getId() == null
          || parameterValue.getValue() == null
          || parameterValue.getValue().getAnnotationValue() == null) {
        continue;
      }

      final String parameterName = parameterNamesById.get(parameterValue.getCategory().getId());
      if (parameterName == null || parameterName.isBlank()) {
        continue;
      }

      parameterValuesByName.put(parameterName, parameterValue.getValue().getAnnotationValue());
    }

    return parameterValuesByName;
  }

  /** Populates the ENA LIBRARY_DESCRIPTOR block in schema order. */
  private void addLibraryDescriptor(
      final Element libraryDescriptorElement,
      final OtherMaterial library,
      final Map<String, String> libraryParameters) {
    addOptionalTextElement(
        libraryDescriptorElement,
        "LIBRARY_NAME",
        firstNonBlank(libraryParameters.get("LIBRARY_NAME"), library.getName()));
    libraryDescriptorElement
        .addElement("LIBRARY_STRATEGY")
        .addText(requireParameter(libraryParameters, "LIBRARY_STRATEGY"));
    libraryDescriptorElement
        .addElement("LIBRARY_SOURCE")
        .addText(requireParameter(libraryParameters, "LIBRARY_SOURCE"));
    libraryDescriptorElement
        .addElement("LIBRARY_SELECTION")
        .addText(requireParameter(libraryParameters, "LIBRARY_SELECTION"));

    final Element libraryLayoutElement = libraryDescriptorElement.addElement("LIBRARY_LAYOUT");
    final String layout = requireParameter(libraryParameters, "LIBRARY_LAYOUT");
    final Element layoutElement = libraryLayoutElement.addElement(layout);

    if ("PAIRED".equals(layout)) {
      addOptionalAttribute(layoutElement, "NOMINAL_LENGTH", libraryParameters.get("NOMINAL_LENGTH"));
      addOptionalAttribute(layoutElement, "NOMINAL_SDEV", libraryParameters.get("NOMINAL_SDEV"));
    }

    addOptionalTextElement(
        libraryDescriptorElement,
        "POOLING_STRATEGY",
        libraryParameters.get("POOLING_STRATEGY"));
    addOptionalTextElement(
        libraryDescriptorElement,
        "LIBRARY_CONSTRUCTION_PROTOCOL",
        libraryParameters.get("LIBRARY_CONSTRUCTION_PROTOCOL"));
  }

  /** Populates the ENA PLATFORM block directly from sequencing-process parameters. */
  private void addPlatform(
      final Element experimentElement, final Map<String, String> sequencingParameters) {
    final String platform = requireParameter(sequencingParameters, "PLATFORM");
    final String instrumentModel = requireParameter(sequencingParameters, "INSTRUMENT_MODEL");

    final Element platformElement = experimentElement.addElement("PLATFORM");
    final Element platformTypeElement = platformElement.addElement(platform);
    platformTypeElement.addElement("INSTRUMENT_MODEL").addText(instrumentModel);
  }

  private void addOptionalTextElement(
      final Element parentElement, final String elementName, final String value) {
    if (value != null && !value.isBlank()) {
      parentElement.addElement(elementName).addText(value);
    }
  }

  private void addOptionalAttribute(
      final Element element, final String attributeName, final String value) {
    if (value != null && !value.isBlank()) {
      element.addAttribute(attributeName, value);
    }
  }

  private String requireParameter(
      final Map<String, String> parameterValues, final String parameterName) {
    return requireValue(
        parameterValues.get(parameterName), "parameter " + parameterName);
  }

  private String requireValue(final String value, final String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Missing required ENA experiment " + fieldName + ".");
    }
    return value;
  }

  private String firstNonBlank(final String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
