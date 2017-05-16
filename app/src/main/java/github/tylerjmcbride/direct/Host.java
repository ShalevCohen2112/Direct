package github.tylerjmcbride.direct;

import android.app.Application;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.util.Log;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import github.tylerjmcbride.direct.broadcasts.DirectBroadcastReceiver;
import github.tylerjmcbride.direct.callbacks.ClientCallback;
import github.tylerjmcbride.direct.callbacks.ResultCallback;
import github.tylerjmcbride.direct.callbacks.ServiceCallback;
import github.tylerjmcbride.direct.callbacks.SingleResultCallback;
import github.tylerjmcbride.direct.model.WifiP2pDeviceInfo;
import github.tylerjmcbride.direct.registration.HostRegistrar;
import github.tylerjmcbride.direct.registration.listeners.HandshakeListener;
import github.tylerjmcbride.direct.sockets.listeners.ServerSocketInitializationListener;
import github.tylerjmcbride.direct.transceivers.callbacks.ObjectCallback;

public class Host extends Direct {

    private HostRegistrar registrar;
    private WifiP2pDnsSdServiceInfo serviceInfo;
    private Map<String, String> record = new HashMap<>();
    private Thread serviceBroadcastingThread;

    private ClientCallback clientCallback;
    private ServiceCallback serviceCallback;

    private Map<WifiP2pDeviceInfo, WifiP2pDevice> clients = new HashMap<>();

    public Host(Application application, final String service, final String instance) {
        super(application, service);
        record.put(SERVICE_NAME_TAG, service);
        record.put(INSTANCE_NAME_TAG, instance);

        registrar = new HostRegistrar(this, handler, new HandshakeListener() {
            @Override
            public void onHandshake(final WifiP2pDeviceInfo clientInfo) {
                manager.requestPeers(channel, new PeerListListener() {
                    @Override
                    public void onPeersAvailable(WifiP2pDeviceList peers) {
                        WifiP2pDevice clientDevice = peers.get(clientInfo.getMacAddress());

                        if(clientDevice != null) {
                            Log.d(TAG, String.format("Succeeded to register client %s.", clientInfo.getMacAddress()));
                            clients.put(clientInfo, clientDevice);

                            if(clientCallback != null) {
                                clientCallback.onConnected(clientDevice);
                            }
                        } else {
                            Log.d(TAG, String.format("Failed to register client %s.", clientInfo.getMacAddress()));
                        }
                    }
                });
            }

            @Override
            public void onAdieu(WifiP2pDeviceInfo clientInfo) {
                Log.d(TAG, String.format("Succeeded to unregister client %s.", clientInfo.getMacAddress()));
                WifiP2pDevice clientDevice = clients.remove(clientInfo);

                if(clientCallback != null && clientDevice != null) {
                    clientCallback.onDisconnected(clientDevice);
                }
            }
        });

        receiver = new DirectBroadcastReceiver() {
            @Override
            protected void connectionChanged(NetworkInfo networkInfo) {
                if(networkInfo.isAvailable()) {
                    Log.d(TAG, "Succeeded to confirm network connectivity is available.");
                    manager.requestConnectionInfo(channel, new ConnectionInfoListener() {
                        @Override
                        public void onConnectionInfoAvailable(final WifiP2pInfo p2pInfo) {
                            Log.d(TAG, "Succeeded to retrieve connection information.");
                            manager.requestGroupInfo(channel, new GroupInfoListener() {
                                @Override
                                public void onGroupInfoAvailable(WifiP2pGroup p2pGroup) {
                                    Log.d(TAG, "Succeeded to retrieve group information.");
                                    if(p2pGroup != null) {
                                        Collection<WifiP2pDevice> clientList = p2pGroup.getClientList();

                                        // Remove clients whom no longer are connected
                                        for (WifiP2pDeviceInfo clientInfo : clients.keySet()) {
                                            if (!clientList.contains(clients.get(clientInfo))) {
                                                Log.d(TAG, clientInfo.getMacAddress() + " has disconnected.");
                                                WifiP2pDevice clientDevice = clients.remove(clientInfo);

                                                if (clientCallback != null) {
                                                    clientCallback.onDisconnected(clientDevice);
                                                }
                                            }
                                        }
                                    }
                                }
                            });
                        }
                    });
                } else {
                    Log.d(TAG, "Succeeded to confirm network connectivity is available.");
                    clearResources();
                }
            }

            @Override
            protected void stateChanged(boolean wifiEnabled) {
                if(wifiEnabled) {
                    Log.d(TAG, "Succeeded to confirm Wi-Fi P2P availability.");
                } else {
                    Log.d(TAG, "Failed to confirm Wi-Fi P2P availability.");
                    clearResources();
                }
            }

            @Override
            protected void peersChanged() {
                pruneLostClients();
            }

            @Override
            protected void thisDeviceChanged(WifiP2pDevice thisDevice) {
                Host.this.thisDevice = new WifiP2pDevice(thisDevice);
                thisDeviceInfo.setMacAddress(thisDevice.deviceAddress);
            }
        };
        application.getApplicationContext().registerReceiver(receiver, intentFilter);
    }

    /**
     * Sends the respective client the given serializable object.
     *
     * @param clientDevice The client device to receive the given serializable object.
     * @param object The serializable object to send to the respective client.
     * @param callback Invoked upon the success or failure of the request.
     */
    public void send(WifiP2pDevice clientDevice, Serializable object, final ResultCallback callback) {
        for(WifiP2pDeviceInfo clientInfo : clients.keySet()) {
            if(clientDevice != null && clientDevice.deviceAddress.equals(clientInfo.getMacAddress())) {
                objectTransmitter.send(object, new InetSocketAddress(clientInfo.getIpAddress(), clientInfo.getPort()), callback);
                return;
            }
        }

        // We failed to find respective client device
        callback.onFailure();
    }

