/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import com.elixir.biohackaton.ISAToSRA.receipt.marsmodel.*;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.service.MarsReceiptService;
import com.elixir.biohackaton.ISAToSRA.sra.service.ReceiptConversionService;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinExperimentXmlCreator;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinHttpSubmissionService;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinProjectXmlCreator;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinRunXmlCreator;
import com.elixir.biohackaton.ISAToSRA.sra.service.WebinStudyXmlCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class WebinIsaToXmlSubmissionController {
  @Autowired private WebinStudyXmlCreator webinStudyXmlCreator;

  @Autowired private WebinExperimentXmlCreator webinExperimentXmlCreator;

  @Autowired private WebinProjectXmlCreator webinProjectXmlCreator;

  @Autowired private WebinRunXmlCreator webinRunXmlCreator;

  @Autowired private WebinHttpSubmissionService webinHttpSubmissionService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ReceiptConversionService receiptConversionService;

  @Autowired private MarsReceiptService marsReceiptService;

  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Ok"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "400", description = "Bad request"),
        @ApiResponse(responseCode = "408", description = "Request Timeout"),
        @ApiResponse(responseCode = "415", description = "Unsupported media type")
      })
  @PostMapping(
      value = "/submit",
      consumes = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE})
  public String performSubmissionToEna(
      @RequestBody final String submissionPayload,
      @RequestParam(value = "webinUserName") String webinUserName,
      @RequestParam(value = "webinPassword") String webinPassword)
      throws Exception {
    if (webinUserName == null) {
      throw new RuntimeException("Webin Authentication username is not provided");
    }

    if (webinPassword == null) {
      throw new RuntimeException("Webin Authentication password is not provided");
    }

    final IsaJson isaJson = this.objectMapper.readValue(submissionPayload, IsaJson.class);

    final Document document = DocumentHelper.createDocument();
    final Element webinElement = startPreparingWebinV2SubmissionXml(document);
    final String randomSubmissionIdentifier = String.valueOf(Math.random());

    final List<Study> studies = getStudies(isaJson);
    this.webinStudyXmlCreator.createENAStudySetElement(
        webinElement, studies, randomSubmissionIdentifier);

    final Map<String, String> typeToBioSamplesAccessionMap = getBiosamples(studies);
    final Map<String, String> experimentSequenceMap =
        this.webinExperimentXmlCreator.createENAExperimentSetElement(
            typeToBioSamplesAccessionMap, webinElement, studies, randomSubmissionIdentifier);

    this.webinRunXmlCreator.createENARunSetElement(
        webinElement, studies, experimentSequenceMap, randomSubmissionIdentifier);
    this.webinProjectXmlCreator.createENAProjectSetElement(
        webinElement, getInvestigation(isaJson), randomSubmissionIdentifier);

    final OutputFormat format = OutputFormat.createPrettyPrint();
    final XMLWriter writer = new XMLWriter(System.out, format);

    writer.write(document);

    final String receiptXml =
        webinHttpSubmissionService.performWebinSubmission(
            webinUserName, document.asXML(), webinPassword);
    final Receipt receiptJson = receiptConversionService.readReceiptXml(receiptXml);
    System.out.println(receiptXml);
    System.out.println(receiptJson);
    final MarsReceipt marsReceipt = marsReceiptService.convertReceiptToMars(receiptJson, isaJson);

    return marsReceiptService.convertMarsReceiptToJson(marsReceipt);
  }

  public List<Study> getStudies(final IsaJson isaJson) {
    try {
      return isaJson.getInvestigation().getStudies();
    } catch (final Exception e) {
      log.info("Failed to parse ISA JSON and get studies", e);
    }

    return null;
  }

  public Investigation getInvestigation(final IsaJson isaJson) {
    try {
      return isaJson.getInvestigation();
    } catch (final Exception e) {
      log.info("Failed to parse ISA JSON and get studies", e);
    }

    return null;
  }

  public Map<String, String> getBiosamples(List<Study> studies) {
    HashMap<String, String> biosamples = new HashMap<>();
    for (Study study : studies) {
      for (Source source : study.materials.sources) {
        String sourceAccession = getCharacteresticAnnotation(source.characteristics);
        if (!sourceAccession.isBlank()) {
          biosamples.put("SOURCE", sourceAccession);
          return biosamples;
        }
      }
    }

    return biosamples;
  }

  private String getCharacteresticAnnotation(List<Characteristic> characteristics) {
    List<Characteristic> filteredCharacteristics =
        characteristics.stream()
            .filter(
                characteristic ->
                    characteristic.category.id.contains("#characteristic_category/accession"))
            .collect(Collectors.toList());

    if (filteredCharacteristics.isEmpty()) {
      log.error("No accession found in the characteristics");
      throw new RuntimeException("No accession found in the characteristics");
    }

    if (filteredCharacteristics.size() > 1) {
      log.error("More than one accession found in the characteristics");
      throw new RuntimeException("Too many accessions found in the characteristics");
    }
    return filteredCharacteristics.get(0).value.annotationValue;
  }

  private static Element startPreparingWebinV2SubmissionXml(Document document) {
    final Element webinElement = document.addElement("WEBIN");
    final Element submissionElement = webinElement.addElement("SUBMISSION");
    final Element actionsElement = submissionElement.addElement("ACTIONS");
    final Element actionElement = actionsElement.addElement("ACTION");

    actionElement.addElement("ADD");

    return webinElement;
  }
}
