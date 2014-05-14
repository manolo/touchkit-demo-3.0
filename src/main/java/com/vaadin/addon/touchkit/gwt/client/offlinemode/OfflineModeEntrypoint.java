package com.vaadin.addon.touchkit.gwt.client.offlinemode;

import java.util.logging.Logger;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode.ActivationEvent;
import com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode.ActivationReason;
import com.vaadin.addon.touchkit.gwt.client.vcom.OfflineModeConnector;
import com.vaadin.client.ApplicationConnection;
import com.vaadin.client.ApplicationConnection.CommunicationErrorHandler;
import com.vaadin.client.ApplicationConnection.CommunicationHandler;
import com.vaadin.client.ApplicationConnection.ConnectionStatusEvent;
import com.vaadin.client.ApplicationConnection.ConnectionStatusEvent.ConnectionStatusHandler;
import com.vaadin.client.ApplicationConnection.RequestStartingEvent;
import com.vaadin.client.ApplicationConnection.ResponseHandlingEndedEvent;
import com.vaadin.client.ApplicationConnection.ResponseHandlingStartedEvent;

/**
 * This configures the browser for detecting when the vaadin server goes
 * online/off-line.
 *
 * When Network connection fails, it displays the GWT OfflineMode application
 * otherwise it commutes to the Vaadin application.
 *
 * It listen for certain events in DOM (HTML5 of Phonegap) and adds handlers
 * to Vaadin requests for detecting when connection fails.
 *
 */
