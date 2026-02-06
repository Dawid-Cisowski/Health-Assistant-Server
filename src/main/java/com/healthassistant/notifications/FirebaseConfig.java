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
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
@Slf4j
class FirebaseConfig {

    @Value("${app.notifications.firebase-credentials}")
    private String firebaseCredentialsJson;

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().stream().anyMatch(app -> FirebaseApp.DEFAULT_APP_NAME.equals(app.getName()))) {
            return FirebaseApp.getInstance();
        }

        byte[] decoded = Base64.getDecoder().decode(firebaseCredentialsJson);
        GoogleCredentials credentials;
        try (InputStream is = new ByteArrayInputStream(decoded)) {
            credentials = GoogleCredentials.fromStream(is);
        }

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
}
