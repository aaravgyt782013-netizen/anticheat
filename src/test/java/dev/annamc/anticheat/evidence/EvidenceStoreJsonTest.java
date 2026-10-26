package dev.annamc.anticheat.evidence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceStoreJsonTest {
    @Test
    void escapesQuotesBackslashesAndControlCharactersForJsonStrings() {
        String input = "quote=\" slash=\\ line1\nline2\ttab" + (char) 1;
        String escaped = EvidenceStore.escapeJson(input);

        assertEquals("quote=\\\" slash=\\\\ line1\\nline2\\ttab\\u0001", escaped);
        for (int i = 0; i < escaped.length(); i++) {
            assertTrue(escaped.charAt(i) >= 0x20,
                    "JSON string output must not contain raw control characters");
        }
    }

    @Test
    void nullEvidenceTextIsSafe() {
        assertEquals("", EvidenceStore.escapeJson(null));
    }

    @Test
    void ordinaryUnicodeIsPreserved() {
        assertEquals("ANNA AC • सुरक्षा", EvidenceStore.escapeJson("ANNA AC • सुरक्षा"));
    }
}
