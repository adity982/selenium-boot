package com.seleniumboot.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seleniumboot.metrics.ExecutionMetrics;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

public class FailureDetailExportTest {

    @BeforeMethod
    @AfterMethod
    public void reset() {
        ExecutionMetrics.reset();
    }

    @Test
    public void failedTestsExportBoundedRecordAndSharedRepresentative() throws Exception {
        recordFailure("example.FirstTest.run", "screenshots/first.png", "traces/first.zip");
        recordFailure("example.SecondTest.run", null, null);

        ExecutionMetrics.exportToJson();
        JsonNode tests = new ObjectMapper()
                .readTree(new File("target/selenium-boot-metrics.json")).path("tests");

        Assert.assertEquals(tests.size(), 2);
        for (JsonNode test : tests) {
            JsonNode detail = test.path("failureDetail");
            Assert.assertEquals(detail.path("failingLocator").asText(), "By.id: submit");
            Assert.assertEquals(detail.path("pageUrl").asText(), "https://app.example/checkout");
            Assert.assertEquals(detail.path("consoleErrors").get(0).asText(), "ReferenceError: submit is missing");
            Assert.assertEquals(detail.path("groupContext").path("size").asInt(), 2);
            Assert.assertEquals(detail.path("groupContext").path("representativeTest").asText(),
                    "example.FirstTest.run");
            Assert.assertEquals(detail.path("groupContext").path("representativeScreenshot").asText(),
                    "screenshots/first.png");
            Assert.assertTrue(test.has("errorMessage"));
            Assert.assertTrue(test.has("stackTrace"));
        }
    }

    private static void recordFailure(String testId, String screenshot, String trace) {
        ExecutionMetrics.markStart(testId);
        ExecutionMetrics.recordStatus(testId, "FAILED");
        ExecutionMetrics.recordError(testId, new IllegalStateException("checkout failed"));
        ExecutionMetrics.recordFailingLocator(testId, "By.id: submit");
        ExecutionMetrics.recordPageContext(testId, "https://app.example/checkout", "Checkout");
        ExecutionMetrics.recordConsoleErrors(testId, List.of("ReferenceError: submit is missing"));
        ExecutionMetrics.recordScreenshot(testId, screenshot);
        ExecutionMetrics.recordTracePath(testId, trace);
        ExecutionMetrics.markEnd(testId);
    }
}
