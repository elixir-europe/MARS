/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.biosamples.service;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.Attribute;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BioSample;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.Relationship;
import com.elixir.biohackaton.ISAToSRA.receipt.ReceiptAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
  public BiosampleAccessionsMap createBioSamples(
      final List<Study> studies, final String webinToken) {
    final BiosampleAccessionsMap typeToBioSamplesAccessionMap = new BiosampleAccessionsMap();

    try {
      /*TODO: check if it is guaranteed to have one source */
      final BioSample sourceBioSample = this.createSourceBioSample(studies, webinToken).get(0);

      typeToBioSamplesAccessionMap.sourceAccessionsMap.keyName = Source.Fields.name;
      typeToBioSamplesAccessionMap.sourceAccessionsMap.accessionMap.put(
          sourceBioSample.getName(), sourceBioSample.getAccession());

      studies.forEach(
          study -> {
            typeToBioSamplesAccessionMap.studyAccessionsMap =
                new ReceiptAccessionsMap(Study.Fields.title, study.getTitle());

            study
                .getMaterials()
                .getSamples()
                .forEach(
                    sample -> {
                      final BioSample persistedChildSample =
                          this.createAndUpdateChildSampleWithRelationship(
                              sample, sourceBioSample, webinToken);

                      if (persistedChildSample != null) {
                        typeToBioSamplesAccessionMap.sampleAccessionsMap.keyName =
                            Sample.Fields.name;
                        typeToBioSamplesAccessionMap.sampleAccessionsMap.accessionMap.put(
                            persistedChildSample.getName(), persistedChildSample.getAccession());
                      }
                    });
          });
    } catch (final Exception e) {
      throw new RuntimeException("Failed to parse ISA Json and create samples in BioSamples", e);
    }

    return typeToBioSamplesAccessionMap;
  }

  private BioSample createAndUpdateChildSampleWithRelationship(
      final Sample sample, final BioSample sourceBioSample, final String webinToken) {
    final SortedSet<Attribute> sourceBioSampleAttributes = sourceBioSample.getAttributes();
    final BioSample bioSample;

    sourceBioSampleAttributes.removeIf(
        attribute -> attribute.getType().equalsIgnoreCase("SRA accession"));
    bioSample =
        new BioSample.Builder(sample.getName() != null ? sample.getName() : "child_sample")
            .withRelease(Instant.now())
            .withAttributes(sourceBioSampleAttributes)
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

  private List<BioSample> createSourceBioSample(
      final List<Study> studies, final String webinToken) {
    final List<Attribute> attributes = new ArrayList<>();
    List<BioSample> biosamples = new ArrayList<>();

    studies.forEach(
        study ->
            study
                .getMaterials()
                .getSources()
                .forEach(
                    source -> {
                      source
                          .getCharacteristics()
                          .forEach(
                              characteristic -> {
                                if (characteristic.getCategory().getId() != null) {
                                  final String rawId = characteristic.getCategory().getId();
                                  final String extractedKey = extractCharacteristicKey(rawId);

                                  attributes.add(
                                      Attribute.build(
                                          extractedKey,
                                          characteristic.getValue().getAnnotationValue()));
                                }
                              });
                      final BioSample sourceSample =
                          new BioSample.Builder(source.getName())
                              .withRelease(Instant.now())
                              .withAttributes(attributes)
                              .build();
                      biosamples.add(this.createSampleInBioSamples(sourceSample, webinToken));
                    }));

    return biosamples;
  }

  private static Characteristic getBioSampleAccessionCharacteristic(
      AtomicReference<BioSample> biosample) {
    final Characteristic biosampleAccessionCharacteristic = new Characteristic();
    final Category biosampleAccessionCategory = new Category();
    final Value biosampleAccessionValue = new Value();

    biosampleAccessionCategory.setId("#characteristic_category/accession");
    biosampleAccessionValue.setAnnotationValue(biosample.get().getAccession());

    biosampleAccessionCharacteristic.setCategory(biosampleAccessionCategory);
    biosampleAccessionCharacteristic.setValue(biosampleAccessionValue);

    return biosampleAccessionCharacteristic;
  }

  /**
   * Extracts a concise key from a characteristic category id. Example:
   * "#characteristic_category/collection_date_323" -> "collection_date"
   * "#characteristic_category/isolation_source_324" -> "isolation_source" Falls back to the
   * original id if it doesn't match the expected pattern.
   */
  private static String extractCharacteristicKey(final String categoryId) {
    if (categoryId == null) {
      return null;
    }

    final String prefix = "#characteristic_category/";

    if (!categoryId.startsWith(prefix)) {
      return categoryId;
    }

    // Strip a trailing underscore followed by digits, if present
    return categoryId.substring(prefix.length()).replaceFirst("_[0-9]+$", "");
  }

  private BioSample updateSampleWithRelationshipsToBioSamples(
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

  private BioSample createSampleInBioSamples(final BioSample sample, final String webinToken) {
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
