package com.seleniumboot.unit;

import com.seleniumboot.config.SeleniumBootConfig;
import com.seleniumboot.driver.DriverManager;
import com.seleniumboot.internal.SeleniumBootContext;
import com.seleniumboot.locator.Locator;
import com.seleniumboot.locator.LocatorException;
import com.seleniumboot.locator.State;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

/**
 * {@link Locator} terminal actions must survive the page re-rendering the element between
 * resolving it and acting on it — they re-resolve instead of reusing a stale reference.
 */
public class LocatorStaleElementTest {

    private WebDriver driver;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<SeleniumBootContext> contextMock;

    @BeforeMethod
    public void setup() {
        driver = mock(WebDriver.class);
        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(driver);

        SeleniumBootConfig.Timeouts timeouts = new SeleniumBootConfig.Timeouts();
        timeouts.setExplicit(2);
        SeleniumBootConfig config = new SeleniumBootConfig();
        config.setTimeouts(timeouts);
        contextMock = mockStatic(SeleniumBootContext.class);
        contextMock.when(SeleniumBootContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void teardown() {
        driverManagerMock.close();
        contextMock.close();
    }

    private static WebElement liveElement() {
        WebElement el = mock(WebElement.class);
        when(el.isDisplayed()).thenReturn(true);
        when(el.isEnabled()).thenReturn(true);
        when(el.getCssValue(anyString())).thenReturn("block");
        return el;
    }

    @Test
    public void click_elementGoesStaleBetweenCheckAndClick_reResolvesAndClicksFreshElement() {
        WebElement stale = liveElement();
        doThrow(new StaleElementReferenceException("re-rendered")).when(stale).click();
        WebElement fresh = liveElement();
        By by = By.id("save");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        Locator.of(by).click();

        verify(fresh, times(1)).click();
    }

    @Test
    public void click_elementIsStaleWhileWaitingForClickable_reResolves() {
        WebElement stale = mock(WebElement.class);
        when(stale.isDisplayed()).thenThrow(new StaleElementReferenceException("detached"));
        WebElement fresh = liveElement();
        By by = By.id("save");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        Locator.of(by).click();

        verify(fresh, times(1)).click();
    }

    @Test
    public void click_elementNeverGoesStale_clicksExactlyOnce() {
        WebElement el = liveElement();
        By by = By.id("save");
        when(driver.findElements(by)).thenReturn(List.of(el));

        Locator.of(by).click();

        verify(el, times(1)).click();
        assertEquals(mockingDetails(driver).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("findElements")).count(), 1L);
    }

    // ------------------------------------------------------------------
    // Presence wait + the remaining terminal actions
    // ------------------------------------------------------------------

    @Test
    public void click_elementNotInDomYet_waitsForItToAppear() {
        WebElement fresh = liveElement();
        By by = By.id("late");
        when(driver.findElements(by)).thenReturn(List.of()).thenReturn(List.of()).thenReturn(List.of(fresh));

        Locator.of(by).click();

        verify(fresh, times(1)).click();
    }

    @Test
    public void click_elementNeverAppears_throwsLocatorExceptionCausedByTimeout() {
        By by = By.id("never");
        when(driver.findElements(by)).thenReturn(List.of());

        try {
            Locator.of(by).click();
            org.testng.Assert.fail("expected LocatorException");
        } catch (com.seleniumboot.locator.LocatorException e) {
            org.testng.Assert.assertTrue(e.getMessage().contains("No element found"), e.getMessage());
            org.testng.Assert.assertTrue(e.getMessage().contains("waited 2s"), e.getMessage());
            org.testng.Assert.assertTrue(e.getCause() instanceof org.openqa.selenium.TimeoutException);
        }
    }

    @Test
    public void getText_elementGoesStale_reResolves() {
        WebElement stale = mock(WebElement.class);
        when(stale.isDisplayed()).thenReturn(true);
        when(stale.getText()).thenThrow(new StaleElementReferenceException("re-rendered"));
        WebElement fresh = liveElement();
        when(fresh.getText()).thenReturn("  hello ");
        By by = By.id("label");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        assertEquals(Locator.of(by).getText(), "hello");
    }

    @Test
    public void getAttribute_nullValueIsReturnedNotRetried() {
        WebElement el = liveElement();
        when(el.getAttribute("data-x")).thenReturn(null);
        By by = By.id("attr");
        when(driver.findElements(by)).thenReturn(List.of(el));

        org.testng.Assert.assertNull(Locator.of(by).getAttribute("data-x"));
        verify(el, times(1)).getAttribute("data-x");
    }

    @Test
    public void type_elementGoesStale_reResolvesAndTypesIntoFreshElement() {
        WebElement stale = liveElement();
        doThrow(new StaleElementReferenceException("re-rendered")).when(stale).clear();
        WebElement fresh = liveElement();
        By by = By.id("name");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        Locator.of(by).type("abc");

        verify(fresh).clear();
        verify(fresh).sendKeys("abc");
    }

    @Test
    public void append_elementGoesStale_reResolves() {
        WebElement stale = liveElement();
        doThrow(new StaleElementReferenceException("re-rendered")).when(stale).sendKeys("x");
        WebElement fresh = liveElement();
        By by = By.id("name");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        Locator.of(by).append("x");

        verify(fresh).sendKeys("x");
    }

    @Test
    public void jsClick_elementGoesStale_reResolves() {
        WebDriver jsDriver = mock(WebDriver.class, withSettings().extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) jsDriver;
        driverManagerMock.when(DriverManager::getDriver).thenReturn(jsDriver);
        WebElement stale = liveElement();
        WebElement fresh = liveElement();
        By by = By.id("obscured");
        when(jsDriver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));
        when(js.executeScript(anyString(), eq(stale))).thenThrow(new StaleElementReferenceException("re-rendered"));

        Locator.of(by).jsClick();

        verify(js).executeScript(anyString(), eq(fresh));
    }

