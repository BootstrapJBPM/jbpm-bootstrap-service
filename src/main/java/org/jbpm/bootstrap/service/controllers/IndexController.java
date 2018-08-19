/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.bootstrap.service.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.mail.internet.MimeUtility;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.jbpm.bootstrap.model.Project;
import org.jbpm.services.api.ProcessService;
import org.kie.server.springboot.autoconfiguration.KieServerAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Arrays;

@Controller
public class IndexController {

    private static final Logger logger = LoggerFactory.getLogger(IndexController.class);

    private static final String CONTAINER_ID = "jbpm-bootstrap-kjar";
    private static final String PROCESS_ID = "GenerateProject";

    private static final String KIE_VERSION = "7.10.0-SNAPSHOT";

    private File parent = new File(System.getProperty("java.io.tmpdir"));

    @Autowired
    private KieServerAutoConfiguration serverConfiguration;

    @Autowired
    private ProcessService processService;

    @GetMapping("/")
    public String showIndex(Model model) {
        return "index";
    }

    @ModelAttribute("project")
    public Project getProject() {
        return new Project();
    }

    @PostMapping(value = "/", produces = {"application/octet-stream"})
    public @ResponseBody
    ResponseEntity buildApp(@ModelAttribute Project project) {
        if (project == null) {
            logger.error("Project is missing");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if(project.getOptions() == null) {
            project.setOptions(Arrays.asList("kjar", "model", "service"));
        }

        if(project.getCapabilities() == null) {
            project.setCapabilities(Arrays.asList("bpm"));
        }

        if(project.getName() == null || project.getName().length() < 1) {
            project.setName("business-application");
        }

        if(project.getVersion() == null || project.getVersion().length() < 1) {
            project.setVersion(KIE_VERSION);
        }

        logger.info("Received request for generating application for project {}",
                    project);
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
                kjarSettings = "-DkjarGroupId=" + project.getName() + " -DkjarArtifactId=" + project.getName() + "-kjar -DkjarVersion=1.0-SNAPSHOT";
            }

            Map<String, Object> params = new HashMap<>();
            params.put("project",
                       project);
            params.put("projectSetup",
                       resolveApplicationType(project));
            params.put("kjarSettings",
                       kjarSettings);
            params.put("kieVersion",
                       project.getVersion());
            long processInstanceId = processService.startProcess(CONTAINER_ID,
                                                                 PROCESS_ID,
                                                                 params);

            generatedProject = new File(tempFolder,
                                        fileName);
            waitForGeneratedProject(generatedProject);

            logger.info("Project generation via process with instance id {} done in {} ms",
                        processInstanceId,
                        (System.currentTimeMillis() - timestamp));
            input = new FileInputStream(generatedProject);

            StreamingOutput entity = new StreamingOutput() {

                @Override
                public void write(OutputStream output) throws IOException, WebApplicationException {
                    output.write(IOUtils.toByteArray(input));
                    input.close();
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition",
                        "attachment; filename=\"" + fileName + "\"");

            ResponseEntity<byte[]> response = new ResponseEntity<byte[]>(
                    IOUtils.toByteArray(input),
                    headers,
                    org.springframework.http.HttpStatus.OK);

            return response;
        } catch (Exception e) {
            logger.error("Error when generating project",
                         e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } finally {

            if (generatedProject != null) {
                boolean deleted = FileSystemUtils.deleteRecursively(generatedProject.getParentFile());
                logger.info("Project archive {} and temp files deleted ({})",
                            generatedProject,
                            deleted);
            }
        }
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

    protected void waitForGeneratedProject(File generatedProject) {
        try {
            long totalWaitTime = 0;
            while (true) {

                if (!generatedProject.exists()) {
                    Thread.sleep(200);
                    totalWaitTime += 200;

                    if (totalWaitTime > 20000) {
                        throw new RuntimeException("Timeout while waiting for generated project");
                    }
                    continue;
                }

                return;
            }
        } catch (InterruptedException e) {
        }
    }
}
