package com.corndwell.lootherald.discord;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validated Discord endpoints. Error messages and toString never contain credentials. */
public final class Destination
{
    private static final Pattern PATH = Pattern.compile("/api/(?:v[0-9]+/)?webhooks/([0-9]{1,25})/([A-Za-z0-9_-]{10,200})/?");
    private final String id, name, url;

    private Destination(String name, String url)
    {
        this.name = name;
        this.url = url;
        this.id = digest(url);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    String queueId() { int query = url.indexOf('?'); return query < 0 ? id : digest(url.substring(0, query)); }
    @Override public String toString() { return name; }

    public static List<Destination> parse(String multiline)
    {
        Map<String, Destination> parsed = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String raw : (multiline == null ? "" : multiline).split("\\R"))
        {
            lineNumber++;
            String line = raw.trim();
            if (line.isEmpty()) { continue; }
            int separator = line.indexOf('|');
            String name = separator < 0 ? "Destination " + (parsed.size() + 1) : line.substring(0, separator).trim();
            String value = separator < 0 ? line : line.substring(separator + 1).trim();
            if (name.isEmpty() || name.length() > 60 || name.contains("://") || name.chars().anyMatch(Character::isISOControl))
            {
                throw invalid(lineNumber);
            }
            String normalized = normalize(value, lineNumber);
            parsed.putIfAbsent(normalized, new Destination(name, normalized));
            if (parsed.size() > 5) { throw new IllegalArgumentException("Use at most five Discord destinations."); }
        }
        return Collections.unmodifiableList(new ArrayList<>(parsed.values()));
    }

    private static String normalize(String value, int line)
    {
        try
        {
            URI uri = new URI(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !(host.equals("discord.com") || host.equals("discordapp.com"))
                || uri.getUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443))
            {
                throw invalid(line);
            }
            Matcher path = PATH.matcher(uri.getRawPath());
            if (!path.matches()) { throw invalid(line); }
            String thread = "";
            if (uri.getRawQuery() != null)
            {
                for (String parameter : uri.getRawQuery().split("&"))
                {
                    if (parameter.matches("thread_id=[0-9]{1,25}") && thread.isEmpty()) { thread = parameter; }
                    else if (!parameter.equals("wait=true") && !parameter.equals("wait=false")) { throw invalid(line); }
                }
            }
            return "https://discord.com/api/webhooks/" + path.group(1) + "/" + path.group(2)
                + (thread.isEmpty() ? "" : "?" + thread);
        }
        catch (URISyntaxException | NullPointerException e) { throw invalid(line); }
    }

    private static IllegalArgumentException invalid(int line)
    {
        return new IllegalArgumentException("Invalid Discord destination on line " + line + ". Use Name | HTTPS Discord webhook URL.");
    }

    private static String digest(String value)
    {
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) { out.append(String.format(Locale.ROOT, "%02x", b & 0xff)); }
            return out.toString();
        }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("Required digest unavailable"); }
    }
}
