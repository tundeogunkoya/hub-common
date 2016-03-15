package com.blackducksoftware.integration.hub;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import com.blackducksoftware.integration.hub.exception.BDRestException;
import com.blackducksoftware.integration.hub.exception.HubIntegrationException;
import com.blackducksoftware.integration.hub.response.ReportMetaInformationItem;
import com.blackducksoftware.integration.hub.response.mapping.ScanHistoryItem;
import com.blackducksoftware.integration.hub.response.mapping.ScanLocationItem;
import com.blackducksoftware.integration.hub.response.mapping.ScanStatus;
import com.blackducksoftware.integration.hub.scan.status.ScanStatusToPoll;
import com.blackducksoftware.integration.suite.sdk.logging.IntLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class HubEventPolling {

    private final HubIntRestService service;

    public HubEventPolling(HubIntRestService service) {
        this.service = service;
    }

    public HubIntRestService getService() {
        return service;
    }

    /**
     * Check the code locations with the host specified and the paths provided. Check the history for the scan history
     * that falls between the times provided, if the status of that scan history for all code locations is complete then
     * the bom is up to date with these scan results. Otherwise we try again after 10 sec, and we keep trying until it
     * is up to date or until we hit the maximum wait time.
     * If we find a scan history object that has status cancelled or an error type then we throw an exception.
     *
     * @return True if the bom has been updated with the code locations from this scan
     * @throws InterruptedException
     * @throws BDRestException
     * @throws HubIntegrationException
     * @throws URISyntaxException
     * @throws IOException
     */
    public boolean isBomUpToDate(DateTime timeBeforeScan, DateTime timeAfterScan, String hostname, List<String>
            scanTargets, long maximumWait) throws InterruptedException, BDRestException, HubIntegrationException, URISyntaxException, IOException {
        long startTime = System.currentTimeMillis();
        long elapsedTime = 0;
        while (elapsedTime < maximumWait) {
            // logger.trace("CHECKING CODE LOCATIONS");
            List<ScanLocationItem> scanLocationsToCheck = getService().getScanLocations(hostname, scanTargets);
            boolean upToDate = true;
            for (ScanLocationItem currentCodeLocation : scanLocationsToCheck) {
                for (ScanHistoryItem currentScanHistory : currentCodeLocation.getScanList()) {
                    DateTime scanHistoryCreationTime = currentScanHistory.getCreatedOnTime();
                    if (scanHistoryCreationTime != null && scanHistoryCreationTime.isAfter(timeBeforeScan) && scanHistoryCreationTime.isBefore(timeAfterScan)) {
                        // This scan history Item came from the scan we executed
                        if (ScanStatus.isFinishedStatus(currentScanHistory.getStatus())) {
                            if (ScanStatus.isErrorStatus(currentScanHistory.getStatus())) {
                                throw new HubIntegrationException("There was a problem with one of the code locations. Error Status : "
                                        + currentScanHistory.getStatus().name());
                            }
                        } else {
                            // The code location is still updating or matching, etc.
                            upToDate = false;
                        }
                    } else {
                        // This scan history Item did not come from the scan we executed
                        continue;
                    }
                }
            }
            if (upToDate) {
                // The code locations are all finished, so we know the bom has been updated with our scan results
                // So we break out of this loop
                return true;
            }
            // wait 10 seconds before checking the status's again
            Thread.sleep(10000);
            elapsedTime = System.currentTimeMillis() - startTime;
        }
        String formattedTime = String.format("%d minutes", TimeUnit.MILLISECONDS.toMinutes(maximumWait));
        throw new HubIntegrationException("The Bom has not finished updating from the scan within the specified wait time : " + formattedTime);
    }

    /**
     * Checks the status's in the scan files and polls their URL's, every 10 seconds,
     * until they have all have status COMPLETE. We keep trying until we hit the maximum wait time.
     * If we find a scan history object that has status cancelled or an error type then we throw an exception.
     *
     * @return True if all of the status are COMPLETE
     * @throws InterruptedException
     * @throws BDRestException
     * @throws HubIntegrationException
     * @throws URISyntaxException
     * @throws IOException
     */
    public boolean isBomUpToDate(int expectedNumScans, String scanStatusDirectory, long maximumWait, IntLogger logger) throws InterruptedException,
            BDRestException,
            HubIntegrationException,
            URISyntaxException,
            IOException {
        if (StringUtils.isBlank(scanStatusDirectory)) {
            throw new HubIntegrationException("The scan status directory must be a non empty value.");
        }
        File statusDirectory = new File(scanStatusDirectory);
        if (!statusDirectory.exists()) {
            throw new HubIntegrationException("The scan status directory does not exist.");
        }
        if (!statusDirectory.isDirectory()) {
            throw new HubIntegrationException("The scan status directory provided is not a directory.");
        }
        File[] statusFiles = statusDirectory.listFiles();
        if (statusFiles == null || statusFiles.length == 0) {
            throw new HubIntegrationException("Can not find the scan status files in the directory provided.");
        }
        if (statusFiles.length != expectedNumScans) {
            throw new HubIntegrationException("There were " + expectedNumScans + " scans configured and we found " + statusFiles.length + " status files.");
        }
        logger.info("Checking the directory : " + scanStatusDirectory + " for the scan status's.");
        List<ScanStatusToPoll> scanStatusList = new ArrayList<ScanStatusToPoll>();
        for (File currentStatusFile : statusFiles) {
            String fileContent = readFileAsString(currentStatusFile.getCanonicalPath());
            Gson gson = new GsonBuilder().create();
            ScanStatusToPoll status = gson.fromJson(fileContent, ScanStatusToPoll.class);
            if (status.get_meta() == null || status.getStatus() == null) {
                throw new HubIntegrationException("The scan status file : " + currentStatusFile.getCanonicalPath()
                        + " does not contain valid scan status json.");
            }
            scanStatusList.add(status);
        }

        logger.debug("Cleaning up the scan staus files at : " + scanStatusDirectory);
        // We delete the files in a second loop to ensure we have all the scan status's in memory before we start
        // deleting the files. This way, if there is an exception thrown, the User can go look at the files to see what
        // went wrong.
        for (File currentStatusFile : statusFiles) {
            currentStatusFile.delete();
        }
        statusDirectory.delete();
        return pollScanStatusList(scanStatusList, maximumWait);
    }

    private String readFileAsString(String file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader bufReader = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = bufReader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } finally {
            bufReader.close();
        }
        return sb.toString();
    }

    private boolean pollScanStatusList(List<ScanStatusToPoll> scanStatusList, long maximumWait) throws InterruptedException, IOException, BDRestException,
            URISyntaxException, HubIntegrationException {
        long startTime = System.currentTimeMillis();
        long elapsedTime = 0;

        List<ScanStatusToPoll> newStatusList = scanStatusList;
        while (elapsedTime < maximumWait) {
            boolean upToDate = true;
            List<ScanStatusToPoll> tmpStatusList = new ArrayList<ScanStatusToPoll>();

            if (newStatusList != null && !newStatusList.isEmpty()) {
                for (ScanStatusToPoll currentStatus : newStatusList) {
                    if (ScanStatus.isFinishedStatus(currentStatus.getStatusEnum())) {
                        if (ScanStatus.isErrorStatus(currentStatus.getStatusEnum())) {
                            throw new HubIntegrationException("There was a problem with one of the scans. Error Status : "
                                    + currentStatus.getStatusEnum().name());
                        }
                    } else {
                        // The code location is still updating or matching, etc.
                        ScanStatusToPoll newStatus = service.checkScanStatus(currentStatus.get_meta().getHref());
                        upToDate = false;
                        tmpStatusList.add(newStatus);
                    }
                }
                newStatusList = tmpStatusList;
            }
            if (upToDate) {
                // All scans have completed updating the bom
                return true;
            }
            // wait 10 seconds before checking the status's again
            Thread.sleep(10000);
            elapsedTime = System.currentTimeMillis() - startTime;
        }
        String formattedTime = String.format("%d minutes", TimeUnit.MILLISECONDS.toMinutes(maximumWait));
        throw new HubIntegrationException("The Bom has not finished updating from the scan within the specified wait time : " + formattedTime);
    }

    /**
     * Checks the report URL every 5 seconds until the report has a finished time available, then we know it is done
     * being generated. Throws HubIntegrationException after 30 minutes if the report has not been generated yet.
     *
     * @throws IOException
     * @throws BDRestException
     * @throws URISyntaxException
     * @throws InterruptedException
     * @throws HubIntegrationException
     */
    public boolean isReportFinishedGenerating(String reportUrl) throws IOException, BDRestException, URISyntaxException,
            InterruptedException, HubIntegrationException {
        // maximum wait time of 30 minutes
        final long maximumWait = 1000 * 60 * 30;
        return isReportFinishedGenerating(reportUrl, maximumWait);
    }

    /**
     * Checks the report URL every 5 seconds until the report has a finished time available, then we know it is done
     * being generated. Throws HubIntegrationException after the maximum wait if the report has not been generated yet.
     *
     * @throws IOException
     * @throws BDRestException
     * @throws URISyntaxException
     * @throws InterruptedException
     * @throws HubIntegrationException
     */
    public boolean isReportFinishedGenerating(String reportUrl, final long maximumWait) throws IOException, BDRestException,
            URISyntaxException,
            InterruptedException, HubIntegrationException {
        final long startTime = System.currentTimeMillis();
        long elapsedTime = 0;
        String timeFinished = null;
        ReportMetaInformationItem reportInfo = null;

        while (timeFinished == null) {
            reportInfo = getService().getReportLinks(reportUrl);
            timeFinished = reportInfo.getFinishedAt();
            if (timeFinished != null) {
                break;
            }
            if (elapsedTime >= maximumWait) {
                String formattedTime = String.format("%d minutes", TimeUnit.MILLISECONDS.toMinutes(maximumWait));
                throw new HubIntegrationException("The Report has not finished generating in : " + formattedTime);
            }
            // Retry every 5 seconds
            Thread.sleep(5000);
            elapsedTime = System.currentTimeMillis() - startTime;
        }
        return true;
    }

}
