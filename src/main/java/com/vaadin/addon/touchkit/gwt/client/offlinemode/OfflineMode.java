package com.vaadin.addon.touchkit.gwt.client.offlinemode;

/**
 * Applications that need to have an advanced offline mode use this interface to
 * connect to TouchKit. By default the framework uses an instance of
 * {@link DefaultOfflineMode} that simply displays a sad face and a message why
 * the online app cannot be used.
 * <p>
 * Add a GWT deferred binding rule in your widgetset to replace this with your
 * own implementation. An example: <code><pre>
        <replace-with
                class="com.example.widgetset.client.MyOfflineMode">
                <when-type-is
                        class="com.vaadin.addon.touchkit.gwt.client.OfflineMode" />
        </replace-with>
 * </pre></code>
 * <p>
 */
public interface OfflineMode {

    public static final OfflineModeActivationEventImpl NO_NETWORK = new OfflineModeActivationEventImpl(
            "Offline mode because a network failure.",
            ActivationReason.NO_NETWORK);

    public static final OfflineModeActivationEventImpl UNKNOWN = new OfflineModeActivationEventImpl(
            "Offline mode because server is unreachable.",
            ActivationReason.UNKNOWN);

    public static final OfflineModeActivationEventImpl ACTIVATED_BY_REQUEST = new OfflineModeActivationEventImpl(
            "Offline mode started by a request.",
            ActivationReason.ACTIVATED_BY_REQUEST);

    public static final OfflineModeActivationEventImpl BAD_RESPONSE = new OfflineModeActivationEventImpl(
            "The response from the server seems to take a very long time. "
                    + "Either the server is down or there's a network issue.",
            ActivationReason.BAD_RESPONSE);

    public static final  OfflineModeActivationEventImpl APP_STARTING = new OfflineModeActivationEventImpl(
            "Loading app.",
            ActivationReason.APP_STARTING);

    public enum ActivationReason {
        /**
         * The device has no network connection
         */
        NO_NETWORK,
        /**
         * The server responded, but the response couldn't be parsed.
         */
        BAD_RESPONSE,
        /**
         * The offline mode activation was requested by the server side
         * application or in developer console.
         */
        ACTIVATED_BY_REQUEST,
        /**
         * The reason is unknown.
         */
        UNKNOWN,
        /**
         * The online app is starting
         */
        APP_STARTING
    }

    /**
     * Holds the reason for why the offline mode was activated. Passed to the
     * {@link #activate(ActivationEvent)} method.
     */
    public interface ActivationEvent {

        /**
         * @return A human readable message telling why the offline mode was
         *         activated.
         */
        String getActivationMessage();

        /**
         * @return the ActivationReason code for why the offline mode was
         *         activated.
         */
        ActivationReason getActivationReason();

    }

    /**
     * This method is called when a TouchKit app decides to go offline. This
     * most commonly happens if there is no network connection available.
     * Offline mode can also be activated e.g. due to an inaccessible server,
     * bad responses or as the result of a request by the server side
     * application.
     * 
     * @param event
     *            Details about the activation.
     */
    public abstract void activate(ActivationEvent event);

    /**
     * This method is called when TouchKit detects that it might be possible to
     * go online again (e.g. the network connection has returned). The
     * implementation should e.g. remove or hide offline related elements from
     * the document.
     * 
     * <p>
     * If you have implemented a more advanced offline mode, override this
     * method and gracefully return to normal operation. In that case return
     * false
     * 
     * @return true if offline mode was shut down, false if offline mode was not
     *         shut down
     */
    public abstract boolean deactivate();

    /**
     * @return true if offline mode is currently active
     */
    public abstract boolean isActive();
}
