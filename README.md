# Direct
Direct is a library that provides a simplified interface to wrap around the Wi-Fi Peer-to-Peer API for building applications with a client-server architecture. In essence, this interface acts as a facade around the Wi-Fi Peer-to-Peer API by hiding its implementation details. This library uses [service discovery](https://developer.android.com/training/connect-devices-wirelessly/nsd-wifi-direct.html) exclusively.
## Table of Contents
- [Initial Setup](#initial-setup)
- [Instantiation](#instantiation)
- [Hosting a Service](#hosting-a-service)
- [Service Discovery](#service-discovery)
- [Connecting to a Service](#connecting-to-a-service)
- [Data Transfer](#data-transfer)
- [Cleaning Up](#cleaning-up)
## Initial Setup
The following must be added to the Android Manifest XML. As this library deals exclusively with service discovery, an API level of 16 is required. 
```xml
<uses-sdk android:minSdkVersion="16" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
```
## Instantiation
Following from a client-server architecture, the library provides functionality for both a client and a server. This library will refer to a server as a host.
### Instantiating a Host
This instance is very important and must persist throughout the lifecycle of your application so long as you are maintaining connections with clients.
```java
WifiDirectHost host = new WifiDirectHost(getApplication(), "UNIQUE_SERVICE_TAG", "UNIQUE_INSTANCE_TAG");
```
The service tag is important is it is used as a filter for those clients who are looking for the specific service hosted by the implementing application. The service tag must be unique in order to no conflict with other applications using the Wi-Fi Peer-to-Peer API. The instance tag is used to indentify the specific device hosting the service rather than the service itself.
### Instantiating a Client
This instance is very important and must persist throughout the lifecycle of your application so long as you are maintaining connection with a host.
```java
WifiDirectHost host = new WifiDirectClient(getApplication(), "UNIQUE_SERVICE_TAG");
```
The service tag must be a direct match of the host device in order for the client to discover the given service.
## Hosting a Service
### Starting a Service
```java
host.startService(new ObjectCallback() {
    @Override
    public void onReceived(Object object) {
        // Invoked when an object has been received from a client
    }
}, new ClientCallback() {
    @Override
    public void onConnected(WifiP2pDevice clientDevice) {
        // Invoked when a Client has connected
    }

    @Override
    public void onDisconnected(WifiP2pDevice clientDevice) {
        // Invoked when a Client has disconnected
    }
}, new ServiceCallback() {
    @Override
    public void onServiceStopped() {
        // Invoked when the service is no longer available
    }
}, new ResultCallback() {
    @Override
    public void onSuccess() {
        // Invoked when the request to the Wi-Fi P2P Framework to start a service was successful
    }

    @Override
    public void onFailure() {
        // Invoked when the request to the Wi-Fi P2P Framework to start a service was unsuccessful
    }
});
```
### Stopping a Service
The result of this function call does not guarantee that the service has stopped, it is simply a request to the Wi-Fi Peer-to-Peer Framework; however, it is possible to capture this event through the ServiceCallback passed as an argument when starting a service.
```java
host.stopService(new ResultCallback() {
    @Override
    public void onSuccess() {
        // Invoked when the request to the Wi-Fi P2P Framework to stop the service was successful
    }

    @Override
    public void onFailure() {
        // Invoked when the request to the Wi-Fi P2P Framework to stop the service was unsuccessful
    }
});
```
## Service Discovery
### Starting Service Discovery
Prior to connecting to a service, the client must discover said service.
```java
client.startDiscovery(new DiscoveryCallback() {
    @Override
    public void onDiscovered(WifiP2pDevice hostDevice) {
        // Invoked when a new service has been discovered
    }

    @Override
    public void onLost(WifiP2pDevice hostDevice) {
        // Invoked when a previously discovered service is no longer available
    }
}, new ResultCallback() {
    @Override
    public void onSuccess() {
        // Invoked when the request to the Wi-Fi P2P Framework to start the service discovery was successful
    }

    @Override
    public void onFailure() {
        // Invoked when the request to the Wi-Fi P2P Framework to start the service discovery was unsuccessful
    }
});
```
### Stopping Service Discovery
If for whatever reason, the client would like to stop discovering services.
```java
client.stopDiscovery(new ResultCallback() {
    @Override
    public void onSuccess() {
        // Invoked when the request to the Wi-Fi P2P Framework to stop service discovery was successful
    }

    @Override
    public void onFailure() {
        // Invoked when the request to the Wi-Fi P2P Framework to stop service discovery was unsuccessful
    }
});
```
## Connecting to a Service
### Connecting to the Host Device
After the client has discovered a service that they wish to make a connection with, that client may now connect to that service with the respective host WifiP2pDevice. The physical connection with the host is captured in the ConnectionCallback, the ResultCallback only captures the success of the request to the Wi-Fi Peer-to-Peer Framework.
```java
client.connect(hostDevice, new ObjectCallback() {
    @Override
    public void onReceived(Object object) {
        // Invoked when an object has been received from the host
    }
}, new ConnectionCallback() {
    @Override
    public void onConnected() {
        // Invoked when a connection with the host has been successfully made
    }

    @Override
    public void onDisconnected() {
        // Invoked when a connection with the host has been lost
    }
}, new ResultCallback() {
    @Override
    public void onSuccess() {
        // Invoked when the request to the Wi-Fi P2P Framework to connect to the host was successful
    }

    @Override
    public void onFailure() {
        // Invoked when the request to the Wi-Fi P2P Framework to connect to the host was successful
    }
});
```
### Disconnecting from the Host Device
The result of this function call does not guarantee that the client has disconnected from the host, it is simply a request to the Wi-Fi Peer-to-Peer Framework; however, it is possible to capture this event through the ConnectionCallback passed as an argument when connecting to the host.
```java
client.disconnect(new ResultCallback() {
    @Override
    public void onSuccess() {
        // Invoked when the request to the Wi-Fi P2P Framework to disconnect from the host was successful
    }

    @Override
    public void onFailure() {
        // Invoked when the request to the Wi-Fi P2P Framework to disconnect from the host was unuccessful
    }
});
```
## Data Transfer
### Sending an Object to a Client
```java
host.send(clientDevice, text, new ResultCallback() {
    @Override
    public void onSuccess() {
        // Invoked when the object was successfully sent to the respective client
    }

    @Override
    public void onFailure() {
        // Invoked when the object was unable to be sent to the respective client
    }
});
```
## Sending an Object to the Host
```java
client.send(text, new ResultCallback() {
    @Override
    public void onSuccess() {
        // Invoked when the object was successfully sent to the host
    }

    @Override
    public void onFailure() {
        // Invoked when the object was unable to be sent to the host
    }
});
```
## Cleaning Up
When the application is finished with the Wi-Fi Peer-to-Peer Framework, it is useful to clean up after ourselves as there are resources that will linger otherwise. This library will register a broadcast receiver which will need to be unregistered.
### Cleaning Up Host Resources
In addition to unregistering the broadcast receiver, the host will attempt to clear all local services.
```java
host.cleanUp();
```
### Cleaning Up Client Resources
In addition to unregistering the broadcast receiver, the client will attempt to disconnect from a potential connection.
```java
client.cleanUp();
```