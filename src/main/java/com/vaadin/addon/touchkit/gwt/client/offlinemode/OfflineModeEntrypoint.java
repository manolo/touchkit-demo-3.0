package com.vaadin.addon.touchkit.gwt.client.offlinemode;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.user.client.Timer;
import com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode.ActivationEvent;
import com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode.OfflineEvent;
import com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode.OnlineEvent;
import com.vaadin.addon.touchkit.gwt.client.vcom.OfflineModeConnector;
import com.vaadin.client.ApplicationConnection;
import com.vaadin.client.ApplicationConnection.CommunicationErrorHandler;
import com.vaadin.client.ApplicationConnection.CommunicationHandler;
import com.vaadin.client.ApplicationConnection.RequestStartingEvent;
import com.vaadin.client.ApplicationConnection.ResponseHandlingEndedEvent;
import com.vaadin.client.ApplicationConnection.ResponseHandlingStartedEvent;
import com.vaadin.client.VConsole;

/**
 * When this entry point starts an OfflineMode application is started.
 *
 * When the online application goes available, it deactivates the offline
 * app.
 *
 * It listen for HTML5/Cordova online/off-line events activating/deactivating
 * the offline app.
 */
public class OfflineModeEntrypoint implements EntryPoint, CommunicationHandler,
        CommunicationErrorHandler {

    private static final String TK_OFFLINE = "TkOffline";

    private static OfflineMode offlineModeApp = GWT.create(OfflineMode.class);

    private OfflineModeConnector offlineModeConn = null;

    private static boolean online = true;
    private static boolean forcedOffline = false;

    private ActivationEvent lastOfflineEvent = null;
    private static HasHandlers eventBus = null;

    public static boolean isNetworkOnline() {
        return online;
    }

    private static OfflineModeEntrypoint instance;

    public static OfflineModeEntrypoint get() {
        // Shoulden't happen unless someone does not inherits TK module
        if (instance == null) {
            new OfflineModeEntrypoint().onModuleLoad();
        }
        return instance;
    }

    @Override
    public void onModuleLoad() {
        // Do not run twice.
        if (instance != null) {
            return;
        }
        instance = this;

        // Configure HTML5 off-line listeners
        configureApplicationOfflineEvents();

        // restore forcedOffline flag from local-storage
        restoreForcedOffline();

        // We always go off-line at the beginning until we receive
        // a Vaadin online response
        goOffline(OfflineMode.APP_STARTING);
    }

    /**
     * Set the offlineModeConnector when the online vaadin app starts.
     *
     * @param offlineModeConnector
     */
    public void setOfflineModeConnector(OfflineModeConnector oc) {
        offlineModeConn = oc;
        eventBus = getEventBus(offlineModeConn.getConnection());
        ApplicationConnection conn = offlineModeConn.getConnection();
        conn.addHandler(RequestStartingEvent.TYPE, this);
        conn.addHandler(ResponseHandlingStartedEvent.TYPE, this);
        conn.addHandler(ResponseHandlingEndedEvent.TYPE, this);
        conn.setCommunicationErrorDelegate(this);

        // If we get the connection means we are online, so we force
        // online even thought we were online already.
        forceResume();
    }

    // This is a hack, in 7.2 we will have access to the eventBus via ApplicationConnection
    // and Touchkit 4.0 will use that approach.
    // In this version we have to use JSNI to access protected fields.
    private static native EventBus getEventBus(ApplicationConnection conn) /*-{
        return conn.@com.vaadin.client.ApplicationConnection::eventBus;
    }-*/;

    /**
     * @return the OfflineMode application.
     */
    public static OfflineMode getOfflineMode() {
        return offlineModeApp;
    }

    private void forceResume() {
        online = false;
        resume();
    }

    /**
     * Go online if we were not, deactivating off-line UI and
     * reactivating online one.
     */
    public void resume() {
        if (!online) {
            VConsole.log("Network Back ONLINE");
            online = true;
            if (offlineModeConn != null) {
                lastOfflineEvent = null;
                if (offlineModeApp.isActive()) {
                    offlineModeApp.deactivate();
                }
                offlineModeConn.getConnection().setApplicationRunning(true);
                eventBus.fireEvent(new OnlineEvent());
            } else {
                lastOfflineEvent = OfflineMode.ONLINE_APP_NOT_STARTED;
                offlineModeApp.activate(lastOfflineEvent);
            }
        }
    }

    /**
     * Go off-line showing off-line UI, or notify it with the last off-line event.
     */
    public void goOffline(ActivationEvent event) {
        if (lastOfflineEvent == null
                || lastOfflineEvent.getActivationReason() != event
                        .getActivationReason()) {
            VConsole.log("Network OFFLINE (" + event.getActivationReason() + ")");
            online = false;
            lastOfflineEvent = event;

            if (!offlineModeApp.isActive()
                    || lastOfflineEvent != null
                    || lastOfflineEvent.getActivationReason() != event
                            .getActivationReason()) {
                offlineModeApp.activate(event);
            }
            if (offlineModeConn != null) {
                offlineModeConn.getConnection().setApplicationRunning(false);
                eventBus.fireEvent(new OfflineEvent(event));
            }
        }
    }

    /**
     * Synthetic off-line, normally forced from server side or from JS
     * with the window.tkGoOffline() method.
     */
    public void forceOffline(ActivationEvent event) {
        VConsole.error("Going offline due to a force offline call.");
        setForcedOffline(true);
        goOffline(event);
    }

    /**
     * Remove forced offline flag, normally from server side or from JS
     * calling the window.tkGoOnline() function
     */
    public void forceOnline() {
        VConsole.error("Going online due to a force online call.");
        setForcedOffline(false);
        resume();
    }

    @Override
    public boolean onError(String details, int statusCode) {
        VConsole.error("onError " + details + " " + statusCode);
        goOffline(OfflineMode.BAD_RESPONSE);
        return true;
    }

    @Override
    public void onRequestStarting(RequestStartingEvent e) {
    }

    @Override
    public void onResponseHandlingStarted(ResponseHandlingStartedEvent e) {
    }

    @Override
    public void onResponseHandlingEnded(ResponseHandlingEndedEvent e) {
        VConsole.error("onResponseHandlingEnded ");
        resume();
    }

    /*
     * Using this JSNI block in order to listen to certain DOM events not available
     * in GWT: HTML-5 and Cordova online/offline.
     *
     * We also listen to hash fragment changes and window post-messages, so as the app
     * is notified with offline events from the parent when it is embedded in an iframe.
     *
     * This block has a couple of hacks to force the app to go off-line:
     *    $wnd.tkGoOffline() and $wnd.tkGoOnline()
     *
     * Most code here is for fixing android firing messages and setting offline flags
     * in wrong ways.
     */
    private native void configureApplicationOfflineEvents() /*-{
        var _this = this;
        var hasCordovaEvents = false;

        function offline() {
          console.log(">>> going offline.");
          var ev = @com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode::NO_NETWORK;
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::goOffline(*)(ev);
        }
        function online() {
          console.log(">>> going online.");
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::resume()();
        }
        function check() {
          console.log(">>> ckeck online flag " + $wnd.navigator.onLine);
          ($wnd.navigator.onLine ? online : offline)();
        }
        function message(msg) {
          console.log(">>> parent message " + msg);
          if (/^(cordova-.+)$/.test(msg)) {
            hasCordovaEvents = true;
            // Take an action depending on the message
            if (msg == 'cordova-offline') {
              offline();
            } else if (msg == 'cordova-online') {
              online();
            } // TODO: handle #cordova-pause #cordova-resume messages
            return true;
          }
          return false;
        }

        // Export a couple of functions for allowing developer to switch on/offline from JS console
        $wnd.tkGoOffline = function() {
          var ev = @com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode::ACTIVATED_BY_REQUEST;
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forceOffline(*)(ev);
        }
        $wnd.tkGoOnline = function() {
          _this.@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forceOnline()();
        }
        // When offline is forced make any XHR fail
        var realSend = $wnd.XMLHttpRequest.prototype.send;
        $wnd.XMLHttpRequest.prototype.send = function() {
          console.log(">>> send " + @com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forcedOffline);
          if (@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forcedOffline) {
            throw "OFF_LINE_MODE_FORCED";
          } else {
            realSend.apply(this, arguments);
          }
        }

        // Listen to HTML5 offline-online events
        if ($wnd.navigator.onLine != undefined) {
            $wnd.addEventListener("offline", function() {
               console.log(">>> html5 offline event received, ignored=" + hasCordovaEvents);
               if (!hasCordovaEvents) offline();
            }, false);
            $wnd.addEventListener("online", function() {
               console.log(">>> html5 online event received, ignored=" + hasCordovaEvents);
               if (!hasCordovaEvents) online();
            }, false);
            // use HTML5 to test whether connection is available when the app starts
            if (!$wnd.navigator.onLine) {
              console.log(">>> html5 onLine status=" + $wnd.navigator.onLine);
              offline();
            }
        }

        // Redefine HTML-5 onLine indicator.
        // This fixes the issue of android inside phonegap returning erroneus values
        // allowing old vaadin apps based on this approach continue working
        $wnd.navigator.__defineSetter__('onLine', function(b) {
          online = b;
        });
        $wnd.navigator.__defineGetter__('onLine', function() {
          return @com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::online &&
                !@com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint::forcedOffline;
        });
        // Sometimes events are not passed to the app because of paused, we use a timer as well.
        setInterval(check, 30000);

        //////////////////////////////////////////////////////////////////////////////////////////
        //// Everything from here is for phonegap
        if (!$wnd._cordovaNative && !$wnd._nativeReady) {
          return;
        }
        console.log(">>> This app is embedded in a cordova container.");

        // Use hash fragment to go online-offline, useful when the
        // application is embedded in a Cordova iframe, so as it
        // can pass network status messages to the iframe.
        //
        // It's a better approach the postMessage below, so we can
        // remove this when everything is tested with postMessage.
        $wnd.addEventListener("popstate", function(e) {
          var hash = $wnd.location.hash;
          if (hash && message(hash.substring(1))) {
            // We always go a step back to avoid weird behavior when
            // pushing the back button in android, and prevent default
            $wnd.history.back();
            // Prevent default
            e.preventDefault();
            return false;
          }
        }, false);
        // Cordova could have sent offline before the app started.
        if ($wnd.location.hash == "#cordova-offline") {
          $wnd.history.back();
          offline();
        }

        // Use postMessage approach to go online-offline, useful when the
        // application is embedded in a Cordova iframe, so as it
        // can pass network status messages to the iframe.
        if (typeof(window.postMessage) === 'function') {
          $wnd.addEventListener("message", function(ev) {
            message(ev.data);
          }, false);
        }

        // Listen to Cordova specific online/off-line stuff
        // this needs cordova.js to be loaded in the current page.
        // It has to be done overriding ApplicationCacheSettings.
        if ($wnd.navigator.network && $wnd.navigator.network.connection && $wnd.Connection) {
          hasCordovaEvents = true;
          $doc.addEventListener("offline", offline, false);
          $doc.addEventListener("online", online, false);
          // use Cordova to test whether connection is available when the app starts
          if ($wnd.navigator.network.connection.type == $wnd.Connection.NONE) {
            offline();
          }
        }

        // Notify parent cordova container about the app was loaded.
        if ($wnd.parent && $wnd.parent.window && $wnd.parent.window.postMessage) {
          $wnd.parent.window.postMessage("touchkit-ready", "*");
        }
    }-*/;

    /*
     * We save forced off-line in localstorage to continue off-line when we
     * reload the page.
     */
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

    /*
     * Restore off-line flag from localStorage
     */
    private void restoreForcedOffline() {
        if (Storage.isSessionStorageSupported()) {
            forcedOffline = Storage.getSessionStorageIfSupported()
                    .getItem(TK_OFFLINE) != null;

            // Only used when when we reload the app with forcedOffline.
            // We have to delay because the online UI takes a while to
            // be available and it could overlap the off-line screen
            if (forcedOffline) {
                new Timer() {
                    @Override
                    public void run() {
                        goOffline(OfflineMode.ACTIVATED_BY_REQUEST);
                    }
                }.schedule(1000);
            }
        }
    }
}
