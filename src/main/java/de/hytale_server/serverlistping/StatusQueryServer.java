package de.hytale_server.serverlistping;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class StatusQueryServer {

    private final HytaleLogger logger;
    private final StatusConfig config;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running = false;
    private Thread acceptThread;

    // Rate limiting: IP -> request timestamps
    private final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    private static class RateLimitInfo {
        private final long[] timestamps;
        private int index = 0;

        RateLimitInfo(int size) {
            this.timestamps = new long[size];
        }

        synchronized boolean isAllowed() {
            long now = System.currentTimeMillis();
            long oldestTimestamp = timestamps[index];

            // If oldest timestamp is more than 1 minute old, allow
            if (now - oldestTimestamp > 60_000) {
                timestamps[index] = now;
                index = (index + 1) % timestamps.length;
                return true;
            }

            return false;
        }
    }

    public StatusQueryServer(HytaleLogger logger, StatusConfig config) {
        this.logger = logger;
        this.config = config;
    }

    public void start() throws IOException {
        if (running) {
            logger.at(Level.WARNING).log("Status query server is already running");
            return;
        }

        serverSocket = new ServerSocket(config.getPort(), 50, InetAddress.getByName("0.0.0.0"));
        serverSocket.setSoTimeout(1000);

        executorService = Executors.newFixedThreadPool(10);
        running = true;

        acceptThread = new Thread(this::acceptConnections, "StatusQuery-Accept");
        acceptThread.start();

        logger.at(Level.INFO).log("Status query server started on port " + config.getPort());
    }

    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).log("Error closing server socket", e);
        }

        if (acceptThread != null) {
            try {
                acceptThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        rateLimitMap.clear();
        logger.at(Level.INFO).log("Status query server stopped");
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleConnection(clientSocket));
            } catch (SocketTimeoutException e) {
                // Expected, continue loop
            } catch (IOException e) {
                if (running) {
                    logger.at(Level.WARNING).log("Error accepting connection", e);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        String clientIp = socket.getInetAddress().getHostAddress();

        try {
            socket.setSoTimeout(5000);

            // Rate limiting check
            if (config.isEnableRateLimiting()) {
                RateLimitInfo rateLimitInfo = rateLimitMap.computeIfAbsent(
                        clientIp,
                        k -> new RateLimitInfo(config.getMaxRequestsPerMinute())
                );

                if (!rateLimitInfo.isAllowed()) {
                    sendError(socket, "Rate limit exceeded");
                    logger.at(Level.WARNING).log("Rate limit exceeded for IP: " + clientIp);
                    return;
                }
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String request = in.readLine();

            if (request == null || request.trim().isEmpty()) {
                sendError(socket, "Empty request");
                return;
            }

            // Parse request: just "QUERY"
            if (!"QUERY".equals(request.trim())) {
                sendError(socket, "Invalid command");
                logger.at(Level.WARNING).log("Invalid command from " + clientIp + ": " + request);
                return;
            }

            // Get player counts
            int currentPlayers = Universe.get().getPlayers().size();
            int maxPlayers = HytaleServer.get().getConfig().getMaxPlayers();

            // Send response
            out.println("OK " + currentPlayers + " " + maxPlayers);
            out.flush();

            logger.at(Level.FINE).log("Status query from " + clientIp + ": " + currentPlayers + "/" + maxPlayers);

        } catch (SocketTimeoutException e) {
            logger.at(Level.WARNING).log("Connection timeout from " + clientIp);
        } catch (IOException e) {
            logger.at(Level.WARNING).log("Error handling connection from " + clientIp, e);
        } catch (Exception e) {
            logger.at(Level.SEVERE).log("Unexpected error handling connection from " + clientIp, e);
            try {
                sendError(socket, "Internal error");
            } catch (IOException ignored) {
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.at(Level.FINE).log("Error closing socket", e);
            }
        }
    }

    private void sendError(Socket socket, String message) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println("ERROR " + message);
        out.flush();
    }

    public boolean isRunning() {
        return running;
    }
}