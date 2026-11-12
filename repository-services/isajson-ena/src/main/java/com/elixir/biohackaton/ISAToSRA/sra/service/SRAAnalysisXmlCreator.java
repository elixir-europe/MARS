/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.models.isa.Assay;
import com.elixir.mars.repository.models.isa.DataFile;
import com.elixir.mars.repository.models.isa.Study;

/**
 * Creates ENA ANALYSIS XML from ISA derived data files.
 *
 * <p>The analysis path is intentionally limited to derived data files. The attributes of each ENA
 * {@code FILE} element come from ISA comments named after those attributes ({@code filetype},
 * {@code checksum_method}, {@code checksum}; see {@link DataFileComments}). The checksum is
 * required, the checksum method defaults to MD5, and the file type falls back to the file
 * extension.
 */
@Service
@Slf4j
public class SRAAnalysisXmlCreator {
  private static final String DERIVED_FILE_KEY = "Derived Data File";

  /** Creates the ENA ANALYSIS_SET element for derived data files in each submitted assay. */
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

  /**
   * Converts a derived ISA data file into an ENA analysis FILE element.
   *
   * <p>Primary/raw data files are ignored here because they are submitted through ENA RUN XML.
   */
  private void convertDataFileToFileElement(DataFile dataFile, Element filesElement) {
    // Analysis must use derived files
    if (dataFile == null
        || dataFile.getType() == null
        || !dataFile.getType().equalsIgnoreCase(DERIVED_FILE_KEY)) {
      return;
    }

    final String filename = dataFile.getName();

    // Prefer the declared ENA filetype (vcf, bam, ...); otherwise derive it from the extension.
    final String declaredFiletype = DataFileComments.findValue(dataFile, DataFileComments.FILETYPE);
    final String filetype =
        declaredFiletype != null && !declaredFiletype.isBlank()
            ? declaredFiletype
            : filetypeFromExtension(filename);

    // Files must have a checksum (stored in comments)
    final String checksumMethod = DataFileComments.resolveChecksumMethod(dataFile);
    final String checksum = DataFileComments.findValue(dataFile, DataFileComments.CHECKSUM);
    if (checksum == null) {
      throw new MarsReceiptException(
          "Data file " + filename + " is missing the '" + DataFileComments.CHECKSUM + "' comment");
    }

    Element fileElement = filesElement.addElement("FILE");
    fileElement.addAttribute("filename", filename);
    fileElement.addAttribute("filetype", filetype);
    fileElement.addAttribute("checksum_method", checksumMethod);
    fileElement.addAttribute("checksum", checksum);
  }

  /** Derives an ENA filetype from the file extension, without the leading dot. */
  private static String filetypeFromExtension(final String filename) {
    final int dotIndex = filename == null ? -1 : filename.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == filename.length() - 1) {
      throw new MarsReceiptException(
          "Cannot derive the ENA filetype of data file "
              + filename
              + "; add a '"
              + DataFileComments.FILETYPE
              + "' comment");
    }
    return filename.substring(dotIndex + 1);
  }
}
