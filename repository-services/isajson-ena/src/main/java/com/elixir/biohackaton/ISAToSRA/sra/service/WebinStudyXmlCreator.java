/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.biohackaton.ISAToSRA.receipt.isamodel.*;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

/**
 * Service for creating ENA STUDY XML elements from ISA-JSON Study objects.
 *
 * <p>Converts ISA-JSON Study objects to ENA STUDY_SET XML structure. Each Study from the ISA-JSON
 * is mapped to an ENA STUDY element with:
 *
 * <ul>
 *   <li>STUDY_TITLE: from Study.title
 *   <li>STUDY_DESCRIPTION: from Study.description
 *   <li>STUDY_ABSTRACT: from Study.description (same as description)
 *   <li>STUDY_TYPE: hardcoded to "Other"
 *   <li>STUDY_ATTRIBUTES: from Study.comments
 * </ul>
 */
@Service
@Slf4j
public class WebinStudyXmlCreator {
  /**
   * Creates ENA STUDY_SET XML element from ISA-JSON Study objects.
   *
   * <p>Maps each Study to an ENA STUDY element within a STUDY_SET. The alias is generated using the
   * study title and a random submission identifier to ensure uniqueness.
   *
   * @param webinElement the parent WEBIN element to add STUDY_SET to
   * @param studies list of Study objects from ISA-JSON
   * @param randomSubmissionIdentifier unique identifier for this submission
   */
  public void createENAStudySetElement(
      final Element webinElement,
      final List<Study> studies,
      final String randomSubmissionIdentifier) {
    try {
      final Element studySetElement = webinElement.addElement("STUDY_SET");

      studies.forEach(
          study -> {
            // Create STUDY element with alias based on title and submission identifier
            final Element studyElement =
                studySetElement
                    .addElement("STUDY")
                    .addAttribute("alias", study.getTitle() + "-" + randomSubmissionIdentifier);

            // Create DESCRIPTOR element containing study metadata
            final Element studyDescriptorElement = studyElement.addElement("DESCRIPTOR");

            studyDescriptorElement.addElement("STUDY_TITLE").addText(study.getTitle());
            studyDescriptorElement.addElement("STUDY_DESCRIPTION").addText(study.getDescription());
            // ENA requires both STUDY_DESCRIPTION and STUDY_ABSTRACT
            studyDescriptorElement.addElement("STUDY_ABSTRACT").addText(study.getDescription());
            studyDescriptorElement
                .addElement("STUDY_TYPE")
                .addAttribute("existing_study_type", "Other");

            // Add study attributes from comments
            final Element studyAttributesElement = studyElement.addElement("STUDY_ATTRIBUTES");

            if (study.getComments() != null) {
              study
                  .getComments()
                  .forEach(
                      comment -> {
                        final Element studyAttributeElement =
                            studyAttributesElement.addElement("STUDY_ATTRIBUTE");

                        studyAttributeElement.addElement("TAG").addText(comment.getName());
                        studyAttributeElement
                            .addElement("VALUE")
                            .addText((String) comment.getValue());
                      });
            }
          });
    } catch (final Exception e) {
      log.error("Failed to parse ISA JSON and create ENA study", e);
      throw new RuntimeException("Failed to create ENA STUDY elements", e);
    }
  }
}
