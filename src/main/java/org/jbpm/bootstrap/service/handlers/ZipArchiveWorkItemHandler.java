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
package org.jbpm.bootstrap.service.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ZipArchiveWorkItemHandler implements WorkItemHandler {

    private static final Logger logger = LoggerFactory.getLogger(ZipArchiveWorkItemHandler.class);

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        String sourceDirectory = (String) workItem.getParameter("SourcePath");
        String archiveName = (String) workItem.getParameter("Archive");
        logger.debug("About to create zip archive {} based on content of {}", archiveName, sourceDirectory);
        if (sourceDirectory == null || archiveName == null) {
            throw new RuntimeException("Archive and SourcePath parameters are mandatory");
        }
        
        String archivePath = sourceDirectory + File.separator + archiveName + ".zip";
        
        List<String> fileList = new ArrayList<>();
        generateFileList(sourceDirectory, fileList, new File(sourceDirectory));
        zipIt(sourceDirectory, archivePath, fileList);
        logger.debug("Zip created successfully and stored at {}", archivePath);
        
        // create empty marker file to make sure zip is completely stored 
        // on file system before it can be streamed back to client
        File file = new File(sourceDirectory, archiveName + ".marker");
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        manager.completeWorkItem(workItem.getId(), null);
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        // no-op

    }
    
    public void zipIt(String sourceDirectory, String zipFile, List<String> fileList) {
        byte[] buffer = new byte[1024];
        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        try {
            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(fos);

            logger.debug("Output to zip {}", zipFile);
            FileInputStream in = null;

            for (String file: fileList) {
                logger.debug("File added : " + file);
                ZipEntry ze = new ZipEntry(file);
                zos.putNextEntry(ze);
                try {
                    in = new FileInputStream(sourceDirectory + File.separator + file);
                    int len;
                    while ((len = in .read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                } finally {
                    in.close();
                }
            }

            zos.closeEntry();
            logger.debug("Folder successfully compressed");

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                zos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void generateFileList(String sourceDirectory, List<String> fileList, File node) {
        // add file only
        if (node.isFile()) {
            fileList.add(generateZipEntry(sourceDirectory, node.toString()));
        }

        if (node.isDirectory()) {
            String[] subNode = node.list();
            for (String filename: subNode) {
                generateFileList(sourceDirectory, fileList, new File(node, filename));
            }
        }
    }

    private String generateZipEntry(String sourceDirectory, String file) {
        return file.substring(sourceDirectory.length() + 1, file.length());
    }
}
