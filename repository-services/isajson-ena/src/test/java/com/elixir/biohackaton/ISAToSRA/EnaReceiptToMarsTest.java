/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.models.receipt.MarsError;
import com.elixir.mars.repository.models.receipt.MarsErrorType;
import com.elixir.mars.repository.models.isa.IsaJson;
import com.elixir.mars.repository.models.receipt.MarsReceipt;
import com.elixir.mars.repository.models.receipt.MarsSubmissionStatus;
import com.elixir.biohackaton.ISAToSRA.controller.WebinIsaToXmlSubmissionController;
import com.elixir.biohackaton.ISAToSRA.sra.model.Receipt;
import com.elixir.biohackaton.ISAToSRA.sra.service.MarsReceiptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EnaReceiptToMarsTest {

  @Test
  void initiateSubmissionToEnaReturnsConfiguredFtpCredentials() throws Exception {
    final WebinIsaToXmlSubmissionController controller = new WebinIsaToXmlSubmissionController();
    final Field objectMapperField =
        WebinIsaToXmlSubmissionController.class.getDeclaredField("objectMapper");
    objectMapperField.setAccessible(true);
    objectMapperField.set(controller, new ObjectMapper());

    final String response =
        controller.initiateSubmissionToEna(
            "{}", "ftp.example.org", "/incoming", "webin-user", "webin-password");
    final JsonNode responseJson = new ObjectMapper().readTree(response);

    Assertions.assertEquals("ftp.example.org", responseJson.at("/ftpCredentials/host").asText());
    Assertions.assertEquals("/incoming", responseJson.at("/ftpCredentials/uploadPath").asText());
    Assertions.assertEquals("webin-user", responseJson.at("/ftpCredentials/username").asText());
    Assertions.assertEquals("webin-password", responseJson.at("/ftpCredentials/password").asText());
    Assertions.assertFalse(responseJson.has("submissionId"));
  }

  @Test
  void convertToMars_validInput() {
    String enaReceiptFilePath = "../../test-data/ena-receipt.json";
    String marsReceiptPath = "../../test-data/mars-ena-receipt.json";
    MarsReceipt marsReceipt = convertToMars(enaReceiptFilePath, marsReceiptPath);

    Assertions.assertEquals(MarsSubmissionStatus.SUCCESS, marsReceipt.getSubmissionStatus());
    Assertions.assertEquals(2, marsReceipt.getAssayComments().size());
    Assertions.assertEquals("ena_submission_date", marsReceipt.getAssayComments().get(0).getName());
    Assertions.assertEquals("2023-11-16T10:44:41.618Z", marsReceipt.getAssayComments().get(0).getValue());
    Assertions.assertEquals(
        "#assay/18_20_21",
        marsReceipt.getAssayComments().get(0).getPath().get(1).getWhere().getValue());
    Assertions.assertEquals("ena_release_date", marsReceipt.getAssayComments().get(1).getName());
    Assertions.assertEquals("2023-01-01Z", marsReceipt.getAssayComments().get(1).getValue());
    Assertions.assertEquals(
        "#assay/18_20_21",
        marsReceipt.getAssayComments().get(1).getPath().get(1).getWhere().getValue());
  }

  @Test
  void convertToMars_missingAccessionsDoNotChangeRepositoryStatus() {
    String enaReceiptFilePath = "../../test-data/ena-receipt-invalid.json";
    String marsReceiptPath = "../../test-data/mars-ena-receipt-invalid.json";
    MarsReceipt marsReceipt = convertToMars(enaReceiptFilePath, marsReceiptPath);

    Assertions.assertEquals(MarsSubmissionStatus.SUCCESS, marsReceipt.getSubmissionStatus());
  }

  @Test
  void convertToMars_mapsRepositoryFailureStatus() throws Exception {
    ObjectMapper jsonMapper = new ObjectMapper();
    Receipt receipt =
        jsonMapper.readValue(
            Files.readString(new File("../../test-data/ena-receipt.json").toPath()), Receipt.class);
    receipt.setSuccess(false);
    IsaJson isaJson =
        jsonMapper.readValue(
            Files.readString(new File("../../test-data/ena-minimal-after-biosamples.json").toPath()),
            IsaJson.class);

    MarsReceipt marsReceipt = new MarsReceiptService().convertReceiptToMars(receipt, isaJson);

    Assertions.assertEquals(MarsSubmissionStatus.FAILURE, marsReceipt.getSubmissionStatus());
  }

  MarsReceipt convertToMars(final String enaReceiptFilePath, final String marsReceiptPath) {
    ObjectMapper jsonMapper = new ObjectMapper();
    jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    MarsReceipt marsReceipt;
    try {
      try {
        // Reading Inputs
        String receiptFile = Files.readString(new File(enaReceiptFilePath).toPath());
        String isaJsonFilePath = "../../test-data/ena-minimal-after-biosamples.json";
        String isaJsonFile = Files.readString(new File(isaJsonFilePath).toPath());

        // Mapping inputs to the proper objects
        Receipt receipt = jsonMapper.readValue(receiptFile, Receipt.class);
        IsaJson isaJson = jsonMapper.readValue(isaJsonFile, IsaJson.class);

        // Converting ENA receipt to MARS receipt
        marsReceipt = new MarsReceiptService().convertReceiptToMars(receipt, isaJson);
      } catch (MarsReceiptException e) {
        marsReceipt = errorReceipt(e.getError());
      } catch (Exception e) {
        marsReceipt = errorReceipt(
            MarsError.builder().message(String.valueOf(e.getMessage())).build());
      }

      Files.write(
          new File(marsReceiptPath).toPath(),
          jsonMapper.writeValueAsBytes(marsReceipt));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return marsReceipt;
  }

  private MarsReceipt errorReceipt(final MarsError error) {
    return MarsReceipt.builder()
        .targetRepository("ena")
        .errors(List.of(
            MarsError.builder()
                .type(MarsErrorType.INVALID_METADATA)
                .message(error.message)
                .path(error.path)
                .build()))
        .info(List.of())
        .accessions(List.of())
        .build();
  }
}
