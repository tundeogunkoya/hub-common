package com.blackducksoftware.integration.hub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.restlet.Response;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ClientResource;

import com.blackducksoftware.integration.hub.api.VersionComparison;
import com.blackducksoftware.integration.hub.exception.HubIntegrationException;
import com.blackducksoftware.integration.hub.exception.ProjectDoesNotExistException;
import com.blackducksoftware.integration.hub.policy.api.PolicyStatus;
import com.blackducksoftware.integration.hub.policy.api.PolicyStatusEnum;
import com.blackducksoftware.integration.hub.project.api.AutoCompleteItem;
import com.blackducksoftware.integration.hub.project.api.ProjectItem;
import com.blackducksoftware.integration.hub.report.api.ReportFormatEnum;
import com.blackducksoftware.integration.hub.report.api.ReportMetaInformationItem;
import com.blackducksoftware.integration.hub.report.api.ReportMetaInformationItem.ReportMetaLinkItem;
import com.blackducksoftware.integration.hub.report.api.VersionReport;
import com.blackducksoftware.integration.hub.scan.api.ScanLocationItem;
import com.blackducksoftware.integration.hub.scan.api.ScanLocationResults;
import com.blackducksoftware.integration.hub.util.HubIntTestHelper;
import com.blackducksoftware.integration.hub.util.TestLogger;
import com.blackducksoftware.integration.hub.version.api.DistributionEnum;
import com.blackducksoftware.integration.hub.version.api.PhaseEnum;
import com.blackducksoftware.integration.hub.version.api.ReleaseItem;
import com.google.gson.Gson;

public class HubIntRestServiceTest {

    private static Properties testProperties;

    private static HubIntTestHelper helper;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void testInit() throws Exception {
        testProperties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream is = classLoader.getResourceAsStream("test.properties");
        try {
            testProperties.load(is);
        } catch (IOException e) {
            System.err.println("reading test.properties failed!");
        }
        // p.load(new FileReader(new File("test.properties")));
        System.out.println(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        System.out.println(testProperties.getProperty("TEST_USERNAME"));
        System.out.println(testProperties.getProperty("TEST_PASSWORD"));

        helper = new HubIntTestHelper(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        helper.setLogger(new TestLogger());
        helper.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        try {
            ProjectItem project = helper.getProjectByName(testProperties.getProperty("TEST_PROJECT"));

            List<ReleaseItem> projectVersions = helper.getVersionsForProject(project.getId());
            boolean versionExists = false;
            for (ReleaseItem release : projectVersions) {
                if (testProperties.getProperty("TEST_VERSION").equals(release.getVersion())) {
                    versionExists = true;
                    break;
                }
            }
            if (!versionExists) {
                helper.createHubVersion(testProperties.getProperty("TEST_VERSION"), project.getId(),
                        testProperties.getProperty("TEST_PHASE"),
                        testProperties.getProperty("TEST_DISTRIBUTION"));
            }

        } catch (ProjectDoesNotExistException e) {
            helper.createHubProjectAndVersion(testProperties.getProperty("TEST_PROJECT"), testProperties.getProperty("TEST_VERSION"),
                    testProperties.getProperty("TEST_PHASE"), testProperties.getProperty("TEST_DISTRIBUTION"));
        }
    }

    @AfterClass
    public static void testTeardown() {
        try {
            ProjectItem project = helper.getProjectByName(testProperties.getProperty("TEST_PROJECT"));
            helper.deleteHubProject(project.getId());
        } catch (Exception e) {

        }

    }

    @Test
    public void testSetCookies() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        assertNotNull(restService.getCookies());
        assertTrue(!restService.getCookies().isEmpty());
        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testSetTimeoutZero() throws Exception {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Can not set the timeout to zero.");
        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setTimeout(0);
    }

    @Test
    public void testSetTimeout() throws Exception {
        TestLogger logger = new TestLogger();
        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setTimeout(120);
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        assertNotNull(restService.getCookies());
        assertTrue(!restService.getCookies().isEmpty());
        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testGetProjectMatches() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));
        String testProjectName = "TESTNAME";
        String projectId = null;
        try {

            projectId = restService.createHubProject(testProjectName);

            // Sleep for 3 second, server takes a second before you can start using projects
            Thread.sleep(3000);

            List<AutoCompleteItem> matches = restService.getProjectMatches(testProjectName);

            assertNotNull("matches must be not null", matches);
            assertTrue(!matches.isEmpty());
            assertTrue("error log expected to be empty", logger.getErrorList().isEmpty());
        } finally {
            if (StringUtils.isNotBlank(projectId)) {
                helper.deleteHubProject(projectId);
            }
        }
    }

