package com.auto.master.Task.Operation;

import com.auto.master.Task.Handler.OperationHandler.OperationHandler;

import java.util.HashMap;
import java.util.Map;


//重新设计下好混乱啊：

//假设每个operation 看作一个函数
//一个task 看作一个类
//project 有 N 个 task  ，每个task 有不定数量的 operation
//目前operation没有任何关系

//也就是：
//1、先创建一个project
//2、创建N个Task
//3、每个task有很多个 operation，构成 操作池
//前3步称之为 `池化`

//有了池子之后，可以自行构建 `instance`


public abstract class MetaOperation {

    // 这个是失败 跳转的节点的id
    public static final String FALLBACKOPERATIONID = "FALLBACKOPERATIONID";

    public static final String SUCCEESCLICK = "SUCCESSCLICK";

    //图集匹配 用match map进行匹配
    public static final String MATCHMAP = "MatchMap";
    //    下一个节点
    public static String NEXT_OPERATION_ID = "nextOperationId";

    //    点击的坐标
    public static String CLICK_TARGET = "clickTarget";
    public static String CLICK_EXECUTION_MODE = "CLICK_EXECUTION_MODE";
    public static String CLICK_SETTLE_MS = "CLICK_SETTLE_MS";
    public static String CLICK_WAIT_TIMEOUT_MS = "CLICK_WAIT_TIMEOUT_MS";
    public static String CLICK_MODE_FAST = "fast";
    public static String CLICK_MODE_STRICT = "strict";

    //    截图的区域
    public static String PROJECT = "PROJECT";

    public static String TASK = "TASK";

    public static String SAVEFILENAME = "SAVEFILENAME";


    //    模板匹配参数
    public static String MATCHTIMEOUT = "MATCHTIMEOUT";
    public static final long DEFAULT_MATCH_TIMEOUT_MS = 500L;
    public static final long MAX_MATCH_DELAY_MS = 500L;
    public static final long DEFAULT_NODE_PRE_DELAY_MS = 0L;
    public static final long MAX_NODE_PRE_DELAY_MS = 60_000L;
    public static String NODE_PRE_DELAY_MS = "NODE_PRE_DELAY_MS";
    public static String NODE_PRE_DELAY_MIN_MS = "NODE_PRE_DELAY_MIN_MS";
    public static String NODE_PRE_DELAY_MAX_MS = "NODE_PRE_DELAY_MAX_MS";
    public static String NODE_PRE_DELAY_RANDOM = "NODE_PRE_DELAY_RANDOM";
    public static String NODE_BOUND_SESSION_ID = "NODE_BOUND_SESSION_ID";
    public static String NODE_BOUND_SESSION_NAME = "NODE_BOUND_SESSION_NAME";
    public static String MATCH_PRE_DELAY_MS = "MATCH_PRE_DELAY_MS";
    public static String MATCH_POST_DELAY_MS = "MATCH_POST_DELAY_MS";
    public static String POLL_FAST_INTERVAL_MS = "POLL_FAST_INTERVAL_MS";
    public static String POLL_MEDIUM_INTERVAL_MS = "POLL_MEDIUM_INTERVAL_MS";
    public static String POLL_SLOW_INTERVAL_MS = "POLL_SLOW_INTERVAL_MS";

    // 固定区域干净的背景
    public static String MATCHBACKGROUND = "MATCHBACKGROUND";

    public static String MATCHSIMILARITY = "MATCHSIMILARITY";

    public static String MATCHSCALEFACTOR = "MATCHSCALEFACTOR";

    public static String MATCHMETHOD = "MATCHMETHOD";
    public static String MATCH_SAMPLE_RATIO = "MATCH_SAMPLE_RATIO";
    public static final int MATCH_METHOD_RANDOM_SAMPLE = 99;
    public static final int MATCH_METHOD_RANDOM_ROI = 100;

