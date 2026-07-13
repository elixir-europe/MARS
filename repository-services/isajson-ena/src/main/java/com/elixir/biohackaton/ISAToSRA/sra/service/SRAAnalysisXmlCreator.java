/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.MarsReceiptException;
import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SRAAnalysisXmlCreator {
  private static final String DERIVED_FILE_KEY = "Derived Data File";

  private static final String CHECKSUM_KEY = "checksum";

  private static final String CHECKSUM_TYPE_KEY = "checksum type";

  public void createENAAnalysisSetElement(final Element webinElement, final List<Study> studies) {
    final Element analysisSetElement = webinElement.addElement("ANALYSIS_SET");

    studies.forEach(
        study ->
            study
                .getAssays()
                .forEach(assay -> convertAssayToAnalysisElement(assay, analysisSetElement)));
  }

  private void convertAssayToAnalysisElement(final Assay assay, final Element analysisSetElement) {
    final Element analysisElement = analysisSetElement.addElement("ANALYSIS");

    // TODO top level analysis attributes (including type)

    // TODO add SAMPLE_REF elements once analysis submissions define sample accession handling.

    // Add files
    final Element filesElement = analysisElement.addElement("FILES");
    assay.getDataFiles().forEach(dataFile -> convertDataFileToFileElement(dataFile, filesElement));
  }

  private void convertDataFileToFileElement(DataFile dataFile, Element filesElement) {
    // Analysis must use derived files
    if (dataFile == null
        || dataFile.getType() == null
        || !dataFile.getType().equalsIgnoreCase(DERIVED_FILE_KEY)) {
      return;
    }

    String filename = dataFile.getName();
    // TODO any way to get filetype (vcf, bam, etc.) besides extension? also what if files are
    // compressed?
    String filetype = dataFile.getName().substring(dataFile.getName().lastIndexOf('.'));

    // Files must have a checksum (stored in comments)
    final String checksum = findCommentValue(dataFile.getComments(), CHECKSUM_KEY);
    final String checksumType = findCommentValue(dataFile.getComments(), CHECKSUM_TYPE_KEY);

    if (checksum == null || checksumType == null) {
      throw new MarsReceiptException("Checksum and checksum type not found");
    }

    Element fileElement = filesElement.addElement("FILE");
    fileElement.addAttribute("filename", filename);
    fileElement.addAttribute("filetype", filetype);
    fileElement.addAttribute("checksum_method", checksumType);
    fileElement.addAttribute("checksum", checksum);
  }

  private String findCommentValue(final List<Comment> comments, final String commentName) {
    if (comments == null) {
      return null;
    }

    for (final Comment comment : comments) {
      if (comment == null || comment.getName() == null) {
        continue;
      }

      if (comment.getName().equalsIgnoreCase(commentName)) {
        return comment.getValue();
      }
    }

    return null;
  }
}
