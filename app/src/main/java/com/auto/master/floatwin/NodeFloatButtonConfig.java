package com.auto.master.floatwin;

/**
 * Configuration for a node-specific floating button.
 * Each OperationItem can have at most one floating button.
 */
public class NodeFloatButtonConfig {
    public static final String SHAPE_CIRCLE = "circle";
    public static final String SHAPE_CAPSULE = "capsule";
    public static final String SHAPE_ROUNDED_RECT = "rounded_rect";

    public static final String EFFECT_NONE = "none";
    public static final String EFFECT_PULSE = "pulse";
    public static final String EFFECT_SCALE = "scale";
    public static final String EFFECT_ROTATE = "rotate";
    public static final String EFFECT_BREATH = "breath";

    public static final String STATUS_STYLE_NONE = "none";
    public static final String STATUS_STYLE_PROGRESS = "progress";
    public static final String STATUS_STYLE_FLASH = "flash";

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
    /** Optional ring color around the circular button. 0 = disabled. */
    public int borderColor;
    /** Ring width in dp. 0 = disabled. */
    public int borderWidthDp;
    /** Optional custom image path. Absolute path or path relative to the owning task directory. */
    public String imagePath;
    /** Built-in icon id. Empty = no icon. */
    public String iconKey;
    /** Button shape. */
    public String shape;
    /** Click / idle animation style. */
    public String clickEffect;
    /** Runtime state visual style. */
    public String statusStyle;
    /** Image inset in dp, so the ring and rounded edge have breathing room. */
    public int imagePaddingDp;
    /** Button diameter in dp. Default 48. */
    public int sizeDp;
    /** Opacity 0.0–1.0. Default 1.0. */
    public float alpha;
    /** If true, hide the button while the node is executing. */
    public boolean hideWhileRunning;
    /** Runtime variable overrides injected before launching from this node button. */
    public String runtimeVariablesText;
    /** Bound visual config UI schema id. Empty = use legacy text editor only. */
    public String configUiSchemaId;
    /** Whether the bound Config UI must be confirmed before every node run. */
    public Boolean promptConfigUiBeforeRun;
    /** Whether the floating button itself should be rendered. */
    public Boolean buttonEnabled;

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
        if (imagePath == null) imagePath = "";
        if (iconKey == null) iconKey = "";
        if (shape == null || shape.isEmpty()) shape = SHAPE_CIRCLE;
        if (clickEffect == null || clickEffect.isEmpty()) clickEffect = EFFECT_NONE;
        if (statusStyle == null || statusStyle.isEmpty()) statusStyle = STATUS_STYLE_NONE;
        if (imagePaddingDp < 0) imagePaddingDp = 0;
        if (imagePaddingDp == 0) imagePaddingDp = 4;
        if (borderWidthDp < 0) borderWidthDp = 0;
        if (sizeDp   <= 0) sizeDp    = 48;
        if (alpha    <= 0) alpha     = 1.0f;
        if (runtimeVariablesText == null) runtimeVariablesText = "";
        if (configUiSchemaId == null) configUiSchemaId = "";
        if (promptConfigUiBeforeRun == null) promptConfigUiBeforeRun = false;
        if (buttonEnabled == null) buttonEnabled = true;
    }
}