    /**
     * Registers the local service for service discovery effectively starting the service; however,
     * this is only a request to add said local service, the service will not officially be added
     * until the framework has been notified.
     *
     * @param dataCallback Invoked when receiving data from a client.
     * @param clientCallback Invoked when a client either connects or disconnects.
     * @param serviceCallback Invoked when the service has officially stopped.
     * @param callback Invoked upon the success or failure of the request.
     */
    public void startService(final ObjectCallback dataCallback, final ClientCallback clientCallback, final ServiceCallback serviceCallback, final ResultCallback callback) {
        // Clear any previously existing service
        stopService(new SingleResultCallback() {
            @Override
            public void onSuccessOrFailure() {
                objectReceiver.start(dataCallback, new ServerSocketInitializationListener() {
                    @Override
                    public void onSuccess(ServerSocket serverSocket) {
                        Log.d(TAG, String.format("Succeeded to start object receiver on port %d.", serverSocket.getLocalPort()));
                        thisDeviceInfo.setPort(serverSocket.getLocalPort());

                        registrar.start(new ServerSocketInitializationListener() {
                            @Override
                            public void onSuccess(final ServerSocket serverSocket) {
                                Log.d(TAG, String.format("Succeeded to start registrar on port %d.", serverSocket.getLocalPort()));

                                // Reinitialize the service information to reflect the new registration port
                                record.put(REGISTRAR_PORT_TAG, Integer.toString(serverSocket.getLocalPort()));
                                serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(thisDevice.deviceAddress, SERVICE_TYPE, record);

                                manager.addLocalService(channel, serviceInfo, new ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Succeeded to add local service.");
                                        Host.this.clientCallback = clientCallback;
                                        Host.this.serviceCallback = serviceCallback;
                                        serviceBroadcastingThread = new Thread(new ServiceBroadcastingRunnable());
                                        serviceBroadcastingThread.start();
                                        callback.onSuccess();
                                    }

                                    @Override
                                    public void onFailure(int reason) {
                                        Log.d(TAG, "Failed to add local service.");
                                        registrar.stop();
                                        objectReceiver.stop();
                                        callback.onFailure();
                                    }
                                });
                            }

                            @Override
                            public void onFailure() {
                                Log.d(TAG, "Failed to start registrar.");
                                objectReceiver.stop();
                                callback.onFailure();
                            }
                        });
                    }

                    @Override
                    public void onFailure() {
                        Log.d(TAG, "Failed to start object receiver.");
                        callback.onFailure();
                    }
                });
            }
        });
    }

    /**
     * If a local service exists, this method will remove said service effectively stopping the
     * service; however, this is only a request to remove said local service, the service will not
     * officially be removed until the framework has been notified. The {@link ServiceCallback}
     * will capture this event.
     *
     * @param callback Invoked upon the success or failure of the request.
     */
    public void stopService(final ResultCallback callback) {
        manager.clearLocalServices(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Succeeded to clear local services.");
                if (serviceBroadcastingThread != null) {
                    serviceBroadcastingThread.interrupt();
                }

                clearResources();
                removeGroup(callback);
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to clear local services.");
                callback.onFailure();
            }
        });
    }

    /**
     * Creates and returns a deep copy of the list of client {@link WifiP2pDevice}s.
     * @return A deep copy of the list of client {@link WifiP2pDevice}s.
     */
    public List<WifiP2pDevice> getRegisteredClients() {
        List<WifiP2pDevice> deepClientsClone = new ArrayList<>();
        for(WifiP2pDevice device : clients.values()) {
            deepClientsClone.add(new WifiP2pDevice(device));
        }
        return deepClientsClone;
    }

    /**
     * Will compare {@link Host#clients} to the {@link WifiP2pDeviceList} to unsure that all
     * registered clients are within range. If any of the existing {@link Host#clients} are out of
     * range they will be pruned.
     */
    private void pruneLostClients() {
        manager.requestPeers(channel, new PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                Iterator<WifiP2pDeviceInfo> iterator = clients.keySet().iterator();
                while (iterator.hasNext()) {
                    WifiP2pDeviceInfo clientInfo = iterator.next();
                    boolean containsClient = false;

                    for(WifiP2pDevice peer : peers.getDeviceList()) {
                        if (peer.deviceAddress.equals(clientInfo.getMacAddress())) {
                            containsClient = true;
                        }
                    }

                    // Prune disconnected client
                    if(!containsClient) {
                        Log.d(TAG, clientInfo.getMacAddress() + " has disconnected.");
                        WifiP2pDevice clientDevice = clients.remove(clientInfo);

                        if(clientCallback != null) {
                            clientCallback.onDisconnected(clientDevice);
                        }
                    }
                }
            }
        });
    }

    /**
     * Cleans the resources, this method should be called after the service has been concluded.
     */
    private void clearResources() {
        if(serviceCallback != null) {
            serviceCallback.onServiceStopped();
            serviceCallback = null;
        }

        clientCallback = null;
        registrar.stop();
        objectReceiver.stop();
        clients.clear();
    }

    /**
     * If this instance is garbage collected, attempt to end the service.
     * @throws Throwable Throws any given exception.
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if(manager != null && channel != null) {
            manager.clearLocalServices(channel, null);
        }
    }

    /**
     * Will continually broadcast the service. The {@link ServiceBroadcastingRunnable} will run on
     * its respective {@link Thread} until {@link Thread#interrupt()} is called.
     */
    class ServiceBroadcastingRunnable implements Runnable {

        private static final int SERVICE_BROADCASTING_INTERVAL = 5000;

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(SERVICE_BROADCASTING_INTERVAL);
                    manager.discoverPeers(channel, null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