    public static String MATCHUSEGRAY = "MATCHUSEGRAY";
    public static String MATCHUSEMASK = "MATCHUSEMASK";

    public static String MATCHUSECANNARY = "MATCHUSECANNARY";


    //    截图产生的bitmap的结果
    public static String FULLBITMAPRES = "FULL_BITMAP_RES";

    public static String CROPPEDBITMAPRES = "CROPPED_BITMAP_RES";

    public static String BBOX = "BBOX";

    public static String SLEEP_DURATION = "SLEEP_DURATION";
    public static String DELAY_SHOW_COUNTDOWN = "DELAY_SHOW_COUNTDOWN";

    // 动态延时参数
    public static String DYNAMIC_DELAY_VAR_NAME = "DYNAMIC_DELAY_VAR_NAME";

    // JumpTask 相关参数
    public static String TARGET_TASK_ID = "TARGET_TASK_ID";
    public static String TARGET_OPERATION_ID = "TARGET_OPERATION_ID";
    public static String RETURN_AFTER_COMPLETE = "RETURN_AFTER_COMPLETE";
    public static String ORIGIN_TASK_ID = "ORIGIN_TASK_ID";
    public static String ORIGIN_OPERATION_ID = "ORIGIN_OPERATION_ID";
    public static String RETURN_TO_OPERATION_ID = "RETURN_TO_OPERATION_ID";

    public static String RESULT = "result";

    public static String MATCHED = "MATCHED";

    public static String VAR_NAME = "VAR_NAME";
    public static String VAR_TYPE = "VAR_TYPE";
    public static String VAR_SOURCE_MODE = "VAR_SOURCE_MODE";
    public static String VAR_SOURCE_VALUE = "VAR_SOURCE_VALUE";
    public static String VAR_SCRIPT_CODE = "VAR_SCRIPT_CODE";
    public static String VAR_ACTION = "VAR_ACTION";
    public static String VAR_OPERAND_MODE = "VAR_OPERAND_MODE";
    public static String VAR_OPERAND_VALUE = "VAR_OPERAND_VALUE";
    public static String VAR_OPERAND_TYPE = "VAR_OPERAND_TYPE";
    public static String VAR_TEMPLATE = "VAR_TEMPLATE";
    public static String BRANCH_RULES = "BRANCH_RULES";
    public static String BRANCH_DEFAULT_NEXT = "BRANCH_DEFAULT_NEXT";
    public static String BRANCH_NEXT_ID = "BRANCH_NEXT_ID";
    public static String APP_PACKAGE = "APP_PACKAGE";
    public static String APP_LABEL = "APP_LABEL";
    public static String APP_SKIP_IF_FOREGROUND = "APP_SKIP_IF_FOREGROUND";
    public static String APP_LAUNCH_DELAY_MS = "APP_LAUNCH_DELAY_MS";
    public static String APP_CLOSE_DELAY_MS = "APP_CLOSE_DELAY_MS";
    public static String APP_CLOSE_RETURN_HOME = "APP_CLOSE_RETURN_HOME";
    public static String APP_CLOSE_KILL_BACKGROUND = "APP_CLOSE_KILL_BACKGROUND";

    // SWITCH_BRANCH 参数
    public static String SWITCH_VAR_NAME = "SWITCH_VAR_NAME";
    // SWITCH_BRANCH reuses BRANCH_RULES and BRANCH_DEFAULT_NEXT

    // LOOP 参数
    public static String LOOP_CONDITION_VAR = "LOOP_CONDITION_VAR";
    public static String LOOP_OPERATOR = "LOOP_OPERATOR";
    public static String LOOP_OPERAND = "LOOP_OPERAND";
    public static String LOOP_BODY_NEXT = "LOOP_BODY_NEXT";
    public static String LOOP_EXIT_NEXT = "LOOP_EXIT_NEXT";

