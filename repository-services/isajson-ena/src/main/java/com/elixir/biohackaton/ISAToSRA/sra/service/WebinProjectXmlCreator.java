/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

/**
 * Service for creating ENA PROJECT XML elements from ISA-JSON Investigation objects.
 *
 * <p>Converts ISA-JSON Investigation to ENA PROJECT_SET XML structure. The Investigation (top-level
 * ISA-JSON element) is mapped to an ENA PROJECT element with:
 *
 * <ul>
 *   <li>TITLE: from Investigation.title
 *   <li>DESCRIPTION: from Investigation.description
 *   <li>SUBMISSION_PROJECT: contains SEQUENCING_PROJECT (hardcoded project type)
 * </ul>
 */
@Service
public class WebinProjectXmlCreator {
  /**
   * Creates ENA PROJECT_SET XML element from ISA-JSON Investigation.
   *
   * <p>Maps the Investigation to an ENA PROJECT element within a PROJECT_SET. The alias is
   * generated using the investigation title and a random submission identifier to ensure
   * uniqueness.
   *
   * @param webinElement the parent WEBIN element to add PROJECT_SET to
   * @param investigation the Investigation object from ISA-JSON
   * @param randomSubmissionIdentifier unique identifier for this submission
   */
  public void createENAProjectSetElement(
      final Element webinElement,
      final Investigation investigation,
      final String randomSubmissionIdentifier) {
    final Element projectSetElement = webinElement.addElement("PROJECT_SET");
    final Element projectElement = projectSetElement.addElement("PROJECT");

    // Create PROJECT element with alias based on investigation title and submission identifier
    projectElement.addAttribute(
        "alias", investigation.getTitle() + "-" + randomSubmissionIdentifier);
    projectElement.addElement("TITLE").addText(investigation.getTitle());
    projectElement.addElement("DESCRIPTION").addText(investigation.getDescription());

    // ENA requires SUBMISSION_PROJECT with a project type (e.g., SEQUENCING_PROJECT)
    final Element submissionProjectElement = projectElement.addElement("SUBMISSION_PROJECT");
    submissionProjectElement.addElement("SEQUENCING_PROJECT");
  }
}
