package org.jbpm.bootstrap.service.util;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.mail.internet.MimeUtility;

import org.apache.commons.io.IOUtils;
import org.jbpm.bootstrap.model.Project;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.admin.ProcessInstanceAdminService;
import org.kie.api.runtime.query.QueryContext;
import org.kie.internal.runtime.error.ExecutionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

@Component
public class BuildComponent {

    private static final Logger logger = LoggerFactory.getLogger(BuildComponent.class);
    private File parent = new File(System.getProperty("java.io.tmpdir"));

    private static final String CONTAINER_ID = "jbpm-bootstrap-kjar";
    private static final String PROCESS_ID = "GenerateProject";

    private static final String DEFAULT_VERSION = "7.59.0.Final";
    private static final String KIE_VERSION = System.getProperty("org.kie.version",
                                                                 DEFAULT_VERSION);
    private static final String MVN_SETTINGS = System.getProperty("kie.maven.settings.custom");

    private static final String DEFAULT_SPRING_BOOT_VERSION = "2.3.4.RELEASE";
    private static final String OLD_SPRING_BOOT_VERSION = "1.5.12.RELEASE";

    @Autowired
    private ProcessService processService;

    @Autowired
    ProcessInstanceAdminService processInstanceAdminService;

    public ResponseEntity<byte[]> buildApp(Project project, boolean isWeb) throws Exception {
        logger.info("Received request for generating application for project {}",
                    project);

        project = setDefaultsIfNotExist(project);

        String baseFileName = project.getName() + ".zip";

        String fileName = null;
        FileInputStream input;
        File generatedProject = null;
        try {
            File tempFolder = new File(parent,
                                       UUID.randomUUID().toString());
            project.setLocation(tempFolder.getAbsolutePath());
            logger.info("Location for the generated project is {}, final file name of the generated project is {}",
                        project.getLocation(),
                        fileName);
            fileName = MimeUtility.encodeWord(baseFileName,
                                              "utf-8",
                                              "Q");

            long timestamp = System.currentTimeMillis();
            logger.info("About to start new process with container {} and process id {}",
                        CONTAINER_ID,
                        PROCESS_ID);

            String kjarSettings = "";
            if (project.getOptions().contains("kjar")) {
                kjarSettings = "-DkjarGroupId=" + project.getPackageName() + " -DkjarArtifactId=" + project.getName() + "-kjar -DkjarVersion=1.0-SNAPSHOT";
            }
            if (project.getOptions().contains("dkjar")) {
                kjarSettings = "-DkjarGroupId=" + project.getPackageName() + " -DkjarArtifactId=" + project.getName() + "-kjar -DkjarVersion=1.0-SNAPSHOT -DruntimeStrategy=PER_CASE";
            }

            String mavenSettings = "";
            if (MVN_SETTINGS != null) {
                mavenSettings = "-s " + MVN_SETTINGS;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("project",
                       project);
            params.put("projectSetup",
                       resolveApplicationType(project));
            params.put("kjarSettings",
                       kjarSettings);
            params.put("kieVersion",
                       KIE_VERSION);
            params.put("projectVersion",
                       project.getVersion());
            params.put("projectOptions",
                       project.getOptions().stream().collect(Collectors.joining(",")));
            params.put("mavenSettings",
                       mavenSettings);
            params.put("generationType", isWeb ? "web" : "rest");

            String[] versionItems = project.getVersion().split("\\.");
            Integer minorVersion = Integer.parseInt(versionItems[1]);
            // in 7.18 spring boot was upgraded to 2.1.x and thus needs to change version based on selected version
            params.put("springbootVersion", minorVersion >= 18 ? DEFAULT_SPRING_BOOT_VERSION : OLD_SPRING_BOOT_VERSION);

            long processInstanceId = processService.startProcess(CONTAINER_ID,
                                                                 PROCESS_ID,
                                                                 params);

            generatedProject = new File(tempFolder,
                                        fileName);
            waitForGeneratedProject(new File(tempFolder,
                                             project.getName() + ".marker"),
                                    processInstanceId);

            logger.info("Project generation via process with instance id {} done in {} ms",
                        processInstanceId,
                        (System.currentTimeMillis() - timestamp));
            input = new FileInputStream(generatedProject);

            HttpHeaders headers = new HttpHeaders();

            if(isWeb) {
                headers.add("Content-Disposition",
                            "attachment; filename=\"" + fileName + "\"");
            }

            ResponseEntity<byte[]> response =  new ResponseEntity<byte[]>(
                    IOUtils.toByteArray(input),
                    headers,
                    org.springframework.http.HttpStatus.OK);

            return response;

        } catch (Exception e) {
            logger.error("Error when generating project",
                         e);
            throw new Exception(e.getMessage());
        } finally {

            if (generatedProject != null) {
                boolean deleted = FileSystemUtils.deleteRecursively(generatedProject.getParentFile());
                logger.info("Project archive {} and temp files deleted ({})",
                            generatedProject,
                            deleted);
            }
        }
    }

    public String getDefaultVersion() {
        return DEFAULT_VERSION;
    }

    public Project setDefaultsIfNotExist(Project project) {
        if (project.getOptions() == null) {
            project.setOptions(Arrays.asList("kjar",
                                             "model",
                                             "service"));
        }

        if (project.getCapabilities() == null) {
            project.setCapabilities(Arrays.asList("bpm"));
        }

        if (project.getName() == null || project.getName().length() < 1) {
            project.setName("business-application");
        }

        if (project.getPackageName() == null || project.getPackageName().length() < 1) {
            project.setPackageName("com.company");
        }

        if (project.getVersion() == null || project.getVersion().length() < 1) {
            project.setVersion(DEFAULT_VERSION);
        }

        return project;
    }

    protected String resolveApplicationType(Project project) {
        if (project.getCapabilities().contains("brm")) {
            return "brm";
        } else if (project.getCapabilities().contains("planner")) {
            return "planner";
        } else {
            return "bpm";
        }
    }

    protected void waitForGeneratedProject(File generatedProject,
                                           long processInstanceId) throws Exception {
        // check errors before we begin
        checkProcessInstanceErrors(processInstanceId);

        long totalWaitTime = 0;
        int i = 0;
        while (true) {

            if (!generatedProject.exists()) {
                Thread.sleep(200);
                totalWaitTime += 200;

                i++;
                // every 4 seconds check errors again
                if (i > 0 && (i % 20 == 0)) {
                    checkProcessInstanceErrors(processInstanceId);
                }

                if (totalWaitTime > 60000) {
                    throw new RuntimeException("Timeout while waiting for generated project");
                }

                continue;
            }

            return;
        }
    }

    protected void checkProcessInstanceErrors(long processInstanceId) throws Exception {
        List<ExecutionError> executionErrors = processInstanceAdminService.getErrorsByProcessInstanceId(processInstanceId,
                                                                                                        true,
                                                                                                        new QueryContext());
        if (executionErrors != null && executionErrors.size() > 0) {
            String errorString = executionErrors.stream()
                    .map(i -> i.toString())
                    .collect(Collectors.joining("\n "));

            throw new Exception(errorString);
        }
    }
}
