package com.auto.master.floatwin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Map;

public class GestureReferenceCounterTest {

    @Test
    public void countsEachReferencingNodeOnceAcrossLegacyAndPipelineFields() {
        String operations = "["
                + "{\"type\":5,\"inputMap\":{"
                + "\"SAVEFILENAME\":\"tap\","
                + "\"GESTURE_PIPELINE\":[{\"file\":\"tap.json\"},{\"file\":\"swipe.json\"}]}},"
                + "{\"type\":5,\"inputMap\":{"
                + "\"GESTURE_TEMPLATE_ID\":\"tap.json\"}},"
                + "{\"type\":3,\"inputMap\":{\"SAVEFILENAME\":\"not-a-gesture.json\"}}"
                + "]";

        Map<String, Integer> usage =
                GestureReferenceCounter.countFromOperationsJson(operations);

        assertEquals(Integer.valueOf(2), usage.get("tap.json"));
        assertEquals(Integer.valueOf(1), usage.get("swipe.json"));
        assertFalse(usage.containsKey("not-a-gesture.json"));
    }

    @Test
    public void invalidOrEmptyJsonReturnsNoReferences() {
        assertTrueEmpty(GestureReferenceCounter.countFromOperationsJson("[]"));
        assertTrueEmpty(GestureReferenceCounter.countFromOperationsJson("{}"));
        assertTrueEmpty(GestureReferenceCounter.countFromOperationsJson(""));
    }

    private static void assertTrueEmpty(Map<String, Integer> value) {
        assertEquals(0, value.size());
    }
}
