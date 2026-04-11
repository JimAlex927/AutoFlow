package com.auto.master.floatwin;

/**
 * Configuration for a node-specific floating button.
 * Each OperationItem can have at most one floating button.
 */
public class NodeFloatButtonConfig {
    public String operationId;
    public String operationName;
    public String projectName;
    public String taskName;
    /** ARGB color of the circular button body. */
    public int color;
    public int posX;
    public int posY;
    /** Custom label text. null / empty = show abbreviated operationName. */
    public String labelText;
    /** Label text color. Default 0xFFFFFFFF (white). */
    public int textColor;
    /** Button diameter in dp. Default 48. */
    public int sizeDp;
    /** Opacity 0.0–1.0. Default 1.0. */
    public float alpha;
    /** If true, hide the button while the node is executing. */
    public boolean hideWhileRunning;
    /** Runtime variable overrides injected before launching from this node button. */
    public String runtimeVariablesText;

    public NodeFloatButtonConfig() {}

    public NodeFloatButtonConfig(String operationId, String operationName,
                                  String projectName, String taskName,
                                  int color, int posX, int posY) {
        this.operationId   = operationId;
        this.operationName = operationName;
        this.projectName   = projectName;
        this.taskName      = taskName;
        this.color         = color;
        this.posX          = posX;
        this.posY          = posY;
        ensureDefaults();
    }

    /**
     * Fills zero/null fields with defaults.
     * Must be called after Gson deserialization so old saved configs
     * that lack the new fields still behave correctly.
     */
    public void ensureDefaults() {
        if (textColor == 0) textColor = 0xFFFFFFFF;
        if (sizeDp   <= 0) sizeDp    = 48;
        if (alpha    <= 0) alpha     = 1.0f;
        if (runtimeVariablesText == null) runtimeVariablesText = "";
    }
}
