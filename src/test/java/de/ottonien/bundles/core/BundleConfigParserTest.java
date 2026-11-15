package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BundleConfigParserTest {

    private static void assertError(String json, String expectedFragment) {
        BundleConfigParser.Result r = BundleConfigParser.parse(json);
        assertFalse(r.ok(), "expected errors for " + json);
        assertTrue(r.errors().stream().anyMatch(e -> e.contains(expectedFragment)),
                "errors " + r.errors() + " should contain '" + expectedFragment + "'");
        assertTrue(r.sizes().isEmpty());
    }

    @Test
    void shippedDefaultFileIsValid() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/bundles.json")) {
            assertNotNull(in);
            BundleConfigParser.Result r = BundleConfigParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(r.ok(), r.errors().toString());
            assertEquals(64, r.bundleSize());
            assertEquals(10, r.sizes().size());
            assertEquals(4, r.sizes().get("ottonien:erbsen"));
            assertEquals(4, r.sizes().get("ottonien:kohl"));
            assertEquals(4, r.sizes().get("ottonien:ruebe"));
            assertEquals(16, r.sizes().get("ottonien:erbsensuppe"));
            assertEquals(16, r.sizes().get("ottonien:gemuesesuppe"));
            assertEquals(16, r.sizes().get("ottonien:zwiebelsuppe"));
            assertEquals(16, r.sizes().get("ottonien:bratgemuese"));
            assertEquals(32, r.sizes().get("ottonien:eintopf"));
            assertEquals(64, r.sizes().get("ottonien:apfelkuchen"));
            assertEquals(8, r.sizes().get("ottonien:salz"));
            assertFalse(r.sizes().containsKey("ottonien:zwiebel"));
        }
    }

    @Test
    void bundleSizeDefaultsToSixtyFourWhenMissingOrNull() {
        assertEquals(64, BundleConfigParser.parse("{}").bundleSize());
        assertEquals(64, BundleConfigParser.parse("{\"bundleSize\":null}").bundleSize());
        assertTrue(BundleConfigParser.parse("{\"bundleSize\":null}").ok());
        assertEquals(64, BundleConfigParser.parse("{\"items\":{}}").bundleSize());
    }

    @Test
    void bundleSizeIsReadWithinItsLimits() {
        assertEquals(80, BundleConfigParser.parse("{\"bundleSize\":80}").bundleSize());
        assertEquals(1, BundleConfigParser.parse("{\"bundleSize\":1}").bundleSize());
        assertEquals(96, BundleConfigParser.parse("{\"bundleSize\":96,\"items\":{}}").bundleSize());
        assertTrue(BundleConfigParser.parse("{\"bundleSize\":96}").ok());
    }

    @Test
    void invalidBundleSizeIsRejected() {
        assertError("{\"bundleSize\":0}", "bundleSize 0 liegt außerhalb von 1 bis 96");
        assertError("{\"bundleSize\":97}", "außerhalb");
        assertError("{\"bundleSize\":-64}", "außerhalb");
        assertError("{\"bundleSize\":99999999999999}", "außerhalb");
        assertError("{\"bundleSize\":64.0}", "keine ganze Zahl");
        assertError("{\"bundleSize\":\"64\"}", "String");
        assertError("{\"bundleSize\":\"\"}", "leerer String");
        assertError("{\"bundleSize\":64,\"bundleSize\":128}", "Doppelter JSON-Schlüssel 'bundleSize'");
    }

    @Test
    void sizeBoundariesAreValid() {
        BundleConfigParser.Result r = BundleConfigParser.parse(
                "{\"items\":{\"a\":{\"size\":1},\"b\":{\"size\":1000000}}}");
        assertTrue(r.ok(), r.errors().toString());
        assertEquals(1, r.sizes().get("a"));
        assertEquals(1000000, r.sizes().get("b"));
    }

    @Test
    void sizeAboveBundleSizeIsValid() {
        BundleConfigParser.Result r = BundleConfigParser.parse("{\"bundleSize\":32,\"items\":{\"a\":{\"size\":64}}}");
        assertTrue(r.ok(), r.errors().toString());
        assertEquals(32, r.bundleSize());
        assertEquals(64, r.sizes().get("a"));
    }

    @Test
    void zeroIsRejected() {
        assertError("{\"items\":{\"a\":{\"size\":0}}}", "außerhalb");
    }

    @Test
    void negativeAndHugeAreRejected() {
        assertError("{\"items\":{\"a\":{\"size\":-1}}}", "außerhalb");
        assertError("{\"items\":{\"a\":{\"size\":1000001}}}", "außerhalb");
        assertError("{\"items\":{\"a\":{\"size\":99999999999999}}}", "außerhalb");
    }

    @Test
    void emptyStringIsRejected() {
        assertError("{\"items\":{\"a\":{\"size\":\"\"}}}", "leerer String");
    }

    @Test
    void otherTypesAreRejected() {
        assertError("{\"items\":{\"a\":{\"size\":\"8\"}}}", "String");
        assertError("{\"items\":{\"a\":{\"size\":true}}}", "Boolean");
        assertError("{\"items\":{\"a\":{\"size\":[8]}}}", "Array");
        assertError("{\"items\":{\"a\":{\"size\":{}}}}", "Objekt");
    }

    @Test
    void nonIntegerNumbersAreRejected() {
        assertError("{\"items\":{\"a\":{\"size\":1.5}}}", "keine ganze Zahl");
        assertError("{\"items\":{\"a\":{\"size\":8.0}}}", "keine ganze Zahl");
        assertError("{\"items\":{\"a\":{\"size\":1e1}}}", "keine ganze Zahl");
    }

    @Test
    void nullAndMissingMeanVanillaCost() {
        BundleConfigParser.Result r = BundleConfigParser.parse(
                "{\"items\":{\"a\":{\"size\":null},\"b\":{},\"c\":{\"size\":4}}}");
        assertTrue(r.ok(), r.errors().toString());
        assertEquals(1, r.sizes().size());
        assertEquals(4, r.sizes().get("c"));
    }

    @Test
    void oldBundleCapacityIsAnErrorWithAHintAtSize() {
        String old = "{\"items\":{\"ottonien:erbsen\":{\"bundleCapacity\":16}}}";
        assertError(old, "'bundleCapacity' gibt es nicht mehr");
        assertError(old, "size = 64 / bundleCapacity");
        assertError(old, "\"size\": 4");
        assertError("{\"items\":{\"a\":{\"bundleCapacity\":1}}}", "\"size\": 64");
        // no even conversion: the hint stays general
        BundleConfigParser.Result r = BundleConfigParser.parse("{\"items\":{\"a\":{\"bundleCapacity\":3}}}");
        assertFalse(r.ok());
        assertTrue(r.errors().get(0).contains("size = 64 / bundleCapacity"));
        assertFalse(r.errors().get(0).contains("hier:"));
        // also when size is given next to it, and for null
        assertError("{\"items\":{\"a\":{\"size\":4,\"bundleCapacity\":16}}}", "'bundleCapacity' gibt es nicht mehr");
        assertError("{\"items\":{\"a\":{\"bundleCapacity\":null}}}", "'bundleCapacity' gibt es nicht mehr");
    }

    @Test
    void emptyDocumentAndMissingOrNullItemsAreValidAndEmpty() {
        assertTrue(BundleConfigParser.parse("{}").ok());
        assertTrue(BundleConfigParser.parse("{\"items\":null}").ok());
        assertTrue(BundleConfigParser.parse("{\"items\":{}}").sizes().isEmpty());
    }

    @Test
    void duplicateItemKeyIsAnError() {
        assertError("{\"items\":{\"a\":{\"size\":4},\"a\":{\"size\":8}}}", "Doppelter JSON-Schlüssel 'a'");
    }

    @Test
    void duplicateFieldKeyIsAnError() {
        assertError("{\"items\":{\"a\":{\"size\":4,\"size\":8}}}", "Doppelter JSON-Schlüssel 'size'");
    }

    @Test
    void duplicateItemsKeyIsAnError() {
        assertError("{\"items\":{},\"items\":{}}", "Doppelter JSON-Schlüssel 'items'");
    }

    @Test
    void unknownFieldsAreErrors() {
        assertError("{\"items\":{\"a\":{\"size\":4,\"weight\":2}}}", "unbekanntes Feld 'weight'");
        assertError("{\"items\":{},\"extra\":1}", "Unbekanntes Feld 'extra'");
    }

    @Test
    void emptyItemIdIsAnError() {
        assertError("{\"items\":{\"\":{\"size\":4}}}", "Leere Item-ID");
    }

    @Test
    void keysAreNotRestrictedToNamespacedFormat() {
        BundleConfigParser.Result r = BundleConfigParser.parse("{\"items\":{\"erbsen\":{\"size\":4}}}");
        assertTrue(r.ok());
        assertEquals(4, r.sizes().get("erbsen"));
    }

    @Test
    void structuralTypeErrors() {
        assertError("[]", "JSON-Objekt");
        assertError("{\"items\":[]}", "'items' muss ein JSON-Objekt");
        assertError("{\"items\":{\"a\":4}}", "muss ein JSON-Objekt");
        assertError("{\"items\":{\"a\":null}}", "muss ein JSON-Objekt");
    }

    @Test
    void malformedJsonIsReportedNotThrown() {
        assertFalse(BundleConfigParser.parse("{\"items\":").ok());
        assertFalse(BundleConfigParser.parse("").ok());
        assertFalse(BundleConfigParser.parse("not json").ok());
        assertFalse(BundleConfigParser.parse("{\"items\":{}} trailing").ok());
        assertFalse(BundleConfigParser.parse("{'items':{}}").ok());
        assertFalse(BundleConfigParser.parse("{\"items\":{},}").ok());
    }

    @Test
    void allErrorsAreCollected() {
        BundleConfigParser.Result r = BundleConfigParser.parse(
                "{\"bundleSize\":0,\"items\":{\"a\":{\"size\":0},\"b\":{\"size\":\"\"}}}");
        assertEquals(3, r.errors().size());
    }
}