    @Test
    public void clear_elementGoesStale_reResolvesAndClearsFreshElement() {
        WebElement stale = liveElement();
        doThrow(new StaleElementReferenceException("re-rendered")).when(stale).clear();
        WebElement fresh = liveElement();
        By by = By.id("name");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        Locator.of(by).clear();

        verify(fresh).clear();
    }

    @Test
    public void clear_elementNeverAppears_throwsLocatorExceptionCausedByTimeout() {
        By by = By.id("never");
        when(driver.findElements(by)).thenReturn(List.of());

        try {
            Locator.of(by).clear();
            org.testng.Assert.fail("expected LocatorException");
        } catch (com.seleniumboot.locator.LocatorException e) {
            org.testng.Assert.assertTrue(e.getMessage().contains("No element found"), e.getMessage());
            org.testng.Assert.assertTrue(e.getMessage().contains("waited 2s"), e.getMessage());
            org.testng.Assert.assertTrue(e.getCause() instanceof org.openqa.selenium.TimeoutException);
        }
    }

    @Test
    public void check_elementGoesStale_reResolvesAndChecksFreshElement() {
        WebElement stale = liveElement();
        when(stale.isSelected()).thenReturn(false);
        doThrow(new StaleElementReferenceException("re-rendered")).when(stale).click();
        WebElement fresh = liveElement();
        when(fresh.isSelected()).thenReturn(false);
        By by = By.id("agree");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        Locator.of(by).check();

        verify(fresh).click();
    }

    @Test
    public void uncheck_elementGoesStale_reResolvesAndUnchecksFreshElement() {
        WebElement stale = liveElement();
        when(stale.isSelected()).thenReturn(true);
        doThrow(new StaleElementReferenceException("re-rendered")).when(stale).click();
        WebElement fresh = liveElement();
        when(fresh.isSelected()).thenReturn(true);
        By by = By.id("newsletter");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        Locator.of(by).uncheck();

        verify(fresh).click();
    }

    @Test
    public void selectOption_elementGoesStale_reResolves() {
        WebElement staleSelect = liveElement();
        when(staleSelect.getTagName()).thenReturn("select");
        when(staleSelect.findElements(any(By.class)))
                .thenThrow(new StaleElementReferenceException("re-rendered"));

        WebElement freshSelect = liveElement();
        when(freshSelect.getTagName()).thenReturn("select");
        WebElement opt = liveElement();
        when(freshSelect.findElements(By.xpath(".//option[normalize-space(.) = \"UK\"]")))
                .thenReturn(List.of(opt));

        By by = By.id("country");
        when(driver.findElements(by)).thenReturn(List.of(staleSelect)).thenReturn(List.of(freshSelect));

        Locator.of(by).selectOption("UK");

        verify(opt, times(1)).click();
    }

    @Test
    public void selectOptionByValue_elementGoesStale_reResolves() {
        WebElement staleSelect = liveElement();
        when(staleSelect.getTagName()).thenReturn("select");
        when(staleSelect.findElements(any(By.class)))
                .thenThrow(new StaleElementReferenceException("re-rendered"));

        WebElement freshSelect = liveElement();
        when(freshSelect.getTagName()).thenReturn("select");
        WebElement opt = liveElement();
        when(freshSelect.findElements(By.xpath(".//option[@value = \"uk\"]")))
                .thenReturn(List.of(opt));

        By by = By.id("country");
        when(driver.findElements(by)).thenReturn(List.of(staleSelect)).thenReturn(List.of(freshSelect));

        Locator.of(by).selectOptionByValue("uk");

        verify(opt, times(1)).click();
    }

    @Test
    public void inputValue_elementGoesStale_reResolves() {
        WebElement stale = liveElement();
        when(stale.getDomProperty("value"))
                .thenThrow(new StaleElementReferenceException("re-rendered"));
        WebElement fresh = liveElement();
        when(fresh.getDomProperty("value")).thenReturn("admin");
        By by = By.id("user");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        assertEquals(Locator.of(by).inputValue(), "admin");
    }

