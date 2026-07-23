/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.biosamples.service;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.Attribute;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BioSample;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.Relationship;
import com.elixir.mars.repository.ReceiptAccessionsMap;
import com.elixir.mars.repository.models.isa.*;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class BioSamplesSubmitter {
  private static final String ORGANISM_ATTRIBUTE = "organism";
  private static final String TAX_ID_ATTRIBUTE = "tax_id";
  private static final String COLLECTION_DATE_ATTRIBUTE = "collection date";
  private static final String GEOGRAPHIC_LOCATION_ATTRIBUTE =
      "geographic location (country and/or sea)";

  public BiosampleAccessionsMap createBioSamples(
      final List<Study> studies, final String webinToken) {
    final BiosampleAccessionsMap typeToBioSamplesAccessionMap = new BiosampleAccessionsMap();

    try {
      typeToBioSamplesAccessionMap.sourceAccessionsMap.isaItemName = Source.Fields.name;
      typeToBioSamplesAccessionMap.sampleAccessionsMap.isaItemName = Sample.Fields.name;

      for (final Study study : studies) {
        final Map<String, String> characteristicKeyLookup = buildCharacteristicKeyLookup(study);
        final Map<String, Map<String, String>> protocolToParameterNameMap =
            buildProtocolToParameterNameLookup(study);
        // Keep the source lookup scoped to the study; ISA material ids are not safely global.
        final Map<String, BioSample> sourceBioSamplesById =
            createSourceBioSamplesById(study, characteristicKeyLookup, webinToken);

        addSourceAccessions(typeToBioSamplesAccessionMap, sourceBioSamplesById.values());
        typeToBioSamplesAccessionMap.studyAccessionsMap =
            new ReceiptAccessionsMap(Study.Fields.title, study.getTitle());

        for (final Sample sample : study.getMaterials().getSamples()) {
          final ProcessSequence sampleCollectionProcess =
              findProcessByOutputId(study.getProcessSequence(), sample.getId());
          final BioSample sourceBioSample =
              findSourceBioSampleForSample(sample, sampleCollectionProcess, sourceBioSamplesById);
          final BioSample persistedChildSample =
              this.createAndUpdateChildSampleWithRelationship(
                  sample,
                  sourceBioSample,
                  webinToken,
                  characteristicKeyLookup,
                  protocolToParameterNameMap,
                  sampleCollectionProcess);

          if (persistedChildSample != null) {
            typeToBioSamplesAccessionMap.sampleAccessionsMap.accessionMap.put(
                persistedChildSample.getName(), persistedChildSample.getAccession());
          }
        }
      }
    } catch (final Exception e) {
      throw new RuntimeException("Failed to parse ISA Json and create samples in BioSamples", e);
    }

    return typeToBioSamplesAccessionMap;
  }

  private BioSample createAndUpdateChildSampleWithRelationship(
      final Sample sample,
      final BioSample sourceBioSample,
      final String webinToken,
      final Map<String, String> characteristicKeyLookup,
      final Map<String, Map<String, String>> protocolToParameterNameMap,
      final ProcessSequence sampleCollectionProcess) {
    final SortedSet<Attribute> childSampleAttributes =
        buildChildSampleAttributes(
            sample,
            sourceBioSample,
            characteristicKeyLookup,
            protocolToParameterNameMap,
            sampleCollectionProcess);
    final BioSample bioSample =
        new BioSample.Builder(sample.getName() != null ? sample.getName() : "child_sample")
            .withRelease(Instant.now())
            .withAttributes(childSampleAttributes)
            .build();
    try {
      final BioSample persistedBioSample = this.createSampleInBioSamples(bioSample, webinToken);

      if (persistedBioSample != null) {
        final BioSample sampleWithRelationship =
            BioSample.Builder.fromSample(persistedBioSample)
                .withRelationships(
                    Collections.singletonList(
                        Relationship.build(
                            persistedBioSample.getAccession(),
                            "derived from",
                            sourceBioSample.getAccession())))
                .build();

        return this.updateSampleWithRelationshipsToBioSamples(sampleWithRelationship, webinToken);
      } else {
        throw new RuntimeException("Failed to handle child samples");
      }
    } catch (final Exception e) {
      throw new RuntimeException("Failed to handle child samples", e);
    }
  }

  private SortedSet<Attribute> buildChildSampleAttributes(
      final Sample sample,
      final BioSample sourceBioSample,
      final Map<String, String> characteristicKeyLookup,
      final Map<String, Map<String, String>> protocolToParameterNameMap,
      final ProcessSequence sampleCollectionProcess) {
    final SortedSet<Attribute> childSampleAttributes =
        buildAttributesFromCharacteristics(sample.getCharacteristics(), characteristicKeyLookup);

    // Direct sample characteristics are the most specific metadata; process parameters only fill
    // BioSample attributes that are not already present on the output sample.
    addAttributesIfMissing(
        childSampleAttributes,
        buildAttributesFromProcessParameters(
            sampleCollectionProcess, protocolToParameterNameMap));

    synchronizeSharedAttribute(childSampleAttributes, sourceBioSample, ORGANISM_ATTRIBUTE);
    synchronizeSharedAttribute(childSampleAttributes, sourceBioSample, TAX_ID_ATTRIBUTE);
    copySourceAttributeIfMissing(
        childSampleAttributes, sourceBioSample, COLLECTION_DATE_ATTRIBUTE);
    copySourceAttributeIfMissing(
        childSampleAttributes, sourceBioSample, GEOGRAPHIC_LOCATION_ATTRIBUTE);

    return childSampleAttributes;
  }

  private Map<String, BioSample> createSourceBioSamplesById(
      final Study study,
      final Map<String, String> characteristicKeyLookup,
      final String webinToken) {
    final Map<String, BioSample> biosamplesBySourceId = new LinkedHashMap<>();

    for (final Source source : study.getMaterials().getSources()) {
      final SortedSet<Attribute> attributes =
          buildAttributesFromCharacteristics(source.getCharacteristics(), characteristicKeyLookup);
      final BioSample sourceSample =
          new BioSample.Builder(source.getName())
              .withRelease(Instant.now())
              .withAttributes(attributes)
              .build();
      biosamplesBySourceId.put(
          source.getId(), this.createSampleInBioSamples(sourceSample, webinToken));
    }

    return biosamplesBySourceId;
  }

  private void addSourceAccessions(
      final BiosampleAccessionsMap typeToBioSamplesAccessionMap,
      final Collection<BioSample> sourceBioSamples) {
    sourceBioSamples.forEach(
        sourceBioSample ->
            typeToBioSamplesAccessionMap.sourceAccessionsMap.accessionMap.put(
                sourceBioSample.getName(), sourceBioSample.getAccession()));
  }

  /**
   * Resolves the BioSamples source record for a study sample.
   *
   * <p>ISA exports can express source-to-sample lineage either directly through
   * {@code sample.derivesFrom} or indirectly through the sample collection process input. When a
   * study has a single source, that source is used as a final fallback.
   */
  private BioSample findSourceBioSampleForSample(
      final Sample sample,
      final ProcessSequence sampleCollectionProcess,
      final Map<String, BioSample> sourceBioSamplesById) {
    // Prefer explicit material lineage, but fall back to the process input for ISA exports that
    // only carry the source-to-sample relationship in processSequence.
    final Optional<String> sourceId =
        findSourceIdFromSampleDerivesFrom(sample, sourceBioSamplesById)
            .or(() -> findSourceIdFromProcessInputs(sampleCollectionProcess, sourceBioSamplesById));
    if (sourceId.isPresent()) {
      return sourceBioSamplesById.get(sourceId.get());
    }

    if (sourceBioSamplesById.size() == 1) {
      return sourceBioSamplesById.values().iterator().next();
    }

    throw new IllegalArgumentException(
        "Could not resolve source BioSample for sample " + sample.getId() + ".");
  }

  /**
   * Finds a source ID from the sample's explicit ISA material lineage.
   *
   * <p>The {@code derivesFrom} entries store IDs, so they are resolved against the source
   * BioSamples that were already created for the current study.
   */
  private Optional<String> findSourceIdFromSampleDerivesFrom(
      final Sample sample, final Map<String, BioSample> sourceBioSamplesById) {
    if (sample == null || sample.getDerivesFrom() == null) {
      return Optional.empty();
    }

    return sample.getDerivesFrom().stream()
        .filter(Objects::nonNull)
        .map(DerivesFrom::getId)
        .filter(sourceBioSamplesById::containsKey)
        .findFirst();
  }

  /**
   * Finds a source ID from the inputs of the process that produced the sample.
   *
   * <p>Some ISA exports keep source-to-sample lineage in {@code processSequence} instead of
   * {@code sample.derivesFrom}, so this is the fallback lookup for that representation.
   */
  private Optional<String> findSourceIdFromProcessInputs(
      final ProcessSequence processSequence, final Map<String, BioSample> sourceBioSamplesById) {
    if (processSequence == null || processSequence.getInputs() == null) {
      return Optional.empty();
    }

    return processSequence.getInputs().stream()
        .filter(Objects::nonNull)
        .map(Input::getId)
        .filter(sourceBioSamplesById::containsKey)
        .findFirst();
  }

  /**
   * Finds the process that produced the given sample output.
   *
   * <p>BioSamples uses this to locate the sample collection process, whose inputs can identify the
   * source material for the child sample.
   */
  private ProcessSequence findProcessByOutputId(
      final List<ProcessSequence> processSequence, final String outputId) {
    if (processSequence == null || outputId == null) {
      return null;
    }

    for (final ProcessSequence process : processSequence) {
      if (process == null || process.getOutputs() == null) {
        continue;
      }

      for (final Output output : process.getOutputs()) {
        if (output != null && outputId.equals(output.getId())) {
          return process;
        }
      }
    }

    return null;
  }

  private SortedSet<Attribute> buildAttributesFromCharacteristics(
      final List<Characteristic> characteristics, final Map<String, String> characteristicKeyLookup) {
    final SortedSet<Attribute> attributes = new TreeSet<>();

    if (characteristics == null) {
      return attributes;
    }

    characteristics.forEach(
        characteristic -> {
          if (characteristic == null
              || characteristic.getCategory() == null
              || characteristic.getValue() == null) {
            return;
          }

          final String key =
              getCharacteristicKey(characteristic.getCategory(), characteristicKeyLookup);
          final String value = characteristic.getValue().getAnnotationValue();

          if (key != null && value != null) {
            attributes.add(Attribute.build(key, value));
          }
        });

    return attributes;
  }

  private SortedSet<Attribute> buildAttributesFromProcessParameters(
      final ProcessSequence processSequence,
      final Map<String, Map<String, String>> protocolToParameterNameMap) {
    final SortedSet<Attribute> attributes = new TreeSet<>();

    if (processSequence == null
        || processSequence.getExecutesProtocol() == null
        || processSequence.getExecutesProtocol().getId() == null
        || processSequence.getParameterValues() == null) {
      return attributes;
    }

    final Map<String, String> parameterNameLookup =
        protocolToParameterNameMap.get(processSequence.getExecutesProtocol().getId());

    if (parameterNameLookup == null) {
      return attributes;
    }

    processSequence
        .getParameterValues()
        .forEach(
            parameterValue -> {
              if (parameterValue == null
                  || parameterValue.getCategory() == null
                  || parameterValue.getCategory().getId() == null
                  || parameterValue.getValue() == null) {
                return;
              }

              final String key = parameterNameLookup.get(parameterValue.getCategory().getId());
              final String value = parameterValue.getValue().getAnnotationValue();

              if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                attributes.add(Attribute.build(key, value));
              }
            });

    return attributes;
  }

  private Map<String, String> buildCharacteristicKeyLookup(final Study study) {
    final Map<String, String> keyLookup = new HashMap<>();

    if (study == null || study.getCharacteristicCategories() == null) {
      return keyLookup;
    }

    study.getCharacteristicCategories().forEach(
        characteristicCategory -> {
          if (characteristicCategory == null
              || characteristicCategory.getId() == null
              || characteristicCategory.getCharacteristicType() == null
              || characteristicCategory.getCharacteristicType().getAnnotationValue() == null
              || characteristicCategory.getCharacteristicType().getAnnotationValue().isBlank()) {
            return;
          }

          keyLookup.put(
              characteristicCategory.getId(),
              characteristicCategory.getCharacteristicType().getAnnotationValue());
        });

    return keyLookup;
  }

  private Map<String, Map<String, String>> buildProtocolToParameterNameLookup(final Study study) {
    final Map<String, Map<String, String>> protocolToParameterNameMap = new HashMap<>();

    if (study == null || study.getProtocols() == null) {
      return protocolToParameterNameMap;
    }

    study
        .getProtocols()
        .forEach(
            protocol -> {
              if (protocol == null || protocol.getId() == null) {
                return;
              }

              final Map<String, String> parameterNameMap = new HashMap<>();

              if (protocol.getParameters() != null) {
                protocol
                    .getParameters()
                    .forEach(parameter -> addParameterName(parameterNameMap, parameter));
              }

              protocolToParameterNameMap.put(protocol.getId(), parameterNameMap);
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

  private void addAttributesIfMissing(
      final SortedSet<Attribute> targetAttributes, final Collection<Attribute> sourceAttributes) {
    if (sourceAttributes == null) {
      return;
    }

    sourceAttributes.forEach(attribute -> addAttributeIfMissing(targetAttributes, attribute));
  }

  private void addAttributeIfMissing(
      final SortedSet<Attribute> attributes, final Attribute attribute) {
    if (attribute == null || attribute.getType() == null) {
      return;
    }

    final boolean alreadyPresent =
        attributes.stream()
            .anyMatch(
                existingAttribute ->
                    existingAttribute.getType().equalsIgnoreCase(attribute.getType()));

    if (!alreadyPresent) {
      attributes.add(attribute);
    }
  }

  private void copySourceAttributeIfMissing(
      final SortedSet<Attribute> childAttributes,
      final BioSample sourceBioSample,
      final String attributeType) {
    if (sourceBioSample.getAttributes() == null) {
      return;
    }

    sourceBioSample.getAttributes().stream()
        .filter(attribute -> attribute.getType().equalsIgnoreCase(attributeType))
        .findFirst()
        .ifPresent(attribute -> addAttributeIfMissing(childAttributes, attribute));
  }

  private void synchronizeSharedAttribute(
      final SortedSet<Attribute> childAttributes,
      final BioSample sourceBioSample,
      final String attributeType) {
    final Optional<Attribute> childAttribute =
        childAttributes.stream()
            .filter(attribute -> attribute.getType().equalsIgnoreCase(attributeType))
            .findFirst();

    final Optional<Attribute> sourceAttribute =
        sourceBioSample.getAttributes() == null
            ? Optional.empty()
            : sourceBioSample.getAttributes().stream()
                .filter(attribute -> attribute.getType().equalsIgnoreCase(attributeType))
                .findFirst();

    if (childAttribute.isPresent() && sourceAttribute.isEmpty()) {
      sourceBioSample.getAttributes().add(childAttribute.get());
      return;
    }

    if (childAttribute.isEmpty() && sourceAttribute.isPresent()) {
      childAttributes.add(sourceAttribute.get());
    }
  }

  /**
   * Uses the study-level ISA characteristic definition to resolve the human-readable attribute
   * name for a characteristic id.
   */
  private static String getCharacteristicKey(
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

  private static String characteristicCategoryIdToName(final String characteristicCategoryId) {
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

  protected BioSample updateSampleWithRelationshipsToBioSamples(
      final BioSample sampleWithRelationship, final String webinToken) {
    final RestTemplate restTemplate = new RestTemplate();
    final ResponseEntity<EntityModel<BioSample>> biosamplesResponse;

    try {
      final HttpHeaders headers = getHttpHeaders(webinToken);
      final HttpEntity<?> entity = new HttpEntity<>(sampleWithRelationship, headers);

      biosamplesResponse =
          restTemplate.exchange(
              "https://wwwdev.ebi.ac.uk/biosamples/samples/"
                  + sampleWithRelationship.getAccession(),
              HttpMethod.PUT,
              entity,
              new ParameterizedTypeReference<>() {});
      return biosamplesResponse.getBody().getContent();
    } catch (final Exception ex) {
      throw new RuntimeException("Failed to add relationships to child samples", ex);
    }
  }

  protected BioSample createSampleInBioSamples(final BioSample sample, final String webinToken) {
    final RestTemplate restTemplate = new RestTemplate();
    final ResponseEntity<EntityModel<BioSample>> biosamplesResponse;

    try {
      final HttpHeaders headers = getHttpHeaders(webinToken);
      final HttpEntity<?> entity = new HttpEntity<>(sample, headers);

      biosamplesResponse =
          restTemplate.exchange(
              "https://wwwdev.ebi.ac.uk/biosamples/samples/",
              HttpMethod.POST,
              entity,
              new ParameterizedTypeReference<>() {});

      return biosamplesResponse.getBody().getContent();
    } catch (final Exception ex) {
      throw new RuntimeException("Failed to create samples in BioSamples", ex);
    }
  }

  private static HttpHeaders getHttpHeaders(String webinToken) {
    final HttpHeaders headers =
        new HttpHeaders() {
          {
            final String authHeader = "Bearer " + webinToken;
            this.set("Authorization", authHeader);
          }
        };
    headers.add("Content-Type", "application/json;charset=UTF-8");
    headers.add("Accept", "application/json");
    return headers;
  }
}
