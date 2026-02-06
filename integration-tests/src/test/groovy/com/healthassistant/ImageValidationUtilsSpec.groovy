package com.healthassistant

import com.healthassistant.config.ImageValidationUtils
import org.springframework.mock.web.MockMultipartFile
import spock.lang.Specification
import spock.lang.Title

@Title("Unit: ImageValidationUtils - image file validation and type detection")
class ImageValidationUtilsSpec extends Specification {

    static final byte[] JPEG_MAGIC = [(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0] as byte[]
    static final byte[] PNG_MAGIC = [(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A] as byte[]

    // --- validateImage ---

    def "validateImage throws exception for empty file"() {
        given:
        def file = new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[0])

        when:
        ImageValidationUtils.validateImage(file)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("empty")
    }

    def "validateImage throws exception for file exceeding 10MB"() {
        given:
        def largeContent = new byte[10 * 1024 * 1024 + 1]
        def file = new MockMultipartFile("image", "test.jpg", "image/jpeg", largeContent)

        when:
        ImageValidationUtils.validateImage(file)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("10MB")
    }

    def "validateImage accepts valid JPEG with correct content type"() {
        given:
        def file = new MockMultipartFile("image", "test.jpg", "image/jpeg", JPEG_MAGIC)

        when:
        ImageValidationUtils.validateImage(file)

        then:
        noExceptionThrown()
    }

    def "validateImage accepts valid PNG with correct content type"() {
        given:
        def file = new MockMultipartFile("image", "test.png", "image/png", PNG_MAGIC)

        when:
        ImageValidationUtils.validateImage(file)

        then:
        noExceptionThrown()
    }

    // --- detectImageType ---

    def "detectImageType detects JPEG from magic bytes FF D8 FF"() {
        given:
        def file = new MockMultipartFile("image", "unknown", "application/octet-stream", JPEG_MAGIC)

        expect:
        ImageValidationUtils.detectImageType(file) == "image/jpeg"
    }

    def "detectImageType detects PNG from magic bytes 89 50 4E 47"() {
        given:
        def file = new MockMultipartFile("image", "unknown", "application/octet-stream", PNG_MAGIC)

        expect:
        ImageValidationUtils.detectImageType(file) == "image/png"
    }
}
