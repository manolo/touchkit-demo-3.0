package com.vaadin.touchkit.demo.gwt.client;

import com.vaadin.shared.communication.ServerRpc;

public interface DemoAppPersistToServerRpc extends ServerRpc {
    void persistToServer();
}
