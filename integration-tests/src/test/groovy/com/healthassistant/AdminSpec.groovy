package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Isolated

@Isolated
class AdminSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-admin"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "POST /v1/admin/reproject should return reprojection result"() {
        when: "POST /v1/admin/reproject is called"
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, "/v1/admin/reproject")
                .when()
                .post("/v1/admin/reproject")

        then: "the response should be 200 OK with reprojection result"
        response.statusCode() == 200
        response.body().jsonPath().getInt("totalEvents") >= 0
        response.body().jsonPath().getInt("stepsEvents") >= 0
        response.body().jsonPath().getInt("workoutEvents") >= 0
        response.body().jsonPath().getInt("sleepEvents") >= 0
    }

    def "POST /v1/admin/reproject should require HMAC authentication"() {
        when: "POST /v1/admin/reproject is called without authentication"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .when()
                .post("/v1/admin/reproject")

        then: "should return 401 Unauthorized"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }
}
