package com.healthassistant.medicalexams;

import java.util.UUID;

class AttachmentNotFoundException extends RuntimeException {

    AttachmentNotFoundException(UUID attachmentId) {
        super("Attachment not found: " + attachmentId);
    }
}