    @Test
    public void last_elementGoesStaleBetweenCheckAndClick_reResolvesAndClicksFreshLastElement() {
        WebElement other = liveElement();
        WebElement staleLast = liveElement();
        doThrow(new StaleElementReferenceException("re-rendered")).when(staleLast).click();
        WebElement freshLast = liveElement();
        By by = By.cssSelector(".items");
        when(driver.findElements(by))
                .thenReturn(List.of(other, staleLast))
                .thenReturn(List.of(other, freshLast));

        Locator.of(by).last().click();

        verify(freshLast, times(1)).click();
        verify(staleLast, times(1)).click();
    }

    @Test
    public void last_elementNotInDomYet_waitsForItToAppear() {
        WebElement el1 = liveElement();
        WebElement el2 = liveElement();
        By by = By.cssSelector(".late-items");
        when(driver.findElements(by))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(el1, el2));

        Locator.of(by).last().click();

        verify(el2, times(1)).click();
    }

    @Test
    public void first_elementGoesStaleBetweenCheckAndClick_reResolvesAndClicksFreshFirstElement() {
        WebElement staleFirst = liveElement();
        doThrow(new StaleElementReferenceException("re-rendered")).when(staleFirst).click();
        WebElement freshFirst = liveElement();
        WebElement other = liveElement();
        By by = By.cssSelector(".items");
        when(driver.findElements(by))
                .thenReturn(List.of(staleFirst, other))
                .thenReturn(List.of(freshFirst, other));

        Locator.of(by).first().click();

        verify(freshFirst, times(1)).click();
        verify(staleFirst, times(1)).click();
    }

    @Test
    public void waitFor_visible_elementGoesStale_reResolves() {
        WebElement stale = mock(WebElement.class);
        when(stale.isDisplayed()).thenThrow(new StaleElementReferenceException("detached"));
        WebElement fresh = liveElement();
        By by = By.id("status");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        Locator.of(by).waitFor(State.VISIBLE);

        verify(fresh, atLeastOnce()).isDisplayed();
    }

    @Test
    public void waitFor_hidden_elementGoesStaleWhileChecking_reResolves() {
        WebElement stale = mock(WebElement.class);
        when(stale.isDisplayed()).thenThrow(new StaleElementReferenceException("detached"));
        By by = By.id("loader");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of());

        Locator.of(by).waitFor(State.HIDDEN);

        verify(driver, times(2)).findElements(by);
    }

    @Test(expectedExceptions = LocatorException.class)
    public void waitFor_hidden_elementGoesStaleAndReRendersStillDisplayed_throwsLocatorException() {
        WebElement stale = mock(WebElement.class);
        when(stale.isDisplayed()).thenThrow(new StaleElementReferenceException("re-rendered"));
        WebElement freshVisible = liveElement();
        By by = By.id("spinner");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(freshVisible));

        Locator.of(by).waitFor(State.HIDDEN);
    }

    @Test
    public void allTextContents_elementGoesStaleBetweenResolveAndGetText_reResolves() {
        WebElement el1 = liveElement();
        when(el1.getText()).thenReturn("Alpha");
        WebElement staleEl2 = liveElement();
        when(staleEl2.getText()).thenThrow(new StaleElementReferenceException("re-rendered"));

        WebElement freshEl1 = liveElement();
        when(freshEl1.getText()).thenReturn("Alpha");
        WebElement freshEl2 = liveElement();
        when(freshEl2.getText()).thenReturn("Beta");

        By by = By.cssSelector(".item");
        when(driver.findElements(by))
                .thenReturn(List.of(el1, staleEl2))
                .thenReturn(List.of(freshEl1, freshEl2));

        assertEquals(Locator.of(by).allTextContents(), List.of("Alpha", "Beta"));
        verify(driver, times(2)).findElements(by);
    }

    @Test
    public void screenshot_elementGoesStaleBetweenCheckAndCapture_reResolvesAndCaptures() {
        WebElement stale = liveElement();
        when(stale.getScreenshotAs(OutputType.BYTES))
                .thenThrow(new StaleElementReferenceException("re-rendered"));
        WebElement fresh = liveElement();
        byte[] expected = new byte[]{1, 2, 3};
        when(fresh.getScreenshotAs(OutputType.BYTES)).thenReturn(expected);

        By by = By.id("card");
        when(driver.findElements(by)).thenReturn(List.of(stale)).thenReturn(List.of(fresh));

        byte[] result = Locator.of(by).screenshot();

        assertEquals(result, expected);
        verify(stale, times(1)).getScreenshotAs(OutputType.BYTES);
        verify(fresh, times(1)).getScreenshotAs(OutputType.BYTES);
    }
}
