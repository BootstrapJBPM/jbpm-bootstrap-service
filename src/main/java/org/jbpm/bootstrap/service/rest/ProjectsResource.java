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
package org.jbpm.bootstrap.service.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.mail.internet.MimeUtility;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.jbpm.bootstrap.model.Project;
import org.jbpm.services.api.ProcessService;
import org.kie.server.springboot.autoconfiguration.KieServerAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

@Component
@Path("/projects")
public class ProjectsResource {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectsResource.class);
    
    private static final String CONTAINER_ID = "jbpm-bootstrap-kjar";
    private static final String PROCESS_ID = "GenerateProject";
    
    private static final String KIE_VERSION = "7.9.0.Final";
    
    private File parent = new File(System.getProperty("java.io.tmpdir"));    
    
    @Autowired
    private KieServerAutoConfiguration serverConfiguration;
    
    @Autowired
    private ProcessService processService;

    @PostConstruct
    public void configure() {
        serverConfiguration.registerInstances(this);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response newProject(Project requestedProject)  {
        
        if (requestedProject == null || requestedProject.getOptions() == null) {
            logger.error("Project is missing or no options selected to generate project");
            return Response.status(Status.BAD_REQUEST).build();
        }
        
        logger.info("Received request for generating application for project {}", requestedProject);
        String baseFileName = requestedProject.getName() + ".zip";
        
        String fileName = null;
        FileInputStream input;
        File generatedProject = null;
        try {
            File tempFolder = new File(parent, UUID.randomUUID().toString());
            requestedProject.setLocation(tempFolder.getAbsolutePath());
            logger.info("Location for the generated project is {}, final file name of the generated project is {}", requestedProject.getLocation(), fileName);
            fileName = MimeUtility.encodeWord(baseFileName, "utf-8", "Q");
            
            long timestamp = System.currentTimeMillis();
            logger.info("About to start new process with container {} and process id {}", CONTAINER_ID, PROCESS_ID);
            
            String kjarSettings = "";
            if (requestedProject.getOptions().contains("kjar")) {
                kjarSettings = "-DkjarGroupId=com.company -DkjarArtifactId=" + requestedProject.getName() + "-kjar -DkjarVersion=1.0-SNAPSHOT";
            }
            
            Map<String, Object> params = new HashMap<>();
            params.put("project", requestedProject);
            params.put("projectSetup", resolveApplicationType(requestedProject));
            params.put("kjarSettings", kjarSettings);
            params.put("kieVersion", KIE_VERSION); // this refers to KIE archetypes version and should be aligned with KIE version used in the app
            long processInstanceId = processService.startProcess(CONTAINER_ID, PROCESS_ID, params);
            
            
            generatedProject = new File(tempFolder, fileName);
            waitForGeneratedProject(generatedProject);
            
            logger.info("Project generation via process with instance id {} done in {} ms", processInstanceId, (System.currentTimeMillis() - timestamp));
            input = new FileInputStream(generatedProject);

            StreamingOutput entity = new StreamingOutput() {
    
                @Override
                public void write(OutputStream output) throws IOException, WebApplicationException {
                    output.write(IOUtils.toByteArray(input));
                    input.close();                    
                }
            };
        
            return Response.ok().entity(entity)                    
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"").build();
        } catch (Exception e) {
            logger.error("Error when generating project", e);
            return Response.serverError().build();
        } finally {
            
            if (generatedProject != null) {
                boolean deleted = FileSystemUtils.deleteRecursively(generatedProject.getParentFile());
                logger.info("Project archive {} and temp files deleted ({})", generatedProject, deleted);
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
    	try{
    		long totalWaitTime = 0;
            while(true) {
                
                if(!generatedProject.exists()) {                       
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
