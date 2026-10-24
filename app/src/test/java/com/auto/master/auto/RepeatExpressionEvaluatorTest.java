package com.auto.master.auto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RepeatExpressionEvaluatorTest {

    @Test
    public void evaluatesTopLevelVariablesWithBothSupportedSyntaxes() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("retryCount", 3);
        variables.put("times", 10);

        assertTrue(RepeatExpressionEvaluator.shouldContinue("${retryCount} < 5", variables));
        assertTrue(RepeatExpressionEvaluator.shouldContinue("retryCount < 5", variables));
        assertTrue(RepeatExpressionEvaluator.shouldContinue("${times}>0", variables));
    }

    @Test
    public void evaluatesNestedMapFromConfigUi() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("limit", 5);
        Map<String, Object> variables = new HashMap<>();
        variables.put("configMap", configMap);

        assertTrue(RepeatExpressionEvaluator.shouldContinue(
                "${configMap.limit} < 6", variables));
        assertTrue(RepeatExpressionEvaluator.shouldContinue(
                "vars.configMap.limit < 6", variables));
    }

    @Test
    public void evaluatesListAndPrimitiveArrayIndexes() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("items", Arrays.asList(2, 4));
        variables.put("attempts", new int[]{1, 3});

        assertTrue(RepeatExpressionEvaluator.shouldContinue("${items[1]} == 4", variables));
        assertTrue(RepeatExpressionEvaluator.shouldContinue("vars.attempts[0] == 1", variables));
    }

    @Test
    public void keepsExactDottedVariableNameCompatibility() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("config.limit", 5);

        assertTrue(RepeatExpressionEvaluator.shouldContinue(
                "${config.limit} === 5", variables));
    }

    @Test
    public void reportsMissingTemplateVariableInsteadOfSilentlyReturningFalse() {
        RepeatExpressionEvaluator.EvaluationResult result =
                RepeatExpressionEvaluator.evaluate("${configMap.limit} < 6", new HashMap<>());

        assertFalse(result.shouldContinue);
        assertTrue(result.hasError());
        assertTrue(result.errorMessage.contains("configMap.limit"));
    }

    @Test
    public void validatesExpressionSyntaxBeforeSavingNode() {
        assertTrue(RepeatExpressionEvaluator.validateSyntax("${retryCount} < 5").isEmpty());
        assertFalse(RepeatExpressionEvaluator.validateSyntax("${retryCount} <").isEmpty());
    }

    @Test
    public void executesNextRoundUpdateAndPersistsChangedVariable() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("times", 10L);

        RepeatExpressionEvaluator.UpdateResult result =
                RepeatExpressionEvaluator.executeNextRoundUpdate(
                        "times = times + 1", variables);

        assertTrue(result.success);
        assertEquals(11L, variables.get("times"));
    }

    @Test
    public void nextRoundUpdateSupportsTemplateAndNestedVariables() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("times", 2L);
        Map<String, Object> variables = new HashMap<>();
        variables.put("times", 4L);
        variables.put("configMap", configMap);

        assertTrue(RepeatExpressionEvaluator.executeNextRoundUpdate(
                "${times} = ${times} + 1; configMap.times = configMap.times + 1",
                variables).success);

        assertEquals(5L, variables.get("times"));
        assertEquals(3L, ((Map<?, ?>) variables.get("configMap")).get("times"));
    }

    @Test
    public void validatesNextRoundUpdateSyntax() {
        assertTrue(RepeatExpressionEvaluator
                .validateNextRoundUpdateSyntax("times = times + 1").isEmpty());
        assertFalse(RepeatExpressionEvaluator
                .validateNextRoundUpdateSyntax("times =").isEmpty());
    }
}
