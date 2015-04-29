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

package org.opencb.opencga.catalog;

import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.datastore.core.config.DataStoreServerAddress;
import org.opencb.datastore.mongodb.MongoDBConfiguration;
import org.opencb.opencga.catalog.api.IFileManager;
import org.opencb.opencga.catalog.api.IUserManager;
import org.opencb.opencga.catalog.authentication.AuthenticationManager;
import org.opencb.opencga.catalog.authentication.CatalogAuthenticationManager;
import org.opencb.opencga.catalog.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.authorization.CatalogAuthorizationManager;
import org.opencb.opencga.catalog.beans.*;
import org.opencb.opencga.catalog.client.CatalogClient;
import org.opencb.opencga.catalog.client.CatalogDBClient;
import org.opencb.opencga.catalog.db.api.CatalogDBAdaptor;
import org.opencb.opencga.catalog.db.CatalogDBException;
import org.opencb.opencga.catalog.db.mongodb.CatalogMongoDBAdaptor;
import org.opencb.opencga.catalog.io.CatalogIOManager;
import org.opencb.opencga.catalog.io.CatalogIOManagerException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;

import org.opencb.opencga.core.common.MailUtils;
import org.opencb.opencga.core.common.StringUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

public class CatalogManager {

    /* DBAdaptor properties */
    public static final String CATALOG_DB_USER = "OPENCGA.CATALOG.DB.USER";
    public static final String CATALOG_DB_DATABASE = "OPENCGA.CATALOG.DB.DATABASE";
    public static final String CATALOG_DB_PASSWORD = "OPENCGA.CATALOG.DB.PASSWORD";
    public static final String CATALOG_DB_HOSTS = "OPENCGA.CATALOG.DB.HOSTS";
    public static final String CATALOG_DB_AUTHENTICATION_DB = "OPENCGA.CATALOG.DB.AUTHENTICATION.DB";
    /* IOManager properties */
    public static final String CATALOG_MAIN_ROOTDIR = "OPENCGA.CATALOG.MAIN.ROOTDIR";
    /* Manager policies properties */
    public static final String CATALOG_MANAGER_POLICY_CREATION_USER = "OPENCGA.CATALOG.MANAGER.POLICY.CREATION_USER";
    /* Other properties */
    public static final String CATALOG_MAIL_USER = "CATALOG.MAIL.USER";
    public static final String CATALOG_MAIL_PASSWORD = "CATALOG.MAIL.PASSWORD";
    public static final String CATALOG_MAIL_HOST = "CATALOG.MAIL.HOST";
    public static final String CATALOG_MAIL_PORT = "CATALOG.MAIL.PORT";


    private CatalogDBAdaptor catalogDBAdaptor;
    private CatalogIOManager ioManager;
    private CatalogIOManagerFactory catalogIOManagerFactory;
    private CatalogClient catalogClient;

    private IUserManager userManager;
    private IFileManager fileManager;

//    private PosixCatalogIOManager ioManager;


    private Properties properties;
    private String creationUserPolicy;

    protected static Logger logger = LoggerFactory.getLogger(CatalogManager.class);
    protected static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
            + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    protected static final Pattern emailPattern = Pattern.compile(EMAIL_PATTERN);
    private AuthenticationManager authenticationManager;
    private AuthorizationManager authorizationManager;


    public CatalogManager(CatalogDBAdaptor catalogDBAdaptor, Properties catalogProperties) throws IOException, CatalogIOManagerException {
        this.catalogDBAdaptor = catalogDBAdaptor;
        this.properties = catalogProperties;

        configureManager(properties);
        configureIOManager(properties);
        configureManagers(properties);
    }

    public CatalogManager(Properties catalogProperties)
            throws CatalogIOManagerException, CatalogDBException {
        this.properties = catalogProperties;
        logger.debug("CatalogManager configureManager");
        configureManager(properties);
        logger.debug("CatalogManager configureDBAdaptor");
        configureDBAdaptor(properties);
        logger.debug("CatalogManager configureIOManager");
        configureIOManager(properties);

        configureManagers(properties);
    }

    private void configureManagers(Properties properties) {
        authenticationManager = new CatalogAuthenticationManager(catalogDBAdaptor);
        authorizationManager = new CatalogAuthorizationManager(catalogDBAdaptor);
        userManager = new UserManager(ioManager, catalogDBAdaptor, authenticationManager, authorizationManager);
        fileManager = new FileManager(authorizationManager, catalogDBAdaptor, catalogIOManagerFactory);
    }

    public CatalogClient client() {
        return client("");
    }

    public CatalogClient client(String sessionId) {
        catalogClient.setSessionId(sessionId);
        return catalogClient;
    }

    public CatalogIOManagerFactory getCatalogIOManagerFactory() {
        return catalogIOManagerFactory;
    }

    private void configureIOManager(Properties properties)
            throws CatalogIOManagerException {
        catalogIOManagerFactory = new CatalogIOManagerFactory(properties);
//        ioManager = this.catalogIOManagerFactory.get(properties.getProperty("CATALOG.MODE", DEFAULT_CATALOG_SCHEME));
        String scheme = URI.create(properties.getProperty(CATALOG_MAIN_ROOTDIR)).getScheme();
        if (scheme == null) {
            scheme = "file";
        }
        ioManager = this.catalogIOManagerFactory.get(scheme);
    }

    private void configureDBAdaptor(Properties properties)
            throws CatalogDBException {

        MongoDBConfiguration mongoDBConfiguration = MongoDBConfiguration.builder()
                .add("username", properties.getProperty(CATALOG_DB_USER, null))
                .add("password", properties.getProperty(CATALOG_DB_PASSWORD, null))
                .add("authenticationDatabase", properties.getProperty(CATALOG_DB_AUTHENTICATION_DB, null))
                .build();

        List<DataStoreServerAddress> dataStoreServerAddresses = new LinkedList<>();
        for (String hostPort : properties.getProperty(CATALOG_DB_HOSTS, "localhost").split(",")) {
            if (hostPort.contains(":")) {
                String[] split = hostPort.split(":");
                Integer port = Integer.valueOf(split[1]);
                dataStoreServerAddresses.add(new DataStoreServerAddress(split[0], port));
            } else {
                dataStoreServerAddresses.add(new DataStoreServerAddress(hostPort, 27017));
            }
        }
        catalogDBAdaptor = new CatalogMongoDBAdaptor(dataStoreServerAddresses, mongoDBConfiguration, properties.getProperty(CATALOG_DB_DATABASE, ""));
    }

    private void configureManager(Properties properties) {
        creationUserPolicy = properties.getProperty(CATALOG_MANAGER_POLICY_CREATION_USER, "always");
        catalogClient = new CatalogDBClient(this);

        //TODO: Check if is empty
        //TODO: Setup catalog if it's empty.
    }

    /**
     * Getter path methods
     * ***************************
     */

    public URI getUserUri(String userId) throws CatalogIOManagerException {
        return ioManager.getUserUri(userId);
    }

    public URI getProjectUri(String userId, String projectId) throws CatalogIOManagerException {
        return ioManager.getProjectUri(userId, projectId);
    }

    public URI getStudyUri(int studyId)
            throws CatalogException {
        return catalogDBAdaptor.getStudy(studyId, new QueryOptions("include", Arrays.asList("projects.studies.uri"))).first().getUri();
    }

    public URI getFileUri(int studyId, String relativeFilePath)
            throws CatalogException {
        return ioManager.getFileUri(getStudyUri(studyId), relativeFilePath);
    }

    public URI getFileUri(URI studyUri, String relativeFilePath)
            throws CatalogIOManagerException, IOException {
        return catalogIOManagerFactory.get(studyUri).getFileUri(studyUri, relativeFilePath);
    }

    public URI getFileUri(File file) throws CatalogException {
        int studyId = catalogDBAdaptor.getStudyIdByFileId(file.getId());
        return getFileUri(studyId, file.getPath());
    }

    public int getProjectIdByStudyId(int studyId) throws CatalogException {
        return catalogDBAdaptor.getProjectIdByStudyId(studyId);
    }

    /**
     * Id methods
     * <user>@project:study:directories:filePath
     * ***************************
     */

    public int getProjectId(String id) throws CatalogDBException {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignore) {
        }