    @Test
    public void testGetProjectByName() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        ProjectItem project = restService.getProjectByName(testProperties.getProperty("TEST_PROJECT"));

        assertNotNull(project);
        assertEquals(testProperties.getProperty("TEST_PROJECT"), project.getName());
        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testGetProjectByNameSpecialCharacters() throws Exception {
        TestLogger logger = new TestLogger();

        String projectName = "CItest!@#$^&";

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));
        String projectId = null;
        try {

            projectId = restService.createHubProject(projectName);

            assertTrue(StringUtils.isNotBlank(projectId));

            ProjectItem project = restService.getProjectByName(projectName);

            assertNotNull(project);
            assertEquals(projectName, project.getName());
            assertTrue(logger.getErrorList().isEmpty());

        } finally {
            if (StringUtils.isBlank(projectId)) {
                try {
                    ProjectItem project = restService.getProjectByName(projectName);
                    projectId = project.getId();
                } catch (Exception e) {
                    // ignore exception
                }
            }
            if (StringUtils.isNotBlank(projectId)) {
                helper.deleteHubProject(projectId);
            }
        }

        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testGetProjectById() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        ProjectItem project = restService.getProjectByName(testProperties.getProperty("TEST_PROJECT"));

        assertNotNull(project);
        assertEquals(testProperties.getProperty("TEST_PROJECT"), project.getName());
        assertTrue(logger.getErrorList().isEmpty());

        String id = project.getId();
        project = restService.getProjectById(id);

        assertNotNull(project);
        assertEquals(testProperties.getProperty("TEST_PROJECT"), project.getName());
        assertEquals(id, project.getId());
        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testGetVersionsForProject() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        ProjectItem project = restService.getProjectByName(testProperties.getProperty("TEST_PROJECT"));

        assertNotNull(project);
        assertEquals(testProperties.getProperty("TEST_PROJECT"), project.getName());
        assertTrue(logger.getErrorList().isEmpty());

        String id = project.getId();
        List<ReleaseItem> releases = restService.getVersionsForProject(id);

        assertNotNull(releases);
        assertTrue(!releases.isEmpty());

        boolean foundRelease = false;
        for (ReleaseItem release : releases) {
            if (release.getVersion().equals(testProperties.getProperty("TEST_VERSION"))) {
                foundRelease = true;
            }
        }
        assertTrue(foundRelease);

        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testCreateHubProject() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));
        // TEST_CREATE_PROJECT
        String projectId = null;
        try {

            projectId = restService.createHubProject(testProperties.getProperty("TEST_CREATE_PROJECT"));

            assertTrue(StringUtils.isNotBlank(projectId));
        } finally {
            if (StringUtils.isBlank(projectId)) {
                try {
                    ProjectItem project = restService.getProjectByName(testProperties.getProperty("TEST_CREATE_PROJECT"));
                    projectId = project.getId();
                } catch (Exception e) {
                    // ignore exception
                }
            }
            if (StringUtils.isNotBlank(projectId)) {
                helper.deleteHubProject(projectId);
            }
        }

        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testCreateHubProjectSpecialCharacters() throws Exception {
        TestLogger logger = new TestLogger();

        String projectName = "CItest!@#$^&";

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));
        String projectId = null;
        try {

            projectId = restService.createHubProject(projectName);

            assertTrue(StringUtils.isNotBlank(projectId));
        } finally {
            if (StringUtils.isBlank(projectId)) {
                try {
                    ProjectItem project = restService.getProjectByName(projectName);
                    projectId = project.getId();
                } catch (Exception e) {
                    // ignore exception
                }
            }
            if (StringUtils.isNotBlank(projectId)) {
                helper.deleteHubProject(projectId);
            }
        }

        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testCreateHubProjectAndVersion() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));
        // TEST_CREATE_PROJECT
        String projectId = null;
        try {

            projectId = restService.createHubProjectAndVersion(testProperties.getProperty("TEST_CREATE_PROJECT"),
                    testProperties.getProperty("TEST_CREATE_VERSION"), PhaseEnum.DEVELOPMENT.name(),
                    DistributionEnum.INTERNAL.name());

            assertTrue(StringUtils.isNotBlank(projectId));
        } finally {
            if (StringUtils.isBlank(projectId)) {
                try {
                    ProjectItem project = restService.getProjectByName(testProperties.getProperty("TEST_CREATE_PROJECT"));
                    projectId = project.getId();
                } catch (Exception e) {
                    // ignore exception
                }
            }
            if (StringUtils.isNotBlank(projectId)) {
                helper.deleteHubProject(projectId);
            }
        }

        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testCreateHubVersion() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));
        // TEST_CREATE_PROJECT
        String projectId = null;
        try {

            projectId = restService.createHubProject(testProperties.getProperty("TEST_CREATE_PROJECT"));
            assertTrue(StringUtils.isNotBlank(projectId));

            String versionId = restService.createHubVersion(testProperties.getProperty("TEST_CREATE_VERSION"), projectId, PhaseEnum.DEVELOPMENT.name(),
                    DistributionEnum.INTERNAL.name());

            assertTrue(StringUtils.isNotBlank(versionId));
        } finally {
            if (StringUtils.isBlank(projectId)) {
                try {
                    ProjectItem project = restService.getProjectByName(testProperties.getProperty("TEST_CREATE_PROJECT"));
                    projectId = project.getId();
                } catch (Exception e) {
                    // ignore exception
                }
            }
            if (StringUtils.isNotBlank(projectId)) {
                helper.deleteHubProject(projectId);
            }
        }

        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testGetHubVersion() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        String version = restService.getHubVersion();

        assertTrue(StringUtils.isNotBlank(version));
        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testCompareWithHubVersion() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        VersionComparison comparison = restService.compareWithHubVersion("1");

        assertNotNull(comparison);
        assertEquals("1", comparison.getConsumerVersion());
        assertEquals(Integer.valueOf(-1), comparison.getNumericResult());
        assertEquals("<", comparison.getOperatorResult());

        comparison = restService.compareWithHubVersion("9999999");

        assertNotNull(comparison);
        assertEquals("9999999", comparison.getConsumerVersion());
        assertEquals(Integer.valueOf(1), comparison.getNumericResult());
        assertEquals(">", comparison.getOperatorResult());

        assertTrue(logger.getErrorList().isEmpty());
    }

    @Test
    public void testGenerateHubReport() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        ProjectItem project = restService.getProjectByName(testProperties.getProperty("TEST_PROJECT"));
        List<ReleaseItem> releases = restService.getVersionsForProject(project.getId());
        ReleaseItem release = null;
        for (ReleaseItem currentRelease : releases) {
            if (testProperties.getProperty("TEST_VERSION").equals(currentRelease.getVersion())) {
                release = currentRelease;
                break;
            }
        }
        assertNotNull(
                "In project : " + testProperties.getProperty("TEST_PROJECT") + " , could not find the version : " + testProperties.getProperty("TEST_VERSION"),
                release);
        String reportUrl = null;
        reportUrl = restService.generateHubReport(release.getId(), ReportFormatEnum.JSON);

        assertNotNull(reportUrl, reportUrl);
        // The project specified in the test properties file will be deleted at the end of the tests
        // So we dont need to worry about cleaning up the reports
    }

    @Test
    public void testGenerateHubReportFormatUNKNOWN() throws Exception {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Can not generate a report of format : ");
        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));

        restService.generateHubReport(null, ReportFormatEnum.UNKNOWN);
    }

    @Test
    public void testGenerateHubReportAndReadReport() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        restService.setCookies(testProperties.getProperty("TEST_USERNAME"), testProperties.getProperty("TEST_PASSWORD"));

        ProjectItem project = restService.getProjectByName(testProperties.getProperty("TEST_PROJECT"));

        String versionId = restService.createHubVersion("Report Version", project.getId(), PhaseEnum.DEVELOPMENT.name(), DistributionEnum.INTERNAL.name());

        String reportUrl = null;
        System.err.println(versionId);

        // Give the server a second to recognize the new version
        Thread.sleep(1000);

        reportUrl = restService.generateHubReport(versionId, ReportFormatEnum.JSON);

        assertNotNull(reportUrl, reportUrl);

        DateTime timeFinished = null;
        ReportMetaInformationItem reportInfo = null;

        while (timeFinished == null) {
            Thread.sleep(5000);
            reportInfo = restService.getReportLinks(reportUrl);

            timeFinished = reportInfo.getTimeFinishedAt();
        }

        List<ReportMetaLinkItem> links = reportInfo.get_meta().getLinks();

        ReportMetaLinkItem contentLink = null;
        for (ReportMetaLinkItem link : links) {
            if (link.getRel().equalsIgnoreCase("content")) {
                contentLink = link;
                break;
            }
        }
        assertNotNull("Could not find the content link for the report at : " + reportUrl, contentLink);
        // The project specified in the test properties file will be deleted at the end of the tests
        // So we dont need to worry about cleaning up the reports

        VersionReport report = restService.getReportContent(contentLink.getHref());
        assertNotNull(report);
        assertNotNull(report.getDetailedReleaseSummary());
        assertNotNull(report.getDetailedReleaseSummary().getPhase());
        assertNotNull(report.getDetailedReleaseSummary().getDistribution());
        assertNotNull(report.getDetailedReleaseSummary().getProjectId());
        assertNotNull(report.getDetailedReleaseSummary().getProjectName());
        assertNotNull(report.getDetailedReleaseSummary().getVersionId());
        assertNotNull(report.getDetailedReleaseSummary().getVersion());

        String reportId = restService.getReportIdFromReportUrl(reportUrl);
        assertEquals(204, restService.deleteHubReport(versionId, reportId));

    }

    @Test
    public void testGetReportIdFromReportUrl() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        restService.setLogger(logger);
        String expectedId = "IDThatShouldBeFound";

        String reportUrl = "test/test/test/id/id/yoyo.yo/" + expectedId;
        String reportId = restService.getReportIdFromReportUrl(reportUrl);
        assertEquals(expectedId, reportId);

        reportUrl = "test/test/" + expectedId + "/test/id/id/yoyo.yo/";
        reportId = restService.getReportIdFromReportUrl(reportUrl);
        assertEquals("yoyo.yo", reportId);
    }

    @Test
    public void testGetCodeLocations() throws Exception {
        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService("FakeUrl");
        restService.setLogger(logger);

        final String fakeHost = "TestHost";
        final String serverPath1 = "/Test/Fake/Path";
        final String serverPath2 = "/Test/Fake/Path/Child/";
        final String serverPath3 = "/Test/Fake/File";

        HubIntRestService restServiceSpy = Mockito.spy(restService);

        ClientResource clientResource = new ClientResource("");
        final ClientResource resourceSpy = Mockito.spy(clientResource);

        Mockito.when(resourceSpy.handle()).then(new Answer<Representation>() {
            @Override
            public Representation answer(InvocationOnMock invocation) throws Throwable {

                ScanLocationResults scanLocationResults = new ScanLocationResults();
                scanLocationResults.setTotalCount(3);
                ScanLocationItem sl1 = new ScanLocationItem();
                sl1.setHost(fakeHost);
                sl1.setPath(serverPath1);
                ScanLocationItem sl2 = new ScanLocationItem();
                sl2.setHost(fakeHost);
                sl2.setPath(serverPath2);
                ScanLocationItem sl3 = new ScanLocationItem();
                sl3.setHost(fakeHost);
                sl3.setPath(serverPath3);

                List<ScanLocationItem> items = new ArrayList<ScanLocationItem>();
                items.add(sl1);
                items.add(sl2);
                items.add(sl3);

                scanLocationResults.setItems(items);

                String scResults = new Gson().toJson(scanLocationResults);
                StringRepresentation rep = new StringRepresentation(scResults);
                Response response = new Response(null);
                response.setEntity(rep);

                resourceSpy.setResponse(response);
                return null;
            }
        });

        Mockito.when(restServiceSpy.createClientResource()).thenReturn(resourceSpy);

        List<String> scanTargets = new ArrayList<String>();
        scanTargets.add("Test/Fake/Path/Child");
        scanTargets.add("Test\\Fake\\File");

        List<ScanLocationItem> codeLocations = restServiceSpy.getScanLocations(fakeHost, scanTargets);

        assertNotNull(codeLocations);
        assertTrue(codeLocations.size() == 2);
        assertNotNull(codeLocations.get(0));
        assertNotNull(codeLocations.get(1));
    }

    @Test
    public void testGetCodeLocationsUnmatched() throws Exception {
        exception.expect(HubIntegrationException.class);
        exception.expectMessage("Could not determine the code location");

        TestLogger logger = new TestLogger();

        HubIntRestService restService = new HubIntRestService("FakeUrl");
        restService.setLogger(logger);

        final String fakeHost = "TestHost";

        HubIntRestService restServiceSpy = Mockito.spy(restService);

        ClientResource clientResource = new ClientResource("");
        final ClientResource resourceSpy = Mockito.spy(clientResource);

        Mockito.when(resourceSpy.handle()).then(new Answer<Representation>() {
            @Override
            public Representation answer(InvocationOnMock invocation) throws Throwable {

                ScanLocationResults scanLocationResults = new ScanLocationResults();
                scanLocationResults.setTotalCount(0);

                List<ScanLocationItem> items = new ArrayList<ScanLocationItem>();

                scanLocationResults.setItems(items);

                String scResults = new Gson().toJson(scanLocationResults);
                StringRepresentation rep = new StringRepresentation(scResults);
                Response response = new Response(null);
                response.setEntity(rep);

                resourceSpy.setResponse(response);
                return null;
            }
        });

        Mockito.when(restServiceSpy.createClientResource()).thenReturn(resourceSpy);

        List<String> scanTargets = new ArrayList<String>();
        scanTargets.add("Test/Fake/Path/Child");

        restServiceSpy.getScanLocations(fakeHost, scanTargets);
    }

    @Test
    public void testGetPolicyStatus() throws Exception {
        TestLogger logger = new TestLogger();
        HubIntRestService restService = new HubIntRestService("FakeUrl");
        restService.setLogger(logger);

        final String overallStatus = PolicyStatusEnum.IN_VIOLATION.name();
        final String updatedAt = new DateTime().toString();

        final PolicyStatus policyStatus = new PolicyStatus(overallStatus, updatedAt, null, null);

        HubIntRestService restServiceSpy = Mockito.spy(restService);

        ClientResource clientResource = new ClientResource("");
        final ClientResource resourceSpy = Mockito.spy(clientResource);

        Mockito.when(resourceSpy.handle()).then(new Answer<Representation>() {
            @Override
            public Representation answer(InvocationOnMock invocation) throws Throwable {
                String scResults = new Gson().toJson(policyStatus);
                StringRepresentation rep = new StringRepresentation(scResults);
                Response response = new Response(null);
                response.setEntity(rep);

                resourceSpy.setResponse(response);
                return null;
            }
        });

        Mockito.when(restServiceSpy.createClientResource()).thenReturn(resourceSpy);

        assertEquals(policyStatus, restServiceSpy.getPolicyStatus("projectId", "versionId"));

        try {
            assertEquals(policyStatus, restServiceSpy.getPolicyStatus("", ""));
        } catch (IllegalArgumentException e) {
            assertEquals("Missing the project Id to get the policy status of.", e.getMessage());
        }

        try {
            assertEquals(policyStatus, restServiceSpy.getPolicyStatus("projectId", ""));
        } catch (IllegalArgumentException e) {
            assertEquals("Missing the version Id to get the policy status of.", e.getMessage());
        }

        try {
            assertEquals(policyStatus, restServiceSpy.getPolicyStatus(null, null));
        } catch (IllegalArgumentException e) {
            assertEquals("Missing the project Id to get the policy status of.", e.getMessage());
        }

        try {
            assertEquals(policyStatus, restServiceSpy.getPolicyStatus("projectId", null));
        } catch (IllegalArgumentException e) {
            assertEquals("Missing the version Id to get the policy status of.", e.getMessage());
        }
    }

}
