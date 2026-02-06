package com.healthassistant.notifications;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
@Slf4j
class FirebaseConfig {

    @Value("${app.notifications.firebase-credentials:}")
    private String firebaseCredentials;

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().stream().anyMatch(app -> FirebaseApp.DEFAULT_APP_NAME.equals(app.getName()))) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = resolveCredentials();
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        log.info("Initializing Firebase application");
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private GoogleCredentials resolveCredentials() throws IOException {
        if (firebaseCredentials == null || firebaseCredentials.isBlank()) {
            log.info("Resolving Firebase credentials via application defaults");
            return GoogleCredentials.getApplicationDefault();
        }

        if (Files.exists(Path.of(firebaseCredentials))) {
            log.info("Resolving Firebase credentials from configured source");
            try (InputStream is = new FileInputStream(firebaseCredentials)) {
                return GoogleCredentials.fromStream(is);
            }
        }

        log.info("Resolving Firebase credentials from configured source");
        byte[] decoded = Base64.getDecoder().decode(firebaseCredentials);
        try (InputStream is = new ByteArrayInputStream(decoded)) {
            return GoogleCredentials.fromStream(is);
        }
    }
}
