package voldemar.dev.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class AppController {
    private final AtomicBoolean isReady = new AtomicBoolean(true);
    private final AtomicBoolean newGreetingFlag = new AtomicBoolean(false);
    private final String appVersion;
    private final String pod;

    public AppController(@Value("${app.version}") String version) {
        this.appVersion = version;
        String localHost = "unknown-pod";
        try {
            localHost = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        this.pod = localHost;
    }

    @GetMapping("/")
    public Map<String, String> hello() {
        return Map.of("message", "hello", "version", appVersion, "pod", pod);
    }
    
    @GetMapping("/message")
    public Map<String, Object> message() {
        String text = newGreetingFlag.get() ? "Hello from new feature flag!" : "hello";
        return Map.of(
            "message", text, 
            "feature-enabled", newGreetingFlag.get(), 
            "version", appVersion, 
            "pod", pod
        );
    }

    @GetMapping("/admin/feature")
    public Map<String, Object> toggleFeature(@RequestParam boolean enabled) {
        if (enabled != null) {
            if (enabled.equals("true")) {
                newGreeting.set(true);
            } else if (enabled.equals("false")) {
                newGreeting.set(false);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "enabled must be 'true' or 'false'");
            }
        }
        return Map.of("feature", "new-greeting", "enabled", newGreetingFlag.get());
    }

    @GetMapping("/live")
    public String live() {
        return "OK";
    }

    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        if (isReady.get()) {
            return ResponseEntity.ok("READY");
        } else {
            return ResponseEntity.status(503).body("NOT READY");
        }
    }

    @PostMapping("/unready")
    public String unready() {
        isReady.set(false);
        return "Switched to NOT READY";
    }

    @PostMapping("/ready-on")
    public String readyOn() {
        isReady.set(true);
        return "Switched to READY";
    }
}