public class OfflineModeEntrypoint implements EntryPoint, CommunicationHandler,
        CommunicationErrorHandler, ConnectionStatusHandler, RequestCallback {

    private static final String TK_OFFLINE = "TkOffline";

    private static OfflineModeEntrypoint instance;
    private OfflineModeConnector offlineConn;
    private OfflineMode offlineApp = GWT.create(OfflineMode.class);
    private ApplicationConnection onlineApp;

    private boolean forcedOffline = false;
    private int hbeatIntervalSecs = 300;
    private int pingTimeoutSecs = 10;

    private final Logger logger = Logger.getLogger(this.getClass().getName());


    private OfflineModeActivationEventImpl offlineActivatedByNetwork = new OfflineModeActivationEventImpl(
            "Offline mode because a network failure.",
            ActivationReason.NO_NETWORK);

    public static OfflineModeEntrypoint get() {
        return instance;
    }

    private Timer pingServer = new Timer() {
        public void run() {
            RequestBuilder rq = new RequestBuilder(RequestBuilder.POST,
                    GWT.getHostPageBaseURL() + "PING");
            rq.setTimeoutMillis(pingTimeoutSecs * 1000);
            rq.setCallback(instance);
            try {
                logger.fine("Sending a ping request to the server.");
                rq.send();
            } catch (Exception e) {
                onError(null, e);
            }
        }
    };

    public void forceOffline(ActivationEvent event) {
        logger.severe("Going offline due to a force offline call.");
        setForcedOffline(true);
        goOffline(event);
    }

    public void forceOnline() {
        setForcedOffline(false);
        resume();
    }

    public boolean isOnline() {
        return !offlineApp.isActive();
    }

    @Override
    public void onConnectionStatusChange(int status) {
        if (status == 200) {
            resume();
        } else {
            logger.severe("Going offline due to a communication error while sending heartbeat.");
            goOffline(new OfflineModeActivationEventImpl(
                    "Offline mode because an incorrect server response",
                    ActivationReason.BAD_RESPONSE));
        }
    }

    @Override
    public void onError(Request request, Throwable exception) {
        logger.severe("Unable to ping server, forcing offline mode.");
        goOffline(null);
    }

    @Override
    public boolean onError(String details, int statusCode) {
        logger.severe("Going offline due to a communication error.");
        goOffline(new OfflineModeActivationEventImpl(
                "Going offline due to a communication error.",
                ActivationReason.BAD_RESPONSE));
        return true;
    }

    @Override
    public void onModuleLoad() {
        // Don't run twice
        if (instance == null) {
            instance = this;

            // Add Native online/off-line native events
            configureApplicationOfflineEvents();

            // Check that server is available, so as we load the appropriate
            // off-line or online screens
            if (isForcedOffline()) {
                goOffline(null);
            } else {
                pingServer.run();
            }
        }
    }

    @Override
    public void onRequestStarting(RequestStartingEvent e) {
    }

    @Override
    public void onResponseHandlingEnded(ResponseHandlingEndedEvent e) {
    }

    @Override
    public void onResponseHandlingStarted(ResponseHandlingStartedEvent e) {
        if (!isOnline()) {
            logger.info("Received a response while offline, going back online");
            resume();
        }
    }

    @Override
    public void onResponseReceived(Request request, Response response) {
        if (response != null && response.getStatusCode() == Response.SC_OK) {
            logger.info("Received a server ping response network is all right.");
            resume();
        }
    }

    /**
     * When Vaadin app has been loaded, we listen to specific communication
     * events
     */
    public void setOfflineModeConnector(
            OfflineModeConnector offlineModeConnector) {
        logger.info("Offline connector has been loaded.");
        offlineConn = offlineModeConnector;

        onlineApp = offlineModeConnector.getConnection();
        onlineApp.addHandler(RequestStartingEvent.TYPE, instance);
        onlineApp.addHandler(ResponseHandlingStartedEvent.TYPE, instance);
        onlineApp.addHandler(ResponseHandlingEndedEvent.TYPE, instance);
        onlineApp.setCommunicationErrorDelegate(instance);
        onlineApp.addHandler(ConnectionStatusEvent.TYPE, instance);

        if (isForcedOffline()) {
            goOffline(null);
        }
    }

    /*
     * Export two methods to JS so as we can switch online/offline from
     * developer console
     */
    private native void configureApplicationOfflineEvents() /*-{
        var _this = this;

        // Export a couple of functions for allowing developer to set offline manually in JS console
        $wnd.tkGoOffline = function() {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forceOffline(*)(null);
        }
        $wnd.tkGoOnline = function() {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forceOnline()();
        }

        // Listen to HTML5 offline-online events
        $wnd.addEventListener("offline", function(e) {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(null);
        }, false);
        $wnd.addEventListener("online", function(e) {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::resume()();
        }, false);

        // use HTML5 to test whether connection is available when the app starts
        if ($wnd.navigator.onLine != undefined && !$wnd.navigator.onLine) {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(null);
        }

        // Listen to Cordova specific online/off-line stuff
        if ($wnd.navigator.network && $wnd.navigator.network.connection && $wnd.Connection) {
            $doc.addEventListener("offline", function(e) {
              _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(null);
            }, false);
            $doc.addEventListener("online", function(e) {
              _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::resume(*)(null);
            }, false);
            // use Cordova to test whether connection is available when the app starts
            if (navigator.network.connection.type == Connection.NONE) {
              _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(null);
            }
        }
    }-*/;

    public void goOffline(ActivationEvent event) {
        try {

        if (isOnline()) {
            offlineApp.activate(
                    event != null ? event : offlineActivatedByNetwork);

            if (!isForcedOffline()) {
                // Configure polling to the server using Vaadin heartbeat if the
                // online application is available.
                if (onlineApp != null) {
                    if (onlineApp.getHeartbeat().getInterval() > -1) {
                        hbeatIntervalSecs = onlineApp.getHeartbeat().getInterval();
                    }

                    // Save interval before setting running to false which sets it to -1
                    onlineApp.setApplicationRunning(false);

                    if (offlineConn.getOfflineModeTimeout() > -1) {
                        pingTimeoutSecs = offlineConn.getOfflineModeTimeout();
                    }
                    onlineApp.getHeartbeat().setInterval(pingTimeoutSecs);
                } else {
                    pingServer.scheduleRepeating(pingTimeoutSecs * 1000);
                }
            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isForcedOffline() {
        if (Storage.isSessionStorageSupported()) {
            forcedOffline = Storage.getSessionStorageIfSupported()
                    .getItem(TK_OFFLINE) != null;
        }
        return forcedOffline;
    }

    private void resume() {
        if (!isForcedOffline() && !isOnline()) {
            if (offlineApp.deactivate()) {
                pingServer.cancel();
                logger.info("Going back online.");
                if (onlineApp != null) {
                    onlineApp.setApplicationRunning(true);
                    onlineApp.getHeartbeat().setInterval(hbeatIntervalSecs);
                } else {
                    Window.Location.reload();
                }
            }
        }
    }

    private void setForcedOffline(boolean forced) {
        forcedOffline = forced;
        if (Storage.isSessionStorageSupported()) {
            if (forced) {
                Storage.getSessionStorageIfSupported().setItem(TK_OFFLINE, "1");
            } else {
                Storage.getSessionStorageIfSupported().removeItem(TK_OFFLINE);
            }
        }
    }
}
