/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.workflow_catalog.rest.controller;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ow2.proactive.workflow_catalog.rest.Application;
import org.ow2.proactive.workflow_catalog.rest.dto.WorkflowMetadata;
import org.ow2.proactive.workflow_catalog.rest.entity.Bucket;
import org.ow2.proactive.workflow_catalog.rest.service.WorkflowRevisionService;
import org.ow2.proactive.workflow_catalog.rest.service.WorkflowService;
import org.ow2.proactive.workflow_catalog.rest.service.repository.BucketRepository;
import org.ow2.proactive.workflow_catalog.rest.service.repository.WorkflowRepository;
import org.ow2.proactive.workflow_catalog.rest.util.IntegrationTestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.io.ByteStreams;
import com.jayway.restassured.response.Response;


/**
 * @author ActiveEon Team
 */
@ActiveProfiles("test")
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { Application.class })
@WebIntegrationTest(randomPort = true)
public class WorkflowControllerIntegrationTest extends AbstractRestAssuredTest {

    private static final String WORKFLOWS_RESOURCE = "/buckets/{bucketId}/workflows";

    private static final String WORKFLOW_RESOURCE = "/buckets/{bucketId}/workflows/{workflowId}";

    private static final String layoutMetadata = "{\"offsets\":{\"Linux_Bash_Task\":{\"top\":222" +
                                                 ",\"left\":681.5}},\"project\":\"Deployment\",\"detailedView\":true}";

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowRevisionService workflowRevisionService;

    @Autowired
    private WorkflowService workflowService;

    private Bucket bucket;

    private WorkflowMetadata workflow;

    @Before
    public void setup() throws IOException {
        bucket = bucketRepository.save(new Bucket("myBucket", "BucketControllerIntegrationTestUser"));
        workflow = workflowService.createWorkflow(bucket.getId(),
                                                  Optional.of(layoutMetadata),
                                                  IntegrationTestUtil.getWorkflowAsByteArray("workflow.xml"));
    }

    @Test
    public void testCreateWorkflowShouldReturnSavedWorkflow() {
        given().pathParam("bucketId", bucket.getId())
               .queryParam("layout", layoutMetadata)
               .multiPart(IntegrationTestUtil.getWorkflowFile("workflow.xml"))
               .when()
               .post(WORKFLOWS_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_CREATED)
               .body("workflow[0].bucket_id", is(bucket.getId().intValue()))
               .body("workflow[0].id", is(2))
               .body("workflow[0].name", is("Valid Workflow"))
               .body("workflow[0].project_name", is("Project Name"))
               .body("workflow[0].revision_id", is(1))
               .body("workflow[0].generic_information", hasSize(2))
               .body("workflow[0].generic_information[0].key", is("genericInfo1"))
               .body("workflow[0].generic_information[0].value", is("genericInfo1Value"))
               .body("workflow[0].generic_information[1].key", is("genericInfo2"))
               .body("workflow[0].generic_information[1].value", is("genericInfo2Value"))
               .body("workflow[0].variables", hasSize(2))
               .body("workflow[0].variables[0].key", is("var1"))
               .body("workflow[0].variables[0].value", is("var1Value"))
               .body("workflow[0].variables[1].key", is("var2"))
               .body("workflow[0].variables[1].value", is("var2Value"))
               .body("workflow[0].layout", is(layoutMetadata));
    }