    // REPEAT_EXECUTION 参数
    public static String REPEAT_START_OPERATION_ID = "REPEAT_START_OPERATION_ID";
    public static String REPEAT_INFINITE = "REPEAT_INFINITE";
    public static String REPEAT_COUNT = "REPEAT_COUNT";

    // MTry 包装节点参数
    public static String MTRY_WRAPPED_OPERATION_ID = "MTRY_WRAPPED_OPERATION_ID";
    public static String MTRY_ATTEMPTS = "MTRY_ATTEMPTS";
    public static String MTRY_RUN_RESPONSE_HANDLER = "MTRY_RUN_RESPONSE_HANDLER";
    // 每次重试之间的延时（毫秒），0 表示不延时
    public static String MTRY_RETRY_DELAY_MS = "MTRY_RETRY_DELAY_MS";
    // 重试延时期间是否显示倒计时悬浮条
    public static String MTRY_RETRY_SHOW_COUNTDOWN = "MTRY_RETRY_SHOW_COUNTDOWN";

    // Gesture 参数
    public static String GESTURE_PIPELINE = "GESTURE_PIPELINE";
    public static String GESTURE_STEP_FILE = "file";
    public static String GESTURE_STEP_DELAY_MS = "delayAfterMs";
    public static String GESTURE_TEMPLATE_ID = "GESTURE_TEMPLATE_ID";
    public static String COLOR_POINTS = "COLOR_POINTS";
    public static String COLOR_MATCH_MODE = "COLOR_MATCH_MODE";
    public static String COLOR_MATCH_MODE_ALL = "all";
    public static String COLOR_MATCH_MODE_ANY = "any";
    public static String COLOR_TOLERANCE = "COLOR_TOLERANCE";
    public static String COLOR_VALUE = "COLOR_VALUE";
    public static String COLOR_SEARCH_MIN_PIXELS = "COLOR_SEARCH_MIN_PIXELS";

    // 采集倍率节点参数
    public static String CAPTURE_SCALE_VALUE = "CAPTURE_SCALE_VALUE";

    // 模板保存目标子目录（如 "scale_100"），为空时由当前 CAPTURE_SCALE 决定
    public static String TARGET_SCALE_DIR = "TARGET_SCALE_DIR";
    public static String PRE_CAPTURED_BITMAP = "__PRE_CAPTURED_BITMAP";

    // HTTP 请求参数
    public static String HTTP_URL = "HTTP_URL";
    public static String HTTP_METHOD = "HTTP_METHOD";
    public static String HTTP_HEADERS = "HTTP_HEADERS";
    public static String HTTP_BODY = "HTTP_BODY";
    public static String HTTP_RESPONSE_VAR = "HTTP_RESPONSE_VAR";
    public static String HTTP_STATUS_VAR = "HTTP_STATUS_VAR";
    public static String HTTP_TIMEOUT_MS = "HTTP_TIMEOUT_MS";

