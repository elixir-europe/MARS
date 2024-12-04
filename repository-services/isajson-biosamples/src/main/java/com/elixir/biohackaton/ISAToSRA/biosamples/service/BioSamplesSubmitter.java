/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.biosamples.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.Attribute;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BioSample;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.biosamples.model.Relationship;
import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptException;
import com.elixir.biohackaton.ISAToSRA.receipt.ReceiptAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Category;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Characteristic;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Sample;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Source;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Value;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BioSamplesSubmitter {

  @Autowired
  private MarsReceiptService marsReceiptService;

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

      typeToBioSamplesAccessionMap.sourceAccessionsMap.isaItemName = Source.Fields.name;
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
                        try {
                          final BioSample persistedChildSample = this.createAndUpdateChildSampleWithRelationship(
                              sample,
                              sourceBioSample.getAccession(),
                              finalSourceBioSampleOrganismAttribute.getValue(),
                              webinToken);

                          if (persistedChildSample != null) {
                            final Characteristic biosampleAccessionCharacteristic = getBioSampleAccessionCharacteristic(
                                new AtomicReference<>(persistedChildSample));
                            final ArrayList<Characteristic> sampleCharacteristics = sample
                                .getCharacteristics() != null
                                    ? sample.getCharacteristics()
                                    : new ArrayList<>();
                            sampleCharacteristics.add(biosampleAccessionCharacteristic);

                            typeToBioSamplesAccessionMap.sampleAccessionsMap.isaItemName = Sample.Fields.name;
                            typeToBioSamplesAccessionMap.sampleAccessionsMap.accessionMap.put(
                                persistedChildSample.getName(),
                                persistedChildSample.getAccession());
                          }
                        } catch (Exception e) {
                          throw new MarsReceiptException(e,
                              "Failed to parse ISA Json and create samples in BioSamples (SAMPLE)",
                              marsReceiptService.getSampleMarsPath(
                                  Map.entry(Study.Fields.title, study.title),
                                  Map.entry(Sample.Fields.id, sample.id)));
                        }
                      });
            });
      }
    } catch (final Exception e) {
      throw new MarsReceiptException(e, "Failed to parse ISA Json and create samples in BioSamples");
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
      throw new MarsReceiptException(e, "Failed to handle child samples");
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
    } catch (final Exception e) {
      throw new MarsReceiptException(e, "Failed to add relationships to child samples");
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
    } catch (final Exception e) {
      throw new MarsReceiptException(e, "Failed to create samples in BioSamples");
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
