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
import com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode.ActivationEvent;
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
    private int hbeatIntervalSecs = 60;
    private int pingTimeoutSecs = 10;
    private ActivationEvent lastOfflineEvent;

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    public static OfflineModeEntrypoint get() {
        return instance;
    }

    private Timer pingToServer = new Timer() {
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
            goOffline(OfflineMode.BAD_RESPONSE);
        }
    }

    @Override
    public void onError(Request request, Throwable exception) {
        logger.severe("Unable to ping server, forcing offline mode.");
        goOffline(OfflineMode.UNKNOWN);
    }

    @Override
    public boolean onError(String details, int statusCode) {
        logger.severe("Going offline due to a communication error.");
        goOffline(OfflineMode.BAD_RESPONSE);
        return true;
    }

    @Override
    public void onModuleLoad() {
        // Don't run twice
        if (instance == null) {
            instance = this;

            // Add Native online/off-line native events
            configureApplicationOfflineEvents();

            // Off-line until online app is started
            goOffline(isForcedOffline() ? OfflineMode.ACTIVATED_BY_REQUEST
                    : OfflineMode.APP_STARTING);
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
            logger.info("Received a server PING response network is all right.");
            resume();
        } else {
            onError(request, null);
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
    }

    /*
     * Export two methods to JS so as we can switch online/offline from
     * developer console
     */
    private native void configureApplicationOfflineEvents() /*-{
        var _this = this;
        var noNetwork =  @com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode::NO_NETWORK;
        var byRequest =  @com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode::ACTIVATED_BY_REQUEST;

        // Export a couple of functions for allowing developer to set offline manually in JS console
        $wnd.tkGoOffline = function() {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forceOffline(*)(byRequest);
        }
        $wnd.tkGoOnline = function() {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forceOnline()();
        }

        // Listen to HTML5 offline-online events
        $wnd.addEventListener("offline", function(e) {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(noNetwork);
        }, false);
        $wnd.addEventListener("online", function(e) {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::resume()();
        }, false);

        // use HTML5 to test whether connection is available when the app starts
        if ($wnd.navigator.onLine != undefined && !$wnd.navigator.onLine) {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(noNetwork);
        }

        // Listen to Cordova specific online/off-line stuff
        if ($wnd.navigator.network && $wnd.navigator.network.connection && $wnd.Connection) {
            $doc.addEventListener("offline", function(e) {
              _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(noNetwork);
            }, false);
            $doc.addEventListener("online", function(e) {
              _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::resume(*)(null);
            }, false);
            // use Cordova to test whether connection is available when the app starts
            if ($wnd.navigator.network.connection.type == $wnd.Connection.NONE) {
              _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(noNetwork);
            }
        }
    }-*/;

    public void goOffline(ActivationEvent event) {
        logger.info("Received Offline Event: " + event.getActivationReason());
        if (isOnline()
                || (lastOfflineEvent != null && lastOfflineEvent
                        .getActivationReason() != event.getActivationReason())) {

            lastOfflineEvent = event;
            offlineApp.activate(event);

            if (!isForcedOffline()) {
                // Configure polling to the server using Vaadin heartbeat if the
                // online application is available.
                if (onlineApp != null) {
                    onlineApp.setApplicationRunning(false);
                    if (offlineConn.getOfflineModeTimeout() > -1) {
                        pingTimeoutSecs = offlineConn.getOfflineModeTimeout();
                    }
                    onlineApp.getHeartbeat().setInterval(pingTimeoutSecs);
                } else {
                    pingToServer.scheduleRepeating(pingTimeoutSecs * 1000);
                }
            }
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
                pingToServer.cancel();
                logger.info("Going back online.");
                if (onlineApp != null) {
                    onlineApp.setApplicationRunning(true);
                    onlineApp.getHeartbeat().setInterval(hbeatIntervalSecs);
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
