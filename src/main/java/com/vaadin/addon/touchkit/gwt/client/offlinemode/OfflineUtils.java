package com.vaadin.addon.touchkit.gwt.client.offlinemode;

import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.shared.EventHandler;

public class OfflineUtils {
    public static class OnlineEvent extends DomEvent<OnlineEvent.OnlineHandler> {
        public interface OnlineHandler extends EventHandler {
            void onOnline();
        }

        private static final Type<OnlineHandler> TYPE = new Type<OnlineHandler>(
                "online", new OnlineEvent());

        public static Type<OnlineHandler> getType() {
            return TYPE;
        }

        public final Type<OnlineHandler> getAssociatedType() {
            return TYPE;
        }

        protected void dispatch(OnlineHandler handler) {
            handler.onOnline();
        }
    }
    
    public static class OfflineEvent extends DomEvent<OfflineEvent.OfflineHandler> {
        public interface OfflineHandler extends EventHandler {
            void onOffline();
        }

        private static final Type<OfflineHandler> TYPE = new Type<OfflineHandler>(
                "online", new OfflineEvent());

        public static Type<OfflineHandler> getType() {
            return TYPE;
        }

        public final Type<OfflineHandler> getAssociatedType() {
            return TYPE;
        }

        protected void dispatch(OfflineHandler handler) {
            handler.onOffline();
        }
    }
}
