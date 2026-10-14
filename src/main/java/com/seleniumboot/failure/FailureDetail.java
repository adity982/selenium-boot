package com.seleniumboot.failure;

import com.seleniumboot.flakiness.TestStatus;
import com.seleniumboot.healing.HealEvent;
import com.seleniumboot.healing.HealLog;
import com.seleniumboot.metrics.TestTiming;
import com.seleniumboot.steps.StepRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded, provider-neutral context for one failed test.
 *
 * <p>The same record is exported to metrics, rendered in the HTML report, and supplied to the
 * optional AI analyzer. Missing values are omitted instead of being represented by placeholders.
 */
public final class FailureDetail {

    public static final int MAX_TEXT_LENGTH = 500;
    public static final int MAX_URL_LENGTH = 2_048;
    public static final int MAX_ITEMS = 10;

    private final String failingLocator;
    private final String applicationFrame;
    private final String lastSuccessfulStep;
    private final String pageUrl;
    private final String pageTitle;
    private final List<Map<String, String>> healEvents;
    private final List<String> consoleErrors;
    private final String testStatus;
    private final Map<String, Object> groupContext;

    private FailureDetail(
            String failingLocator,
            String applicationFrame,
            String lastSuccessfulStep,
            String pageUrl,
            String pageTitle,
            List<Map<String, String>> healEvents,
            List<String> consoleErrors,
            String testStatus,
            Map<String, Object> groupContext) {
        this.failingLocator = failingLocator;
        this.applicationFrame = applicationFrame;
        this.lastSuccessfulStep = lastSuccessfulStep;
        this.pageUrl = pageUrl;
        this.pageTitle = pageTitle;
        this.healEvents = healEvents;
        this.consoleErrors = consoleErrors;
        this.testStatus = testStatus;
        this.groupContext = groupContext;
    }

    /** Builds the detail available for a failed test at the time of the call. */
    public static FailureDetail from(
            TestTiming timing,
            TestStatus status,
            int groupSize,
            TestTiming representative) {
        if (timing == null) {
            return new FailureDetail(null, null, null, null, null,
                    List.of(), List.of(), null, Map.of());
        }

        List<Map<String, String>> heals = new ArrayList<>();
        for (HealEvent event : limited(HealLog.getForTest(timing.getTestId()))) {
            Map<String, String> item = new LinkedHashMap<>();
            put(item, "originalLocator", event.getOriginalLocator(), MAX_TEXT_LENGTH);
            put(item, "healedLocator", event.getHealedLocator(), MAX_TEXT_LENGTH);
            put(item, "strategy", event.getStrategy(), MAX_TEXT_LENGTH);
            if (!item.isEmpty()) heals.add(item);
        }

        List<String> errors = new ArrayList<>();
        for (String error : limited(timing.getConsoleErrors())) {
            String value = bounded(error, MAX_TEXT_LENGTH);
            if (value != null) errors.add(value);
        }

        Map<String, Object> group = new LinkedHashMap<>();
        if (groupSize > 0) group.put("size", groupSize);
        if (representative != null) {
            put(group, "representativeTest", representative.getTestId(), MAX_TEXT_LENGTH);
            put(group, "representativeScreenshot", representative.getScreenshotPath(), MAX_URL_LENGTH);
            put(group, "representativeTrace", representative.getTracePath(), MAX_URL_LENGTH);
        }

        return new FailureDetail(
                bounded(failingLocator(timing), MAX_TEXT_LENGTH),
                bounded(applicationFrame(timing.getStackTrace()), MAX_TEXT_LENGTH),
                bounded(lastSuccessfulStep(timing.getSteps()), MAX_TEXT_LENGTH),
                bounded(timing.getPageUrl(), MAX_URL_LENGTH),
                bounded(timing.getPageTitle(), MAX_TEXT_LENGTH),
                Collections.unmodifiableList(heals),
                Collections.unmodifiableList(errors),
                status != null ? status.name() : null,
                Collections.unmodifiableMap(group));
    }

    private static String failingLocator(TestTiming timing) {
        String finalErrorLocator = locatorFrom(timing.getErrorMessage());
        if (finalErrorLocator != null && !finalErrorLocator.isBlank()) {
            return finalErrorLocator;
        }

        FailureCategory category = FailureClassifier.classify(timing).category();
        if (category != FailureCategory.LOCATOR_NOT_FOUND && category != FailureCategory.TIMEOUT) {
            return null;
        }
        return hasLocatorWaitFrame(timing.getStackTrace()) ? timing.getFailingLocator() : null;
    }

    private static boolean hasLocatorWaitFrame(String stackTrace) {
        if (stackTrace == null) return false;
        return stackTrace.contains("com.seleniumboot.locator.Locator")
                || stackTrace.contains("com.seleniumboot.wait.WaitEngine");
    }

