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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * REST Controller for converting ISA-JSON to ENA XML and submitting to ENA Webin.
 *
 * <p>This controller orchestrates the conversion of ISA-JSON format to ENA submission XML format
 * and handles the submission workflow:
 *
 * <ol>
 *   <li>Parse ISA-JSON payload
 *   <li>Convert ISA-JSON elements to ENA XML (Study, Project, Experiment, Run)
 *   <li>Submit XML to ENA Webin API
 *   <li>Convert ENA receipt to MARS receipt format
 *   <li>Return MARS receipt as JSON
 * </ol>
 *
 * <p>The conversion follows a bottom-up approach for Experiments and Runs (starting from DataFiles
 * and working up), while Study and Project use a top-down approach.
 */
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
  /**
   * Main endpoint for submitting ISA-JSON to ENA.
   *
   * <p>Converts ISA-JSON to ENA XML format and submits to ENA Webin API. The conversion process:
   *
   * <ol>
   *   <li>Parse ISA-JSON payload
   *   <li>Create ENA XML structure (WEBIN element with SUBMISSION)
   *   <li>Convert Study → ENA STUDY (top-down)
   *   <li>Get BioSamples accessions for source samples
   *   <li>Convert Library → ENA EXPERIMENT (bottom-up: DataFile → Library)
   *   <li>Convert DataFile → ENA RUN (bottom-up: DataFile → Experiment reference)
   *   <li>Convert Investigation → ENA PROJECT (top-down)
   *   <li>Submit XML to ENA Webin API
   *   <li>Convert ENA receipt to MARS receipt format
   * </ol>
   *
   * @param submissionPayload ISA-JSON string payload
   * @param webinUserName ENA Webin username for authentication
   * @param webinPassword ENA Webin password for authentication
   * @param bioSampleAccessions optional JSON string of BioSamples accessions map. Must have
   *     "SOURCE" as the key. Examples:
   *     <ul>
   *       <li>URL-encoded query param: {@code
   *           ?bioSampleAccessions=%7B%22SOURCE%22%3A%22SAMEA130793922%22%7D}
   *       <li>Raw JSON string: {@code {"SOURCE":"SAMEA130793922"}}
   *     </ul>
   *     If not provided, will be extracted from ISA-JSON.
   * @return MARS receipt as JSON string
   * @throws Exception if submission fails
   */
  @PostMapping(
      value = "/submit",
      consumes = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE})
  public String performSubmissionToEna(
      @RequestBody final String submissionPayload,
      @RequestParam(value = "webinUserName") String webinUserName,
      @RequestParam(value = "webinPassword") String webinPassword,
      @RequestParam(value = "bioSampleAccessions", required = false) String bioSampleAccessions)
      throws Exception {
    // Validate authentication parameters
    if (webinUserName == null) {
      throw new RuntimeException("Webin Authentication username is not provided");
    }

    if (webinPassword == null) {
      throw new RuntimeException("Webin Authentication password is not provided");
    }

    // Parse ISA-JSON payload
    final IsaJson isaJson = this.objectMapper.readValue(submissionPayload, IsaJson.class);

    // Initialize ENA XML document structure
    final Document document = DocumentHelper.createDocument();
    final Element webinElement = startPreparingWebinV2SubmissionXml(document);
    final String randomSubmissionIdentifier = String.valueOf(Math.random());

    // Step 1: Convert Study → ENA STUDY (top-down approach)
    final List<Study> studies = getStudies(isaJson);
    this.webinStudyXmlCreator.createENAStudySetElement(
        webinElement, studies, randomSubmissionIdentifier);

    // Step 2: Get BioSamples accessions for source samples (needed for EXPERIMENT)
    // Use provided bioSampleAccessions if available, otherwise extract from ISA-JSON
    final Map<String, String> typeToBioSamplesAccessionMap =
        parseBioSampleAccessions(bioSampleAccessions, studies);

    // Step 3: Convert Library → ENA EXPERIMENT (bottom-up: DataFile → Library → Experiment)
    final Map<String, String> experimentSequenceMap =
        this.webinExperimentXmlCreator.createENAExperimentSetElement(
            typeToBioSamplesAccessionMap, webinElement, studies, randomSubmissionIdentifier);

    // Step 4: Convert DataFile → ENA RUN (bottom-up: DataFile → Experiment reference)
    this.webinRunXmlCreator.createENARunSetElement(
        webinElement, studies, experimentSequenceMap, randomSubmissionIdentifier);

    // Step 5: Convert Investigation → ENA PROJECT (top-down approach)
    this.webinProjectXmlCreator.createENAProjectSetElement(
        webinElement, getInvestigation(isaJson), randomSubmissionIdentifier);

    // Debug: Print generated XML to console
    final OutputFormat format = OutputFormat.createPrettyPrint();
    final XMLWriter writer = new XMLWriter(System.out, format);
    writer.write(document);

    // Step 6: Submit XML to ENA Webin API
    final String receiptXml =
        webinHttpSubmissionService.performWebinSubmission(
            webinUserName, document.asXML(), webinPassword);

    // Step 7: Convert ENA XML receipt to JSON
    final Receipt receiptJson = receiptConversionService.readReceiptXml(receiptXml);

    // Step 8: Convert ENA receipt to MARS receipt format
    final MarsReceipt marsReceipt = marsReceiptService.convertReceiptToMars(receiptJson, isaJson);

    // Step 9: Return MARS receipt as JSON
    return marsReceiptService.convertMarsReceiptToJson(marsReceipt);
  }

  /**
   * Extracts Study objects from ISA-JSON.
   *
   * @param isaJson the parsed ISA-JSON object
   * @return list of Study objects, or null if extraction fails
   */
  public List<Study> getStudies(final IsaJson isaJson) {
    try {
      return isaJson.getInvestigation().getStudies();
    } catch (final Exception e) {
      log.error("Failed to parse ISA JSON and get studies", e);
    }

    return null;
  }

  /**
   * Extracts Investigation object from ISA-JSON.
   *
   * @param isaJson the parsed ISA-JSON object
   * @return Investigation object, or null if extraction fails
   */
  public Investigation getInvestigation(final IsaJson isaJson) {
    try {
      return isaJson.getInvestigation();
    } catch (final Exception e) {
      log.error("Failed to parse ISA JSON and get investigation", e);
    }

    return null;
  }

  /**
   * Parses BioSamples accessions from input parameter or extracts from ISA-JSON.
   *
   * <p>If the bioSampleAccessions JSON string is provided, it will be parsed and used. Otherwise,
   * falls back to extracting from ISA-JSON Study sources.
   *
   * <p>Expected format: JSON string with "SOURCE" as key, e.g., {@code {"SOURCE":"SAMEA130793922"}}
   *
   * <p>When passing as URL query parameter, the JSON must be URL-encoded:
   *
   * <ul>
   *   <li>Raw: {@code {"SOURCE":"SAMEA130793922"}}
   *   <li>URL-encoded: {@code %7B%22SOURCE%22%3A%22SAMEA130793922%22%7D}
   * </ul>
   *
   * @param bioSampleAccessions optional JSON string of BioSamples accessions map (must have
   *     "SOURCE" key)
   * @param studies list of Study objects to search if bioSampleAccessions is not provided
   * @return map with BioSamples accessions (e.g., "SOURCE" -> "SAMEA130793922")
   */
  private Map<String, String> parseBioSampleAccessions(
      final String bioSampleAccessions, final List<Study> studies) {
    // If bioSampleAccessions is provided as JSON string, parse it
    // Expected format: {"SOURCE":"SAMEA130793922"}
    if (bioSampleAccessions != null && !bioSampleAccessions.trim().isEmpty()) {
      try {
        final String trimmedJson = bioSampleAccessions.trim();
        // Validate that it looks like JSON (starts with { and ends with })
        if (!trimmedJson.startsWith("{") || !trimmedJson.endsWith("}")) {
          log.warn(
              "bioSampleAccessions does not appear to be valid JSON (expected format: {\"SOURCE\":\"value\"}), "
                  + "received: {}. Falling back to extraction from ISA-JSON",
              trimmedJson);
          return getBiosamples(studies);
        }
        return objectMapper.readValue(trimmedJson, new TypeReference<Map<String, String>>() {});
      } catch (final Exception e) {
        log.warn(
            "Failed to parse bioSampleAccessions JSON: '{}'. Error: {}. "
                + "Falling back to extraction from ISA-JSON",
            bioSampleAccessions,
            e.getMessage(),
            e);
        // Fall through to extraction from ISA-JSON
      }
    }

    // Fall back to extracting from ISA-JSON
    return getBiosamples(studies);
  }

  /**
   * Extracts BioSamples accessions from Study sources.
   *
   * <p>Looks for source samples that have a BioSamples accession stored in the characteristic with
   * category "#characteristic_category/accession". Returns the first source accession found.
   *
   * @param studies list of Study objects to search
   * @return map with "SOURCE" key and BioSamples accession value, or empty map if not found
   */
  public Map<String, String> getBiosamples(List<Study> studies) {
    Map<String, String> biosamples = new HashMap<>();

    if (studies == null) {
      return biosamples;
    }

    for (Study study : studies) {
      if (study.materials != null && study.materials.sources != null) {
        for (Source source : study.materials.sources) {
          String sourceAccession = getCharacteresticAnnotation(source.characteristics);
          if (sourceAccession != null && !sourceAccession.isBlank()) {
            biosamples.put("SOURCE", sourceAccession);
            return biosamples;
          }
        }
      }
    }

    return biosamples;
  }

  /**
   * Extracts BioSamples accession from source characteristics.
   *
   * <p>Looks for a characteristic with category "#characteristic_category/accession" and returns
   * its value.
   *
   * @param characteristics list of characteristics to search
   * @return BioSamples accession value, or empty string if not found
   */
  private String getCharacteresticAnnotation(List<Characteristic> characteristics) {
    if (characteristics == null) {
      return "";
    }

    for (Characteristic characteristic : characteristics) {
      if (characteristic.category != null
          && "#characteristic_category/accession".equals(characteristic.category.id)) {
        if (characteristic.value != null) {
          return characteristic.value.annotationValue;
        }
      }
    }

    return "";
  }

  /**
   * Initializes the ENA Webin V2 XML submission structure.
   *
   * <p>Creates the root WEBIN element with SUBMISSION and ACTIONS structure required by ENA Webin
   * API. The ACTION is set to "ADD" to indicate this is a new submission.
   *
   * @param document the XML document to add elements to
   * @return the WEBIN element that serves as the root for all ENA submission elements
   */
  private static Element startPreparingWebinV2SubmissionXml(Document document) {
    final Element webinElement = document.addElement("WEBIN");
    final Element submissionElement = webinElement.addElement("SUBMISSION");
    final Element actionsElement = submissionElement.addElement("ACTIONS");
    final Element actionElement = actionsElement.addElement("ACTION");

    // Set action to "ADD" for new submissions
    actionElement.addElement("ADD");

    return webinElement;
  }
}