    // OCR 参数
    public static String OCR_LANGUAGE = "OCR_LANGUAGE";
    public static String OCR_MODEL = "OCR_MODEL";
    public static String OCR_TRAINED_DATA = "OCR_TRAINED_DATA";
    public static String OCR_TARGET_TEXT = "OCR_TARGET_TEXT";
    public static String OCR_MATCH_MODE = "OCR_MATCH_MODE";
    public static String OCR_TEXT_VAR = "OCR_TEXT_VAR";
    public static String OCR_CONFIDENCE_VAR = "OCR_CONFIDENCE_VAR";
    public static String OCR_MIN_CONFIDENCE = "OCR_MIN_CONFIDENCE";
    public static String OCR_SCALE_FACTOR = "OCR_SCALE_FACTOR";
    public static String OCR_GRAYSCALE = "OCR_GRAYSCALE";
    public static String OCR_THRESHOLD = "OCR_THRESHOLD";
    public static String OCR_COMPRESS_PERCENT = "OCR_COMPRESS_PERCENT";
    public static String OCR_GAUSSIAN_BLUR = "OCR_GAUSSIAN_BLUR";
    public static String OCR_GAUSSIAN_RADIUS = "OCR_GAUSSIAN_RADIUS";
    public static String OCR_USE_MASK = "OCR_USE_MASK";
    public static String OCR_MASK_PNG = "OCR_MASK_PNG";
    public static String OCR_MASK_WIDTH = "OCR_MASK_WIDTH";
    public static String OCR_MASK_HEIGHT = "OCR_MASK_HEIGHT";
    public static String OCR_INVERT = "OCR_INVERT";
    public static String OCR_CONTRAST_PERCENT = "OCR_CONTRAST_PERCENT";
    public static String OCR_MEDIAN_DENOISE = "OCR_MEDIAN_DENOISE";
    public static String OCR_SHARPEN = "OCR_SHARPEN";
    public static String OCR_MORPH_MODE = "OCR_MORPH_MODE";
    public static String OCR_MORPH_ITERATIONS = "OCR_MORPH_ITERATIONS";
    public static String OCR_PREPROCESS_PIPELINE = "OCR_PREPROCESS_PIPELINE";
    public static String OCR_DIGITS_ONLY = "OCR_DIGITS_ONLY";
    public static String OCR_PAGE_SEG_MODE = "OCR_PAGE_SEG_MODE";
    public static String OCR_TEXT = "OCR_TEXT";
    public static String OCR_CONFIDENCE = "OCR_CONFIDENCE";
    public static final String OCR_MATCH_CONTAINS = "contains";
    public static final String OCR_MATCH_EQUALS = "equals";
    public static final String OCR_MATCH_REGEX = "regex";
    public static final String OCR_MATCH_ANY = "any";

    // 播放音频参数
    public static String AUDIO_FILE_PATH = "AUDIO_FILE_PATH";
    public static String AUDIO_WAIT_COMPLETE = "AUDIO_WAIT_COMPLETE";

    // 日志输出参数
    public static String LOG_MESSAGE = "LOG_MESSAGE";
    public static String LOG_LEVEL = "LOG_LEVEL";

    // 修改系统参数
    public static String SYS_PARAM_KEY = "SYS_PARAM_KEY";
    public static String SYS_PARAM_VALUE = "SYS_PARAM_VALUE";
    // SYS_PARAM_KEY 的可选值
    public static final String SYS_PARAM_CAPTURE_SCALE = "capture_scale";
    public static final String SYS_PARAM_COUNTDOWN_COLOR = "countdown_color";
    public static final String SYS_PARAM_GESTURE_COLOR = "gesture_color";
    public static final String SYS_PARAM_RUNTIME_LOG_ENABLED = "runtime_log_enabled";

    // 修改屏幕亮度参数（百分比 1-100）
    public static String BRIGHTNESS_PERCENT = "BRIGHTNESS_PERCENT";
    public static final int DEFAULT_SCREEN_BRIGHTNESS_PERCENT = 50;

    // AI 目标检测参数
    public static String AI_MODEL_PATH = "AI_MODEL_PATH";
    public static String AI_LABELS_PATH = "AI_LABELS_PATH";
    public static String AI_INPUT_SIZE = "AI_INPUT_SIZE";
    public static String AI_MIN_CONFIDENCE = "AI_MIN_CONFIDENCE";
    public static String AI_IOU_THRESHOLD = "AI_IOU_THRESHOLD";
    public static String AI_MAX_RESULTS = "AI_MAX_RESULTS";
    public static String AI_TARGET_LABEL = "AI_TARGET_LABEL";
    public static String AI_RESULT_VAR = "AI_RESULT_VAR";
    public static String AI_BBOX_VAR = "AI_BBOX_VAR";
    public static String AI_CONFIDENCE_VAR = "AI_CONFIDENCE_VAR";
    public static String AI_LABEL_VAR = "AI_LABEL_VAR";
    public static String AI_CENTER_VAR = "AI_CENTER_VAR";
    public static String AI_DETECTIONS = "AI_DETECTIONS";
    public static String AI_LABEL = "AI_LABEL";
    public static String AI_CONFIDENCE = "AI_CONFIDENCE";
    public static String AI_CENTER = "AI_CENTER";

