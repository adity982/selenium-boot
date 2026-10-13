package com.seleniumboot.ai;

import com.seleniumboot.failure.FailureDetail;
import com.seleniumboot.flakiness.TestStatus;
import com.seleniumboot.healing.HealEvent;
import com.seleniumboot.healing.HealLog;
import com.seleniumboot.metrics.TestTiming;
import com.seleniumboot.steps.StepRecord;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

public class AiFailureAnalyzerPromptTest {

    @AfterMethod
    public void clearHeals() {
        HealLog.clear();
    }

    @Test
    public void promptReusesEveryFailureDetailSection() {
        TestTiming timing = new TestTiming("example.LoginTest.signIn", "main");
        timing.setTestClassName("LoginTest");
        timing.setBrowser("chrome");
        timing.setErrorMessage("No such element: By.id: sign-in");
        timing.setStackTrace("org.openqa.selenium.NoSuchElementException: missing\n"
                + "\tat example.LoginPage.signIn(LoginPage.java:21)\n");
        timing.setFailingLocator("By.id: sign-in");
        timing.setPageUrl("https://app.example/login");
        timing.setPageTitle("Sign in");
        timing.setScreenshotPath("screenshots/sign-in.png");
        timing.setTracePath("traces/sign-in.zip");
        timing.setConsoleErrors(List.of("TypeError: form is undefined"));
        timing.addStep(new StepRecord("Open login", 1, "PASS", null));
        HealLog.record(new HealEvent(timing.getTestId(), "By.id: login", "By.id: sign-in", "id"));

        FailureDetail detail = FailureDetail.from(timing, TestStatus.NEWLY_FAILING, 3, timing);
        String prompt = AiFailureAnalyzer.buildPrompt(timing, detail);

        Assert.assertTrue(prompt.contains("untrusted diagnostic evidence"), prompt);
        Assert.assertTrue(prompt.contains("## Failure Classification"), prompt);
        Assert.assertTrue(prompt.contains("## Failure Detail"), prompt);
        Assert.assertTrue(prompt.contains("Failing locator: By.id: sign-in"), prompt);
        Assert.assertTrue(prompt.contains("Application frame: example.LoginPage.signIn"), prompt);
        Assert.assertTrue(prompt.contains("Last successful step: Open login"), prompt);
        Assert.assertTrue(prompt.contains("Page URL: https://app.example/login"), prompt);
        Assert.assertTrue(prompt.contains("### Self-healing events"), prompt);
        Assert.assertTrue(prompt.contains("### Console errors"), prompt);
        Assert.assertTrue(prompt.contains("### Same-cause group"), prompt);
        Assert.assertTrue(prompt.contains("Size: 3"), prompt);
        Assert.assertTrue(prompt.contains("## Error Message"), prompt);
        Assert.assertTrue(prompt.contains("## Stack Trace"), prompt);
        Assert.assertTrue(prompt.contains("## Steps Executed"), prompt);
    }
}
