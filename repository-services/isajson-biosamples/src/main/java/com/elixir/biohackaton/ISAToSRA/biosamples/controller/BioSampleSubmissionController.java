/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.biosamples.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.elixir.biohackaton.ISAToSRA.biosamples.model.BiosampleAccessionsMap;
import com.elixir.biohackaton.ISAToSRA.biosamples.service.BioSamplesSubmitter;
import com.elixir.biohackaton.ISAToSRA.biosamples.service.MarsReceiptService;
import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptException;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.IsaJson;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.Study;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class BioSampleSubmissionController {
  @Autowired private BioSamplesSubmitter bioSamplesSubmitter;
  @Autowired private ObjectMapper objectMapper;
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
  @CrossOrigin(origins = "http://localhost:8000")
  @PostMapping(
    value = "/submit",
    consumes = { APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE })
  public String performSubmissionToBioSamplesAndEna(
      @RequestBody final String submissionPayload,
      @RequestParam(value = "webinjwt") String webinJwt) {
    try {
      if (webinJwt == null || webinJwt.isEmpty()) {
        throw new RuntimeException("Webin Authentication Token is not provided");
      }

      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

      final IsaJson isaJson = this.objectMapper.readValue(submissionPayload, IsaJson.class);
      final List<Study> studies = getStudies(isaJson);

      final BiosampleAccessionsMap accessionsMap = this.bioSamplesSubmitter.createBioSamples(studies, webinJwt);
      marsReceiptService.convertReceiptToMars(accessionsMap, isaJson);

      return marsReceiptService.convertMarsReceiptToJson();
    } catch (final MarsReceiptException e) {
      log.error("Mars receipt excption", e);
      marsReceiptService.setMarsReceiptErrors(e.getError());
      return marsReceiptService.convertMarsReceiptToJson();
    } catch (final Exception e) {
      log.error("Internal server error", e);
      marsReceiptService.setMarsReceiptErrors(e.getMessage());
      return marsReceiptService.convertMarsReceiptToJson();
    }
  }

  public List<Study> getStudies(final IsaJson isaJson) {
    try {
      return isaJson.getInvestigation().getStudies();
    } catch (final Exception e) {
      throw new MarsReceiptException(e, "Failed to parse ISA JSON and get studies");
    }
  }
}
