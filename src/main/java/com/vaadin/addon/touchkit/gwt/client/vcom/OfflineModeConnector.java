package com.vaadin.addon.touchkit.gwt.client.vcom;

import java.util.Date;

import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Timer;
import com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineMode;
import com.vaadin.addon.touchkit.gwt.client.offlinemode.OfflineModeEntrypoint;
import com.vaadin.client.ApplicationConnection.CommunicationHandler;
import com.vaadin.client.ApplicationConnection.RequestStartingEvent;
import com.vaadin.client.ApplicationConnection.ResponseHandlingEndedEvent;
import com.vaadin.client.ApplicationConnection.ResponseHandlingStartedEvent;
import com.vaadin.client.ServerConnector;
import com.vaadin.client.communication.StateChangeEvent;
import com.vaadin.client.extensions.AbstractExtensionConnector;
import com.vaadin.shared.ui.Connect;

@SuppressWarnings("serial")
@Connect(com.vaadin.addon.touchkit.extensions.OfflineMode.class)
public class OfflineModeConnector extends AbstractExtensionConnector implements
        CommunicationHandler {

    private int offlineModeTimeout = 30;

    private static final String SESSION_COOKIE = "JSESSIONID";
    private boolean persistenCookieSet;
    boolean applicationStarted = false;
    private static OfflineModeEntrypoint offlineEntrypoint;

    private Timer requestTimeoutTracker = new Timer() {
        @Override
        public void run() {
            offlineEntrypoint.goOffline(OfflineMode.BAD_RESPONSE);
        }
        public void cancel() {
            super.cancel();
        };
    };

    @Override
    protected void extend(ServerConnector target) {
        offlineEntrypoint = OfflineModeEntrypoint.get();

        // Shoulden't happen unless someone does not inherits TK module
        if (offlineEntrypoint != null) {
            offlineEntrypoint.setOfflineModeConnector(this);

            // Intercept server requests to setup a timeout
            getConnection().addHandler(RequestStartingEvent.TYPE, this);
            getConnection().addHandler(ResponseHandlingStartedEvent.TYPE, this);
            getConnection().addHandler(ResponseHandlingEndedEvent.TYPE, this);

            registerRpc(OfflineModeClientRpc.class, new OfflineModeClientRpc() {
                @Override
                public void goOffline() {
                    offlineEntrypoint.forceOffline(OfflineMode.ACTIVATED_BY_REQUEST);
                }
                @Override
                public void goOnline() {
                    offlineEntrypoint.forceOnline();
                }
            });
        }
    }

    /**
     * @deprecated use OfflineModeEntrypoint.get().isOnline() instead
     */
    @Deprecated
    public static boolean isNetworkOnline() {
        return offlineEntrypoint == null
                || offlineEntrypoint.isOnline();
    }

    @Override
    public OfflineModeState getState() {
        return (OfflineModeState) super.getState();
    }

    @Override
    protected void init() {
        offlineModeTimeout = getState().offlineModeTimeout;
    }

    @Override
    public void onStateChanged(StateChangeEvent stateChangeEvent) {
        super.onStateChanged(stateChangeEvent);
        offlineModeTimeout = getState().offlineModeTimeout;
    }

    @Override
    public void onResponseHandlingEnded(ResponseHandlingEndedEvent e) {
        updateSessionCookieExpiration();
    }

    @Override
    public void onResponseHandlingStarted(ResponseHandlingStartedEvent e) {
        requestTimeoutTracker.cancel();
    }

    @Override
    public void onRequestStarting(RequestStartingEvent e) {
        if (!applicationStarted) {
            applicationStarted = true;
        } else if (persistenCookieSet && getSessionCookie() == null) {
            // Session expired, add fake id -> server side visit will cause
            // normal session expired message instead of disabled cookies
            // warning. See #11420 && VaadinServlet.ensureCookiesEnabled...
            // method
            Cookies.setCookie(SESSION_COOKIE, "invalidateme");
        }

        if (offlineModeTimeout >= 0) {
            requestTimeoutTracker.schedule(offlineModeTimeout * 1000);
        }
    }

    public int getOfflineModeTimeout() {
        return offlineModeTimeout;
    }

    private void updateSessionCookieExpiration() {
        if (getState().persistentSessionTimeout != null) {
            String cookie = getSessionCookie();
            if (cookie != null) {
                Date date = new Date();
                date = new Date(date.getTime()
                        + getState().persistentSessionTimeout * 1000L);
                Cookies.setCookie(SESSION_COOKIE, cookie, date);
                persistenCookieSet = true;
            }
            // else httpOnly, noop
        }
    }

    private String getSessionCookie() {
        return Cookies.getCookie(SESSION_COOKIE);
    }
}