    /** JSON-ready representation. Fields without data are deliberately absent. */
    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "failingLocator", failingLocator, MAX_TEXT_LENGTH);
        put(values, "applicationFrame", applicationFrame, MAX_TEXT_LENGTH);
        put(values, "lastSuccessfulStep", lastSuccessfulStep, MAX_TEXT_LENGTH);
        put(values, "pageUrl", pageUrl, MAX_URL_LENGTH);
        put(values, "pageTitle", pageTitle, MAX_TEXT_LENGTH);
        if (!healEvents.isEmpty()) values.put("healEvents", healEvents);
        if (!consoleErrors.isEmpty()) values.put("consoleErrors", consoleErrors);
        put(values, "testStatus", testStatus, MAX_TEXT_LENGTH);
        if (!groupContext.isEmpty()) values.put("groupContext", groupContext);
        return values;
    }

    /** Appends the same structured record to the optional AI prompt. */
    public void appendPrompt(StringBuilder prompt) {
        if (toMap().isEmpty()) return;
        prompt.append("\n## Failure Detail\n");
        line(prompt, "Failing locator", failingLocator);
        line(prompt, "Application frame", applicationFrame);
        line(prompt, "Last successful step", lastSuccessfulStep);
        line(prompt, "Page URL", pageUrl);
        line(prompt, "Page title", pageTitle);
        line(prompt, "Test status", testStatus);

        if (!healEvents.isEmpty()) {
            prompt.append("\n### Self-healing events\n");
            for (Map<String, String> event : healEvents) {
                prompt.append("- ").append(event.getOrDefault("originalLocator", "unknown"))
                        .append(" -> ").append(event.getOrDefault("healedLocator", "unknown"));
                if (event.containsKey("strategy")) {
                    prompt.append(" (").append(event.get("strategy")).append(")");
                }
                prompt.append("\n");
            }
        }

        if (!consoleErrors.isEmpty()) {
            prompt.append("\n### Console errors\n");
            for (String error : consoleErrors) prompt.append("- ").append(error).append("\n");
        }

        if (!groupContext.isEmpty()) {
            prompt.append("\n### Same-cause group\n");
            Object size = groupContext.get("size");
            if (size != null) prompt.append("- Size: ").append(size).append("\n");
            Object representativeTest = groupContext.get("representativeTest");
            if (representativeTest != null) {
                prompt.append("- Representative: ").append(representativeTest).append("\n");
            }
            Object screenshot = groupContext.get("representativeScreenshot");
            if (screenshot != null) prompt.append("- Screenshot: ").append(screenshot).append("\n");
            Object trace = groupContext.get("representativeTrace");
            if (trace != null) prompt.append("- Trace: ").append(trace).append("\n");
        }
    }

    private static String lastSuccessfulStep(List<StepRecord> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            StepRecord step = steps.get(i);
            if ("PASS".equalsIgnoreCase(step.getStatus())) return step.getName();
        }
        return null;
    }

    private static String applicationFrame(String stackTrace) {
        if (stackTrace == null) return null;
        for (String raw : stackTrace.split("\\R")) {
            String line = raw.strip();
            if (!line.startsWith("at ")) continue;
            String frame = line.substring(3);
            if (frame.startsWith("com.seleniumboot.")
                    || frame.startsWith("org.openqa.selenium.")
                    || frame.startsWith("org.testng.")
                    || frame.startsWith("org.junit.")
                    || frame.startsWith("java.")
                    || frame.startsWith("jdk.")
                    || frame.startsWith("sun.")) continue;
            return frame;
        }
        return null;
    }

    private static String locatorFrom(String message) {
        if (message == null) return null;
        String[] prefixes = {"By.cssSelector: ", "By.xpath: ", "By.id: ", "By.name: ",
                "By.className: ", "By.tagName: ", "By.linkText: ", "By.partialLinkText: ",
                "css selector: ", "xpath: ", "id: ", "name: "};
        for (String prefix : prefixes) {
            int start = message.indexOf(prefix);
            if (start < 0) continue;
            int end = message.indexOf('\n', start);
            return end < 0 ? message.substring(start) : message.substring(start, end);
        }
        return null;
    }

    private static String bounded(String value, int limit) {
        if (value == null || value.isBlank()) return null;
        String stripped = value.strip();
        return stripped.length() <= limit ? stripped : stripped.substring(0, limit);
    }

    private static <T> List<T> limited(List<T> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.subList(0, Math.min(values.size(), MAX_ITEMS));
    }

    private static void line(StringBuilder prompt, String label, String value) {
        if (value != null) prompt.append("- ").append(label).append(": ").append(value).append("\n");
    }

    private static void put(Map<String, ? super String> map, String key, String value, int limit) {
        String bounded = bounded(value, limit);
        if (bounded != null) map.put(key, bounded);
    }
}
