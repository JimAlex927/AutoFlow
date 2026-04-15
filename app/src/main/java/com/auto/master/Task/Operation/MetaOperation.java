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
    public static String MATCH_PRE_DELAY_MS = "MATCH_PRE_DELAY_MS";
    public static String MATCH_POST_DELAY_MS = "MATCH_POST_DELAY_MS";

    // 固定区域干净的背景
    public static String MATCHBACKGROUND = "MATCHBACKGROUND";

    public static String MATCHSIMILARITY = "MATCHSIMILARITY";

    public static String MATCHSCALEFACTOR = "MATCHSCALEFACTOR";

    public static String MATCHMETHOD = "MATCHMETHOD";

    public static String MATCHUSEGRAY = "MATCHUSEGRAY";

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

    public static String OCR_TEXT = "OCR_TEXT";
    public static String OCR_TARGET_TEXT = "OCR_TARGET_TEXT";
    public static String OCR_MATCH_MODE = "OCR_MATCH_MODE";
    public static String OCR_TIMEOUT = "OCR_TIMEOUT";
    public static String OCR_OUTPUT_VAR = "OCR_OUTPUT_VAR";
    public static String OCR_ENGINE = "OCR_ENGINE";

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

    // SWITCH_BRANCH 参数
    public static String SWITCH_VAR_NAME = "SWITCH_VAR_NAME";
    // SWITCH_BRANCH reuses BRANCH_RULES and BRANCH_DEFAULT_NEXT

    // LOOP 参数
    public static String LOOP_CONDITION_VAR = "LOOP_CONDITION_VAR";
    public static String LOOP_OPERATOR = "LOOP_OPERATOR";
    public static String LOOP_OPERAND = "LOOP_OPERAND";
    public static String LOOP_BODY_NEXT = "LOOP_BODY_NEXT";
    public static String LOOP_EXIT_NEXT = "LOOP_EXIT_NEXT";

    // Gesture 参数
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

    // HTTP 请求参数
    public static String HTTP_URL = "HTTP_URL";
    public static String HTTP_METHOD = "HTTP_METHOD";
    public static String HTTP_HEADERS = "HTTP_HEADERS";
    public static String HTTP_BODY = "HTTP_BODY";
    public static String HTTP_RESPONSE_VAR = "HTTP_RESPONSE_VAR";
    public static String HTTP_STATUS_VAR = "HTTP_STATUS_VAR";
    public static String HTTP_TIMEOUT_MS = "HTTP_TIMEOUT_MS";


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
