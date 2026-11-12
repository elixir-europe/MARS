/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.sra.service;

import com.elixir.mars.repository.models.isa.Comment;
import com.elixir.mars.repository.models.isa.DataFile;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the ISA data-file comments that carry the attributes of an ENA {@code FILE} element.
 *
 * <p>The comment names are exactly the attribute names of the {@code FILE} element in ENA's
 * {@code SRA.run.xsd} and {@code SRA.analysis.xsd}: {@code filetype}, {@code checksum_method}
 * and {@code checksum}. Matching is strict and case-sensitive; no other spelling is accepted.
 */
final class DataFileComments {
  /** Comment holding the ENA {@code filetype} attribute, e.g. {@code fastq} or {@code bam}. */
  static final String FILETYPE = "filetype";

  /** Comment holding the ENA {@code checksum_method} attribute: {@code MD5} or {@code SHA-256}. */
  static final String CHECKSUM_METHOD = "checksum_method";

  /** Comment holding the ENA {@code checksum} attribute. */
  static final String CHECKSUM = "checksum";

  /** Checksum methods permitted by the ENA {@code FILE} element. */
  static final Set<String> CHECKSUM_METHODS = Set.of("MD5", "SHA-256");

  /** Method assumed when the ISA-JSON does not name one. */
  static final String DEFAULT_CHECKSUM_METHOD = "MD5";

  private DataFileComments() {}

  /** Returns the value of the data-file comment with exactly this name, or {@code null}. */
  static String findValue(final DataFile dataFile, final String name) {
    if (dataFile == null || dataFile.getComments() == null) {
      return null;
    }

    for (final Comment comment : dataFile.getComments()) {
      if (comment != null && name.equals(comment.getName())) {
        return comment.getValue();
      }
    }

    return null;
  }

  /**
   * Returns the ENA checksum method declared for a data file, defaulting to {@link
   * #DEFAULT_CHECKSUM_METHOD} when none is declared.
   *
   * @throws IllegalArgumentException when the declared method is not one ENA accepts
   */
  static String resolveChecksumMethod(final DataFile dataFile) {
    final String declaredMethod = findValue(dataFile, CHECKSUM_METHOD);
    if (declaredMethod == null || declaredMethod.isBlank()) {
      return DEFAULT_CHECKSUM_METHOD;
    }

    final String checksumMethod = declaredMethod.trim().toUpperCase(Locale.ROOT);
    if (!CHECKSUM_METHODS.contains(checksumMethod)) {
      throw new IllegalArgumentException(
          "Data file "
              + dataFile.getName()
              + " declares "
              + CHECKSUM_METHOD
              + " '"
              + declaredMethod
              + "', but ENA only accepts "
              + CHECKSUM_METHODS
              + ".");
    }

    return checksumMethod;
  }
}