        String[] split = id.split("@");
        if (split.length != 2) {
            return -1;
        }
        return catalogDBAdaptor.getProjectId(split[0], split[1]);
    }

    public int getStudyId(String id) throws CatalogException {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignore) {
        }

        String[] split = id.split("@");
        if (split.length != 2) {
            return -1;
        }
        String[] projectStudy = split[1].replace(':', '/').split("/", 2);
        if (projectStudy.length != 2) {
            return -2;
        }
        int projectId = catalogDBAdaptor.getProjectId(split[0], projectStudy[0]);
        return catalogDBAdaptor.getStudyId(projectId, projectStudy[1]);
    }

    public int getFileId(String id) throws CatalogException {
        return fileManager.getFileId(id);
    }

    public int getToolId(String id) throws CatalogDBException {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignore) {
        }

        String[] split = id.split("@");
        if (split.length != 2) {
            return -1;
        }
        return catalogDBAdaptor.getToolId(split[0], split[1]);
    }

    /**
     * User methods
     * ***************************
     */

    public QueryResult<User> createUser(String id, String name, String email, String password, String organization, QueryOptions options)
            throws CatalogException {
        return createUser(id, name, email, password, organization, options, null);
    }

    public QueryResult<User> createUser(String id, String name, String email, String password, String organization, QueryOptions options, String sessionId)
            throws CatalogException {
        return userManager.create(id, name, email, password, organization, options, sessionId);
    }

    public QueryResult<ObjectMap> loginAsAnonymous(String sessionIp)
            throws CatalogException, IOException {
        return userManager.loginAsAnonymous(sessionIp);
    }

    public QueryResult<ObjectMap> login(String userId, String password, String sessionIp)
            throws CatalogException, IOException {
        return userManager.login(userId, password, sessionIp);
    }

    public QueryResult logout(String userId, String sessionId) throws CatalogException {
        return userManager.logout(userId, sessionId);
    }

    public QueryResult logoutAnonymous(String sessionId) throws CatalogException {
        return userManager.logoutAnonymous(sessionId);
    }

    public QueryResult changePassword(String userId, String oldPassword, String newPassword, String sessionId)
            throws CatalogException {
        userManager.changePassword(userId, oldPassword, newPassword);
        return new QueryResult("changePassword", 0, 0, 0, "", "", Collections.emptyList());
    }

    public QueryResult changeEmail(String userId, String nEmail, String sessionId) throws CatalogException {
        checkParameter(userId, "userId");
        checkParameter(sessionId, "sessionId");
        checkSessionId(userId, sessionId);
        checkEmail(nEmail);
        catalogDBAdaptor.updateUserLastActivity(userId);
        return catalogDBAdaptor.changeEmail(userId, nEmail);
    }

    public QueryResult resetPassword(String userId, String email) throws CatalogException {
        checkParameter(userId, "userId");
        checkEmail(email);
        catalogDBAdaptor.updateUserLastActivity(userId);

        String newPassword = StringUtils.randomString(6);
        String newCryptPass;
        try {
            newCryptPass = StringUtils.sha1(newPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new CatalogDBException("could not encode password");
        }

        QueryResult qr = catalogDBAdaptor.resetPassword(userId, email, newCryptPass);

        String mailUser = properties.getProperty(CATALOG_MAIL_USER);
        String mailPassword = properties.getProperty(CATALOG_MAIL_PASSWORD);
        String mailHost = properties.getProperty(CATALOG_MAIL_HOST);
        String mailPort = properties.getProperty(CATALOG_MAIL_PORT);

        MailUtils.sendResetPasswordMail(email, newPassword, mailUser, mailPassword, mailHost, mailPort);

        return qr;
    }


    public QueryResult<User> getUser(String userId, String lastActivity, String sessionId) throws CatalogException {
        return getUser(userId, lastActivity, new QueryOptions(), sessionId);
    }

    public QueryResult<User> getUser(String userId, String lastActivity, QueryOptions options, String sessionId)
            throws CatalogException {
        return userManager.read(userId, lastActivity, options, sessionId);
    }

    public String getUserIdBySessionId(String sessionId) {
        return userManager.getUserId(sessionId);
    }

    public QueryResult modifyUser(String userId, ObjectMap parameters, String sessionId)
            throws CatalogException {
        return userManager.update(userId, parameters, sessionId);
    }

    public void deleteUser(String userId, String sessionId) throws CatalogException {
        userManager.delete(userId, null, sessionId);
    }

    /**
     * Project methods
     * ***************************
     */

    public QueryResult<Project> createProject(String ownerId, String name, String alias, String description,
                                              String organization, QueryOptions options, String sessionId)
            throws CatalogException {
        checkParameter(ownerId, "ownerId");
        checkParameter(name, "name");
        checkAlias(alias, "alias");
        checkParameter(sessionId, "sessionId");

        checkSessionId(ownerId, sessionId);    //Only the user can create a project

        description = description != null ? description : "";
        organization = organization != null ? organization : "";

        Project project = new Project(name, alias, description, "", organization);

        /* Add default ACL */
        //Add generic permissions to the project.
        project.getAcl().add(new Acl(Acl.USER_OTHERS_ID, false, false, false, false));

        QueryResult<Project> result = catalogDBAdaptor.createProject(ownerId, project, options);
        project = result.getResult().get(0);

        try {
            ioManager.createProject(ownerId, Integer.toString(project.getId()));
        } catch (CatalogIOManagerException e) {
            e.printStackTrace();
            catalogDBAdaptor.deleteProject(project.getId());
        }
        catalogDBAdaptor.updateUserLastActivity(ownerId);
        return result;
    }

    public QueryResult<Project> getProject(int projectId, QueryOptions options, String sessionId)
            throws CatalogException {
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);

        Acl projectAcl = authorizationManager.getProjectACL(userId, projectId);
        if (projectAcl.isRead()) {
            QueryResult<Project> projectResult = catalogDBAdaptor.getProject(projectId, options);
            if (!projectResult.getResult().isEmpty()) {
                authorizationManager.filterStudies(userId, projectAcl, projectResult.getResult().get(0).getStudies());
            }
            return projectResult;
        } else {
            throw new CatalogDBException("Permission denied. Can't read project.");
        }
    }

    public QueryResult<Project> getAllProjects(String ownerId, QueryOptions options, String sessionId)
            throws CatalogException {
        checkParameter(ownerId, "ownerId");
        checkParameter(sessionId, "sessionId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);

        QueryResult<Project> allProjects = catalogDBAdaptor.getAllProjects(ownerId, options);

        List<Project> projects = allProjects.getResult();
        authorizationManager.filterProjects(userId, projects);
        allProjects.setResult(projects);

        return allProjects;
    }

    public QueryResult renameProject(int projectId, String newProjectAlias, String sessionId)
            throws CatalogException {
        checkAlias(newProjectAlias, "newProjectAlias");
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        String ownerId = catalogDBAdaptor.getProjectOwnerId(projectId);

        Acl projectAcl = authorizationManager.getProjectACL(userId, projectId);
        if (projectAcl.isWrite()) {
            catalogDBAdaptor.updateUserLastActivity(ownerId);
            return catalogDBAdaptor.renameProjectAlias(projectId, newProjectAlias);
        } else {
            throw new CatalogDBException("Permission denied. Can't rename project");
        }
    }

    /**
     * Modify some params from the specified project:
     * <p/>
     * name
     * description
     * organization
     * status
     * attributes
     *
     * @param projectId  Project identifier
     * @param parameters Parameters to change.
     * @param sessionId  sessionId to check permissions
     * @return
     * @throws org.opencb.opencga.catalog.db.CatalogDBException
     */
    public QueryResult modifyProject(int projectId, ObjectMap parameters, String sessionId)
            throws CatalogException {
        checkObj(parameters, "Parameters");
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        String ownerId = catalogDBAdaptor.getProjectOwnerId(projectId);
        if (!authorizationManager.getProjectACL(userId, projectId).isWrite()) {
            throw new CatalogDBException("User '" + userId + "' can't modify the project " + projectId);
        }
        for (String s : parameters.keySet()) {
            if (!s.matches("name|description|organization|status|attributes")) {
                throw new CatalogDBException("Parameter '" + s + "' can't be changed");
            }
        }
        catalogDBAdaptor.updateUserLastActivity(ownerId);
        return catalogDBAdaptor.modifyProject(projectId, parameters);
    }

    public QueryResult shareProject(int projectId, Acl acl, String sessionId) throws CatalogException {
        checkObj(acl, "acl");
        checkParameter(sessionId, "sessionId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        Acl projectAcl = authorizationManager.getProjectACL(userId, projectId);
        if (!projectAcl.isWrite()) {
            throw new CatalogDBException("Permission denied. Can't modify project");
        }

        return catalogDBAdaptor.setProjectAcl(projectId, acl);
    }

    /**
     * Study methods
     * ***************************
     */
    public QueryResult<Study> createStudy(int projectId, String name, String alias, Study.Type type, String description,
                                          String sessionId)
            throws CatalogException, IOException {
        return createStudy(projectId, name, alias, type, null, null, description, null, null, null, null, null, null, null, null, sessionId);
    }

    /**
     * Creates a new Study in catalog
     * @param projectId     Parent project id
     * @param name          Study Name
     * @param alias         Study Alias. Must be unique in the project's studies
     * @param type          Study type: CONTROL_CASE, CONTROL_SET, ... (see org.opencb.opencga.catalog.beans.Study.Type)
     * @param creatorId     Creator user id. If null, user by sessionId
     * @param creationDate  Creation date. If null, now
     * @param description   Study description. If null, empty string
     * @param status        Unused
     * @param cipher        Unused
     * @param uriScheme     UriScheme to select the CatalogIOManager. Default: CatalogIOManagerFactory.DEFAULT_CATALOG_SCHEME
     * @param uri           URI for the folder where to place the study. Scheme must match with the uriScheme. Folder must exist.
     * @param datastores    DataStores information
     * @param stats         Optional stats
     * @param attributes    Optional attributes
     * @param options       QueryOptions
     * @param sessionId     User's sessionId
     * @return              Generated study
     * @throws CatalogException
     * @throws IOException
     */
    public QueryResult<Study> createStudy(int projectId, String name, String alias, Study.Type type,
                                          String creatorId, String creationDate, String description, String status,
                                          String cipher, String uriScheme, URI uri,
                                          Map<File.Bioformat, DataStore> datastores, Map<String, Object> stats,
                                          Map<String, Object> attributes, QueryOptions options, String sessionId)
            throws CatalogException, IOException {
        checkParameter(name, "name");
        checkParameter(alias, "alias");
        checkObj(type, "type");
        checkAlias(alias, "alias");
        checkParameter(sessionId, "sessionId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        description = defaultString(description, "");
        creatorId = defaultString(creatorId, userId);
        creationDate = defaultString(creationDate, TimeUtils.getTime());
        status = defaultString(status, "active");
        cipher = defaultString(cipher, "none");
        if (uri != null) {
            if (uri.getScheme() == null) {
                throw new CatalogException("StudyUri must specify the scheme");
            } else {
                if (uriScheme != null && !uriScheme.isEmpty()) {
                    if (!uriScheme.equals(uri.getScheme())) {
                        throw new CatalogException("StudyUri must specify the scheme");
                    }
                } else {
                    uriScheme = uri.getScheme();
                }
            }
        } else {
            uriScheme = defaultString(uriScheme, CatalogIOManagerFactory.DEFAULT_CATALOG_SCHEME);
        }
        datastores = defaultObject(datastores, new HashMap<File.Bioformat, DataStore>());
        stats = defaultObject(stats, new HashMap<String, Object>());
        attributes = defaultObject(attributes, new HashMap<String, Object>());

        CatalogIOManager catalogIOManager = catalogIOManagerFactory.get(uriScheme);

        String projectOwnerId = catalogDBAdaptor.getProjectOwnerId(projectId);


        /* Check project permissions */
        if (!authorizationManager.getProjectACL(userId, projectId).isWrite()) { //User can't write/modify the project
            throw new CatalogDBException("Permission denied. Can't write in project");
        }
        if (!creatorId.equals(userId)) {
            if (!authorizationManager.getUserRole(userId).equals(User.Role.ADMIN)) {
                throw new CatalogException("Permission denied. Required ROLE_ADMIN to create a study with creatorId != userId");
            } else {
                if (!catalogDBAdaptor.userExists(creatorId)) {
                    throw new CatalogException("ERROR: CreatorId does not exist.");
                }
            }
        }

//        URI projectUri = catalogIOManager.getProjectUri(projectOwnerId, Integer.toString(projectId));
        LinkedList<File> files = new LinkedList<>();
        LinkedList<Experiment> experiments = new LinkedList<>();
        LinkedList<Job> jobs = new LinkedList<>();
        LinkedList<Acl> acls = new LinkedList<>();

        /* Add default ACL */
        if (!creatorId.equals(projectOwnerId)) {
            //Add full permissions for the creator if he is not the owner
            acls.add(new Acl(creatorId, true, true, true, true));
        }

        //Copy generic permissions from the project.

        QueryResult<Acl> aclQueryResult = catalogDBAdaptor.getProjectAcl(projectId, Acl.USER_OTHERS_ID);
        if (!aclQueryResult.getResult().isEmpty()) {
            //study.getAcl().add(aclQueryResult.getResult().get(0));
        } else {
            throw new CatalogDBException("Project " + projectId + " must have generic ACL");
        }


        files.add(new File(".", File.Type.FOLDER, null, null, "", creatorId, "study root folder", File.Status.READY, 0));

        Study study = new Study(-1, name, alias, type, creatorId, creationDate, description, status, TimeUtils.getTime(),
                0, cipher, acls, experiments, files, jobs, new LinkedList<Sample>(), new LinkedList<Dataset>(),
                new LinkedList<Cohort>(), new LinkedList<VariableSet>(), null, datastores, stats, attributes);


        /* CreateStudy */
        QueryResult<Study> result = catalogDBAdaptor.createStudy(projectId, study, options);
        study = result.getResult().get(0);

//        URI studyUri;
        if (uri == null) {
            try {
                uri = catalogIOManager.createStudy(projectOwnerId, Integer.toString(projectId), Integer.toString(study.getId()));
            } catch (CatalogIOManagerException e) {
                e.printStackTrace();
                catalogDBAdaptor.deleteStudy(study.getId());
                throw e;
            }
        }

        catalogDBAdaptor.modifyStudy(study.getId(), new ObjectMap("uri", uri));

        createFolder(result.getResult().get(0).getId(), Paths.get("data"), true, null, sessionId);
        createFolder(result.getResult().get(0).getId(), Paths.get("analysis"), true, null, sessionId);

        catalogDBAdaptor.updateUserLastActivity(projectOwnerId);
        return result;
    }

    public QueryResult<Study> getStudy(int studyId, String sessionId)
            throws CatalogException {
        return getStudy(studyId, sessionId, null);
    }

    public QueryResult<Study> getStudy(int studyId, String sessionId, QueryOptions options)
            throws CatalogException {
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        Acl studyAcl = authorizationManager.getStudyACL(userId, studyId);
        if (studyAcl.isRead()) {
            QueryResult<Study> studyResult = catalogDBAdaptor.getStudy(studyId, options);
            if (!studyResult.getResult().isEmpty()) {
                authorizationManager.filterFiles(userId, studyAcl, studyResult.getResult().get(0).getFiles());
            }
            return studyResult;
        } else {
            throw new CatalogDBException("Permission denied. Can't read this study");
        }
    }

    public QueryResult<Study> getAllStudies(int projectId, QueryOptions options, String sessionId)
            throws CatalogException {
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);

        Acl projectAcl = authorizationManager.getProjectACL(userId, projectId);
        if (!projectAcl.isRead()) {
            throw new CatalogDBException("Permission denied. Can't read project");
        }

        QueryResult<Study> allStudies = catalogDBAdaptor.getAllStudies(projectId, options);
        List<Study> studies = allStudies.getResult();
        authorizationManager.filterStudies(userId, projectAcl, studies);
        allStudies.setResult(studies);

        return allStudies;


    }

    public QueryResult renameStudy(int studyId, String newStudyAlias, String sessionId)
            throws CatalogException {
        checkAlias(newStudyAlias, "newStudyAlias");
        checkParameter(sessionId, "sessionId");
        String sessionUserId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        String studyOwnerId = catalogDBAdaptor.getStudyOwnerId(studyId);

        if (!authorizationManager.getStudyACL(sessionUserId, studyId).isWrite()) {  //User can't write/modify the study
            throw new CatalogDBException("Permission denied. Can't write in project");
        }

        // Both users must bu updated
        catalogDBAdaptor.updateUserLastActivity(sessionUserId);
        catalogDBAdaptor.updateUserLastActivity(studyOwnerId);
        //TODO get all shared users to updateUserLastActivity

        return catalogDBAdaptor.renameStudy(studyId, newStudyAlias);

    }

    /**
     * Modify some params from the specified study:
     * <p/>
     * name
     * description
     * organization
     * status
     * <p/>
     * attributes
     * stats
     *
     * @param studyId    Study identifier
     * @param parameters Parameters to change.
     * @param sessionId  sessionId to check permissions
     * @return
     * @throws org.opencb.opencga.catalog.db.CatalogDBException
     */
    public QueryResult modifyStudy(int studyId, ObjectMap parameters, String sessionId)
            throws CatalogException {
        checkObj(parameters, "Parameters");
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        if (!authorizationManager.getStudyACL(userId, studyId).isWrite()) {
            throw new CatalogDBException("User " + userId + " can't modify the study " + studyId);
        }
        for (String s : parameters.keySet()) {
            if (!s.matches("name|type|description|status|attributes|stats")) {
                throw new CatalogDBException("Parameter '" + s + "' can't be changed");
            }
        }

        String ownerId = catalogDBAdaptor.getStudyOwnerId(studyId);
        catalogDBAdaptor.updateUserLastActivity(ownerId);
        return catalogDBAdaptor.modifyStudy(studyId, parameters);
    }

    public QueryResult shareStudy(int studyId, Acl acl, String sessionId) throws CatalogException {
        checkObj(acl, "acl");
        checkParameter(sessionId, "sessionId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        Acl studyAcl = authorizationManager.getStudyACL(userId, studyId);
        if (!studyAcl.isWrite()) {
            throw new CatalogDBException("Permission denied. Can't modify project");
        }

        return catalogDBAdaptor.setStudyAcl(studyId, acl);
    }

    /**
     * File methods
     * ***************************
     */

    public String getFileOwner(int fileId) throws CatalogDBException {
        return catalogDBAdaptor.getFileOwnerId(fileId);
    }

    public int getStudyIdByFileId(int fileId) throws CatalogDBException {
        return catalogDBAdaptor.getStudyIdByFileId(fileId);
    }

//    public int getStudyIdByAnalysisId(int analysisId) throws CatalogManagerException {
//        return catalogDBAdaptor.getStudyIdByAnalysisId(analysisId);
//    }

    @Deprecated
    public QueryResult<File> createFile(int studyId, File.Format format, File.Bioformat bioformat, String path, String description,
                                        boolean parents, String sessionId)
            throws CatalogException, CatalogIOManagerException {
        return createFile(studyId, format, bioformat, path, description, parents, -1, sessionId);
    }


//    @Deprecated
//    public QueryResult<File> uploadFile(int studyId, File.Format format, File.Bioformat bioformat, String path, String description,
//                                        boolean parents, String sessionId)
//            throws CatalogException {
//
//        QueryResult<File> result = createFile(studyId, format, bioformat, path, description, parents, -1, sessionId);
//        File file = result.getResult().get(0);
//
//        ObjectMap modifyParameters = new ObjectMap("status", File.Status.READY);
//        catalogDBAdaptor.modifyFile(file.getId(), modifyParameters);
//
//        return result;
//    }

    //create file with byte[]
    public QueryResult<File> createFile(int studyId, File.Format format, File.Bioformat bioformat, String path, byte[] bytes, String description,
                                        boolean parents, String sessionId)
            throws CatalogException, IOException {

        QueryResult<File> result = createFile(studyId, format, bioformat, path, description, parents, -1, sessionId);
        File file = result.getResult().get(0);
        //path is relative to user, get full path...
        URI studyURI = getStudyUri(studyId);
        URI fileURI = getFileUri(studyURI, path);
        Files.write(Paths.get(fileURI), bytes);

        ObjectMap modifyParameters = new ObjectMap("status", File.Status.READY);
        catalogDBAdaptor.modifyFile(file.getId(), modifyParameters);

        return result;
    }

    public QueryResult<File> createFile(int studyId, File.Format format, File.Bioformat bioformat, String path, Path completedFilePath, String description,
                                        boolean parents, String sessionId)
            throws CatalogException, IOException {

        QueryResult<File> result = createFile(studyId, format, bioformat, path, description, parents, -1, sessionId);
        File file = result.getResult().get(0);
        //path is relative to user, get full path...
        URI studyURI = getStudyUri(studyId);
        URI fileURI = getFileUri(studyURI, path);
        Files.move(completedFilePath, Paths.get(fileURI));

        ObjectMap modifyParameters = new ObjectMap("status", File.Status.READY);
        catalogDBAdaptor.modifyFile(file.getId(), modifyParameters);

        return result;
    }

    public QueryResult<File> createFile(int studyId, File.Format format, File.Bioformat bioformat, String path, String description,
                                        boolean parents, int jobId, String sessionId)
            throws CatalogException, CatalogIOManagerException {
        return createFile(studyId, File.Type.FILE, format, bioformat, path, null, null, description, null, 0, -1, null,
                jobId, null, null, parents, null, sessionId);
    }


    public QueryResult<File> createFile(int studyId, File.Type type, File.Format format, File.Bioformat bioformat, String path,
                                        String ownerId, String creationDate, String description, File.Status status,
                                        long diskUsage, int experimentId, List<Integer> sampleIds, int jobId,
                                        Map<String, Object> stats, Map<String, Object> attributes,
                                        boolean parents, QueryOptions options, String sessionId)
            throws CatalogException {
        return fileManager.create(studyId, type, format, bioformat, path, ownerId, creationDate, description, status,
                diskUsage, experimentId, sampleIds, jobId, stats, attributes, parents, options, sessionId);
    }


    @Deprecated
    public QueryResult<File> uploadFile(int studyId, File.Format format, File.Bioformat bioformat, String path, String description,
                                        boolean parents, InputStream fileIs, String sessionId)
            throws IOException, CatalogException {
        QueryResult<File> fileResult = createFile(studyId, format, bioformat, path, description, parents, sessionId);
        int fileId = fileResult.getResult().get(0).getId();
        try {
            fileResult = uploadFile(fileId, fileIs, sessionId);
        } catch (CatalogIOManagerException | InterruptedException | CatalogDBException | IOException e) {
            deleteFile(fileId, sessionId);
            e.printStackTrace();
        }
        return fileResult;
    }


    @Deprecated
    public QueryResult<File> uploadFile(int fileId, InputStream fileIs, String sessionId) throws CatalogException,
            IOException, InterruptedException {

        checkObj(fileIs, "InputStream");
        checkParameter(sessionId, "SessionId");

        String userId = catalogDBAdaptor.getFileOwnerId(fileId);
        int studyId = catalogDBAdaptor.getStudyIdByFileId(fileId);
        int projectId = catalogDBAdaptor.getProjectIdByStudyId(studyId);

        List<File> result = catalogDBAdaptor.getFile(fileId, null).getResult();
        if (result.isEmpty()) {
            throw new CatalogDBException("FileId '" + fileId + "' for found");
        }
        File file = result.get(0);
        if (!file.getStatus().equals(File.Status.UPLOADING)) {
            throw new CatalogDBException("File '" + fileId + "' already uploaded.");
        }
        if (!file.getOwnerId().equals(userId)) {
            throw new CatalogDBException("UserId mismatch with file creator");
        }
        ioManager.createFile(getStudyUri(studyId), file.getPath(), fileIs);
        Study study = catalogDBAdaptor.getStudy(studyId, null).getResult().get(0);

        ObjectMap modifyParameters = new ObjectMap("status", File.Status.UPLOADED);
        modifyParameters.put("uriScheme", study.getUri().getScheme());
        catalogDBAdaptor.modifyFile(fileId, modifyParameters);
        return catalogDBAdaptor.getFile(fileId, null);
    }

    public QueryResult<File> createFolder(int studyId, Path folderPath, boolean parents, QueryOptions options, String sessionId)
            throws CatalogException {
        checkPath(folderPath, "folderPath");
        return fileManager.createFolder(studyId, folderPath.toString() + "/", parents, options, sessionId);
//        checkPath(folderPath, "folderPath");
//        checkParameter(sessionId, "sessionId");
//        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
//        String ownerId = catalogDBAdaptor.getStudyOwnerId(studyId);
//        int projectId = catalogDBAdaptor.getProjectIdByStudyId(studyId);
//
//        LinkedList<File> folders = new LinkedList<>();
//        Path parent = folderPath.getParent();
//        int parentId = -1;
//        if (parent != null) {
//            parentId = catalogDBAdaptor.getFileId(studyId, parent.toString() + "/");
//        }
//        if (!parents && parentId < 0 && parent != null) {  //If !parents and parent does not exist in the DB (but should exist)
//            throw new CatalogDBException("Path '" + parent + "' does not exist");
//        }
//
//        /*
//            PERMISSION CHECK
//         */
//        Acl fileAcl;
//        if (parentId < 0) { //If it hasn't got parent, take the StudyAcl
//            fileAcl = authorizationManager.getStudyACL(userId, studyId);
//        } else {
//            fileAcl = authorizationManager.getFileACL(userId, parentId);
//        }
//
//        if (!fileAcl.isWrite()) {
//            throw new CatalogDBException("Permission denied. Can't create files or folders in this study");
//        }
//
//        /*
//            CHECK ALREADY EXISTS
//         */
//        if (catalogDBAdaptor.getFileId(studyId, folderPath.toString() + "/") >= 0) {
//            throw new CatalogException("Cannot create directory ‘" + folderPath + "’: File exists");
//        }
//
//        /*
//            PARENTS FOLDERS
//         */
//        while (parentId < 0 && parent != null) {  //Add all the parents that should be created
//            folders.addFirst(new File(parent.getFileName().toString(), File.Type.FOLDER, File.Format.PLAIN, File.Bioformat.NONE,
//                    parent.toString() + "/", userId, "", File.Status.READY, 0));
//            parent = parent.getParent();
//            if (parent != null) {
//                parentId = catalogDBAdaptor.getFileId(studyId, parent.toString() + "/");
//            }
//        }
//
//        ioManager.createFolder(getStudyUri(studyId), folderPath.toString(), parents);
//        File mainFolder = new File(folderPath.getFileName().toString(), File.Type.FOLDER, File.Format.PLAIN, File.Bioformat.NONE,
//                folderPath.toString() + "/", userId, "", File.Status.READY, 0);
//
//        QueryResult<File> result;
//        try {
//            assert folders.size() == 0 && !parents || parents;
//            for (File folder : folders) {
//                catalogDBAdaptor.createFileToStudy(studyId, folder, options);
//            }
//            result = catalogDBAdaptor.createFileToStudy(studyId, mainFolder, options);
//        } catch (CatalogDBException e) {
//            ioManager.deleteFile(getStudyUri(studyId), folderPath.toString());
//            throw e;
//        }
//        catalogDBAdaptor.updateUserLastActivity(ownerId);
//        return result;
    }


    public QueryResult deleteFolder(int folderId, String sessionId)
            throws CatalogException, IOException {
        return deleteFile(folderId, sessionId);
    }

    public QueryResult deleteFile(int fileId, String sessionId)
            throws CatalogException, IOException {
        return fileManager.delete(fileId, null, sessionId);
    }

    public QueryResult moveFile(int fileId, int folderId, String sessionId) throws CatalogException {
//        checkParameter(sessionId, "sessionId");
//        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
//        int studyId = catalogDBAdaptor.getStudyIdByFileId(fileId);
//        int projectId = catalogDBAdaptor.getProjectIdByStudyId(studyId);
//        String ownerId = catalogDBAdaptor.getProjectOwnerId(projectId);
//
//        if (!authorizationManager.getFileACL(userId, fileId).isWrite()) {
//            throw new CatalogManagerException("Permission denied. User can't rename this file");
//        }
//        QueryResult<File> fileResult = catalogDBAdaptor.getFile(fileId);
//        if (fileResult.getResult().isEmpty()) {
//            return new QueryResult("Delete file", 0, 0, 0, "File not found", null, null);
//        }
//        File file = fileResult.getResult().get(0);
        throw new UnsupportedClassVersionError("move File unsupported");
    }

    public QueryResult renameFile(int fileId, String newName, String sessionId)
            throws CatalogException, IOException, CatalogIOManagerException {
        return fileManager.renameFile(fileId, newName, sessionId);
    }

    /**
     * Modify some params from the specified file:
     * <p/>
     * name
     * type
     * format
     * bioformat
     * description
     * status
     * <p/>
     * attributes
     * stats
     *
     * @param fileId     File identifier
     * @param parameters Parameters to change.
     * @param sessionId  sessionId to check permissions
     * @return
     * @throws org.opencb.opencga.catalog.db.CatalogDBException
     */
    public QueryResult modifyFile(int fileId, ObjectMap parameters, String sessionId)
            throws CatalogException {
        return fileManager.update(fileId, parameters, sessionId);
    }

    public QueryResult<File> getFileParent(int fileId, QueryOptions options, String sessionId)
            throws CatalogException {
        return fileManager.getParent(fileId, options, sessionId);
    }

    public QueryResult<File> getFile(int fileId, String sessionId)
            throws CatalogException {
        return getFile(fileId, null, sessionId);
    }

    public QueryResult<File> getFile(int fileId, QueryOptions options, String sessionId)
            throws CatalogException {
        return fileManager.read(fileId, options, sessionId);
    }

    public QueryResult<File> getAllFiles(int studyId, QueryOptions options, String sessionId) throws CatalogException {
        checkParameter(sessionId, "sessionId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        Acl studyAcl = authorizationManager.getStudyACL(userId, studyId);
        if (!studyAcl.isRead()) {
            throw new CatalogException("Permission denied. User can't read file");
        }
        QueryResult<File> allFilesResult = catalogDBAdaptor.getAllFiles(studyId, options);
        List<File> files = allFilesResult.getResult();
        authorizationManager.filterFiles(userId, studyAcl, files);
        allFilesResult.setResult(files);
        return allFilesResult;
    }

    public QueryResult<File> getAllFilesInFolder(int folderId, QueryOptions options, String sessionId) throws CatalogException {
        checkParameter(sessionId, "sessionId");
        checkId(folderId, "folderId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        int studyId = catalogDBAdaptor.getStudyIdByFileId(folderId);
        Acl studyAcl = authorizationManager.getStudyACL(userId, studyId);
        if (!studyAcl.isRead()) {
            throw new CatalogDBException("Permission denied. User can't read file");
        }
        QueryResult<File> allFilesResult = catalogDBAdaptor.getAllFilesInFolder(folderId, options);
        List<File> files = allFilesResult.getResult();
        authorizationManager.filterFiles(userId, studyAcl, files);
        allFilesResult.setResult(files);
        return allFilesResult;
    }

    public DataInputStream downloadFile(int fileId, String sessionId)
            throws IOException, CatalogException {
        return downloadFile(fileId, -1, -1, sessionId);
    }

    public DataInputStream downloadFile(int fileId, int start, int limit, String sessionId)    //TODO: start & limit does not work
            throws IOException, CatalogException {
        checkParameter(sessionId, "sessionId");


        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        if (!authorizationManager.getFileACL(userId, fileId).isRead()) {
            throw new CatalogDBException("Permission denied. User can't download file");
        }
        int studyId = catalogDBAdaptor.getStudyIdByFileId(fileId);
        QueryResult<File> fileResult = catalogDBAdaptor.getFile(fileId, null);
        if (fileResult.getResult().isEmpty()) {
            throw new CatalogDBException("File not found");
        }
        File file = fileResult.getResult().get(0);

        return ioManager.getFileObject(getStudyUri(studyId),
                file.getPath(), start, limit);
    }

    public DataInputStream grepFile(int fileId, String pattern, boolean ignoreCase, boolean multi, String sessionId)
            throws IOException, CatalogException {
        checkParameter(sessionId, "sessionId");


        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        if (!authorizationManager.getFileACL(userId, fileId).isRead()) {
            throw new CatalogException("Permission denied. User can't download file");
        }
        int studyId = catalogDBAdaptor.getStudyIdByFileId(fileId);
        int projectId = catalogDBAdaptor.getProjectIdByStudyId(studyId);
        QueryResult<File> fileResult = catalogDBAdaptor.getFile(fileId, null);
        if (fileResult.getResult().isEmpty()) {
            throw new CatalogException("File not found");
        }
        File file = fileResult.getResult().get(0);

        return ioManager.getGrepFileObject(getStudyUri(studyId),
                file.getPath(), pattern, ignoreCase, multi);
    }


    /**
     * TODO: Set per-file ACL
     */
    private QueryResult shareFile(int fileId, Acl acl, String sessionId)
            throws CatalogException {
        checkObj(acl, "acl");
        checkParameter(sessionId, "sessionId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        Acl fileAcl = authorizationManager.getFileACL(userId, fileId);
        if (!fileAcl.isWrite()) {
            throw new CatalogDBException("Permission denied. Can't modify file");
        }

        return catalogDBAdaptor.setFileAcl(fileId, acl);
    }

    /*Require role admin*/
    public QueryResult<File> searchFile(QueryOptions query, QueryOptions options, String sessionId)
            throws CatalogException {
        return searchFile(-1, query, options, sessionId);
    }

    public QueryResult<File> searchFile(int studyId, QueryOptions query, String sessionId)
            throws CatalogException {
        return searchFile(studyId, query, null, sessionId);
    }

    public QueryResult<File> searchFile(int studyId, QueryOptions query, QueryOptions options, String sessionId)
            throws CatalogException {
        return fileManager.readAll(studyId, query, options, sessionId);
    }

    public QueryResult<Dataset> createDataset(int studyId, String name, String description, List<Integer> files,
                                              Map<String, Object> attributes, QueryOptions options, String sessionId)
            throws CatalogException {
        return fileManager.createDataset(studyId, name, description, files, attributes, options, sessionId);
    }

    public QueryResult<Dataset> getDataset(int dataSetId, QueryOptions options, String sessionId)
            throws CatalogException {
        return fileManager.getDataset(dataSetId, options, sessionId);
    }

//    public DataInputStream getGrepFileObjectFromBucket(String userId, String bucketId, Path objectId, String sessionId, String pattern, boolean ignoreCase, boolean multi)
//            throws CatalogIOManagerException, IOException, CatalogManagerException {
//        checkParameter(bucketId, "bucket");
//        checkParameter(userId, "userId");
//        checkParameter(sessionId, "sessionId");
//        checkParameter(objectId.toString(), "objectId");
//        checkParameter(pattern, "pattern");
//
//        return ioManager.getGrepFileObject(userId, bucketId, objectId, pattern, ignoreCase, multi);
//    }


    public QueryResult refreshFolder(final int folderId, final String sessionId)
            throws CatalogDBException, IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * **************************
     * Job methods
     * ***************************
     */

    public int getStudyIdByJobId(int jobId) throws CatalogDBException {
        return catalogDBAdaptor.getStudyIdByJobId(jobId);
    }

    public QueryResult<Job> createJob(int studyId, String name, String toolName, String description, String commandLine,
                                      URI tmpOutDirUri, int outDirId, List<Integer> inputFiles, Map<String, Object> attributes,
                                      Map<String, Object> resourceManagerAttributes, Job.Status status, QueryOptions options, String sessionId)
            throws CatalogException {
        checkParameter(sessionId, "sessionId");
        checkParameter(name, "name");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        checkParameter(toolName, "toolName");
        checkParameter(commandLine, "commandLine");
        description = defaultString(description, "");
        status = defaultObject(status, Job.Status.PREPARED);

        // FIXME check inputFiles? is a null conceptually valid?

//        URI tmpOutDirUri = createJobOutdir(studyId, randomString, sessionId);

        if (!authorizationManager.getStudyACL(userId, studyId).isWrite()) {
            throw new CatalogException("Permission denied. Can't create job");
        }
        QueryOptions fileQueryOptions = new QueryOptions("include", Arrays.asList("id", "type", "path"));
        File outDir = catalogDBAdaptor.getFile(outDirId, fileQueryOptions).getResult().get(0);

        if (!outDir.getType().equals(File.Type.FOLDER)) {
            throw new CatalogException("Bad outDir type. Required type : " + File.Type.FOLDER);
        }

        Job job = new Job(name, userId, toolName, description, commandLine, outDir.getId(), tmpOutDirUri, inputFiles);
        job.setStatus(status);
        if (resourceManagerAttributes != null) {
            job.getResourceManagerAttributes().putAll(resourceManagerAttributes);
        }
        if (attributes != null) {
            job.setAttributes(attributes);
        }

        return catalogDBAdaptor.createJob(studyId, job, options);
    }

    public URI createJobOutDir(int studyId, String dirName, String sessionId)
            throws CatalogException, CatalogIOManagerException {
        checkParameter(sessionId, "sessionId");
        checkParameter(dirName, "dirName");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        QueryOptions studyQueryOptions = null;
//        studyQueryOptions = new QueryOptions("include", Arrays.asList("projects.studies.uri", "projects.studies.id"));
        QueryResult<Study> studyQueryResult = getStudy(studyId, sessionId, studyQueryOptions);

        URI uri = studyQueryResult.getResult().get(0).getUri();
        CatalogIOManager catalogIOManager = catalogIOManagerFactory.get(uri);
        return catalogIOManager.createJobOutDir(userId, dirName);
    }

    public QueryResult<ObjectMap> incJobVisites(int jobId, String sessionId) throws CatalogException {
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
//        int analysisId = catalogDBAdaptor.getStudyIdByJobId(jobId);
//        int studyId = catalogDBAdaptor.getStudyIdByAnalysisId(analysisId);
        int studyId = catalogDBAdaptor.getStudyIdByJobId(jobId);
        if (!authorizationManager.getStudyACL(userId, studyId).isRead()) {
            throw new CatalogException("Permission denied. Can't read job");
        }
        return catalogDBAdaptor.incJobVisits(jobId);
    }

    public QueryResult deleteJob(int jobId, String sessionId)
            throws CatalogException, CatalogIOManagerException {
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
//        int analysisId = catalogDBAdaptor.getStudyIdByJobId(jobId);
//        int studyId = catalogDBAdaptor.getStudyIdByAnalysisId(analysisId);
        int studyId = catalogDBAdaptor.getStudyIdByJobId(jobId);
        if (!authorizationManager.getStudyACL(userId, studyId).isDelete()) {
            throw new CatalogException("Permission denied. Can't delete job");
        }

        return catalogDBAdaptor.deleteJob(jobId);
    }


    public QueryResult<Job> getJob(int jobId, QueryOptions options, String sessionId) throws CatalogException {
        checkParameter(sessionId, "sessionId");
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
//        int analysisId = catalogDBAdaptor.getStudyIdByJobId(jobId);
//        int studyId = catalogDBAdaptor.getStudyIdByAnalysisId(analysisId);
        int studyId = catalogDBAdaptor.getStudyIdByJobId(jobId);
        if (!authorizationManager.getStudyACL(userId, studyId).isRead()) {
            throw new CatalogException("Permission denied. Can't read job");
        }

        return catalogDBAdaptor.getJob(jobId, options);
    }

    public QueryResult<Job> getUnfinishedJobs(String sessionId) throws CatalogException {
        String userId = getUserIdBySessionId(sessionId);
        User.Role role = authorizationManager.getUserRole(userId);
        switch (role) {
            case ADMIN:
                return catalogDBAdaptor.searchJob(new QueryOptions("ready", false));
            default:
                throw new CatalogException("Permission denied. Admin role required");
        }
    }


    public QueryResult<Job> getAllJobs(int studyId, String sessionId) throws CatalogException {
        String userId = getUserIdBySessionId(sessionId);
        if (!authorizationManager.getStudyACL(userId, studyId).isRead()) {
            throw new CatalogException("Permission denied. Can't get jobs");
        }
        return catalogDBAdaptor.getAllJobs(studyId, new QueryOptions());
    }

//    public QueryResult<Job> getJobsByAnalysis(int analysisId, String sessionId) throws CatalogManagerException {
//        String userId = getUserIdBySessionId(sessionId);
////        getAnalysisAcl(); //TODO: Look for ACLs !!!
//        int studyId = getStudyIdByAnalysisId(analysisId);
//        if (authorizationManager.getStudyACL(userId, studyId).isRead()) {
//            return catalogDBAdaptor.searchJob(new QueryOptions("analysisId", analysisId));
//        } else {
//            throw new CatalogManagerException("Permission denied. User can't read this analysis");
//        }
//    }

    public QueryResult modifyJob(int jobId, ObjectMap parameters, String sessionId) throws CatalogException {
        String userId = getUserIdBySessionId(sessionId);

//        User.Role role = authorizationManager.getUserRole(userId);
//        switch (role) {
//            case ADMIN:
                return catalogDBAdaptor.modifyJob(jobId, parameters);
//            default:
//                throw new CatalogException("Permission denied. Admin role required");
//        }
    }

//    public DataInputStream getGrepFileFromJob(String userId, String jobId, String filename, String pattern, boolean ignoreCase, boolean multi, String sessionId)
//            throws CatalogIOManagerException, IOException, CatalogManagerException {
//        checkParameter(userId, "userId");
//        checkParameter(jobId, "jobId");
//        checkParameter(filename, "filename");
//        checkParameter(pattern, "pattern");
//        checkParameter(sessionId, "sessionId");
//
//
//        Path jobPath = getUserUri(userId).resolve(catalogDBAdaptor.getJobPath(userId, jobId, sessionId));
//
//        return ioManager.getGrepFileFromJob(jobPath, filename, pattern, ignoreCase, multi);
//    }
//
//    public InputStream getJobZipped(String userId, String jobId, String sessionId) throws CatalogIOManagerException,
//            IOException, CatalogManagerException {
//        checkParameter(userId, "userId");
//        checkParameter(jobId, "jobId");
//        checkParameter(sessionId, "sessionId");
//
//        Path jobPath = getUserUri(userId).resolve(catalogDBAdaptor.getJobPath(userId, jobId, sessionId));
//        logger.info("getJobZipped");
//        logger.info(jobPath.toString());
//        logger.info(jobId);
//        return ioManager.getJobZipped(jobPath, jobId);
//    }
//
//    public QueryResult createJob(String jobName, String projectId, String jobFolder, String toolName, List<String> dataList,
//                                 String commandLine, String sessionId) throws CatalogManagerException, CatalogIOManagerException, JsonProcessingException {
//
//        checkParameter(jobName, "jobName");
//        checkParameter(projectId, "projectId");
//        checkParameter(toolName, "toolName");
//        checkParameter(sessionId, "sessionId");
//        String userId = catalogDBAdaptor.getAccountIdBySessionId(sessionId);
//
//        String jobId = StringUtils.randomString(15);
//        boolean jobFolderCreated = false;
//
//        if (jobFolder == null) {
//            ioManager.createJob(userId, projectId, jobId);
//            jobFolder = Paths.get("projects", projectId).resolve(jobId).toString();
//            jobFolderCreated = true;
//        }
//        checkParameter(jobFolder, "jobFolder");
//
//        Job job = new Job(jobId, jobName, jobFolder, toolName, Job.QUEUED, commandLine, "", dataList);
//
//        try {
//            return catalogDBAdaptor.createJob(userId, projectId, job, sessionId);
//        } catch (CatalogManagerException e) {
//            if (jobFolderCreated) {
//                ioManager.deleteJob(userId, projectId, jobId);
//            }
//            throw e;
//        }
//    }
//
//
//    public List<AnalysisPlugin> getUserAnalysis(String sessionId) throws CatalogManagerException, IOException {
//        return catalogDBAdaptor.getUserAnalysis(sessionId);
//    }
//
//    public void setJobCommandLine(String userId, String jobId, String commandLine, String sessionId)
//            throws CatalogManagerException, IOException {
//        catalogDBAdaptor.setJobCommandLine(userId, jobId, commandLine, sessionId);// this
//        // method
//        // increases
//        // visites
//        // by 1
//        // in
//        // mongodb
//    }

    /**
     * Samples methods
     * ***************************
     */

    public QueryResult<Sample> createSample(int studyId, String name, String source, String description,
                                            Map<String, Object> attributes, QueryOptions options, String sessionId)
            throws CatalogException {
        checkParameter(sessionId, "sessionId");
        checkParameter(name, "name");
        source = defaultString(source, "");
        description = defaultString(description, "");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);

        if (!authorizationManager.getStudyACL(userId, studyId).isWrite()) {
            throw new CatalogException("Permission denied. User " + userId + " can't modify study");
        }
        Sample sample = new Sample(-1, name, source, null, description, Collections.<AnnotationSet>emptyList(),
                attributes);

        return catalogDBAdaptor.createSample(studyId, sample, options);
    }

    public QueryResult<Sample> getSample(int sampleId, QueryOptions options, String sessionId) throws CatalogException {
        checkParameter(sessionId, "sessionId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        int studyId = catalogDBAdaptor.getStudyIdBySampleId(sampleId);

        if (!authorizationManager.getStudyACL(userId, studyId).isRead()) {
            throw new CatalogException("Permission denied. User " + userId + " can't read study");
        }

        return catalogDBAdaptor.getSample(sampleId, options);
    }

    public QueryResult<Sample> getAllSamples(int studyId, QueryOptions options, String sessionId) throws CatalogException {
        checkParameter(sessionId, "sessionId");

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);

        if (!authorizationManager.getStudyACL(userId, studyId).isRead()) {
            throw new CatalogException("Permission denied. User " + userId + " can't read study");
        }

        return catalogDBAdaptor.getAllSamples(studyId, options);
    }

    public QueryResult<VariableSet> createVariableSet(int studyId, String name, Boolean unique,
                                                      String description, Map<String, Object> attributes,
                                                      List<Variable> variables, String sessionId)
            throws CatalogException {

        checkObj(variables, "Variables List");
        Set<Variable> variablesSet = new HashSet<>(variables);
        if (variables.size() != variablesSet.size()) {
            throw new CatalogException("Error. Repeated variables");
        }
        return createVariableSet(studyId, name, unique, description, attributes, variablesSet, sessionId);
    }

    public QueryResult<VariableSet> createVariableSet(int studyId, String name, Boolean unique,
                                                      String description, Map<String, Object> attributes,
                                                      Set<Variable> variables, String sessionId)
            throws CatalogException {
        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        checkParameter(sessionId, "sessionId");
        checkParameter(name, "name");
        checkObj(variables, "Variables Set");
        unique = defaultObject(unique, true);
        description = defaultString(description, "");
        attributes = defaultObject(attributes, new HashMap<String, Object>());

        for (Variable variable : variables) {
            checkParameter(variable.getId(), "variable ID");
            checkObj(variable.getType(), "variable Type");
            variable.setAllowedValues(defaultObject(variable.getAllowedValues(), Collections.<String>emptyList()));
            variable.setAttributes(defaultObject(variable.getAttributes(), Collections.<String, Object>emptyMap()));
            variable.setCategory(defaultString(variable.getCategory(), ""));
            variable.setDependsOn(defaultString(variable.getDependsOn(), ""));
            variable.setDescription(defaultString(variable.getDescription(), ""));
//            variable.setRank(defaultString(variable.getDescription(), ""));
        }

        if (!authorizationManager.getStudyACL(userId, studyId).isWrite()) {
            throw new CatalogException("Permission denied. User " + userId + " can't modify study");
        }

        VariableSet variableSet = new VariableSet(-1, name, unique, description, variables, attributes);
        CatalogSampleAnnotationsValidator.checkVariableSet(variableSet);

        return catalogDBAdaptor.createVariableSet(studyId, variableSet);
    }

    public QueryResult<VariableSet> getVariableSet(int variableSet, QueryOptions options, String sessionId)
            throws CatalogException {

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        int studyId = catalogDBAdaptor.getStudyIdByVariableSetId(variableSet);
        if (!authorizationManager.getStudyACL(userId, studyId).isRead()) {
            throw new CatalogException("Permission denied. User " + userId + " can't read study");
        }

        return catalogDBAdaptor.getVariableSet(variableSet, options);
    }


    public QueryResult<AnnotationSet> annotateSample(int sampleId, String id, int variableSetId,
                                                     Map<String, Object> annotations,
                                                     Map<String, Object> attributes,
                                                     String sessionId) throws CatalogException {
        return annotateSample(sampleId, id, variableSetId, annotations, attributes, true, sessionId);
    }

    /* package */ QueryResult<AnnotationSet> annotateSample(int sampleId, String id, int variableSetId,
                                                            Map<String, Object> annotations,
                                                            Map<String, Object> attributes,
                                                            boolean checkAnnotationSet,
                                                            String sessionId)
            throws CatalogException {
        checkParameter(sessionId, "sessionId");
        checkParameter(id, "id");
        checkObj(annotations, "annotations");
        attributes = defaultObject(attributes, new HashMap<String, Object>());

        String userId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        int studyId = catalogDBAdaptor.getStudyIdBySampleId(sampleId);
        if (!authorizationManager.getStudyACL(userId, studyId).isWrite()) {
            throw new CatalogException("Permission denied. User " + userId + " can't modify study");
        }

        QueryResult<VariableSet> variableSetResult = catalogDBAdaptor.getVariableSet(variableSetId, null);
        if (variableSetResult.getResult().isEmpty()) {
            throw new CatalogException("VariableSet " + variableSetId + " does not exists");
        }
        VariableSet variableSet = variableSetResult.getResult().get(0);

        AnnotationSet annotationSet = new AnnotationSet(id, variableSetId, new HashSet<Annotation>(), TimeUtils.getTime(), attributes);

        for (Map.Entry<String, Object> entry : annotations.entrySet()) {
            annotationSet.getAnnotations().add(new Annotation(entry.getKey(), entry.getValue()));
        }
        QueryResult<Sample> sampleQueryResult = catalogDBAdaptor.getSample(sampleId,
                new QueryOptions("include", Collections.singletonList("annotationSets")));

        List<AnnotationSet> annotationSets = sampleQueryResult.getResult().get(0).getAnnotationSets();
        if (checkAnnotationSet) {
            CatalogSampleAnnotationsValidator.checkAnnotationSet(variableSet, annotationSet, annotationSets);
        }

        return catalogDBAdaptor.annotateSample(sampleId, annotationSet);
    }

    /**
     * Cohort methods
     * ***************************
     */

    public int getStudyIdByCohortId(int cohortId) throws CatalogException {
        return catalogDBAdaptor.getStudyIdByCohortId(cohortId);
    }

    public QueryResult<Cohort> getCohort(int cohortId, QueryOptions options, String sessionId) throws CatalogException {
        checkParameter(sessionId, "sessionId");

        int studyId = catalogDBAdaptor.getStudyIdByCohortId(cohortId);
        String userId = getUserIdBySessionId(sessionId);

        if (authorizationManager.getStudyACL(userId, studyId).isRead()) {
            return catalogDBAdaptor.getCohort(cohortId);
        } else {
            throw new CatalogException("Permission denied. User " + userId + " can't read cohorts from study");
        }
    }

    public QueryResult<Cohort> createCohort(int studyId, String name, String description, List<Integer> sampleIds,
                                            Map<String, Object> attributes, String sessionId) throws CatalogException {
        checkParameter(name, "name");
        checkObj(sampleIds, "Samples list");
        description = defaultString(description, "");
        attributes = defaultObject(attributes, Collections.<String, Object>emptyMap());

        if (getAllSamples(studyId, new QueryOptions("id", sampleIds), sessionId).getResult().size() != sampleIds.size()) {
//            for (Integer sampleId : samples) {
//                getSample(sampleId, new QueryOptions("include", "id"), sessionId).first();
//            }
            throw new CatalogException("Error: Some sampleId does not exist in the study " + studyId);
        }

        Cohort cohort = new Cohort(name, TimeUtils.getTime(), description, sampleIds, attributes);

        return catalogDBAdaptor.createCohort(studyId, cohort);
    }


    /**
     * Tools methods
     * ***************************
     */

    public QueryResult<Tool> createTool(String alias, String description, Object manifest, Object result,
                                        String path, boolean openTool, String sessionId) throws CatalogException {
        checkParameter(alias, "alias");
        checkObj(description, "description"); //description can be empty
        checkParameter(path, "path");
        checkParameter(sessionId, "sessionId");
        //TODO: Check Path

        String userId = getUserIdBySessionId(sessionId);

        List<Acl> acl = Arrays.asList(new Acl(userId, true, true, true, true));
        if (openTool) {
            acl.add(new Acl(Acl.USER_OTHERS_ID, true, false, true, false));
        }

        String name = Paths.get(path).getFileName().toString();

        Tool tool = new Tool(-1, alias, name, description, manifest, result, path, acl);

        return catalogDBAdaptor.createTool(userId, tool);
    }

    public QueryResult<Tool> getTool(int id, String sessionId) throws CatalogException {
        String userId = getUserIdBySessionId(sessionId);
        checkParameter(sessionId, "sessionId");

        //TODO: Check ACLs
        return catalogDBAdaptor.getTool(id);
    }

//    public QueryResult<Tool> searchTool(QueryOptions options, String sessionId) {
//        String userId = getUserIdBySessionId(sessionId);
//
//        catalogDBAdaptor.searchTool(options);
//    }

    /**
     * ****************
     */
    private void checkEmail(String email) throws CatalogException {
        if (email == null || !emailPattern.matcher(email).matches()) {
            throw new CatalogException("email not valid");
        }
    }

    private void checkId(int id, String name) throws CatalogException {
        if (id < 0) {
            throw new CatalogException("Error in id: '" + name + "' is not valid: "
                    + id + ".");
        }
    }

    private void checkParameter(String param, String name) throws CatalogException {
        if (param == null || param.equals("") || param.equals("null")) {
            throw new CatalogException("Error in parameter: parameter '" + name + "' is null or empty: "
                    + param + ".");
        }
    }

    private void checkParameters(String... args) throws CatalogException {
        if (args.length % 2 == 0) {
            for (int i = 0; i < args.length; i += 2) {
                checkParameter(args[i], args[i + 1]);
            }
        } else {
            throw new CatalogException("Error in parameter: parameter list is not multiple of 2");
        }
    }

    private void checkObj(Object obj, String name) throws CatalogException {
        if (obj == null) {
            throw new CatalogException("parameter '" + name + "' is null.");
        }
    }

    private void checkRegion(String regionStr, String name) throws CatalogException {
        if (Pattern.matches("^([a-zA-Z0-9])+:([0-9])+-([0-9])+$", regionStr)) {//chr:start-end
            throw new CatalogException("region '" + name + "' is not valid");
        }
    }

    private void checkSessionId(String userId, String sessionId) throws CatalogException {
        String userIdBySessionId = catalogDBAdaptor.getUserIdBySessionId(sessionId);
        if (!userIdBySessionId.equals(userId)) {
            throw new CatalogException("Invalid sessionId for user: " + userId);
        }
    }

    private void checkPath(String path, String name) throws CatalogException {
        if (path == null) {
            throw new CatalogException("parameter '" + name + "' is null.");
        }
        checkPath(Paths.get(path), name);
    }

    private void checkPath(Path path, String name) throws CatalogException {
        checkObj(path, name);
        if (path.isAbsolute()) {
            throw new CatalogException("Error in path: Path '" + name + "' can't be absolute");
        } else if (path.toString().matches("\\.|\\.\\.")) {
            throw new CatalogException("Error in path: Path '" + name + "' can't have relative names '.' or '..'");
        }
    }

    private void checkAlias(String alias, String name) throws CatalogException {
        if (alias == null || alias.isEmpty() || !alias.matches("^[_A-Za-z0-9-\\+]+$")) {
            throw new CatalogException("Error in alias: Invalid alias for '" + name + "'.");
        }
    }

    private String defaultString(String string, String defaultValue) {
        if (string == null || string.isEmpty()) {
            string = defaultValue;
        }
        return string;
    }

    private <T> T defaultObject(T object, T defaultObject) {
        if (object == null) {
            object = defaultObject;
        }
        return object;
    }


}
