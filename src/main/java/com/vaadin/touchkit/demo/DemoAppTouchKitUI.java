package com.vaadin.touchkit.demo;

import com.vaadin.touchkit.demo.ui.*;
import com.vaadin.touchkit.demo.gwt.client.*;

import com.vaadin.addon.touchkit.extensions.OfflineMode;
//import com.vaadin.addon.touchkit.extensions.TouchKitIcon;
import com.vaadin.addon.touchkit.ui.NavigationManager;
import com.vaadin.addon.touchkit.ui.TabBarView;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet.Tab;
import com.vaadin.ui.UI;

/**
 * The UI's "main" class
 */
@SuppressWarnings("serial")
@Widgetset("com.vaadin.touchkit.demo.gwt.DemoAppWidgetSet")
@Theme("touchkit")
public class DemoAppTouchKitUI extends UI {
    
    private final DemoAppPersistToServerRpc serverRpc = new DemoAppPersistToServerRpc() {
        @Override
        public void persistToServer() {
            // TODO this method is called from client side to store offline data
        }
    };
    
    @Override
    protected void init(VaadinRequest request) {
        final TabBarView tabBarView = new TabBarView();
        final NavigationManager navigationManager = new NavigationManager();
        navigationManager.setCaption("Tab 1");
        navigationManager.setCurrentComponent(new MenuView());
        Tab tab; 
        tab = tabBarView.addTab(navigationManager);
//        TouchKitIcon.book.addTo(tab);
        tab = tabBarView.addTab(new Label("Tab 2"), "Tab 2");
//        TouchKitIcon.ambulance.addTo(tab);
        tab = tabBarView.addTab(new Label("Tab 3"), "Tab 3");
//        TouchKitIcon.download.addTo(tab);
        setContent(tabBarView);

        OfflineMode offlineMode = new OfflineMode();
        offlineMode.extend(this);
        offlineMode.setPersistentSessionCookie(true);
        offlineMode.setOfflineModeEnabled(true);
        offlineMode.setOfflineModeTimeout(15);        
    }
}
