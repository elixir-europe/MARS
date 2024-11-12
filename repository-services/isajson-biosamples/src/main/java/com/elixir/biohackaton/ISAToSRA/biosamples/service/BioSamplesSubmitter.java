/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.biosamples.service;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.Attribute;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.Relationship;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BioSample;
import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptException;
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

  public BiosampleAccessionsMap createBioSamples(final List<Study> studies, final String webinToken) {
    final BiosampleAccessionsMap typeToBioSamplesAccessionMap = new BiosampleAccessionsMap();

    try {
      final BioSample sourceBioSample = this.createSourceBioSample(studies, webinToken);
      Attribute sourceBioSampleOrganismAttribute = null;

      for (final Attribute attribute : sourceBioSample.getAttributes()) {
        if (attribute.getType().equalsIgnoreCase("organism")) {
          sourceBioSampleOrganismAttribute = attribute;
        }
      }

      typeToBioSamplesAccessionMap.sourceAccessionsMap.keyName = Source.Fields.name;
      typeToBioSamplesAccessionMap.sourceAccessionsMap.accessionMap.put(
          sourceBioSample.getName(),
          sourceBioSample.getAccession());

      if (sourceBioSampleOrganismAttribute != null) {
        final Attribute finalSourceBioSampleOrganismAttribute = sourceBioSampleOrganismAttribute;

        studies.forEach(
            study -> {
              typeToBioSamplesAccessionMap.studyAccessionsMap = new ReceiptAccessionsMap(
                  Study.Fields.title,
                  study.getTitle());

              study
                  .getMaterials()
                  .getSamples()
                  .forEach(
                      sample -> {
                        final BioSample persistedChildSample = this.createAndUpdateChildSampleWithRelationship(
                            sample,
                            sourceBioSample.getAccession(),
                            finalSourceBioSampleOrganismAttribute.getValue(),
                            webinToken);

                        if (persistedChildSample != null) {
                          final Characteristic biosampleAccessionCharacteristic = getBioSampleAccessionCharacteristic(
                              new AtomicReference<>(persistedChildSample));
                          final ArrayList<Characteristic> sampleCharacteristics = sample.getCharacteristics() != null
                              ? sample.getCharacteristics()
                              : new ArrayList<>();
                          sampleCharacteristics.add(biosampleAccessionCharacteristic);

                          typeToBioSamplesAccessionMap.sampleAccessionsMap.keyName = Sample.Fields.name;
                          typeToBioSamplesAccessionMap.sampleAccessionsMap.accessionMap.put(
                              persistedChildSample.getName(),
                              persistedChildSample.getAccession());
                        }
                      });
            });
      }
    } catch (final Exception e) {
      throw new MarsReceiptException("Failed to parse ISA Json and create samples in BioSamples", e);
    }

    return typeToBioSamplesAccessionMap;
  }

  private BioSample createAndUpdateChildSampleWithRelationship(
      final Sample sample,
      final String sourceBioSampleAccession,
      final String parentSampleOrganism,
      final String webinToken) {
    final BioSample bioSample = new BioSample.Builder(sample.getName() != null ? sample.getName() : "child_sample")
        .withRelease(Instant.now())
        .withAttributes(
            List.of(Attribute.build("organism", parentSampleOrganism),
                    Attribute.build("collection date", "not provided"),
                    Attribute.build("geographic location (country and/or sea)", "not provided")))
        .build();
    try {
      final EntityModel<BioSample> persistedSampleEntity = this.createSampleInBioSamples(bioSample, webinToken);

      if (persistedSampleEntity != null) {
        final BioSample persistedBioSample = persistedSampleEntity.getContent();

        if (persistedBioSample != null) {
          final BioSample sampleWithRelationship = BioSample.Builder.fromSample(persistedBioSample)
              .withRelationships(
                  Collections.singletonList(
                      Relationship.build(
                          persistedBioSample.getAccession(),
                          "derived from",
                          sourceBioSampleAccession)))
              .build();

          return this.updateSampleWithRelationshipsToBioSamples(sampleWithRelationship, webinToken);
        } else {
          return null;
        }
      } else {
        return null;
      }
    } catch (final Exception e) {
      throw new MarsReceiptException("Failed to handle child samples", e);
    }
  }

  private BioSample createSourceBioSample(final List<Study> studies, final String webinToken) {
    final AtomicReference<Attribute> organismAttribute = new AtomicReference<>(Attribute.build("", ""));
    final AtomicReference<BioSample> sourceBioSample = new AtomicReference<>(null);

    studies.forEach(
        study -> study
            .getMaterials()
            .getSources()
            .forEach(
                source -> {
                  final ArrayList<Characteristic> sourceCharacteristics = source.getCharacteristics();

                  sourceCharacteristics.forEach(
                      characteristic -> {
                        if (characteristic.getCategory().getId().contains("organism")) {
                          organismAttribute.set(
                              Attribute.build(
                                  "organism", characteristic.getValue().getAnnotationValue()));
                        }
                      });

                  final BioSample sourceSample = new BioSample.Builder(source.getName())
                      .withRelease(Instant.now())
                      .withAttributes(List.of(organismAttribute.get(),
                              Attribute.build("collection date", "not provided"),
                              Attribute.build("geographic location (country and/or sea)", "not provided")))
                      .build();
                  final EntityModel<BioSample> persistedParentSampleEntity = this.createSampleInBioSamples(sourceSample,
                      webinToken);

                  if (persistedParentSampleEntity != null) {
                    sourceBioSample.set(persistedParentSampleEntity.getContent());

                    final Characteristic biosampleAccessionCharacteristic = getBioSampleAccessionCharacteristic(
                        sourceBioSample);

                    sourceCharacteristics.add(biosampleAccessionCharacteristic);
                    source.setCharacteristics(sourceCharacteristics);
                  } else {
                    throw new MarsReceiptException("Failed to store source sample to BioSamples");
                  }
                }));

    return sourceBioSample.get();
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

  private BioSample updateSampleWithRelationshipsToBioSamples(
      final BioSample sampleWithRelationship, final String webinToken) {
    final RestTemplate restTemplate = new RestTemplate();
    final ResponseEntity<EntityModel<BioSample>> biosamplesResponse;

    try {
      final HttpHeaders headers = getHttpHeaders(webinToken);
      final HttpEntity<?> entity = new HttpEntity<>(sampleWithRelationship, headers);

      biosamplesResponse = restTemplate.exchange(
          "https://wwwdev.ebi.ac.uk/biosamples/samples/"
              + sampleWithRelationship.getAccession(),
          HttpMethod.PUT,
          entity,
          new ParameterizedTypeReference<>() {
          });
      return biosamplesResponse.getBody().getContent();
    } catch (final Exception ex) {
      throw new MarsReceiptException("Failed to add relationships to child samples", ex);
    }
  }

  private EntityModel<BioSample> createSampleInBioSamples(
      final BioSample sample, final String webinToken) {
    final RestTemplate restTemplate = new RestTemplate();
    final ResponseEntity<EntityModel<BioSample>> biosamplesResponse;

    try {
      final HttpHeaders headers = getHttpHeaders(webinToken);
      final HttpEntity<?> entity = new HttpEntity<>(sample, headers);

      biosamplesResponse = restTemplate.exchange(
          "https://wwwdev.ebi.ac.uk/biosamples/samples/",
          HttpMethod.POST,
          entity,
          new ParameterizedTypeReference<>() {
          });

      return biosamplesResponse.getBody();
    } catch (final Exception ex) {
      throw new MarsReceiptException("Failed to create samples in BioSamples", ex);
    }
  }

  private static HttpHeaders getHttpHeaders(String webinToken) {
    final HttpHeaders headers = new HttpHeaders() {
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
