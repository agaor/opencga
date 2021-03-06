/*
 * Copyright 2015 OpenCB
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

package org.opencb.opencga.storage.core.variant;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.StudyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jacobo Coll <jacobo167@gmail.com>
 */
public class FileStudyConfigurationManager extends StudyConfigurationManager {
    public static final String STUDY_CONFIGURATION_PATH = "studyConfigurationPath";
    protected static Logger logger = LoggerFactory.getLogger(FileStudyConfigurationManager.class);

    static final private Map<Integer, Path> filePaths = new HashMap<>();

    public FileStudyConfigurationManager(ObjectMap objectMap) {
        super(objectMap);
    }

    @Override
    protected QueryResult<StudyConfiguration> _getStudyConfiguration(String studyName, Long timeStamp, QueryOptions options) {
        Path path = getPath(studyName, options);
        return readStudyConfiguration(path);    }

    @Override
    public QueryResult<StudyConfiguration> _getStudyConfiguration(int studyId, Long timeStamp, QueryOptions options) {
        Path path = getPath(studyId, options);
        return readStudyConfiguration(path);
    }

    public QueryResult<StudyConfiguration> readStudyConfiguration(Path path) {
        long startTime = System.currentTimeMillis();
        StudyConfiguration studyConfiguration;
        try {
            studyConfiguration = read(path);
        } catch (IOException e) {
            logger.error("Fail at reading StudyConfiguration from " + path, e);
            return new QueryResult<>(path.getFileName().toString(), (int) (System.currentTimeMillis() - startTime), 0, 0, "", e.getMessage(), Collections.<StudyConfiguration>emptyList());
        }

        return new QueryResult<>(path.getFileName().toString(), (int) (System.currentTimeMillis() - startTime), 1, 1, "", "", Collections.singletonList(studyConfiguration));
    }

    @Override
    protected QueryResult _updateStudyConfiguration(StudyConfiguration studyConfiguration, QueryOptions options) {
        long startTime = System.currentTimeMillis();

        Path path = getPath(studyConfiguration.getStudyId(), options);
        try {
            write(studyConfiguration, path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new QueryResult();
    }

    private Path getPath(int studyId, QueryOptions options) {
        Path path;
        if (filePaths.containsKey(studyId)) {
            path = filePaths.get(studyId);
        } else {
            Object o = options.get(STUDY_CONFIGURATION_PATH);
            if (o == null) {
                //TODO: Read path from a default folder?
                return null;
            } else if (o instanceof Path) {
                path = (Path) o;
            } else {
                path = Paths.get(o.toString());
            }
            filePaths.put(studyId, path);
        }
        return path;
    }

    private Path getPath(String studyName, QueryOptions options) {
        Path path;
        Object o = options.get(STUDY_CONFIGURATION_PATH);
        if (o == null) {
            //TODO: Read path from a default folder?
            return null;
        } else if (o instanceof Path) {
            path = (Path) o;
        } else {
            path = Paths.get(o.toString());
        }
        return path;
    }

    static public StudyConfiguration read(Path path) throws IOException {
        return new ObjectMapper(new JsonFactory()).readValue(path.toFile(), StudyConfiguration.class);
    }

    static public void write(StudyConfiguration studyConfiguration, Path path) throws IOException {
        new ObjectMapper(new JsonFactory()).writerWithDefaultPrettyPrinter().withoutAttribute("inverseFileIds").writeValue(path.toFile(), studyConfiguration);
    }


}
