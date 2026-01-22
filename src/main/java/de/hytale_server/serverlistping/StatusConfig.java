package de.hytale_server.serverlistping;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.Codec;

public class StatusConfig {

    public static final BuilderCodec<StatusConfig> CODEC = BuilderCodec.builder(StatusConfig.class, StatusConfig::new)
            .append(
                    new KeyedCodec<>("Port", Codec.INTEGER),
                    (c, v, e) -> c.port = v,
                    (c, e) -> c.port
            )
            .add()
            .append(
                    new KeyedCodec<>("EnableRateLimiting", Codec.BOOLEAN),
                    (c, v, e) -> c.enableRateLimiting = v,
                    (c, e) -> c.enableRateLimiting
            )
            .add()
            .append(
                    new KeyedCodec<>("MaxRequestsPerMinute", Codec.INTEGER),
                    (c, v, e) -> c.maxRequestsPerMinute = v,
                    (c, e) -> c.maxRequestsPerMinute
            )
            .add()
            .build();

    private int port = 8193;
    private boolean enableRateLimiting = true;
    private int maxRequestsPerMinute = 120;

    public int getPort() {
        return port;
    }

    public boolean isEnableRateLimiting() {
        return enableRateLimiting;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }
}