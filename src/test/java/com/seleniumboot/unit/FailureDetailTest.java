package com.seleniumboot.unit;

import com.seleniumboot.failure.FailureDetail;
import com.seleniumboot.flakiness.TestStatus;
import com.seleniumboot.healing.HealEvent;
import com.seleniumboot.healing.HealLog;
import com.seleniumboot.metrics.TestTiming;
import com.seleniumboot.steps.StepRecord;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FailureDetailTest {

    @BeforeMethod
    @AfterMethod
    public void clearHeals() {
        HealLog.clear();
    }

    @Test
    public void recordContainsAvailableFieldsAndOmitsMissingOnes() {
        TestTiming timing = timing("example.CheckoutTest.submit");
        Map<String, Object> detail = FailureDetail.from(timing, TestStatus.NEWLY_FAILING, 2, timing).toMap();

        Assert.assertEquals(detail.get("failingLocator"), "By.cssSelector: #submit");
        Assert.assertEquals(detail.get("applicationFrame"), "example.CheckoutPage.submit(CheckoutPage.java:42)");
        Assert.assertEquals(detail.get("lastSuccessfulStep"), "Fill payment details");
        Assert.assertEquals(detail.get("pageUrl"), "https://shop.example/checkout");
        Assert.assertEquals(detail.get("pageTitle"), "Checkout");
        Assert.assertEquals(detail.get("testStatus"), "NEWLY_FAILING");
        Assert.assertFalse(detail.containsKey("notPresent"));

        Map<?, ?> group = (Map<?, ?>) detail.get("groupContext");
        Assert.assertEquals(group.get("size"), 2);
        Assert.assertEquals(group.get("representativeTest"), timing.getTestId());
        Assert.assertEquals(group.get("representativeScreenshot"), "screenshots/submit.png");
        Assert.assertEquals(group.get("representativeTrace"), "traces/submit.zip");
    }

    @Test
    public void recordAppliesFixedTextAndListLimits() {
        TestTiming timing = timing("example.CheckoutTest.submit");
        timing.setPageTitle("x".repeat(FailureDetail.MAX_TEXT_LENGTH + 50));
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < FailureDetail.MAX_ITEMS + 3; i++) {
            errors.add("error-" + i + "-" + "y".repeat(FailureDetail.MAX_TEXT_LENGTH));
            HealLog.record(new HealEvent(timing.getTestId(), "old-" + i, "new-" + i, "strategy"));
        }
        timing.setConsoleErrors(errors);

        Map<String, Object> detail = FailureDetail.from(timing, null, 1, timing).toMap();
        Assert.assertEquals(((String) detail.get("pageTitle")).length(), FailureDetail.MAX_TEXT_LENGTH);
        Assert.assertEquals(((List<?>) detail.get("consoleErrors")).size(), FailureDetail.MAX_ITEMS);
        Assert.assertEquals(((String) ((List<?>) detail.get("consoleErrors")).get(0)).length(),
                FailureDetail.MAX_TEXT_LENGTH);
        Assert.assertEquals(((List<?>) detail.get("healEvents")).size(), FailureDetail.MAX_ITEMS);
    }

    @Test
    public void missingValuesAreNotInvented() {
        TestTiming timing = new TestTiming("example.EmptyTest.run", "main");
        Map<String, Object> detail = FailureDetail.from(timing, null, 1, timing).toMap();

        Assert.assertFalse(detail.containsKey("failingLocator"));
        Assert.assertFalse(detail.containsKey("applicationFrame"));
        Assert.assertFalse(detail.containsKey("lastSuccessfulStep"));
        Assert.assertFalse(detail.containsKey("pageUrl"));
        Assert.assertFalse(detail.containsKey("pageTitle"));
        Assert.assertFalse(detail.containsKey("healEvents"));
        Assert.assertFalse(detail.containsKey("consoleErrors"));
        Assert.assertFalse(detail.containsKey("testStatus"));
    }

    @Test
    public void caughtLocatorTimeoutDoesNotLabelLaterAssertion() {
        TestTiming timing = new TestTiming("example.OptionalBannerTest.run", "main");
        timing.setFailingLocator("By.id: optional-banner");
        timing.setErrorMessage("expected [ready] but found [failed]");
        timing.setStackTrace("java.lang.AssertionError: expected [ready] but found [failed]\n"
                + "\tat example.OptionalBannerTest.run(OptionalBannerTest.java:42)\n");

        Map<String, Object> detail = FailureDetail.from(timing, null, 1, timing).toMap();

        Assert.assertFalse(detail.containsKey("failingLocator"));
    }

    @Test
    public void recordedLocatorRemainsFallbackForFinalWaitTimeout() {
        TestTiming timing = new TestTiming("example.CheckoutTest.submit", "main");
        timing.setFailingLocator("By.cssSelector: #submit");
        timing.setErrorMessage("Expected condition failed: element was not clickable");
        timing.setStackTrace("org.openqa.selenium.TimeoutException: element was not clickable\n"
                + "\tat com.seleniumboot.wait.WaitEngine.waitForClickable(WaitEngine.java:68)\n"
                + "\tat example.CheckoutPage.submit(CheckoutPage.java:42)\n");

        Map<String, Object> detail = FailureDetail.from(timing, null, 1, timing).toMap();

        Assert.assertEquals(detail.get("failingLocator"), "By.cssSelector: #submit");
    }

    private static TestTiming timing(String testId) {
        TestTiming timing = new TestTiming(testId, "main");
        timing.setFailingLocator("By.cssSelector: #submit");
        timing.setErrorMessage("Unable to find By.cssSelector: #submit");
        timing.setStackTrace("org.openqa.selenium.NoSuchElementException: missing\n"
                + "\tat com.seleniumboot.wait.WaitEngine.waitForVisible(WaitEngine.java:50)\n"
                + "\tat example.CheckoutPage.submit(CheckoutPage.java:42)\n");
        timing.addStep(new StepRecord("Open checkout", 10, "INFO", null));
        timing.addStep(new StepRecord("Fill payment details", 20, "PASS", null));
        timing.addStep(new StepRecord("Submit order", 30, "FAIL", null));
        timing.setPageUrl("https://shop.example/checkout");
        timing.setPageTitle("Checkout");
        timing.setScreenshotPath("screenshots/submit.png");
        timing.setTracePath("traces/submit.zip");
        timing.setConsoleErrors(List.of("ReferenceError: checkout is not defined"));
        HealLog.record(new HealEvent(testId, "By.id: old", "By.id: new", "id"));
        return timing;
    }
}
