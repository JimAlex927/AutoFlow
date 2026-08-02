package com.auto.master.floatwin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NodeFloatButtonConfigTest {

    @Test
    public void oldConfigDefaultsToNoPromptBeforeRun() {
        NodeFloatButtonConfig config = new NodeFloatButtonConfig();

        config.ensureDefaults();

        assertFalse(config.promptConfigUiBeforeRun);
    }

    @Test
    public void explicitPromptBeforeRunIsPreserved() {
        NodeFloatButtonConfig config = new NodeFloatButtonConfig();
        config.promptConfigUiBeforeRun = true;

        config.ensureDefaults();

        assertTrue(config.promptConfigUiBeforeRun);
    }
}
