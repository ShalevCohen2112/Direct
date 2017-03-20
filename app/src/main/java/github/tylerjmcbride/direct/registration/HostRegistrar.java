package github.tylerjmcbride.direct.registration;

import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import github.tylerjmcbride.direct.Direct;
import github.tylerjmcbride.direct.listeners.ServerSocketInitializationCompleteListener;
import github.tylerjmcbride.direct.model.Device;
import github.tylerjmcbride.direct.registration.runnables.HostRegistrarRunnable;

/**
 * A {@link HostRegistrar} is in charge of handling the registration of client {@link WifiP2pDevice}s.
 */
public class HostRegistrar extends Registrar {

    private static final int DEFAULT_REGISTRATION_PORT = 37500;
    private static final int MAX_SERVER_CONNECTIONS = 25;

    private ServerSocket serverSocket;
    private List<Device> registeredClients;

    public HostRegistrar(Direct direct, List<Device> registeredClients, Handler handler) {
        super(direct, handler);
        this.registeredClients = registeredClients;
    }

    /**
     * Starts the registration process.
     * @param listener The {@link ServerSocketInitializationCompleteListener} to capture the result of
     *                 the initialization.
     */
    public void start(final ServerSocketInitializationCompleteListener listener) {
        ServerSockets.initializeServerSocket(DEFAULT_REGISTRATION_PORT, MAX_SERVER_CONNECTIONS, BUFFER_SIZE, new ServerSocketInitializationCompleteListener() {
            @Override
            public void onSuccess(ServerSocket socket) {
                Log.d(Direct.TAG, String.format("Succeeded to initialize registration socket on port %d.", socket.getLocalPort()));
                serverSocket = socket;
                new Thread(new HostRegistrarRunnable(socket, direct, registeredClients)).start();
                listener.onSuccess(socket);
            }

            @Override
            public void onFailure() {
                Log.e(Direct.TAG, "Failed to initialize registration socket.");
                listener.onFailure();
            }
        });
    }

    /**
     * Stops the registration process.
     */
    public void stop() {
        registeredClients.clear();
        if(serverSocket != null) {
            try {
                serverSocket.close();
                serverSocket = null;
                Log.d(Direct.TAG, "Succeeded to stop registrar.");
            } catch (IOException e) {
                Log.e(Direct.TAG, "Failed to stop registrar.");
            }
        }
    }
}
