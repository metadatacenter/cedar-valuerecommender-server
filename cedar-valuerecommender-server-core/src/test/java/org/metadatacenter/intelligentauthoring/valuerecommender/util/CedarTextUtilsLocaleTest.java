package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CedarTextUtilsLocaleTest {
  @Test void recommendationsUseStableKeysAndRetainTermUris() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr"));
      assertEquals("IDENTITY", CedarTextUtils.normalizeValue("identity"));
      assertEquals("STUDY.TITLE", CedarTextUtils.normalizePath("study.title"));
      assertEquals("http://example.org/identity", CedarTextUtils.normalizePath("http://example.org/identity"));
    } finally { Locale.setDefault(original); }
  }
}
