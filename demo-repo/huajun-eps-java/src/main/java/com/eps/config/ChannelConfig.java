package com.eps.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for EPS payment channels.
 * <p>
 * Each channel (FAST, MEPS, PayNow, GIRO, SWIFT) can be individually
 * enabled/disabled and configured. Properties are bound from the
 * {@code eps.channels} namespace in application.yml.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Autowired
 * private ChannelConfig channelConfig;
 *
 * boolean fastEnabled = channelConfig.getChannels().get("fast").isEnabled();
 * }</pre>
 *
 * @see ChannelProperties
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "eps.channels")
public class ChannelConfig {

    /**
     * Map of channel name to its individual properties.
     * Keys are channel codes: fast, meps, paynow, giro, swift.
     */
    private Map<String, ChannelProperties> channels = new HashMap<>();

    /**
     * Checks whether a given channel is enabled.
     *
     * @param channelCode the channel code (e.g., "fast", "meps")
     * @return true if the channel exists and is enabled; false otherwise
     */
    public boolean isChannelEnabled(String channelCode) {
        ChannelProperties props = channels.get(channelCode);
        return props != null && props.isEnabled();
    }

    /**
     * Returns all currently enabled channel codes.
     *
     * @return a map of enabled channel code to its description
     */
    public Map<String, String> getEnabledChannels() {
        Map<String, String> result = new HashMap<>();
        channels.forEach((code, props) -> {
            if (props.isEnabled()) {
                result.put(code, props.getDescription());
            }
        });
        return result;
    }

    /**
     * Individual channel properties.
     */
    @Data
    public static class ChannelProperties {

        /** Whether this channel is active. */
        private boolean enabled;

        /** Human-readable description of the channel. */
        @NotBlank
        private String description;
    }
}
