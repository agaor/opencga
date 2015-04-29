package org.opencb.opencga.catalog.authorization;


//import java.security.acl.Acl;

import org.opencb.opencga.catalog.CatalogException;
import org.opencb.opencga.catalog.beans.*;
import org.opencb.opencga.catalog.db.CatalogDBException;

import java.util.List;

/**
 * @author Jacobo Coll <jacobo167@gmail.com>
 */
public interface AuthorizationManager {

    public User.Role getUserRole(String userId) throws CatalogException;

    public Acl getProjectACL(String userId, int projectId) throws CatalogException;

    public Acl getStudyACL(String userId, int studyId) throws CatalogException;

    public Acl getFileACL(String userId, int fileId) throws CatalogException;

    public Acl getSampleACL(String userId, int sampleId) throws CatalogException;

    void filterProjects(String userId, List<Project> projects) throws CatalogException;

    void filterStudies(String userId, Acl projectAcl, List<Study> studies) throws CatalogException;

    void filterFiles(String userId, Acl studyAcl, List<File> files) throws CatalogException;
}
