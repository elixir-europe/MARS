/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.IsaJsonGraphLookup;
import com.elixir.mars.repository.models.isa.Assay;
import com.elixir.mars.repository.models.isa.Category;
import com.elixir.mars.repository.models.isa.Characteristic;
import com.elixir.mars.repository.models.isa.CharacteristicCategory;
import com.elixir.mars.repository.models.isa.DataFile;
import com.elixir.mars.repository.models.isa.DerivesFrom;
import com.elixir.mars.repository.models.isa.Materials;
import com.elixir.mars.repository.models.isa.OtherMaterial;
import com.elixir.mars.repository.models.isa.Parameter;
import com.elixir.mars.repository.models.isa.ParameterValue;
import com.elixir.mars.repository.models.isa.ProcessSequence;
import com.elixir.mars.repository.models.isa.Sample;
import com.elixir.mars.repository.models.isa.Study;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WebinExperimentXmlCreator {
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
      final Assay assay,
      final DataFile dataFile) {
    final ProcessSequence sequencingProcess =
        IsaJsonGraphLookup.findProcessByOutputId(assay.getProcessSequence(), dataFile.getId());

    if (sequencingProcess == null) {
      return;
    }

    final OtherMaterial library =
        IsaJsonGraphLookup.findOtherMaterialFromProcessInput(
            sequencingProcess, assay.getMaterials());

    if (library == null || experimentSequence.containsKey(library.getId())) {
      return;
    }

    final String experimentId = library.getId() + "-" + randomSubmissionIdentifier;
    experimentSequence.put(library.getId(), experimentId);

    final List<OtherMaterial> materialLineage =
        findMaterialLineageToSample(library, assay.getMaterials());
    final ProcessSequence libraryConstructionProcess =
        IsaJsonGraphLookup.findProcessByOutputId(assay.getProcessSequence(), library.getId());
    final List<ProcessSequence> experimentProcesses =
        findExperimentProcesses(
            assay.getProcessSequence(),
            libraryConstructionProcess,
            sequencingProcess,
            materialLineage);

    createExperimentElement(
        root,
        study,
        library,
        materialLineage,
        assay,
        experimentProcesses,
        protocolToParameterNameMap,
        bioSampleAccessions,
        experimentId,
        randomSubmissionIdentifier);
  }

  /**
   * Collects the ISA processes that describe one ENA experiment.
   *
   * <p>An ENA experiment is built around the library material. The relevant ISA process context is
   * the process that constructed that library, the sequencing process that produced the data file,
   * and any upstream material-producing processes in the library-to-sample lineage.
   */
  private List<ProcessSequence> findExperimentProcesses(
      final List<ProcessSequence> assayProcesses,
      final ProcessSequence libraryConstructionProcess,
      final ProcessSequence sequencingProcess,
      final List<OtherMaterial> materialLineage) {
    final List<ProcessSequence> experimentProcesses = new ArrayList<>();
    final Set<String> processIds = new HashSet<>();

    addExperimentProcess(experimentProcesses, processIds, libraryConstructionProcess);
    addExperimentProcess(experimentProcesses, processIds, sequencingProcess);

    if (materialLineage == null) {
      return experimentProcesses;
    }

    for (final OtherMaterial material : materialLineage) {
      if (material == null || material.getId() == null) {
        continue;
      }

      addExperimentProcess(
          experimentProcesses,
          processIds,
          IsaJsonGraphLookup.findProcessByOutputId(assayProcesses, material.getId()));
    }

    return experimentProcesses;
  }

  private void addExperimentProcess(
      final List<ProcessSequence> experimentProcesses,
      final Set<String> processIds,
      final ProcessSequence process) {
    if (process == null || process.getId() == null || !processIds.add(process.getId())) {
      return;
    }

    experimentProcesses.add(process);
  }

  /**
   * Returns the library and upstream other materials until the chain reaches a study sample.
   *
   * <p>ISA material lineage is represented as {@code derivesFrom} ID references. This helper keeps
   * the ENA experiment metadata close to the full library lineage while stopping before biological
   * samples, which are handled through BioSamples accessions.
   */
  private List<OtherMaterial> findMaterialLineageToSample(
      final OtherMaterial material, final Materials materials) {
    final List<OtherMaterial> materialLineage = new ArrayList<>();
    final Map<String, OtherMaterial> otherMaterialsById =
        IsaJsonGraphLookup.buildOtherMaterialsById(materials);

    collectMaterialLineage(material, otherMaterialsById, materialLineage, new HashSet<>());

    return materialLineage;
  }

  /**
   * Recursively follows {@code derivesFrom} links between other materials.
   *
   * <p>The visited set prevents loops in malformed ISA graphs from causing unbounded recursion.
   */
  private void collectMaterialLineage(
      final OtherMaterial material,
      final Map<String, OtherMaterial> otherMaterialsById,
      final List<OtherMaterial> materialLineage,
      final Set<String> visitedIds) {
    if (material == null || material.getId() == null || !visitedIds.add(material.getId())) {
      return;
    }

    materialLineage.add(material);

    if (material.getDerivesFrom() == null) {
      return;
    }

    for (final DerivesFrom derivesFrom : material.getDerivesFrom()) {
      if (derivesFrom == null || derivesFrom.getId() == null) {
        continue;
      }

      collectMaterialLineage(
          otherMaterialsById.get(derivesFrom.getId()),
          otherMaterialsById,
          materialLineage,
          visitedIds);
    }
  }

  /** Creates one ENA EXPERIMENT element using ENA-native assay/process parameter names. */
  private void createExperimentElement(
      final Element root,
      final Study study,
      final OtherMaterial library,
      final List<OtherMaterial> materialLineage,
      final Assay assay,
      final List<ProcessSequence> experimentProcesses,
      final Map<String, Map<String, String>> protocolToParameterNameMap,
      final Map<String, String> bioSampleAccessions,
      final String experimentId,
      final String randomSubmissionIdentifier) {
    // Process parameters are closest to ENA submission fields; material characteristics fill gaps.
    final ExperimentMetadata experimentMetadata =
        new ExperimentMetadata(
            extractParameterValues(experimentProcesses, protocolToParameterNameMap),
            extractMaterialCharacteristicValues(
                materialLineage, buildCharacteristicKeyLookup(study, assay)));

    final Element experimentElement = root.addElement("EXPERIMENT");
    experimentElement.addAttribute("alias", experimentId);
    experimentElement
        .addElement("TITLE")
        .addText(
            firstNonBlank(
                experimentMetadata.get("EXPERIMENT_TITLE", "EXPERIMENT TITLE", "TITLE"),
                library.getName()));
    experimentElement
        .addElement("STUDY_REF")
        .addAttribute("refname", assay.getId() + "-" + randomSubmissionIdentifier);

    final Element designElement = experimentElement.addElement("DESIGN");
    designElement
        .addElement("DESIGN_DESCRIPTION")
        .addText(experimentMetadata.require("DESIGN_DESCRIPTION"));

    final String sampleAccession =
        resolveSampleAccessionForLibrary(study, assay, library, bioSampleAccessions);
    designElement
        .addElement("SAMPLE_DESCRIPTOR")
        .addAttribute("accession", requireValue(sampleAccession, "BioSamples sample accession"));

    final Element libraryDescriptorElement = designElement.addElement("LIBRARY_DESCRIPTOR");
    addLibraryDescriptor(libraryDescriptorElement, library, experimentMetadata);
    addPlatform(experimentElement, experimentMetadata);
  }

  /**
   * Resolves the BioSamples accession for the biological sample that the library derives from.
   */
  private String resolveSampleAccessionForLibrary(
      final Study study,
      final Assay assay,
      final OtherMaterial library,
      final Map<String, String> bioSampleAccessions) {
    if (study == null || study.getMaterials() == null || study.getMaterials().getSamples() == null) {
      return getBioSampleAccessionFallback(bioSampleAccessions, null);
    }

    final Map<String, Sample> samplesById = new HashMap<>();
    for (final Sample sample : study.getMaterials().getSamples()) {
      if (sample != null && sample.getId() != null) {
        samplesById.put(sample.getId(), sample);
      }
    }

    final Map<String, OtherMaterial> otherMaterialsById =
        IsaJsonGraphLookup.buildOtherMaterialsById(assay.getMaterials());

    final Sample sample =
        findSampleForMaterialId(library.getId(), samplesById, otherMaterialsById, new HashSet<>());
    if (sample == null) {
      return getBioSampleAccessionFallback(bioSampleAccessions, null);
    }

    final Map<String, String> characteristicKeyLookup = buildCharacteristicKeyLookup(study, assay);
    return firstNonBlank(
        getCharacteristicAnnotation(sample.getCharacteristics(), characteristicKeyLookup),
        getBioSampleAccessionFallback(bioSampleAccessions, sample));
  }

  private String getBioSampleAccessionFallback(
      final Map<String, String> bioSampleAccessions, final Sample sample) {
    if (bioSampleAccessions == null || bioSampleAccessions.isEmpty()) {
      return "";
    }

    if (sample != null) {
      final String sampleAccession =
          firstNonBlank(
              getBioSampleAccessionByKey(bioSampleAccessions, sample.getId()),
              getBioSampleAccessionByKey(bioSampleAccessions, sample.getName()));
      if (sampleAccession != null) {
        return sampleAccession;
      }
    }

    return firstNonBlank(bioSampleAccessions.get("SAMPLE"), bioSampleAccessions.get("SOURCE"));
  }

  private String getBioSampleAccessionByKey(
      final Map<String, String> bioSampleAccessions, final String key) {
    return key == null ? null : bioSampleAccessions.get(key);
  }

  /**
   * Finds the biological sample at the start of an other-material lineage.
   *
   * <p>Libraries and other materials can derive from other materials before eventually reaching a
   * study sample. This resolves those ID links recursively so the experiment can reference the
   * correct BioSamples accession.
   */
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

  private Map<String, String> buildCharacteristicKeyLookup(final Study study, final Assay assay) {
    final Map<String, String> keyLookup = new HashMap<>();

    if (study != null) {
      addCharacteristicCategories(keyLookup, study.characteristicCategories);
    }

    if (assay != null) {
      addCharacteristicCategories(keyLookup, assay.characteristicCategories);
    }

    return keyLookup;
  }

  private void addCharacteristicCategories(
      final Map<String, String> keyLookup,
      final List<CharacteristicCategory> characteristicCategories) {
    if (characteristicCategories == null) {
      return;
    }

    for (CharacteristicCategory characteristicCategory : characteristicCategories) {
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

      final String characteristicName =
          getCharacteristicName(characteristic.category, characteristicKeyLookup);

      if (metadataKeyMatches(characteristicName, "accession") && characteristic.value != null) {
        return characteristic.value.annotationValue;
      }
    }

    return "";
  }

  private Map<String, String> extractMaterialCharacteristicValues(
      final List<OtherMaterial> materialLineage,
      final Map<String, String> characteristicKeyLookup) {
    final Map<String, String> characteristicValuesByName = new HashMap<>();

    if (materialLineage == null) {
      return characteristicValuesByName;
    }

    for (final OtherMaterial material : materialLineage) {
      if (material == null || material.getCharacteristics() == null) {
        continue;
      }

      for (final Characteristic characteristic : material.getCharacteristics()) {
        if (characteristic == null
            || characteristic.getValue() == null
            || characteristic.getValue().getAnnotationValue() == null) {
          continue;
        }

        final String characteristicName =
            getCharacteristicName(characteristic.getCategory(), characteristicKeyLookup);
        if (characteristicName == null || characteristicName.isBlank()) {
          continue;
        }

        characteristicValuesByName.putIfAbsent(
            characteristicName, characteristic.getValue().getAnnotationValue());
      }
    }

    return characteristicValuesByName;
  }

  private String getCharacteristicName(
      final Category category, final Map<String, String> characteristicKeyLookup) {
    if (category == null) {
      return null;
    }

    if (category.getId() != null) {
      final String characteristicName = characteristicKeyLookup.get(category.getId());
      if (characteristicName != null && !characteristicName.isBlank()) {
        return characteristicName;
      }
    }

    if (category.getCharacteristicType() != null
        && category.getCharacteristicType().getAnnotationValue() != null
        && !category.getCharacteristicType().getAnnotationValue().isBlank()) {
      return category.getCharacteristicType().getAnnotationValue();
    }

    return characteristicCategoryIdToName(category.getId());
  }

  private String characteristicCategoryIdToName(final String characteristicCategoryId) {
    if (characteristicCategoryId == null || characteristicCategoryId.isBlank()) {
      return null;
    }

    final String prefix = "#characteristic_category/";
    final String characteristicName =
        characteristicCategoryId.startsWith(prefix)
            ? characteristicCategoryId.substring(prefix.length())
            : characteristicCategoryId;

    return characteristicName.replaceFirst("_[0-9]+$", "");
  }

  /**
   * Reads process parameter values by their declared ISA parameter names so ENA field names can be
   * used directly in the ISA without an extra mapping layer.
   */
  private Map<String, String> extractParameterValues(
      final List<ProcessSequence> processSequences,
      final Map<String, Map<String, String>> protocolToParameterNameMap) {
    final Map<String, String> parameterValuesByName = new HashMap<>();

    if (processSequences == null) {
      return parameterValuesByName;
    }

    for (final ProcessSequence processSequence : processSequences) {
      extractParameterValues(processSequence, protocolToParameterNameMap)
          .forEach(parameterValuesByName::putIfAbsent);
    }

    return parameterValuesByName;
  }

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
          || parameterValue.getValue().getAnnotationValue() == null
          || parameterValue.getValue().getAnnotationValue().isBlank()) {
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
      final ExperimentMetadata experimentMetadata) {
    addOptionalTextElement(
        libraryDescriptorElement,
        "LIBRARY_NAME",
        firstNonBlank(
            experimentMetadata.get("LIBRARY_NAME", "LIBRARY NAME"),
            library.getName()));
    libraryDescriptorElement
        .addElement("LIBRARY_STRATEGY")
        .addText(experimentMetadata.require("LIBRARY_STRATEGY"));
    libraryDescriptorElement
        .addElement("LIBRARY_SOURCE")
        .addText(experimentMetadata.require("LIBRARY_SOURCE"));
    libraryDescriptorElement
        .addElement("LIBRARY_SELECTION")
        .addText(experimentMetadata.require("LIBRARY_SELECTION"));

    final Element libraryLayoutElement = libraryDescriptorElement.addElement("LIBRARY_LAYOUT");
    final String layout = experimentMetadata.require("LIBRARY_LAYOUT");
    final Element layoutElement = libraryLayoutElement.addElement(layout);

    if ("PAIRED".equals(layout)) {
      addOptionalAttribute(
          layoutElement, "NOMINAL_LENGTH", experimentMetadata.get("NOMINAL_LENGTH"));
      addOptionalAttribute(
          layoutElement, "NOMINAL_SDEV", experimentMetadata.get("NOMINAL_SDEV"));
    }

    addOptionalTextElement(
        libraryDescriptorElement,
        "POOLING_STRATEGY",
        experimentMetadata.get("POOLING_STRATEGY"));
    addOptionalTextElement(
        libraryDescriptorElement,
        "LIBRARY_CONSTRUCTION_PROTOCOL",
        experimentMetadata.get("LIBRARY_CONSTRUCTION_PROTOCOL"));
  }

  /** Populates the ENA PLATFORM block directly from sequencing-process parameters. */
  private void addPlatform(
      final Element experimentElement, final ExperimentMetadata experimentMetadata) {
    final String platform = experimentMetadata.require("PLATFORM");
    final String instrumentModel = experimentMetadata.require("INSTRUMENT_MODEL");

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

  private String getMetadataValue(final Map<String, String> metadata, final String... metadataNames) {
    if (metadata == null || metadataNames == null) {
      return null;
    }

    for (final String metadataName : metadataNames) {
      final String metadataValue = metadata.get(metadataName);
      if (metadataValue != null && !metadataValue.isBlank()) {
        return metadataValue;
      }
    }

    for (final String metadataName : metadataNames) {
      for (final Map.Entry<String, String> metadataEntry : metadata.entrySet()) {
        if (metadataEntry.getValue() == null || metadataEntry.getValue().isBlank()) {
          continue;
        }

        if (metadataKeyMatches(metadataEntry.getKey(), metadataName)) {
          return metadataEntry.getValue();
        }
      }
    }

    return null;
  }

  private boolean metadataKeyMatches(final String metadataKey, final String expectedMetadataKey) {
    final String normalizedMetadataKey = normalizeMetadataKey(metadataKey);
    return !normalizedMetadataKey.isBlank()
        && normalizedMetadataKey.equals(normalizeMetadataKey(expectedMetadataKey));
  }

  private String normalizeMetadataKey(final String metadataKey) {
    if (metadataKey == null) {
      return "";
    }

    return metadataKey.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }

  private class ExperimentMetadata {
    private final Map<String, String> processMetadata;
    private final Map<String, String> materialMetadata;

    private ExperimentMetadata(
        final Map<String, String> processMetadata, final Map<String, String> materialMetadata) {
      this.processMetadata = processMetadata;
      this.materialMetadata = materialMetadata;
    }

    private String get(final String... metadataNames) {
      return firstNonBlank(
          getMetadataValue(processMetadata, metadataNames),
          getMetadataValue(materialMetadata, metadataNames));
    }

    private String require(final String metadataName) {
      return requireValue(get(metadataName), "metadata " + metadataName);
    }
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