    // 截图区域 → 变量 参数（responseType=4）
    // base64 结果存入的变量名
    public static String CROP_RESULT_VAR = "CROP_RESULT_VAR";
    // 编码格式: "jpeg" | "png"（默认 "jpeg"）
    public static String CROP_FORMAT = "CROP_FORMAT";
    // JPEG 质量 0-100（默认 80，PNG 时忽略）
    public static String CROP_QUALITY = "CROP_QUALITY";
    // 是否在 base64 前加 data URI 前缀（默认 false）
    public static String CROP_DATA_URI = "CROP_DATA_URI";

    // 无障碍节点 (AccessibilityNode) 参数
    // 查找模式: "text" | "textContains" | "textStartsWith" | "viewId" | "contentDesc" | "contentDescContains" | "className"
    public static String A11Y_FIND_MODE = "A11Y_FIND_MODE";
    // 查找值，支持 ${变量名} 模板替换
    public static String A11Y_FIND_VALUE = "A11Y_FIND_VALUE";
    // 可选：限定在指定包名内查找（如 com.example.app）
    public static String A11Y_PACKAGE_FILTER = "A11Y_PACKAGE_FILTER";
    // 动作: "click" | "longClick" | "getText" | "getDesc" | "setText" | "scrollUp" | "scrollDown" | "focus" | "exists"
    public static String A11Y_ACTION = "A11Y_ACTION";
    // setText 动作时的文字内容，支持 ${变量名}
    public static String A11Y_ACTION_TEXT = "A11Y_ACTION_TEXT";
    // getText / getDesc / exists 动作时结果存入的变量名
    public static String A11Y_RESULT_VAR = "A11Y_RESULT_VAR";
    // 当匹配到多个节点时取第几个（0=第一个）
    public static String A11Y_MATCH_INDEX = "A11Y_MATCH_INDEX";
    // 等待节点出现的超时（毫秒），0=不等待直接失败
    public static String A11Y_TIMEOUT_MS = "A11Y_TIMEOUT_MS";
    // 等待轮询间隔（毫秒）
    public static String A11Y_POLL_INTERVAL_MS = "A11Y_POLL_INTERVAL_MS";
    // 点击/长按前是否先将节点滚动至可见（true/false）
    public static String A11Y_SCROLL_INTO_VIEW = "A11Y_SCROLL_INTO_VIEW";


    // 无参构造方法 - Gson 必需
    public MetaOperation() {}


    //    1、type   --->>> Operation handler
    private Integer type;

    public MetaOperation(Integer type, Integer responseType, Map<String, Object> inputMap, String taskId, String id, String name) {
        this.type = type;
        this.responseType = responseType;
        this.inputMap = inputMap;
        this.taskId = taskId;
        this.id = id;
        this.name = name;
    }

    public Integer getType() {
        return type;
    }

    //    2、 responseType   --->> responseHandler
    private Integer responseType = 1;
    //    默认值
    public Integer getResponseType() {
        return responseType;
    }

    //     3、 predefined half res that will be used in handler or responseHandler
    private Map<String, Object> inputMap = new HashMap<>();


    //    这个operation属于的 task
    public String taskId;


    public Map<String, Object> getInputMap() {
        return inputMap;
    }



    private String id;

    //    operation的名字
    private String name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    //    operation唯一id



    public void setType(Integer type) {
        this.type = type;
    }

    public void setResponseType(Integer responseType) {
        this.responseType = responseType;
    }

    public void setInputMap(Map<String, Object> inputMap) {
        this.inputMap = inputMap;
    }
}
