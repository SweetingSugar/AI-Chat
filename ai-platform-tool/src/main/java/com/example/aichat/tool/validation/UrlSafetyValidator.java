package com.example.aichat.tool.validation;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.config.ToolProperties;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

public class UrlSafetyValidator {
    private final ToolProperties properties;

    public UrlSafetyValidator(ToolProperties properties) {
        this.properties = properties;
    }

    public URI validate(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("URL must not be blank");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw invalid("URL is invalid");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalid("Only HTTP and HTTPS URLs are supported");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw invalid("URL host is invalid");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isAlwaysBlocked(address)
                        || (!properties.isAllowPrivateNetwork() && isPrivateNetwork(address))) {
                    throw invalid("URL resolves to a restricted network address");
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "URL host cannot be resolved", exception);
        }
        return uri;
    }

    private boolean isAlwaysBlocked(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLinkLocalAddress();
    }

    private boolean isPrivateNetwork(InetAddress address) {
        if (address.isSiteLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0
                    || first == 10
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 198 && (second == 18 || second == 19));
        }
        return bytes.length == 16 && (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, message);
    }
}