    @Test
    public void testCreateWorkflowShouldReturnUnsupportedMediaTypeWithoutBody() {
        given().pathParam("bucketId", bucket.getId())
               .when()
               .post(WORKFLOWS_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    public void testCreateWorkflowShouldReturnNotFoundIfNonExistingBucketId() {
        given().pathParam("bucketId", 42)
               .multiPart(IntegrationTestUtil.getWorkflowFile("workflow.xml"))
               .when()
               .post(WORKFLOWS_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testGetWorkflowShouldReturnLatestSavedWorkflowRevision() throws IOException {
        WorkflowMetadata secondWorkflowRevision = workflowRevisionService.createWorkflowRevision(workflow.bucketId,
                                                                                                 Optional.of(workflow.id),
                                                                                                 IntegrationTestUtil.getWorkflowAsByteArray("workflow.xml"),
                                                                                                 Optional.empty());

        given().pathParam("bucketId", workflow.bucketId)
               .pathParam("workflowId", workflow.id)
               .when()
               .get(WORKFLOW_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_OK)
               .body("bucket_id", is(secondWorkflowRevision.bucketId.intValue()))
               .body("id", is(secondWorkflowRevision.id.intValue()))
               .body("name", is(secondWorkflowRevision.name))
               .body("project_name", is(secondWorkflowRevision.projectName))
               .body("revision_id", is(secondWorkflowRevision.revisionId.intValue()))
               .body("generic_information", hasSize(secondWorkflowRevision.genericInformation.size()))
               .body("generic_information[0].key", is("genericInfo1"))
               .body("generic_information[0].value", is("genericInfo1Value"))
               .body("generic_information[1].key", is("genericInfo2"))
               .body("generic_information[1].value", is("genericInfo2Value"))
               .body("variables", hasSize(secondWorkflowRevision.variables.size()))
               .body("variables[0].key", is("var1"))
               .body("variables[0].value", is("var1Value"))
               .body("variables[1].key", is("var2"))
               .body("variables[1].value", is("var2Value"));
    }

    @Test
    public void testGetWorkflowPayloadShouldReturnSavedXmlPayload() throws IOException {
        Response response = given().pathParam("bucketId", 1)
                                   .pathParam("workflowId", 1)
                                   .when()
                                   .get(WORKFLOW_RESOURCE + "?alt=xml");

        Arrays.equals(ByteStreams.toByteArray(response.asInputStream()),
                      workflowRepository.getMostRecentWorkflowRevision(1L, 1L).getXmlPayload());

        response.then().assertThat().statusCode(HttpStatus.SC_OK).contentType("application/xml");
    }

    @Test
    public void testGetWorkflowPayloadShouldReturnUnsupportedMediaTypeIfInvalidAltValue() throws IOException {
        given().pathParam("bucketId", 1)
               .pathParam("workflowId", 1)
               .when()
               .get(WORKFLOW_RESOURCE + "?alt=wrong")
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    public void testGetWorkflowShouldReturnNotFoundIfNonExistingBucketId() {
        given().pathParam("bucketId", 42)
               .pathParam("workflowId", 1)
               .when()
               .get(WORKFLOW_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testGetWorkflowPayloadShouldReturnNotFoundIfNonExistingBucketId() {
        given().pathParam("bucketId", 42)
               .pathParam("workflowId", 1)
               .when()
               .get(WORKFLOW_RESOURCE + "?alt=xml")
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testGetWorkflowShouldReturnNotFoundIfNonExistingWorkflowId() {
        given().pathParam("bucketId", 1)
               .pathParam("workflowId", 42)
               .when()
               .get(WORKFLOW_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testGetWorkflowPayloadShouldReturnNotFoundIfNonExistingWorkflowId() {
        given().pathParam("bucketId", 1)
               .pathParam("workflowId", 42)
               .when()
               .get(WORKFLOW_RESOURCE + "?alt=xml")
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testListWorkflowsShouldReturnSavedWorkflows() {
        given().pathParam("bucketId", bucket.getId())
               .when()
               .get(WORKFLOWS_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void testListWorkflowsShouldReturnNotFoundIfNonExistingBucketId() {
        given().pathParam("bucketId", 42)
               .when()
               .get(WORKFLOWS_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testDeleteExistingWorkflow() {
        given().pathParam("bucketId", bucket.getId())
               .pathParam("workflowId", workflow.id)
               .when()
               .delete(WORKFLOW_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_OK)
               .body("name", is(workflow.name));

        // check that the workflow is really gone
        given().pathParam("bucketId", bucket.getId())
               .pathParam("workflowId", workflow.id)
               .when()
               .get(WORKFLOW_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testDeleteNonExistingWorkflow() {
        given().pathParam("bucketId", bucket.getId())
               .pathParam("workflowId", 42)
               .when()
               .delete(WORKFLOW_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testCreateWorkflowsFromArchive() {
        given().pathParam("bucketId", bucket.getId())
               .queryParam("layout", layoutMetadata)
               .queryParam("alt", "zip")
               .multiPart(IntegrationTestUtil.getArchiveFile("archive.zip"))
               .when()
               .post(WORKFLOWS_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_CREATED);
    }

    @Test
    public void testCreateWorkflowsFromArchiveWithBadArchive() {
        given().pathParam("bucketId", bucket.getId())
               .queryParam("layout", layoutMetadata)
               .queryParam("alt", "zip")
               .multiPart(IntegrationTestUtil.getArchiveFile("workflow_0.xml"))
               .when()
               .post(WORKFLOWS_RESOURCE)
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void testGetWorkflowsAsArchive() {
        given().pathParam("bucketId", bucket.getId())
               .pathParam("workflowId", workflow.id)
               .queryParam("layout", layoutMetadata)
               .when()
               .get(WORKFLOW_RESOURCE + "?alt=zip")
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_OK)
               .contentType("application/zip");
    }

    @Test
    public void testGetWorkflowsAsArchiveWithNotExistingWorkflow() {
        given().pathParam("bucketId", bucket.getId())
               .pathParam("workflowId", "1,2")
               .queryParam("layout", layoutMetadata)
               .when()
               .get(WORKFLOW_RESOURCE + "?alt=zip")
               .then()
               .assertThat()
               .statusCode(HttpStatus.SC_NOT_FOUND);
    }
}
