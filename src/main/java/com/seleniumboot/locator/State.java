package com.seleniumboot.locator;

import com.seleniumboot.api.SeleniumBootApi;

/**
 * Element lifecycle states for {@link Locator#waitFor(State)}.
 *
 * <p>Mirrors Playwright's {@code locator.waitFor({ state })}:
 * <ul>
 *   <li>{@link #VISIBLE} — element is attached to the DOM and displayed.</li>
 *   <li>{@link #HIDDEN} — element is either detached from the DOM or not displayed.</li>
 *   <li>{@link #ATTACHED} — element is present in the DOM (regardless of visibility).</li>
 *   <li>{@link #DETACHED} — element is not present in the DOM.</li>
 * </ul>
 *
 * @since 3.9.0
 */
@SeleniumBootApi(since = "3.9.0")
public enum State {

    /**
     * Element is present in the DOM and displayed to the user.
     */
    VISIBLE,

    /**
     * Element is either detached from the DOM or hidden (e.g. {@code display: none},
     * {@code visibility: hidden}, or opacity 0).
     */
    HIDDEN,

    /**
     * Element is present in the DOM, regardless of its visibility.
     */
    ATTACHED,

    /**
     * Element is not present in the DOM.
     */
    DETACHED
}
