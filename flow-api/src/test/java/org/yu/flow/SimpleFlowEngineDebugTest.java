package org.yu.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.yu.flow.engine.evaluator.ExecutionResult;
import org.yu.flow.engine.evaluator.FlowEngine;

import java.util.HashMap;

public class SimpleFlowEngineDebugTest {

    @Test
    void debugSimple() throws JsonProcessingException {
        FlowEngine engine = new FlowEngine();

        String flowJson = "{\n" +
                "  \"id\": \"test-set-var\",\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"startStepId\": \"set1\",\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"id\": \"set1\",\n" +
                "      \"type\": \"set\",\n" +
                "      \"expression\": \"message='Hello World'\",\n" +
                "      \"next\": {\"out\": \"return1\"}\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"return1\",\n" +
                "      \"type\": \"return\",\n" +
                "      \"value\": \"['message']\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        try {
            ExecutionResult result = engine.execute(flowJson, new HashMap<>());
            if (!result.isSuccess()) {
                throw new AssertionError("Flow failed: Code=" + result.getCode() + ", Message=" + result.getMessage());
            }
            System.out.println("Success: " + result.isSuccess());
            System.out.println("Code: " + result.getCode());
            System.out.println("Message: " + result.getMessage());
            System.out.println("Data: " + result.getData());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
